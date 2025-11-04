package app.lantra.network

import android.util.Log
import app.lantra.model.ServerMessage
import app.lantra.model.SpeakerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException

class SpeakerDiscovery(
    private val serverHost: String,
    private val serverPort: Int,
    private val scope: CoroutineScope
) {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var job: Job? = null

    private val _speakers = MutableStateFlow<List<SpeakerDevice>>(emptyList())
    val speakers: StateFlow<List<SpeakerDevice>> = _speakers

    private val json = Json { ignoreUnknownKeys = true }

    /** Start connecting and listening for data */
    fun connect() {
        if (job?.isActive == true) return // Already running

        job = scope.launch(Dispatchers.IO) {
            try {
                Log.d("SpeakerDiscovery", "Connecting to $serverHost:$serverPort")
                socket = Socket().apply {
                    connect(InetSocketAddress(serverHost, serverPort), 5000)
                }
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                Log.d("SpeakerDiscovery", "Connected to $serverHost:$serverPort")

                var line: String? = null;
                while (socket?.isConnected == true && reader?.readLine()
                        .also { line = it } != null
                ) {
                    line?.let { jsonString ->
                        try {
                            val message =
                                json.decodeFromString<ServerMessage>(jsonString)
                            Log.d("SpeakerDiscovery", message.toString());
                            when (message.type) {
                                "device_list" -> {
                                    _speakers.value =
                                        json.decodeFromJsonElement<List<SpeakerDevice>>(
                                            message.data
                                        )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SpeakerDiscovery", "Failed to parse speaker JSON: ${e.message}")
                        }
                    }
                }
            } catch (e: SocketException) {
                Log.e("SpeakerDiscovery", "Socket exception: ${e.message}")
            } catch (e: Exception) {
                Log.e("SpeakerDiscovery", "Connection error: ${e.message}")
            } finally {
                close()
            }
        }
    }

    /** Close socket and cancel reading job */
    fun close() {
        job?.cancel()
        try {
            reader?.close()
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        reader = null
        _speakers.value = emptyList()
        Log.d("SpeakerDiscovery", "Disconnected")
    }
}
