package com.sweet.qr_scan_10_feb_26.data.repository

import androidx.lifecycle.LiveData
import com.sweet.qr_scan_10_feb_26.data.dao.ScanFolderDao
import com.sweet.qr_scan_10_feb_26.data.dao.ScanItemDao
import com.sweet.qr_scan_10_feb_26.data.entity.ScanFolder
import com.sweet.qr_scan_10_feb_26.data.entity.ScanItem
import com.sweet.qr_scan_10_feb_26.data.entity.FolderWithStats

class ScanRepository(
    private val folderDao: ScanFolderDao,
    private val itemDao: ScanItemDao
) {
    // FolderWithStats ကို သုံးရန် ပြင်ဆင်ထားသည်
    val allFolders: LiveData<List<FolderWithStats>> = folderDao.getAllFoldersWithStats()

    suspend fun insertFolder(folder: ScanFolder): Long {
        return folderDao.insertFolder(folder)
    }

    suspend fun updateFolder(folder: ScanFolder) {
        folderDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folder: ScanFolder) {
        folderDao.deleteFolder(folder)
    }

    suspend fun getFolderById(folderId: Long): ScanFolder? {
        return folderDao.getFolderById(folderId)
    }

    // Item operations
    fun getItemsByFolder(folderId: Long): LiveData<List<ScanItem>> {
        return itemDao.getItemsByFolder(folderId)
    }

    suspend fun addOrUpdateScanItem(folderId: Long, scanValue: String, format: String) {
        val existingItem = itemDao.getItemByValue(folderId, scanValue)

        if (existingItem != null) {
            itemDao.incrementQuantity(existingItem.id)
        } else {
            val newItem = ScanItem(
                folderId = folderId,
                scanValue = scanValue,
                barcodeFormat = format
            )
            itemDao.insertItem(newItem)
        }

        val folder = folderDao.getFolderById(folderId)
        folder?.let {
            folderDao.updateFolder(it.copy(lastModified = System.currentTimeMillis()))
        }
    }

    suspend fun incrementItemQuantity(itemId: Long) {
        itemDao.incrementQuantity(itemId)
    }

    suspend fun decrementItemQuantity(item: ScanItem) {
        val rowsAffected = itemDao.decrementQuantity(item.id)
        if (rowsAffected == 0 && item.quantity <= 1) {
            itemDao.deleteItem(item)
        }
    }

    suspend fun deleteItem(item: ScanItem) {
        itemDao.deleteItem(item)
    }

    suspend fun getDistinctCount(folderId: Long): Int {
        return itemDao.getDistinctCount(folderId)
    }

    suspend fun getTotalCount(folderId: Long): Int {
        return itemDao.getTotalCount(folderId) ?: 0
    }
}