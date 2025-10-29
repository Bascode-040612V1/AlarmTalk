package com.yourapp.test.alarm

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AlarmAdapter(
    private val alarmList: MutableList<AlarmItem>,
    private val onDeleteClick: (AlarmItem) -> Unit,
    private val onToggleClick: (AlarmItem, Boolean) -> Unit,
    private val onEditClick: (AlarmItem) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    // Multi-select mode variables
    private var isMultiSelectMode = false
    private val selectedAlarms = mutableSetOf<AlarmItem>()
    private var multiSelectListener: (() -> Unit)? = null

    class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.textAlarmTime)
        val titleText: TextView = itemView.findViewById(R.id.textAlarmTitle)
        val repeatText: TextView = itemView.findViewById(R.id.textAlarmRepeat)
        val ringtoneText: TextView = itemView.findViewById(R.id.textAlarmRingtone)
        val enableSwitch: Switch = itemView.findViewById(R.id.switchAlarmEnabled)
        val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteAlarm)
        val selectCheckBox: CheckBox = itemView.findViewById(R.id.checkBoxSelectAlarm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarmList[position]
        
        // Format time according to system preference
        val displayTime = formatTimeForDisplay(holder.itemView.context, alarm.time)
        holder.timeText.text = displayTime
        
        holder.titleText.text = if (alarm.title.isNotEmpty()) alarm.title else "Alarm"
        holder.repeatText.text = alarm.getRepeatDaysString()
        holder.ringtoneText.text = alarm.ringtoneName
        holder.enableSwitch.isChecked = alarm.isEnabled
        
        // Handle multi-select mode
        if (isMultiSelectMode) {
            holder.selectCheckBox.visibility = View.VISIBLE
            holder.selectCheckBox.isChecked = selectedAlarms.contains(alarm)
            holder.deleteButton.visibility = View.GONE
            holder.enableSwitch.visibility = View.GONE
        } else {
            holder.selectCheckBox.visibility = View.GONE
            holder.deleteButton.visibility = View.VISIBLE
            holder.enableSwitch.visibility = View.VISIBLE
        }
        
        holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isMultiSelectMode) {
                onToggleClick(alarm, isChecked)
            }
        }
        
        holder.deleteButton.setOnClickListener {
            if (!isMultiSelectMode) {
                onDeleteClick(alarm)
            }
        }
        
        holder.selectCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedAlarms.add(alarm)
            } else {
                selectedAlarms.remove(alarm)
            }
            multiSelectListener?.invoke()
        }
        
        holder.itemView.setOnClickListener {
            if (isMultiSelectMode) {
                // Toggle selection in multi-select mode
                holder.selectCheckBox.isChecked = !holder.selectCheckBox.isChecked
            } else {
                // Edit alarm in normal mode
                onEditClick(alarm)
            }
        }
        
        holder.itemView.setOnLongClickListener {
            if (!isMultiSelectMode) {
                // Enter multi-select mode on long press
                isMultiSelectMode = true
                selectedAlarms.add(alarm)
                holder.selectCheckBox.isChecked = true
                notifyDataSetChanged()
                multiSelectListener?.invoke()
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = alarmList.size
    
    private fun formatTimeForDisplay(context: android.content.Context, time24Hour: String): String {
        val is24HourFormat = DateFormat.is24HourFormat(context)
        if (is24HourFormat) {
            return time24Hour
        }
        
        // Convert 24-hour to 12-hour format
        val parts = time24Hour.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1]
        
        return when {
            hour == 0 -> "12:$minute AM"
            hour < 12 -> "$hour:$minute AM"
            hour == 12 -> "12:$minute PM"
            else -> "${hour - 12}:$minute PM"
        }
    }
    
    // Multi-select mode methods
    fun isInMultiSelectMode(): Boolean = isMultiSelectMode
    
    fun getSelectedAlarms(): Set<AlarmItem> = selectedAlarms.toSet()
    
    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedAlarms.clear()
        notifyDataSetChanged()
    }
    
    fun setMultiSelectListener(listener: () -> Unit) {
        multiSelectListener = listener
    }
    
    fun selectAll() {
        selectedAlarms.clear()
        selectedAlarms.addAll(alarmList)
        notifyDataSetChanged()
        multiSelectListener?.invoke()
    }
    
    fun deselectAll() {
        selectedAlarms.clear()
        notifyDataSetChanged()
        multiSelectListener?.invoke()
    }
}