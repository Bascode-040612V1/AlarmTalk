package com.yourapp.test.alarm

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class AlarmScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_TIME = "alarm_time"
        const val EXTRA_ALARM_TITLE = "alarm_title"
        const val EXTRA_ALARM_NOTE = "alarm_note"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_RINGTONE_NAME = "ringtone_name"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val EXTRA_VOICE_RECORDING_PATH = "voice_recording_path"
        const val EXTRA_HAS_VOICE_OVERLAY = "has_voice_overlay"
        const val EXTRA_HAS_TTS_OVERLAY = "has_tts_overlay"
        const val EXTRA_ALARM_VOLUME = "alarm_volume" // Single alarm volume
        const val EXTRA_TTS_VOICE = "tts_voice"
        const val EXTRA_HAS_VIBRATION = "has_vibration"
    }

    private lateinit var textTime: TextView
    private lateinit var textDate: TextView
    private lateinit var textTitle: TextView
    private lateinit var textNote: TextView
    private lateinit var scrollNoteContainer: ScrollView
    private lateinit var imageAlarmBell: ImageView
    private lateinit var buttonSnooze: MaterialButton
    private lateinit var buttonDismiss: MaterialButton

    private var mediaPlayer: MediaPlayer? = null
    private var voiceMediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var audioSequenceManager: AudioSequenceManager? = null
    private var bellAnimator: AnimatorSet? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeUpdateRunnable: Runnable? = null
    private var stopAlarmReceiver: BroadcastReceiver? = null
    private var volumeIncreaseRunnable: Runnable? = null
    private var currentVolume: Float = 0.1f // Start at 10% volume
    private val maxVolume: Float = 1.0f
    private val volumeIncreaseInterval: Long = 2000 // Increase every 2 seconds
    private val volumeIncreaseStep: Float = 0.1f // Increase by 10% each step
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var alarmId: Int = 0
    private var alarmTitle: String = ""
    private var alarmNote: String = ""
    private var ringtoneUri: Uri? = null
    private var ringtoneName: String = ""
    private var snoozeMinutes: Int = 10
    private var voiceRecordingPath: String? = null
    private var hasVoiceOverlay: Boolean = false
    private var hasTtsOverlay: Boolean = false
    private var alarmVolume: Float = 0.8f // Single alarm volume
    private var hasVibration: Boolean = true
    private var isDismissInitiatedByBroadcast = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("AlarmScreenActivity", "onCreate called with intent: $intent")
        Log.d("AlarmScreenActivity", "Intent extras: ${intent.extras}")
        Log.d("AlarmScreenActivity", "Activity task ID: ${this.taskId}, Process ID: ${android.os.Process.myPid()}")
        
        // Initialize audio manager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Enhanced lock screen display - works on all Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            Log.d("AlarmScreenActivity", "Using modern lock screen display methods (Android 8.0+)")
        } else {
            // Legacy support for older Android versions
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
            Log.d("AlarmScreenActivity", "Using legacy lock screen display methods (Android < 8.0)")
        }
        
        // Comprehensive window flags for maximum lock screen compatibility
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        
        // For Android 8.0+ (API 26+), request keyguard dismissal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
                Log.d("AlarmScreenActivity", "Keyguard dismissal requested")
            } catch (e: SecurityException) {
                Log.e("AlarmScreenActivity", "SecurityException: Failed to request keyguard dismissal. This might be due to missing permissions or device restrictions.", e)
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Failed to request keyguard dismissal", e)
            }
        }
        
        // Ensure fullscreen display
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        Log.d("AlarmScreenActivity", "Window flags set. Checking if activity is visible...")
        
        try {
            Log.d("AlarmScreenActivity", "Attempting to inflate activity_alarm_screen layout...")
            setContentView(R.layout.activity_alarm_screen)
            Log.d("AlarmScreenActivity", "✅ Layout set successfully")
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.e("AlarmScreenActivity", "❌ CRITICAL FAILURE: Layout resource not found. This might be due to missing layout resources.", e)
            Log.e("AlarmScreenActivity", "DIAGNOSTIC INFO:")
            Log.e("AlarmScreenActivity", "- Check if R.layout.activity_alarm_screen exists")
            Log.e("AlarmScreenActivity", "- Verify all referenced drawables and resources exist")
            Toast.makeText(this, "❌ CRITICAL ERROR: Layout resource not found - ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: android.view.InflateException) {
            Log.e("AlarmScreenActivity", "❌ CRITICAL FAILURE: Failed to inflate layout. This might be due to issues in the XML layout file.", e)
            Log.e("AlarmScreenActivity", "DIAGNOSTIC INFO:")
            Log.e("AlarmScreenActivity", "- Check for errors in activity_alarm_screen.xml")
            Log.e("AlarmScreenActivity", "- Verify all referenced drawables and resources exist")
            Toast.makeText(this, "❌ CRITICAL ERROR: Failed to inflate layout - ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ CRITICAL FAILURE: Failed to set content view. This might be due to missing layout resources or inflation errors.", e)
            Log.e("AlarmScreenActivity", "DIAGNOSTIC INFO:")
            Log.e("AlarmScreenActivity", "- Check if R.layout.activity_alarm_screen exists")
            Log.e("AlarmScreenActivity", "- Verify all referenced drawables and resources exist")
            Log.e("AlarmScreenActivity", "- Check for resource qualifier issues (dpi, orientation, etc.)")
            Toast.makeText(this, "❌ CRITICAL ERROR: Failed to initialize alarm screen UI - ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Setup back button handling with modern OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevent accidental back button dismissal
                Log.d("AlarmScreenActivity", "Back button pressed - showing warning")
                showBackButtonWarning()
            }
        })
        
        // Initialize audio sequence manager
        audioSequenceManager = AudioSequenceManager(this)
        
        try {
            Log.d("AlarmScreenActivity", "=== STARTING ALARM SCREEN INITIALIZATION ===")
            getIntentExtras()
            Log.d("AlarmScreenActivity", "✅ Intent extras parsed successfully")
            initViews()
            Log.d("AlarmScreenActivity", "✅ Views initialized successfully")
            setupUI()
            Log.d("AlarmScreenActivity", "✅ UI setup completed successfully")
            
            // Start bell animation after UI is fully set up
            handler.postDelayed({
                startAnimatedBell()
                Log.d("AlarmScreenActivity", "✅ Bell animation started successfully")
            }, 100)
            
            acquireWakeLock()
            Log.d("AlarmScreenActivity", "✅ Wake lock acquired successfully")
            setupStopAlarmReceiver()
            Log.d("AlarmScreenActivity", "✅ Stop alarm receiver setup completed successfully")
            playAlarmSound()
            Log.d("AlarmScreenActivity", "✅ Alarm sound started successfully")
            startTimeUpdates()
            Log.d("AlarmScreenActivity", "✅ Time updates started successfully")
            
            Log.d("AlarmScreenActivity", "🎉 ALARM SCREEN INITIALIZATION COMPLETED SUCCESSFULLY")
        } catch (e: SecurityException) {
            Log.e("AlarmScreenActivity", "❌ SECURITY ERROR: During onCreate initialization. This might be due to missing permissions.", e)
            Log.e("AlarmScreenActivity", "DIAGNOSTIC INFO:")
            Log.e("AlarmScreenActivity", "- Check SYSTEM_ALERT_WINDOW permission (Draw over other apps)")
            Log.e("AlarmScreenActivity", "- Check notification permissions")
            Log.e("AlarmScreenActivity", "- Verify app is not restricted by device policy")
            Toast.makeText(this, "❌ SECURITY ERROR: Failed to initialize alarm screen - ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ RUNTIME ERROR: During onCreate initialization. This could be due to missing permissions, device restrictions, or UI inflation issues.", e)
            Log.e("AlarmScreenActivity", "DIAGNOSTIC INFO:")
            Log.e("AlarmScreenActivity", "- Check logcat for more detailed error information")
            Log.e("AlarmScreenActivity", "- Verify all required permissions are granted")
            Log.e("AlarmScreenActivity", "- Check if device has battery optimization enabled for this app")
            Log.e("AlarmScreenActivity", "- Verify layout resources are properly configured")
            Toast.makeText(this, "❌ INITIALIZATION ERROR: Failed to initialize alarm screen - ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Restart bell animation when activity resumes to ensure it's visible
        handler.postDelayed({
            startAnimatedBell()
        }, 50)
        Log.d("AlarmScreenActivity", "onResume called - bell animation restarted")
    }

    private fun getIntentExtras() {
        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        alarmTitle = intent.getStringExtra(EXTRA_ALARM_TITLE) ?: "Alarm Title"
        alarmNote = intent.getStringExtra(EXTRA_ALARM_NOTE) ?: ""
        ringtoneName = intent.getStringExtra(EXTRA_RINGTONE_NAME) ?: "Default"
        snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        
        val ringtoneUriString = intent.getStringExtra(EXTRA_RINGTONE_URI)
        
        // Fix: Properly handle ringtone URI - don't default to alarm if custom ringtone is selected
        ringtoneUri = if (!ringtoneUriString.isNullOrEmpty() && ringtoneUriString != "null") {
            try {
                val parsedUri = Uri.parse(ringtoneUriString)
                parsedUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Failed to parse ringtone URI: $ringtoneUriString", e)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
        } else {
            // Only use default if no custom ringtone was selected
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        
        voiceRecordingPath = intent.getStringExtra(EXTRA_VOICE_RECORDING_PATH)
        hasVoiceOverlay = intent.getBooleanExtra(EXTRA_HAS_VOICE_OVERLAY, false)
        hasTtsOverlay = intent.getBooleanExtra(EXTRA_HAS_TTS_OVERLAY, false)
        // Use single alarm volume instead of separate volumes
        alarmVolume = intent.getFloatExtra(EXTRA_ALARM_VOLUME, 0.8f)
        hasVibration = intent.getBooleanExtra(EXTRA_HAS_VIBRATION, true)
        
        Log.d("AlarmScreenActivity", "Alarm screen started - Title: $alarmTitle, Ringtone: $ringtoneName, URI: $ringtoneUri, Volume: $alarmVolume")
    }

    private fun initViews() {
        try {
            textTime = findViewById(R.id.textTime)
            textDate = findViewById(R.id.textDate)
            textTitle = findViewById(R.id.textTitle)
            textNote = findViewById(R.id.textNote)
            scrollNoteContainer = findViewById(R.id.scrollNoteContainer)
            imageAlarmBell = findViewById(R.id.imageAlarmBell)
            buttonSnooze = findViewById(R.id.buttonSnooze)
            buttonDismiss = findViewById(R.id.buttonDismiss)
            
            // Verify that imageAlarmBell was found
            if (imageAlarmBell == null) {
                Log.e("AlarmScreenActivity", "❌ CRITICAL ERROR: imageAlarmBell view not found in layout")
                throw IllegalStateException("imageAlarmBell view not found in layout")
            }
            
            Log.d("AlarmScreenActivity", "✅ All views initialized successfully")
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ ERROR: Failed to initialize views", e)
            throw e
        }
    }

    private fun setupUI() {
        textTitle.text = alarmTitle
        
        if (alarmNote.isNotEmpty()) {
            textNote.text = alarmNote
            textNote.visibility = View.VISIBLE
            scrollNoteContainer.visibility = View.VISIBLE
        } else {
            textNote.visibility = View.GONE
            scrollNoteContainer.visibility = View.GONE
        }

        buttonSnooze.text = "Snooze ($snoozeMinutes min)"
        
        buttonSnooze.setOnClickListener {
            snoozeAlarm()
        }
        
        buttonDismiss.setOnClickListener {
            dismissAlarm()
        }
        
        updateTimeAndDate()
        
        // Ensure bell animation is started after UI is fully set up
        handler.postDelayed({
            startAnimatedBell()
        }, 200)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                PowerManager.ON_AFTER_RELEASE,
                "AlarmApp:AlarmScreenWakeLock"
            )
            // Check if we already hold a wake lock before acquiring
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
                Log.d("AlarmScreenActivity", "Wake lock acquired successfully")
            } else {
                Log.d("AlarmScreenActivity", "Wake lock already held or null")
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error acquiring wake lock: ${e.message}")
        }
    }

    private fun playAlarmSound() {
        Log.d("AlarmScreenActivity", "Starting sequential alarm sound playback")
        
        try {
            // Stop any existing playback
            stopAlarmSound()
            
            // Request audio focus first
            if (!requestAudioFocus()) {
                Log.w("AlarmScreenActivity", "Failed to gain audio focus, playing anyway")
            }
            
            // Initialize and start the audio sequence manager with user-selected volume
            audioSequenceManager?.let { manager ->
                manager.initialize(
                    ringtoneUri = ringtoneUri,
                    // Pass the full alarm volume - AudioSequenceManager will adjust based on other settings
                    ringtoneVolume = alarmVolume,
                    voiceRecordingPath = voiceRecordingPath,
                    // Voice always plays at 100% of selected volume
                    voiceVolume = alarmVolume,
                    ttsText = alarmNote,
                    // TTS always plays at 100% of selected volume
                    ttsVolume = alarmVolume,
                    hasVoiceOverlay = hasVoiceOverlay,
                    hasTtsOverlay = hasTtsOverlay
                )
                
                manager.startSequence()
                Log.d("AlarmScreenActivity", "✅ Audio sequence started successfully with user-selected volume: $alarmVolume")
            } ?: run {
                Log.e("AlarmScreenActivity", "❌ AudioSequenceManager not initialized")
                // Fallback to old system if sequence manager fails
                playFallbackSound()
            }
            
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to start audio sequence", e)
            // Try fallback sound
            try {
                playFallbackSound()
            } catch (fallbackEx: Exception) {
                Log.e("AlarmScreenActivity", "Fallback sound also failed", fallbackEx)
                // Show error to user as last resort
                Toast.makeText(this, "Failed to play alarm sound: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startRingtonePlayback() {
        try {
            mediaPlayer = MediaPlayer().apply {
                // Set audio attributes for alarm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                }
                
                // Set data source with fallback
                val uri = ringtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                
                try {
                    setDataSource(this@AlarmScreenActivity, uri)
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Failed to set ringtone data source: $uri", e)
                    // Try with default alarm sound
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    setDataSource(this@AlarmScreenActivity, defaultUri)
                }
                
                // Configure looping and USE USER-SELECTED VOLUME
                isLooping = true
                
                // Adjust volume based on whether voice/TTS is enabled
                val adjustedVolume = if (hasVoiceOverlay || hasTtsOverlay) {
                    alarmVolume * 0.5f // 50% volume when voice/TTS is enabled
                } else {
                    alarmVolume // 100% volume when only ringtone is enabled
                }
                
                setVolume(adjustedVolume, adjustedVolume)
                
                // Add error listener
                setOnErrorListener { _, what, extra ->
                    Log.e("AlarmScreenActivity", "MediaPlayer error: what=$what, extra=$extra")
                    // Try to recover by playing fallback sound
                    try {
                        playFallbackSound()
                    } catch (fallbackEx: Exception) {
                        Log.e("AlarmScreenActivity", "Fallback sound also failed", fallbackEx)
                    }
                    true // Indicate we handled the error
                }
                
                // Prepare and start with error handling
                try {
                    prepare()
                    start()
                    Log.d("AlarmScreenActivity", "Ringtone playback started with URI: $uri, Volume: $alarmVolume")
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Failed to prepare/start ringtone", e)
                    playFallbackSound()
                }
            }
            
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to create MediaPlayer for ringtone", e)
            playFallbackSound()
        }
    }
    
    private fun startVoiceOverlay() {
        try {
            voiceRecordingPath?.let { path ->
                Log.d("AlarmScreenActivity", "🎙️ Attempting to start voice overlay from: $path with user-selected volume: $alarmVolume")
                
                // Check if file exists
                val file = java.io.File(path)
                if (!file.exists()) {
                    Log.e("AlarmScreenActivity", "❌ Voice recording file not found: $path")
                    return
                }
                
                voiceMediaPlayer = MediaPlayer().apply {
                    // Set audio attributes for voice overlay
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                    
                    setDataSource(path)
                    // Voice always plays at 100% of selected volume
                    setVolume(alarmVolume, alarmVolume)
                    
                    // Loop the voice recording continuously
                    isLooping = true
                    
                    setOnPreparedListener {
                        Log.d("AlarmScreenActivity", "✅ Voice overlay prepared, starting playback with volume: $alarmVolume")
                        start()
                    }
                    
                    // Add error listener first
                    setOnErrorListener { _, what, extra ->
                        Log.e("AlarmScreenActivity", "❌ Voice overlay error: what=$what, extra=$extra")
                        // Clean up on error
                        try {
                            release()
                        } catch (e: Exception) {
                            Log.e("AlarmScreenActivity", "Error releasing voice MediaPlayer", e)
                        }
                        voiceMediaPlayer = null
                        false // Let the system handle the error
                    }
                    
                    prepareAsync() // Use async to avoid blocking
                    
                    Log.d("AlarmScreenActivity", "🎙️ Voice overlay MediaPlayer configured with volume: $alarmVolume")
                }
            } ?: run {
                Log.e("AlarmScreenActivity", "❌ Voice recording path is null")
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ Failed to start voice overlay", e)
        }
    }

    private fun startTtsOverlay() {
        try {
            if (alarmNote.isBlank()) {
                Log.w("AlarmScreenActivity", "❌ TTS overlay requested but note is empty")
                return
            }
            
            Log.d("AlarmScreenActivity", "🗣️ Initializing TTS for note: '$alarmNote' with volume: $alarmVolume")
            
            tts = TextToSpeech(this) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        Log.d("AlarmScreenActivity", "✅ TTS initialized successfully")
                        
                        // Configure TTS settings with error handling
                        tts?.let { ttsEngine ->
                            try {
                                // Use phone's default TTS voice
                                val result = ttsEngine.setLanguage(Locale.getDefault())
                                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                    Log.w("AlarmScreenActivity", "TTS language not supported, using default")
                                }
                                
                                // Set audio attributes for TTS to use alarm stream
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    val params = Bundle()
                                    // TTS always plays at 100% of selected volume
                                    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, alarmVolume)
                                } else {
                                    // For older Android versions
                                    val params = HashMap<String, String>()
                                    params[TextToSpeech.Engine.KEY_PARAM_STREAM] = AudioManager.STREAM_ALARM.toString()
                                    params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = alarmVolume.toString()
                                    
                                    // Start TTS with looping
                                    startTtsLoopLegacy(params)
                                }
                            } catch (e: Exception) {
                                Log.e("AlarmScreenActivity", "Error configuring TTS", e)
                            }
                        }
                    } else {
                        Log.e("AlarmScreenActivity", "❌ Failed to initialize TTS: $status")
                    }
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Error in TTS initialization callback", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ Failed to start TTS overlay", e)
        }
    }
    
    private fun startTtsLoop(params: Bundle) {
        // Create a repeating TTS loop with 1 second delays between repetitions
        val ttsRunnable = object : Runnable {
            override fun run() {
                tts?.let { ttsEngine ->
                    if (!isFinishing && !isDestroyed) {
                        Log.d("AlarmScreenActivity", "🗣️ Speaking note: '$alarmNote'")
                        val result = ttsEngine.speak(alarmNote, TextToSpeech.QUEUE_FLUSH, params, "alarm_tts")
                        if (result == TextToSpeech.SUCCESS) {
                            // Schedule next TTS after 1 second for faster looping
                            handler.postDelayed(this, 1000) // 1 second delay between TTS repetitions
                        } else {
                            Log.e("AlarmScreenActivity", "❌ TTS speak failed: $result")
                            // Retry after 2 seconds if TTS fails
                            handler.postDelayed(this, 2000)
                        }
                    }
                }
            }
        }
        
        // Start the first TTS immediately
        handler.post(ttsRunnable)
    }
    
    private fun startTtsLoopLegacy(params: HashMap<String, String>) {
        // Create a repeating TTS loop for legacy Android with 1 second delays
        val ttsRunnable = object : Runnable {
            override fun run() {
                tts?.let { ttsEngine ->
                    if (!isFinishing && !isDestroyed) {
                        Log.d("AlarmScreenActivity", "🗣️ Speaking note (legacy): '$alarmNote'")
                        @Suppress("DEPRECATION")
                        val result = ttsEngine.speak(alarmNote, TextToSpeech.QUEUE_FLUSH, params)
                        if (result == TextToSpeech.SUCCESS) {
                            // Schedule next TTS after 1 second for faster looping
                            handler.postDelayed(this, 1000)
                        } else {
                            Log.e("AlarmScreenActivity", "❌ TTS speak failed (legacy): $result")
                            // Retry after 2 seconds if TTS fails
                            handler.postDelayed(this, 2000)
                        }
                    }
                }
            }
        }
        
        // Start the first TTS immediately
        handler.post(ttsRunnable)
    }
    
    private fun startAnimatedBell() {
        try {
            Log.d("AlarmScreenActivity", "Attempting to start animated bell...")
            
            // Check if image view is properly initialized
            if (imageAlarmBell == null) {
                Log.e("AlarmScreenActivity", "❌ CRITICAL ERROR: imageAlarmBell is null - cannot start animation")
                return
            }
            
            // Ensure the view is visible
            imageAlarmBell.visibility = View.VISIBLE
            
            // Log drawable information for debugging
            val drawable = imageAlarmBell.drawable
            Log.d("AlarmScreenActivity", "Bell drawable class: ${drawable?.javaClass?.simpleName}")
            Log.d("AlarmScreenActivity", "Bell drawable: $drawable")
            
            // For animated vector drawables, we need to start the animation explicitly
            if (drawable is android.graphics.drawable.AnimatedVectorDrawable) {
                Log.d("AlarmScreenActivity", "Starting AnimatedVectorDrawable animation")
                // Start animation
                try {
                    drawable.start()
                    Log.d("AlarmScreenActivity", "✅ Animated bell started successfully")
                } catch (animEx: Exception) {
                    Log.e("AlarmScreenActivity", "❌ ERROR: Failed to start AnimatedVectorDrawable animation", animEx)
                    // Fallback to the old animation if animated drawable fails
                    startBellAnimation()
                }
            } else {
                Log.w("AlarmScreenActivity", "Drawable is not AnimatedVectorDrawable, using fallback animation")
                // Fallback to the old animation if animated drawable fails
                startBellAnimation()
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ ERROR: Failed to start animated bell", e)
            Log.e("AlarmScreenActivity", "DIAGNOSTIC INFO:")
            Log.e("AlarmScreenActivity", "- Check if drawable resources are properly configured")
            Log.e("AlarmScreenActivity", "- Check for resource qualifier issues")
            // Fallback to the old animation if animated drawable fails
            startBellAnimation()
        }
    }
    
    private fun startBellAnimation() {
        try {
            Log.d("AlarmScreenActivity", "Attempting to start fallback bell animation...")
            
            // Check if image view is properly initialized
            if (imageAlarmBell == null) {
                Log.e("AlarmScreenActivity", "❌ CRITICAL ERROR: imageAlarmBell is null - cannot start fallback animation")
                return
            }
            
            // Ensure the view is visible
            imageAlarmBell.visibility = View.VISIBLE
            
            // Cancel any existing animation
            bellAnimator?.cancel()
            
            // Create a more complex animation with multiple properties
            val rotationAnimator = ObjectAnimator.ofFloat(imageAlarmBell, "rotation", -30f, 30f).apply {
                duration = 200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }
            
            // Add scaling animation for a ringing effect
            val scaleAnimatorX = ObjectAnimator.ofFloat(imageAlarmBell, "scaleX", 1.0f, 1.1f, 1.0f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
            }
            
            val scaleAnimatorY = ObjectAnimator.ofFloat(imageAlarmBell, "scaleY", 1.0f, 1.1f, 1.0f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
            }
            
            // Add alpha animation for pulsing effect
            val alphaAnimator = ObjectAnimator.ofFloat(imageAlarmBell, "alpha", 1.0f, 0.7f, 1.0f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
            }
            
            // Combine all animations
            val animatorSet = AnimatorSet().apply {
                playTogether(rotationAnimator, scaleAnimatorX, scaleAnimatorY, alphaAnimator)
                start()
            }
            
            bellAnimator = animatorSet
            Log.d("AlarmScreenActivity", "✅ Fallback bell animation started successfully")
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "❌ ERROR: Failed to start fallback bell animation", e)
            Log.e("AlarmScreenActivity", "DIAGNOSTIC INFO:")
            Log.e("AlarmScreenActivity", "- Check if ObjectAnimator is properly supported")
            Log.e("AlarmScreenActivity", "- Verify View properties are accessible")
            Log.e("AlarmScreenActivity", "- Check for animation framework issues")
            
            // Final fallback - just make sure the bell is visible
            try {
                imageAlarmBell?.visibility = View.VISIBLE
                Log.d("AlarmScreenActivity", "✅ Final fallback - bell made visible")
            } catch (finalEx: Exception) {
                Log.e("AlarmScreenActivity", "❌ Final fallback also failed", finalEx)
            }
        }
    }

    private fun startTimeUpdates() {
        timeUpdateRunnable = object : Runnable {
            override fun run() {
                updateTimeAndDate()
                handler.postDelayed(this, 1000)
            }
        }
        timeUpdateRunnable?.let { 
            handler.post(it)
        }
    }

    private fun updateTimeAndDate() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        
        textTime.text = timeFormat.format(calendar.time)
        textDate.text = dateFormat.format(calendar.time)
    }

    private fun snoozeAlarm() {
        Log.d("AlarmScreenActivity", "Snoozing alarm - ID: $alarmId")
        
        // Stop all audio immediately
        stopAlarmSound()
        stopBellAnimation()
        
        // Create comprehensive snooze intent with all original data
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE
            putExtra("ALARM_ID", alarmId)
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            
            // Preserve ALL original alarm data to prevent data loss
            putExtra("ALARM_TIME", intent.getStringExtra(AlarmScreenActivity.EXTRA_ALARM_TIME))
            putExtra("ALARM_TITLE", alarmTitle)
            putExtra("ALARM_NOTE", alarmNote)
            putExtra("RINGTONE_URI", intent.getStringExtra(AlarmScreenActivity.EXTRA_RINGTONE_URI))
            putExtra("RINGTONE_NAME", ringtoneName)
            putExtra("VOICE_RECORDING_PATH", voiceRecordingPath)
            putExtra("HAS_VOICE_OVERLAY", hasVoiceOverlay)
            putExtra("HAS_TTS_OVERLAY", hasTtsOverlay)
            // Use single alarm volume instead of separate volumes
            putExtra("ALARM_VOLUME", alarmVolume)
            putExtra("HAS_VIBRATION", hasVibration)
            
            // Add reliability flags
            putExtra("IS_SNOOZE_ACTION", true)
            putExtra("SNOOZE_TIMESTAMP", System.currentTimeMillis())
        }
        
        try {
            sendBroadcast(snoozeIntent)
            Log.d("AlarmScreenActivity", "Snooze broadcast sent successfully")
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Failed to send snooze broadcast: ${e.message}")
            // Show error to user
            showSnoozeError()
            return
        }
        
        // Finish activity with delay to ensure broadcast is processed
        handler.postDelayed({
            finish()
        }, 100)
    }
    
    private fun dismissAlarm() {
        Log.d("AlarmScreenActivity", "Dismissing alarm - ID: $alarmId")
        
        // Stop all audio immediately
        stopAlarmSound()
        stopBellAnimation()
        
        // Only send broadcast if this dismiss was not initiated by the STOP_ALARM broadcast
        // This prevents an infinite loop
        if (!isDismissInitiatedByBroadcast) {
            // Create dismiss intent
            val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_DISMISS
                putExtra("ALARM_ID", alarmId)
                putExtra("DISMISS_TIMESTAMP", System.currentTimeMillis())
            }
            
            try {
                sendBroadcast(dismissIntent)
                Log.d("AlarmScreenActivity", "Dismiss broadcast sent successfully")
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Failed to send dismiss broadcast: ${e.message}")
            }
        } else {
            Log.d("AlarmScreenActivity", "Skipping dismiss broadcast as it was initiated by STOP_ALARM broadcast")
        }
        
        // Finish activity immediately for dismiss
        finish()
    }
    
    private fun showSnoozeError() {
        // Implementation would go here
    }
    
    private fun stopAlarmSound() {
        try {
            // Stop main ringtone
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            
            // Stop voice overlay
            voiceMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            voiceMediaPlayer = null
            
            // Stop TTS
            tts?.stop()
            
            // Stop audio sequence manager
            audioSequenceManager?.stopSequence()
            
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error stopping alarm sound: ${e.message}")
        }
    }
    
    private fun stopBellAnimation() {
        try {
            // Stop animated vector drawable if it's running
            val drawable = imageAlarmBell.drawable
            if (drawable is AnimatedVectorDrawable) {
                // For AnimatedVectorDrawable, we don't need to explicitly stop it
                // The animation will stop when the view is no longer visible
            }
            
            // Stop the old bell animation if it was running
            bellAnimator?.cancel()
            bellAnimator = null
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error stopping bell animation: ${e.message}")
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { /* No-op */ }
                    .build()
                
                val result = audioManager?.requestAudioFocus(audioFocusRequest!!)
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error requesting audio focus: ${e.message}")
            false
        }
    }
    
    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager?.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error releasing audio focus: ${e.message}")
        }
    }
    
    private fun playFallbackSound() {
        try {
            // Try notification sound first as it's more likely to be available
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            Log.d("AlarmScreenActivity", "Using notification sound as fallback: $notificationUri")
            
            mediaPlayer = MediaPlayer().apply {
                try {
                    // Set audio attributes for alarm
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    
                    setDataSource(this@AlarmScreenActivity, notificationUri)
                    isLooping = true
                    
                    // Adjust volume based on whether voice/TTS is enabled
                    val adjustedVolume = if (hasVoiceOverlay || hasTtsOverlay) {
                        alarmVolume * 0.5f // 50% volume when voice/TTS is enabled
                    } else {
                        alarmVolume // 100% volume when only ringtone is enabled
                    }
                    
                    setVolume(adjustedVolume, adjustedVolume)
                    prepare()
                    start()
                    Log.d("AlarmScreenActivity", "Fallback notification sound started successfully with volume: $adjustedVolume")
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Notification fallback failed, trying ringtone", e)
                    
                    try {
                        // Try ringtone as second fallback
                        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        reset()
                        setDataSource(this@AlarmScreenActivity, ringtoneUri)
                        
                        // Adjust volume based on whether voice/TTS is enabled
                        val adjustedVolume = if (hasVoiceOverlay || hasTtsOverlay) {
                            alarmVolume * 0.5f // 50% volume when voice/TTS is enabled
                        } else {
                            alarmVolume // 100% volume when only ringtone is enabled
                        }
                        
                        setVolume(adjustedVolume, adjustedVolume)
                        prepare()
                        start()
                        Log.d("AlarmScreenActivity", "Fallback ringtone sound started with volume: $adjustedVolume")
                    } catch (e2: Exception) {
                        Log.e("AlarmScreenActivity", "All fallback sounds failed", e2)
                        // If all sounds fail, at least ensure vibration works if enabled
                        release()
                        mediaPlayer = null
                    }
                }
            }
        } catch (finalEx: Exception) {
            Log.e("AlarmScreenActivity", "Complete fallback failure: ${finalEx.message}")
            mediaPlayer = null
        }
    }
    
    private fun showBackButtonWarning() {
        // Implementation would go here
    }
    
    private fun setupStopAlarmReceiver() {
        try {
            stopAlarmReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == "STOP_ALARM") {
                        Log.d("AlarmScreenActivity", "Received stop alarm broadcast")
                        isDismissInitiatedByBroadcast = true
                        dismissAlarm()
                    }
                }
            }
            
            val filter = IntentFilter("STOP_ALARM")
            // Use RECEIVER_NOT_EXPORTED for security
            registerReceiver(stopAlarmReceiver, filter, null, null, Context.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error setting up stop alarm receiver: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            // Release audio sequence manager first
            audioSequenceManager?.release()
            audioSequenceManager = null
            
            stopAlarmSound()
            stopBellAnimation()
            timeUpdateRunnable?.let { handler.removeCallbacks(it) }
            
            // Release wake lock safely
            wakeLock?.let {
                try {
                    if (it.isHeld) {
                        it.release()
                        Log.d("AlarmScreenActivity", "Wake lock released successfully")
                    } else {
                        // Wake lock is not held, nothing to release
                        Log.d("AlarmScreenActivity", "Wake lock not held, no need to release")
                    }
                } catch (e: Exception) {
                    Log.e("AlarmScreenActivity", "Error releasing wake lock: ${e.message}")
                }
            }
            
            // Release audio focus
            releaseAudioFocus()
            
            // Unregister stop alarm receiver
            try {
                stopAlarmReceiver?.let { unregisterReceiver(it) }
            } catch (e: Exception) {
                Log.e("AlarmScreenActivity", "Error unregistering receiver: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("AlarmScreenActivity", "Error in onDestroy: ${e.message}")
        }
    }
}