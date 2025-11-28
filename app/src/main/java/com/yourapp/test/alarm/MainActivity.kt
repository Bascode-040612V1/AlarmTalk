package com.yourapp.test.alarm

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourapp.test.alarm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fabAddAlarm: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var viewPagerAdapter: ViewPagerAdapter
    private lateinit var permissionManager: PermissionManager
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var alarmSetupLauncher: ActivityResultLauncher<Intent>
    
    private var isRequestingPermissions = false
    
    // Permission launchers for modern permission handling
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
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
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionRationale("Microphone permission is required for voice recording features.")
        }
        // Continue checking other permissions
        checkRemainingPermissions()
    }

    // Add storage permission launcher
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            showPermissionRationale("Storage permission is required to save voice recordings to the Music folder.")
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
        
        // Initialize activity result launcher
        alarmSetupLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // Handle result from AlarmSetupActivity if needed
        }
        
        // Setup UI immediately - moved to requestAllPermissions
        // setupUI()
        
        // Request all necessary permissions in background
        requestAllPermissions()
    }
    
    private fun requestAllPermissions() {
        // Always setup UI immediately, don't wait for permissions
        setupUI()
        
        // Check if this is the first time the app is being used
        val isFirstUse = permissionManager.isFirstUse()
        
        // If it's the first use, request all permissions
        if (isFirstUse && permissionManager.shouldShowPermissionDialog()) {
            isRequestingPermissions = true
            permissionManager.setRequestingPermissions(true)
            checkRemainingPermissions()
        }
        // Otherwise, we don't block the UI with permission requests
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
            
            // Request storage permission (Different permissions for different Android versions)
            if (!checkStoragePermission()) {
                requestStoragePermission()
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
            
            // Show a toast to inform user that all permissions are granted
            Toast.makeText(this, "All permissions granted! App is fully functional.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking permissions", e)
            Toast.makeText(this, "Error checking permissions: ${e.message}", Toast.LENGTH_LONG).show()
            // Clear the requesting permissions flag even on error
            permissionManager.setRequestingPermissions(false)
        }
    }
    
    // Add storage permission check method
    private fun checkStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // For Android 13+, we need READ_MEDIA_AUDIO for accessing audio files
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // For Android 10-12, we need READ_EXTERNAL_STORAGE for accessing files in shared storage
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                // For Android 9 and earlier, we need WRITE_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    // Add storage permission request method
    private fun requestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // For Android 13+, request READ_MEDIA_AUDIO
                storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // For Android 10-12, request READ_EXTERNAL_STORAGE
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                // For Android 9 and earlier, request WRITE_EXTERNAL_STORAGE
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
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
            
            // Check storage permission (Different permissions for different Android versions)
            if (!checkStoragePermission()) {
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
                    "• Storage permission - To save voice recordings to the Music folder\n" +
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
            }
            .show()
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization")
            .setMessage("Please disable battery optimization for this app to ensure reliable alarm functionality.")
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
            // Setup ViewPager2 with adapter - ALWAYS setup UI regardless of permissions
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
            
            // Ensure the main content is visible
            binding.root.visibility = android.view.View.VISIBLE
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
            
            // Check storage permission (Different permissions for different Android versions)
            val hasStoragePermission = checkStoragePermission()
            if (!hasStoragePermission) {
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