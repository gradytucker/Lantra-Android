package app.lantra.ui.speakers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import app.lantra.R
import app.lantra.databinding.FragmentSpeakersBinding
import app.lantra.model.SpeakerDevice
import app.lantra.service.AudioCaptureService
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SpeakersFragment : Fragment() {

    private var _binding: FragmentSpeakersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SpeakersViewModel by activityViewModels()
    private lateinit var speakerAdapter: SpeakerAdapter

    private var discoveredHost: String? = null
    private var discoveredPort: Int? = null
    private var isRequestingPermission = false
    private var pendingToggle: Pair<SpeakerDevice, Boolean>? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequestingPermission = false
        if (granted) {
            startProjection()
        } else {
            Toast.makeText(requireContext(), "Microphone permission required", Toast.LENGTH_SHORT)
                .show()
            pendingToggle = null
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isRequestingPermission = false
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val startStreamIntent =
                Intent(requireContext(), AudioCaptureService::class.java).apply {
                    putExtra(AudioCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(AudioCaptureService.EXTRA_DATA, result.data)
                    putExtra("server_host", discoveredHost)
                    putExtra("server_port", discoveredPort)
                }
            ContextCompat.startForegroundService(requireContext(), startStreamIntent)

            pendingToggle?.let { (device, isCasting) ->
                viewModel.toggleDeviceCasting(device, isCasting)
                pendingToggle = null
            }
        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            pendingToggle = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeakersBinding.inflate(inflater, container, false)

        setupRecyclerView()
        setupButtons()
        observeUiState()
        observeSpeakers()

        if (viewModel.uiState.value is SpeakersUiState.Searching) viewModel.startServerDiscovery()

        return binding.root
    }

    private fun onSpeakerToggled(device: SpeakerDevice, isCasting: Boolean) {
        val isStartingFirstStream = isCasting && !viewModel.isStreaming.value
        if (isStartingFirstStream) {
            pendingToggle = device to true
            checkMicAndStart()
        } else {
            viewModel.toggleDeviceCasting(device, isCasting)
        }
    }

    private fun setupTitleGradient(title: TextView) {
        title.post {
            val paint = title.paint
            val width = paint.measureText(title.text.toString())

            val startColor =
                MaterialColors.getColor(requireContext(), R.attr.gradientStartColor, Color.BLACK)
            val endColor =
                MaterialColors.getColor(requireContext(), R.attr.gradientEndColor, Color.BLACK)

            val textShader: Shader = LinearGradient(
                0f, 0f, width, title.textSize,
                intArrayOf(startColor, endColor),
                null, Shader.TileMode.CLAMP
            )
            title.paint.shader = textShader
            title.invalidate()
        }
    }

    private fun setupRecyclerView(columns: Int = 2) {
        speakerAdapter = SpeakerAdapter { device, isCasting ->
            onSpeakerToggled(device, isCasting)
        }

        binding.groupConnectedInclude.rvSpeakers.apply {
            adapter = speakerAdapter
            layoutManager = GridLayoutManager(requireContext(), columns)
        }
    }

    private fun observeSpeakers() {
        lifecycleScope.launch {
            viewModel.speakers.collectLatest { speakers ->
                speakerAdapter.submitList(speakers)

                if (viewModel.uiState.value is SpeakersUiState.Connected) {
                    val noSpeakers = speakers.isEmpty()
                    updateHeader(speakers.count { it.isCasting }, noSpeakers)

                    if (noSpeakers) {
                        binding.groupNoSpeakersInclude.root.visibility = View.VISIBLE
                        binding.groupConnectedInclude.root.visibility = View.GONE
                        setupTitleGradient(binding.groupNoSpeakersInclude.tvTitle)
                    } else {
                        binding.groupNoSpeakersInclude.root.visibility = View.GONE
                        binding.groupConnectedInclude.root.visibility = View.VISIBLE
                        setupTitleGradient(binding.groupConnectedInclude.tvTitle)
                    }
                }

                val anyCasting = speakers.any { it.isCasting }
                val isStreaming = viewModel.isStreaming.value
                if (anyCasting && !isStreaming && pendingToggle == null) {
                    checkMicAndStart()
                } else if (!anyCasting && isStreaming) {
                    stopStreaming()
                }
            }
        }
    }

    private fun updateHeader(castingCount: Int, noSpeakers: Boolean) {
        val (subtitleView, iconView) = if (noSpeakers) {
            binding.groupNoSpeakersInclude.tvSubtitle to binding.groupNoSpeakersInclude.ivSubtitleIcon
        } else {
            binding.groupConnectedInclude.tvSubtitle to binding.groupConnectedInclude.ivSubtitleIcon
        }

        if (castingCount > 0) {
            subtitleView.text = resources.getQuantityString(
                R.plurals.casting_to_n_speakers,
                castingCount,
                castingCount
            )
            iconView.setImageResource(R.drawable.ic_casting_24dp)
        } else {
            subtitleView.text = getString(R.string.all_quiet)
            iconView.setImageResource(R.drawable.ic_all_quiet_24dp)
        }
    }

    private fun checkMicAndStart() {
        if (isRequestingPermission) return
        isRequestingPermission = true
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startProjection()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startProjection() {
        val manager =
            requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun stopStreaming() {
        requireContext().stopService(Intent(requireContext(), AudioCaptureService::class.java))
    }

    private fun setupButtons() {
        binding.groupNoServerInclude.btnRetry.setOnClickListener { viewModel.startServerDiscovery() }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.groupSearchingInclude.root.visibility = View.GONE
                binding.groupNoServerInclude.root.visibility = View.GONE

                if (state is SpeakersUiState.Searching) {
                    binding.groupSearchingInclude.root.visibility = View.VISIBLE
                    binding.groupConnectedInclude.root.visibility = View.GONE
                    binding.groupNoSpeakersInclude.root.visibility = View.GONE
                } else if (state is SpeakersUiState.NoServer) {
                    binding.groupNoServerInclude.root.visibility = View.VISIBLE
                    binding.groupConnectedInclude.root.visibility = View.GONE
                    binding.groupNoSpeakersInclude.root.visibility = View.GONE
                } else if (state is SpeakersUiState.Connected) {
                    discoveredHost = state.host
                    discoveredPort = state.port
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
