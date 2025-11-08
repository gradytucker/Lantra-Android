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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import app.lantra.R
import app.lantra.databinding.FragmentSpeakersBinding
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

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startProjection()
        } else {
            Toast.makeText(requireContext(), "Microphone permission required", Toast.LENGTH_SHORT).show()
            viewModel.revertCastingState()
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val startStreamIntent =
                Intent(requireContext(), AudioCaptureService::class.java).apply {
                    putExtra(AudioCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(AudioCaptureService.EXTRA_DATA, result.data)
                    putExtra("server_host", discoveredHost)
                    putExtra("server_port", discoveredPort)
                }
            ContextCompat.startForegroundService(requireContext(), startStreamIntent)
        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
            viewModel.revertCastingState()
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
        setupTitleGradient()

        if (viewModel.uiState.value is SpeakersUiState.Searching) viewModel.startServerDiscovery()

        return binding.root
    }

    private fun setupTitleGradient() {
        val title = binding.groupConnectedInclude.tvTitle
        title.post {
            val paint = title.paint
            val width = paint.measureText(title.text.toString())

            val startColor = MaterialColors.getColor(requireContext(), R.attr.gradientStartColor, Color.BLACK)
            val endColor = MaterialColors.getColor(requireContext(), R.attr.gradientEndColor, Color.BLACK)

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
            viewModel.toggleDeviceCasting(device, isCasting)
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

                updateHeader(speakers.count { it.isCasting })

                if (viewModel.uiState.value is SpeakersUiState.Connected) {
                    binding.groupNoSpeakersInclude.root.visibility =
                        if (speakers.isEmpty()) View.VISIBLE else View.GONE
                    binding.groupConnectedInclude.root.visibility =
                        if (speakers.isNotEmpty()) View.VISIBLE else View.GONE
                }

                val anyCasting = speakers.any { it.isCasting }
                val isStreaming = viewModel.isStreaming.value
                if (anyCasting && !isStreaming) {
                    checkMicAndStart()
                } else if (!anyCasting && isStreaming) {
                    stopStreaming()
                }
            }
        }
    }

    private fun updateHeader(castingCount: Int) {
        val subtitle = binding.groupConnectedInclude.tvSubtitle
        val icon = binding.groupConnectedInclude.ivSubtitleIcon

        if (castingCount > 0) {
            subtitle.text = resources.getQuantityString(R.plurals.casting_to_n_speakers, castingCount, castingCount)
            icon.setImageResource(R.drawable.ic_casting_24dp)
        } else {
            subtitle.text = getString(R.string.all_quiet)
            icon.setImageResource(R.drawable.ic_all_quiet_24dp)
        }
    }

    private fun checkMicAndStart() {
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
                binding.groupConnectedInclude.root.visibility = View.GONE
                binding.groupNoServerInclude.root.visibility = View.GONE
                binding.groupNoSpeakersInclude.root.visibility = View.GONE

                when (state) {
                    is SpeakersUiState.Searching -> {
                        binding.groupSearchingInclude.root.visibility = View.VISIBLE
                    }

                    is SpeakersUiState.Connected -> {
                        if (speakerAdapter.itemCount == 0) {
                            binding.groupNoSpeakersInclude.root.visibility = View.VISIBLE
                        } else {
                            binding.groupConnectedInclude.root.visibility = View.VISIBLE
                        }
                        discoveredHost = state.host
                        discoveredPort = state.port
                    }

                    is SpeakersUiState.NoServer -> {
                        binding.groupNoServerInclude.root.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}