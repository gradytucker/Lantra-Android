package app.lantra.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeakerDevice(
    val application: String,
    val browser: String
)