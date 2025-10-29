package com.yourapp.test.alarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class VoiceRecordingAdapter(
    private val voiceRecordings: List<VoiceRecording>,
    private val onItemClick: (VoiceRecording) -> Unit,
    private val onPlayClick: (VoiceRecording) -> Unit
) : RecyclerView.Adapter<VoiceRecordingAdapter.VoiceRecordingViewHolder>() {

    private var selectedPosition = -1

    inner class VoiceRecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val textName: TextView = itemView.findViewById(R.id.textVoiceName)
        val textDate: TextView = itemView.findViewById(R.id.textVoiceDate)
        val textDuration: TextView = itemView.findViewById(R.id.textVoiceDuration)
        val imagePlayButton: ImageView = itemView.findViewById(R.id.imagePlayButton)

        fun bind(voiceRecording: VoiceRecording) {
            textName.text = voiceRecording.name
            textDate.text = voiceRecording.date
            textDuration.text = voiceRecording.duration

            // Highlight selected item
            cardView.strokeWidth = if (adapterPosition == selectedPosition) 3 else 1
            cardView.strokeColor = if (adapterPosition == selectedPosition) 
                itemView.context.getColor(R.color.accent_red) 
            else 
                itemView.context.getColor(R.color.border_color)

            // Set click listeners
            itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onItemClick(voiceRecording)
            }

            imagePlayButton.setOnClickListener {
                onPlayClick(voiceRecording)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceRecordingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voice_recording, parent, false)
        return VoiceRecordingViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoiceRecordingViewHolder, position: Int) {
        holder.bind(voiceRecordings[position])
    }

    override fun getItemCount(): Int = voiceRecordings.size

    fun getSelectedRecording(): VoiceRecording? {
        return if (selectedPosition >= 0 && selectedPosition < voiceRecordings.size) {
            voiceRecordings[selectedPosition]
        } else {
            null
        }
    }
}