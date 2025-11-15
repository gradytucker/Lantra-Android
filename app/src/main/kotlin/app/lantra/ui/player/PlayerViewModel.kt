package app.lantra.ui.player

import androidx.lifecycle.ViewModel
import app.lantra.media.MediaNotifier
import app.lantra.model.MediaInfo
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel : ViewModel() {

    // Always reflect the latest MediaNotificationListenerService state
    val mediaInfo: StateFlow<MediaInfo?> = MediaNotifier.listener?.mediaInfo
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)

    fun togglePlayback() {
        mediaInfo.value?.controller?.let { controller ->
            val state = controller.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
    }

    fun next() {
        mediaInfo.value?.controller?.transportControls?.skipToNext()
    }

    fun prev() {
        mediaInfo.value?.controller?.transportControls?.skipToPrevious()
    }
}
