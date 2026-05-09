package com.sweet.qr_scan_10_feb_26.ui.main

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.data.entity.TrashItem
import com.sweet.qr_scan_10_feb_26.databinding.ActivityRecentlyDeletedBinding
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager

class RecentlyDeletedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecentlyDeletedBinding
    private val viewModel: RecentlyDeletedViewModel by viewModels()
    private lateinit var trashAdapter: TrashAdapter

    // ✅ မူရင်း List အား သိမ်းထားရန်
    private var originalTrashList: List<TrashItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI Appearance
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        binding = ActivityRecentlyDeletedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Notch Padding အတွက် Container ကို အသုံးပြုထားသည်
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        // ==========================================
        // 🌟 Dynamic UI Text Logic
        // ==========================================
        val prefs = PreferencesManager(this)
        val days = prefs.autoEmptyTrashDays

        binding.tvTrashHint.text = if (days > 0) {
            "Items in Trash will be permanently deleted after $days days."
        } else {
            "Items in Trash will stay here until you manually delete them."
        }

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()

        // ✅ Back Handler for Search & Selection Mode
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutSearchToolbar.visibility == View.VISIBLE) {
                    binding.btnCloseSearch.performClick()
                } else if (trashAdapter.isSelectionMode) {
                    exitSelectionMode()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        trashAdapter = TrashAdapter(
            onRestore = { item ->
                viewModel.restore(item)
                Toast.makeText(this, "Restored Successfully", Toast.LENGTH_SHORT).show()
            },
            onDelete = { item ->
                showSingleDeleteDialog(item)
            }
        )

        binding.rvTrash.apply {
            layoutManager = LinearLayoutManager(this@RecentlyDeletedActivity)
            adapter = trashAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.trashItems.observe(this) { list ->
            originalTrashList = list // မူရင်း List သိမ်းမည်

            // Search Box ထဲက စာသားယူပြီး Filter လုပ်မည်
            val currentQuery = binding.etSearchTrash.text.toString().trim()
            filterTrash(currentQuery)

            if (list.isNullOrEmpty() && trashAdapter.isSelectionMode) {
                exitSelectionMode()
            }
        }
    }

    // ✅ Search Filter Function
    private fun filterTrash(query: String) {
        val filteredList = if (query.isEmpty()) {
            originalTrashList
        } else {
            originalTrashList.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
        trashAdapter.submitList(filteredList)
        binding.emptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvTrash.visibility = if (filteredList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // ==========================================
        // 🌟 Expandable Search Bar Logic
        // ==========================================
        binding.btnOpenSearch.setOnClickListener {
            binding.layoutNormalToolbar.visibility = View.GONE
            binding.layoutSearchToolbar.visibility = View.VISIBLE
            binding.etSearchTrash.requestFocus()
            showKeyboard(binding.etSearchTrash)
        }

        binding.btnCloseSearch.setOnClickListener {
            binding.layoutSearchToolbar.visibility = View.GONE
            binding.layoutNormalToolbar.visibility = View.VISIBLE
            binding.etSearchTrash.setText("")
            hideKeyboard(binding.etSearchTrash)
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchTrash.setText("")
            binding.etSearchTrash.requestFocus()
        }

        binding.etSearchTrash.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                filterTrash(query)
                binding.btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            }
        })

        // ==========================================
        // Selection Mode
        // ==========================================
        binding.btnEnterSelection.setOnClickListener {
            if (trashAdapter.currentList.isNotEmpty()) {
                enterSelectionMode()
            } else {
                Toast.makeText(this, "Trash is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCloseSelection.setOnClickListener { exitSelectionMode() }

        binding.btnSelectAll.setOnClickListener {
            trashAdapter.toggleSelectAll()
            updateSelectionUI()
        }

        binding.btnRestoreSelected.setOnClickListener {
            val selected = trashAdapter.selectedItems.toList()
            if (selected.isNotEmpty()) {
                viewModel.restoreSelected(selected)
                exitSelectionMode()
                Toast.makeText(this, "Restored ${selected.size} items successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please select items to restore", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selected = trashAdapter.selectedItems.toList()
            if (selected.isNotEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Permanently?")
                    .setMessage("Permanently delete ${selected.size} items? This action cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteSelectedPermanently(selected)
                        exitSelectionMode()
                        Toast.makeText(this, "Deleted permanently", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "Please select items to delete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSingleDeleteDialog(item: TrashItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Permanently?")
            .setMessage("Confirm deleting \"${item.name}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePermanently(item)
                Toast.makeText(this, "Deleted permanently", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enterSelectionMode() {
        // Selection Mode ဝင်လျှင် Search Bar ပွင့်နေပါက ပိတ်မည်
        if (binding.layoutSearchToolbar.visibility == View.VISIBLE) {
            binding.btnCloseSearch.performClick()
        }
        trashAdapter.toggleSelectionMode(true)
        binding.selectionBarCard.visibility = View.VISIBLE
        binding.selectionBarCard.animate().translationY(0f).setDuration(200).start()
        binding.btnEnterSelection.visibility = View.GONE
        binding.btnOpenSearch.visibility = View.GONE // Selection Mode တွင် Search မလိုပါ
        updateSelectionUI()
    }

    private fun exitSelectionMode() {
        trashAdapter.toggleSelectionMode(false)
        binding.selectionBarCard.animate().translationY(200f).setDuration(200).withEndAction {
            binding.selectionBarCard.visibility = View.GONE
        }.start()
        binding.btnEnterSelection.visibility = View.VISIBLE
        binding.btnOpenSearch.visibility = View.VISIBLE
    }

    fun updateSelectionUI() {
        val count = trashAdapter.selectedItems.size
        val total = trashAdapter.currentList.size
        binding.tvSelectionCount.text = "$count Selected"

        if (count == total && total > 0) {
            binding.btnSelectAll.setImageResource(R.drawable.ic_deselect_all)
        } else {
            binding.btnSelectAll.setImageResource(R.drawable.ic_select_all)
        }
    }

    // ✅ Keyboard Helpers
    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}