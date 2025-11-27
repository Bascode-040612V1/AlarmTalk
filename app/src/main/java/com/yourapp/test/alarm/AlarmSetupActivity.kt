package com.yourapp.test.alarm

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.format.DateFormat
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AlarmSetupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ITEM = "alarm_item"
        const val EXTRA_IS_EDIT_MODE = "is_edit_mode"
        const val RESULT_ALARM_SAVED = Activity.RESULT_OK
        const val RESULT_ALARM_DELETED = Activity.RESULT_FIRST_USER + 1
        const val REQUEST_RECORD_AUDIO_PERMISSION = 1
    }

    private lateinit var timePicker: TimePicker
    private lateinit var editTextTitle: TextInputEditText
    private lateinit var editTextNote: TextInputEditText
    private lateinit var textInputLayoutNote: TextInputLayout // Added for character counter
    private lateinit var chipSunday: Chip
    private lateinit var chipMonday: Chip
    private lateinit var chipTuesday: Chip
    private lateinit var chipWednesday: Chip
    private lateinit var chipThursday: Chip
    private lateinit var chipFriday: Chip
    private lateinit var chipSaturday: Chip
    private lateinit var spinnerRingtone: Spinner
    private lateinit var spinnerSnooze: Spinner
    private lateinit var buttonSave: Button
    private lateinit var buttonDelete: Button
    private lateinit var buttonRecordVoice: Button  // Changed from ImageButton to Button
    private lateinit var buttonPlayVoice: Button    // Changed from ImageButton to Button
    private lateinit var textVoiceStatus: TextView
    private lateinit var textRecordingTimer: TextView
    private lateinit var seekBarRingtoneVolume: SeekBar
    private lateinit var textRingtoneVolume: TextView
//    private lateinit var buttonTestTts: Button  // Commented out - element doesn't exist
    private lateinit var switchTtsOverlay: SwitchCompat
    private lateinit var buttonEveryday: Button
//    private lateinit var toggleVoiceHistory: ToggleButton  // Commented out - element doesn't exist
    private lateinit var switchVibration: SwitchCompat
    
    // Add switch for simultaneous TTS and voice overlay
    private lateinit var switchSimultaneousPlayback: SwitchCompat
    
    // Add button for managing voice recordings
    private lateinit var buttonManageVoiceRecordings: Button
    
    // Advanced Options UI elements
    private lateinit var layoutAdvancedToggle: LinearLayout
    private lateinit var layoutAdvancedSettings: LinearLayout
    private lateinit var imageArrow: ImageView
    
    // Alarm properties
    private var existingAlarm: AlarmItem? = null
    private var isEditMode: Boolean = false
    
    // Ringtone properties
    private var selectedRingtoneUri: Uri? = null
    private var selectedRingtoneName: String = "Default"
    private lateinit var ringtoneNames: MutableList<String>
    private lateinit var ringtoneUris: MutableList<Uri?>
    
    // Voice Recording Variables
    private lateinit var voiceRecordingManager: VoiceRecordingManager
    private var currentVoiceRecordingPath: String? = null
    private val recordingHandler = Handler(Looper.getMainLooper())
    private var recordingStartTime: Long = 0
    private var recordingTimerRunnable: Runnable? = null
    private var isRecordingTimerActive = false
    
    // CRITICAL FIX: Add missing handler for volume slider delays
    private val handler = Handler(Looper.getMainLooper())
    
    // TTS Variables
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    private var isTtsOverlayEnabled: Boolean = false
    
    // Single alarm volume variable
    private var alarmVolume: Float = 0.8f // Default 80%
    private lateinit var audioManager: AudioManager // For system volume control
    
    // Vibration Setting - CRITICAL FIX: Ensure proper initialization
    private var hasVibration: Boolean = true // Default enabled
    
    // Sound Preview Variables
    private var ringtonePreviewPlayer: MediaPlayer? = null
    private var voicePreviewPlayer: MediaPlayer? = null
    
    // Volume synchronization
    private var volumeReceiver: BroadcastReceiver? = null

    private lateinit var dayChips: Map<Int, Chip>
    
    // Audio permission launcher
    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("AlarmSetupActivity", "Audio recording permission granted")
        } else {
            Toast.makeText(this, "Audio recording permission is required for voice alarms", Toast.LENGTH_LONG).show()
            // Disable voice recording features if permission denied
            buttonRecordVoice.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_alarm_setup)
            
            // Initialize audio manager
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Get data from intent
            existingAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_ALARM_ITEM, AlarmItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_ALARM_ITEM)
            }
            isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)

            initViews()
            setupToolbar()
            setupTimePicker()
            setupRingtoneSpinner()
            setupSnoozeSpinner()
            setupButtons()  // Initialize dayChips before it's used
            setupNewUIElements()  // Now dayChips is available when setting up the everyday button
            setupVoiceRecording()
            setupTts()
            setupVolumeControls()
            initVolumeReceiver()

            existingAlarm?.let { populateExistingAlarm(it) }

            // Request record audio permission with modern approach
            requestAudioPermission()
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error initializing AlarmSetupActivity", e)
            Toast.makeText(this, "Error initializing alarm setup: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        } catch (t: Throwable) {
            Log.e("AlarmSetupActivity", "Unexpected error in onCreate", t)
            Toast.makeText(this, "Unexpected error: ${t.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        try {
            timePicker = findViewById(R.id.timePicker)
            editTextTitle = findViewById(R.id.editTextTitle)
            editTextNote = findViewById(R.id.editTextNote)
            textInputLayoutNote = findViewById(R.id.textInputLayoutNote) // Added for character counter
            chipSunday = findViewById(R.id.chipSunday)
            chipMonday = findViewById(R.id.chipMonday)
            chipTuesday = findViewById(R.id.chipTuesday)
            chipWednesday = findViewById(R.id.chipWednesday)
            chipThursday = findViewById(R.id.chipThursday)
            chipFriday = findViewById(R.id.chipFriday)
            chipSaturday = findViewById(R.id.chipSaturday)
            spinnerRingtone = findViewById(R.id.spinnerRingtone)
            spinnerSnooze = findViewById(R.id.spinnerSnooze)
            buttonSave = findViewById(R.id.buttonSave)
            buttonDelete = findViewById(R.id.buttonDelete)
            buttonRecordVoice = findViewById(R.id.buttonRecordVoice)
            buttonPlayVoice = findViewById(R.id.buttonPlayVoice)
            textVoiceStatus = findViewById(R.id.textVoiceStatus)
            textRecordingTimer = findViewById(R.id.textRecordingTimer)
    //        buttonTestTts = findViewById(R.id.buttonTestTts)  // Commented out - element doesn't exist
            switchTtsOverlay = findViewById(R.id.switchTtsOverlay)
            buttonEveryday = findViewById(R.id.buttonEveryday)
    //        toggleVoiceHistory = findViewById(R.id.toggleVoiceHistory)  // Commented out - element doesn't exist
            switchVibration = findViewById(R.id.switchVibration)
            
            // Initialize the new simultaneous playback switch
            switchSimultaneousPlayback = findViewById(R.id.switchSimultaneousPlayback)
            
            // Initialize the new voice recordings management button
            buttonManageVoiceRecordings = findViewById(R.id.buttonManageVoiceRecordings)
            
            // Advanced Options UI elements
            layoutAdvancedToggle = findViewById(R.id.layoutAdvancedToggle)
            layoutAdvancedSettings = findViewById(R.id.layoutAdvancedSettings)
            imageArrow = findViewById(R.id.imageArrow)

            // Simplify volume controls to use only one slider for alarm volume
            seekBarRingtoneVolume = findViewById(R.id.seekBarRingtoneVolume)
            textRingtoneVolume = findViewById(R.id.textRingtoneVolume)
            
            // Hide other volume controls since we're using only one
            findViewById<View>(R.id.layoutVoiceVolume).visibility = View.GONE
            findViewById<View>(R.id.layoutTtsVolume).visibility = View.GONE
            
            // Setup single volume slider that controls system alarm volume
            seekBarRingtoneVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        alarmVolume = progress / 100.0f
                        textRingtoneVolume.text = "$progress%"
                        Log.d("AlarmSetupActivity", "Alarm volume changed to: $progress%")
                        
                        // Directly control system alarm volume
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                        val systemVolume = (alarmVolume * maxVolume).toInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, systemVolume, 0)
                        
                        // Play a brief ringtone sample to preview the volume level
                        playRingtonePreview()
                    }
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    stopRingtonePreview()
                }
                
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    // Stop preview when user stops adjusting
                    stopRingtonePreview()
                }
            })
            
            // Set initial value based on current system alarm volume
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val progress = (currentVolume.toFloat() / maxVolume.toFloat() * 100).toInt()
            seekBarRingtoneVolume.progress = progress
            textRingtoneVolume.text = "$progress%"
            alarmVolume = progress / 100.0f
            
            // Setup character counter for note field
            setupNoteCharacterCounter()
            
            // Setup advanced settings toggle
            setupAdvancedSettingsToggle()
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error initializing views", e)
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_LONG).show()
            throw e // Re-throw to be caught in onCreate
        } catch (t: Throwable) {
            Log.e("AlarmSetupActivity", "Unexpected error initializing views", t)
            Toast.makeText(this, "Unexpected UI initialization error: ${t.message}", Toast.LENGTH_LONG).show()
            throw t // Re-throw to be caught in onCreate
        }
    }

    private fun setupNoteCharacterCounter() {
        // Add text watcher to update character counter
        editTextNote.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // The TextInputLayout automatically handles the character counter
                // We just need to make sure it's visible and working
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {
                // Update TTS availability in real-time as user types
                updateTtsAvailability()
            }
        })
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = if (isEditMode) "Edit Alarm" else "Add Alarm"
        }

        // Show delete button only in edit mode
        buttonDelete.visibility = if (isEditMode) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupTimePicker() {
        // Set TimePicker to use system's 12/24-hour format
        timePicker.setIs24HourView(DateFormat.is24HourFormat(this))
    }

    private fun setupSnoozeSpinner() {
        val snoozeOptions = arrayOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "25 minutes", "30 minutes")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, snoozeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSnooze.adapter = adapter
        spinnerSnooze.setSelection(1) // Default to 10 minutes
    }

    private fun setupRingtoneSpinner() {
        val ringtoneManager = RingtoneManager(this)
        ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
        
        val cursor = ringtoneManager.cursor
        val ringtoneNames = mutableListOf<String>()
        val ringtoneUris = mutableListOf<Uri?>()
        
        // Add default option with proper URI
        ringtoneNames.add("Default")
        ringtoneUris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        
        // Add system ringtones
        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = ringtoneManager.getRingtoneUri(cursor.position)
            ringtoneNames.add(title)
            ringtoneUris.add(uri)
        }
        cursor.close()
        
        // Add custom ringtones if no system ringtones found
        if (ringtoneNames.size == 1) {
            val customRingtones = arrayOf("Beep Beep", "Rooster", "Gentle Wake", "Classic Bell", "Digital Alarm")
            ringtoneNames.addAll(customRingtones)
            customRingtones.forEach { 
                ringtoneUris.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            }
        }
        
        // Store ringtone data for later use
        this.ringtoneNames = ringtoneNames
        this.ringtoneUris = ringtoneUris
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ringtoneNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRingtone.adapter = adapter
        
        // Set default selection
        spinnerRingtone.setSelection(0)
        
        // Set listener for ringtone selection
        spinnerRingtone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedRingtoneName = ringtoneNames[position]
                selectedRingtoneUri = ringtoneUris[position]
                
                Log.d("AlarmSetupActivity", "Ringtone selected: $selectedRingtoneName, URI: $selectedRingtoneUri")
                
                // Preview the selected ringtone
                ringtoneUris[position]?.let { uri ->
                    try {
                        val ringtone = RingtoneManager.getRingtone(this@AlarmSetupActivity, uri)
                        ringtone?.play()
                        // Stop after 2 seconds
                        Handler(Looper.getMainLooper()).postDelayed({
                            ringtone?.stop()
                        }, 2000)
                    } catch (e: Exception) {
                        Log.e("AlarmSetupActivity", "Failed to preview ringtone: ${e.message}")
                    }
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun setupButtons() {
        // Initialize dayChips map for Everyday button functionality
        dayChips = mapOf(
            Calendar.SUNDAY to chipSunday,
            Calendar.MONDAY to chipMonday,
            Calendar.TUESDAY to chipTuesday,
            Calendar.WEDNESDAY to chipWednesday,
            Calendar.THURSDAY to chipThursday,
            Calendar.FRIDAY to chipFriday,
            Calendar.SATURDAY to chipSaturday
        )
        
        buttonSave.setOnClickListener { 
            try {
                saveAlarm()
            } catch (e: Exception) {
                Log.e("AlarmSetupActivity", "Error in save button click listener", e)
                Toast.makeText(this, "Error saving alarm: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Log.e("AlarmSetupActivity", "Unexpected error in save button click listener", t)
                Toast.makeText(this, "Unexpected error saving alarm: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }

        buttonDelete.setOnClickListener {
            try {
                deleteAlarm()
            } catch (e: Exception) {
                Log.e("AlarmSetupActivity", "Error in delete button click listener", e)
                Toast.makeText(this, "Error deleting alarm: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Log.e("AlarmSetupActivity", "Unexpected error in delete button click listener", t)
                Toast.makeText(this, "Unexpected error deleting alarm: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupVoiceRecording() {
        Log.d("AlarmSetupActivity", "Setting up voice recording...")
        
        // Initialize VoiceRecordingManager
        voiceRecordingManager = VoiceRecordingManager(this)
        
        // Set up listener for automatic recording stop
        voiceRecordingManager.setOnRecordingStoppedListener {
            runOnUiThread {
                // Stop the recording timer
                stopRecordingTimer()
                
                // Update UI when recording stops automatically
                buttonRecordVoice.text = "Record"
                buttonRecordVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
                textVoiceStatus.text = "Voice recorded"
                currentVoiceRecordingPath = voiceRecordingManager.stopRecording()
                if (currentVoiceRecordingPath != null) {
                    buttonPlayVoice.isEnabled = true
                    updateVolumeControlsState() // Enable voice volume control
                    
                    // Implement mutual exclusivity: if voice recording is set, disable TTS
                    if (isTtsOverlayEnabled) {
                        switchTtsOverlay.isChecked = false
                        isTtsOverlayEnabled = false
                        Toast.makeText(this, "TTS disabled - Voice recording is now active", Toast.LENGTH_SHORT).show()
                    }
                    
                    Toast.makeText(this, "Recording saved! (20 second limit reached)", Toast.LENGTH_LONG).show()
                } else {
                    textVoiceStatus.text = "Recording failed"
                    Toast.makeText(this, "Failed to save recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Set up listener for playback completion
        voiceRecordingManager.setOnPlaybackCompletedListener {
            runOnUiThread {
                // Update UI when playback completes
                buttonPlayVoice.text = "Play"
                buttonPlayVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
                textVoiceStatus.text = "Voice recorded"
                Toast.makeText(this, "Playback completed", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Simple Record button - just test if it works
        buttonRecordVoice.setOnClickListener {
            Log.d("AlarmSetupActivity", "RECORD BUTTON CLICKED!")
            
            // Check if we have permission
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                // Request permission
                Toast.makeText(this, "Requesting microphone permission...", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_PERMISSION
                )
                return@setOnClickListener
            }
            
            // If currently playing, stop playback first
            if (voiceRecordingManager.isPlaying()) {
                voiceRecordingManager.stopPlayback()
                // Reset play button to "Play" state
                buttonPlayVoice.text = "Play"
                buttonPlayVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
                textVoiceStatus.text = "Voice recorded"
            }
            
            // Toggle recording
            if (voiceRecordingManager.isRecording()) {
                // Stop recording
                val path = voiceRecordingManager.stopRecording()
                if (path != null) {
                    currentVoiceRecordingPath = path
                    
                    // Stop the recording timer
                    stopRecordingTimer()
                    
                    // Change text to "Record"
                    buttonRecordVoice.text = "Record"
                    buttonRecordVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
                    buttonPlayVoice.isEnabled = true
                    textVoiceStatus.text = "Voice recorded"
                    updateVolumeControlsState() // Enable voice volume control
                    
                    // Implement mutual exclusivity: if voice recording is set, disable TTS
                    if (isTtsOverlayEnabled) {
                        switchTtsOverlay.isChecked = false
                        isTtsOverlayEnabled = false
                        Toast.makeText(this, "TTS disabled - Voice recording is now active", Toast.LENGTH_SHORT).show()
                    }
                    
                    Toast.makeText(this, "Recording saved!", Toast.LENGTH_SHORT).show()
                } else {
                    textVoiceStatus.text = "Recording failed"
                    
                    // Stop the recording timer
                    stopRecordingTimer()
                    
                    Toast.makeText(this, "Failed to save recording", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Start recording
                val alarmId = existingAlarm?.id ?: System.currentTimeMillis().toInt()
                if (voiceRecordingManager.startRecording(alarmId)) {
                    // Record the start time
                    recordingStartTime = System.currentTimeMillis()
                    
                    // Start the recording timer
                    startRecordingTimer()
                    
                    // Change text to "Recording" and background to red
                    buttonRecordVoice.text = "Recording"
                    buttonRecordVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.delete_color))
                    textVoiceStatus.text = "Recording... (Max 20 seconds)"
                    Toast.makeText(this, "Recording started! (Max 20 seconds)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Simple Play button
        buttonPlayVoice.setOnClickListener {
            Log.d("AlarmSetupActivity", "PLAY BUTTON CLICKED!")
            
            if (currentVoiceRecordingPath == null) {
                Toast.makeText(this, "No recording to play", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // If currently recording, stop recording first
            if (voiceRecordingManager.isRecording()) {
                voiceRecordingManager.stopRecording()
                // Reset record button to "Record" state
                buttonRecordVoice.text = "Record"
                buttonRecordVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
                textVoiceStatus.text = "Voice recorded"
            }
            
            if (voiceRecordingManager.isPlaying()) {
                // Stop playback
                voiceRecordingManager.stopPlayback()
                // Change text to "Play"
                buttonPlayVoice.text = "Play"
                buttonPlayVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
                textVoiceStatus.text = "Voice recorded"
                Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
            } else {
                // Start playback
                if (voiceRecordingManager.playRecording(currentVoiceRecordingPath!!)) {
                    // Change text to "Pause"
                    buttonPlayVoice.text = "Pause"
                    buttonPlayVoice.setBackgroundColor(ContextCompat.getColor(this, R.color.delete_color))
                    textVoiceStatus.text = "Playing..."
                    Toast.makeText(this, "Playing recording", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to play recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        Log.d("AlarmSetupActivity", "Voice recording setup completed!")
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            ttsInitialized = status == TextToSpeech.SUCCESS
            if (ttsInitialized) {
                Log.d("AlarmSetupActivity", "TTS initialized successfully")
                updateTtsAvailability()
            } else {
                Log.e("AlarmSetupActivity", "Failed to initialize TTS")
            }
        }
        
        // Setup TTS Enable switch with mutual exclusivity
        switchTtsOverlay.setOnCheckedChangeListener { _, isChecked ->
            isTtsOverlayEnabled = isChecked
            updateVolumeControlsState()
            Log.d("AlarmSetupActivity", "TTS overlay enabled: $isChecked")
            
            // Implement mutual exclusivity: if TTS is enabled, disable voice recording
            if (isChecked && currentVoiceRecordingPath != null) {
                // Clear voice recording
                currentVoiceRecordingPath = null
                buttonPlayVoice.isEnabled = false
                textVoiceStatus.text = "No voice recording (TTS enabled)"
                Toast.makeText(this, "Voice recording disabled - TTS is now active", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Monitor note text changes to update TTS availability
        editTextNote.setOnFocusChangeListener { _, _ ->
            updateTtsAvailability()
        }
        
        // CRITICAL FIX: Add text change listener for real-time updates
        editTextNote.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Update TTS availability in real-time as user types
                updateTtsAvailability()
            }
        })
        
        // Update TTS availability initially
        updateTtsAvailability()
    }
    
    private fun updateTtsAvailability() {
        val noteText = editTextNote.text?.toString()?.trim()
        val hasNote = !noteText.isNullOrEmpty()
        
        if (hasNote && ttsInitialized) {
            // Enable all TTS controls when note exists and TTS is ready
//            buttonTestTts.isEnabled = true  // Commented out as the UI element doesn't exist
            switchTtsOverlay.isEnabled = true
            
        } else if (!hasNote) {
            // Disable TTS controls when no note is available
//            buttonTestTts.isEnabled = false  // Commented out as the UI element doesn't exist
            switchTtsOverlay.isEnabled = false
            switchTtsOverlay.isChecked = false
            isTtsOverlayEnabled = false
            
        } else {
            // TTS is initializing
//            buttonTestTts.isEnabled = false  // Commented out as the UI element doesn't exist
            switchTtsOverlay.isEnabled = false
        }
        
        // Update volume controls when TTS availability changes
        updateVolumeControlsState()
    }

    private fun setupNewUIElements() {
        Log.d("AlarmSetupActivity", "Setting up new UI elements...")
        
        // Setup Everyday button with TOGGLE functionality
        buttonEveryday.setOnClickListener {
            Log.d("AlarmSetupActivity", "Everyday button clicked")
            
            // Check if dayChips is initialized
            if (::dayChips.isInitialized) {
                // Check if all days are currently selected
                val allSelected = dayChips.values.all { it.isChecked }
                
                if (allSelected) {
                    // If all days are selected, deselect all
                    dayChips.values.forEach { chip ->
                        chip.isChecked = false
                    }
                    Toast.makeText(this, "All days deselected", Toast.LENGTH_SHORT).show()
                } else {
                    // If not all days are selected, select all
                    dayChips.values.forEach { chip ->
                        chip.isChecked = true
                    }
                    Toast.makeText(this, "All days selected", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("AlarmSetupActivity", "dayChips not initialized")
                Toast.makeText(this, "Error: UI not fully initialized", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Setup Vibration switch
        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            hasVibration = isChecked
            Log.d("AlarmSetupActivity", "Vibration enabled: $isChecked")
            Toast.makeText(this, if (isChecked) "Vibration enabled" else "Vibration disabled", Toast.LENGTH_SHORT).show()
        }
        
        // Setup Simultaneous Playback switch
        switchSimultaneousPlayback.setOnCheckedChangeListener { _, isChecked ->
            Log.d("AlarmSetupActivity", "Simultaneous playback enabled: $isChecked")
            Toast.makeText(this, if (isChecked) "Both TTS and voice will play simultaneously" else "TTS and voice will play separately", Toast.LENGTH_SHORT).show()
        }
        
        // Setup Voice Recordings Management button
        buttonManageVoiceRecordings.setOnClickListener {
            showVoiceHistoryDialog()
        }
        
        // CRITICAL FIX: Remove reference to toggleVoiceHistory as it doesn't exist
        
        // Setup Advanced Options collapsible section
        setupAdvancedSettingsToggle()
        
        Log.d("AlarmSetupActivity", "New UI elements setup completed!")
    }
    
    private fun setupAdvancedSettingsToggle() {
        var isAdvancedSettingsVisible = false
        
        layoutAdvancedToggle.setOnClickListener {
            isAdvancedSettingsVisible = !isAdvancedSettingsVisible
            
            // Animate the visibility change
            if (isAdvancedSettingsVisible) {
                layoutAdvancedSettings.visibility = View.VISIBLE
                // Rotate arrow 90 degrees clockwise for expanded state (pointing down)
                imageArrow.animate().rotation(90f).setDuration(300).start()
            } else {
                layoutAdvancedSettings.visibility = View.GONE
                // Rotate arrow back to original position (pointing right)
                imageArrow.animate().rotation(0f).setDuration(300).start()
            }
        }
    }
    
    private fun setupVolumeControls() {
        Log.d("AlarmSetupActivity", "Setting up enhanced contextual volume controls...")
        
        // Setup Ringtone Volume Control with safety enforcement
        seekBarRingtoneVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100.0f
                    
                    // Enforce minimum volume for safety
                    if (volume < 0.3f) {
                        seekBar?.progress = 30
                        Toast.makeText(this@AlarmSetupActivity, "Volume  below 30% for safety", Toast.LENGTH_SHORT).show()
                        return
                    }
                    
                    alarmVolume = volume
                    textRingtoneVolume.text = "$progress%"
                    
                    // Update system alarm volume when user changes slider
                    updateSystemAlarmVolume(volume)
                    
                    // Show warning for low volumes
                    if (volume < 0.5f) {
                        Toast.makeText(this@AlarmSetupActivity, "Low volume ($progress%). Alarm may be quiet!", Toast.LENGTH_SHORT).show()
                    }
                    
                    Log.d("AlarmSetupActivity", "Alarm volume: $progress%")
                    playRingtonePreview()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopRingtonePreview()
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Delayed stop to allow for continued preview during adjustment
                handler.postDelayed({ stopRingtonePreview() }, 4000)
            }
        })
        
        Log.d("AlarmSetupActivity", "Volume controls setup completed with single slider!")
    }
    
    private fun initVolumeReceiver() {
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    // Get the stream type that changed
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    
                    // Check if it's the alarm stream
                    if (streamType == AudioManager.STREAM_ALARM) {
                        // Get current system alarm volume
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                        
                        // Convert to percentage (0-100)
                        val percentage = (currentVolume.toFloat() / maxVolume.toFloat() * 100).toInt()
                        
                        // Update our slider and alarmVolume variable
                        alarmVolume = percentage / 100.0f
                        seekBarRingtoneVolume.progress = percentage
                        textRingtoneVolume.text = "$percentage%"
                        
                        Log.d("AlarmSetupActivity", "System alarm volume changed to: $percentage%")
                    }
                }
            }
        }
        
        // Register the receiver
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeReceiver, filter)
        
        // Initialize with current system volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val percentage = (currentVolume.toFloat() / maxVolume.toFloat() * 100).toInt()
        alarmVolume = percentage / 100.0f
        seekBarRingtoneVolume.progress = percentage
        textRingtoneVolume.text = "$percentage%"
        
        Log.d("AlarmSetupActivity", "Volume receiver initialized with system volume: $percentage%")
    }
    
    private fun updateSystemAlarmVolume(volume: Float) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val systemVolume = (volume * maxVolume).toInt()
            
            // Set the system alarm volume
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, systemVolume, 0)
            
            Log.d("AlarmSetupActivity", "Updated system alarm volume to: ${systemVolume}/${maxVolume} ($volume)")
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Failed to update system alarm volume", e)
        }
    }
    
    private fun updateVolumeControlsState() {
        // With single volume control, we don't need to enable/disable individual controls
        // The single volume control is always available
        Log.d("AlarmSetupActivity", "Volume controls updated - Using single volume control")
    }

    private fun populateExistingAlarm(alarm: AlarmItem) {
        try {
            // Set time
            timePicker.hour = alarm.calendar.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = alarm.calendar.get(Calendar.MINUTE)
            
            // Set title and note
            editTextTitle.setText(alarm.title)
            editTextNote.setText(alarm.note)
            
            // Set repeat days
            alarm.repeatDays.forEach { day ->
                dayChips[day]?.isChecked = true
            }
            
            // Set ringtone
            selectedRingtoneUri = alarm.ringtoneUri
            selectedRingtoneName = alarm.ringtoneName
            // Find the position of the ringtone in our lists
            val ringtonePosition = ringtoneNames.indexOf(alarm.ringtoneName)
            if (ringtonePosition >= 0) {
                spinnerRingtone.setSelection(ringtonePosition)
            } else {
                // If not found, select default
                spinnerRingtone.setSelection(0)
            }
            
            // Set snooze duration
            val snoozeIndex = when (alarm.snoozeMinutes) {
                5 -> 0
                10 -> 1
                15 -> 2
                20 -> 3
                25 -> 4
                30 -> 5
                else -> 1 // Default to 10 minutes
            }
            spinnerSnooze.setSelection(snoozeIndex)
            
            // Set voice recording
            currentVoiceRecordingPath = alarm.voiceRecordingPath
            
            // Update UI based on voice recording
            if (currentVoiceRecordingPath != null) {
                buttonPlayVoice.isEnabled = true
                textVoiceStatus.text = "Voice recorded"
            }
            
            // Set TTS
            isTtsOverlayEnabled = alarm.hasTtsOverlay
            switchTtsOverlay.isChecked = isTtsOverlayEnabled
            
            // Implement mutual exclusivity when loading existing alarm
            // If both TTS and voice recording are enabled, prioritize voice recording
            if (alarm.hasTtsOverlay && alarm.voiceRecordingPath != null) {
                // Voice recording takes precedence
                switchTtsOverlay.isChecked = false
                isTtsOverlayEnabled = false
                Toast.makeText(this, "Note: TTS disabled to prioritize voice recording", Toast.LENGTH_LONG).show()
            }
            
            // Set TTS voice selection - Removed: Using phone's default TTS voice
            
            // Set volumes - Use single alarm volume
            alarmVolume = alarm.alarmVolume
            
            // Update seek bars
            seekBarRingtoneVolume.progress = (alarmVolume * 100).toInt()
            
            // Sync with system volume
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val percentage = (currentVolume.toFloat() / maxVolume.toFloat() * 100).toInt()
            textRingtoneVolume.text = "$percentage%"
            
            // Set vibration
            hasVibration = alarm.hasVibration
            switchVibration.isChecked = hasVibration
            
            // Set simultaneous playback
            switchSimultaneousPlayback.isChecked = alarm.allowSimultaneousPlayback
            
            // Update volume controls visibility
            updateVolumeControlsState()
            
            Log.d("AlarmSetupActivity", "Populated existing alarm: ${alarm.title}")
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error populating existing alarm", e)
            Toast.makeText(this, "Error loading alarm data: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Log.e("AlarmSetupActivity", "Unexpected error populating existing alarm", t)
            Toast.makeText(this, "Unexpected error loading alarm data: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAlarm() {
        Log.d("AlarmSetupActivity", "Saving alarm...")
        
        try {
            // Get the current time to ensure we're working with the correct date
            val now = Calendar.getInstance()

            val calendar = Calendar.getInstance().apply {
                // Set the date to today first
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, now.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))

                // Set the time from TimePicker (this handles 24-hour format correctly)
                set(Calendar.HOUR_OF_DAY, timePicker.hour)
                set(Calendar.MINUTE, timePicker.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // If the selected time has already passed today, schedule for tomorrow
                if (before(now)) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val title = editTextTitle.text?.toString()?.takeIf { it.isNotBlank() } ?: "Alarm Title"
            val note = editTextNote.text?.toString() ?: ""

            val repeatDays = dayChips.filter { it.value.isChecked }.keys.toSet()

            val snoozeMinutes = when (spinnerSnooze.selectedItemPosition) {
                0 -> 5
                1 -> 10
                2 -> 15
                3 -> 20
                4 -> 25
                5 -> 30
                else -> 10
            }

            // Format time string for display using 24-hour format
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val timeString = timeFormat.format(calendar.time)

            // Log the scheduled time for debugging
            Log.d("AlarmSetupActivity", "Scheduling alarm for: $timeString (${calendar.timeInMillis})")
            Log.d("AlarmSetupActivity", "TimePicker values - Hour: ${timePicker.hour}, Minute: ${timePicker.minute}")

            val alarmItem = AlarmItem(
                id = existingAlarm?.id ?: generateSafeAlarmId(),
                time = timeString,
                isEnabled = true,
                calendar = calendar,
                repeatDays = repeatDays,
                title = title,
                note = note,
                ringtoneUri = selectedRingtoneUri,
                ringtoneName = selectedRingtoneName,
                snoozeMinutes = snoozeMinutes,
                voiceRecordingPath = currentVoiceRecordingPath,
                hasVoiceOverlay = currentVoiceRecordingPath != null, // Enable voice overlay if a recording exists
                alarmVolume = alarmVolume, // User-selected alarm volume
                hasTtsOverlay = isTtsOverlayEnabled, // User-selected TTS overlay
                ttsVoice = "female", // Using phone's default TTS voice (female)
                hasVibration = hasVibration, // User-selected vibration setting - CRITICAL FIX: Ensure proper initialization
                allowSimultaneousPlayback = switchSimultaneousPlayback.isChecked // New setting for simultaneous playback
            )

            Log.d("AlarmSetupActivity", "Created alarm item: ${alarmItem.title} (ID: ${alarmItem.id})")
            
            // Fix the Parcelable passing - use apply block like in deleteAlarm
            val resultIntent = Intent().apply {
                putExtra(EXTRA_ALARM_ITEM, alarmItem)
            }
            
            Log.d("AlarmSetupActivity", "Setting result and finishing activity")
            setResult(RESULT_ALARM_SAVED, resultIntent)
            
            // Show a toast to confirm the alarm was saved
            val repeatText = if (alarmItem.isRepeating()) " (${alarmItem.getRepeatDaysString()})" else ""
            Toast.makeText(this, "Alarm '${alarmItem.title}' set for ${timeString}$repeatText", Toast.LENGTH_SHORT).show()
            
            finish()
            
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error saving alarm", e)
            Toast.makeText(this, "Error saving alarm: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Log.e("AlarmSetupActivity", "Unexpected error saving alarm", t)
            Toast.makeText(this, "Unexpected error saving alarm: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteAlarm() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Delete Alarm")
                .setMessage("Are you sure you want to delete this alarm?")
                .setPositiveButton("Delete") { _, _ ->
                    try {
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_ALARM_ITEM, existingAlarm)
                        }
                        setResult(RESULT_ALARM_DELETED, resultIntent)
                        finish()
                    } catch (e: Exception) {
                        Log.e("AlarmSetupActivity", "Error setting delete result", e)
                        Toast.makeText(this, "Error deleting alarm: ${e.message}", Toast.LENGTH_SHORT).show()
                    } catch (t: Throwable) {
                        Log.e("AlarmSetupActivity", "Unexpected error setting delete result", t)
                        Toast.makeText(this, "Unexpected error deleting alarm: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error showing delete dialog", e)
            Toast.makeText(this, "Error showing delete confirmation: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Log.e("AlarmSetupActivity", "Unexpected error showing delete dialog", t)
            Toast.makeText(this, "Unexpected error showing delete confirmation: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted! Tap Record to start recording.", Toast.LENGTH_SHORT).show()
                textVoiceStatus.text = "Ready to record"
            } else {
                Toast.makeText(this, " Microphone permission denied", Toast.LENGTH_SHORT).show()
                textVoiceStatus.text = "Permission denied"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showVoiceHistoryDialog() {
        try {
            // Get the app's private directory where voice recordings are stored
            val voiceDir = File(filesDir, "alarm_voice_recordings")
            if (!voiceDir.exists()) {
                Toast.makeText(this, "No recorded voices found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Get all .3gp files in the voice recordings directory
            val voiceFiles = voiceDir.listFiles { file ->
                file.isFile && file.extension.equals("3gp", ignoreCase = true)
            }
            
            if (voiceFiles.isNullOrEmpty()) {
                Toast.makeText(this, "No recorded voices found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Sort files by last modified (newest first)
            val sortedFiles = voiceFiles.sortedByDescending { it.lastModified() }
            
            // Create voice recording objects with sequential naming (Voice_1, Voice_2, etc.)
            val voiceRecordings = sortedFiles.mapIndexed { index, file ->
                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                val date = dateFormat.format(java.util.Date(file.lastModified()))
                
                // Calculate duration
                val durationMs = VoiceRecordingManager(this).getRecordingDuration(file.absolutePath)
                val duration = String.format("%02d:%02d", durationMs / 60000, (durationMs % 60000) / 1000)
                
                VoiceRecording(file, "Voice_${index + 1}", date, "Duration: $duration")
            }
            
            // Create and show modern dialog
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_voice_selection, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerViewVoiceList)
            val buttonCancel = dialogView.findViewById<Button>(R.id.buttonCancel)
            val buttonSelect = dialogView.findViewById<Button>(R.id.buttonSelect)
            val buttonSelectAll = dialogView.findViewById<Button>(R.id.buttonSelectAll)
            val buttonDeleteSelected = dialogView.findViewById<Button>(R.id.buttonDeleteSelected)
            // New buttons
            val buttonRenameSequentially = dialogView.findViewById<Button>(R.id.buttonRenameSequentially)
            val buttonPlayAll = dialogView.findViewById<Button>(R.id.buttonPlayAll)
            
            // Use a nullable variable to avoid circular reference
            var adapter: VoiceRecordingAdapter? = null
            adapter = VoiceRecordingAdapter(voiceRecordings, 
                onItemClick = { /* Handle item selection */ },
                onPlayClick = { voiceRecording ->
                    // Play the selected voice recording
                    VoiceRecordingManager(this).playRecording(voiceRecording.file.absolutePath)
                },
                onDeleteClick = { voiceRecording ->
                    // Delete the selected voice recording
                    adapter?.let { deleteVoiceRecording(voiceRecording, it) }
                }
            )
            
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
            
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            
            buttonCancel.setOnClickListener {
                VoiceRecordingManager(this).stopPlayback()
                dialog.dismiss()
            }
            
            buttonSelect.setOnClickListener {
                val selectedRecording = adapter.getSelectedRecording()
                if (selectedRecording != null) {
                    selectVoiceFile(selectedRecording.file)
                    VoiceRecordingManager(this).stopPlayback()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Please select a voice recording", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Handle Select All button
            buttonSelectAll.setOnClickListener {
                adapter.selectAll()
                Toast.makeText(this, "All items selected", Toast.LENGTH_SHORT).show()
            }
            
            // Handle Delete Selected button
            buttonDeleteSelected.setOnClickListener {
                val selectedRecordings = adapter.getSelectedRecordings()
                if (selectedRecordings.isEmpty()) {
                    Toast.makeText(this, "No items selected for deletion", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // Confirm deletion of selected items
                AlertDialog.Builder(this)
                    .setTitle("Delete Selected Voice Recordings")
                    .setMessage("Are you sure you want to delete ${selectedRecordings.size} selected voice recording(s)?")
                    .setPositiveButton("Delete") { _, _ ->
                        // Delete all selected files
                        val deletedRecordings = mutableListOf<VoiceRecording>()
                        for (recording in selectedRecordings) {
                            if (recording.file.delete()) {
                                deletedRecordings.add(recording)
                                
                                // Reset voice recording UI if this was the current recording
                                if (currentVoiceRecordingPath == recording.file.absolutePath) {
                                    currentVoiceRecordingPath = null
                                    buttonPlayVoice.isEnabled = false
                                    textVoiceStatus.text = "No voice recording"
                                }
                            }
                        }
                        
                        // Remove from adapter
                        adapter.removeRecordings(deletedRecordings.toList())
                        
                        Toast.makeText(this, "${deletedRecordings.size} voice recording(s) deleted", Toast.LENGTH_SHORT).show()
                        
                        // Clear selection after deletion
                        adapter.clearSelection()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            
            // Handle Rename Sequentially button
            buttonRenameSequentially.setOnClickListener {
                renameVoiceRecordingsSequentially(voiceRecordings, adapter)
                Toast.makeText(this, getString(R.string.voice_renamed), Toast.LENGTH_SHORT).show()
            }
            
            // Handle Play All button
            buttonPlayAll.setOnClickListener {
                playAllVoiceRecordings(voiceRecordings)
                Toast.makeText(this, getString(R.string.playing_all_recordings), Toast.LENGTH_SHORT).show()
            }
            
            dialog.show()
                
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error showing voice history dialog", e)
            Toast.makeText(this, "Error loading voice history", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Rename voice recordings sequentially (Voice_1, Voice_2, etc.)
     */
    private fun renameVoiceRecordingsSequentially(voiceRecordings: List<VoiceRecording>, adapter: VoiceRecordingAdapter) {
        try {
            // Sort recordings by name to ensure consistent ordering
            val sortedRecordings = voiceRecordings.sortedBy { it.name }
            
            // Rename each file sequentially
            for (i in sortedRecordings.indices) {
                val recording = sortedRecordings[i]
                val newName = "Voice_${i + 1}.${recording.file.extension}"
                val newFile = File(recording.file.parentFile, newName)
                
                // Only rename if the name is different
                if (recording.file.name != newName) {
                    if (recording.file.renameTo(newFile)) {
                        Log.d("AlarmSetupActivity", "Renamed ${recording.file.name} to $newName")
                    } else {
                        Log.e("AlarmSetupActivity", "Failed to rename ${recording.file.name} to $newName")
                    }
                }
            }
            
            // Refresh the adapter to show updated names
            adapter.notifyDataSetChanged()
            
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error renaming voice recordings", e)
            Toast.makeText(this, "Error renaming voice recordings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Play all voice recordings sequentially
     */
    private fun playAllVoiceRecordings(voiceRecordings: List<VoiceRecording>) {
        try {
            // Stop any currently playing recording
            VoiceRecordingManager(this).stopPlayback()
            
            // Sort recordings by name to play them in order
            val sortedRecordings = voiceRecordings.sortedBy { it.name }
            
            // Play each recording with a delay between them
            playRecordingsWithDelay(sortedRecordings, 0)
            
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error playing all voice recordings", e)
            Toast.makeText(this, "Error playing all voice recordings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Play recordings with delay between each one
     */
    private fun playRecordingsWithDelay(recordings: List<VoiceRecording>, index: Int) {
        if (index >= recordings.size) return
        
        val recording = recordings[index]
        
        // Play the current recording
        VoiceRecordingManager(this).playRecording(recording.file.absolutePath)
        
        // Schedule the next recording after a delay
        // We estimate the delay based on the recording duration plus some buffer
        val durationMs = VoiceRecordingManager(this).getRecordingDuration(recording.file.absolutePath)
        val delay = durationMs + 1000 // Add 1 second buffer
        
        Handler(Looper.getMainLooper()).postDelayed({
            playRecordingsWithDelay(recordings, index + 1)
        }, delay.toLong())
    }
    
    private fun selectVoiceFile(file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(this, "Voice file not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Set the selected voice file
            currentVoiceRecordingPath = file.absolutePath
            
            // Update UI
            buttonPlayVoice.isEnabled = true
            textVoiceStatus.text = "Voice selected from history"
            
            // Update volume controls
            updateVolumeControlsState()
            
            val timestamp = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(file.lastModified()))
            Toast.makeText(this, "Selected voice from $timestamp", Toast.LENGTH_SHORT).show()
            
            Log.d("AlarmSetupActivity", "Voice file selected from history: ${file.absolutePath}")
            
            // Implement mutual exclusivity: if voice recording is selected, disable TTS
            if (isTtsOverlayEnabled) {
                switchTtsOverlay.isChecked = false
                isTtsOverlayEnabled = false
                Toast.makeText(this, "TTS disabled - Voice recording is now active", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error selecting voice file", e)
            Toast.makeText(this, "Error selecting voice file", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun deleteVoiceRecording(voiceRecording: VoiceRecording, adapter: VoiceRecordingAdapter) {
        try {
            // Confirm deletion
            AlertDialog.Builder(this)
                .setTitle("Delete Voice Recording")
                .setMessage("Are you sure you want to delete this voice recording?")
                .setPositiveButton("Delete") { _, _ ->
                    // Delete the file
                    if (voiceRecording.file.delete()) {
                        // Remove from adapter
                        adapter.removeRecording(voiceRecording)
                        
                        // Reset voice recording UI if this was the current recording
                        if (currentVoiceRecordingPath == voiceRecording.file.absolutePath) {
                            currentVoiceRecordingPath = null
                            buttonPlayVoice.isEnabled = false

                            textVoiceStatus.text = "No voice recording"
                        }
                        
                        Toast.makeText(this, "Voice recording deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to delete voice recording", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error deleting voice recording", e)
            Toast.makeText(this, "Error deleting voice recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Sound Preview Functions
    private fun playRingtonePreview() {
        try {
            // Stop any existing preview
            stopRingtonePreview()
            
            // Create and configure MediaPlayer for ringtone preview
            ringtonePreviewPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                
                setDataSource(this@AlarmSetupActivity, selectedRingtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                // CRITICAL FIX: Apply current alarm volume to preview
                setVolume(alarmVolume, alarmVolume)
                
                setOnPreparedListener {
                    start()
                    // Stop after 4 seconds preview (as requested by user)
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopRingtonePreview()
                    }, 4000)
                }
                
                setOnErrorListener { _, _, _ ->
                    Log.e("AlarmSetupActivity", "Error playing ringtone preview")
                    false
                }
                
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error starting ringtone preview", e)
        }
    }
    
    private fun stopRingtonePreview() {
        try {
            ringtonePreviewPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            ringtonePreviewPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error stopping ringtone preview", e)
        }
    }
    
    private fun playVoicePreview() {
        try {
            currentVoiceRecordingPath?.let { path ->
                // Stop any existing voice preview
                stopVoicePreview()
                
                // Check if file exists
                val file = File(path)
                if (!file.exists()) {
                    Log.e("AlarmSetupActivity", "Voice recording file not found: $path")
                    return
                }
                
                voicePreviewPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    
                    setDataSource(path)
                    // CRITICAL FIX: Apply current alarm volume to preview
                    setVolume(alarmVolume, alarmVolume)
                    
                    setOnPreparedListener {
                        start()
                        // Stop after 4 seconds preview (as requested by user)
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopVoicePreview()
                        }, 4000)
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e("AlarmSetupActivity", "Voice preview error: what=$what, extra=$extra")
                        false
                    }
                    
                    prepareAsync()
                }
                
                Log.d("AlarmSetupActivity", "Voice preview started with volume: $alarmVolume")
            }
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error starting voice preview", e)
        }
    }
    
    private fun stopVoicePreview() {
        try {
            voicePreviewPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            voicePreviewPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error stopping voice preview", e)
        }
    }
    
    private fun stopAllPreviews() {
        stopRingtonePreview()
        stopVoicePreview()
    }
    
    private fun requestAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                Log.d("AlarmSetupActivity", "Audio recording permission already granted")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show rationale dialog
                AlertDialog.Builder(this)
                    .setTitle("Audio Recording Permission")
                    .setMessage("This app needs audio recording permission to create voice alarms. Voice recordings will be used only for alarm customization.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    .setNegativeButton("Skip") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this, "Voice recording features will be disabled", Toast.LENGTH_LONG).show()
                        disableVoiceFeatures()
                    }
                    .show()
            }
            else -> {
                // Request permission directly
                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun disableVoiceFeatures() {
        buttonRecordVoice.isEnabled = false
        buttonPlayVoice.isEnabled = false

        textVoiceStatus.text = "Voice recording unavailable - permission denied"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop recording timer
        stopRecordingTimer()
        
        // Unregister volume receiver
        try {
            volumeReceiver?.let { 
                unregisterReceiver(it)
                volumeReceiver = null
            }
        } catch (e: Exception) {
            Log.e("AlarmSetupActivity", "Error unregistering volume receiver", e)
        }
        
        // Clean up sound preview players
        stopAllPreviews()
        
        // Clean up TTS
        tts?.shutdown()
    }
    
    // Add a helper method to generate safe alarm IDs
    private fun generateSafeAlarmId(): Int {
        // Use a safer method to generate alarm IDs to prevent integer overflow
        // Take absolute value of current time modulo a large prime number
        return Math.abs(System.currentTimeMillis().toInt() % 1000000)
    }
    
    // Voice recording timer methods
    private fun startRecordingTimer() {
        // Stop any existing timer
        stopRecordingTimer()
        
        // Show the timer display
        textRecordingTimer.visibility = View.VISIBLE
        
        // Mark timer as active
        isRecordingTimerActive = true
        
        // Create and start the timer runnable
        recordingTimerRunnable = object : Runnable {
            override fun run() {
                if (isRecordingTimerActive && voiceRecordingManager.isRecording()) {
                    // Calculate elapsed time
                    val elapsedMillis = System.currentTimeMillis() - recordingStartTime
                    val seconds = (elapsedMillis / 1000).toInt()
                    val minutes = seconds / 60
                    val remainingSeconds = seconds % 60
                    
                    // Format as MM:SS
                    val timeString = String.format("%02d:%02d", minutes, remainingSeconds)
                    textRecordingTimer.text = timeString
                    
                    // Schedule next update in 1 second
                    recordingHandler.postDelayed(this, 1000)
                }
            }
        }
        
        // Start the timer
        recordingTimerRunnable?.let { 
            recordingHandler.post(it)
        }
        
        Log.d("AlarmSetupActivity", "Recording timer started")
    }
    
    private fun stopRecordingTimer() {
        isRecordingTimerActive = false
        val runnable = recordingTimerRunnable
        if (runnable != null) {
            recordingHandler.removeCallbacks(runnable)
        }
        recordingTimerRunnable = null
        
        // Hide the timer display
        textRecordingTimer.visibility = View.GONE
        textRecordingTimer.text = "00:00"
        
        Log.d("AlarmSetupActivity", "Recording timer stopped")
    }
}