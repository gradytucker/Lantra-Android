package app.lantra.network.discovery

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@kotlinx.serialization.Serializable
data class SpeakerDevice(
    val application: String,
    val browser: String
)

class SpeakerDiscovery(
    private val wsUrl: String,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val _speakers = MutableStateFlow<List<SpeakerDevice>>(emptyList())
    val speakers: StateFlow<List<SpeakerDevice>> = _speakers

    fun connect() {
        if (webSocket != null) return

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.Default) {
                    try {
                        val list = kotlinx.serialization.json.Json.decodeFromString(
                            kotlinx.serialization.builtins.ListSerializer(
                                kotlinx.serialization.Serializable::class as kotlinx.serialization.KSerializer<SpeakerDevice>
                            ),
                            text
                        )
                        _speakers.value = list
                    } catch (e: Exception) {
                        Log.e("SpeakerDiscovery", "Parse error: ${e.message}")
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch { _speakers.value = emptyList() }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: okhttp3.Response?
            ) {
                Log.e("SpeakerDiscovery", "WS error: ${t.message}")
                scope.launch { _speakers.value = emptyList() }
            }
        })
    }

    fun close() {
        webSocket?.close(1000, "user closed")
        webSocket = null
    }
}
