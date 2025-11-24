package app.lantra.model

import kotlinx.serialization.Serializable

@Serializable
data class SourceDevice(
    val name: String,
    val type: String,
)
