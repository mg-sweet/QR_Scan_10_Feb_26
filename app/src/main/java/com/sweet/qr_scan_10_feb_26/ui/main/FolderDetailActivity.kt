package com.sweet.qr_scan_10_feb_26.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.data.entity.FileWithStats
import com.sweet.qr_scan_10_feb_26.databinding.ActivityFolderDetailBinding
import com.sweet.qr_scan_10_feb_26.databinding.LayoutShareBottomSheetBinding
import com.sweet.qr_scan_10_feb_26.ui.scanner.ScannerActivity
import com.sweet.qr_scan_10_feb_26.utils.CSVExporter
import com.sweet.qr_scan_10_feb_26.utils.DownloadHelper
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayList

class FolderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderDetailBinding
    private val viewModel: FolderDetailViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter
    private var folderId: Long = -1
    private var currentFolderName: String = ""

    // ✅ မူရင်း List အား သိမ်းထားရန်
    private var originalFileList: List<FileWithStats> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        binding = ActivityFolderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // CardView အစား အတွင်း Layout ကို Padding ပေးသဖြင့် UI လုံးဝ မပျက်တော့ပါ
            view.setPadding(0, top, 0, 0)
            insets
        }

        folderId = intent.getLongExtra("FOLDER_ID", -1)
        currentFolderName = intent.getStringExtra("FOLDER_NAME") ?: "Project"
        binding.tvFolderName.text = currentFolderName

        setupRecyclerView()
        observeFiles()
        setupClickListeners()

        // ✅ Back နှိပ်လျှင် Search Mode ထဲမှ အရင်ထွက်ရန် Logic
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutSearchToolbar.visibility == View.VISIBLE) {
                    binding.btnCloseSearch.performClick()
                } else if (fileAdapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onFileClick = { file ->
                val intent = Intent(this, ScannerActivity::class.java).apply {
                    putExtra("FILE_ID", file.id); putExtra("FILE_NAME", file.fileName)
                }
                startActivity(intent)
            },
            onLongClick = { stats ->
                enterSelectionMode()
                fileAdapter.selectedIds.add(stats.id)
                fileAdapter.notifyDataSetChanged()
                binding.tvSelectionCount.text = "1 Selected"
            },
            onMenuClick = { stats, view -> showFilePopupMenu(stats, view) },
            onSelectionChanged = { count ->
                binding.tvSelectionCount.text = "$count Selected"
                val totalCount = fileAdapter.currentList.size
                if (count == totalCount && totalCount > 0) {
                    binding.btnSelectAll.setImageResource(R.drawable.ic_deselect_all)
                } else {
                    binding.btnSelectAll.setImageResource(R.drawable.ic_select_all)
                }
            }
        )
        binding.rvFiles.adapter = fileAdapter
        binding.rvFiles.layoutManager = LinearLayoutManager(this)

        fileAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) binding.rvFiles.smoothScrollToPosition(0)
            }
        })
    }

    private fun observeFiles() {
        viewModel.getFiles(folderId).observe(this) { list ->
            originalFileList = list // မူရင်း List သိမ်းမည်

            // Search Box ထဲက စာသားယူပြီး Filter လုပ်မည်
            val currentQuery = binding.etSearchFiles.text.toString().trim()
            filterFiles(currentQuery)
        }
    }

    // ✅ Search Filter Function
    private fun filterFiles(query: String) {
        val filteredList = if (query.isEmpty()) {
            originalFileList
        } else {
            originalFileList.filter {
                it.fileName.contains(query, ignoreCase = true)
            }
        }
        fileAdapter.submitList(ArrayList(filteredList))
        binding.emptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.fabAddFile.setOnClickListener { showCreateFileDialog() }
        binding.btnCloseSelection.setOnClickListener { exitSelectionMode() }

        // ==========================================
        // 🌟 Expandable Search Bar Logic 🌟
        // ==========================================

        // Search ဖွင့်ရန်
        binding.btnOpenSearch.setOnClickListener {
            binding.layoutNormalToolbar.visibility = View.GONE
            binding.layoutSearchToolbar.visibility = View.VISIBLE
            binding.etSearchFiles.requestFocus()
            showKeyboard(binding.etSearchFiles)
        }

        // Search ပိတ်ရန်
        binding.btnCloseSearch.setOnClickListener {
            binding.layoutSearchToolbar.visibility = View.GONE
            binding.layoutNormalToolbar.visibility = View.VISIBLE
            binding.etSearchFiles.text?.clear() // Text ရှင်းလိုက်လျှင် မူရင်း List ပြန်ပေါ်လာမည်
            hideKeyboard(binding.etSearchFiles)
        }

        // စာသားရှင်းရန် (Clear Button)
        binding.btnClearSearch.setOnClickListener {
            binding.etSearchFiles.text?.clear()
        }

        // စာရိုက်တိုင်း Filter အလုပ်လုပ်ရန်
        binding.etSearchFiles.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFiles(s.toString().trim())
                // Clear ခလုတ် အဖွင့်အပိတ်
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        // ==========================================
        // Selection Mode & Other Actions
        // ==========================================
        binding.btnSelectAll.setOnClickListener {
            fileAdapter.toggleSelectAll()
            val selectedCount = fileAdapter.selectedIds.size
            val totalCount = fileAdapter.currentList.size
            binding.tvSelectionCount.text = "$selectedCount Selected"
            if (selectedCount == totalCount && totalCount > 0) {
                binding.btnSelectAll.setImageResource(R.drawable.ic_deselect_all)
            } else {
                binding.btnSelectAll.setImageResource(R.drawable.ic_select_all)
            }
        }

        binding.btnShareSelected.setOnClickListener { shareSelectedFiles() }

        binding.btnDownloadSelected.setOnClickListener {
            val selectedIds = fileAdapter.selectedIds.toList()

            if (selectedIds.isNotEmpty()) {

                // ✅ Setting ကို လှမ်းဖတ်မည်
                val prefs = PreferencesManager(this)
                val isMergeMode = (prefs.exportMethod == 0)

                lifecycleScope.launch {
                    try {
                        val allFiles = fileAdapter.currentList
                        val selected = allFiles.filter { selectedIds.contains(it.id) }
                        if (selected.isNotEmpty()) {
                            // ✅ Setting ပေါ်မူတည်၍ ခေါ်မည်
                            val csvFiles = if (isMergeMode) {
                                CSVExporter.exportMergedSessionsToOneFile(this@FolderDetailActivity, selected, currentFolderName)
                            } else {
                                CSVExporter.exportIndividualSessions(this@FolderDetailActivity, selected)
                            }
                            if (csvFiles.isNotEmpty()) {
                                csvFiles.forEach { file -> DownloadHelper.saveFileToDownloads(this@FolderDetailActivity, file) }
                                Toast.makeText(this@FolderDetailActivity, "Saved ${csvFiles.size} files to Downloads", Toast.LENGTH_SHORT).show()
                                exitSelectionMode()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@FolderDetailActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else { Toast.makeText(this, "Please select at least one item", Toast.LENGTH_SHORT).show() }
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selectedIds = fileAdapter.selectedIds.toList()
            if (selectedIds.isNotEmpty()) {
                MaterialAlertDialogBuilder(this).setTitle("Move to Trash?").setMessage("Move ${selectedIds.size} sessions to Trash?")
                    .setPositiveButton("OK") { _, _ ->
                        selectedIds.forEach { viewModel.softDeleteFileById(it, currentFolderName) }
                        exitSelectionMode()
                        Toast.makeText(this, "Moved to trash", Toast.LENGTH_SHORT).show()
                    }.setNegativeButton("Cancel", null).show()
            }
        }

        binding.btnShareFiles.setOnClickListener {
            if (!fileAdapter.isSelectionMode) {
                enterSelectionMode()

            }
            // when user click select mode , search is gone


        }
    }

    private fun showDeleteConfirmation(stats: FileWithStats) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Move to Trash?")
            .setMessage("Move session \"${stats.fileName}\" to recently deleted?")
            .setPositiveButton("Trash") { _, _ ->
                viewModel.deleteFile(stats, currentFolderName)
                Toast.makeText(this, "Moved to trash", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun shareSelectedFiles() {
        val selectedIds = fileAdapter.selectedIds.toList()

        // ✅ Setting ကို လှမ်းဖတ်မည်
        val prefs = PreferencesManager(this)
        val isMergeMode = (prefs.exportMethod == 0)

        if (selectedIds.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val allFiles = fileAdapter.currentList
                    val selectedSessions = allFiles.filter { selectedIds.contains(it.id) }
                    if (selectedSessions.isNotEmpty()) {
                        // ✅ Setting ပေါ်မူတည်၍ ခေါ်မည်
                        val csvFiles = if (isMergeMode) {
                            CSVExporter.exportMergedSessionsToOneFile(this@FolderDetailActivity, selectedSessions, currentFolderName)
                        } else {
                            CSVExporter.exportIndividualSessions(this@FolderDetailActivity, selectedSessions)
                        }

                        if (csvFiles.isNotEmpty()) {
                            shareCsvFiles(csvFiles)
                            exitSelectionMode()
                        } else { Toast.makeText(this@FolderDetailActivity, "No data to export", Toast.LENGTH_SHORT).show() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@FolderDetailActivity, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else { Toast.makeText(this, "Please select at least one item", Toast.LENGTH_SHORT).show() }
    }

    private fun shareCsvFiles(files: List<File>) {
        if (files.isEmpty()) return
        val uris = files.map { FileProvider.getUriForFile(this, "$packageName.provider", it) }
        val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = "text/csv"
            if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            else putExtra(Intent.EXTRA_STREAM, uris[0])
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Data"))
    }

    private fun showFilePopupMenu(stats: FileWithStats, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menu.add("Download CSV"); popup.menu.add("Rename"); popup.menu.add("Select"); popup.menu.add("Delete")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Download CSV" -> downloadSingleFile(stats)
                "Rename" -> showRenameFileDialog(stats)
                "Select" -> { enterSelectionMode(); fileAdapter.selectedIds.add(stats.id); fileAdapter.notifyDataSetChanged(); binding.tvSelectionCount.text = "1 Selected" }
                "Delete" -> showDeleteConfirmation(stats)
            }
            true
        }
        popup.show()
    }

    private fun downloadSingleFile(stats: FileWithStats) {
        lifecycleScope.launch {
            try {
                val file = CSVExporter.exportSingleFile(this@FolderDetailActivity, stats)
                DownloadHelper.saveFileToDownloads(this@FolderDetailActivity, file)
                Toast.makeText(this@FolderDetailActivity, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showRenameFileDialog(stats: FileWithStats) {
        val input = TextInputEditText(this).apply { setText(stats.fileName) }
        MaterialAlertDialogBuilder(this).setTitle("Rename Session").setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) viewModel.renameFile(stats.id, newName)
            }.show()
    }

    private fun enterSelectionMode() {

        // ✅ Search Mode ဝင်နေလျှင် အရင်ပိတ်မည်
        if (binding.layoutSearchToolbar.visibility == View.VISIBLE) {
            binding.btnCloseSearch.performClick()
        }

        fileAdapter.toggleSelectionMode(true)
        binding.selectionBarCard.visibility = View.VISIBLE
        binding.selectionBarCard.animate().translationY(0f).setDuration(200).start()
        binding.fabAddFile.animate().translationY(500f).setDuration(200).start()

        // ✅ Selection Mode ဝင်လျှင် Search Icon ကို ဖျောက်မည်
        binding.btnOpenSearch.visibility = View.GONE

    }

    private fun exitSelectionMode() {
        fileAdapter.toggleSelectionMode(false)
        binding.selectionBarCard.animate().translationY(200f).setDuration(200).withEndAction { binding.selectionBarCard.visibility = View.GONE }.start()
        binding.fabAddFile.animate().translationY(0f).setDuration(200).start()

        // ✅ Selection Mode ထွက်လျှင် Search Icon ပြန်ဖော်မည်
        binding.btnOpenSearch.visibility = View.VISIBLE
    }

//    private fun showCreateFileDialog() {
//        val input = TextInputEditText(this).apply { hint = "Session Name" }
//        MaterialAlertDialogBuilder(this).setTitle("New Session").setView(input)
//            .setPositiveButton("Start") { _, _ ->
//                val name = input.text.toString().trim()
//                viewModel.createFile(folderId, name) { id ->
//                    val intent = Intent(this, ScannerActivity::class.java).apply {
//                        putExtra("FILE_ID", id); putExtra("FILE_NAME", if (name.isEmpty()) "File" else name)
//                    }
//                    startActivity(intent)
//                }
//            }.show()
//    }

    private fun showCreateFileDialog() {
        val context = this
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }

        val input = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = "Session Name"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }

        val textInputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            addView(input)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_NONE
        }

        val helperText = android.widget.TextView(context).apply {
            text = "Leave blank to auto-generate a name."
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            setPadding(12, 8, 0, 0)
        }

        layout.addView(textInputLayout)
        layout.addView(helperText)

        MaterialAlertDialogBuilder(context)
            .setTitle("New Session")
            .setView(layout)
            .setPositiveButton("Start") { _, _ ->
                val name = input.text.toString().trim()
                viewModel.createFile(folderId, name) { id ->
                    val intent = Intent(this, ScannerActivity::class.java).apply {
                        putExtra("FILE_ID", id)
                        // အလွတ်ထားခဲ့ရင် Repository က Auto Name ("File_1") ထုတ်ပေးမှာဖြစ်လို့၊ Activity ကို လှမ်းပို့မယ့် နာမည်ကိုပါ ချိန်ညှိပါမယ်
                        putExtra("FILE_NAME", if (name.isEmpty()) "Session" else name)
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
            .apply {
                input.requestFocus()
                window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
    }

    // ✅ Keyboard အဖွင့်အပိတ် လုပ်ရန် Helper Functions
    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}