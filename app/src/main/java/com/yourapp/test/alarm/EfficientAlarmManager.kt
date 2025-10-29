package com.yourapp.test.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.*

/**
 * Efficient alarm manager that optimizes battery consumption
 */
class EfficientAlarmManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EfficientAlarmManager"
        private const val ALARM_WINDOW_MS = 60000L // 1 minute window for inexact alarms
    }
    
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    
    /**
     * Schedule an alarm with battery optimization
     */
    fun scheduleEfficientAlarm(
        alarmId: Int,
        triggerTime: Long,
        alarmIntent: Intent,
        isExact: Boolean = true
    ): Boolean {
        return try {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (isExact) {
                scheduleExactAlarm(alarmId, triggerTime, pendingIntent)
            } else {
                scheduleInexactAlarm(alarmId, triggerTime, pendingIntent)
            }
            
            Log.d(TAG, "Scheduled alarm ID: $alarmId, Exact: $isExact, Time: ${Date(triggerTime)}")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception scheduling alarm ID: $alarmId", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm ID: $alarmId", e)
            false
        }
    }
    
    /**
     * Schedule an exact alarm (for critical alarms)
     */
    private fun scheduleExactAlarm(
        alarmId: Int,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For Android 6.0+, use setExactAndAllowWhileIdle for better reliability
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // For Android 4.4+, use setExact
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // For older versions, use set
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling exact alarm ID: $alarmId", e)
            throw e
        }
    }
    
    /**
     * Schedule an inexact alarm (for non-critical alarms to save battery)
     */
    private fun scheduleInexactAlarm(
        alarmId: Int,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            // Use inexact timing to allow Android to optimize battery usage
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                ALARM_WINDOW_MS,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling inexact alarm ID: $alarmId", e)
            // Fallback to exact alarm if window scheduling fails
            scheduleExactAlarm(alarmId, triggerTime, pendingIntent)
        }
    }
    
    /**
     * Cancel a scheduled alarm
     */
    fun cancelAlarm(alarmId: Int, alarmIntent: Intent) {
        try {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            
            Log.d(TAG, "Cancelled alarm ID: $alarmId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarm ID: $alarmId", e)
        }
    }
    
    /**
     * Batch cancel multiple alarms
     */
    fun cancelAlarms(alarmIds: List<Int>, baseIntent: Intent) {
        alarmIds.forEach { alarmId ->
            cancelAlarm(alarmId, baseIntent)
        }
    }
    
    /**
     * Get information about scheduled alarms
     */
    fun getAlarmInfo(): Map<String, Any> {
        val info = mutableMapOf<String, Any>()
        
        try {
            // Note: AlarmManager doesn't provide direct access to scheduled alarms
            // This is just placeholder for future implementation
            info["Status"] = "Active"
            info["Next Alarm"] = "Not directly accessible"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting alarm info", e)
            info["Error"] = e.message ?: "Unknown error"
        }
        
        return info
    }
    
    /**
     * Optimize existing alarms for better battery efficiency
     */
    fun optimizeExistingAlarms() {
        try {
            Log.d(TAG, "Optimizing existing alarms for battery efficiency")
            // This would be implemented based on specific app requirements
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing existing alarms", e)
        }
    }
}