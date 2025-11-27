package com.yourapp.test.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
    private lateinit var btnEnableNotification: Button
    private lateinit var btnEnableMicrophone: Button
    private lateinit var btnEnableOverlay: Button
    private lateinit var btnEnableBattery: Button

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
            btnEnableNotification = findViewById(R.id.btnEnableNotification)
            btnEnableMicrophone = findViewById(R.id.btnEnableMicrophone)
            btnEnableOverlay = findViewById(R.id.btnEnableOverlay)
            btnEnableBattery = findViewById(R.id.btnEnableBattery)
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
        } catch (e: Exception) {
            Log.e("DeveloperContactActivity", "Error setting up contact options", e)
        }
    }
    
    private fun checkPermissions() {
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
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
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
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
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
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
    }
    
    private fun updatePermissionStatus(
        statusText: TextView, 
        enableButton: Button, 
        isEnabled: Boolean
    ) {
        if (isEnabled) {
            statusText.text = "Enabled"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.white))
            // Use rounded green button with app theme blending
            statusText.setBackgroundResource(R.drawable.rounded_green_button)
            statusText.setPadding(
                resources.getDimension(R.dimen.spacing_medium).toInt(),
                resources.getDimension(R.dimen.spacing_small).toInt(),
                resources.getDimension(R.dimen.spacing_medium).toInt(),
                resources.getDimension(R.dimen.spacing_small).toInt()
            )
            enableButton.visibility = android.view.View.GONE
        } else {
            statusText.text = "Disabled"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.white))
            statusText.setBackgroundColor(ContextCompat.getColor(this, R.color.delete_color))
            statusText.setBackgroundResource(R.drawable.button_gradient_background)
            statusText.setPadding(
                resources.getDimension(R.dimen.spacing_medium).toInt(),
                resources.getDimension(R.dimen.spacing_small).toInt(),
                resources.getDimension(R.dimen.spacing_medium).toInt(),
                resources.getDimension(R.dimen.spacing_small).toInt()
            )
            enableButton.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun requestNotificationPermission() {
        // Check if permission is already granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission already granted", Toast.LENGTH_SHORT).show()
                // Refresh the UI
                checkPermissions()
                return
            }
        }
        
        // Use PermissionManager to check if we should show a dialog
        val permissionManager = PermissionManager(this)
        if (!permissionManager.isRequestingPermissions()) {
            // Open app settings to allow user to grant permission
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Permission request in progress", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestMicrophonePermission() {
        // Check if permission is already granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission already granted", Toast.LENGTH_SHORT).show()
            // Refresh the UI
            checkPermissions()
            return
        }
        
        // Use PermissionManager to check if we should show a dialog
        val permissionManager = PermissionManager(this)
        if (!permissionManager.isRequestingPermissions()) {
            // Open app settings to allow user to grant permission
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Permission request in progress", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestOverlayPermission() {
        // Check if permission is already granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
                // Refresh the UI
                checkPermissions()
                return
            }
        }
        
        // Use PermissionManager to check if we should show a dialog
        val permissionManager = PermissionManager(this)
        if (!permissionManager.isRequestingPermissions()) {
            // Open overlay permission settings
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Permission request in progress", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestBatteryPermission() {
        // Check if permission is already granted
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Battery optimization already disabled", Toast.LENGTH_SHORT).show()
                // Refresh the UI
                checkPermissions()
                return
            }
        }
        
        // Use PermissionManager to check if we should show a dialog
        val permissionManager = PermissionManager(this)
        if (!permissionManager.isRequestingPermissions()) {
            // Open battery optimization settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        } else {
            Toast.makeText(this, "Permission request in progress", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Open app settings directly instead of trying to open a non-existent PermissionsActivity
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
    
    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning to this screen
        checkPermissions()
    }
    
    private fun sendEmail() {
        try {
            val email = getString(R.string.developer_email)
            val subject = "Alarm App - Contact"
            val body = "Hi developers,\n\nI'm reaching out regarding the Alarm App.\n\n"
            
            // Improved email intent with better compatibility across email clients
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // Only email apps should handle this
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            // Check if there's an app that can handle this intent
            if (emailIntent.resolveActivity(packageManager) != null) {
                startActivity(emailIntent)
            } else {
                // Fallback to ACTION_SEND if ACTION_SENDTO doesn't work
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                
                // Try to create chooser with the fallback intent
                val chooser = Intent.createChooser(fallbackIntent, "Send email using...")
                startActivity(chooser)
            }
        } catch (e: Exception) {
            // Ultimate fallback - try basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - Contact"
                val body = "Hi developers,\n\nI'm reaching out regarding the Alarm App.\n\n"
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
            val chooser = Intent.createChooser(githubIntent, "Open with")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open GitHub", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendFeedback() {
        try {
            val email = getString(R.string.developer_email)
            val subject = "Alarm App - Feedback"
            val body = "Hi team,\n\nI'd like to share feedback about the Alarm App:\n\n" +
                    "What I like:\n\n" +
                    "What could be improved:\n\n" +
                    "Additional features I'd like to see:\n\n" +
                    "Thanks for creating this app!"
            
            // Improved email intent with better compatibility across email clients
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // Only email apps should handle this
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            // Check if there's an app that can handle this intent
            if (emailIntent.resolveActivity(packageManager) != null) {
                startActivity(emailIntent)
            } else {
                // Fallback to ACTION_SEND if ACTION_SENDTO doesn't work
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                
                // Try to create chooser with the fallback intent
                val chooser = Intent.createChooser(fallbackIntent, "Send feedback using...")
                startActivity(chooser)
            }
        } catch (e: Exception) {
            // Ultimate fallback - try basic mailto URI
            try {
                val email = getString(R.string.developer_email)
                val subject = "Alarm App - Feedback"
                val body = "Hi team,\n\nI'd like to share feedback about the Alarm App:\n\n" +
                        "What I like:\n\n" +
                        "What could be improved:\n\n" +
                        "Additional features I'd like to see:\n\n" +
                        "Thanks for creating this app!"
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
                    "- Android version: " + android.os.Build.VERSION.RELEASE + "\n" +
                    "- Device model: " + android.os.Build.MODEL + "\n" +
                    "- App version: 2.3\n\n" +
                    "Additional notes:\n\n"
            
            // Improved email intent with better compatibility across email clients
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") // Only email apps should handle this
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            
            // Check if there's an app that can handle this intent
            if (emailIntent.resolveActivity(packageManager) != null) {
                startActivity(emailIntent)
            } else {
                // Fallback to ACTION_SEND if ACTION_SENDTO doesn't work
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                
                // Try to create chooser with the fallback intent
                val chooser = Intent.createChooser(fallbackIntent, "Report bug using...")
                startActivity(chooser)
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
                        "- Android version: " + android.os.Build.VERSION.RELEASE + "\n" +
                        "- Device model: " + android.os.Build.MODEL + "\n" +
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
            val websiteUrl = "https://alarmtalk.is-best.net/AlarmTalk_Website"
            val websiteIntent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
            
            // More robust intent handling
            val chooser = Intent.createChooser(websiteIntent, "Open website with...")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}