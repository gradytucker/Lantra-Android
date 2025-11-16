package app.lantra.ui.player

import android.media.session.MediaController
import androidx.lifecycle.ViewModel
import app.lantra.model.MediaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel : ViewModel() {

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> get() = _mediaInfo

    private val _duration = MutableStateFlow(0L)

    private val _position = MutableStateFlow(0L)

    fun setMediaInfo(info: MediaInfo) {
        _mediaInfo.value = info
        _duration.value = info.duration
        _position.value = info.position
    }

    fun togglePlayback() {
        val controller: MediaController = _mediaInfo.value?.controller ?: return
        val state = controller.playbackState?.state ?: return
        if (state == android.media.session.PlaybackState.STATE_PLAYING)
            controller.transportControls.pause()
        else
            controller.transportControls.play()
    }

    fun next() {
        _mediaInfo.value?.controller?.transportControls?.skipToNext()
    }

    fun prev() {
        _mediaInfo.value?.controller?.transportControls?.skipToPrevious()
    }
}
