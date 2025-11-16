package app.lantra.ui.player

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lantra.R
import app.lantra.databinding.FragmentPlayerBinding
import app.lantra.media.MediaNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()
    private var progressJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerBinding.bind(view)

        setupControls()
        setupOpenSettings()

        // --- Check permission ---
        val hasAccess = hasNotificationAccess()
        binding.permissionContainer.isVisible = !hasAccess
        binding.playerContent.isVisible = hasAccess

        if (hasAccess) {
            // Listen to service updates
            MediaNotifier.onListenerReady = {
                MediaNotifier.listener?.mediaInfo?.value?.let { info ->
                    viewModel.setMediaInfo(info)
                }
            }

            // Initial sync
            MediaNotifier.listener?.mediaInfo?.value?.let { info ->
                viewModel.setMediaInfo(info)
            }

            // Start jumping progress updater
            startProgressUpdater()
        }

        observeMediaInfo()
    }

    private fun observeMediaInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mediaInfo.collect { info ->
                    val hasInfo = info != null

                    // Show player if we have permission
                    if (hasNotificationAccess()) {
                        binding.playerContent.isVisible = true
                        binding.permissionContainer.isVisible = false
                    }

                    if (hasInfo) {
                        binding.titleText.text = info.title ?: "Unknown Title"
                        binding.metaText.text = if (info.artist != null && info.album != null) {
                            "${info.artist} - ${info.album}"
                        } else ""

                        val art = info.albumArt
                        if (art != null) binding.albumArt.setImageBitmap(art)
                        else binding.albumArt.setImageResource(R.drawable.default_album_art)

                        val state = info.controller.playbackState?.state
                        updatePlayPauseUI(state == android.media.session.PlaybackState.STATE_PLAYING)
                    } else {
                        binding.albumArt.setImageResource(R.drawable.default_album_art)
                        binding.titleText.text = "Nothing playing"
                        binding.metaText.text = ""
                        updatePlayPauseUI(false)
                        updateProgress(0L, 1L)
                    }
                }
            }
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver,
            "enabled_notification_listeners"
        )
        return enabled.contains(requireContext().packageName)
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { viewModel.togglePlayback() }
        binding.btnNext.setOnClickListener { viewModel.next() }
        binding.btnPrev.setOnClickListener { viewModel.prev() }
    }

    private fun updatePlayPauseUI(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun setupOpenSettings() {
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    // --- Jumping progress updater ---
    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val info = viewModel.mediaInfo.value
                    if (info != null) {
                        val state = info.controller.playbackState
                        val duration = info.duration.coerceAtLeast(1L)
                        val position = state?.let { playbackState ->
                            val lastPos = playbackState.position
                            val deltaTime =
                                SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
                            (lastPos + (deltaTime * playbackState.playbackSpeed)).toLong()
                                .coerceAtMost(duration)
                        } ?: info.position
                        updateProgress(position, duration)
                    }
                    delay(500) // jump every half second
                }
            }
        }
    }

    private fun updateProgress(position: Long, duration: Long) {
        val progressPercent = ((position.toDouble() / duration) * 100).toInt().coerceIn(0, 100)
        binding.progressBar.progress = progressPercent
        binding.currentTimeText.text = formatTime(position)
        binding.totalTimeText.text = formatTime(duration)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressJob?.cancel()
        _binding = null
    }
}
