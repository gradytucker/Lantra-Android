package app.lantra.ui.speakers

sealed class SpeakersUiState {
    object Searching : SpeakersUiState()
    data class Connected(val host: String, val port: Int) : SpeakersUiState()
    object NoServer : SpeakersUiState()
}
