package app.lantra.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Listens for media notifications from other apps.
 */
class MediaNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val pkg = it.packageName
            val extras = it.notification.extras
            val title = extras.getString(NotificationCompat.EXTRA_TITLE)
            val artist = extras.getString(NotificationCompat.EXTRA_TEXT)
            Log.d("MediaNotification", "App: $pkg, Track: $title by $artist")

            // Here you could notify your fragment/service about new track
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: handle removed notifications
    }
}