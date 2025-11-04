package app.lantra.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeakerCastToggle(
    val deviceId: String,
    val enabled: Boolean

)
