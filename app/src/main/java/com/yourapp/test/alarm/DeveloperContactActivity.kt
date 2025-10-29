package com.yourapp.test.alarm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.card.MaterialCardView

class DeveloperContactActivity : AppCompatActivity() {

    private lateinit var cardEmail: MaterialCardView
    private lateinit var cardGithub: MaterialCardView
    private lateinit var cardFeedback: MaterialCardView
    private lateinit var cardBugReport: MaterialCardView
    private lateinit var cardWebsite: MaterialCardView
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_contact)
        
        initViews()
        setupToolbar()
        setupContactOptions()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        cardEmail = findViewById(R.id.cardEmail)
        cardGithub = findViewById(R.id.cardGithub)
        cardFeedback = findViewById(R.id.cardFeedback)
        cardBugReport = findViewById(R.id.cardBugReport)
        cardWebsite = findViewById(R.id.cardWebsite)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Information"
        }
    }
    
    private fun setupContactOptions() {
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
                val mailto = "mailto:$email?subject=${Uri.encode(subject)}&body=${Uri.encode("Hi developers,

I'm reaching out regarding the Alarm App.

")}"
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
                val mailto = "mailto:$email?subject=${Uri.encode(subject)}&body=${Uri.encode("Hi team,

I'd like to share feedback about the Alarm App:

" +
                        "What I like:\n\n" +
                        "What could be improved:\n\n" +
                        "Additional features I'd like to see:\n\n" +
                        "Thanks for creating this app!")}"
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
                    "- Android version: ${android.os.Build.VERSION.RELEASE}\n" +
                    "- Device model: ${android.os.Build.MODEL}\n" +
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
                val mailto = "mailto:$email?subject=${Uri.encode(subject)}&body=${Uri.encode("Hi developers,

I found a bug in the Alarm App:

" +
                        "Steps to reproduce:\n1. \n2. \n3. \n\n" +
                        "Expected behavior:\n\n" +
                        "Actual behavior:\n\n" +
                        "Device information:\n" +
                        "- Android version: ${android.os.Build.VERSION.RELEASE}\n" +
                        "- Device model: ${android.os.Build.MODEL}\n" +
                        "- App version: 2.3\n\n" +
                        "Additional notes:\n\n")}"
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