package app.lantra.ui.player

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ImageView
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
                        if (art != null) {
                            binding.albumArt.setImageBitmap(art)
                            setBlurBackground(art)
                        } else {
                            binding.albumArt.setImageResource(R.drawable.default_album_art)
                        }

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
                            when (playbackState.state) {
                                android.media.session.PlaybackState.STATE_PLAYING -> {
                                    // Update based on elapsed time
                                    val lastPos = playbackState.position
                                    val deltaTime =
                                        SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
                                    (lastPos + (deltaTime * playbackState.playbackSpeed)).toLong()
                                        .coerceAtMost(duration)
                                }

                                android.media.session.PlaybackState.STATE_PAUSED,
                                android.media.session.PlaybackState.STATE_STOPPED -> {
                                    // Keep position constant when paused or stopped
                                    playbackState.position.coerceAtMost(duration)
                                }

                                else -> info.position.coerceAtMost(duration)
                            }
                        } ?: info.position.coerceAtMost(duration)

                        // Only update progress if it actually changed to reduce redraws
                        if (binding.progressBar.progress != ((position.toDouble() / duration) * 100).toInt()) {
                            updateProgress(position, duration)
                        }
                    }
                    delay(500) // Update every 500ms
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

    private fun setBlurBackground(albumArt: Bitmap?) {
        val blurImageView: ImageView = binding.blurBackground
        val blurEffect = RenderEffect.createBlurEffect(150f, 150f, Shader.TileMode.CLAMP)

        val tintColor = Color.parseColor("#80000000") // 50% black
        val colorFilter = BlendModeColorFilter(tintColor, BlendMode.SRC_ATOP)
        val colorFilterEffect = RenderEffect.createColorFilterEffect(colorFilter)

        val combinedEffect = RenderEffect.createChainEffect(blurEffect, colorFilterEffect)

        blurImageView.setRenderEffect(combinedEffect)
        blurImageView.setImageBitmap(albumArt)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressJob?.cancel()
        _binding = null
    }
}
