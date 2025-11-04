package app.lantra.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerMessage(
    val type: String,
    val data: JsonElement
)
