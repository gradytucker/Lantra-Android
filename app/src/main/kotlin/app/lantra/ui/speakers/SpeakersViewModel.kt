package app.lantra.ui.speakers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lantra.model.SpeakerDevice
import app.lantra.network.ServerDiscovery
import app.lantra.network.SpeakerDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SpeakersViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SpeakersUiState>(SpeakersUiState.Searching)
    val uiState: StateFlow<SpeakersUiState> = _uiState

    private val _speakers = MutableStateFlow<List<SpeakerDevice>>(emptyList())
    val speakers: StateFlow<List<SpeakerDevice>> = _speakers

    private val context = getApplication<Application>()
    private var speakerDiscovery: SpeakerDiscovery? = null

    private var serverDiscovery: ServerDiscovery? = null

    // Server discovery
    fun startServerDiscovery() {
        viewModelScope.launch {
            _uiState.value = SpeakersUiState.Searching
            val discovery = ServerDiscovery(context)
            val result = discovery.findServer(timeoutMs = 5000)
            if (result != null) {
                val (host, port) = result
                _uiState.value = SpeakersUiState.Connected(host, port)
                startSpeakerDiscovery(host, port)
            } else {
                _uiState.value = SpeakersUiState.NoServer
            }
        }
    }

    // Speaker discovery
    private fun startSpeakerDiscovery(host: String, port: Int) {
        speakerDiscovery = SpeakerDiscovery(host, port, viewModelScope)
        speakerDiscovery?.connect()
        viewModelScope.launch {
            speakerDiscovery?.speakers?.collect {
                _speakers.value = it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speakerDiscovery?.close()
    }
}
