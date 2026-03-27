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

    private val repository: ScanRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ScanRepository(database.scanFolderDao(), database.scanItemDao())
    }

    fun getScanItems(folderId: Long): LiveData<List<ScanItem>> {
        return repository.getItemsByFolder(folderId)
    }

    fun addScanItem(folderId: Long, scanValue: String, format: String) {
        viewModelScope.launch {
            repository.addOrUpdateScanItem(folderId, scanValue, format)
        }
    }

    fun incrementQuantity(itemId: Long) {
        viewModelScope.launch {
            repository.incrementItemQuantity(itemId)
        }
    }

    fun decrementQuantity(item: ScanItem) {
        viewModelScope.launch {
            repository.decrementItemQuantity(item)
        }
    }

    fun deleteItem(item: ScanItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }
}
