package app.lantra.ui.speakers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lantra.databinding.ItemSpeakerBinding
import app.lantra.model.SpeakerDevice

class SpeakerAdapter : ListAdapter<SpeakerDevice, SpeakerAdapter.SpeakerViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SpeakerDevice>() {
            override fun areItemsTheSame(oldItem: SpeakerDevice, newItem: SpeakerDevice): Boolean {
                // Use a unique identifier if you have one; here we use combination of app + browser
                return oldItem.application == newItem.application && oldItem.browser == newItem.browser
            }

            override fun areContentsTheSame(
                oldItem: SpeakerDevice,
                newItem: SpeakerDevice
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpeakerViewHolder {
        val binding = ItemSpeakerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpeakerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpeakerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SpeakerViewHolder(private val binding: ItemSpeakerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(speaker: SpeakerDevice) {
            binding.tvAppName.text = speaker.application
            binding.tvBrowser.text = speaker.browser
        }
    }
}
