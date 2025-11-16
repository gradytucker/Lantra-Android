package app.lantra.ui.player

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.launch

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerBinding.bind(view)

        setupControls()
        checkPermission()
        observeMediaInfo()
    }

    private fun observeMediaInfo() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mediaInfo.collect { info ->
                    val hasInfo = info != null
                    binding.playerContent.isVisible = hasInfo
                    binding.permissionContainer.isVisible = !hasInfo

                    if (!hasInfo) {
                        binding.albumArt.setImageResource(R.drawable.default_album_art)
                        binding.titleText.text = "Nothing playing"
                        binding.metaText.text = ""
                        updatePlayPauseUI(false)
                        return@collect
                    }

                    // Update text
                    binding.titleText.text = info.title ?: "Unknown Title"
                    if (info.artist != null && info.album != null) {
                        binding.metaText.text = "${info.artist} - ${info.album}"
                    } else {
                        binding.metaText.text = ""
                    }

                    // Update album art
                    val art = info.albumArt
                    if (art != null) {
                        binding.albumArt.setImageBitmap(art)
                    } else {
                        binding.albumArt.setImageResource(R.drawable.default_album_art)
                    }

                    // Update play/pause button
                    val state = info.controller?.playbackState?.state
                    updatePlayPauseUI(state == android.media.session.PlaybackState.STATE_PLAYING)
                }
            }
        }
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

    private fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabled.contains(context.packageName)
    }

    private fun checkPermission() {
        val hasAccess = hasNotificationAccess(requireContext())
        binding.permissionContainer.isVisible = !hasAccess
        binding.playerContent.isVisible = hasAccess

        binding.btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
