package com.sweet.qr_scan_10_feb_26.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.sweet.qr_scan_10_feb_26.data.database.AppDatabase
import com.sweet.qr_scan_10_feb_26.data.entity.ScanFolder
import com.sweet.qr_scan_10_feb_26.data.entity.FolderWithStats
import com.sweet.qr_scan_10_feb_26.data.repository.ScanRepository
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScanRepository
    // Type ကို FolderWithStats သို့ ပြောင်းလဲထားသည်
    val allFolders: LiveData<List<FolderWithStats>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ScanRepository(database.scanFolderDao(), database.scanItemDao())
        allFolders = repository.allFolders
    }

    fun createFolder(name: String, callback: (Long) -> Unit) {
        viewModelScope.launch {
            val folder = ScanFolder(name = name)
            val folderId = repository.insertFolder(folder)
            callback(folderId)
        }
    }

    fun deleteFolder(folder: ScanFolder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
        }
    }

    // Main Screen အတွက် ဤ function သည် ယခုအခါ မလိုအပ်တော့ပါ (Query ထဲတွင် တစ်ခါတည်း ပါပြီးဖြစ်သောကြောင့်)
    // သို့သော် အခြားနေရာတွင် သုံးချင်ပါက ဆက်ထားနိုင်ပါသည်
    suspend fun getFolderStats(folderId: Long): Pair<Int, Int> {
        val distinctCount = repository.getDistinctCount(folderId)
        val totalCount = repository.getTotalCount(folderId)
        return Pair(distinctCount, totalCount)
    }
}