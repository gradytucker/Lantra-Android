package app.lantra.network

import android.util.Log
import app.lantra.model.ServerMessage
import app.lantra.model.SpeakerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException

class CastControlClient(
    private val serverHost: String,
    private val serverPort: Int,
    private val scope: CoroutineScope
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val _speakers = MutableStateFlow<List<SpeakerDevice>>(emptyList())
    val speakers: StateFlow<List<SpeakerDevice>> = _speakers

    fun connect() {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d("CastControlClient", "Connecting to $serverHost:$serverPort")
                socket = Socket().apply { connect(InetSocketAddress(serverHost, serverPort), 5000) }
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                writer = OutputStreamWriter(socket!!.getOutputStream())
                Log.d("CastControlClient", "Connected")

                // Listen for messages from server
                while (socket?.isConnected == true) {
                    val line = reader?.readLine() ?: break
                    // parse JSON into ControlMessage
                    val msg = parseMessage(line)
                    handleServerMessage(msg)
                }
            } catch (e: SocketException) {
                Log.e("CastControlClient", "Socket exception: ${e.message}")
            } catch (e: Exception) {
                Log.e("CastControlClient", "Connection error: ${e.message}")
            } finally {
                close()
            }
        }
    }

    private fun parseMessage(jsonString: String): ServerMessage {
        return json.decodeFromString<ServerMessage>(jsonString);
    }

    private fun handleServerMessage(message: ServerMessage) {
        when (message.type) {
            "device_list" -> {
                val devices = message.data.let {
                    try {
                        json.decodeFromJsonElement<List<SpeakerDevice>>(it)
                    } catch (e: Exception) {
                        Log.e("CastControlClient", "Failed to decode device list: ${e.message}")
                        emptyList()
                    }
                }
                _speakers.value = devices
            }

            else -> Log.d("CastControlClient", "Unhandled message type: ${message.type}")
        }
    }

    fun sendMessage(msg: ServerMessage) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = serializeMessage(msg)
                writer?.write("$json\n")
                writer?.flush()
            } catch (e: Exception) {
                Log.e("CastControlClient", "Send failed: ${e.message}")
            }
        }
    }

    private fun serializeMessage(msg: ServerMessage): String {
        return Json.encodeToString(ServerMessage.serializer(), msg)
    }

    fun close() {
        try {
            reader?.close()
            writer?.close()
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        reader = null
        writer = null
        Log.d("CastControlClient", "Disconnected")
    }
}
