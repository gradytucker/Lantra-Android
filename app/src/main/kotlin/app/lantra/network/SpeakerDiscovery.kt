package app.lantra.network

import android.util.Log
import app.lantra.model.SpeakerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class SpeakerDiscovery(
    private val host: String,
    private val port: Int,
    private val scope: CoroutineScope
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null

    private val _speakers = MutableStateFlow<List<SpeakerDevice>>(emptyList())
    val speakers: StateFlow<List<SpeakerDevice>> = _speakers

    fun connect() {
        Log.d("SpeakerDiscovery", "Attempting to connect to host=$host, port=$port")

        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), 5000)
            }
            Log.d("SpeakerDiscovery", "Connected to $host:$port")
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            // Listen for incoming data
            scope.launch(Dispatchers.IO) {
                try {
                    var line: String? = null
                    while (socket?.isConnected == true && reader?.readLine()
                            .also { line = it } != null
                    ) {
                        line?.let { json ->
                            try {
                                // Deserialize JSON to list of SpeakerDevice
                                val list = parseSpeakerList(json)
                                _speakers.value = list
                            } catch (e: Exception) {
                                Log.e(
                                    "SpeakerDiscovery",
                                    "Failed to parse speaker data: ${e.message}"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SpeakerDiscovery", "Socket read error: ${e.message}")
                } finally {
                    close()
                }
            }
        } catch (e: Exception) {
            Log.e("SpeakerDiscovery", "Connection error: ${e.message}")
        }
    }

    private fun parseSpeakerList(json: String): List<SpeakerDevice> {
        // Simple manual parsing for demo purposes
        // Replace with your preferred JSON library, e.g., kotlinx.serialization or Moshi
        return if (json.contains("devices")) {
            // This is a placeholder — parse JSON into SpeakerDevice objects
            emptyList()
        } else {
            emptyList()
        }
    }

    fun close() {
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
