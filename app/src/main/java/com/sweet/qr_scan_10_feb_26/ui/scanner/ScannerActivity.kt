package com.sweet.qr_scan_10_feb_26.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.data.entity.ScanItem
import com.sweet.qr_scan_10_feb_26.databinding.ActivityScannerBinding
import com.sweet.qr_scan_10_feb_26.utils.PreferencesManager
import com.sweet.qr_scan_10_feb_26.utils.QRCodeGenerator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private val viewModel: ScannerViewModel by viewModels()

    // ✅ Settings ဖတ်ရန် Variable
    private lateinit var prefs: PreferencesManager

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null

    private lateinit var adapter: ScanResultAdapter
    private var originalScanList: List<ScanItem> = emptyList()

    private var fileId: Long = -1
    private var fileName: String = ""
    private var isFlashOn = false
    private var isCameraActive = true

    private var lastScanTime = 0L
    private val SCAN_DELAY = 1500L

    private val scanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_EAN_13, Barcode.FORMAT_CODE_128)
            .build()
        BarcodeScanning.getClient(options)
    }

    private val vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scanImageFromGallery(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Settings Manager ကို အစပျိုးခြင်း
        prefs = PreferencesManager(this)

        setupEdgeToEdge()
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, statusBarHeight, view.paddingRight, view.paddingBottom)
            insets
        }

        fileId = intent.getLongExtra("FILE_ID", -1)
        fileName = intent.getStringExtra("FILE_NAME") ?: "Scan Session"

        if (fileId == -1L) {
            Toast.makeText(this, "Invalid Session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvFolderTitle.text = fileName
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupRecycler()
        observeData()
        setupClicks()
        checkCameraPermission()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun setupRecycler() {
        adapter = ScanResultAdapter(
            onItemClick = { showQrOverlay(it.scanValue) },
            onPlusClick = { item -> viewModel.incrementItemQuantity(item.id); vibrate() },
            onMinusClick = { item -> viewModel.decrementItemQuantity(item); vibrate() },
            onDeleteClick = { item -> showDeleteConfirmation(item) }
        )
        binding.rvScanResults.layoutManager = LinearLayoutManager(this)
        binding.rvScanResults.adapter = adapter
    }

    private fun observeData() {
        viewModel.getItems(fileId).observe(this) { list ->
            originalScanList = list
            val currentQuery = binding.etSearchScans.text.toString().trim()
            filterScans(currentQuery)
        }
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btnStopCamera.setOnClickListener { toggleCamera() }
        binding.btnGallery.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnManualAdd.setOnClickListener { showManualAddDialog() }

        binding.etSearchScans.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterScans(s.toString().trim())
            }
        })
    }

    private fun filterScans(query: String) {
        val filteredList = if (query.isEmpty()) originalScanList else {
            originalScanList.filter { it.scanValue.contains(query, ignoreCase = true) }
        }
        adapter.submitList(ArrayList(filteredList)) {
            if (filteredList.isNotEmpty() && query.isEmpty()) {
                binding.rvScanResults.scrollToPosition(0)
            }
        }
        val totalScans = filteredList.sumOf { it.quantity }
        binding.tvScanCount.text = "${filteredList.size} Items $totalScans Total"
        binding.emptyResults.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { proxy -> processImage(proxy) }

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                isCameraActive = true
                updateCameraButton()
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(proxy: ImageProxy) {
        val mediaImage = proxy.image ?: run { proxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)

        scanner.process(image).addOnSuccessListener { barcodes ->
            if (barcodes.isEmpty() || !isCameraActive) return@addOnSuccessListener
            val centerX = image.width / 2f
            val centerY = image.height / 2f
            val thresholdSq = 80f * 80f
            var bestCandidate: String? = null
            var minDistanceSq = Float.MAX_VALUE

            for (barcode in barcodes) {
                val box = barcode.boundingBox ?: continue
                val bx = box.centerX().toFloat()
                val by = box.centerY().toFloat()
                val dx = bx - centerX
                val dy = by - centerY
                val dSq = dx * dx + dy * dy

                if (dSq < thresholdSq && dSq < minDistanceSq) {
                    minDistanceSq = dSq
                    bestCandidate = barcode.rawValue
                }
            }
            bestCandidate?.let { runOnUiThread { onScanned(it) } }
        }.addOnCompleteListener { proxy.close() }
    }

    // ==========================================
    // 🌟 1. Settings ချိတ်ဆက်ထားသော Logic များ
    // ==========================================
    private fun onScanned(value: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_DELAY || !isCameraActive) return
        lastScanTime = now

        // Duplicate Warning Setting ဖွင့်ထားလျှင် စစ်ဆေးမည်
        val isDuplicate = originalScanList.any { it.scanValue == value }

        if (isDuplicate && prefs.warnDuplicateScan) {
            // ကင်မရာ ခဏရပ်မည် (Popup မေးနေစဉ် နောက်တစ်ခု ထပ်မဖတ်မိစေရန်)
            isCameraActive = false

            MaterialAlertDialogBuilder(this)
                .setTitle("Duplicate Scan")
                .setMessage("This item is already scanned. Add +1 to quantity?")
                .setPositiveButton("Add +1") { _, _ ->
                    viewModel.addScanItem(fileId, value, "QR/Barcode")
                    vibrate()
                    beep()
                    isCameraActive = true // ကင်မရာ ပြန်ဖွင့်မည်
                }
                .setNegativeButton("Cancel") { _, _ ->
                    isCameraActive = true // မတိုးဘဲ ကင်မရာ ပြန်ဖွင့်မည်
                }
                .setCancelable(false)
                .show()
        } else {
            // Warning ပိတ်ထားလျှင် (သို့) အသစ်ဖြစ်လျှင် ပုံမှန်အတိုင်း အလုပ်လုပ်မည်
            viewModel.addScanItem(fileId, value, "QR/Barcode")
            vibrate()
            beep()
        }
    }

    private fun beep() {
        // ✅ Setting ပိတ်ထားလျှင် အသံမမြည်ပါ
        if (!prefs.isBeepEnabled) return
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (e: Exception) {}
    }

    private fun vibrate() {
        // ✅ Setting ပိတ်ထားလျှင် မတုန်ပါ
        if (!prefs.isVibrateEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else { vibrator.vibrate(50) }
    }

    // ==========================================
    // အခြား Function များ (မူလအတိုင်း)
    // ==========================================
    private fun scanImageFromGallery(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            scanner.process(image).addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    barcodes.first().rawValue?.let { onScanned(it) }
                } else {
                    Toast.makeText(this, "No QR found", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showQrOverlay(value: String) {
        toggleCamera()
        val view = layoutInflater.inflate(R.layout.dialog_qr_overlay, null)
        val iv = view.findViewById<android.widget.ImageView>(R.id.ivQrCode)
        val tv = view.findViewById<android.widget.TextView>(R.id.tvQrValue)

        iv.setImageBitmap(QRCodeGenerator.generateQRCode(value, 800, 800))
        tv.text = value

        val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(view)

        view.setOnClickListener {
            rootView.removeView(view)
            toggleCamera()
        }
    }

    private fun showDeleteConfirmation(item: ScanItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Scan?")
            .setMessage("Confirm deleting this record?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteItem(item) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showManualAddDialog() {
        val input = TextInputEditText(this).apply { hint = "Enter Value" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Manual Entry")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) onScanned(value)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun toggleCamera() {
        isCameraActive = !isCameraActive
        val cameraProvider = ProcessCameraProvider.getInstance(this).get()

        if (isCameraActive) {
            binding.previewView.visibility = View.VISIBLE
            binding.scannerOverlay.visibility = View.VISIBLE
            binding.cameraPlaceholder.animate().alpha(0f).setDuration(200).withEndAction {
                binding.cameraPlaceholder.visibility = View.GONE
            }.start()
            startCamera()
        } else {
            binding.cameraPlaceholder.alpha = 0f
            binding.cameraPlaceholder.visibility = View.VISIBLE
            binding.cameraPlaceholder.animate().alpha(1f).setDuration(300).start()
            binding.previewView.visibility = View.INVISIBLE
            binding.scannerOverlay.visibility = View.INVISIBLE
            cameraProvider.unbindAll()
            if (isFlashOn) toggleFlash()
        }
        updateCameraButton()
    }

    private fun toggleFlash() {
        camera?.let {
            isFlashOn = !isFlashOn
            it.cameraControl.enableTorch(isFlashOn)
            binding.btnFlash.setImageResource(if (isFlashOn) R.drawable.ic_flash_off else R.drawable.ic_flash_on)
        }
    }

    private fun updateCameraButton() {
        binding.btnStopCamera.setImageResource(if (isCameraActive) R.drawable.ic_camera_off else R.drawable.ic_qr_scan)
        val color = if (isCameraActive) "#EF4444" else "#10B981"
        binding.btnStopCamera.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startCamera() }.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}