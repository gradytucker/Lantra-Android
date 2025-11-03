package app.lantra.audio.streaming

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket

/**
 * Streams audio from apps using AudioPlaybackCapture or microphone fallback.
 */
class AudioStreamer(
    private val context: Context,
    private val serverIp: String,
    private val serverPort: Int,
    private val mediaProjection: MediaProjection
) {

    private var streamingJob: Job? = null
    private var isStreaming = false
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize =
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2;

    fun start() {
        if (isStreaming) return

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("AudioStreamer", "RECORD_AUDIO permission missing")
            return
        }

        isStreaming = true

        streamingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val audioRecord = AudioRecord.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                val socket = Socket(serverIp, serverPort)
                val outputStream: OutputStream = socket.getOutputStream()
                val buffer = ByteArray(bufferSize)

                audioRecord.startRecording()
                Log.d("AudioStreamer", "Streaming started")

                while (isStreaming && isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                        outputStream.flush()
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                outputStream.close()
                socket.close()
                Log.d("AudioStreamer", "Streaming stopped")

            } catch (e: Exception) {
                Log.e("AudioStreamer", "Error streaming audio: ${e.message}")
            }
        }
    }

    fun stop() {
        isStreaming = false
        streamingJob?.cancel()
    }
}