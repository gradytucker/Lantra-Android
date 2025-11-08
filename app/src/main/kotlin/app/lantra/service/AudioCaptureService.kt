package app.lantra.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.lantra.audio.AudioStreamer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "AudioCaptureChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_SERVER_HOST = "server_host"
        const val EXTRA_SERVER_PORT = "server_port"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()
    }

    private var streamer: AudioStreamer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
            ?: return START_NOT_STICKY

        val host = intent.getStringExtra(EXTRA_SERVER_HOST)
        val port = intent.getIntExtra(EXTRA_SERVER_PORT, -1)

        if (host == null || port == -1) {
            Log.e("AudioCaptureService", "Missing server host/port; cannot stream.")
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(host, port),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data)

        if (projection != null) {
            streamer = AudioStreamer(applicationContext, host, port, projection)
            streamer?.start()
            Log.i("AudioCaptureService", "Streaming started to $host:$port")
        } else {
            Log.e("AudioCaptureService", "MediaProjection is null; cannot start streaming.")
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        streamer?.stop()
        _isRunning.value = false
        Log.d("AudioCaptureService", "Service destroyed, streaming stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(host: String, port: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Streaming Active")
            .setContentText("Streaming audio to $host:$port")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Streaming",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}