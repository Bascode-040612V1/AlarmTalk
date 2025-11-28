package com.yourapp.test.alarm

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourapp.test.alarm.databinding.FragmentAlarmsBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.Timer
import java.util.TimerTask

class AlarmsFragment : Fragment() {
    
    private var _binding: FragmentAlarmsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var alarmAdapter: AlarmAdapter
    private val alarmList = mutableListOf<AlarmItem>()
    private lateinit var alarmStorage: AlarmStorage
    private lateinit var alarmSetupLauncher: ActivityResultLauncher<Intent>
    
    // Action mode for multi-select
    private var actionMode: ActionMode? = null
    
    // Timer for updating current time
    private var timer: Timer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize activity result launcher
            alarmSetupLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                try {
                    handleActivityResult(result.resultCode, result.data)
                } catch (e: Exception) {
                    Log.e("AlarmsFragment", "Error in alarmSetupLauncher callback", e)
                    Toast.makeText(requireContext(), "Error processing alarm result: ${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: Throwable) {
                    Log.e("AlarmsFragment", "Unexpected error in alarmSetupLauncher callback", e)
                    Toast.makeText(requireContext(), "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmsFragment", "Error initializing alarmSetupLauncher", e)
            Toast.makeText(requireContext(), "Error initializing alarm setup: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Log.e("AlarmsFragment", "Unexpected error initializing alarmSetupLauncher", e)
            Toast.makeText(requireContext(), "Unexpected initialization error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // Public method to handle activity results from MainActivity
    fun onActivityResult(resultCode: Int, data: Intent?) {
        handleActivityResult(resultCode, data)
    }
    
    // Private method to handle activity results
    private fun handleActivityResult(resultCode: Int, data: Intent?) {
        when (resultCode) {
            AlarmSetupActivity.RESULT_ALARM_SAVED -> {
                data?.let { intentData ->
                    try {
                        val alarmItem = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intentData.getParcelableExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, AlarmItem::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intentData.getParcelableExtra<AlarmItem>(AlarmSetupActivity.EXTRA_ALARM_ITEM)
                        }
                        alarmItem?.let { alarm ->
                            val existingAlarm = alarmList.find { it.id == alarm.id }
                            if (existingAlarm != null) {
                                updateAlarm(existingAlarm, alarm)
                            } else {
                                createAlarm(alarm)
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error saving alarm: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            AlarmSetupActivity.RESULT_ALARM_DELETED -> {
                data?.let { intentData ->
                    try {
                        val alarmItem = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intentData.getParcelableExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, AlarmItem::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intentData.getParcelableExtra<AlarmItem>(AlarmSetupActivity.EXTRA_ALARM_ITEM)
                        }
                        alarmItem?.let { alarm ->
                            deleteAlarm(alarm)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error deleting alarm: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return try {
            _binding = FragmentAlarmsBinding.inflate(inflater, container, false)
            binding.root
        } catch (e: Exception) {
            Log.e("AlarmsFragment", "Error in onCreateView", e)
            // If binding fails, try to create a basic view
            val errorView = TextView(requireContext())
            errorView.text = "Error loading alarms screen: ${e.message}"
            errorView.setPadding(16, 16, 16, 16)
            errorView
        } catch (e: Throwable) {
            Log.e("AlarmsFragment", "Unexpected error in onCreateView", e)
            val errorView = TextView(requireContext())
            errorView.text = "Unexpected error loading alarms screen: ${e.message}"
            errorView.setPadding(16, 16, 16, 16)
            errorView
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            alarmStorage = AlarmStorage(requireContext())
            loadSavedAlarms()
            setupRecyclerView()
            setupUI()
        } catch (e: Exception) {
            Log.e("AlarmsFragment", "Error in onViewCreated", e)
            Toast.makeText(requireContext(), "Error setting up alarms screen: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Log.e("AlarmsFragment", "Unexpected error in onViewCreated", e)
            Toast.makeText(requireContext(), "Unexpected error setting up alarms screen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupUI() {
        updateCurrentTime()
        
        // Info button (information) - Directly open the information screen
        binding.buttonInfo.setOnClickListener {
            val intent = Intent(requireContext(), DeveloperContactActivity::class.java)
            startActivity(intent)
        }
        
        // Update time every second
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                activity?.runOnUiThread {
                    updateCurrentTime()
                }
            }
        }, 0, 1000)
    }
    
    private fun updateCurrentTime() {
        // Safely check if binding is still available
        if (_binding == null) return
        
        val currentTime = Calendar.getInstance()
        
        // Use system's 12/24-hour format preference
        val is24HourFormat = DateFormat.is24HourFormat(requireContext())
        val timeFormat = if (is24HourFormat) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        } else {
            SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        }
        
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        
        binding.textCurrentTime.text = timeFormat.format(currentTime.time)
        binding.textCurrentDate.text = dateFormat.format(currentTime.time)
    }
    
    private fun loadSavedAlarms() {
        try {
            val savedAlarms = alarmStorage.loadAlarms()
            alarmList.clear()
            alarmList.addAll(savedAlarms)
            alarmList.sortBy { it.time }
        } catch (e: Exception) {
            // Log the error for debugging
            Log.e("AlarmsFragment", "Error loading alarms", e)
        } finally {
            // Update empty state visibility
            updateEmptyStateVisibility()
        }
    }
    
    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(
            alarmList,
            onDeleteClick = { alarm -> deleteAlarm(alarm) },
            onToggleClick = { alarm, isEnabled -> toggleAlarm(alarm, isEnabled) },
            onEditClick = { alarm -> editAlarm(alarm) }
        )
        
        // Set up multi-select listener
        alarmAdapter.setMultiSelectListener {
            updateMultiSelectMode()
        }
        
        binding.recyclerViewAlarms.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alarmAdapter
        }
        
        // Update empty state visibility
        updateEmptyStateVisibility()
    }
    
    private fun updateEmptyStateVisibility() {
        if (alarmList.isEmpty()) {
            binding.textEmptyState.visibility = View.VISIBLE
            binding.recyclerViewAlarms.visibility = View.GONE
        } else {
            binding.textEmptyState.visibility = View.GONE
            binding.recyclerViewAlarms.visibility = View.VISIBLE
        }
    }
    
    private fun updateMultiSelectMode() {
        if (alarmAdapter.isInMultiSelectMode()) {
            if (actionMode == null) {
                actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
                        menu.add("Delete").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                        menu.add("Select All").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
                        val selectedCount = alarmAdapter.getSelectedAlarms().size
                        mode.title = "$selectedCount selected"
                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean {
                        return when (item.title) {
                            "Delete" -> {
                                deleteSelectedAlarms()
                                true
                            }
                            "Select All" -> {
                                if (alarmAdapter.getSelectedAlarms().size == alarmList.size) {
                                    alarmAdapter.deselectAll()
                                } else {
                                    alarmAdapter.selectAll()
                                }
                                true
                            }
                            else -> false
                        }
                    }

                    override fun onDestroyActionMode(mode: ActionMode) {
                        alarmAdapter.exitMultiSelectMode()
                        actionMode = null
                    }
                })
            }
            actionMode?.invalidate()
        } else {
            actionMode?.finish()
            actionMode = null
        }
    }
    
    private fun deleteSelectedAlarms() {
        val selectedAlarms = alarmAdapter.getSelectedAlarms()
        if (selectedAlarms.isEmpty()) return
        
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Alarms")
            .setMessage("Are you sure you want to delete ${selectedAlarms.size} alarm(s)?")
            .setPositiveButton("Delete") { _, _ ->
                selectedAlarms.forEach { alarm ->
                    deleteAlarm(alarm)
                }
                alarmAdapter.exitMultiSelectMode()
                actionMode?.finish()
                Toast.makeText(requireContext(), "${selectedAlarms.size} alarm(s) deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openAlarmSetupActivity(existingAlarm: AlarmItem? = null) {
        val intent = Intent(requireContext(), AlarmSetupActivity::class.java).apply {
            existingAlarm?.let {
                putExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, it)
                putExtra(AlarmSetupActivity.EXTRA_IS_EDIT_MODE, true)
            }
        }
        alarmSetupLauncher.launch(intent)
    }
    
    private fun createAlarm(alarmItem: AlarmItem) {
        try {
            Log.d("AlarmsFragment", "Creating alarm: ${alarmItem.title} at ${alarmItem.time}")
            
            alarmList.add(alarmItem)
            alarmList.sortBy { it.time }
            alarmAdapter.notifyDataSetChanged()
            
            Log.d("AlarmsFragment", "Scheduling alarm with ID: ${alarmItem.id}")
            scheduleAlarm(alarmItem)
            saveAlarms()
            
            // Update empty state visibility
            updateEmptyStateVisibility()
            
            val repeatText = if (alarmItem.isRepeating()) " (${alarmItem.getRepeatDaysString()})" else ""
            Toast.makeText(requireContext(), "Alarm '${alarmItem.title}' set for ${formatTimeForDisplay(alarmItem.time)}$repeatText", Toast.LENGTH_SHORT).show()
            
            Log.d("AlarmsFragment", "Alarm created successfully: ${alarmItem.title}")
        } catch (e: Exception) {
            Log.e("AlarmsFragment", "Error creating alarm: ${e.message}", e)
            Toast.makeText(requireContext(), "Error creating alarm: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Remove the alarm from the list if it was added
            alarmList.remove(alarmItem)
            alarmAdapter.notifyDataSetChanged()
            updateEmptyStateVisibility()
        }
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
        Toast.makeText(requireContext(), "Alarm updated", Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleAlarm(alarm: AlarmItem, isEnabled: Boolean) {
        val index = alarmList.indexOf(alarm)
        if (index != -1) {
            val updatedAlarm = alarm.copy(isEnabled = isEnabled)
            alarmList[index] = updatedAlarm
            
            if (isEnabled) {
                scheduleAlarm(updatedAlarm)
                Toast.makeText(requireContext(), "Alarm enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelAlarmSchedule(updatedAlarm)
                Toast.makeText(requireContext(), "Alarm disabled", Toast.LENGTH_SHORT).show()
            }
            
            saveAlarms()
        }
    }
    
    private fun deleteAlarm(alarm: AlarmItem) {
        // Cancel alarm schedule
        cancelAlarmSchedule(alarm)
        
        // Remove from list
        alarmList.remove(alarm)
        alarmAdapter.notifyDataSetChanged()
        
        saveAlarms()
        
        // Update empty state visibility
        updateEmptyStateVisibility()
        
        Toast.makeText(requireContext(), "Alarm deleted", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveAlarms() {
        try {
            alarmStorage.saveAlarms(alarmList)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error saving alarms: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun scheduleAlarm(alarmItem: AlarmItem) {
        try {
            Log.d("AlarmsFragment", "Scheduling alarm: ${alarmItem.title} (ID: ${alarmItem.id})")
            
            val alarmManager = requireContext().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            
            if (alarmItem.isRepeating()) {
                Log.d("AlarmsFragment", "Scheduling repeating alarm for days: ${alarmItem.repeatDays}")
                // Schedule for each repeat day
                alarmItem.repeatDays.forEach { dayOfWeek ->
                    scheduleRepeatingAlarm(alarmManager, alarmItem, dayOfWeek)
                }
            } else {
                Log.d("AlarmsFragment", "Scheduling single alarm for: ${alarmItem.calendar.time}")
                // Schedule single alarm
                scheduleSingleAlarm(alarmManager, alarmItem)
            }
            
            Log.d("AlarmsFragment", "Alarm scheduled successfully: ${alarmItem.title}")
        } catch (e: Exception) {
            Log.e("AlarmsFragment", "Error scheduling alarm: ${e.message}", e)
            Toast.makeText(requireContext(), "Error scheduling alarm: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun scheduleSingleAlarm(alarmManager: android.app.AlarmManager, alarmItem: AlarmItem) {
        Log.d("AlarmsFragment", "Setting up single alarm intent for: ${alarmItem.title} (ID: ${alarmItem.id})")
        
        val intent = android.content.Intent(requireContext(), AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmItem.id)
            putExtra("ALARM_TIME", alarmItem.time)
            putExtra("ALARM_TITLE", alarmItem.title)
            putExtra("ALARM_NOTE", alarmItem.note)
            putExtra("RINGTONE_URI", alarmItem.ringtoneUri?.toString())
            putExtra("RINGTONE_NAME", alarmItem.ringtoneName)
            putExtra("SNOOZE_MINUTES", alarmItem.snoozeMinutes)
            putExtra("VOICE_RECORDING_PATH", alarmItem.voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", alarmItem.hasVoiceOverlay)
            putExtra("ALARM_VOLUME", alarmItem.alarmVolume)
            putExtra("HAS_TTS_OVERLAY", alarmItem.hasTtsOverlay)
            putExtra("HAS_VIBRATION", alarmItem.hasVibration)
        }
        
        Log.d("AlarmsFragment", "Creating PendingIntent for alarm: ${alarmItem.title}")
        
        val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.getBroadcast(
                requireContext(),
                alarmItem.id,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            android.app.PendingIntent.getBroadcast(
                requireContext(),
                alarmItem.id,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        Log.d("AlarmsFragment", "Scheduling alarm with setExactAndAllowWhileIdle at: ${alarmItem.calendar.timeInMillis}")
        
        // Schedule the alarm
        alarmManager.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            alarmItem.calendar.timeInMillis,
            pendingIntent
        )
        
        Log.d("AlarmsFragment", "Single alarm scheduled successfully: ${alarmItem.title}")
    }
    
    private fun scheduleRepeatingAlarm(alarmManager: android.app.AlarmManager, alarmItem: AlarmItem, dayOfWeek: Int) {
        // Create a calendar for the specific day of week
        val alarmCalendar = alarmItem.calendar.clone() as Calendar
        alarmCalendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        
        // If the time has already passed today, schedule for next week
        val now = Calendar.getInstance()
        if (alarmCalendar.before(now)) {
            alarmCalendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        
        val intent = android.content.Intent(requireContext(), AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmItem.id)
            putExtra("ALARM_TIME", alarmItem.time)
            putExtra("ALARM_TITLE", alarmItem.title)
            putExtra("ALARM_NOTE", alarmItem.note)
            putExtra("RINGTONE_URI", alarmItem.ringtoneUri?.toString())
            putExtra("RINGTONE_NAME", alarmItem.ringtoneName)
            putExtra("SNOOZE_MINUTES", alarmItem.snoozeMinutes)
            putExtra("VOICE_RECORDING_PATH", alarmItem.voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", alarmItem.hasVoiceOverlay)
            putExtra("ALARM_VOLUME", alarmItem.alarmVolume)
            putExtra("HAS_TTS_OVERLAY", alarmItem.hasTtsOverlay)
            putExtra("HAS_VIBRATION", alarmItem.hasVibration)
            putExtra("REPEAT_DAY", dayOfWeek)
        }
        
        // Use a safer method to generate unique request codes to prevent integer overflow
        val requestCode = Math.abs(alarmItem.id * 31 + dayOfWeek) % 1000000
        
        val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.getBroadcast(
                requireContext(),
                requestCode, // Use safer request code
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            android.app.PendingIntent.getBroadcast(
                requireContext(),
                requestCode, // Use safer request code
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        // Schedule the repeating alarm
        alarmManager.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            alarmCalendar.timeInMillis,
            pendingIntent
        )
    }
    
    private fun cancelAlarmSchedule(alarmItem: AlarmItem) {
        val alarmManager = requireContext().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        
        if (alarmItem.isRepeating()) {
            // Cancel alarms for each repeat day
            alarmItem.repeatDays.forEach { dayOfWeek ->
                val intent = android.content.Intent(requireContext(), AlarmReceiver::class.java)
                // Use the same safer method to generate request codes
                val requestCode = Math.abs(alarmItem.id * 31 + dayOfWeek) % 1000000
                val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.app.PendingIntent.getBroadcast(
                        requireContext(),
                        requestCode,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    android.app.PendingIntent.getBroadcast(
                        requireContext(),
                        requestCode,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } else {
            // Cancel single alarm
            val intent = android.content.Intent(requireContext(), AlarmReceiver::class.java)
            val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.app.PendingIntent.getBroadcast(
                    requireContext(),
                    alarmItem.id,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                android.app.PendingIntent.getBroadcast(
                    requireContext(),
                    alarmItem.id,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
    
    private fun formatTimeForDisplay(time: String): String {
        return try {
            val inputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val outputFormat = if (DateFormat.is24HourFormat(requireContext())) {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            } else {
                SimpleDateFormat("h:mm a", Locale.getDefault())
            }
            val date = inputFormat.parse(time)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            time
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        try {
            // Cancel the timer to prevent memory leaks
            timer?.cancel()
            timer = null
            _binding = null
        } catch (e: Exception) {
            Log.e("AlarmsFragment", "Error in onDestroyView", e)
        }
    }
}