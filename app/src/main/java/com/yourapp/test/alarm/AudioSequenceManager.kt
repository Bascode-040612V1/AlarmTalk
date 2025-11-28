package com.yourapp.test.alarm

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * Manages sequential audio playback for alarms with fade effects
 * Sequence: Alarm plays for 6 seconds (2s fade-in + 4s full volume) -> Fade out for 3 seconds -> TTS/Voice -> Repeat
 */
class AudioSequenceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioSequenceManager"
        private const val FADE_IN_DURATION = 2000L // 2 seconds
        private const val FULL_VOLUME_DURATION = 4000L // 4 seconds
        private const val FADE_OUT_DURATION = 3000L // 3 seconds
        private const val TOTAL_ALARM_DURATION = FADE_IN_DURATION + FULL_VOLUME_DURATION + FADE_OUT_DURATION // 9 seconds
        private const val ALARM_PHASE_DURATION = 6000L // 6 seconds total (2s fade-in + 4s full volume)
    }
    
    // Audio players
    private var ringtonePlayer: MediaPlayer? = null
    private var voicePlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    
    // Animation and timing
    private var fadeAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sequenceRunnable: Runnable? = null
    
    // Audio settings
    private var ringtoneUri: Uri? = null
    private var ringtoneVolume: Float = 0.8f
    private var voiceRecordingPath: String? = null
    private var voiceVolume: Float = 1.0f
    private var ttsText: String? = null
    private var ttsVolume: Float = 1.0f
    private var hasTtsOverlay: Boolean = false
    private var hasVoiceOverlay: Boolean = false
    
    // State management
    private var isPlaying = false
    private var isInAlarmPhase = true
    private var sequenceStep = 0
    private var ttsInitialized = false
    
    /**
     * Initialize the audio sequence with alarm settings
     */
    fun initialize(
        ringtoneUri: Uri?,
        ringtoneVolume: Float,
        voiceRecordingPath: String?,
        voiceVolume: Float,
        ttsText: String?,
        ttsVolume: Float,
        hasVoiceOverlay: Boolean,
        hasTtsOverlay: Boolean
    ) {
        this.ringtoneUri = ringtoneUri
        // Adjust ringtone volume based on whether voice/TTS is enabled
        this.ringtoneVolume = if (hasVoiceOverlay || hasTtsOverlay) {
            ringtoneVolume * 0.5f // 50% volume when voice/TTS is enabled
        } else {
            ringtoneVolume // 100% volume when only ringtone is enabled
        }
        this.voiceRecordingPath = voiceRecordingPath
        this.voiceVolume = voiceVolume // Voice always plays at 100% of selected volume
        this.ttsText = ttsText
        this.ttsVolume = ttsVolume // TTS always plays at 100% of selected volume
        this.hasVoiceOverlay = hasVoiceOverlay
        this.hasTtsOverlay = hasTtsOverlay
        
        Log.d(TAG, "Audio sequence initialized - Voice: $hasVoiceOverlay, TTS: $hasTtsOverlay, Ringtone Volume: $this.ringtoneVolume, Voice Volume: $voiceVolume, TTS Volume: $ttsVolume")
        
        // Initialize TTS if needed
        if (hasTtsOverlay && !ttsText.isNullOrBlank()) {
            initializeTts()
        }
    }
    
    /**
     * Start the audio sequence
     */
    fun startSequence() {
        if (isPlaying) {
            Log.w(TAG, "Audio sequence already playing")
            return
        }
        
        isPlaying = true
        isInAlarmPhase = true
        sequenceStep = 0
        
        Log.d(TAG, "🎵 Starting audio sequence")
        
        // If TTS overlay is enabled, start TTS loop directly
        if (hasTtsOverlay && !ttsText.isNullOrBlank()) {
            Log.d(TAG, "🗣️ Starting TTS-only loop mode")
            startTtsLoop()
        } 
        // If voice overlay is enabled, play voice after alarm
        else if (hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty()) {
            Log.d(TAG, "🎙️ Starting voice overlay after alarm")
            startAlarmPhase()
        } else {
            // Otherwise, use the normal sequence starting with alarm
            startAlarmPhase()
        }
    }
    
    /**
     * Play voice overlay after alarm
     */
    private fun startVoiceAfterAlarm() {
        if (!isPlaying) return
        
        Log.d(TAG, "🎙️ Starting voice playback")
        
        try {
            voicePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                setDataSource(voiceRecordingPath)
                // Use the same volume for voice as other audio types
                setVolume(voiceVolume, voiceVolume)
                isLooping = true
                
                setOnCompletionListener {
                    Log.d(TAG, "✅ Voice playback completed")
                    // Restart voice playback if still playing
                    if (isPlaying) {
                        handler.postDelayed({
                            restartVoiceOverlay()
                        }, 100) // Small delay before restarting
                    }
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Voice playback error: what=$what, extra=$extra")
                    onVoicePhaseComplete()
                    true // Return true to indicate we handled the error
                }
                
                prepare()
                start()
            }
            
            Log.d(TAG, "🎙️ Voice overlay started with volume: $voiceVolume")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice", e)
            // Fallback to normal alarm phase if playback fails
            onVoicePhaseComplete()
        }
    }
    
    /**
     * Stop voice playback
     */
    private fun stopVoicePlayback() {
        try {
            // Stop voice player
            voicePlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping voice player", e)
                }
                
                try {
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing voice player", e)
                }
            }
            voicePlayer = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice", e)
        }
    }
    
    /**
     * Start the alarm phase with fade in/out effects
     */
    private fun startAlarmPhase() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔔 Starting alarm phase (fade in/out sequence)")
        
        try {
            // Create and configure the ringtone player
            ringtonePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                val uri = ringtoneUri ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                setDataSource(context, uri)
                
                // Set initial volume to 0 for fade-in effect
                setVolume(0f, 0f)
                isLooping = true
                
                setOnPreparedListener {
                    Log.d(TAG, "✅ Ringtone prepared, starting playback")
                    start()
                    // Start fade-in animation
                    startFadeIn()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Ringtone error: what=$what, extra=$extra")
                    // Try fallback sound
                    playFallbackSound()
                    true
                }
                
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm phase", e)
            // Try fallback sound
            playFallbackSound()
        }
    }
    
    /**
     * Start fade-in animation for the ringtone
     */
    private fun startFadeIn() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔊 Starting fade-in animation")
        
        // Cancel any existing fade animation
        fadeAnimator?.cancel()
        
        // Create fade-in animation
        fadeAnimator = ValueAnimator.ofFloat(0f, ringtoneVolume).apply {
            duration = FADE_IN_DURATION
            addUpdateListener { animator ->
                val volume = animator.animatedValue as Float
                ringtonePlayer?.setVolume(volume, volume)
            }
            start()
        }
        
        // Schedule full volume phase after fade-in
        handler.postDelayed({
            if (isPlaying) {
                startFullVolume()
            }
        }, FADE_IN_DURATION)
    }
    
    /**
     * Start full volume phase
     */
    private fun startFullVolume() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔊 Starting full volume phase")
        
        // Set to full volume
        ringtonePlayer?.setVolume(ringtoneVolume, ringtoneVolume)
        
        // Schedule fade-out phase
        handler.postDelayed({
            if (isPlaying) {
                startFadeOut()
            }
        }, FULL_VOLUME_DURATION)
    }
    
    /**
     * Start fade-out animation
     */
    private fun startFadeOut() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔊 Starting fade-out animation")
        
        // Cancel any existing fade animation
        fadeAnimator?.cancel()
        
        // Create fade-out animation
        fadeAnimator = ValueAnimator.ofFloat(ringtoneVolume, 0f).apply {
            duration = FADE_OUT_DURATION
            addUpdateListener { animator ->
                val volume = animator.animatedValue as Float
                ringtonePlayer?.setVolume(volume, volume)
            }
            start()
        }
        
        // Schedule next phase after fade-out
        handler.postDelayed({
            if (isPlaying) {
                onAlarmPhaseComplete()
            }
        }, FADE_OUT_DURATION)
    }
    
    /**
     * Called when the alarm phase is complete
     */
    private fun onAlarmPhaseComplete() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔔 Alarm phase complete")
        
        // Stop the current ringtone player
        ringtonePlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ringtone player", e)
            }
            
            try {
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing ringtone player", e)
            }
        }
        ringtonePlayer = null
        
        // Cancel fade animation
        try {
            fadeAnimator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling fade animator", e)
        }
        fadeAnimator = null
        
        // Move to next phase: voice overlay if enabled
        if (hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty()) {
            // Start voice overlay phase
            startVoiceAfterAlarm()
        } else {
            // No additional phases, repeat alarm
            sequenceStep++
            Log.d(TAG, "🔁 Repeating alarm sequence (step: $sequenceStep)")
            startAlarmPhase()
        }
    }
    
    /**
     * Start TTS phase
     */
    private fun startTtsPhase() {
        if (!isPlaying) return
        
        Log.d(TAG, "🗣️ Starting TTS phase")
        
        // Ensure TTS is initialized
        if (!ttsInitialized) {
            initializeTts()
            // Wait a bit for TTS to initialize
            handler.postDelayed({
                speakTtsText()
            }, 500)
        } else {
            speakTtsText()
        }
    }
    
    /**
     * Initialize Text-to-Speech engine
     */
    private fun initializeTts() {
        if (ttsInitialized) return
        
        Log.d(TAG, "🗣️ Initializing TTS engine")
        
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsInitialized = true
                    Log.d(TAG, "✅ TTS engine initialized successfully")
                    
                    // Configure TTS settings
                    tts?.let { ttsEngine ->
                        try {
                            // Use phone's default TTS voice
                            val result = ttsEngine.setLanguage(Locale.getDefault())
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                Log.w(TAG, "TTS language not supported")
                            } else {
                                Log.d(TAG, "TTS language set successfully")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error configuring TTS", e)
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Failed to initialize TTS engine: $status")
                    ttsInitialized = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS", e)
            ttsInitialized = false
        }
    }
    
    /**
     * Speak the TTS text
     */
    private fun speakTtsText() {
        ttsText?.let { text ->
            if (text.isNotBlank() && isPlaying) {
                Log.d(TAG, "🗣️ Speaking TTS text: $text")
                
                tts?.let { ttsEngine ->
                    if (ttsInitialized) {
                        try {
                            val params = Bundle().apply {
                                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
                            }
                            
                            val result = ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "alarm_tts")
                            if (result == TextToSpeech.SUCCESS) {
                                Log.d(TAG, "✅ TTS speaking started successfully")
                                
                                // Schedule completion callback
                                handler.postDelayed({
                                    if (isPlaying) {
                                        onTtsPhaseComplete()
                                    }
                                }, text.length * 100L) // Rough estimate of speech duration
                            } else {
                                Log.e(TAG, "❌ Failed to start TTS speaking: $result")
                                // Move to next phase even if TTS fails
                                onTtsPhaseComplete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error speaking TTS text", e)
                            // Move to next phase even if TTS fails
                            onTtsPhaseComplete()
                        }
                    } else {
                        Log.w(TAG, "TTS not initialized, skipping TTS phase")
                        onTtsPhaseComplete()
                    }
                }
            } else {
                Log.d(TAG, "TTS text is blank or playback stopped, skipping TTS phase")
                onTtsPhaseComplete()
            }
        }
    }
    
    /**
     * Called when the TTS phase is complete
     */
    private fun onTtsPhaseComplete() {
        if (!isPlaying) return
        
        Log.d(TAG, "🗣️ TTS phase complete")
        
        // Stop TTS
        tts?.stop()
        
        // Move to next phase: repeat the sequence
        sequenceStep++
        Log.d(TAG, "🔁 Repeating alarm sequence (step: $sequenceStep)")
        startAlarmPhase()
    }
    
    /**
     * Start voice overlay phase
     */
    private fun startVoicePhase() {
        if (!isPlaying) return
        
        Log.d(TAG, "🎙️ Starting voice overlay phase")
        
        try {
            voicePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                setDataSource(voiceRecordingPath)
                setVolume(voiceVolume, voiceVolume)
                isLooping = false
                
                setOnCompletionListener {
                    Log.d(TAG, "✅ Voice playback completed")
                    onVoicePhaseComplete()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Voice playback error: what=$what, extra=$extra")
                    onVoicePhaseComplete()
                    true // Return true to indicate we handled the error
                }
                
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice phase", e)
            onVoicePhaseComplete()
        }
    }
    
    /**
     * Called when the voice phase is complete
     */
    private fun onVoicePhaseComplete() {
        if (!isPlaying) return
        
        Log.d(TAG, "🎙️ Voice phase complete")
        
        // Stop voice player
        voicePlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice player", e)
            }
        }
        voicePlayer = null
        
        // Move to next phase: repeat the sequence
        sequenceStep++
        Log.d(TAG, "🔁 Repeating alarm sequence (step: $sequenceStep)")
        startAlarmPhase()
    }
    
    /**
     * Restart voice overlay (for continuous playback)
     */
    private fun restartVoiceOverlay() {
        if (!isPlaying) return
        
        try {
            voicePlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting voice overlay", e)
        }
    }
    
    /**
     * Start TTS loop for continuous playback
     */
    private fun startTtsLoop() {
        if (!isPlaying) return
        
        Log.d(TAG, "🗣️ Starting TTS loop")
        
        // Ensure TTS is initialized
        if (!ttsInitialized) {
            initializeTts()
        }
        
        // Create a repeating TTS loop
        val ttsRunnable = object : Runnable {
            override fun run() {
                tts?.let { ttsEngine ->
                    ttsText?.let { text ->
                        if (isPlaying && text.isNotBlank()) {
                            Log.d(TAG, "🗣️ Speaking TTS in loop: $text")
                            
                            try {
                                val params = Bundle().apply {
                                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
                                }
                                
                                val result = ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "alarm_tts_loop")
                                if (result != TextToSpeech.SUCCESS) {
                                    Log.e(TAG, "❌ TTS speak failed: $result")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error speaking TTS in loop", e)
                            }
                            
                            // Schedule next TTS after a delay
                            handler.postDelayed(this, Math.max(1000, text.length * 100L))
                        }
                    }
                }
            }
        }
        
        // Start the first TTS
        handler.post(ttsRunnable)
    }
    
    /**
     * Play fallback sound if primary sound fails
     */
    private fun playFallbackSound() {
        try {
            Log.d(TAG, "🎵 Playing fallback sound")
            
            // Try notification sound first
            val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            
            ringtonePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                
                setDataSource(context, notificationUri)
                setVolume(ringtoneVolume, ringtoneVolume)
                isLooping = true
                
                setOnPreparedListener {
                    start()
                    Log.d(TAG, "✅ Fallback sound started")
                }
                
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing fallback sound", e)
        }
    }
    
    /**
     * Stop the audio sequence
     */
    fun stopSequence() {
        Log.d(TAG, "⏹️ Stopping audio sequence")
        
        isPlaying = false
        
        // Stop all handlers
        handler.removeCallbacksAndMessages(null)
        
        // Stop ringtone and voice players
        stopRingtoneAndVoice()
        
        // Stop TTS
        tts?.let { ttsEngine ->
            try {
                ttsEngine.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping TTS", e)
            }
        }
        
        // Cancel fade animation
        fadeAnimator?.cancel()
        fadeAnimator = null
        
        // Reset state
        isInAlarmPhase = true
        sequenceStep = 0
        ttsInitialized = false
    }
    
    /**
     * Stop ringtone and voice players
     */
    private fun stopRingtoneAndVoice() {
        Log.d(TAG, "⏹️ Stopping ringtone and voice players")
        
        try {
            // Stop ringtone player
            ringtonePlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping ringtone player", e)
                }
                
                try {
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing ringtone player", e)
                }
            }
            ringtonePlayer = null
            
            // Stop voice player
            voicePlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping voice player", e)
                }
                
                try {
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing voice player", e)
                }
            }
            voicePlayer = null
            
            // Cancel fade animation
            try {
                fadeAnimator?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling fade animator", e)
            }
            fadeAnimator = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone and voice", e)
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        Log.d(TAG, "🗑️ Releasing AudioSequenceManager resources")
        
        stopSequence()
        
        // Release TTS
        tts?.let { ttsEngine ->
            try {
                ttsEngine.stop()
                ttsEngine.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down TTS", e)
            }
        }
        tts = null
        
        // Clear references
        ringtoneUri = null
        voiceRecordingPath = null
        ttsText = null
        
        // Remove all callbacks from handler to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }
}