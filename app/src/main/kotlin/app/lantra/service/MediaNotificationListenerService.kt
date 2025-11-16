package app.lantra.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import app.lantra.media.MediaNotifier
import app.lantra.model.MediaInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class MediaNotificationListenerService : NotificationListenerService(), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> get() = _mediaInfo

    private var currentController: MediaController? = null
    private var positionUpdater: Job? = null

    override fun onListenerConnected() {
        MediaNotifier.listener = this
        updateCurrentMediaSession()
        MediaNotifier.onListenerReady?.invoke()
    }

    override fun onListenerDisconnected() {
        MediaNotifier.listener = null
        currentController?.unregisterCallback(mediaControllerCallback)
        currentController = null
        stopPositionUpdates()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        if (sbn.packageName == packageName) return
        updateCurrentMediaSession()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        updateCurrentMediaSession()
    }

    private fun updateCurrentMediaSession() {
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        val sessions = try {
            mediaSessionManager.getActiveSessions(
                ComponentName(this, MediaNotificationListenerService::class.java)
            )
        } catch (_: SecurityException) {
            return
        }

        if (sessions.isEmpty()) return

        // Pick the currently playing session (most recent) or fallback
        val controller = sessions
            .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            .maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            ?: sessions.firstOrNull { it.playbackState?.state != PlaybackState.STATE_NONE }
            ?: return

        if (controller != currentController) {
            currentController?.unregisterCallback(mediaControllerCallback)
            controller.registerCallback(mediaControllerCallback)
            currentController = controller
            MediaNotifier.onListenerReady?.invoke()
        }

        val metadata = controller.metadata
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = controller.playbackState?.currentPosition() ?: 0L

        _mediaInfo.value = MediaInfo(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            albumArt = albumArt,
            controller = controller,
            duration = duration,
            position = position
        )

        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) startPositionUpdates()
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val current = _mediaInfo.value ?: return

            val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            val duration =
                metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: current.duration

            _mediaInfo.value = current.copy(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: current.title,
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: current.artist,
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: current.album,
                albumArt = albumArt ?: current.albumArt,
                duration = duration
            )
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val current = _mediaInfo.value ?: return
            val position = state?.currentPosition() ?: 0L
            _mediaInfo.value = current.copy(controller = current.controller, position = position)

            if (state?.state == PlaybackState.STATE_PLAYING) startPositionUpdates()
            else stopPositionUpdates()
        }
    }

    private fun startPositionUpdates() {
        positionUpdater?.cancel()
        val controller = currentController ?: return
        positionUpdater = launch {
            while (isActive) {
                val state = controller.playbackState
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    val current = _mediaInfo.value ?: continue
                    _mediaInfo.value = current.copy(position = state.currentPosition())
                }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdater?.cancel()
        positionUpdater = null
    }

    private fun PlaybackState.currentPosition(): Long {
        val timeDelta = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        return (position + (timeDelta * playbackSpeed)).toLong().coerceAtLeast(0L)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
