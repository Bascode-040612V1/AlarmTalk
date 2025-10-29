package com.yourapp.test.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourapp.test.alarm.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var alarmAdapter: AlarmAdapter
    private val alarmList = mutableListOf<AlarmItem>()
    private lateinit var alarmStorage: AlarmStorage
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var efficientAlarmManager: EfficientAlarmManager
    
    // Background service connection
    private var backgroundService: BackgroundOptimizationService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BackgroundOptimizationService.LocalBinder
            backgroundService = binder.getService()
            isServiceBound = true
            Log.d("MainActivity", "Background optimization service connected")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            backgroundService = null
            Log.d("MainActivity", "Background optimization service disconnected")
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for alarms", Toast.LENGTH_LONG).show()
        }
    }
    
    private val alarmSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            AlarmSetupActivity.RESULT_ALARM_SAVED -> {
                result.data?.let { data ->
                    val alarmItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        data.getParcelableExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, AlarmItem::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        data.getParcelableExtra<AlarmItem>(AlarmSetupActivity.EXTRA_ALARM_ITEM)
                    }
                    alarmItem?.let { alarm ->
                        val existingAlarm = alarmList.find { it.id == alarm.id }
                        if (existingAlarm != null) {
                            updateAlarm(existingAlarm, alarm)
                        } else {
                            createAlarm(alarm)
                        }
                    }
                }
            }
            AlarmSetupActivity.RESULT_ALARM_DELETED -> {
                result.data?.let { data ->
                    val alarmItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        data.getParcelableExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, AlarmItem::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        data.getParcelableExtra<AlarmItem>(AlarmSetupActivity.EXTRA_ALARM_ITEM)
                    }
                    alarmItem?.let { alarm ->
                        deleteAlarm(alarm)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        alarmStorage = AlarmStorage(this)
        batteryOptimizationManager = BatteryOptimizationManager(this)
        efficientAlarmManager = EfficientAlarmManager(this)
        
        loadSavedAlarms()
        
        setupUI()
        setupRecyclerView()
        requestPermissions()
        checkOverlayPermission()
        
        // Start and bind background optimization service
        startBackgroundOptimizationService()
    }
    
    private fun startBackgroundOptimizationService() {
        try {
            val intent = Intent(this, BackgroundOptimizationService::class.java)
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d("MainActivity", "Background optimization service started and bound")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting background optimization service", e)
        }
    }
    
    private fun setupUI() {
        updateCurrentTime()
        
        // Use the FAB for adding alarms
        binding.fabAddAlarm.setOnClickListener {
            openAlarmSetupActivity()
        }
        
        // Info button (contact developers)
        binding.buttonInfo.setOnClickListener {
            openDeveloperContact()
        }
        
        // Update time every second
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateCurrentTime()
                }
            }
        }, 0, 1000)
    }
    
    private fun updateCurrentTime() {
        val currentTime = Calendar.getInstance()
        
        // Use system's 12/24-hour format preference
        val is24HourFormat = DateFormat.is24HourFormat(this)
        val timeFormat = if (is24HourFormat) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        } else {
            SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        }
        
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        
        binding.textCurrentTime.text = timeFormat.format(currentTime.time)
        binding.textCurrentDate.text = dateFormat.format(currentTime.time)
    }
    
    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(
            alarmList,
            onDeleteClick = { alarm -> deleteAlarm(alarm) },
            onToggleClick = { alarm, isEnabled -> toggleAlarm(alarm, isEnabled) },
            onEditClick = { alarm -> editAlarm(alarm) }
        )
        binding.recyclerViewAlarms.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = alarmAdapter
        }
    }
    
    private fun openAlarmSetupActivity(existingAlarm: AlarmItem? = null) {
        val intent = Intent(this, AlarmSetupActivity::class.java).apply {
            existingAlarm?.let {
                putExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, it)
                putExtra(AlarmSetupActivity.EXTRA_IS_EDIT_MODE, true)
            }
        }
        alarmSetupLauncher.launch(intent)
    }
    
    private fun createAlarm(alarmItem: AlarmItem) {
        alarmList.add(alarmItem)
        alarmList.sortBy { it.time }
        alarmAdapter.notifyDataSetChanged()
        
        scheduleAlarm(alarmItem)
        saveAlarms()
        
        val repeatText = if (alarmItem.isRepeating()) " (${alarmItem.getRepeatDaysString()})" else ""
        Toast.makeText(this, "Alarm '${alarmItem.title}' set for ${formatTimeForDisplay(alarmItem.time)}$repeatText", Toast.LENGTH_SHORT).show()
    }
    
    private fun editAlarm(alarm: AlarmItem) {
        openAlarmSetupActivity(alarm)
    }
    
    private fun updateAlarm(oldAlarm: AlarmItem, newAlarm: AlarmItem) {
        // Cancel old alarm
        cancelAlarmSchedule(oldAlarm)
        
        // Update in list
        val index = alarmList.indexOf(oldAlarm)
        if (index != -1) {
            alarmList[index] = newAlarm
            alarmList.sortBy { it.time }
            alarmAdapter.notifyDataSetChanged()
        }
        
        // Schedule new alarm if enabled
        if (newAlarm.isEnabled) {
            scheduleAlarm(newAlarm)
        }
        
        saveAlarms()
        Toast.makeText(this, "Alarm updated", Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleAlarm(alarm: AlarmItem, isEnabled: Boolean) {
        val index = alarmList.indexOf(alarm)
        if (index != -1) {
            val updatedAlarm = alarm.copy(isEnabled = isEnabled)
            alarmList[index] = updatedAlarm
            
            if (isEnabled) {
                scheduleAlarm(updatedAlarm)
                Toast.makeText(this, "Alarm enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelAlarmSchedule(updatedAlarm)
                Toast.makeText(this, "Alarm disabled", Toast.LENGTH_SHORT).show()
            }
            
            saveAlarms()
        }
    }
    
    private fun scheduleAlarm(alarmItem: AlarmItem) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (alarmItem.isRepeating()) {
            // Schedule for each repeat day
            alarmItem.repeatDays.forEach { dayOfWeek ->
                scheduleRepeatingAlarm(alarmManager, alarmItem, dayOfWeek)
            }
        } else {
            // Schedule single alarm
            scheduleSingleAlarm(alarmManager, alarmItem)
        }
    }
    
    private fun scheduleSingleAlarm(alarmManager: AlarmManager, alarmItem: AlarmItem) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmItem.id)
            putExtra("ALARM_TIME", alarmItem.time)
            putExtra("ALARM_TITLE", alarmItem.title)
            putExtra("ALARM_NOTE", alarmItem.note)
            putExtra("RINGTONE_URI", alarmItem.ringtoneUri?.toString())
            putExtra("RINGTONE_NAME", alarmItem.ringtoneName)
            putExtra("SNOOZE_MINUTES", alarmItem.snoozeMinutes)
            putExtra("VOICE_RECORDING_PATH", alarmItem.voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", alarmItem.hasVoiceOverlay)
            putExtra("HAS_TTS_OVERLAY", alarmItem.hasTtsOverlay)
            putExtra("RINGTONE_VOLUME", alarmItem.ringtoneVolume)
            putExtra("VOICE_VOLUME", alarmItem.voiceVolume)
            putExtra("TTS_VOLUME", alarmItem.ttsVolume)
            putExtra("HAS_VIBRATION", alarmItem.hasVibration) // CRITICAL FIX: Add vibration setting
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarmItem.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmItem.calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmItem.calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("MainActivity", "Single alarm scheduled successfully for ${alarmItem.time}")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission denied for exact alarm scheduling", e)
            Toast.makeText(this, "Please allow exact alarm permission in settings for reliable alarms", Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(settingsIntent)
                } catch (ex: Exception) {
                    Log.e("MainActivity", "Could not open alarm settings", ex)
                    // Fallback: Try less precise scheduling
                    fallbackAlarmScheduling(alarmManager, alarmItem, pendingIntent)
                }
            } else {
                // For pre-Android 12, try fallback scheduling
                fallbackAlarmScheduling(alarmManager, alarmItem, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Unexpected error scheduling single alarm", e)
            // Try fallback scheduling method
            fallbackAlarmScheduling(alarmManager, alarmItem, pendingIntent)
        }
    }
    
    private fun scheduleRepeatingAlarm(alarmManager: AlarmManager, alarmItem: AlarmItem, dayOfWeek: Int) {
        val currentTime = Calendar.getInstance()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarmItem.calendar.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, alarmItem.calendar.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            
            // CRITICAL FIX: Proper logic for determining next occurrence
            // If this day/time combination has already passed this week, schedule for next week
            if (before(currentTime) || 
                (get(Calendar.DAY_OF_WEEK) == currentTime.get(Calendar.DAY_OF_WEEK) && 
                 get(Calendar.HOUR_OF_DAY) < currentTime.get(Calendar.HOUR_OF_DAY)) ||
                (get(Calendar.DAY_OF_WEEK) == currentTime.get(Calendar.DAY_OF_WEEK) && 
                 get(Calendar.HOUR_OF_DAY) == currentTime.get(Calendar.HOUR_OF_DAY) &&
                 get(Calendar.MINUTE) <= currentTime.get(Calendar.MINUTE))) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmItem.id + dayOfWeek) // Unique ID for each day
            putExtra("ALARM_TIME", alarmItem.time)
            putExtra("ALARM_TITLE", alarmItem.title)
            putExtra("ALARM_NOTE", alarmItem.note)
            putExtra("RINGTONE_URI", alarmItem.ringtoneUri?.toString())
            putExtra("RINGTONE_NAME", alarmItem.ringtoneName)
            putExtra("SNOOZE_MINUTES", alarmItem.snoozeMinutes)
            putExtra("IS_REPEATING", true)
            putExtra("REPEAT_DAY", dayOfWeek)
            putExtra("VOICE_RECORDING_PATH", alarmItem.voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", alarmItem.hasVoiceOverlay)
            putExtra("HAS_TTS_OVERLAY", alarmItem.hasTtsOverlay)
            putExtra("RINGTONE_VOLUME", alarmItem.ringtoneVolume)
            putExtra("VOICE_VOLUME", alarmItem.voiceVolume)
            putExtra("TTS_VOLUME", alarmItem.ttsVolume)
            putExtra("HAS_VIBRATION", alarmItem.hasVibration)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarmItem.id + dayOfWeek,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // CRITICAL FIX: Use reliable exact scheduling for repeating alarms
            // Note: We schedule individual occurrences and let AlarmReceiver handle the next one
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("MainActivity", "Scheduled repeating alarm for ${getDayName(dayOfWeek)} at ${alarmItem.time} (${calendar.time})")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission denied for repeating alarm scheduling", e)
            Toast.makeText(this, "Please allow exact alarm permission in settings for reliable alarms", Toast.LENGTH_LONG).show()
            // Try fallback scheduling for repeating alarms
            fallbackRepeatingAlarmScheduling(alarmManager, alarmItem, dayOfWeek, calendar, pendingIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Unexpected error scheduling repeating alarm", e)
            fallbackRepeatingAlarmScheduling(alarmManager, alarmItem, dayOfWeek, calendar, pendingIntent)
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
    
    private fun deleteAlarm(alarmItem: AlarmItem) {
        cancelAlarmSchedule(alarmItem)
        alarmList.remove(alarmItem)
        alarmAdapter.notifyDataSetChanged()
        
        saveAlarms()
        Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
    }
    
    private fun cancelAlarmSchedule(alarmItem: AlarmItem) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (alarmItem.isRepeating()) {
            // Cancel all repeat day alarms
            alarmItem.repeatDays.forEach { dayOfWeek ->
                val intent = Intent(this, AlarmReceiver::class.java).apply {
                    // Include ALL the same extras that were used when scheduling
                    putExtra("ALARM_ID", alarmItem.id + dayOfWeek) // Unique ID for each day
                    putExtra("ALARM_TIME", alarmItem.time)
                    putExtra("ALARM_TITLE", alarmItem.title)
                    putExtra("ALARM_NOTE", alarmItem.note)
                    putExtra("RINGTONE_URI", alarmItem.ringtoneUri?.toString())
                    putExtra("RINGTONE_NAME", alarmItem.ringtoneName)
                    putExtra("SNOOZE_MINUTES", alarmItem.snoozeMinutes)
                    putExtra("IS_REPEATING", true)
                    putExtra("REPEAT_DAY", dayOfWeek)
                    putExtra("VOICE_RECORDING_PATH", alarmItem.voiceRecordingPath)
                    putExtra("HAS_VOICE_OVERLAY", alarmItem.hasVoiceOverlay)
                    putExtra("HAS_TTS_OVERLAY", alarmItem.hasTtsOverlay)
                    putExtra("RINGTONE_VOLUME", alarmItem.ringtoneVolume)
                    putExtra("VOICE_VOLUME", alarmItem.voiceVolume)
                    putExtra("TTS_VOLUME", alarmItem.ttsVolume)
                    putExtra("HAS_VIBRATION", alarmItem.hasVibration) // CRITICAL FIX: Add vibration setting
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    alarmItem.id + dayOfWeek,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel() // Also cancel the PendingIntent itself
            }
        } else {
            // Cancel single alarm
            val intent = Intent(this, AlarmReceiver::class.java).apply {
                // Include ALL the same extras that were used when scheduling
                putExtra("ALARM_ID", alarmItem.id)
                putExtra("ALARM_TIME", alarmItem.time)
                putExtra("ALARM_TITLE", alarmItem.title)
                putExtra("ALARM_NOTE", alarmItem.note)
                putExtra("RINGTONE_URI", alarmItem.ringtoneUri?.toString())
                putExtra("RINGTONE_NAME", alarmItem.ringtoneName)
                putExtra("SNOOZE_MINUTES", alarmItem.snoozeMinutes)
                putExtra("VOICE_RECORDING_PATH", alarmItem.voiceRecordingPath)
                putExtra("HAS_VOICE_OVERLAY", alarmItem.hasVoiceOverlay)
                putExtra("HAS_TTS_OVERLAY", alarmItem.hasTtsOverlay)
                putExtra("RINGTONE_VOLUME", alarmItem.ringtoneVolume)
                putExtra("VOICE_VOLUME", alarmItem.voiceVolume)
                putExtra("TTS_VOLUME", alarmItem.ttsVolume)
                putExtra("HAS_VIBRATION", alarmItem.hasVibration) // CRITICAL FIX: Add vibration setting
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                alarmItem.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // Also cancel the PendingIntent itself
        }
    }
    
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Request battery optimization exemption for reliable alarms
        requestBatteryOptimizationExemption()
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("To ensure alarms work reliably, please disable battery optimization for this app. This prevents the system from stopping alarms during sleep mode.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback to general battery optimization settings
                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(fallbackIntent)
                        }
                    }
                    .setNegativeButton("Skip") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this, "Alarms may not work reliably in battery saver mode", Toast.LENGTH_LONG).show()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    private fun formatTimeForDisplay(time24Hour: String): String {
        val is24HourFormat = DateFormat.is24HourFormat(this)
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
    
    private fun loadSavedAlarms() {
        val savedAlarms = alarmStorage.loadAlarms()
        alarmList.clear()
        alarmList.addAll(savedAlarms)
        
        // Reschedule enabled alarms
        savedAlarms.filter { it.isEnabled }.forEach { alarm ->
            scheduleAlarm(alarm)
        }
    }
    
    private fun saveAlarms() {
        alarmStorage.saveAlarms(alarmList)
    }
    
    override fun onPause() {
        super.onPause()
        saveAlarms()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unbind service when activity is destroyed
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
    
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Display Over Other Apps Permission")
                    .setMessage("This app needs permission to display alarms over other apps (like when you're watching videos or using other apps). This ensures your alarms will always be visible when they trigger.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                    .setNegativeButton("Skip") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this, "Alarms may not display properly over other apps", Toast.LENGTH_LONG).show()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    /**
     * Opens the developer contact screen
     */
    private fun openDeveloperContact() {
        try {
            Log.d("MainActivity", "Opening developer contact screen")
            val intent = Intent(this, DeveloperContactActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open developer contact screen", e)
            Toast.makeText(this, "Unable to open developer contact", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Fallback alarm scheduling when exact scheduling fails
     */
    private fun fallbackAlarmScheduling(alarmManager: AlarmManager, alarmItem: AlarmItem, pendingIntent: PendingIntent) {
        try {
            Log.w("MainActivity", "Using fallback alarm scheduling for alarm: ${alarmItem.title}")
            
            // Try using set() for less precise but more compatible scheduling
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                alarmItem.calendar.timeInMillis,
                pendingIntent
            )
            
            Toast.makeText(this, "Alarm scheduled with reduced precision due to system limitations", Toast.LENGTH_LONG).show()
            Log.d("MainActivity", "Fallback alarm scheduled for ${alarmItem.time}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Fallback alarm scheduling also failed", e)
            Toast.makeText(this, "Failed to schedule alarm. Please check system alarm settings.", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Fallback scheduling for repeating alarms
     */
    private fun fallbackRepeatingAlarmScheduling(
        alarmManager: AlarmManager, 
        alarmItem: AlarmItem, 
        dayOfWeek: Int, 
        calendar: Calendar, 
        pendingIntent: PendingIntent
    ) {
        try {
            Log.w("MainActivity", "Using fallback repeating alarm scheduling for ${getDayName(dayOfWeek)}")
            
            // Use less precise scheduling method
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            
            Log.d("MainActivity", "Fallback repeating alarm scheduled for ${getDayName(dayOfWeek)} at ${alarmItem.time}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Fallback repeating alarm scheduling failed for ${getDayName(dayOfWeek)}", e)
        }
    }
}