package com.sweet.qr_scan_10_feb_26.ui.main

import android.app.Application
import androidx.lifecycle.*
import com.sweet.qr_scan_10_feb_26.data.database.AppDatabase
import com.sweet.qr_scan_10_feb_26.data.entity.TrashItem // ✅ Correct Import
import com.sweet.qr_scan_10_feb_26.data.repository.ScanRepository
import kotlinx.coroutines.launch

class RecentlyDeletedViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: ScanRepository
    val trashItems: LiveData<List<TrashItem>>

    init {
        val db = AppDatabase.getDatabase(application)
        repo = ScanRepository(db.scanFolderDao(), db.scanFileDao(), db.scanItemDao())
        trashItems = repo.getUnifiedTrash()
    }

    fun restore(item: TrashItem) = viewModelScope.launch {
        if (item.isFolder) repo.restoreFolder(item.id) else repo.restoreFileAndParent(item.id)
    }

    fun deletePermanently(item: TrashItem) = viewModelScope.launch {
        if (item.isFolder) repo.permanentDeleteFolder(item.id) else repo.permanentDeleteFile(item.id)
    }

    fun restoreAll() = viewModelScope.launch {
        val items = trashItems.value ?: return@launch
        items.forEach { item ->
            if (item.isFolder) repo.restoreFolder(item.id)
            else repo.restoreFileAndParent(item.id)
        }
    }

    fun emptyTrash() = viewModelScope.launch {
        val items = trashItems.value ?: return@launch
        items.forEach { item ->
            if (item.isFolder) repo.permanentDeleteFolder(item.id)
            else repo.permanentDeleteFile(item.id)
        }
    }

    // ရွေးထားသော Items များကိုသာ Restore လုပ်ရန်
    fun restoreSelected(items: List<TrashItem>) = viewModelScope.launch {
        items.forEach { item ->
            if (item.isFolder) repo.restoreFolder(item.id)
            else repo.restoreFileAndParent(item.id)
        }
    }

    // ရွေးထားသော Items များကိုသာ အပြီးတိုင်ဖျက်ရန်
    fun deleteSelectedPermanently(items: List<TrashItem>) = viewModelScope.launch {
        items.forEach { item ->
            if (item.isFolder) repo.permanentDeleteFolder(item.id)
            else repo.permanentDeleteFile(item.id)
        }
    }
}