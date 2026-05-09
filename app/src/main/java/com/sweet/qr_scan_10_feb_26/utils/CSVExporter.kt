package com.sweet.qr_scan_10_feb_26.utils

import android.content.Context
import com.sweet.qr_scan_10_feb_26.data.database.AppDatabase
import com.sweet.qr_scan_10_feb_26.data.entity.*
import com.sweet.qr_scan_10_feb_26.data.repository.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class ItemWithFileName(
    val fileName: String, val scanValue: String, val barcodeFormat: String,
    val quantity: Int, val lastScannedDate: Long
)

object CSVExporter {

    // ====================================================
    // 📂 SEPARATE MODE (ဖိုင်ခွဲ၍ ထုတ်ခြင်း)
    // ====================================================

    // ၁။ (Folder Detail မျက်နှာပြင်မှ) Session များကို ဖိုင်ခွဲထုတ်ရန်
    suspend fun exportIndividualSessions(context: Context, sessions: List<FileWithStats>): List<File> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val exportList = mutableListOf<File>()
            for (session in sessions) {
                val items = db.scanItemDao().getItemsByFileSync(session.id)
                val sanitized = session.fileName.replace(Regex("[^a-zA-Z0-9]"), "_")
                val file = File(context.cacheDir, "${sanitized}_Session.csv")
                FileWriter(file).use { writer ->
                    writer.append("Value,Format,Quantity\n")
                    items.forEach { writer.append("\"${it.scanValue}\",${it.barcodeFormat},${it.quantity}\n") }
                }
                exportList.add(file)
            }
            exportList
        }
    }

    // ၂။ (Home မျက်နှာပြင်မှ) Folder များကို ဖိုင်ခွဲထုတ်ရန်
    suspend fun exportFolders(context: Context, folders: List<ScanFolder>): List<File> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val repo = ScanRepository(db.scanFolderDao(), db.scanFileDao(), db.scanItemDao())
            val csvFiles = mutableListOf<File>()
            for (folder in folders) {
                val items = repo.getItemsWithFileNamesByFolder(folder.id)
                val sanitized = folder.name.replace(Regex("[^a-zA-Z0-9]"), "_")
                val file = File(context.cacheDir, "${sanitized}_Project.csv")
                FileWriter(file).use { writer ->
                    writer.append("Session_Name,Value,Format,Quantity,Date_Modified\n")
                    val sdf = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.getDefault())
                    items.forEach { item ->
                        writer.append("\"${item.fileName}\",\"${item.scanValue}\",${item.barcodeFormat},${item.quantity},\"${sdf.format(Date(item.lastScannedDate))}\"\n")
                    }
                }
                csvFiles.add(file)
            }
            csvFiles
        }
    }

    // ====================================================
    // 📦 MERGE MODE (CSV တစ်ဖိုင်တည်းအဖြစ် ပေါင်းထုတ်ခြင်း)
    // ====================================================

    // ၃။ (Folder Detail မှ) Session များအားလုံးကို CSV တစ်ဖိုင်တည်း ပေါင်းထုတ်ရန်
    suspend fun exportMergedSessionsToOneFile(context: Context, sessions: List<FileWithStats>, folderName: String = "Merged"): List<File> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val timestamp = SimpleDateFormat("ddMMM_HHmm", Locale.getDefault()).format(Date())
            val file = File(context.cacheDir, "${folderName}_All_Sessions_$timestamp.csv")
            FileWriter(file).use { writer ->
                writer.append("Session_Name,Value,Format,Quantity\n") // စာရင်းမရောသွားအောင် Session နာမည်ပါ ထည့်ပေးထားသည်
                sessions.forEach { session ->
                    val items = db.scanItemDao().getItemsByFileSync(session.id)
                    items.forEach { item ->
                        writer.append("\"${session.fileName}\",\"${item.scanValue}\",${item.barcodeFormat},${item.quantity}\n")
                    }
                }
            }
            listOf(file) // List ပုံစံဖြင့် ပြန်ပို့ပေးမည် (Flow မပျက်စေရန်)
        }
    }

    // ၄။ (Home မှ) Project/Folder များအားလုံးကို CSV တစ်ဖိုင်တည်း ပေါင်းထုတ်ရန်
    suspend fun exportMergedFoldersToOneFile(context: Context, folders: List<ScanFolder>): List<File> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val repo = ScanRepository(db.scanFolderDao(), db.scanFileDao(), db.scanItemDao())
            val timestamp = SimpleDateFormat("ddMMM_HHmm", Locale.getDefault()).format(Date())
            val file = File(context.cacheDir, "All_Projects_Merged_$timestamp.csv")
            FileWriter(file).use { writer ->
                writer.append("Project_Name,Session_Name,Value,Format,Quantity,Date_Modified\n")
                val sdf = SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.getDefault())
                folders.forEach { folder ->
                    val items = repo.getItemsWithFileNamesByFolder(folder.id)
                    items.forEach { item ->
                        writer.append("\"${folder.name}\",\"${item.fileName}\",\"${item.scanValue}\",${item.barcodeFormat},${item.quantity},\"${sdf.format(Date(item.lastScannedDate))}\"\n")
                    }
                }
            }
            listOf(file)
        }
    }

    // (Single Item အတွက်)
    suspend fun exportSingleFile(context: Context, stats: FileWithStats): File {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val items = db.scanItemDao().getItemsByFileSync(stats.id)
            val sanitized = stats.fileName.replace(Regex("[^a-zA-Z0-9]"), "_")
            val file = File(context.cacheDir, "${sanitized}_Single.csv")
            FileWriter(file).use { writer ->
                writer.append("Value,Format,Quantity\n")
                items.forEach { writer.append("\"${it.scanValue}\",${it.barcodeFormat},${it.quantity}\n") }
            }
            file
        }
    }
}