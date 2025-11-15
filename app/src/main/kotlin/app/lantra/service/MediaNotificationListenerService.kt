package app.lantra.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import app.lantra.media.MediaNotifier
import app.lantra.model.MediaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaNotificationListenerService : NotificationListenerService() {

    private val _mediaInfo = MutableStateFlow<MediaInfo?>(null)
    val mediaInfo: StateFlow<MediaInfo?> get() = _mediaInfo

    private var currentController: MediaController? = null

    override fun onListenerConnected() {
        MediaNotifier.listener = this
        updateCurrentMediaSession()
    }

    override fun onListenerDisconnected() {
        MediaNotifier.listener = null
        currentController?.unregisterCallback(mediaControllerCallback)
        currentController = null
    }

    // Called whenever a new notification arrives
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        // Ignore your own notifications
        if (sbn.packageName == packageName) return
        updateCurrentMediaSession()
    }

    private fun updateCurrentMediaSession() {
        val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        // Get all active media sessions
        val sessions = try {
            mediaSessionManager.getActiveSessions(
                ComponentName(
                    this,
                    MediaNotificationListenerService::class.java
                )
            )
        } catch (_: SecurityException) {
            // Notification listener permission not granted
            return
        }

        // Pick the first session that is actually playing or paused
        val controller =
            sessions.firstOrNull { it.playbackState?.state != PlaybackState.STATE_NONE } ?: return

        // If the controller changed, unregister previous callback
        if (controller != currentController) {
            currentController?.unregisterCallback(mediaControllerCallback)
            controller.registerCallback(mediaControllerCallback)
            currentController = controller
        }

        val metadata = controller.metadata
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        _mediaInfo.value = MediaInfo(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            albumArt = albumArt,
            controller = controller
        )
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val current = _mediaInfo.value ?: return
            val updatedArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            _mediaInfo.value = current.copy(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: current.title,
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: current.artist,
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: current.album,
                albumArt = updatedArt ?: current.albumArt
            )
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val current = _mediaInfo.value ?: return
            _mediaInfo.value = current.copy(controller = current.controller)
        }
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        updateCurrentMediaSession()
    }
}
