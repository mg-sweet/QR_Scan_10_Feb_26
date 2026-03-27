package com.sweet.qr_scan_10_feb_26.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.data.entity.FolderWithStats
import com.sweet.qr_scan_10_feb_26.data.entity.ScanFolder
import com.sweet.qr_scan_10_feb_26.databinding.ActivityMainBinding
import com.sweet.qr_scan_10_feb_26.ui.scanner.ScannerActivity
import com.sweet.qr_scan_10_feb_26.utils.CSVExporter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var folderAdapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar အရောင်သတ်မှတ်ခြင်း
        window.statusBarColor = ContextCompat.getColor(this, R.color.purple_500)

        setupRecyclerView()
        observeFolders()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onFolderClick = { folderWithStats ->
                val intent = Intent(this, ScannerActivity::class.java)
                intent.putExtra("FOLDER_ID", folderWithStats.id)
                intent.putExtra("FOLDER_NAME", folderWithStats.name)
                startActivity(intent)
            },
            onDeleteClick = { folderWithStats ->
                showDeleteConfirmation(folderWithStats)
            }
        )

        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = folderAdapter
        }
    }

    private fun observeFolders() {
        // ViewModel မှ LiveData<List<FolderWithStats>> ကို စောင့်ကြည့်ခြင်း
        viewModel.allFolders.observe(this) { folders ->
            folderAdapter.submitList(folders)

            if (folders.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvFolders.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvFolders.visibility = View.VISIBLE
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabCreateFolder.setOnClickListener {
            showCreateFolderDialog()
        }

        // Share Icon နှင့် Arrow နှစ်ခုလုံးအတွက် logic တစ်ခုတည်းသုံးခြင်း
        val shareAction = View.OnClickListener {
            val folders = viewModel.allFolders.value ?: emptyList()
            if (folders.isEmpty()) {
                Toast.makeText(this, "No folders to share", Toast.LENGTH_SHORT).show()
            } else {
                showShareOptionsDialog(folders)
            }
        }

        binding.btnShareIcon.setOnClickListener(shareAction)
        binding.btnShareArrow.setOnClickListener(shareAction)
    }

    private fun showCreateFolderDialog() {
        val input = TextInputEditText(this).apply {
            hint = "Folder name"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString()?.trim() ?: ""
                if (name.isNotEmpty()) {
                    viewModel.createFolder(name) {
                        Toast.makeText(this, "Folder created", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please enter a folder name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(folderWithStats: FolderWithStats) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Folder?")
            .setMessage("This will delete all scans in \"${folderWithStats.name}\". This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // FolderWithStats မှ ScanFolder object သို့ ပြောင်းလဲခြင်း
                val folderToDelete = ScanFolder(
                    id = folderWithStats.id,
                    name = folderWithStats.name,
                    createdDate = folderWithStats.createdDate,
                    lastModified = folderWithStats.lastModified
                )
                viewModel.deleteFolder(folderToDelete)
                Toast.makeText(this, "Folder deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showShareOptionsDialog(folders: List<FolderWithStats>) {
        val folderNames = folders.map { it.name }.toTypedArray()
        val selectedFolders = BooleanArray(folders.size) { false }

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Folders to Export")
            .setMultiChoiceItems(folderNames, selectedFolders) { _, which, isChecked ->
                selectedFolders[which] = isChecked
            }
            .setPositiveButton("Export") { _, _ ->
                // ရွေးချယ်ထားသော FolderWithStats များကို ScanFolder အဖြစ်ပြောင်း၍ Export လုပ်ခြင်း
                val selected = folders.filterIndexed { index, _ -> selectedFolders[index] }
                    .map { stats ->
                        ScanFolder(
                            id = stats.id,
                            name = stats.name,
                            createdDate = stats.createdDate,
                            lastModified = stats.lastModified
                        )
                    }

                if (selected.isNotEmpty()) {
                    exportFolders(selected)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportFolders(folders: List<ScanFolder>) {
        lifecycleScope.launch {
            try {
                // CSVExporter သည် List<ScanFolder> ကို လက်ခံသည်
                val csvFiles = CSVExporter.exportFolders(this@MainActivity, folders)

                if (csvFiles.isNotEmpty()) {
                    shareCsvFiles(csvFiles)
                } else {
                    Toast.makeText(this@MainActivity, "No data to export", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareCsvFiles(files: List<File>) {
        val uris = files.map { file ->
            FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share CSV Files"))
    }
}