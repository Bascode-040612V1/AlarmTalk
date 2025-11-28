package com.yourapp.test.alarm

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Background service for optimizing battery consumption
 * Handles periodic cleanup tasks and resource management
 */
class BackgroundOptimizationService : Service() {
    
    companion object {
        private const val TAG = "BackgroundOptimizationService"
        private const val CLEANUP_INTERVAL_MINUTES = 30L
    }
    
    private val binder = LocalBinder()
    private lateinit var executor: ScheduledExecutorService
    
    inner class LocalBinder : Binder() {
        fun getService(): BackgroundOptimizationService = this@BackgroundOptimizationService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Background optimization service created")
        
        // Initialize the scheduled executor for periodic tasks
        executor = Executors.newSingleThreadScheduledExecutor()
        
        // Schedule periodic cleanup tasks
        scheduleCleanupTasks()
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Background optimization service bound")
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Background optimization service started")
        return START_STICKY // Restart service if killed
    }
    
    private fun scheduleCleanupTasks() {
        // Schedule periodic cleanup every 30 minutes
        executor.scheduleAtFixedRate(
            {
                try {
                    performPeriodicCleanup()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during periodic cleanup", e)
                }
            },
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
        
        Log.d(TAG, "Scheduled periodic cleanup tasks")
    }
    
    /**
     * Perform periodic cleanup tasks to optimize battery consumption
     */
    private fun performPeriodicCleanup() {
        Log.d(TAG, "Performing periodic cleanup")
        
        // Cleanup old voice recordings
        cleanupOldVoiceRecordings()
        
        // Optimize memory usage
        optimizeMemoryUsage()
        
        // Log battery optimization status
        logBatteryOptimizationStatus()
    }
    
    /**
     * Cleanup old voice recordings to save storage space
     */
    private fun cleanupOldVoiceRecordings() {
        try {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val voiceDir = File(musicDir, "Alarm_Talk_Voice_recordings")
            if (voiceDir.exists()) {
                val files = voiceDir.listFiles()
                if (files != null) {
                    val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) // 7 days ago
                    
                    files.forEach { file ->
                        if (file.lastModified() < cutoffTime) {
                            if (file.delete()) {
                                Log.d(TAG, "Deleted old voice recording: ${file.name}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up voice recordings", e)
        }
    }
    
    /**
     * Optimize memory usage by triggering garbage collection
     */
    private fun optimizeMemoryUsage() {
        try {
            // Suggest garbage collection to free up memory
            System.gc()
            Log.d(TAG, "Memory optimization completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during memory optimization", e)
        }
    }
    
    /**
     * Log battery optimization status for monitoring
     */
    private fun logBatteryOptimizationStatus() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
            
            Log.d(TAG, "Memory usage: ${usedMemory / (1024 * 1024)} MB / ${maxMemory / (1024 * 1024)} MB (${String.format("%.1f", memoryUsagePercent)}%)")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging battery optimization status", e)
        }
    }
    
    /**
     * Stop all background tasks and cleanup resources
     */
    fun stopBackgroundTasks() {
        try {
            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
            Log.d(TAG, "Background tasks stopped")
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
            Log.e(TAG, "Error stopping background tasks", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundTasks()
        Log.d(TAG, "Background optimization service destroyed")
    }
}