package com.yourapp.test.alarm

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yourapp.test.alarm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var permissionManager: PermissionManager
    private var isRequestingPermissions = false
    private lateinit var fabAddAlarm: FloatingActionButton

    // Add result launcher for AlarmSetupActivity
    private val alarmSetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            when (result.resultCode) {
                AlarmSetupActivity.RESULT_ALARM_SAVED -> {
                    result.data?.let { data ->
                        try {
                            val alarmItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                data.getParcelableExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, AlarmItem::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra<AlarmItem>(AlarmSetupActivity.EXTRA_ALARM_ITEM)
                            }
                            alarmItem?.let { alarm ->
                                // Forward the result to the AlarmsFragment
                                val alarmsFragment = supportFragmentManager.findFragmentByTag("f0") as? AlarmsFragment
                                alarmsFragment?.let { fragment ->
                                    // Create a mock result intent to pass to the fragment's handler
                                    val mockResult = Intent().apply {
                                        putExtra(AlarmSetupActivity.EXTRA_ALARM_ITEM, alarm)
                                    }
                                    // Manually call the fragment's result handler
                                    fragment.onActivityResult(AlarmSetupActivity.RESULT_ALARM_SAVED, mockResult)
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error saving alarm: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                AlarmSetupActivity.RESULT_ALARM_DELETED -> {
                    // Handle delete result if needed
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in alarmSetupLauncher callback", e)
            Toast.makeText(this, "Error processing alarm result: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Permission launchers for modern permission handling
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionRationale("Notification permission is required for alarms to work properly.")
        }
        // Continue checking other permissions
        checkRemainingPermissions()
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionRationale("Microphone permission is required for voice recording features.")
        }
        // Continue checking other permissions
        checkRemainingPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize shared preferences
        sharedPreferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
        
        // Initialize permission manager
        permissionManager = PermissionManager(this)
        
        // Initialize battery optimization manager
        batteryOptimizationManager = BatteryOptimizationManager(this)
        
        // Request all necessary permissions on first use
        requestAllPermissions()
    }
    
    private fun requestAllPermissions() {
        // Check if this is the first time the app is being used
        val isFirstUse = permissionManager.isFirstUse()
        
        // If it's the first use, request all permissions
        if (isFirstUse && permissionManager.shouldShowPermissionDialog()) {
            isRequestingPermissions = true
            permissionManager.setRequestingPermissions(true)
            checkRemainingPermissions()
        } else {
            // Not first use, setup UI directly
            setupUI()
        }
    }
    
    private fun checkRemainingPermissions() {
        try {
            // Request notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            
            // Request microphone permission (All Android versions)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            
            // Check overlay permission (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    showOverlayPermissionDialog()
                    return
                }
            }
            
            // Check battery optimization (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (batteryOptimizationManager.isBatteryOptimizationEnabled()) {
                    showBatteryOptimizationDialog()
                    return
                }
            }
            
            // Mark that first use is complete and we're no longer requesting permissions
            permissionManager.markFirstUseComplete()
            permissionManager.setRequestingPermissions(false)
            
            // All permissions granted
            isRequestingPermissions = false
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking permissions", e)
            Toast.makeText(this, "Error checking permissions: ${e.message}", Toast.LENGTH_LONG).show()
            // Clear the requesting permissions flag even on error
            permissionManager.setRequestingPermissions(false)
        } finally {
            // Always setup UI regardless of permission status
            setupUI()
        }
    }
    
    // Function to check if all required permissions are granted before opening alarm setup
    private fun checkPermissionsAndOpenAlarmSetup() {
        try {
            var allPermissionsGranted = true
            
            // Check notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                }
            }
            
            // Check microphone permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
            }
            
            // Check overlay permission (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    allPermissionsGranted = false
                }
            }
            
            // Check battery optimization (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (batteryOptimizationManager.isBatteryOptimizationEnabled()) {
                    allPermissionsGranted = false
                }
            }
            
            // If all permissions are granted, open the alarm setup screen
            if (allPermissionsGranted) {
                val intent = Intent(this, AlarmSetupActivity::class.java)
                // Pass data indicating this is for creating a new alarm
                intent.putExtra(AlarmSetupActivity.EXTRA_IS_EDIT_MODE, false)
                alarmSetupLauncher.launch(intent)
            } else {
                // Show a consolidated dialog explaining which permissions are needed
                showConsolidatedPermissionDialog()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking permissions before opening alarm setup", e)
            Toast.makeText(this, "Error checking permissions: ${e.message}", Toast.LENGTH_LONG).show()
            // Continue with setup even if permission check fails
            try {
                val intent = Intent(this, AlarmSetupActivity::class.java)
                intent.putExtra(AlarmSetupActivity.EXTRA_IS_EDIT_MODE, false)
                alarmSetupLauncher.launch(intent)
            } catch (startActivityException: Exception) {
                Log.e("MainActivity", "Failed to start AlarmSetupActivity", startActivityException)
                Toast.makeText(this, "Failed to open alarm setup: ${startActivityException.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "Unexpected error when opening alarm setup", e)
            Toast.makeText(this, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showConsolidatedPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app requires several permissions to work properly:\n\n" +
                    "• Notification permission - For alarm notifications\n" +
                    "• Microphone permission - For voice recording features\n" +
                    "• Display over other apps - To show alarms over the lock screen\n" +
                    "• Battery optimization - To ensure reliable alarm functionality\n\n" +
                    "Please enable these permissions in the next screen.")
            .setPositiveButton("Continue") { _, _ ->
                // Mark that we're requesting permissions to prevent UI setup
                isRequestingPermissions = true
                permissionManager.setRequestingPermissions(true)
                // Restart the permission checking process
                checkRemainingPermissions()
            }
            .setNegativeButton("Later") { _, _ ->
                // Still open alarm setup even if user postpones permissions
                val intent = Intent(this, AlarmSetupActivity::class.java)
                intent.putExtra(AlarmSetupActivity.EXTRA_IS_EDIT_MODE, false)
                alarmSetupLauncher.launch(intent)
            }
            .show()
    }
    
    private fun showPermissionRationale(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Display Over Other Apps")
            .setMessage("This permission is required for the alarm to display properly over the lock screen.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Clear the requesting permissions flag
                permissionManager.setRequestingPermissions(false)
                // Continue checking other permissions
                checkRemainingPermissions()
            }
            .setOnDismissListener {
                // Clear the requesting permissions flag
                permissionManager.setRequestingPermissions(false)
                // Continue checking other permissions
                checkRemainingPermissions()
            }
            .show()
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("To ensure reliable alarm functionality, please allow this app to run in the background by disabling battery optimization.")
            .setPositiveButton("Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general settings
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Clear the requesting permissions flag
                permissionManager.setRequestingPermissions(false)
                // Continue checking other permissions
                checkRemainingPermissions()
            }
            .setOnDismissListener {
                // Clear the requesting permissions flag
                permissionManager.setRequestingPermissions(false)
                // Continue checking other permissions
                checkRemainingPermissions()
            }
            .show()
    }
    
    // Separate dialog for battery optimization when trying to add alarm
    private fun showBatteryOptimizationDialogForAlarm() {
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("To ensure reliable alarm functionality, please allow this app to run in the background by disabling battery optimization.")
            .setPositiveButton("Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general settings
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Later") { _, _ ->
                // Clear the requesting permissions flag
                permissionManager.setRequestingPermissions(false)
                // Still open alarm setup even if user postpones battery optimization
                // Ensure UI is set up before launching the activity
                if (isRequestingPermissions) {
                    isRequestingPermissions = false
                    setupUI()
                }
                
                val intent = Intent(this, AlarmSetupActivity::class.java)
                // Pass data indicating this is for creating a new alarm
                intent.putExtra(AlarmSetupActivity.EXTRA_IS_EDIT_MODE, false)
                alarmSetupLauncher.launch(intent)
            }
            .setOnDismissListener {
                // Clear the requesting permissions flag
                permissionManager.setRequestingPermissions(false)
            }
            .show()
    }
    
    private fun setupUI() {
        try {
            // Don't setup UI if we're still requesting permissions
            if (isRequestingPermissions) return
            
            // Setup ViewPager2 with adapter
            viewPagerAdapter = ViewPagerAdapter(this)
            binding.viewPager.adapter = viewPagerAdapter
            binding.viewPager.isUserInputEnabled = false // Disable swipe gesture
            
            // Set up the Add Alarm FAB
            fabAddAlarm = binding.fabAddAlarm
            fabAddAlarm.setOnClickListener {
                try {
                    // Check permissions before opening alarm setup
                    checkPermissionsAndOpenAlarmSetup()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in FAB click listener", e)
                    Toast.makeText(this, "Error opening alarm setup: ${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: Throwable) {
                    Log.e("MainActivity", "Unexpected error in FAB click listener", e)
                    Toast.makeText(this, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            // Set default selection to alarms (only tab now)
            binding.viewPager.currentItem = 0
            
            // Show the FAB since we're only showing the alarms page
            fabAddAlarm.show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupUI", e)
            Toast.makeText(this, "Error setting up UI: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Log.e("MainActivity", "Unexpected error in setupUI", e)
            Toast.makeText(this, "Unexpected UI error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning to the app, but only if we're not in the middle of requesting them
        if (!isRequestingPermissions) {
            // Refresh UI status
            setupUI()
            // Update permission status in case user granted them in settings
            checkPermissionsStatus()
        }
    }
    
    private fun checkPermissionsStatus() {
        try {
            // Update UI based on current permission status without showing dialogs
            // Check notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotificationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!hasNotificationPermission) {
                    // Permission not granted, but don't show dialog automatically
                }
            }
            
            // Check microphone permission
            val hasMicrophonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasMicrophonePermission) {
                // Permission not granted, but don't show dialog automatically
            }
            
            // Check overlay permission (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasOverlayPermission = Settings.canDrawOverlays(this)
                if (!hasOverlayPermission) {
                    // Permission not granted, but don't show dialog automatically
                }
            }
            
            // Check battery optimization (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val isBatteryOptimizationEnabled = batteryOptimizationManager.isBatteryOptimizationEnabled()
                if (isBatteryOptimizationEnabled) {
                    // Optimization enabled, but don't show dialog automatically
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking permissions status", e)
        }
    }
}