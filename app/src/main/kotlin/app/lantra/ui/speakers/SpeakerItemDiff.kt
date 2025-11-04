package app.lantra.ui.speakers

import androidx.recyclerview.widget.DiffUtil
import app.lantra.model.SpeakerDevice

class SpeakerItemDiff : DiffUtil.ItemCallback<SpeakerDevice>() {
    override fun areItemsTheSame(oldItem: SpeakerDevice, newItem: SpeakerDevice): Boolean {
        return oldItem.id == newItem.id // assuming each device has a unique id
    }

    override fun areContentsTheSame(oldItem: SpeakerDevice, newItem: SpeakerDevice): Boolean {
        return oldItem == newItem
    }
}
