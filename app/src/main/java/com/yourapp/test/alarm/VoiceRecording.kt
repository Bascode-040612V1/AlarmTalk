package com.yourapp.test.alarm

import java.io.File

data class VoiceRecording(
    val file: File,
    val name: String,
    val date: String,
    val duration: String
)