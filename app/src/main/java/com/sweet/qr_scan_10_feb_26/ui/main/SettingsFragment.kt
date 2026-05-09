package com.sweet.qr_scan_10_feb_26.ui.main

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sweet.qr_scan_10_feb_26.databinding.FragmentSettingsBinding
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PreferencesManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        prefs = PreferencesManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // ✅ Notch Padding for Settings Toolbar
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, top, 0, 0)
            insets
        }

        loadCurrentSettings()
        setupClickListeners()
    }

    private fun loadCurrentSettings() {
        // 1. Switches Load
        binding.switchBeep.isChecked = prefs.isBeepEnabled
        binding.switchVibrate.isChecked = prefs.isVibrateEnabled
        binding.switchDuplicateWarn.isChecked = prefs.warnDuplicateScan
        binding.switchTimeFormat.isChecked = prefs.use24HourFormat

        // 2. Export Method Text Load
        val exportMethods = arrayOf("Merge Multi-Files into One CSV", "Keep as Separate Files")
        binding.tvExportValue.text = exportMethods[prefs.exportMethod]

        // 3. Auto Trash Text Load
        binding.tvTrashValue.text = when(prefs.autoEmptyTrashDays) {
            7 -> "After 7 Days"
            30 -> "After 30 Days"
            else -> "Never (Manual Empty)"
        }

        // 4. Theme Text Load
        val themes = arrayOf("System Default", "Light Mode", "Dark Mode")
        binding.tvThemeValue.text = themes[prefs.themeMode]
    }

    private fun setupClickListeners() {
        // Switches Save Logic
        binding.switchBeep.setOnCheckedChangeListener { _, isChecked -> prefs.isBeepEnabled = isChecked }
        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked -> prefs.isVibrateEnabled = isChecked }
        binding.switchDuplicateWarn.setOnCheckedChangeListener { _, isChecked -> prefs.warnDuplicateScan = isChecked }
        binding.switchTimeFormat.setOnCheckedChangeListener { _, isChecked -> prefs.use24HourFormat = isChecked }

        // Export Dialog
        binding.btnExportMethod.setOnClickListener {
            val options = arrayOf("Merge into One CSV", "Keep Separate Files")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Default Export Method")
                .setSingleChoiceItems(options, prefs.exportMethod) { dialog, which ->
                    prefs.exportMethod = which
                    loadCurrentSettings()
                    dialog.dismiss()
                }.show()
        }

        // Auto Trash Dialog
        binding.btnAutoEmptyTrash.setOnClickListener {
            val options = arrayOf("Never", "After 7 Days", "After 30 Days")
            val currentChoice = when(prefs.autoEmptyTrashDays) {
                0 -> 0; 7 -> 1; 30 -> 2; else -> 2
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Auto-Empty Trash")
                .setSingleChoiceItems(options, currentChoice) { dialog, which ->
                    prefs.autoEmptyTrashDays = when(which) { 0 -> 0; 1 -> 7; 2 -> 30; else -> 30 }
                    loadCurrentSettings()
                    dialog.dismiss()
                }.show()
        }

        // Theme Dialog
        binding.btnTheme.setOnClickListener {
            val options = arrayOf("System Default", "Light Mode", "Dark Mode")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("App Theme")
                .setSingleChoiceItems(options, prefs.themeMode) { dialog, which ->
                    prefs.themeMode = which
                    loadCurrentSettings()
                    applyTheme(which) // Apply immediately
                    dialog.dismiss()
                }.show()
        }

        // About Links
        binding.btnRateUs.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Play Store...", Toast.LENGTH_SHORT).show()
        }
        binding.btnPrivacyPolicy.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Privacy Policy...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme(themeMode: Int) {
        when (themeMode) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}