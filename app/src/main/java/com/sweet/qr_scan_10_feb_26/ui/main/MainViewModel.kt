package com.sweet.qr_scan_10_feb_26.ui.main

import android.app.Application
import androidx.lifecycle.*
import com.sweet.qr_scan_10_feb_26.data.database.AppDatabase
import com.sweet.qr_scan_10_feb_26.data.entity.*
import com.sweet.qr_scan_10_feb_26.data.repository.ScanRepository
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo: ScanRepository
    val allFolders: LiveData<List<FolderWithStats>>

    // Trash Count Badge
    val trashCount: LiveData<Int>

    init {
        // ၁။ Database နှင့် Repository ကို အရင်ဆုံး Initialize လုပ်ရမည်
        val db = AppDatabase.getDatabase(app)
        repo = ScanRepository(db.scanFolderDao(), db.scanFileDao(), db.scanItemDao())

        // ၂။ LiveData များကို ချိတ်ဆက်မည်
        allFolders = repo.allFolders
        trashCount = repo.getUnifiedTrash().map { it.size }

        // ၃။ Repository အဆင်သင့်ဖြစ်ပြီဖြစ်၍ အမှိုက်ရှင်းစနစ် စတင်မည်
        runAutoTrashCleanup()
    }

    private fun runAutoTrashCleanup() {
        viewModelScope.launch {
            val prefs = PreferencesManager(app)
            val days = prefs.autoEmptyTrashDays

            // ရက်သတ်မှတ်ထားမှသာ အလုပ်လုပ်မည် (0 ဆိုရင် Manual မို့လို့ မလုပ်ပါ)
            if (days > 0) {
                // ၁ရက် = 86,400,000 မီလီစက္ကန့်
                val msInDay = 24L * 60 * 60 * 1000
                val thresholdTime = System.currentTimeMillis() - (days * msInDay)

                // ✅ ဤနေရာတွင် 'repo' ကို သုံးရပါမည် ('repository' ဟု မရေးရပါ)
                repo.cleanupOldTrash(thresholdTime)
            }
        }
    }

    fun createFolder(name: String?, cb: (Long) -> Unit) = viewModelScope.launch {
        cb(repo.insertFolderSmart(name))
    }

    fun renameFolder(id: Long, newName: String) = viewModelScope.launch {
        repo.renameFolder(id, newName)
    }

    fun softDeleteFolder(id: Long) = viewModelScope.launch {
        repo.softDeleteFolder(id)
    }

    fun softDeleteMultipleFolders(ids: List<Long>) = viewModelScope.launch {
        repo.softDeleteMultipleFolders(ids)
    }

    fun cleanupOldTrash(threshold: Long) = viewModelScope.launch {
        repo.cleanupOldTrash(threshold)
    }

    fun createFileInFolder(folderId: Long, name: String?, cb: (Long) -> Unit) = viewModelScope.launch {
        cb(repo.insertFileSmart(folderId, name))
    }
}