package app.lantra.media

import app.lantra.service.MediaNotificationListenerService

object MediaNotifier {
    var listener: MediaNotificationListenerService? = null
    var onListenerReady: (() -> Unit)? = null
}