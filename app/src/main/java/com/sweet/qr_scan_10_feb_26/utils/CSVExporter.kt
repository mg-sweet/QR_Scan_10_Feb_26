package com.sweet.qr_scan_10_feb_26.utils

import android.content.Context
import com.sweet.qr_scan_10_feb_26.data.database.AppDatabase
import com.sweet.qr_scan_10_feb_26.data.entity.ScanFolder
import com.sweet.qr_scan_10_feb_26.data.entity.ScanItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CSVExporter {

    /**
     * Folder များစွာကို တစ်ပြိုင်တည်း Export ထုတ်ပေးပြီး CSV File List ကို ပြန်ပေးသည်။
     */
    suspend fun exportFolders(context: Context, folders: List<ScanFolder>): List<File> {
        return withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            val csvFiles = mutableListOf<File>()

            for (folder in folders) {
                // Database မှ Data ကို တိုက်ရိုက်ဆွဲယူသည်
                val items = getItemsForFolder(database, folder.id)

                if (items.isNotEmpty()) {
                    // Folder Object တစ်ခုလုံးကို ပို့ပေးလိုက်သည် (lastModified သိနိုင်ရန်)
                    val file = createCSVFile(context, folder, items)
                    csvFiles.add(file)
                }
            }
            csvFiles
        }
    }

    private suspend fun getItemsForFolder(database: AppDatabase, folderId: Long): List<ScanItem> {
        return withContext(Dispatchers.IO) {
            try {
                // Suspend function သုံးပြီး Synchronous အတိုင်း Data ဆွဲယူသည်
                database.scanItemDao().getItemsByFolderSync(folderId)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun createCSVFile(
        context: Context,
        folder: ScanFolder,
        items: List<ScanItem>
    ): File {
        // 1. Folder Name ကို ဖိုင်သိမ်းလို့ရအောင် သန့်စင်ခြင်း
        val sanitizedName = folder.name.replace(Regex("[^a-zA-Z0-9]"), "_")

        // 2. ရက်စွဲနှင့် အချိန် Format များ သတ်မှတ်ခြင်း
        // Folder နောက်ဆုံး Scan ဖတ်ခဲ့သည့်ရက် (ဥပမာ - 26Feb)
        val lastUpdateFormatter = SimpleDateFormat("ddMMM", Locale.getDefault())
        val lastUpdateDate = lastUpdateFormatter.format(Date(folder.lastModified))

        // လက်ရှိ Export ထုတ်သည့်အချိန် (နာရီ၊ မိနစ်၊ စက္ကန့်)
        // ဤစက္ကန့်ပိုင်းသည် Android Cache Error ကို ဖြေရှင်းပေးမည့် အဓိကသော့ချက်ဖြစ်သည်
        val exportTimeFormatter = SimpleDateFormat("hh_mm_ss a", Locale.getDefault())
        val exportTime = exportTimeFormatter.format(Date())

        // 3. ဖိုင်နာမည် တည်ဆောက်ခြင်း
        // ဥပမာ - Warehouse_Updated_26Feb_104522.csv
        val fileName = "${sanitizedName}_${lastUpdateDate}_$exportTime.csv"
        val file = File(context.cacheDir, fileName)

        // 4. Maintenance: Cache ထဲရှိ ဤ Folder နှင့်ဆိုင်သော CSV အဟောင်းများကို ရှင်းထုတ်ခြင်း
        try {
            context.cacheDir.listFiles()?.forEach {
                if (it.name.startsWith(sanitizedName) && it.name.endsWith(".csv")) {
                    it.delete()
                }
            }
        } catch (e: Exception) {
            // Cleanup error ကို လျစ်လျူရှုနိုင်သည်
        }

        // 5. CSV ဖိုင်ထဲသို့ Data ရေးသားခြင်း
        FileWriter(file).use { writer ->
            // Header Row
            writer.append("Name,Qty\n")

            // Data Rows
            items.forEach { item ->
                // Scan တန်ဖိုးထဲတွင် (") ပါခဲ့လျှင် ("") ဟု ပြောင်းလဲပေးခြင်းဖြင့် CSV format ကို ထိန်းသိမ်းသည်
                val escapedValue = item.scanValue.replace("\"", "\"\"")

                // တန်ဖိုးများကို Double quotes ကြားထဲထည့်ခြင်းဖြင့် ကော်မာ (comma) ပါခဲ့လျှင်လည်း Column မကွဲစေရန် ကာကွယ်သည်
                writer.append("\"$escapedValue\",${item.quantity}\n")
            }
        }

        return file
    }
}