package com.yourapp.test.alarm

import android.content.Context
import android.text.format.DateFormat
import java.util.*

object TimeUtils {
    
    /**
     * Formats time according to system preference (12-hour or 24-hour format)
     */
    fun formatTimeForDisplay(context: Context, time24Hour: String): String {
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
}