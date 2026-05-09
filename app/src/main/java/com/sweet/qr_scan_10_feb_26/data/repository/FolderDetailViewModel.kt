package com.sweet.qr_scan_10_feb_26.ui.main

import android.app.Application
import androidx.lifecycle.*
import com.sweet.qr_scan_10_feb_26.data.database.AppDatabase
import com.sweet.qr_scan_10_feb_26.data.entity.*
import com.sweet.qr_scan_10_feb_26.data.repository.ScanRepository
import kotlinx.coroutines.launch

class FolderDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: ScanRepository

    // ✅ LiveData ကို အကြိမ်ကြိမ် မဆောက်မိစေရန် Cache သိမ်းမည့် Variable များ
    private var currentFolderId: Long = -1L
    private var cachedFilesLiveData: LiveData<List<FileWithStats>>? = null

    init {
        val db = AppDatabase.getDatabase(application)
        repo = ScanRepository(db.scanFolderDao(), db.scanFileDao(), db.scanItemDao())
    }

    // ✅ Optimized: LiveData အသစ်ထပ်မထွက်အောင် ထိန်းထားသည် (Observer ပွားခြင်းမှ ကာကွယ်ရန်)
    fun getFiles(folderId: Long): LiveData<List<FileWithStats>> {
        if (cachedFilesLiveData == null || currentFolderId != folderId) {
            currentFolderId = folderId
            cachedFilesLiveData = repo.getFiles(folderId)
        }
        return cachedFilesLiveData!!
    }

    fun createFile(folderId: Long, name: String?, callback: (Long) -> Unit) = viewModelScope.launch {
        val id = repo.insertFileSmart(folderId, name)
        callback(id)
    }

    fun renameFile(id: Long, newName: String) = viewModelScope.launch {
        repo.renameFile(id, newName)
    }

    // ✅ File အတွက် Soft Delete (stats နှင့် folderName ကို လက်ခံသည်)
    fun deleteFile(stats: FileWithStats, folderName: String) = viewModelScope.launch {
        repo.softDeleteFile(stats.id, folderName)
    }

    // ✅ Selection Mode အတွက် ID ဖြင့်ဖျက်ခြင်း
    fun softDeleteFileById(fileId: Long, folderName: String) = viewModelScope.launch {
        repo.softDeleteFile(fileId, folderName)
    }

    fun restoreFile(id: Long) = viewModelScope.launch {
        repo.restoreFileAndParent(id)
    }
}