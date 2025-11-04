package app.lantra.ui.speakers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.lantra.databinding.ItemSpeakerBinding
import app.lantra.model.SpeakerDevice

class SpeakerAdapter(
    var items: List<SpeakerDevice>,
    private val onToggleCasting: (SpeakerDevice, Boolean) -> Unit
) : RecyclerView.Adapter<SpeakerAdapter.SpeakerViewHolder>() {

    inner class SpeakerViewHolder(val binding: ItemSpeakerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: SpeakerDevice) {
            binding.tvAppName.text = device.application
            binding.tvBrowser.text = device.browser
            binding.toggleCasting.isChecked = device.isCasting

            binding.toggleCasting.setOnCheckedChangeListener { _, isChecked ->
                onToggleCasting(device, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpeakerViewHolder {
        val binding =
            ItemSpeakerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpeakerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpeakerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun getCurrentItems(): List<SpeakerDevice> = items

    fun submitList(newItems: List<SpeakerDevice>) {
        items = newItems
        notifyDataSetChanged()
    }
}
