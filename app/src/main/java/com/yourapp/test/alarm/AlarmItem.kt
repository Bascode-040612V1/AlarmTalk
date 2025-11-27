package com.yourapp.test.alarm

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.util.*

data class AlarmItem(
    val id: Int,
    val time: String,
    var isEnabled: Boolean,
    val calendar: Calendar,
    val repeatDays: Set<Int> = emptySet(), // Calendar.SUNDAY, MONDAY, etc.
    val title: String = "Alarm Title",
    val note: String = "",
    val ringtoneUri: Uri? = null,
    val ringtoneName: String = "Default",
    val snoozeMinutes: Int = 10,
    val voiceRecordingPath: String? = null, // Path to recorded voice file
    val hasVoiceOverlay: Boolean = false, // Whether to overlay voice on ringtone
    val alarmVolume: Float = 0.8f, // Single alarm volume from 0.0 to 1.0, default 80%
    val hasTtsOverlay: Boolean = false, // Whether to use TTS overlay for reading note
    val hasVibration: Boolean = true, // Whether to vibrate when alarm triggers, default enabled
    val allowSimultaneousPlayback: Boolean = false // Whether to allow TTS and voice recording to play simultaneously
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readInt(), // id
        parcel.readString() ?: "00:00", // time
        parcel.readInt() == 1, // isEnabled
        Calendar.getInstance().apply { 
            timeInMillis = parcel.readLong()
        }, // calendar
        parcel.createIntArray()?.toSet() ?: emptySet(), // repeatDays
        parcel.readString() ?: "Alarm Title", // title
        parcel.readString() ?: "", // note
        parseUri(parcel.readString()), // ringtoneUri - read as string and parse
        parcel.readString() ?: "Default", // ringtoneName
        parcel.readInt(), // snoozeMinutes
        parcel.readString(), // voiceRecordingPath
        parcel.readInt() == 1, // hasVoiceOverlay
        parcel.readFloat(), // alarmVolume
        parcel.readInt() == 1, // hasTtsOverlay
        parcel.readInt() == 1, // hasVibration
        parcel.readInt() == 1 // allowSimultaneousPlayback
    ) {
        // Secondary constructor body is empty
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(time)
        parcel.writeInt(if (isEnabled) 1 else 0)
        parcel.writeLong(calendar.timeInMillis)
        parcel.writeIntArray(repeatDays.toIntArray())
        parcel.writeString(title)
        parcel.writeString(note)
        // Write Uri as string to avoid Parcelable issues
        parcel.writeString(ringtoneUri?.toString())
        parcel.writeString(ringtoneName)
        parcel.writeInt(snoozeMinutes)
        parcel.writeString(voiceRecordingPath)
        parcel.writeInt(if (hasVoiceOverlay) 1 else 0)
        parcel.writeFloat(alarmVolume)
        parcel.writeInt(if (hasTtsOverlay) 1 else 0)
        parcel.writeInt(if (hasVibration) 1 else 0)
        parcel.writeInt(if (allowSimultaneousPlayback) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AlarmItem> {
        override fun createFromParcel(parcel: Parcel): AlarmItem {
            return AlarmItem(parcel)
        }

        override fun newArray(size: Int): Array<AlarmItem?> {
            return arrayOfNulls(size)
        }
        
        private fun parseUri(uriString: String?): Uri? {
            return if (uriString != null) Uri.parse(uriString) else null
        }
    }
    
    fun isRepeating(): Boolean = repeatDays.isNotEmpty()
    
    fun getRepeatDaysString(): String {
        if (repeatDays.isEmpty()) return "Once"
        
        val dayNames = mapOf(
            Calendar.SUNDAY to "Sun",
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )
        
        return when {
            repeatDays.size == 7 -> "Every day"
            repeatDays.containsAll(setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)) && repeatDays.size == 5 -> "Weekdays"
            repeatDays.containsAll(setOf(Calendar.SATURDAY, Calendar.SUNDAY)) && repeatDays.size == 2 -> "Weekends"
            else -> repeatDays.sortedBy { 
                when(it) {
                    Calendar.SUNDAY -> 7
                    else -> it
                }
            }.joinToString(", ") { dayNames[it] ?: "" }
        }
    }
}