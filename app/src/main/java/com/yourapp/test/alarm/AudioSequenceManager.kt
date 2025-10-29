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
        private const val FULL_VOLUME_DURATION = 4000L // 4 seconds (changed from 3s to 4s)
        private const val FADE_OUT_DURATION = 3000L // 3 seconds (changed from 2s to 3s)
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
    private var ttsVoice: String = "female" // Add this line for TTS voice selection
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
        ttsVoice: String, // Add this parameter for TTS voice selection
        hasVoiceOverlay: Boolean,
        hasTtsOverlay: Boolean
    ) {
        this.ringtoneUri = ringtoneUri
        this.ringtoneVolume = ringtoneVolume
        this.voiceRecordingPath = voiceRecordingPath
        this.voiceVolume = voiceVolume
        this.ttsText = ttsText
        this.ttsVolume = ttsVolume
        this.ttsVoice = ttsVoice // Add this line for TTS voice selection
        this.hasVoiceOverlay = hasVoiceOverlay
        this.hasTtsOverlay = hasTtsOverlay
        
        Log.d(TAG, "Audio sequence initialized - Voice: $hasVoiceOverlay, TTS: $hasTtsOverlay, TTS Voice: $ttsVoice")
        
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
        // If voice overlay is enabled, play ringtone and voice simultaneously
        else if (hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty()) {
            Log.d(TAG, "🎙️ Starting simultaneous ringtone and voice overlay")
            startRingtoneAndVoice()
        } else {
            // Otherwise, use the normal sequence starting with alarm
            startAlarmPhase()
        }
    }
    
    /**
     * Play ringtone and voice overlay simultaneously
     */
    private fun startRingtoneAndVoice() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔔 Starting simultaneous ringtone and voice playback")
        
        try {
            // Start ringtone with reduced volume (50% when voice overlay is active)
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
                
                isLooping = true
                // Reduce ringtone volume to 50% when voice overlay is active
                val adjustedRingtoneVolume = ringtoneVolume * 0.5f
                setVolume(adjustedRingtoneVolume, adjustedRingtoneVolume)
                
                prepare()
                start()
            }
            
            Log.d(TAG, "🔔 Ringtone started with 50% volume: ${ringtoneVolume * 0.5f}")
            
            // Start voice overlay with full volume
            voicePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                setDataSource(voiceRecordingPath)
                // Use full voice volume
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
                    true
                }
                
                prepare()
                start()
            }
            
            Log.d(TAG, "🎙️ Voice overlay started with full volume: $voiceVolume")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting simultaneous ringtone and voice", e)
            // Fallback to normal alarm phase if simultaneous playback fails
            startAlarmPhase()
        }
    }
    
    /**
     * Stop simultaneous ringtone and voice playback
     */
    private fun stopRingtoneAndVoice() {
        try {
            // Stop ringtone
            ringtonePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            ringtonePlayer = null
            
            // Stop voice
            voicePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            voicePlayer = null
            
            Log.d(TAG, "🔇 Stopped simultaneous ringtone and voice playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping simultaneous ringtone and voice", e)
        }
    }
    
    /**
     * Stop the audio sequence
     */
    fun stopSequence() {
        if (!isPlaying) return
        
        Log.d(TAG, "🛑 Stopping audio sequence")
        isPlaying = false
        
        // Stop all playback
        stopAlarmPhase()
        stopVoicePhase()
        stopTtsPhase()
        stopTtsLoop()
        stopRingtoneAndVoice() // Add this line to stop simultaneous playback
        
        // Cancel any pending sequence steps
        sequenceRunnable?.let { handler.removeCallbacks(it) }
        sequenceRunnable = null
    }
    
    /**
     * Start the alarm ringtone phase with fade effects
     */
    private fun startAlarmPhase() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔔 Starting alarm phase (6s total, then fade out for 3s)")
        isInAlarmPhase = true
        
        try {
            // Stop any existing ringtone
            stopRingtone()
            
            // Create and configure ringtone player
            ringtonePlayer = MediaPlayer().apply {
                // Set audio attributes for alarm
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                // Set data source
                val uri = ringtoneUri ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                setDataSource(context, uri)
                
                // Configure for looping during the alarm phase
                isLooping = true
                
                // CRITICAL FIX: Apply user-selected volume directly to MediaPlayer
                // This ensures the volume setting is respected regardless of system volume
                setVolume(ringtoneVolume, ringtoneVolume)
                
                // Prepare and start
                prepare()
                start()
            }
            
            // Start fade-in effect
            startFadeIn()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm phase", e)
            // Skip to next phase if alarm fails
            scheduleNextPhase()
        }
    }
    
    /**
     * Create fade-in effect for alarm
     */
    private fun startFadeIn() {
        fadeAnimator?.cancel()
        
        // Calculate adjusted ringtone volume (50% when voice overlay is active)
        val adjustedRingtoneVolume = if (hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty()) {
            ringtoneVolume * 0.5f // Reduce to 50% when voice overlay is active
        } else {
            ringtoneVolume // Use full volume when no voice overlay
        }
        
        fadeAnimator = ValueAnimator.ofFloat(0f, adjustedRingtoneVolume).apply {
            duration = FADE_IN_DURATION
            
            addUpdateListener { animator ->
                val volume = animator.animatedValue as Float
                ringtonePlayer?.setVolume(volume, volume)
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // After fade-in, maintain full volume for 3 seconds
                    scheduleFullVolumePhase()
                }
            })
            
            start()
        }
        
        Log.d(TAG, "📈 Started fade-in (2s) with adjusted volume: $adjustedRingtoneVolume")
    }

    /**
     * Schedule the full volume phase
     */
    private fun scheduleFullVolumePhase() {
        if (!isPlaying) return
        
        // Calculate adjusted ringtone volume (50% when voice overlay is active)
        val adjustedRingtoneVolume = if (hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty()) {
            ringtoneVolume * 0.5f // Reduce to 50% when voice overlay is active
        } else {
            ringtoneVolume // Use full volume when no voice overlay
        }
        
        Log.d(TAG, "🔊 Full volume phase (4s) with adjusted volume: $adjustedRingtoneVolume")
        
        // Ensure full volume
        ringtonePlayer?.setVolume(adjustedRingtoneVolume, adjustedRingtoneVolume)
        
        // Schedule fade-out after 4 seconds of full volume (6 seconds total)
        handler.postDelayed({
            if (isPlaying && isInAlarmPhase) {
                startFadeOut()
                // After fade-out completes (3 seconds), stop alarm and proceed to next phase
                handler.postDelayed({
                    if (isPlaying && isInAlarmPhase) {
                        stopRingtone()
                        scheduleNextPhase()
                    }
                }, FADE_OUT_DURATION)
            }
        }, FULL_VOLUME_DURATION)
    }
    
    /**
     * Create fade-out effect for alarm
     */
    private fun startFadeOut() {
        fadeAnimator?.cancel()
        
        // Calculate adjusted ringtone volume (50% when voice overlay is active)
        val adjustedRingtoneVolume = if (hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty()) {
            ringtoneVolume * 0.5f // Reduce to 50% when voice overlay is active
        } else {
            ringtoneVolume // Use full volume when no voice overlay
        }
        
        fadeAnimator = ValueAnimator.ofFloat(adjustedRingtoneVolume, 0f).apply {
            duration = FADE_OUT_DURATION
            
            addUpdateListener { animator ->
                val volume = animator.animatedValue as Float
                ringtonePlayer?.setVolume(volume, volume)
            }
            
            doOnEnd {
                // After fade-out completes
                Log.d(TAG, "🎵 Fade-out completed")
            }
            
            start()
        }
        
        Log.d(TAG, "📉 Started fade-out (3s) with adjusted volume: $adjustedRingtoneVolume")
    }
    
    /**
     * Reduce alarm volume to 50% at 6 seconds
     */
    // This method is no longer used in the current implementation
    private fun reduceVolumeToHalf() {
        if (!isPlaying || !isInAlarmPhase) return
        
        Log.d(TAG, "🔉 Reducing volume to 50% at 6 seconds")
        
        // Set volume to 50% of the original ringtone volume
        val halfVolume = ringtoneVolume * 0.5f
        ringtonePlayer?.setVolume(halfVolume, halfVolume)
    }
    
    /**
     * Schedule the next phase (TTS or Voice)
     */
    private fun scheduleNextPhase() {
        if (!isPlaying) return
        
        isInAlarmPhase = false
        
        // Determine which phase to play next
        when {
            hasTtsOverlay && !ttsText.isNullOrBlank() -> {
                Log.d(TAG, "🗣️ Moving to TTS phase after fade-out")
                // Delay TTS by 1 second after fade-out completes
                handler.postDelayed({
                    if (isPlaying) {
                        startTtsPhase()
                    }
                }, 1000)
            }
            hasVoiceOverlay && !voiceRecordingPath.isNullOrEmpty() -> {
                Log.d(TAG, "🎙️ Moving to voice phase after fade-out")
                // Delay voice by 1 second after fade-out completes
                handler.postDelayed({
                    if (isPlaying) {
                        startVoicePhase()
                    }
                }, 1000)
            }
            else -> {
                Log.d(TAG, "🔄 No TTS/Voice configured, restarting alarm phase")
                // If no TTS or voice, just loop the alarm
                handler.postDelayed({
                    if (isPlaying) {
                        startAlarmPhase()
                    }
                }, 500) // Small delay before restarting
            }
        }
    }
    
    /**
     * Start the TTS phase (in sequence mode)
     */
    private fun startTtsPhase() {
        if (!isPlaying || ttsText.isNullOrBlank()) {
            scheduleAlarmRestart()
            return
        }
        
        Log.d(TAG, "🗣️ Starting TTS phase with volume: $ttsVolume")
        
        if (!ttsInitialized) {
            Log.w(TAG, "TTS not initialized, skipping to alarm restart")
            scheduleAlarmRestart()
            return
        }
        
        try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
            }
            
            val result = tts?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, params, "alarm_sequence_tts")
            
            if (result == TextToSpeech.SUCCESS) {
                // TTS will call onDone when finished, which will restart the alarm
                Log.d(TAG, "✅ TTS started successfully with volume: $ttsVolume")
            } else {
                Log.e(TAG, "❌ TTS failed to start, restarting alarm")
                scheduleAlarmRestart()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in TTS phase", e)
            scheduleAlarmRestart()
        }
    }
    
    /**
     * Start the voice recording phase
     */
    private fun startVoicePhase() {
        if (!isPlaying || voiceRecordingPath.isNullOrEmpty()) {
            scheduleAlarmRestart()
            return
        }
        
        Log.d(TAG, "🎙️ Starting voice phase with full volume: $voiceVolume")
        
        try {
            // Stop any existing voice playback
            stopVoice()
            
            voicePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                setDataSource(voiceRecordingPath)
                // Use full voice volume since ringtone is reduced to 50%
                setVolume(voiceVolume, voiceVolume)
                
                setOnCompletionListener {
                    Log.d(TAG, "✅ Voice playback completed")
                    scheduleAlarmRestart()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "❌ Voice playback error: what=$what, extra=$extra")
                    scheduleAlarmRestart()
                    true
                }
                
                prepare()
                start()
            }
            
            Log.d(TAG, "✅ Voice playback started with full volume: $voiceVolume")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in voice phase", e)
            scheduleAlarmRestart()
        }
    }
    
    /**
     * Schedule restart of alarm phase
     */
    private fun scheduleAlarmRestart() {
        if (!isPlaying) return
        
        Log.d(TAG, "🔄 Scheduling alarm restart in 1 second")
        
        sequenceRunnable = Runnable {
            if (isPlaying) {
                startAlarmPhase()
            }
        }
        
        handler.postDelayed(sequenceRunnable!!, 1000) // 1 second delay before restarting alarm
    }
    
    /**
     * Start continuous TTS loop (when TTS overlay is enabled)
     */
    private fun startTtsLoop() {
        if (!isPlaying || ttsText.isNullOrBlank() || !ttsInitialized) {
            Log.e(TAG, "Cannot start TTS loop - missing required components")
            return
        }
        
        Log.d(TAG, "🔄 Starting continuous TTS loop with volume: $ttsVolume")
        
        try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, ttsVolume)
            }
            
            // Speak the text continuously
            val result = tts?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, params, "alarm_tts_loop")
            
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "❌ TTS loop failed to start")
            } else {
                Log.d(TAG, "✅ TTS loop started successfully with volume: $ttsVolume")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting TTS loop", e)
        }
    }
    
    /**
     * Stop continuous TTS loop
     */
    private fun stopTtsLoop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS loop", e)
        }
    }
    
    /**
     * Initialize TTS engine
     */
    private fun initializeTts() {
        if (tts != null) return
        
        Log.d(TAG, "🗣️ Initializing TTS engine with voice: $ttsVoice")
        
        tts = TextToSpeech(context) { status ->
            ttsInitialized = (status == TextToSpeech.SUCCESS)
            
            if (ttsInitialized) {
                Log.d(TAG, "✅ TTS initialized successfully")
                tts?.let { ttsEngine ->
                    try {
                        // Apply voice characteristics based on selection
                        when (ttsVoice) {
                            "male" -> {
                                ttsEngine.setPitch(0.8f) // Lower pitch for male voice
                                ttsEngine.setSpeechRate(0.9f) // Slightly slower for male voice
                            }
                            "female" -> {
                                ttsEngine.setPitch(1.2f) // Higher pitch for female voice
                                ttsEngine.setSpeechRate(1.0f) // Normal rate for female voice
                            }
                            else -> {
                                ttsEngine.setPitch(1.0f) // Default pitch
                                ttsEngine.setSpeechRate(1.0f) // Default rate
                            }
                        }
                        
                        val result = ttsEngine.setLanguage(Locale.getDefault())
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "TTS language not fully supported")
                        }
                        
                        // Set completion listener to handle both sequence mode and loop mode
                        ttsEngine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d(TAG, "🗣️ TTS started: $utteranceId")
                            }
                            
                            override fun onDone(utteranceId: String?) {
                                Log.d(TAG, "✅ TTS completed: $utteranceId")
                                when (utteranceId) {
                                    "alarm_sequence_tts" -> {
                                        // In sequence mode, restart the alarm after TTS
                                        scheduleAlarmRestart()
                                    }
                                    "alarm_tts_loop" -> {
                                        // In loop mode, restart the TTS if still playing
                                        if (isPlaying && hasTtsOverlay) {
                                            handler.postDelayed({
                                                startTtsLoop()
                                            }, 500) // Small delay before restarting
                                        }
                                    }
                                }
                            }
                            
                            override fun onError(utteranceId: String?) {
                                Log.e(TAG, "❌ TTS error: $utteranceId")
                                when (utteranceId) {
                                    "alarm_sequence_tts" -> {
                                        scheduleAlarmRestart()
                                    }
                                    "alarm_tts_loop" -> {
                                        // In loop mode, try to restart the TTS if still playing
                                        if (isPlaying && hasTtsOverlay) {
                                            handler.postDelayed({
                                                startTtsLoop()
                                            }, 1000) // Longer delay before retrying
                                        }
                                    }
                                }
                            }
                        })
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error configuring TTS", e)
                        ttsInitialized = false
                    }
                }
            } else {
                Log.e(TAG, "❌ TTS initialization failed")
            }
        }
    }
    
    /**
     * Stop alarm ringtone
     */
    private fun stopAlarmPhase() {
        stopRingtone()
        fadeAnimator?.cancel()
        fadeAnimator = null
    }
    
    private fun stopRingtone() {
        try {
            ringtonePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone", e)
        }
        ringtonePlayer = null
    }
    
    /**
     * Stop voice playback
     */
    private fun stopVoicePhase() {
        stopVoice()
    }
    
    private fun stopVoice() {
        try {
            voicePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping voice", e)
        }
        voicePlayer = null
    }
    
    /**
     * Stop TTS
     */
    private fun stopTtsPhase() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        Log.d(TAG, "🧹 Releasing audio sequence resources")
        
        stopSequence()
        
        // Release TTS
        try {
            tts?.shutdown()
            tts = null
            ttsInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS", e)
        }
        
        // Clean up any remaining callbacks
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Check if sequence is currently playing
     */
    fun isSequencePlaying(): Boolean = isPlaying
    
    /**
     * Check if currently in alarm phase
     */
    fun isInAlarmPhase(): Boolean = isInAlarmPhase
    
    /**
     * Restart voice overlay playback
     */
    private fun restartVoiceOverlay() {
        if (!isPlaying || voiceRecordingPath.isNullOrEmpty()) return
        
        Log.d(TAG, "🔄 Restarting voice overlay")
        
        try {
            // Stop any existing voice playback
            voicePlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            voicePlayer = null
            
            // Start voice overlay with full volume
            voicePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                setDataSource(voiceRecordingPath)
                // Use full voice volume
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
                    // Try to restart if still playing
                    if (isPlaying) {
                        handler.postDelayed({
                            restartVoiceOverlay()
                        }, 1000) // Longer delay before retrying
                    }
                    true
                }
                
                prepare()
                start()
            }
            
            Log.d(TAG, "✅ Voice overlay restarted with full volume: $voiceVolume")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting voice overlay", e)
            // Try to restart if still playing
            if (isPlaying) {
                handler.postDelayed({
                    restartVoiceOverlay()
                }, 1000) // Longer delay before retrying
            }
        }
    }
}

// Extension function for ValueAnimator
private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
            action()
        }
    })
}