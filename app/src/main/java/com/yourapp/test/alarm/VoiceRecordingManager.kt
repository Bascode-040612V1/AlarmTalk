package com.yourapp.test.alarm

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import java.io.IOException

class VoiceRecordingManager(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    private var isRecording = false
    private var isPlayingVoice = false
    private val handler = Handler(Looper.getMainLooper())
    private var recordingTimer: Runnable? = null
    private var onRecordingStoppedListener: (() -> Unit)? = null
    private var onPlaybackCompletedListener: (() -> Unit)? = null
    
    companion object {
        private const val TAG = "VoiceRecordingManager"
        private const val RECORDING_DIRECTORY = "Alarm_Talk_Voice_recordings"
        private const val MAX_RECORDING_DURATION = 20000 // 20 seconds in milliseconds
    }
    
    fun setOnRecordingStoppedListener(listener: () -> Unit) {
        this.onRecordingStoppedListener = listener
    }
    
    fun setOnPlaybackCompletedListener(listener: () -> Unit) {
        this.onPlaybackCompletedListener = listener
    }
    
    // Check if we have the required storage permissions
    fun hasStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // For Android 13+, we need READ_MEDIA_AUDIO for accessing audio files
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // For Android 10-12, we need READ_EXTERNAL_STORAGE for accessing files in shared storage
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                // For Android 9 and earlier, we need WRITE_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    fun startRecording(alarmId: Int): Boolean {
        return try {
            // Check if already recording
            if (isRecording) {
                Log.w(TAG, "Recording already in progress")
                return false
            }
            
            // Check storage permissions before attempting to record
            if (!hasStoragePermission()) {
                Log.e(TAG, "Storage permission not granted")
                return false
            }
            
            // Create recordings directory in Music folder
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val recordingsDir = File(musicDir, RECORDING_DIRECTORY)
            if (!recordingsDir.exists()) {
                val created = recordingsDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create recordings directory")
                    return false
                }
            }
            
            // Create unique recording file for this alarm
            recordingFile = File(recordingsDir, "alarm_voice_${alarmId}_${System.currentTimeMillis()}.m4a")
            
            // Clean up any previous recorder
            stopRecording()
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    // Use higher quality audio format for better recording quality
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    // Use AAC encoder for better quality
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    // Set higher audio quality parameters for improved recording quality
                    setAudioEncodingBitRate(192000) // 192 kbps (higher quality)
                    setAudioSamplingRate(48000) // 48 kHz (higher quality)
                    setOutputFile(recordingFile!!.absolutePath)
                    
                    prepare()
                    start()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure MediaRecorder", e)
                    release()
                    throw e
                }
            }
            
            isRecording = true
            
            // Set up timer to automatically stop recording after MAX_RECORDING_DURATION
            recordingTimer = Runnable {
                if (isRecording) {
                    Log.d(TAG, "Recording time limit reached (20 seconds), stopping recording")
                    stopRecording()
                    // Notify listener that recording was stopped automatically
                    onRecordingStoppedListener?.invoke()
                }
            }
            recordingTimer?.let { 
                handler.postDelayed(it, MAX_RECORDING_DURATION.toLong())
            }
            
            Log.d(TAG, "Recording started: ${recordingFile!!.absolutePath}")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Audio recording permission denied", e)
            stopRecording()
            false
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording - IO error", e)
            stopRecording()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting recording", e)
            stopRecording()
            false
        }
    }
    
    fun stopRecording(): String? {
        return try {
            // Cancel the recording timer
            recordingTimer?.let { handler.removeCallbacks(it) }
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            val filePath = recordingFile?.absolutePath
            Log.d(TAG, "Recording stopped: $filePath")
            filePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            null
        }
    }
    
    fun playRecording(filePath: String, volume: Float = 1.0f): Boolean {
        return try {
            stopPlayback() // Stop any current playback
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                // Set the volume to use the alarm volume
                setVolume(volume, volume)
                prepare()
                start()
                
                setOnCompletionListener {
                    isPlayingVoice = false
                    Log.d(TAG, "Playback completed")
                    // Notify listener that playback completed
                    onPlaybackCompletedListener?.invoke()
                }
            }
            
            isPlayingVoice = true
            Log.d(TAG, "Playing recording: $filePath with volume: $volume")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play recording", e)
            false
        }
    }
    
    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying()) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            isPlayingVoice = false
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop playback", e)
        }
    }
    
    fun deleteRecording(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Recording deleted: $filePath, success: $deleted")
                deleted
            } else {
                Log.w(TAG, "Recording file not found: $filePath")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete recording: $filePath", e)
            false
        }
    }
    
    fun getAllVoiceRecordings(): List<File> {
        return try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val recordingsDir = File(musicDir, RECORDING_DIRECTORY)
            
            if (recordingsDir.exists() && recordingsDir.isDirectory) {
                recordingsDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".m4a")
                }?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get voice recordings", e)
            emptyList()
        }
    }
    
    fun getRecordingDuration(filePath: String): Long {
        return try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            val duration = mediaPlayer.duration.toLong()
            mediaPlayer.release()
            duration
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recording duration for: $filePath", e)
            0L
        }
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun isPlaying(): Boolean = isPlayingVoice
    
    fun release() {
        try {
            stopRecording()
            stopPlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing VoiceRecordingManager", e)
        }
    }
}