package app.lantra.ui.speakers

import android.content.Context
import android.content.Intent
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
import app.lantra.databinding.FragmentSpeakersBinding
import app.lantra.service.AudioCaptureService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SpeakersFragment : Fragment() {

    private var _binding: FragmentSpeakersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SpeakersViewModel by activityViewModels()
    private lateinit var speakerAdapter: SpeakerAdapter

    private var discoveredHost: String? = null
    private var discoveredPort: Int? = null

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

        if (viewModel.uiState.value is SpeakersUiState.Searching) viewModel.startServerDiscovery()

        return binding.root
    }

    private fun setupRecyclerView(columns: Int = 2) {
        speakerAdapter = SpeakerAdapter { device, isCasting ->
            viewModel.toggleDeviceCasting(device, isCasting)
        }

        binding.rvSpeakers.apply {
            adapter = speakerAdapter
            layoutManager = GridLayoutManager(requireContext(), columns)
        }
    }

    private fun observeSpeakers() {
        lifecycleScope.launch {
            viewModel.speakers.collectLatest { speakers ->
                speakerAdapter.submitList(speakers.map { it.copy() })

                binding.groupNoSpeakers.visibility =
                    if (speakers.isEmpty()) View.VISIBLE else View.GONE
                binding.groupConnected.visibility =
                    if (speakers.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            speakerAdapter.anyCasting.collectLatest { anyCasting ->
                if (anyCasting) startCastingFlow() else stopStreaming()
            }
        }
    }

    private fun setupButtons() {
        binding.btnRetry.setOnClickListener { viewModel.startServerDiscovery() }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is SpeakersUiState.Searching -> {
                        binding.groupSearching.visibility = View.VISIBLE
                        binding.groupConnected.visibility = View.GONE
                        binding.groupNoServer.visibility = View.GONE
                        binding.groupNoSpeakers.visibility = View.GONE
                    }

                    is SpeakersUiState.Connected -> {
                        binding.groupConnected.visibility = View.VISIBLE
                        binding.groupNoServer.visibility = View.GONE
                        binding.groupSearching.visibility = View.GONE
                        discoveredHost = state.host
                        discoveredPort = state.port
                    }

                    is SpeakersUiState.NoServer -> {
                        binding.groupNoServer.visibility = View.VISIBLE
                        binding.groupConnected.visibility = View.GONE
                        binding.groupSearching.visibility = View.GONE
                        binding.groupNoSpeakers.visibility = View.GONE
                    }
                }
            }
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
