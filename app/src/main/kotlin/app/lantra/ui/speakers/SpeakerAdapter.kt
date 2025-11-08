package app.lantra.ui.speakers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lantra.databinding.ItemSpeakerBinding
import app.lantra.model.SpeakerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SpeakerAdapter(
    private val onToggleCasting: (SpeakerDevice, Boolean) -> Unit
) : ListAdapter<SpeakerDevice, SpeakerAdapter.SpeakerViewHolder>(SpeakerItemDiff()) {

    private val _anyCasting = MutableStateFlow(false)
    val anyCasting: StateFlow<Boolean> = _anyCasting

    inner class SpeakerViewHolder(val binding: ItemSpeakerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: SpeakerDevice) {
            binding.tvAppName.text = device.application
            binding.tvBrowser.text = device.browser

            // Prevent old listener from firing during binding
            binding.toggleCasting.setOnCheckedChangeListener(null)
            binding.toggleCasting.isChecked = device.isCasting

            binding.toggleCasting.setOnCheckedChangeListener { _, isChecked ->
                onToggleCasting(device, isChecked)
                _anyCasting.value =
                    currentList.any { it.isCasting || (it.id == device.id && isChecked) }
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

    override fun submitList(list: List<SpeakerDevice>?) {
        super.submitList(list)
        _anyCasting.value = currentList.any { it.isCasting }
    }
}
