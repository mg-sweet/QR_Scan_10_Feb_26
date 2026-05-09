package com.sweet.qr_scan_10_feb_26.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.sweet.qr_scan_10_feb_26.data.entity.*

@Dao
interface ScanFolderDao {
    @Query("SELECT f.id, f.name, f.createdDate, f.lastModified, f.restoredDate, (SELECT COUNT(*) FROM scan_files WHERE folderId = f.id AND isDeleted = 0) as totalCount, 0 as distinctCount FROM folders f WHERE isDeleted = 0 ORDER BY f.lastModified DESC")
    fun getAllFoldersWithStats(): LiveData<List<FolderWithStats>>

    @Query("SELECT id, name as name, deletedDate, 1 as isFolder, (SELECT COUNT(*) || ' files' FROM scan_files WHERE folderId = folders.id AND isDeleted = 0) as subInfo FROM folders WHERE isDeleted = 1 UNION ALL SELECT id, fileName as name, deletedDate, 0 as isFolder, originalFolderName as subInfo FROM scan_files WHERE isDeleted = 1 ORDER BY deletedDate DESC")
    fun getUnifiedTrash(): LiveData<List<TrashItem>>

    @Query("SELECT COUNT(*) FROM folders WHERE name = :name AND isDeleted = 0")
    suspend fun checkFolderNameExists(name: String): Int

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderByIdSync(id: Long): ScanFolder?

    @Query("SELECT * FROM folders WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getFolderByName(name: String): ScanFolder?

    @Insert
    suspend fun insertFolder(folder: ScanFolder): Long

    @Query("UPDATE folders SET isDeleted = 1, deletedDate = :ts WHERE id = :id")
    suspend fun softDeleteFolder(id: Long, ts: Long)

    @Query("UPDATE folders SET isDeleted = 1, deletedDate = :ts WHERE id IN (:ids)")
    suspend fun softDeleteMultipleFolders(ids: List<Long>, ts: Long)

    @Query("UPDATE folders SET isDeleted = 0, deletedDate = NULL, restoredDate = :ts WHERE id = :id")
    suspend fun restoreFolder(id: Long, ts: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun permanentDeleteFolder(id: Long)

    @Query("UPDATE folders SET name = :newName, lastModified = :ts WHERE id = :id")
    suspend fun renameFolder(id: Long, newName: String, ts: Long)

    @Query("DELETE FROM folders WHERE isDeleted = 1 AND deletedDate < :threshold")
    suspend fun autoCleanupTrash(threshold: Long)
}

@Dao
interface ScanFileDao {
    @Query("SELECT f.id, f.folderId, f.fileName, f.createdDate, f.restoredDate, (SELECT COUNT(DISTINCT scanValue) FROM scan_items WHERE fileId = f.id) as distinctCount, (SELECT COALESCE(SUM(quantity), 0) FROM scan_items WHERE fileId = f.id) as totalCount FROM scan_files f WHERE f.folderId = :folderId AND isDeleted = 0 ORDER BY f.createdDate DESC")
    fun getFilesWithStats(folderId: Long): LiveData<List<FileWithStats>>

    @Query("SELECT COUNT(*) FROM scan_files WHERE folderId = :folderId AND fileName = :name AND isDeleted = 0")
    suspend fun checkFileNameExists(folderId: Long, name: String): Int

    @Query("SELECT * FROM scan_files WHERE id = :id LIMIT 1")
    suspend fun getFileByIdSync(id: Long): ScanFile?

    @Insert
    suspend fun insertFile(file: ScanFile): Long

    @Query("UPDATE scan_files SET isDeleted = 1, deletedDate = :ts, originalFolderName = :folderName WHERE id = :id")
    suspend fun softDeleteFileWithContext(id: Long, folderName: String, ts: Long)

    @Query("UPDATE scan_files SET isDeleted = 0, deletedDate = NULL, restoredDate = :ts WHERE id = :id")
    suspend fun restoreFile(id: Long, ts: Long)

    @Query("DELETE FROM scan_files WHERE id = :id")
    suspend fun permanentDeleteFile(id: Long)

    @Query("UPDATE scan_files SET folderId = :newFolderId WHERE id = :fileId")
    suspend fun updateFileParent(fileId: Long, newFolderId: Long)

    @Query("UPDATE scan_files SET fileName = :newName WHERE id = :id")
    suspend fun renameFile(id: Long, newName: String)

    // ✅ Folder ကို အပြီးတိုင်မဖျက်မီ File များကို ကယ်တင်ရန် (Detach လုပ်ရန်)
    @Query("UPDATE scan_files SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachFilesFromFolder(folderId: Long)

    // ✅ Folder အများကြီးကို အပြီးတိုင်ဖျက်လျှင် File များကို ကယ်တင်ရန်
    @Query("UPDATE scan_files SET folderId = NULL WHERE folderId IN (:folderIds)")
    suspend fun detachFilesFromMultipleFolders(folderIds: List<Long>)
}

@Dao
interface ScanItemDao {
    @Query("SELECT * FROM scan_items WHERE fileId = :fileId ORDER BY lastScannedDate DESC")
    fun getItemsByFile(fileId: Long): LiveData<List<ScanItem>>

    @Query("SELECT * FROM scan_items WHERE fileId = :fileId ORDER BY lastScannedDate DESC")
    suspend fun getItemsByFileSync(fileId: Long): List<ScanItem>

    @Query("SELECT * FROM scan_items WHERE fileId = :fileId AND scanValue = :scanValue LIMIT 1")
    suspend fun getItemByValue(fileId: Long, scanValue: String): ScanItem?

    @Insert
    suspend fun insertItem(item: ScanItem): Long

    @Delete
    suspend fun deleteItem(item: ScanItem)

    @Query("UPDATE scan_items SET quantity = quantity + 1, lastScannedDate = :ts WHERE id = :id")
    suspend fun incrementQty(id: Long, ts: Long)

    @Query("UPDATE scan_items SET quantity = quantity - 1, lastScannedDate = :ts WHERE id = :id AND quantity > 1")
    suspend fun decrementQty(id: Long, ts: Long): Int

    @Query("SELECT s.fileName as fileName, i.scanValue as scanValue, i.barcodeFormat as barcodeFormat, i.quantity as quantity, i.lastScannedDate as lastScannedDate FROM scan_items i INNER JOIN scan_files s ON i.fileId = s.id WHERE s.folderId = :folderId ORDER BY s.createdDate DESC, i.lastScannedDate DESC")
    suspend fun getItemsWithFileNamesByFolder(folderId: Long): List<com.sweet.qr_scan_10_feb_26.utils.ItemWithFileName>
}