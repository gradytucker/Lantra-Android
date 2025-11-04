package app.lantra.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeakerDevice(
    val id: String,
    val application: String,
    val browser: String,
    var isCasting: Boolean = false
)