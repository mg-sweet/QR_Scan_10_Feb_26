package com.sweet.qr_scan_10_feb_26.data.repository

import androidx.lifecycle.LiveData
import com.sweet.qr_scan_10_feb_26.data.dao.*
import com.sweet.qr_scan_10_feb_26.data.entity.*

class ScanRepository(private val folderDao: ScanFolderDao, private val fileDao: ScanFileDao, private val itemDao: ScanItemDao) {
    val allFolders = folderDao.getAllFoldersWithStats()
    fun getUnifiedTrash() = folderDao.getUnifiedTrash()

    suspend fun insertFolderSmart(name: String?): Long {
        val baseName = if (name.isNullOrBlank()) "Folder" else name.trim()
        var finalName = baseName
        var count = 1
        while (folderDao.checkFolderNameExists(finalName) > 0) { finalName = "${baseName}_$count"; count++ }
        return folderDao.insertFolder(ScanFolder(name = finalName))
    }

    suspend fun renameFolder(id: Long, name: String) = folderDao.renameFolder(id, name, System.currentTimeMillis())


    suspend fun renameFile(id: Long, newName: String) {
        fileDao.renameFile(id, newName)
    }

    suspend fun softDeleteFolder(id: Long) = folderDao.softDeleteFolder(id, System.currentTimeMillis())
    suspend fun softDeleteMultipleFolders(ids: List<Long>) = folderDao.softDeleteMultipleFolders(ids, System.currentTimeMillis())
    suspend fun restoreFolder(id: Long) = folderDao.restoreFolder(id, System.currentTimeMillis())
    //suspend fun permanentDeleteFolder(id: Long) = folderDao.permanentDeleteFolder(id)
    suspend fun cleanupOldTrash(threshold: Long) = folderDao.autoCleanupTrash(threshold)
    suspend fun getFolderById(id: Long) = folderDao.getFolderByIdSync(id)

    fun getFiles(folderId: Long) = fileDao.getFilesWithStats(folderId)
    suspend fun insertFileSmart(folderId: Long, name: String?): Long {
        val baseName = if (name.isNullOrBlank()) "File" else name.trim()
        var finalName = baseName; var count = 1
        while (fileDao.checkFileNameExists(folderId, finalName) > 0) { finalName = "${baseName}_$count"; count++ }
        return fileDao.insertFile(ScanFile(folderId = folderId, fileName = finalName))
    }
    suspend fun softDeleteFile(id: Long, folderName: String) = fileDao.softDeleteFileWithContext(id, folderName, System.currentTimeMillis())

    // fix restore parent folder
    suspend fun restoreFileAndParent(fileId: Long) {
        val file = fileDao.getFileByIdSync(fileId) ?: return
        val parent = folderDao.getFolderByIdSync(file.folderId ?: -1)
        val now = System.currentTimeMillis()

        var activeFolderId = file.folderId // File ဝင်သွားရမည့် Folder ID

        // Parent မရှိတော့ဘူး (သို့) အမှိုက်ပုံးထဲ ရောက်နေတယ်ဆိုရင်...
        if (parent == null || parent.isDeleted) {

            // ၁။ ဆရာပြောသလို မှတ်ထားတဲ့ Folder Name ကို ပြန်ယူမယ် (မရှိရင် Recovered လို့ နာမည်ပေးမယ်)
            val targetFolderName = file.originalFolderName ?: "Recovered Projects"

            // ၂။ အဲဒီနာမည်နဲ့ Folder အရှင် ရှိ/မရှိ စစ်မယ်
            var targetFolder = folderDao.getFolderByName(targetFolderName)

            if (targetFolder == null) {
                // ၃။ မရှိရင် Folder အသစ်ပြန်ဆောက်မယ်
                val newId = folderDao.insertFolder(ScanFolder(name = targetFolderName))
                targetFolder = folderDao.getFolderByIdSync(newId)
            }

            // ၄။ Database ထဲက File ရဲ့ Parent ID ကို အသစ်ဆောက်လိုက်တဲ့ Folder ID နဲ့ အစားထိုးမယ်
            targetFolder?.let {
                fileDao.updateFileParent(file.id, it.id)
                activeFolderId = it.id // ✅ Memory ထဲမှာပါ ID အသစ် ပြောင်းမှတ်ထားလိုက်မယ်
            }
        } else {
            // Parent ရှိသေးတယ်ဆိုရင် Parent ကိုပါ Trash ထဲကနေ ပြန်ဆွဲထုတ်မယ်
            folderDao.restoreFolder(parent.id, now)
        }

        // ၅။ နောက်ဆုံးမှ File ကို Trash ထဲကနေ ပြန်ထုတ်မယ်
        fileDao.restoreFile(file.id, now)

    }
    suspend fun permanentDeleteFile(id: Long) = fileDao.permanentDeleteFile(id)

    fun getItems(fileId: Long) = itemDao.getItemsByFile(fileId)
    suspend fun addOrUpdateScanItem(fileId: Long, value: String, format: String) {
        val existing = itemDao.getItemByValue(fileId, value)
        if (existing != null) itemDao.incrementQty(existing.id, System.currentTimeMillis())
        else itemDao.insertItem(ScanItem(fileId = fileId, scanValue = value, barcodeFormat = format))
    }
    suspend fun incrementItemQuantity(id: Long) = itemDao.incrementQty(id, System.currentTimeMillis())
    suspend fun decrementItemQuantity(item: ScanItem) {
        if (itemDao.decrementQty(item.id, System.currentTimeMillis()) == 0 && item.quantity <= 1) itemDao.deleteItem(item)
    }
    suspend fun deleteItem(item: ScanItem) = itemDao.deleteItem(item)
    suspend fun getItemsWithFileNamesByFolder(folderId: Long) = itemDao.getItemsWithFileNamesByFolder(folderId)

    suspend fun permanentDeleteFolder(id: Long) {
        // ၁။ Database က Cascade နဲ့ လိုက်မဖျက်ခင် File တွေကို အရင်ခွဲထုတ်မယ်
        fileDao.detachFilesFromFolder(id)

        // ၂။ File တွေ လွတ်မြောက်သွားပြီဖြစ်လို့ Folder ကြီးကို စိတ်ချလက်ချ ဖျက်လို့ရပါပြီ
        folderDao.permanentDeleteFolder(id)
    }

    // အကယ်၍ Empty Trash လုပ်တဲ့အခါ Folder တွေ အများကြီးကို တစ်ပြိုင်နက် ဖျက်တဲ့ Logic ရှိရင်-
    suspend fun permanentDeleteMultipleFolders(ids: List<Long>) {
        fileDao.detachFilesFromMultipleFolders(ids)
        ids.forEach { folderDao.permanentDeleteFolder(it) }
    }
}