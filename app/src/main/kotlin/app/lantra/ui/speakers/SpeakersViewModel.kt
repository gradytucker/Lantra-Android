package app.lantra.ui.speakers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lantra.model.ServerMessage
import app.lantra.model.SpeakerCastToggle
import app.lantra.model.SpeakerDevice
import app.lantra.network.CastControlClient
import app.lantra.network.ServerDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class SpeakersViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SpeakersUiState>(SpeakersUiState.Searching)
    val uiState: StateFlow<SpeakersUiState> = _uiState

    private val _speakers = MutableStateFlow<List<SpeakerDevice>>(emptyList())
    val speakers: StateFlow<List<SpeakerDevice>> = _speakers

    private val context = getApplication<Application>()
    private var castControlClient: CastControlClient? = null

    fun startServerDiscovery() {
        viewModelScope.launch {
            _uiState.value = SpeakersUiState.Searching
            val audioDiscovery = ServerDiscovery(context, "_lantra-audio._tcp")
            val controlDiscovery = ServerDiscovery(context, "_lantra-control._tcp")
            val audioServer = audioDiscovery.findServer(timeoutMs = 5000)
            val controlServer = controlDiscovery.findServer(timeoutMs = 5000)
            if (audioServer != null && controlServer != null) {
                val (audioHost, audioPort) = audioServer
                val (controlHost, controlPort) = controlServer
                _uiState.value = SpeakersUiState.Connected(audioHost, audioPort)
                startCastControlClient(controlHost, controlPort)
            } else {
                _uiState.value = SpeakersUiState.NoServer
            }
        }
    }

    private fun startCastControlClient(host: String, port: Int) {
        if (castControlClient != null) return
        castControlClient = CastControlClient(host, port, viewModelScope)
        castControlClient?.connect()

        viewModelScope.launch {
            castControlClient?.speakers?.collect { devices ->
                val merged = devices.map { newDevice ->
                    val existing = _speakers.value.find { it.id == newDevice.id }
                    if (existing != null) newDevice.copy(isCasting = existing.isCasting)
                    else newDevice
                }
                // Only emit if merged is different
                if (merged != _speakers.value) _speakers.value = merged
            }
        }
    }

    fun toggleDeviceCasting(device: SpeakerDevice, enabled: Boolean) {
        val current = _speakers.value
        val updated = current.map {
            if (it.id == device.id && it.isCasting != enabled) it.copy(isCasting = enabled) else it
        }

        // Only update if something changed
        if (updated != current) {
            _speakers.value = updated

            val toggle = SpeakerCastToggle(device.id, enabled)
            val controlMessage = ServerMessage(
                type = "toggle_cast",
                data = Json.encodeToJsonElement(toggle)
            )
            castControlClient?.sendMessage(controlMessage)
        }
    }

    override fun onCleared() {
        super.onCleared()
        castControlClient?.close()
    }
}
