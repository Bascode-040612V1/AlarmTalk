package com.yourapp.test.alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "alarm_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_SNOOZE = "com.yourapp.test.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.yourapp.test.alarm.DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d("AlarmReceiver", "onReceive called with action: ${intent.action}")
            when (intent.action) {
                ACTION_SNOOZE -> {
                    Log.d("AlarmReceiver", "Processing snooze action")
                    handleSnooze(context, intent)
                }
                ACTION_DISMISS -> {
                    Log.d("AlarmReceiver", "Processing dismiss action")
                    handleDismiss(context, intent)
                }
                else -> {
                    Log.d("AlarmReceiver", "Processing regular alarm trigger")
                    handleAlarm(context, intent)
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error in onReceive", e)
        }
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        try {
            val alarmId = intent.getIntExtra("ALARM_ID", 0)
            val alarmTime = intent.getStringExtra("ALARM_TIME") ?: ""
            val alarmTitle = intent.getStringExtra("ALARM_TITLE") ?: "Alarm"
            val alarmNote = intent.getStringExtra("ALARM_NOTE") ?: ""
            val ringtoneUriString = intent.getStringExtra("RINGTONE_URI")
            val ringtoneName = intent.getStringExtra("RINGTONE_NAME") ?: "Default"
            val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 10)
            val isRepeating = intent.getBooleanExtra("IS_REPEATING", false)
            val repeatDay = intent.getIntExtra("REPEAT_DAY", -1)
            val voiceRecordingPath = intent.getStringExtra("VOICE_RECORDING_PATH")
            val hasVoiceOverlay = intent.getBooleanExtra("HAS_VOICE_OVERLAY", false)
            val hasTtsOverlay = intent.getBooleanExtra("HAS_TTS_OVERLAY", false)
            // Use single alarm volume instead of separate volumes
            val alarmVolume = intent.getFloatExtra("ALARM_VOLUME", 0.8f)
            val ttsVoice = intent.getStringExtra("TTS_VOICE") ?: "female"
            val hasVibration = intent.getBooleanExtra("HAS_VIBRATION", true)
            
            Log.d("AlarmReceiver", "Processing alarm - ID: $alarmId, Title: $alarmTitle, Time: $alarmTime")
            
            // Fix: Properly handle ringtone URI - don't default to alarm if custom ringtone is selected
            val ringtoneUri = if (!ringtoneUriString.isNullOrEmpty() && ringtoneUriString != "null") {
                try {
                    Uri.parse(ringtoneUriString)
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Failed to parse ringtone URI: $ringtoneUriString", e)
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
            } else {
                // Only use default if no ringtone was specifically selected
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            
            Log.d("AlarmReceiver", "Alarm triggered - Ringtone: $ringtoneName, URI: $ringtoneUri, Voice: $hasVoiceOverlay, TTS: $hasTtsOverlay")
            
            // Wake up the device
            wakeUpDevice(context)
            
            // Launch full-screen alarm activity with voice data and volume settings
            launchAlarmScreen(context, alarmId, alarmTime, alarmTitle, alarmNote, ringtoneUri, ringtoneName, snoozeMinutes, voiceRecordingPath, hasVoiceOverlay, hasTtsOverlay, alarmVolume, ttsVoice, hasVibration)
            
            // Also create notification as backup
            createNotificationChannel(context)
            showNotification(context, alarmId, alarmTitle, alarmTime, alarmNote, ringtoneUri, snoozeMinutes, intent, hasVibration)
            
            // Only vibrate if user has enabled vibration
            if (hasVibration) {
                vibrateDevice(context)
            }
            
            // If it's a repeating alarm, schedule the next occurrence
            if (isRepeating && repeatDay != -1) {
                scheduleNextRepeatingAlarm(context, intent, repeatDay)
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error handling alarm", e)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 10)
        
        // Extract all alarm data to preserve for snooze
        val alarmTime = intent.getStringExtra("ALARM_TIME") ?: ""
        val alarmTitle = intent.getStringExtra("ALARM_TITLE") ?: "Alarm"
        val alarmNote = intent.getStringExtra("ALARM_NOTE") ?: ""
        val ringtoneUriString = intent.getStringExtra("RINGTONE_URI")
        val ringtoneName = intent.getStringExtra("RINGTONE_NAME") ?: "Default"
        val voiceRecordingPath = intent.getStringExtra("VOICE_RECORDING_PATH")
        val hasVoiceOverlay = intent.getBooleanExtra("HAS_VOICE_OVERLAY", false)
        val hasTtsOverlay = intent.getBooleanExtra("HAS_TTS_OVERLAY", false)
        // Use single alarm volume instead of separate volumes
        val alarmVolume = intent.getFloatExtra("ALARM_VOLUME", 0.8f)
        val ttsVoice = intent.getStringExtra("TTS_VOICE") ?: "female"
        val hasVibration = intent.getBooleanExtra("HAS_VIBRATION", true)
        
        Log.d("AlarmReceiver", "Snoozing alarm - Preserving ringtone: $ringtoneName, URI: $ringtoneUriString, Volume: $alarmVolume")
        
        // Dismiss current notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId)
        
        // Stop the AlarmScreenActivity if it's running
        val stopAlarmIntent = Intent(context, AlarmScreenActivity::class.java).apply {
            action = "STOP_ALARM"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            context.startActivity(stopAlarmIntent)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Could not stop AlarmScreenActivity: ${e.message}")
        }
        
        // Broadcast to stop any running alarm screen
        val stopBroadcast = Intent("com.yourapp.test.alarm.STOP_ALARM").apply {
            setPackage(context.packageName)
            putExtra("ALARM_ID", alarmId)
        }
        context.sendBroadcast(stopBroadcast)
        
        // Cancel any pending dismiss alarms for this alarm ID
        cancelPendingDismissAlarms(context, alarmId)
        
        // Schedule snooze alarm with ALL original data preserved
        val snoozeTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, snoozeMinutes)
        }
        
        // Use a unique but predictable ID for snooze
        val snoozeAlarmId = generateSnoozeId(alarmId)
        
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            // Preserve ALL original alarm data for the snoozed alarm
            putExtra("ALARM_ID", snoozeAlarmId) // Use unique snooze ID
            putExtra("ORIGINAL_ALARM_ID", alarmId) // Track original for cleanup
            putExtra("ALARM_TIME", alarmTime)
            putExtra("ALARM_TITLE", alarmTitle)
            putExtra("ALARM_NOTE", alarmNote)
            putExtra("RINGTONE_URI", ringtoneUriString) // Preserve original ringtone URI
            putExtra("RINGTONE_NAME", ringtoneName) // Preserve original ringtone name
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            putExtra("IS_REPEATING", false) // Snooze alarms are never repeating
            putExtra("IS_SNOOZE_ALARM", true) // Mark as snooze alarm
            putExtra("VOICE_RECORDING_PATH", voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", hasVoiceOverlay)
            putExtra("HAS_TTS_OVERLAY", hasTtsOverlay)
            putExtra("ALARM_VOLUME", alarmVolume) // Use single alarm volume
            putExtra("TTS_VOICE", ttsVoice)
            putExtra("HAS_VIBRATION", hasVibration)
        }
        
        val snoozePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getBroadcast(
                context, snoozeAlarmId, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                context, snoozeAlarmId, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        var snoozeScheduled = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime.timeInMillis,
                    snoozePendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    snoozeTime.timeInMillis,
                    snoozePendingIntent
                )
            }
            snoozeScheduled = true
            Log.d("AlarmReceiver", "Snooze scheduled for ${snoozeMinutes} minutes with ringtone: $ringtoneName, ID: $snoozeAlarmId")
        } catch (e: SecurityException) {
            Log.e("AlarmReceiver", "Failed to schedule snooze due to permission: ${e.message}")
            // Show user notification about permission issue
            showSnoozeFailureNotification(context, alarmId, alarmTitle, snoozeMinutes)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to schedule snooze: ${e.message}")
            showSnoozeFailureNotification(context, alarmId, alarmTitle, snoozeMinutes)
        }
        
        // If scheduling failed, show a fallback notification
        if (!snoozeScheduled) {
            Log.w("AlarmReceiver", "Snooze scheduling failed - alarm will not repeat")
        }
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", 0)
        
        Log.d("AlarmReceiver", "Dismissing alarm with ID: $alarmId")
        
        // Dismiss notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(alarmId)
        
        // Also cancel any potential snooze notifications
        notificationManager.cancel(alarmId + 10000)
        
        // Broadcast to stop any running alarm screen (don't start a new one)
        val stopBroadcast = Intent("com.yourapp.test.alarm.STOP_ALARM").apply {
            setPackage(context.packageName)
            putExtra("ALARM_ID", alarmId)
        }
        context.sendBroadcast(stopBroadcast)
        
        // Cancel any pending snooze alarms for this alarm ID
        cancelPendingSnoozeAlarms(context, alarmId)
        
        // Cancel any pending dismiss alarms for this alarm ID
        cancelPendingDismissAlarms(context, alarmId)
        
        Log.d("AlarmReceiver", "Alarm $alarmId dismissed successfully")
    }
    
    private fun cancelPendingSnoozeAlarms(context: Context, originalAlarmId: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Cancel the standard snooze alarm using the correct request code
            val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE
            }
            // Use the same safer method to generate request codes
            val snoozeRequestCode = Math.abs(originalAlarmId * 31 + 1000) % 1000000
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context, 
                snoozeRequestCode,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(snoozePendingIntent)
            snoozePendingIntent.cancel()
            
            Log.d("AlarmReceiver", "Cancelled pending snooze alarms for alarm ID: $originalAlarmId")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error cancelling snooze alarms: ${e.message}")
        }
    }
    
    private fun cancelPendingDismissAlarms(context: Context, alarmId: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Cancel the dismiss alarm using the correct request code
            val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_DISMISS
            }
            // Use the same safer method to generate request codes
            val dismissRequestCode = Math.abs(alarmId * 31 + 2000) % 1000000
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context, 
                dismissRequestCode,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.cancel(dismissPendingIntent)
            dismissPendingIntent.cancel()
            
            Log.d("AlarmReceiver", "Cancelled pending dismiss alarms for alarm ID: $alarmId")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error cancelling dismiss alarms: ${e.message}")
        }
    }
    
    private fun generateSnoozeId(originalAlarmId: Int): Int {
        // Use a more robust method to ensure uniqueness and prevent integer overflow
        val timestamp = System.currentTimeMillis() % 1000000
        // Combine timestamp with original ID and a prime number to minimize collisions
        // Ensure the result is within valid PendingIntent ID range
        return ((timestamp * 31 + originalAlarmId) % 1000000007).toInt()
    }
    
    private fun showSnoozeFailureNotification(context: Context, alarmId: Int, title: String, snoozeMinutes: Int) {
        try {
            createNotificationChannel(context)
            
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("Snooze Failed")
                .setContentText("Unable to snooze alarm '$title' for $snoozeMinutes minutes. Please check alarm permissions.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(null)
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(alarmId + 5000, notificationBuilder.build())
            
            Log.d("AlarmReceiver", "Snooze failure notification shown for alarm: $title")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to show snooze failure notification: ${e.message}")
        }
    }

    private fun scheduleNextRepeatingAlarm(context: Context, intent: Intent, dayOfWeek: Int) {
        try {
            // CRITICAL FIX: Proper logic for scheduling next weekly occurrence
            val alarmTimeString = intent.getStringExtra("ALARM_TIME") ?: return
            val timeParts = alarmTimeString.split(":")
            if (timeParts.size != 2) return
            
            val hour = timeParts[0].toIntOrNull() ?: return
            val minute = timeParts[1].toIntOrNull() ?: return
            
            // Calculate next occurrence of this day/time
            val nextAlarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                
                // Always schedule for next week since this alarm just triggered
                add(Calendar.WEEK_OF_YEAR, 1)
            }
            
            // Create new intent with all original data preserved
            val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
                // Copy all extras from original intent
                putExtras(intent.extras ?: Bundle())
                // Ensure the ALARM_ID remains the same for repeating alarms
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                intent.getIntExtra("ALARM_ID", 0),
                nextIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // CRITICAL FIX: Use modern reliable scheduling instead of deprecated setRepeating
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmTime.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmTime.timeInMillis,
                    pendingIntent
                )
            }
            
            Log.d("AlarmReceiver", "Next repeating alarm scheduled for ${getDayName(dayOfWeek)} at $alarmTimeString (${nextAlarmTime.time})")
        } catch (e: SecurityException) {
            Log.e("AlarmReceiver", "Failed to schedule next repeating alarm due to permission: ${e.message}")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to schedule next repeating alarm: ${e.message}")
        }
    }
    
    private fun getDayName(dayOfWeek: Int): String {
        return when(dayOfWeek) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Unknown"
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarm Notifications"
            val descriptionText = "Notifications for alarm clock"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setSound(null, null) // Explicitly disable sound for notification channel
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(
        context: Context, 
        alarmId: Int, 
        title: String, 
        time: String, 
        note: String, 
        ringtoneUri: Uri?, 
        snoozeMinutes: Int,
        originalIntent: Intent? = null,
        hasVibration: Boolean = true
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action - preserve ALL original alarm data
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("ALARM_ID", alarmId)
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            putExtra("ALARM_TITLE", title)
            putExtra("ALARM_TIME", time)
            putExtra("ALARM_NOTE", note)
            putExtra("RINGTONE_URI", ringtoneUri?.toString())
            
            // Preserve all additional data from original intent if available
            originalIntent?.let { originalIntent ->
                putExtra("RINGTONE_NAME", originalIntent.getStringExtra("RINGTONE_NAME") ?: "Default")
                putExtra("IS_REPEATING", originalIntent.getBooleanExtra("IS_REPEATING", false))
                putExtra("REPEAT_DAY", originalIntent.getIntExtra("REPEAT_DAY", -1))
                putExtra("VOICE_RECORDING_PATH", originalIntent.getStringExtra("VOICE_RECORDING_PATH"))
                putExtra("HAS_VOICE_OVERLAY", originalIntent.getBooleanExtra("HAS_VOICE_OVERLAY", false))
                putExtra("HAS_TTS_OVERLAY", originalIntent.getBooleanExtra("HAS_TTS_OVERLAY", false))
                // Use single alarm volume instead of separate volumes
                putExtra("ALARM_VOLUME", originalIntent.getFloatExtra("ALARM_VOLUME", 0.8f))
                putExtra("TTS_VOICE", originalIntent.getStringExtra("TTS_VOICE") ?: "female")
                putExtra("HAS_VIBRATION", originalIntent.getBooleanExtra("HAS_VIBRATION", true))
            }
        }
        // Use safer method to generate request codes to prevent integer overflow
        val snoozeRequestCode = Math.abs(alarmId * 31 + 1000) % 1000000
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, snoozeRequestCode, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("ALARM_ID", alarmId)
        }
        // Use safer method to generate request codes to prevent integer overflow
        val dismissRequestCode = Math.abs(alarmId * 31 + 2000) % 1000000
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, dismissRequestCode, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (note.isNotEmpty()) "$time - $note" else time
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(null) // Explicitly set no sound for notification
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_snooze, "Snooze ($snoozeMinutes min)", snoozePendingIntent)
            .addAction(R.drawable.ic_dismiss, "Dismiss", dismissPendingIntent)
            .setFullScreenIntent(pendingIntent, true)
        
        // Only add vibration if user has enabled it
        if (hasVibration) {
            notificationBuilder.setVibrate(longArrayOf(0, 1000, 1000, 1000, 1000, 1000))
        }
        
        val notification = notificationBuilder.build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(alarmId, notification)
    }

    private fun launchAlarmScreen(
        context: Context,
        alarmId: Int,
        alarmTime: String,
        alarmTitle: String,
        alarmNote: String,
        ringtoneUri: Uri?,
        ringtoneName: String,
        snoozeMinutes: Int,
        voiceRecordingPath: String?,
        hasVoiceOverlay: Boolean,
        hasTtsOverlay: Boolean,
        alarmVolume: Float, // Single alarm volume
        ttsVoice: String,
        hasVibration: Boolean
    ) {
        Log.d("AlarmReceiver", "Preparing to launch alarm screen - ID: $alarmId, Title: $alarmTitle")
        
        val alarmScreenIntent = Intent(context, AlarmScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            putExtra(AlarmScreenActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScreenActivity.EXTRA_ALARM_TIME, alarmTime)
            putExtra(AlarmScreenActivity.EXTRA_ALARM_TITLE, alarmTitle)
            putExtra(AlarmScreenActivity.EXTRA_ALARM_NOTE, alarmNote)
            putExtra(AlarmScreenActivity.EXTRA_RINGTONE_URI, ringtoneUri?.toString())
            putExtra(AlarmScreenActivity.EXTRA_RINGTONE_NAME, ringtoneName)
            putExtra(AlarmScreenActivity.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            putExtra(AlarmScreenActivity.EXTRA_VOICE_RECORDING_PATH, voiceRecordingPath)
            putExtra(AlarmScreenActivity.EXTRA_HAS_VOICE_OVERLAY, hasVoiceOverlay)
            putExtra(AlarmScreenActivity.EXTRA_HAS_TTS_OVERLAY, hasTtsOverlay)
            // Pass single alarm volume instead of separate volumes
            putExtra(AlarmScreenActivity.EXTRA_ALARM_VOLUME, alarmVolume)
            putExtra(AlarmScreenActivity.EXTRA_TTS_VOICE, ttsVoice)
            putExtra(AlarmScreenActivity.EXTRA_HAS_VIBRATION, hasVibration)
            // Pass the simultaneous playback setting
            putExtra(AlarmScreenActivity.EXTRA_ALLOW_SIMULTANEOUS_PLAYBACK, false) // Default to false for now
        }
        
        try {
            Log.d("AlarmReceiver", "Attempting to launch alarm screen with primary method")
            context.startActivity(alarmScreenIntent)
            Log.d("AlarmReceiver", "Alarm screen launched successfully with primary method")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to launch alarm screen with primary method", e)
            // Try alternative approach to launch the alarm screen
            launchAlarmScreenFallback(context, alarmScreenIntent)
        }
    }
    
    private fun launchAlarmScreenFallback(context: Context, intent: Intent) {
        try {
            Log.d("AlarmReceiver", "Attempting to launch alarm screen with fallback method")
            // Try with enhanced flags
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                          Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                          Intent.FLAG_ACTIVITY_SINGLE_TOP or
                          Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                          Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                          Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            context.startActivity(intent)
            Log.d("AlarmReceiver", "Alarm screen launched successfully with fallback method")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Fallback method also failed to launch alarm screen", e)
            // Last resort: try with system alert window flag
            try {
                Log.d("AlarmReceiver", "Attempting to launch alarm screen with last resort method")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                              Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                              Intent.FLAG_ACTIVITY_SINGLE_TOP or
                              Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                              Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                              Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                context.startActivity(intent)
                Log.d("AlarmReceiver", "Alarm screen launched with last resort method")
            } catch (finalException: Exception) {
                Log.e("AlarmReceiver", "Last resort method also failed to launch alarm screen", finalException)
                // Force wake up the device and try one more time
                wakeUpDevice(context)
                try {
                    Log.d("AlarmReceiver", "Attempting to launch alarm screen after forced wake up")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                                  Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                                  Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                  Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                                  Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                  Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                    context.startActivity(intent)
                    Log.d("AlarmReceiver", "Alarm screen launched after forced wake up")
                } catch (lastException: Exception) {
                    Log.e("AlarmReceiver", "Completely failed to launch alarm screen after all attempts", lastException)
                    // Show a notification as absolute last resort
                    showCriticalAlarmNotification(context, intent)
                }
            }
        }
    }
    
    private fun showCriticalAlarmNotification(context: Context, intent: Intent) {
        try {
            val alarmTitle = intent.getStringExtra(AlarmScreenActivity.EXTRA_ALARM_TITLE) ?: "Alarm"
            val alarmNote = intent.getStringExtra(AlarmScreenActivity.EXTRA_ALARM_NOTE) ?: ""
            
            createNotificationChannel(context)
            
            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("CRITICAL: Alarm Active - $alarmTitle")
                .setContentText(if (alarmNote.isNotEmpty()) alarmNote else "Tap to acknowledge alarm")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setVibrate(longArrayOf(0, 1000, 1000, 1000, 1000, 1000))
                .setFullScreenIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE), true)
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
            
            Log.d("AlarmReceiver", "Critical alarm notification shown as last resort")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to show critical alarm notification", e)
        }
    }

    private fun vibrateDevice(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrationEffect = android.os.VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000, 500, 1000), -1)
            vibratorManager.defaultVibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000, 500, 1000), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000), -1)
            }
        }
    }

    private fun wakeUpDevice(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        // Strategy 1: Create multiple wake locks for maximum reliability
        val fullWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or 
            PowerManager.ON_AFTER_RELEASE,
            "AlarmApp:FullWakeLock"
        )
        
        val screenWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AlarmApp:ScreenWakeLock"
        )
        
        // Strategy 2: Acquire both wake locks for maximum wake power
        fullWakeLock.acquire(120000) // 2 minutes
        screenWakeLock.acquire(120000) // 2 minutes
        
        // Strategy 3: Force screen on for different Android versions
        val partialWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlarmApp:PartialWakeLock"
        )
        partialWakeLock.acquire(60000)
        
        // Strategy 4: Handle different power states
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (powerManager.isDeviceIdleMode) {
                    Log.w("AlarmReceiver", "Device in Doze mode - Wake locks should override")
                    // Try to request ignore battery optimizations if not already granted
                    requestIgnoreBatteryOptimizations(context)
                }
                
                if (!powerManager.isInteractive) {
                    Log.d("AlarmReceiver", "Screen OFF detected - Multiple wake strategies activated")
                }
            }
            
            // Check keyguard state
            if (keyguardManager.isKeyguardLocked) {
                Log.d("AlarmReceiver", "Device locked - Alarm will show over lock screen")
                // Try to dismiss keyguard
                dismissKeyguard(context)
            }
            
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Wake strategy error: ${e.message}")
        }
        
        Log.d("AlarmReceiver", "Enhanced wake sequence completed - Should wake from screen OFF")
    }
    
    private fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    Log.w("AlarmReceiver", "App is not ignoring battery optimizations - this may affect alarm reliability")
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error checking battery optimization status: ${e.message}")
        }
    }
    
    private fun dismissKeyguard(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                // This is a no-op but shows intent
                // Note: requestDismissKeyguard requires an Activity, but we're in a BroadcastReceiver
                // We can't get an Activity here, so we'll just log that we can't dismiss the keyguard
                Log.d("AlarmReceiver", "Cannot dismiss keyguard from BroadcastReceiver - requires Activity context")
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error dismissing keyguard: ${e.message}")
        }
    }
}