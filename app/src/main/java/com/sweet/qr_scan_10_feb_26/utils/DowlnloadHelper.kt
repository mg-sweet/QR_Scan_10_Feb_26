package com.sweet.qr_scan_10_feb_26.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

object DownloadHelper {

    fun saveFileToDownloads(context: Context, tempFile: File): Boolean {
        val fileName = tempFile.name
        val resolver = context.contentResolver

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ✅ Android 10 (API 29) နှင့် အထက်အတွက် MediaStore သုံးခြင်း
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it).use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream!!)
                    }
                }
                true
            } ?: false
        } else {
            // ✅ Android 9 နှင့် အောက်အတွက် Legacy နည်းလမ်း
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadsDir, fileName)
                tempFile.copyTo(targetFile, overwrite = true)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}