package com.yourapp.test.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class DeveloperContactActivity : AppCompatActivity() {

    private lateinit var cardEmail: MaterialCardView
    private lateinit var cardGithub: MaterialCardView
    private lateinit var cardFeedback: MaterialCardView
    private lateinit var cardBugReport: MaterialCardView
    private lateinit var cardWebsite: MaterialCardView
    private lateinit var cardPermissions: MaterialCardView
    private lateinit var toolbar: Toolbar
    
    // Permission UI elements
    private lateinit var textNotificationStatus: TextView
    private lateinit var textMicrophoneStatus: TextView
    private lateinit var textOverlayStatus: TextView
    private lateinit var textBatteryStatus: TextView
    private lateinit var textStorageStatus: TextView
    private lateinit var btnEnableNotification: Button
    private lateinit var btnEnableMicrophone: Button
    private lateinit var btnEnableOverlay: Button
    private lateinit var btnEnableBattery: Button
    private lateinit var btnEnableStorage: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_developer_contact)
            
            initViews()
            setupToolbar()
            setupContactOptions()
            checkPermissions()
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error opening information screen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun initViews() {
        try {
            toolbar = findViewById(R.id.toolbar)
            cardEmail = findViewById(R.id.cardEmail)
            cardGithub = findViewById(R.id.cardGithub)
            cardFeedback = findViewById(R.id.cardFeedback)
            cardBugReport = findViewById(R.id.cardBugReport)
            cardWebsite = findViewById(R.id.cardWebsite)
            cardPermissions = findViewById(R.id.cardPermissions)
            
            // Initialize permission UI elements
            textNotificationStatus = findViewById(R.id.textNotificationStatus)
            textMicrophoneStatus = findViewById(R.id.textMicrophoneStatus)
            textOverlayStatus = findViewById(R.id.textOverlayStatus)
            textBatteryStatus = findViewById(R.id.textBatteryStatus)
            textStorageStatus = findViewById(R.id.textStorageStatus)
            btnEnableNotification = findViewById(R.id.btnEnableNotification)
            btnEnableMicrophone = findViewById(R.id.btnEnableMicrophone)
            btnEnableOverlay = findViewById(R.id.btnEnableOverlay)
            btnEnableBattery = findViewById(R.id.btnEnableBattery)
            btnEnableStorage = findViewById(R.id.btnEnableStorage)
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error initializing views", e)
            throw e
        }
    }
    
    private fun setupToolbar() {
        try {
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
                title = "Information"
            }
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error setting up toolbar", e)
        }
    }
    
    private fun setupContactOptions() {
        try {
            // Email contact
            cardEmail.setOnClickListener {
                sendEmail()
            }
            
            // GitHub repository
            cardGithub.setOnClickListener {
                openGitHub()
            }
            
            // App feedback
            cardFeedback.setOnClickListener {
                sendFeedback()
            }
            
            // Bug report
            cardBugReport.setOnClickListener {
                reportBug()
            }
            
            // Website
            cardWebsite.setOnClickListener {
                openWebsite()
            }
            
            // Permissions Settings - Directly open app settings instead of trying to open a non-existent activity
            cardPermissions.setOnClickListener {
                openAppSettings()
            }
            
            // Permission enable buttons
            btnEnableNotification.setOnClickListener {
                requestNotificationPermission()
            }
            
            btnEnableMicrophone.setOnClickListener {
                requestMicrophonePermission()
            }
            
            btnEnableOverlay.setOnClickListener {
                requestOverlayPermission()
            }
            
            btnEnableBattery.setOnClickListener {
                requestBatteryPermission()
            }
            
            btnEnableStorage.setOnClickListener {
                requestStoragePermission()
            }
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error setting up contact options", e)
        }
    }
    
    private fun checkPermissions() {
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            updatePermissionStatus(
                textNotificationStatus, 
                btnEnableNotification, 
                hasNotificationPermission
            )
        } else {
            // For older Android versions, hide notification permission UI
            textNotificationStatus.visibility = android.view.View.GONE
            btnEnableNotification.visibility = android.view.View.GONE
        }
        
        // Check microphone permission
        val hasMicrophonePermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        updatePermissionStatus(
            textMicrophoneStatus, 
            btnEnableMicrophone, 
            hasMicrophonePermission
        )
        
        // Check overlay permission
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Overlay permission not needed on older versions
        }
        
        updatePermissionStatus(
            textOverlayStatus, 
            btnEnableOverlay, 
            hasOverlayPermission
        )
        
        // Check battery optimization
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val hasBatteryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true // Battery optimization not needed on older versions
        }
        
        updatePermissionStatus(
            textBatteryStatus, 
            btnEnableBattery, 
            hasBatteryPermission
        )
        
        // Check storage permission
        val hasStoragePermission = checkStoragePermission()
        
        updatePermissionStatus(
            textStorageStatus,
            btnEnableStorage,
            hasStoragePermission
        )
    }
    
    private fun checkStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // For Android 13+, we need READ_MEDIA_AUDIO for accessing audio files
                ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // For Android 10-12, we need READ_EXTERNAL_STORAGE for accessing files in shared storage
                ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                // For Android 9 and earlier, we need WRITE_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
    
    private fun updatePermissionStatus(statusText: TextView, enableButton: Button, isGranted: Boolean) {
        try {
            if (isGranted) {
                statusText.text = "Enabled"
                statusText.setBackgroundResource(R.drawable.rounded_green_button)
                enableButton.visibility = android.view.View.GONE
            } else {
                statusText.text = "Disabled"
                statusText.setBackgroundResource(R.drawable.button_gradient_background)
                enableButton.visibility = android.view.View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error updating permission status", e)
        }
    }
    
    private fun sendEmail() {
        try {
            val email = getString(R.string.developer_email)
            val subject = "Alarm App - General Inquiry"
            val body = "Hi developers,\n\nI have a question about the Alarm App:\n\n" +
                    "Device information:\n" +
                    "- Android version: " + Build.VERSION.RELEASE + "\n" +
                    "- Device model: " + Build.MODEL + "\n" +
                    "- App version: 2.3\n\n"
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            startActivity(Intent.createChooser(intent, "Send email using..."))
        } catch (e: ActivityNotFoundException) {
            // Fallback to basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - General Inquiry"
                val body = "Hi developers,\n\nI have a question about the Alarm App:\n\n" +
                        "Device information:\n" +
                        "- Android version: " + Build.VERSION.RELEASE + "\n" +
                        "- Device model: " + Build.MODEL + "\n" +
                        "- App version: 2.3\n\n"
                val mailto = "mailto:" + email + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
                val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mailto))
                
                // Try to create chooser with the fallback intent
                val chooser = Intent.createChooser(emailIntent, "Send email using...")
                startActivity(chooser)
            } catch (fallbackEx: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Ultimate fallback - try basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - General Inquiry"
                val body = "Hi developers,\n\nI have a question about the Alarm App:\n\n" +
                        "Device information:\n" +
                        "- Android version: " + Build.VERSION.RELEASE + "\n" +
                        "- Device model: " + Build.MODEL + "\n" +
                        "- App version: 2.3\n\n"
                val mailto = "mailto:" + email + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
                val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mailto))
                startActivity(emailIntent)
            } catch (fallbackEx: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openGitHub() {
        try {
            val githubUrl = "https://github.com/Bascode-040612V1/AlarmTalk"
            val githubIntent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
            
            // More robust intent handling
            val chooser = Intent.createChooser(githubIntent, "Open GitHub with...")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendFeedback() {
        try {
            val email = getString(R.string.developer_email)
            val subject = "Alarm App - User Feedback"
            val body = "Hi developers,\n\nI'd like to share my feedback on the Alarm App:\n\n" +
                    "What I like:\n\n" +
                    "Suggestions for improvement:\n\n" +
                    "Device information:\n" +
                    "- Android version: " + Build.VERSION.RELEASE + "\n" +
                    "- Device model: " + Build.MODEL + "\n" +
                    "- App version: 2.3\n\n"
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            startActivity(Intent.createChooser(intent, "Send feedback using..."))
        } catch (e: ActivityNotFoundException) {
            // Fallback to basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - User Feedback"
                val body = "Hi developers,\n\nI'd like to share my feedback on the Alarm App:\n\n" +
                        "What I like:\n\n" +
                        "Suggestions for improvement:\n\n" +
                        "Device information:\n" +
                        "- Android version: " + Build.VERSION.RELEASE + "\n" +
                        "- Device model: " + Build.MODEL + "\n" +
                        "- App version: 2.3\n\n"
                val mailto = "mailto:" + email + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
                val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mailto))
                
                // Try to create chooser with the fallback intent
                val chooser = Intent.createChooser(emailIntent, "Send feedback using...")
                startActivity(chooser)
            } catch (fallbackEx: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Ultimate fallback - try basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - User Feedback"
                val body = "Hi developers,\n\nI'd like to share my feedback on the Alarm App:\n\n" +
                        "What I like:\n\n" +
                        "Suggestions for improvement:\n\n" +
                        "Device information:\n" +
                        "- Android version: " + Build.VERSION.RELEASE + "\n" +
                        "- Device model: " + Build.MODEL + "\n" +
                        "- App version: 2.3\n\n"
                val mailto = "mailto:" + email + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
                val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mailto))
                startActivity(emailIntent)
            } catch (fallbackEx: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun reportBug() {
        try {
            val email = getString(R.string.developer_email)
            val subject = "Alarm App - Bug Report"
            val body = "Hi developers,\n\nI found a bug in the Alarm App:\n\n" +
                    "Steps to reproduce:\n1. \n2. \n3. \n\n" +
                    "Expected behavior:\n\n" +
                    "Actual behavior:\n\n" +
                    "Device information:\n" +
                    "- Android version: " + Build.VERSION.RELEASE + "\n" +
                    "- Device model: " + Build.MODEL + "\n" +
                    "- App version: 2.3\n\n" +
                    "Additional notes:\n\n"
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            startActivity(Intent.createChooser(intent, "Report bug using..."))
        } catch (e: ActivityNotFoundException) {
            // Fallback to basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - Bug Report"
                val body = "Hi developers,\n\nI found a bug in the Alarm App:\n\n" +
                        "Steps to reproduce:\n1. \n2. \n3. \n\n" +
                        "Expected behavior:\n\n" +
                        "Actual behavior:\n\n" +
                        "Device information:\n" +
                        "- Android version: " + Build.VERSION.RELEASE + "\n" +
                        "- Device model: " + Build.MODEL + "\n" +
                        "- App version: 2.3\n\n" +
                        "Additional notes:\n\n"
                val mailto = "mailto:" + email + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
                val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mailto))
                
                // Try to create chooser with the fallback intent
                val chooser = Intent.createChooser(emailIntent, "Report bug using...")
                startActivity(chooser)
            } catch (fallbackEx: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Ultimate fallback - try basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - Bug Report"
                val body = "Hi developers,\n\nI found a bug in the Alarm App:\n\n" +
                        "Steps to reproduce:\n1. \n2. \n3. \n\n" +
                        "Expected behavior:\n\n" +
                        "Actual behavior:\n\n" +
                        "Device information:\n" +
                        "- Android version: " + Build.VERSION.RELEASE + "\n" +
                        "- Device model: " + Build.MODEL + "\n" +
                        "- App version: 2.3\n\n" +
                        "Additional notes:\n\n"
                val mailto = "mailto:" + email + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
                val emailIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mailto))
                startActivity(emailIntent)
            } catch (fallbackEx: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openWebsite() {
        try {
            val websiteUrl = "http://alarmtalk.is-best.net/"
            val websiteIntent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
            
            // More robust intent handling
            val chooser = Intent.createChooser(websiteIntent, "Open website with...")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open app settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error requesting notification permission", e)
            Toast.makeText(this, "Error requesting notification permission", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestMicrophonePermission() {
        try {
            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                1002
            )
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error requesting microphone permission", e)
            Toast.makeText(this, "Error requesting microphone permission", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error requesting overlay permission", e)
            Toast.makeText(this, "Error requesting overlay permission", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestBatteryPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (fallbackEx: Exception) {
                Log.e("DeveloperContactActivity", "Error requesting battery permission", fallbackEx)
                Toast.makeText(this, "Error requesting battery permission", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error requesting battery permission", e)
            Toast.makeText(this, "Error requesting battery permission", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestStoragePermission() {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    // For Android 13+, request READ_MEDIA_AUDIO
                    requestPermissions(
                        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO),
                        1004
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // For Android 10-12, request READ_EXTERNAL_STORAGE
                    requestPermissions(
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                        1004
                    )
                }
                else -> {
                    // For Android 9 and earlier, request WRITE_EXTERNAL_STORAGE
                    requestPermissions(
                        arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        1004
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error requesting storage permission", e)
            Toast.makeText(this, "Error requesting storage permission", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            when (requestCode) {
                1001 -> {
                    // Notification permission result
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
                1002 -> {
                    // Microphone permission result
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
                1004 -> {
                    // Storage permission result
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            // Refresh permission status display
            checkPermissions()
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error handling permission result", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            // Refresh permission status when returning to this screen
            checkPermissions()
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error in onResume", e)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}