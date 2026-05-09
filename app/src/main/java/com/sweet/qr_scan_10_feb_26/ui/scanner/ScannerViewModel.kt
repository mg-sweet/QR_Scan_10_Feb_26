package com.sweet.qr_scan_10_feb_26.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.sweet.qr_scan_10_feb_26.data.database.AppDatabase
import com.sweet.qr_scan_10_feb_26.data.entity.ScanItem
import com.sweet.qr_scan_10_feb_26.data.repository.ScanRepository
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: ScanRepository
    init {
        val db = AppDatabase.getDatabase(application)
        repo = ScanRepository(db.scanFolderDao(), db.scanFileDao(), db.scanItemDao())
    }
    fun getItems(fileId: Long) = repo.getItems(fileId)
    fun addScanItem(fileId: Long, v: String, f: String) = viewModelScope.launch { repo.addOrUpdateScanItem(fileId, v, f) }
    fun incrementItemQuantity(id: Long) = viewModelScope.launch { repo.incrementItemQuantity(id) }
    fun decrementItemQuantity(item: ScanItem) = viewModelScope.launch { repo.decrementItemQuantity(item) }
    fun deleteItem(item: ScanItem) = viewModelScope.launch { repo.deleteItem(item) }
}