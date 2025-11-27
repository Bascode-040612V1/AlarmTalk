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
    private val onPlayClick: (VoiceRecording) -> Unit,
    private val onDeleteClick: (VoiceRecording) -> Unit
) : RecyclerView.Adapter<VoiceRecordingAdapter.VoiceRecordingViewHolder>() {

    private var selectedPosition = -1
    private val selectedItems = mutableSetOf<VoiceRecording>()

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

            // Set click listeners
            itemView.setOnClickListener {
                if (selectedItems.contains(voiceRecording)) {
                    selectedItems.remove(voiceRecording)
                } else {
                    selectedItems.add(voiceRecording)
                }
                notifyItemChanged(adapterPosition)
                onItemClick(voiceRecording)
            }

            imagePlayButton.setOnClickListener {
                onPlayClick(voiceRecording)
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
                    R.id.action_select -> {
                        // Toggle selection for this item
                        if (selectedItems.contains(voiceRecording)) {
                            selectedItems.remove(voiceRecording)
                        } else {
                            selectedItems.add(voiceRecording)
                        }
                        notifyItemChanged(adapterPosition)
                        onItemClick(voiceRecording)
                        true
                    }
                    R.id.action_select_all -> {
                        // Select all items
                        selectedItems.clear()
                        selectedItems.addAll(voiceRecordings)
                        notifyDataSetChanged()
                        true
                    }
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
    
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(voiceRecordings)
        notifyDataSetChanged()
    }
    
    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    fun removeRecording(voiceRecording: VoiceRecording) {
        val position = voiceRecordings.indexOf(voiceRecording)
        if (position != -1) {
            (voiceRecordings as MutableList).removeAt(position)
            selectedItems.remove(voiceRecording)
            notifyItemRemoved(position)
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
    }
}