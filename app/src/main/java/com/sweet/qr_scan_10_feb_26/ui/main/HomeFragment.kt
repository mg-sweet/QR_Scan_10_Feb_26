package com.sweet.qr_scan_10_feb_26.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sweet.qr_scan_10_feb_26.data.entity.FolderWithStats
import com.sweet.qr_scan_10_feb_26.databinding.FragmentHomeBinding
import com.sweet.qr_scan_10_feb_26.utils.CSVExporter
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import com.google.android.material.textfield.TextInputEditText
import com.sweet.qr_scan_10_feb_26.data.entity.FileWithStats
import com.sweet.qr_scan_10_feb_26.data.entity.ScanFolder
import com.sweet.qr_scan_10_feb_26.utils.DownloadHelper
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import java.io.File
import java.util.ArrayList

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private var originalFolderList: List<FolderWithStats> = emptyList()

    lateinit var folderAdapter: FolderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
        // Notch Padding for App Name & Share Button

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Notch Padding Logic: Title နှင့် Share Button ကို အောက်သို့ တွန်းချခြင်း
        ViewCompat.setOnApplyWindowInsetsListener(binding.tvAppName) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val params = v.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.topMargin = top + 20
            v.layoutParams = params
            insets
        }

//        // Share Button အတွက်ပါ လုပ်ပေးရန် (Layout ပေါ်မူတည်၍)
//        ViewCompat.setOnApplyWindowInsetsListener(binding.btnShareAll) { v, insets ->
//            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
//            val params = v.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
//            params.topMargin = top + 20
//            v.layoutParams = params
//            insets
//        }

        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        folderAdapter = FolderAdapter(
            onFolderClick = { stats ->
                val intent = Intent(requireContext(), FolderDetailActivity::class.java).apply {
                    putExtra("FOLDER_ID", stats.id)
                    putExtra("FOLDER_NAME", stats.name)
                }
                startActivity(intent)
            },
            onLongClick = { stats -> enterSelectionMode(stats.id) },
            onMenuClick = { stats, anchor -> showFolderPopupMenu(stats, anchor) },
            onSelectionChanged = { count ->
                (activity as? MainActivity)?.binding?.tvSelectionCount?.text = "$count Selected"
            }
        )
        binding.rvFolders.adapter = folderAdapter
        binding.rvFolders.layoutManager = LinearLayoutManager(requireContext())

        // ✅ ထပ်ဖြည့်ရမည့် အပိုင်း (Item အသစ်ဝင်လာတိုင်း အပေါ်ဆုံးသို့ Auto-scroll လုပ်မည်)
        folderAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                // အပေါ်ဆုံး (position 0) မှာ Data အသစ်ဝင်လာရင် အပေါ်ကို ဆွဲတင်ပေးမည်
                if (positionStart == 0) {
                    binding.rvFolders.smoothScrollToPosition(0)
                }
            }
        })
    }

    private fun observeViewModel() {
        viewModel.allFolders.observe(viewLifecycleOwner) { list ->

            originalFolderList = list // မူရင်း Data ကို အမြဲ Update လုပ်မည်

            // လက်ရှိ Search Box ထဲက စာသားကို ယူမည်
            val currentQuery = binding.etSearchProjects.text.toString()
            filterFolders(currentQuery) // Filter လုပ်ပြီးမှ Adapter သို့ထည့်မည်

            //folderAdapter.submitList(ArrayList(list))
            binding.tvTotalFolders.text = list.size.toString()
            binding.tvAllScansCount.text = list.sumOf { it.totalCount }.toString()
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.trashCount.observe(viewLifecycleOwner) { count ->
            binding.tvTrashBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            binding.tvTrashBadge.text = count.toString()
        }
    }

    private fun setupClickListeners() {
        binding.btnOpenTrash.setOnClickListener {
            startActivity(Intent(requireContext(), RecentlyDeletedActivity::class.java))
        }

        // MainActivity ရှိ FAB နှင့် ချိတ်ဆက်ခြင်း
        (activity as? MainActivity)?.binding?.fabCreateFolder?.setOnClickListener {
            showCreateFolderDialog()
        }

        binding.btnShareAll.setOnClickListener {
            if (folderAdapter.currentList.isNotEmpty()) {

                // ၁။ Selection Mode ကို ဖွင့်မည်
                folderAdapter.toggleSelectionMode(true)

                // ၂။ ယခင်ရွေးထားတာတွေ ရှိခဲ့ရင် အကုန်ရှင်းထုတ်မည် (0 Selected ဖြစ်စေရန်)
                folderAdapter.selectedIds.clear()
                folderAdapter.notifyDataSetChanged()

                // ၃။ အောက်ခြေက Bar ကို ဖော်မည်၊ စာသားကို 0 Selected ဟု ပြမည်
                (activity as? MainActivity)?.setSelectionBarVisibility(true)
                (activity as? MainActivity)?.binding?.tvSelectionCount?.text = "0 Selected"

            } else {
                Toast.makeText(requireContext(), "No projects available", Toast.LENGTH_SHORT).show()
            }
        }
        
        // ✅ စာရိုက်လိုက်တိုင်း ချက်ချင်း ရှာဖွေပေးမည့် Logic
        binding.etSearchProjects.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterFolders(s.toString())
            }
        })
    }

    // ✅ Search Filter Function
    private fun filterFolders(query: String) {
        val filteredList = if (query.isBlank()) {
            originalFolderList // စာဘာမှမရိုက်ထားရင် အကုန်ပြန်ပြမည်
        } else {
            // စာရိုက်ထားရင် အမည် (Name) တွင် ပါ/မပါ စစ်ထုတ်မည် (အကြီးအသေး မရွေးပါ)
            originalFolderList.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }

        folderAdapter.submitList(ArrayList(filteredList))
        binding.emptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }
    fun selectAllItems() {
        folderAdapter.toggleSelectAll() // ဆောက်ထားတဲ့ Toggle function ကို ခေါ်သုံးမယ်

        val selectedCount = folderAdapter.selectedIds.size
        (activity as? MainActivity)?.binding?.tvSelectionCount?.text = "$selectedCount Selected"

        // အကယ်၍ ရွေးထားတာ တစ်ခုမှ မရှိတော့ဘူးဆိုရင် Selection Mode ထဲကပါ ထွက်ခိုင်းလိုက်လို့ ရပါတယ် (Optional)
        if (selectedCount == 0) {
            // exitSelectionMode() // ဒါကတော့ ဆရာ့စိတ်ကြိုက်ပါ၊ mode ထဲမှာပဲ ဆက်ထားချင်လည်း ရပါတယ်
        }
    }
    fun deleteSelectedItems() {
        val selectedIds = folderAdapter.selectedIds.toList()
        if (selectedIds.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Move to Trash?")
                .setMessage("Move ${selectedIds.size} projects to Trash?")
                .setPositiveButton("OK") { _, _ ->
                    viewModel.softDeleteMultipleFolders(selectedIds)
                    exitSelectionMode()
                    Toast.makeText(requireContext(), "Moved to trash", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    fun shareSelectedItems() {
        val selectedIds = folderAdapter.selectedIds.toList()
        if (selectedIds.isEmpty()) return

        // ✅ Setting ကို လှမ်းဖတ်မည်
        val prefs = PreferencesManager(requireContext())
        val isMergeMode = (prefs.exportMethod == 0)

        lifecycleScope.launch {
            try {
                // FolderWithStats ကို ScanFolder အဖြစ် ပြောင်းလဲခြင်း
                val selectedFolders = folderAdapter.currentList
                    .filter { selectedIds.contains(it.id) }
                    .map { ScanFolder(id = it.id, name = it.name) }

                // ✅ Setting ပေါ်မူတည်၍ Merge သို့မဟုတ် Separate ခေါ်မည်
                val csvFiles = if (isMergeMode) {
                    CSVExporter.exportMergedFoldersToOneFile(requireContext(), selectedFolders)
                } else {
                    CSVExporter.exportFolders(requireContext(), selectedFolders)
                }

                if (csvFiles.isNotEmpty()) {
                    shareCsvFiles(csvFiles)
                    exitSelectionMode()
                } else {
                    Toast.makeText(requireContext(), "No data to export", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun downloadSelectedItems() {
        val selectedIds = folderAdapter.selectedIds.toList()
        if (selectedIds.isEmpty()) return

        // ✅ Setting ကို လှမ်းဖတ်မည်
        val prefs = PreferencesManager(requireContext())
        val isMergeMode = (prefs.exportMethod == 0)

        lifecycleScope.launch {
            try {
                val selectedFolders = folderAdapter.currentList
                    .filter { selectedIds.contains(it.id) }
                    .map { ScanFolder(id = it.id, name = it.name) }

                // ✅ Setting ပေါ်မူတည်၍ Merge သို့မဟုတ် Separate ခေါ်မည်
                val csvFiles = if (isMergeMode) {
                    CSVExporter.exportMergedFoldersToOneFile(requireContext(), selectedFolders)
                } else {
                    CSVExporter.exportFolders(requireContext(), selectedFolders)
                }

                if (csvFiles.isNotEmpty()) {
                    csvFiles.forEach { file ->
                        DownloadHelper.saveFileToDownloads(requireContext(), file)
                    }
                    Toast.makeText(requireContext(), "Saved ${csvFiles.size} files to Downloads", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ ၃။ Share Intent ခေါ်သည့် Function
    private fun shareCsvFiles(files: List<File>) {
        if (files.isEmpty()) return
        val uris = files.map { FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", it) }
        val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = "text/csv"
            if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            else putExtra(Intent.EXTRA_STREAM, uris[0])
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Projects"))
    }

//    private fun showCreateFolderDialog() {
//        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply { hint = "Enter Name(blank will create auto Folder)" }
//        MaterialAlertDialogBuilder(requireContext()).setTitle("New Project").setView(input)
//            .setPositiveButton("Create") { _, _ ->
//                viewModel.createFolder(input.text.toString().trim()) { }
//            }.show()
//    }
private fun showCreateFolderDialog() {
    // ၁။ Layout အသစ်တည်ဆောက်ခြင်း
    val context = requireContext()
    val layout = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(64, 32, 64, 0) // ဘေးဘောင်နှင့် အပေါ်အောက် အကွာအဝေး ချိန်ညှိခြင်း
    }

    // ၂။ Input Field တည်ဆောက်ခြင်း
    val input = com.google.android.material.textfield.TextInputEditText(context).apply {
        hint = "Project Name"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        maxLines = 1
    }

    // Input ကို Material TextInputLayout ထဲထည့်မှ ပိုလှမည်
    val textInputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
        addView(input)
        boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_NONE
    }

    // ၃။ Helper Text (Hint စာသားလှလှလေး) တည်ဆောက်ခြင်း
    val helperText = android.widget.TextView(context).apply {
        text = "Leave blank to auto-generate a name."
        textSize = 12f
        setTextColor(android.graphics.Color.parseColor("#94A3B8")) // ခဲဖျော့ရောင်
        setPadding(12, 8, 0, 0)
    }

    // Layout ထဲသို့ ထည့်သွင်းခြင်း
    layout.addView(textInputLayout)
    layout.addView(helperText)

    // ၄။ Dialog ခေါ်ခြင်း
    MaterialAlertDialogBuilder(context)
        .setTitle("New Project")
        .setView(layout)
        .setPositiveButton("Create") { _, _ ->
            val name = input.text.toString().trim()
            viewModel.createFolder(name) { }
        }
        .setNegativeButton("Cancel", null)
        .show()
        .apply {
            // Keyboard အလိုလိုပွင့်လာစေရန်
            input.requestFocus()
            window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
}

    fun isSelectionModeActive() = folderAdapter.isSelectionMode

    private fun enterSelectionMode(firstId: Long) {
        folderAdapter.toggleSelectionMode(true)
        folderAdapter.selectedIds.add(firstId)
        folderAdapter.notifyDataSetChanged()
        (activity as? MainActivity)?.setSelectionBarVisibility(true)

        // ✅ Search Box ကို ပိတ်ပြီး အရောင်မှိန်မည်
        binding.searchCard.isEnabled = false
        binding.searchCard.alpha = 0.5f
        binding.etSearchProjects.isEnabled = false
    }

    fun exitSelectionMode() {
        folderAdapter.toggleSelectionMode(false)
        (activity as? MainActivity)?.setSelectionBarVisibility(false)

        // ✅ Search Box ကို ပုံမှန်အတိုင်း ပြန်ဖွင့်မည်
        binding.searchCard.isEnabled = true
        binding.searchCard.alpha = 1.0f
        binding.etSearchProjects.isEnabled = true
    }

    private fun showFolderPopupMenu(stats: FolderWithStats, anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add("Select"); popup.menu.add("Rename"); popup.menu.add("Delete")
        popup.setOnMenuItemClickListener {
            when(it.title) {
                "Select" -> enterSelectionMode(stats.id)
                "Delete" -> showSoftDeleteConfirmation(stats)
                "Rename" -> showRenameFolderDialog(stats)
            }
            true
        }
        popup.show()
    }

    private fun showSoftDeleteConfirmation(stats: FolderWithStats) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Move to Trash?")
            .setMessage("Are you sure you want to move \"${stats.name}\" to the trash?")
            .setPositiveButton("Trash") { _, _ ->
                viewModel.softDeleteFolder(stats.id)
                Toast.makeText(requireContext(), "Moved to trash", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameFolderDialog(stats: FolderWithStats) {
        // လက်ရှိ Folder နာမည်ကို Dialog ထဲမှာ အသင့်ပေါ်နေအောင် setText နဲ့ ထည့်ပေးထားပါတယ်
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            setText(stats.name)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Project")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newName = input.text.toString().trim()
                // နာမည်အလွတ်မဟုတ်မှ၊ ထို့ပြင် မူရင်းနာမည်နဲ့ မတူမှသာ Database ကို Update လုပ်ပါမည်
                if (newName.isNotEmpty() && newName != stats.name) {
                    viewModel.renameFolder(stats.id, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Home မျက်နှာပြင် ပြန်ပွင့်လာတိုင်း Adapter ကို အသစ်ပြန်ဆွဲခိုင်းမည် (Settings အပြောင်းအလဲများ ချက်ချင်းသိစေရန်)
        if (::folderAdapter.isInitialized) {
            folderAdapter.notifyDataSetChanged()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Tab ပြောင်းပြီး Home ကို ပြန်ရောက်လာချိန်တွင်လည်း Refresh လုပ်မည်
        if (!hidden && ::folderAdapter.isInitialized) {
            folderAdapter.notifyDataSetChanged()
        }
    }

}