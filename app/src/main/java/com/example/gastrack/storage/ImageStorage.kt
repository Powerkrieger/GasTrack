package com.example.gastrack.storage

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageStorage {

    fun createReceiptFile(context: Context): File {
        val dir = File(context.filesDir, "receipts")
        if (!dir.exists()) dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "receipt_$timestamp.jpg")
    }

    fun getReceiptFile(path: String): File = File(path)
}
