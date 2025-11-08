package app.lantra.ui.speakers

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lantra.R
import app.lantra.databinding.ItemSpeakerBinding
import app.lantra.model.SpeakerDevice
import com.google.android.material.color.MaterialColors

class SpeakerAdapter(
    private val onToggleCasting: (SpeakerDevice, Boolean) -> Unit
) : ListAdapter<SpeakerDevice, SpeakerAdapter.SpeakerViewHolder>(SpeakerItemDiff()) {

    inner class SpeakerViewHolder(val binding: ItemSpeakerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: SpeakerDevice) {
            val context = binding.root.context
            binding.tvAppName.text = device.application
            binding.tvBrowser.text = device.browser

            // Update the card's appearance based on the casting state
            if (device.isCasting) {
                // Card background and text color
                binding.cardView.setBackgroundResource(R.drawable.bg_button_gradient)
                binding.tvAppName.setTextColor(Color.WHITE)
                binding.tvBrowser.setTextColor(Color.WHITE)

                // Icon and icon background
                binding.iconBackground.setBackgroundResource(R.drawable.bg_icon_casting)
                binding.ivVolumeIcon.setImageResource(R.drawable.ic_volume_up_white_24dp)
            } else {
                // Card background and text color (theme aware)
                binding.cardView.setCardBackgroundColor(
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
                )
                binding.tvAppName.setTextColor(
                    MaterialColors.getColor(context, android.R.attr.textColorPrimary, Color.BLACK)
                )
                binding.tvBrowser.setTextColor(
                    MaterialColors.getColor(context, android.R.attr.textColorSecondary, Color.GRAY)
                )

                // Icon and icon background
                binding.iconBackground.setBackgroundResource(R.drawable.bg_icon_default)
                binding.ivVolumeIcon.setImageResource(R.drawable.ic_volume_off_lavender_24dp)
            }

            // Prevent old listener from firing during binding to avoid loops
            binding.toggleCasting.setOnCheckedChangeListener(null)
            binding.toggleCasting.isChecked = device.isCasting

            binding.toggleCasting.setOnCheckedChangeListener { _, isChecked ->
                onToggleCasting(device, isChecked)
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
}