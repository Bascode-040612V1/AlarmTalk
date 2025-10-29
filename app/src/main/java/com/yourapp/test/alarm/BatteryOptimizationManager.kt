package com.yourapp.test.alarm

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog

/**
 * Manager class for handling battery optimization and power management
 */
class BatteryOptimizationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryOptimizationManager"
    }
    
    /**
     * Check if battery optimization is enabled for this app
     */
    @SuppressLint("BatteryLife")
    fun isBatteryOptimizationEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                !powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                false // Battery optimization not available on older versions
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization status", e)
            true // Assume optimization is enabled if we can't check
        }
    }
    
    /**
     * Request user to disable battery optimization for this app
     */
    fun requestDisableBatteryOptimization(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
                    showBatteryOptimizationDialog(activity)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting battery optimization disable", e)
        }
    }
    
    /**
     * Show dialog to request battery optimization disable
     */
    private fun showBatteryOptimizationDialog(activity: Activity) {
        try {
            AlertDialog.Builder(activity)
                .setTitle("Battery Optimization")
                .setMessage("To ensure reliable alarm functionality, please allow this app to run in the background by disabling battery optimization.")
                .setPositiveButton("Settings") { _, _ ->
                    openBatteryOptimizationSettings(activity)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing battery optimization dialog", e)
        }
    }
    
    /**
     * Open battery optimization settings for this app
     */
    private fun openBatteryOptimizationSettings(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening battery optimization settings", e)
            // Fallback to general battery settings
            openGeneralBatterySettings(activity)
        }
    }
    
    /**
     * Fallback method to open general battery settings
     */
    private fun openGeneralBatterySettings(activity: Activity) {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening general battery settings", e)
        }
    }
    
    /**
     * Optimize alarm scheduling for better battery efficiency
     */
    fun optimizeAlarmScheduling(): String {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptimizationStatus = if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                "Battery optimization disabled - alarms will work reliably"
            } else {
                "Battery optimization enabled - alarms may be delayed"
            }
            
            Log.d(TAG, "Alarm scheduling optimization: $batteryOptimizationStatus")
            batteryOptimizationStatus
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing alarm scheduling", e)
            "Battery optimization status unknown"
        }
    }
    
    /**
     * Get power management information
     */
    fun getPowerManagementInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                
                info["Battery Optimization"] = if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    "Disabled (Recommended)"
                } else {
                    "Enabled"
                }
                
                info["Device Idle Mode"] = if (powerManager.isDeviceIdleMode) "Yes" else "No"
                info["Interactive"] = if (powerManager.isInteractive) "Yes" else "No"
            } else {
                info["Battery Optimization"] = "Not supported on this Android version"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting power management info", e)
            info["Error"] = e.message ?: "Unknown error"
        }
        
        return info
    }
}