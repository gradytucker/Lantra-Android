package app.lantra.ui.speakers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lantra.network.ServerDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SpeakersViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SpeakersUiState>(SpeakersUiState.Searching)
    val uiState: StateFlow<SpeakersUiState> = _uiState

    private val context = getApplication<Application>()

    fun discoverServer() {
        viewModelScope.launch {
            _uiState.value = SpeakersUiState.Searching
            val discovery = ServerDiscovery(context)
            val result = withTimeoutOrNull(5000) { discovery.findServer() }
            if (result != null) {
                val (host, port) = result
                _uiState.value = SpeakersUiState.Connected(host.hostAddress, port)
            } else {
                _uiState.value = SpeakersUiState.NoServer
            }
        }
    }
}
