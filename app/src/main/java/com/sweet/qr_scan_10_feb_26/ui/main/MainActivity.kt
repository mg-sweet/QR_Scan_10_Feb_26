package com.sweet.qr_scan_10_feb_26.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.os.postDelayed
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.databinding.ActivityMainBinding
//import java.util.logging.Handler
import android.os.Handler
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    private var doubleBackToExitPressedOnce = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ==========================================
        // 🌟 1. Theme (Dark Mode) အသက်သွင်းခြင်း
        // ==========================================
        val prefs = PreferencesManager(this)
        when (prefs.themeMode) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) // Light Mode
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // Dark Mode
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) // System Default
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🌟 Keyboard အတက်အကျကို ထိန်းချုပ်သော Logic
        // ==========================================
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            // Keyboard ပေါ်နေသလား (Visible ဖြစ်လား) စစ်ဆေးခြင်း
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (isKeyboardVisible) {
                // ၁။ Keyboard တက်လာလျှင် Bottom Nav ကို ချက်ချင်းဖျောက်မည်
                binding.bottomNavCoordinator.visibility = View.GONE
            } else {
                // ၂။ Keyboard ကျသွားလျှင် Bottom Nav ပြန်ဖော်မည်
                // (ဒါပေမဲ့ Selection Mode ဝင်နေလျှင်တော့ ဆက်ဖျောက်ထားမည်)
                if (binding.selectionBarCard.visibility == View.GONE) {
                    binding.bottomNavCoordinator.visibility = View.VISIBLE
                }
            }
            insets
        }

        // ✅ အစပျိုးလျှင် HomeFragment ကိုပြမည်
        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), "HOME")
        }

        setupNavigation()
        setupBackHandler()
    }

    private fun setupNavigation() {


        binding.btnNavHome.setOnClickListener {
            loadFragment(HomeFragment(), "HOME")
            updateNavUI(true)


        }

        binding.btnNavSettings.setOnClickListener {
            loadFragment(SettingsFragment(), "SETTINGS")
            updateNavUI(false)


        }

//        binding.btnCloseSelection.setOnClickListener {
//            val fragment = supportFragmentManager.findFragmentByTag("HOME") as? HomeFragment
//            fragment?.exitSelectionMode()
//        }

        // ✅ Selection Bar ခလုတ်များနှင့် HomeFragment ကို ချိတ်ဆက်ခြင်း
        binding.btnCloseSelection.setOnClickListener {
            getHomeFragment()?.exitSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener {
            val homeFragment = getHomeFragment()
            if (homeFragment != null) {
                homeFragment.selectAllItems()

                val selectedCount = homeFragment.folderAdapter.selectedIds.size
                val totalCount = homeFragment.folderAdapter.currentList.size

                // အကုန် Select ဖြစ်နေရင် "Deselect" Icon ပြောင်းမယ်၊ မဟုတ်ရင် "Select All" Icon ပြန်ထားမယ်
                if (selectedCount == totalCount && totalCount > 0) {
                    binding.btnSelectAll.setImageResource(R.drawable.ic_deselect_all)
                } else {
                    binding.btnSelectAll.setImageResource(R.drawable.ic_select_all)
                }
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            getHomeFragment()?.deleteSelectedItems()
        }

        binding.btnShareSelected.setOnClickListener {
            getHomeFragment()?.shareSelectedItems()
        }

        binding.btnDownloadSelected.setOnClickListener {
            getHomeFragment()?.downloadSelectedItems()
        }
    }

    // ✅ HomeFragment ကို အလွယ်တကူ လှမ်းယူနိုင်ရန် Helper Function
    private fun getHomeFragment(): HomeFragment? {
        val fragment = supportFragmentManager.findFragmentByTag("HOME") as? HomeFragment
        return if (fragment != null && fragment.isVisible) fragment else null
    }

//    private fun showCreateFolderDialog() {
//        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply { hint = "Project Name" }
//        MaterialAlertDialogBuilder(requireContext()).setTitle("New Project").setView(input)
//            .setPositiveButton("Create") { _, _ ->
//                viewModel.createFolder(input.text.toString().trim()) { }
//            }.show()
//    }
//    private fun loadFragment(fragment: Fragment, tag: String) {
//        val current = supportFragmentManager.findFragmentByTag(tag)
//        if (current != null && current.isVisible) return
//
//        supportFragmentManager.beginTransaction()
//            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
//            .replace(R.id.nav_host_fragment, fragment, tag)
//            .commit()
//    }

    private fun loadFragment(fragment: Fragment, tag: String) {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        val currentFragment = fragmentManager.fragments.find { it.isVisible }
        val targetFragment = fragmentManager.findFragmentByTag(tag)

        if (currentFragment != null) {
            transaction.hide(currentFragment)
        }

        if (targetFragment != null) {
            transaction.show(targetFragment)
        } else {
            transaction.add(R.id.nav_host_fragment, fragment, tag)
        }

        transaction.commit()
    }

    private fun updateNavUI(isHome: Boolean) {
        val activeColor = Color.parseColor("#4F46E5") // Indigo
        val inactiveColor = Color.parseColor("#94A3B8") // Slate
        val disabledBgColor = Color.parseColor("#6A62FB") // Light Blue for FAB

        // 1. Bottom Nav Icons အရောင်ပြောင်းခြင်း
        binding.btnNavHome.setColorFilter(if (isHome) activeColor else inactiveColor)
        binding.btnNavSettings.setColorFilter(if (isHome) inactiveColor else activeColor)

        // 2. FAB ကို မှိန်ခြင်း (Dimming) နှင့် ဖွင့်/ပိတ် လုပ်ခြင်း
        if (isHome) {
            binding.fabCreateFolder.isEnabled = true
            binding.fabCreateFolder.backgroundTintList = ColorStateList.valueOf(activeColor)
            binding.fabCreateFolder.imageTintList = ColorStateList.valueOf(Color.WHITE)
            binding.fabCreateFolder.compatElevation = 12f // Active ဖြစ်ရင် Shadow ပြန်ပြမယ်
        } else {
            binding.fabCreateFolder.isEnabled = false
            binding.fabCreateFolder.backgroundTintList = ColorStateList.valueOf(disabledBgColor)
            binding.fabCreateFolder.imageTintList = ColorStateList.valueOf(inactiveColor)
            binding.fabCreateFolder.compatElevation = 0f // Inactive ဖြစ်ရင် Shadow ဖျောက်ပြီး Flat ပုံစံလုပ်မယ်
        }

        // ✅ 3. Status Bar (Notch Bar) Icon အရောင်ကို ချိန်ညှိခြင်း (The Magic Fix!)
        val windowController = WindowInsetsControllerCompat(window, window.decorView)
        if (isHome) {
            // Home တွင် နောက်ခံရင့်သဖြင့် Status Bar စာသားများကို 'အဖြူရောင်' ပြမည်
            windowController.isAppearanceLightStatusBars = false
        } else {
            // Settings တွင် နောက်ခံအဖြူဖြစ်သဖြင့် Status Bar စာသားများကို 'အမည်း/အညိုရင့်' ပြမည်
            windowController.isAppearanceLightStatusBars = true
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val homeFragment = supportFragmentManager.findFragmentByTag("HOME") as? HomeFragment

                // ၁။ Selection Mode ဝင်နေလျှင် Selection Mode ထဲမှ အရင်ထွက်မည်
                if (homeFragment != null && homeFragment.isVisible && homeFragment.isSelectionModeActive()) {
                    homeFragment.exitSelectionMode()
                    return
                }

                // ၂။ အကယ်၍ Settings Fragment ကိုရောက်နေပြီး Back နှိပ်လျှင် Home သို့ ပြန်သွားချင်ပါက (Optional UX)
                val settingsFragment = supportFragmentManager.findFragmentByTag("SETTINGS")
                if (settingsFragment != null && settingsFragment.isVisible) {
                    binding.btnNavHome.performClick()
                    return
                }

                // ၃။ ✅ Double Click to Exit Logic
                if (doubleBackToExitPressedOnce) {
                    finish() // ဒုတိယအကြိမ် နှိပ်လျှင် App ထဲမှ ထွက်မည်
                    return
                }

                // ပထမတစ်ကြိမ် နှိပ်လျှင် Toast ပြမည်
                doubleBackToExitPressedOnce = true
                Toast.makeText(this@MainActivity, "Press BACK again to exit", Toast.LENGTH_SHORT).show()

                // စက္ကန့် (၂) စက္ကန့်အတွင်း နောက်တစ်ခါ မနှိပ်လျှင် မူလအခြေအနေသို့ ပြန်ထားမည်
                Handler(Looper.getMainLooper()).postDelayed({
                    doubleBackToExitPressedOnce = false
                }, 2000)
            }
        })
    }

    fun setSelectionBarVisibility(visible: Boolean) {
        binding.selectionBarCard.visibility = if (visible) View.VISIBLE else View.GONE
        binding.bottomNavCoordinator.visibility = if (visible) View.GONE else View.VISIBLE
    }
}