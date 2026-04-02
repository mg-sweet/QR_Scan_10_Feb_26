package com.sweet.qr_scan_10_feb_26.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
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

        // Edge-to-Edge Design Setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Notch Padding Logic (tvAppName နှင့် btnShareAll ကို Notch အောက်သို့ တွန်းချခြင်း)
        ViewCompat.setOnApplyWindowInsetsListener(binding.tvAppName) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            // Margin Top ကို Dynamic ထည့်ခြင်း
            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.topMargin = top + 20
            view.layoutParams = params
            insets
        }


        setupRecyclerView()
        observeFolders()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onFolderClick = { stats ->
                val intent = Intent(this, ScannerActivity::class.java).apply {
                    putExtra("FOLDER_ID", stats.id)
                    putExtra("FOLDER_NAME", stats.name)
                }
                startActivity(intent)
            },
            onDeleteClick = { stats -> showDeleteConfirmation(stats) }
        )

        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = folderAdapter
            // ConstraintLayout အတွင်း Nested Scroll ကောင်းစေရန်
            isNestedScrollingEnabled = true
        }
    }

    private fun observeFolders() {
        viewModel.allFolders.observe(this) { folders ->
            folderAdapter.submitList(folders)

            // Dashboard Stats Summary
            binding.tvTotalFolders.text = folders.size.toString()
            binding.tvAllScansCount.text = folders.sumOf { it.totalCount }.toString()

            binding.emptyState.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        // Folder အသစ်ဆောက်ခြင်း
        binding.fabCreateFolder.setOnClickListener {
            showCreateFolderDialog()
        }

        // Header ရှိ Share ခလုတ်တစ်ခုတည်းဖြင့် Export လုပ်ခြင်း
        binding.btnShareAll.setOnClickListener {
            val folders = viewModel.allFolders.value ?: emptyList()
            if (folders.isNotEmpty()) {
                showShareOptionsBottomSheet(folders)
            } else {
                Toast.makeText(this, "No folders to share", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Modern Bottom Sheet Export
    private fun showShareOptionsBottomSheet(folders: List<FolderWithStats>) {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = com.sweet.qr_scan_10_feb_26.databinding.LayoutShareBottomSheetBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        val selectedMap = mutableMapOf<Long, Boolean>()
        folders.forEach { selectedMap[it.id] = false }

        sheetBinding.rvShareSelection.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val cb = CheckBox(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        setPadding(40, 40, 40, 40)
                    }
                    return object : RecyclerView.ViewHolder(cb) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val folder = folders[position]
                    (holder.itemView as CheckBox).apply {
                        text = "${folder.name} (${folder.totalCount} scans)"
                        isChecked = selectedMap[folder.id] ?: false
                        setOnCheckedChangeListener { _, isChecked -> selectedMap[folder.id] = isChecked }
                    }
                }
                override fun getItemCount() = folders.size
            }
        }

        sheetBinding.btnConfirmExport.setOnClickListener {
            val selected = folders.filter { selectedMap[it.id] == true }
                .map { ScanFolder(it.id, it.name, it.createdDate, it.lastModified) }

            if (selected.isNotEmpty()) {
                exportFolders(selected)
                bottomSheet.dismiss()
            } else {
                Toast.makeText(this, "Select at least one folder", Toast.LENGTH_SHORT).show()
            }
        }
        bottomSheet.show()
    }

    private fun showCreateFolderDialog() {
        val input = TextInputEditText(this).apply { hint = "Enter folder name" }
        MaterialAlertDialogBuilder(this)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) viewModel.createFolder(name) {
                    Toast.makeText(this, "Folder Created", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(stats: FolderWithStats) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Folder?")
            .setMessage("All scans in \"${stats.name}\" will be lost.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteFolder(ScanFolder(stats.id, stats.name, stats.createdDate, stats.lastModified))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportFolders(folders: List<ScanFolder>) {
        lifecycleScope.launch {
            try {
                val files = CSVExporter.exportFolders(this@MainActivity, folders)
                if (files.isNotEmpty()) {
                    val uris = files.map { FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", it) }
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "text/csv"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Share Data"))
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}