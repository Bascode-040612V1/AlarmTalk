package com.yourapp.test.alarm

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * Manager class to handle permission requests and prevent duplicate dialogs
 */
class PermissionManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "permission_manager"
        private const val KEY_IS_REQUESTING_PERMISSIONS = "is_requesting_permissions"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if permission requests are currently in progress
     */
    fun isRequestingPermissions(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_REQUESTING_PERMISSIONS, false)
    }
    
    /**
     * Set the permission request status
     */
    fun setRequestingPermissions(isRequesting: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_REQUESTING_PERMISSIONS, isRequesting).apply()
    }
    
    /**
     * Check if this is the first time the app is being used
     */
    fun isFirstUse(): Boolean {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_first_use", true)
    }
    
    /**
     * Mark first use as complete
     */
    fun markFirstUseComplete() {
        val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_first_use", false).apply()
    }
    
    /**
     * Should show permission dialog based on current state
     */
    fun shouldShowPermissionDialog(): Boolean {
        // Don't show if we're already requesting permissions
        if (isRequestingPermissions()) {
            return false
        }
        
        // Don't show if this isn't first use and we've already asked
        if (!isFirstUse()) {
            return false
        }
        
        return true
    }
}