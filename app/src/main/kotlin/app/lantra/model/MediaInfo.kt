package app.lantra.model

import android.graphics.Bitmap
import android.media.session.MediaController

data class MediaInfo(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArt: Bitmap?,
    val controller: MediaController?
)
