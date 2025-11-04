package app.lantra.ui.speakers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.recyclerview.widget.LinearLayoutManager
import app.lantra.databinding.FragmentSpeakersBinding
import app.lantra.service.AudioCaptureService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SpeakersFragment : Fragment() {

    private var _binding: FragmentSpeakersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SpeakersViewModel by activityViewModels()
    private val speakerAdapter = SpeakerAdapter()

    private var discoveredHost: String? = null
    private var discoveredPort: Int? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCastingFlow()
        else Toast.makeText(requireContext(), "Microphone permission required", Toast.LENGTH_SHORT)
            .show()
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

        if (viewModel.uiState.value is SpeakersUiState.Searching) {
            viewModel.startServerDiscovery()
        }

        return binding.root
    }

    // setup recycler view for speakers
    private fun setupRecyclerView() {
        binding.rvSpeakers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSpeakers.adapter = speakerAdapter
    }

    // observe speakers list
    private fun observeSpeakers() {
        lifecycleScope.launch {
            viewModel.speakers.collectLatest { speakers ->
                speakerAdapter.submitList(speakers)
            }
        }
    }

    // setup buttons
    private fun setupButtons() {
        binding.btnRetry.setOnClickListener { viewModel.startServerDiscovery() }
        binding.btnStart.setOnClickListener { checkMicPermissionAndStart() }
        binding.btnStop.setOnClickListener { stopStreaming() }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is SpeakersUiState.Searching -> {
                        binding.groupSearching.visibility = View.VISIBLE
                        binding.groupConnected.visibility = View.GONE
                        binding.groupNoServer.visibility = View.GONE
                    }

                    is SpeakersUiState.Connected -> {
                        binding.groupConnected.visibility = View.VISIBLE
                        binding.groupNoServer.visibility = View.GONE
                        binding.groupSearching.visibility = View.GONE
                        binding.tvStatus.text = "Found server at ${state.host}:${state.port}"
                        discoveredHost = state.host
                        discoveredPort = state.port
                    }

                    is SpeakersUiState.NoServer -> {
                        binding.groupConnected.visibility = View.GONE
                        binding.groupSearching.visibility = View.GONE
                        binding.groupNoServer.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun checkMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        } else {
            startCastingFlow()
        }
    }

    private fun startCastingFlow() {
        val manager =
            requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun stopStreaming() {
        requireContext().stopService(Intent(requireContext(), AudioCaptureService::class.java))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
