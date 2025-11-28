package com.yourapp.test.alarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class VoiceRecordingAdapter(
    private val voiceRecordings: List<VoiceRecording>,
    private val onItemClick: (VoiceRecording) -> Unit,
    private val onPlayClick: (VoiceRecording, Boolean) -> Unit, // Updated to include play/pause boolean
    private val onDeleteClick: (VoiceRecording) -> Unit,
    private val alarmVolume: Float = 1.0f, // Added alarm volume parameter
    private val onSelectionChanged: ((Int) -> Unit)? = null // Callback for selection changes
) : RecyclerView.Adapter<VoiceRecordingAdapter.VoiceRecordingViewHolder>() {

    private var selectedPosition = -1
    private val selectedItems = mutableSetOf<VoiceRecording>()
    private var currentlyPlaying: VoiceRecording? = null
    private var isPlaying = false

    inner class VoiceRecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val textName: TextView = itemView.findViewById(R.id.textVoiceName)
        val textDate: TextView = itemView.findViewById(R.id.textVoiceDate)
        val textDuration: TextView = itemView.findViewById(R.id.textVoiceDuration)
        val imagePlayButton: ImageView = itemView.findViewById(R.id.imagePlayButton)
        val buttonVoiceMenu: ImageButton = itemView.findViewById(R.id.buttonVoiceMenu)

        fun bind(voiceRecording: VoiceRecording) {
            textName.text = voiceRecording.name
            textDate.text = voiceRecording.date
            textDuration.text = voiceRecording.duration

            // Highlight selected item
            val isSelected = selectedItems.contains(voiceRecording)
            cardView.strokeWidth = if (isSelected) 3 else 1
            cardView.strokeColor = if (isSelected) 
                itemView.context.getColor(R.color.accent_red) 
            else 
                itemView.context.getColor(R.color.border_color)

            // Update play button icon based on playing state
            if (currentlyPlaying == voiceRecording && isPlaying) {
                imagePlayButton.setImageResource(R.drawable.ic_pause)
            } else {
                imagePlayButton.setImageResource(R.drawable.ic_play)
            }

            // Set click listeners
            itemView.setOnClickListener {
                if (selectedItems.contains(voiceRecording)) {
                    selectedItems.remove(voiceRecording)
                } else {
                    selectedItems.add(voiceRecording)
                }
                notifyItemChanged(adapterPosition)
                onSelectionChanged?.invoke(selectedItems.size)
                onItemClick(voiceRecording)
            }

            imagePlayButton.setOnClickListener {
                // If this is the currently playing recording, pause it
                if (currentlyPlaying == voiceRecording && isPlaying) {
                    onPlayClick(voiceRecording, false) // Pause
                    isPlaying = false
                } else {
                    // Stop currently playing if different recording
                    if (currentlyPlaying != null && isPlaying) {
                        onPlayClick(currentlyPlaying!!, false) // Stop
                    }
                    
                    // Set this as currently playing and play it
                    currentlyPlaying = voiceRecording
                    isPlaying = true
                    onPlayClick(voiceRecording, true) // Play
                }
                notifyDataSetChanged()
            }
            
            buttonVoiceMenu.setOnClickListener {
                showPopupMenu(it, voiceRecording)
            }
        }
        
        private fun showPopupMenu(view: View, voiceRecording: VoiceRecording) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.menuInflater.inflate(R.menu.voice_recording_item_menu, popupMenu.menu)
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_delete -> {
                        onDeleteClick(voiceRecording)
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
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
    
    fun getSelectedRecordings(): Set<VoiceRecording> {
        return selectedItems.toSet()
    }
    
    fun removeRecording(voiceRecording: VoiceRecording) {
        val position = voiceRecordings.indexOf(voiceRecording)
        if (position != -1) {
            (voiceRecordings as MutableList).removeAt(position)
            selectedItems.remove(voiceRecording)
            notifyItemRemoved(position)
            onSelectionChanged?.invoke(selectedItems.size)
        }
    }
    
    fun removeRecordings(voiceRecordings: List<VoiceRecording>) {
        for (recording in voiceRecordings) {
            val position = this.voiceRecordings.indexOf(recording)
            if (position != -1) {
                (this.voiceRecordings as MutableList).removeAt(position)
                selectedItems.remove(recording)
                notifyItemRemoved(position)
            }
        }
        onSelectionChanged?.invoke(selectedItems.size)
    }
    
    fun stopPlayback() {
        if (currentlyPlaying != null && isPlaying) {
            isPlaying = false
            currentlyPlaying = null
            notifyDataSetChanged()
        }
    }
}