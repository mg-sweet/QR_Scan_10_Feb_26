package com.sweet.qr_scan_10_feb_26.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
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
import com.google.mlkit.vision.common.InputImage
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.databinding.ActivityScannerBinding
import com.sweet.qr_scan_10_feb_26.utils.QRCodeGenerator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode


class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private val viewModel: ScannerViewModel by viewModels()

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    // ScannerActivity.kt ရဲ့ အပေါ်ပိုင်းမှာ
    private var camera: androidx.camera.core.Camera? = null

    private lateinit var adapter: ScanResultAdapter

    private var folderId: Long = -1
    private var isFlashOn = false
    private var isCameraActive = true

    private var lastScanTime = 0L
    private val SCAN_DELAY = 1000L // ၁.၅ စက္ကန့် ခြားမည်

    // ScannerActivity.kt ထဲတွင် ဤသို့ အစားထိုးပါ

    // ScannerActivity.kt ထဲတွင် ဤအတိုင်း အစားထိုးပါ

    private val scanner by lazy {
        // BarcodeScannerOptions ကို တိုက်ရိုက်ခေါ်သုံးခြင်း
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_CODE_128
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    private val vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scanImageFromGallery(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fun setupEdgeToEdge() {
            // 1. Layout ကို Notch အောက်အထိ တိုးဝင်ခွင့်ပေးမယ်
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT

            // 2. Status Bar ပေါ်က icon များကို အမည်းရောင်ပြောင်းမယ်
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        }

// ဤ code အတိုင်း အတိအကျ အစားထိုးပါ
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            // view.paddingLeft/Right/Bottom များကို မပျောက်စေဘဲ Status bar အမြင့်ကိုပဲ top padding အဖြစ်ထည့်သည်
            view.setPadding(
                view.paddingLeft,
                statusBarHeight, // အပို 10dp မလိုတော့ပါ၊ wrap_content က အလိုလို ညှိပေးပါလိမ့်မည်
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        folderId = intent.getLongExtra("FOLDER_ID", -1)
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

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // ✅ Optimization: Resolution ကို 720p (HD) မှာပဲ ကန့်သတ်မယ်။
            // 1080p ထက် အများကြီး ပိုမြန်ပြီး Smooth ဖြစ်စေပါတယ်။
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                processImage(proxy)
            }

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) { e.printStackTrace() }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(proxy: ImageProxy) {
        val mediaImage = proxy.image ?: run {
            proxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            proxy.imageInfo.rotationDegrees
        )

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isEmpty() || !isCameraActive) return@addOnSuccessListener

                val centerX = image.width / 2f
                val centerY = image.height / 2f

                // ✅ Optimization: sqrt() အစား Squared Distance ကို သုံးမယ်။
                // sqrt က CPU အရမ်းစားလို့ Point Scan အတွက် ဒါက ပိုမြန်ပါတယ်။
                val thresholdSquared = 80f * 80f
                var bestCandidate: String? = null
                var minDistanceSq = Float.MAX_VALUE

                for (barcode in barcodes) {
                    val box = barcode.boundingBox ?: continue
                    val bx = box.centerX().toFloat()
                    val by = box.centerY().toFloat()

                    // Squared Distance (dx^2 + dy^2)
                    val dx = bx - centerX
                    val dy = by - centerY
                    val distSq = dx * dx + dy * dy

                    if (distSq < thresholdSquared && distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        bestCandidate = barcode.rawValue
                    }
                }

                bestCandidate?.let {
                    runOnUiThread { onScanned(it) }
                }
            }
            .addOnCompleteListener {
                // အရေးကြီးသည်: Frame တစ်ခုချင်းစီကို အမြန်ဆုံး ပိတ်ပေးရပါမယ်။
                proxy.close()
            }
    }

    private fun showManualAddDialog() {
        val input = TextInputEditText(this).apply {
            hint = "Enter barcode value"
            setPadding(50, 30, 50, 30)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Manual Add")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val value = input.text?.toString()?.trim() ?: ""
                if (value.isNotEmpty()) {
                    viewModel.addScanItem(folderId, value, "Manual")
                    Toast.makeText(this, "Added", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onScanned(value: String) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_DELAY || !isCameraActive) return
        lastScanTime = now

        viewModel.addScanItem(folderId, value, "QR/Barcode")
        vibrate(); beep()
    }

    private fun scanImageFromGallery(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            scanner.process(image).addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    barcodes.first().rawValue?.let { onScanned(it) }
                } else {
                    Toast.makeText(this, "No QR/Barcode found", Toast.LENGTH_SHORT).show()
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

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btnStopCamera.setOnClickListener { toggleCamera() }
        binding.btnGallery.setOnClickListener { pickImageLauncher.launch("image/*") }

        // ✅ Manual Add Listener ထည့်သွင်းခြင်း
        binding.btnManualAdd.setOnClickListener {
            // သင်၏ Manual Dialog logic ကို ဤနေရာတွင် ထည့်ပါ
            showManualAddDialog()
        }
    }

    private fun showDeleteConfirmation(item: com.sweet.qr_scan_10_feb_26.data.entity.ScanItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Scan?")
            .setMessage("Delete \"${item.scanValue}\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupRecycler() {
        adapter = ScanResultAdapter(
            onItemClick = { showQrOverlay(it.scanValue) },
            onPlusClick = { viewModel.incrementQuantity(it.id); vibrate() },
            onMinusClick = { viewModel.decrementQuantity(it); vibrate() },
            onDeleteClick = { item ->
                // ✅ Delete Logic ထည့်သွင်းခြင်း
                showDeleteConfirmation(item)
                vibrate()
            }
        )
        binding.rvScanResults.layoutManager = LinearLayoutManager(this)
        binding.rvScanResults.adapter = adapter
    }

    private fun observeData() {
        viewModel.getScanItems(folderId).observe(this) { list ->
            // submitList ရဲ့ ဒုတိယ parameter မှာ callback ထည့်မယ်
            adapter.submitList(list) {
                // ✅ List Update ဖြစ်ပြီးတာနဲ့ အပေါ်ဆုံး (Position 0) ကို အလိုအလျောက် ဆွဲတင်မယ်
                if (list.isNotEmpty()) {
                    // scrollToPosition(0) က ချက်ချင်း ရောက်သွားစေပြီး
                    // smoothScrollToPosition(0) ကတော့ လျှောခနဲ တက်သွားစေပါတယ်
                    binding.rvScanResults.scrollToPosition(0)
                }
            }

            binding.tvScanCount.text = "${list.size} Items • ${list.sumOf { it.quantity }} Scans"
            binding.emptyResults.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun toggleCamera() {
        isCameraActive = !isCameraActive
        val cameraProvider = ProcessCameraProvider.getInstance(this).get()
        if (isCameraActive) {
            // --- Camera ဖွင့်ချိန် ---
            binding.previewView.visibility = View.VISIBLE
            binding.scannerOverlay.visibility = View.VISIBLE

            // Placeholder ကို Fade out လုပ်ပြီး ဖျောက်မည်
            binding.cameraPlaceholder.animate().alpha(0f).setDuration(200).withEndAction {
                binding.cameraPlaceholder.visibility = View.GONE
            }.start()
            startCamera()
        } else {
            // --- Camera ပိတ်ချိန် ---
            // Placeholder ကို အရင်ပြမည်
            binding.cameraPlaceholder.alpha = 0f
            binding.cameraPlaceholder.visibility = View.VISIBLE
            binding.cameraPlaceholder.animate().alpha(1f).setDuration(300).start()

            // PreviewView နှင့် Overlay ကို ဖျောက်မည်
            binding.previewView.visibility = View.INVISIBLE
            binding.scannerOverlay.visibility = View.INVISIBLE

            cameraProvider.unbindAll() // Hardware ကို လုံးဝ ပိတ်လိုက်ခြင်း
            if (isFlashOn) toggleFlash()
        }
        updateCameraButton()

    }

    private fun toggleFlash() {
        // အပေါ်မှာ type အတိအကျ ကြေညာထားရင် ဒီတိုင်း ရေးလို့ရပါပြီ
        camera?.let {
            isFlashOn = !isFlashOn
            it.cameraControl.enableTorch(isFlashOn)

            binding.btnFlash.setImageResource(
                if (isFlashOn) R.drawable.ic_flash_off
                else R.drawable.ic_flash_on
            )
        }
    }

    private fun updateCameraButton() {
        // 1. ခလုတ်၏ ပုံရိပ် (Icon) ကို ပြောင်းလဲခြင်း
        binding.btnStopCamera.setImageResource(
            if (isCameraActive) R.drawable.ic_camera_off // Camera ဖွင့်ထားရင် "Stop" ပုံစံပြမည်
            else R.drawable.ic_qr_scan // Camera ပိတ်ထားရင် "Start/Scan" ပုံစံပြမည်
        )

        // 2. ✅ UX Pro Tip: ခလုတ်၏ အရောင် (Tint) ကိုပါ ပြောင်းလဲပေးခြင်း
        // Camera ဖွင့်ထားချိန်မှာ "အနီရောင်" (Stop) ပြပြီး၊ ပိတ်ထားချိန်မှာ "အစိမ်းရောင်" (Start) ပြပါမယ်
        val tintColor = if (isCameraActive) {
            android.graphics.Color.parseColor("#EF4444") // Red (Danger/Stop)
        } else {
            android.graphics.Color.parseColor("#10B981") // Green (Success/Start)
        }

        binding.btnStopCamera.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)

        // 3. Option: ခလုတ်ကို နှိပ်လိုက်တဲ့အခါ ခံစားချက်ပိုကောင်းအောင် Animation အနည်းငယ် ထည့်နိုင်ပါတယ်
        binding.btnStopCamera.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                binding.btnStopCamera.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
            .start()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startCamera() }.launch(Manifest.permission.CAMERA)
        }
    }

    private fun beep() {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {}
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(50)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}