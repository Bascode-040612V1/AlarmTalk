package com.yourapp.test.alarm

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class AlarmStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("alarm_storage", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_ALARMS = "saved_alarms"
    }
    
    data class SerializableAlarm(
        val id: Int,
        val time: String,
        val isEnabled: Boolean,
        val hour: Int,
        val minute: Int,
        val repeatDays: Set<Int>,
        val title: String,
        val note: String,
        val ringtoneUriString: String?,
        val ringtoneName: String,
        val snoozeMinutes: Int,
        val voiceRecordingPath: String?,
        val hasVoiceOverlay: Boolean,
        val alarmVolume: Float, // Single alarm volume setting
        val hasTtsOverlay: Boolean,
        val hasVibration: Boolean
    )
    
    fun saveAlarms(alarms: List<AlarmItem>) {
        try {
            val serializableAlarms = alarms.map { alarm ->
                SerializableAlarm(
                    id = alarm.id,
                    time = alarm.time,
                    isEnabled = alarm.isEnabled,
                    hour = alarm.calendar.get(Calendar.HOUR_OF_DAY),
                    minute = alarm.calendar.get(Calendar.MINUTE),
                    repeatDays = alarm.repeatDays,
                    title = alarm.title,
                    note = alarm.note,
                    ringtoneUriString = alarm.ringtoneUri?.toString(),
                    ringtoneName = alarm.ringtoneName,
                    snoozeMinutes = alarm.snoozeMinutes,
                    voiceRecordingPath = alarm.voiceRecordingPath,
                    hasVoiceOverlay = alarm.hasVoiceOverlay,
                    alarmVolume = alarm.alarmVolume, // Use single alarm volume
                    hasTtsOverlay = alarm.hasTtsOverlay,
                    hasVibration = alarm.hasVibration
                )
            }
            
            val json = gson.toJson(serializableAlarms)
            sharedPreferences.edit()
                .putString(KEY_ALARMS, json)
                .apply()
        } catch (e: Exception) {
            Log.e("AlarmStorage", "Error saving alarms", e)
        }
    }
    
    fun loadAlarms(): List<AlarmItem> {
        return try {
            val json = sharedPreferences.getString(KEY_ALARMS, null) ?: return emptyList()
            
            val type = object : TypeToken<List<SerializableAlarm>>() {}.type
            val serializableAlarms: List<SerializableAlarm> = gson.fromJson(json, type)
            
            serializableAlarms.mapNotNull { serializable ->
                try {
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, serializable.hour)
                        set(Calendar.MINUTE, serializable.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        
                        // For non-repeating alarms, we should preserve the original date
                        // This logic was incorrect - we don't need to adjust the date here
                        // The date adjustment should happen when scheduling the alarm, not when loading it
                    }
                    
                    AlarmItem(
                        id = serializable.id,
                        time = serializable.time,
                        isEnabled = serializable.isEnabled,
                        calendar = calendar,
                        repeatDays = serializable.repeatDays,
                        title = serializable.title,
                        note = serializable.note,
                        ringtoneUri = serializable.ringtoneUriString?.let { Uri.parse(it) },
                        ringtoneName = serializable.ringtoneName,
                        snoozeMinutes = serializable.snoozeMinutes,
                        voiceRecordingPath = serializable.voiceRecordingPath,
                        hasVoiceOverlay = serializable.hasVoiceOverlay,
                        alarmVolume = serializable.alarmVolume, // Use single alarm volume
                        hasTtsOverlay = serializable.hasTtsOverlay,
                        hasVibration = serializable.hasVibration
                    )
                } catch (e: Exception) {
                    Log.e("AlarmStorage", "Error deserializing alarm: ${serializable.id}", e)
                    null // Skip this alarm if there's an error
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmStorage", "Error loading alarms", e)
            // If there's an error loading, return empty list
            emptyList()
        }
    }
    
    fun clearAlarms() {
        try {
            sharedPreferences.edit()
                .remove(KEY_ALARMS)
                .apply()
        } catch (e: Exception) {
            Log.e("AlarmStorage", "Error clearing alarms", e)
        }
    }
}