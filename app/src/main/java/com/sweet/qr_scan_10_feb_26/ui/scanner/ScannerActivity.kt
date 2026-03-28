package com.sweet.qr_scan_10_feb_26.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.sweet.qr_scan_10_feb_26.R
import com.sweet.qr_scan_10_feb_26.databinding.ActivityScannerBinding
import com.sweet.qr_scan_10_feb_26.utils.BarcodeAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import android.media.AudioManager
import android.media.ToneGenerator

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private val viewModel: ScannerViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanResultAdapter: ScanResultAdapter

    private var folderId: Long = -1
    private var folderName: String = ""
    private var camera: Camera? = null
    private var isFlashOn = false
    private var isCameraActive = true
    private var imageAnalyzer: ImageAnalysis? = null

    private var lastScannedTime: Long = 0
    private val SCAN_DELAY = 1000L // 1 စက္ကန့် (၁၀၀၀ မီလီစက္ကန့်) ခြားပြီးမှ နောက်တစ်ခါ ဖတ်မယ်

    private val vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }

    // Gallery picker
    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { scanImageFromGallery(it) }
    }

    // Camera permission
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(
                this,
                "Camera permission is required",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderId = intent.getLongExtra("FOLDER_ID", -1)
        folderName = intent.getStringExtra("FOLDER_NAME") ?: "Folder"

        if (folderId == -1L) {
            Toast.makeText(this, "Invalid folder", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvFolderTitle.text = folderName

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupRecyclerView()
        observeScanItems()
        setupClickListeners()
        checkCameraPermission()
    }

    private fun setupRecyclerView() {
        scanResultAdapter = ScanResultAdapter(
            onItemClick = { item ->
                showQRCodeOverlay(item.scanValue)
            },
            onPlusClick = { item ->
                viewModel.incrementQuantity(item.id)
                vibrateDevice()
            },
            onMinusClick = { item ->
                viewModel.decrementQuantity(item)
                vibrateDevice()
            },
            onDeleteClick = { item ->
                showDeleteConfirmation(item)
            }
        )

        binding.rvScanResults.apply {
            layoutManager = LinearLayoutManager(this@ScannerActivity)
            adapter = scanResultAdapter
        }
    }

    private fun observeScanItems() {
        viewModel.getScanItems(folderId).observe(this) { items ->
            scanResultAdapter.submitList(items)

            val distinctScans = items.size
            val totalScans = items.sumOf { it.quantity }

            binding.tvScanCount.text = "$distinctScans Distinct  |  $totalScans Total Scans"

            if (items.isEmpty()) {
                binding.emptyResults.visibility = View.VISIBLE
            } else {
                binding.emptyResults.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnGallery.setOnClickListener {
            // ဖုန်းဗားရှင်းအလိုက် ဘယ် permission တောင်းရမလဲ ဆုံးဖြတ်မယ်
            val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES // Android 13+
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE // Android 12-
            }

            // Permission ရှိ၊ မရှိ စစ်ဆေးမယ်
            if (ContextCompat.checkSelfPermission(this, permissionToRequest) == PackageManager.PERMISSION_GRANTED) {
                galleryPicker.launch("image/*") // ရှိပြီးသားဆိုရင် Gallery ဖွင့်မယ်
            } else {
                galleryPermissionLauncher.launch(permissionToRequest) // မရှိရင် တောင်းမယ်
            }
        }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        binding.btnStopCamera.setOnClickListener {
            toggleCamera()
        }

        binding.btnManualAdd.setOnClickListener {
            showManualAddDialog()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Gallery Permission တောင်းရန် Launcher
    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryPicker.launch("image/*")
        } else {
            Toast.makeText(this, "Permission denied to access gallery", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // ImageAnalysis ကို Performance အကောင်းဆုံးဖြစ်အောင် ပြင်ဆင်ခြင်း
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // နောက်ဆုံး frame ကိုပဲ ယူမယ် (Lag မဖြစ်အောင်)
                .setTargetResolution(android.util.Size(1280, 720)) // Resolution အသင့်အတင့်ပဲ ထားမယ် (မြန်ဆန်စေရန်)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            // ML Kit က mediaImage ကို တိုက်ရိုက်ဖတ်နိုင်ပါတယ် (Bitmap ပြောင်းစရာမလိုပါ)
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            val scanner = BarcodeScanning.getClient()
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    if (barcodes.isNotEmpty()) {
                                        val barcode = barcodes[0]
                                        barcode.rawValue?.let { value ->
                                            // Scan ဖတ်မိရင် UI Thread ပေါ်မှာ လုပ်ဆောင်မယ်
                                            runOnUiThread {
                                                onBarcodeScanned(value, getFormatName(barcode.format))
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    // Scanning error တက်ရင်လည်း ဘာမှမလုပ်ဘဲ ကျော်သွားမယ်
                                }
                                .addOnCompleteListener {
                                    // အရေးကြီးဆုံးအချက် - ImageProxy ကို အမြဲတမ်း ပြန်ပိတ်ပေးရပါမယ်
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                isCameraActive = true
                updateCameraButton()
            } catch (e: Exception) {
                Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private fun getFormatName(format: Int): String {
        return when (format) {
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> "QR Code"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> "EAN-13"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A -> "UPC-A"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 -> "Code 128"
            else -> "Barcode"
        }
    }
    private fun onBarcodeScanned(value: String, format: String) {
        val currentTime = System.currentTimeMillis()

        // နောက်ဆုံးဖတ်ခဲ့တဲ့အချိန်နဲ့ အခုအချိန်ဟာ ၂ စက္ကန့် (SCAN_DELAY) ကျော်မှ အလုပ်လုပ်မယ်
        if (currentTime - lastScannedTime > SCAN_DELAY) {
            lastScannedTime = currentTime // လက်ရှိအချိန်ကို မှတ်ထားမယ်

            if (isCameraActive) {
                viewModel.addScanItem(folderId, value, format)
                vibrateDevice()
                playBeep()

                // Scan ဖတ်မိသွားကြောင်း သိသာအောင် ခဏလေး Toast ပြပေးလို့ရပါတယ် (Optional)
                // Toast.makeText(this, "Scanned: $value", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scanImageFromGallery(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            val scanner = BarcodeScanning.getClient()

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcode = barcodes[0]
                        barcode.rawValue?.let { value ->
                            val format = when (barcode.format) {
                                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> "QR Code"
                                else -> "Barcode"
                            }
                            viewModel.addScanItem(folderId, value, format)
                            Toast.makeText(
                                this,
                                "Scanned from image",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this,
                            "No barcode found in image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "Failed to scan image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error loading image",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun toggleFlash() {
        camera?.let {
            isFlashOn = !isFlashOn
            it.cameraControl.enableTorch(isFlashOn)

            binding.btnFlash.setImageResource(
                if (isFlashOn) R.drawable.ic_flash_off
                else R.drawable.ic_flash_on
            )
        }
    }

    private fun toggleCamera() {
        isCameraActive = !isCameraActive

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraProvider = cameraProviderFuture.get()

        if (isCameraActive) {
            // Camera ပြန်ဖွင့်မယ်
            binding.previewView.visibility = View.VISIBLE
            binding.scannerOverlay.visibility = View.VISIBLE
            startCamera() // Camera ကို Lifecycle နဲ့ ပြန်ချိတ်မယ်
        } else {
            // Camera ကို လုံးဝ ရပ်ပစ်မယ်
            binding.previewView.visibility = View.INVISIBLE
            binding.scannerOverlay.visibility = View.INVISIBLE

            cameraProvider.unbindAll() // ဒါက Hardware ကို လုံးဝ ပိတ်လိုက်တာပါ

            if (isFlashOn) toggleFlash() // Flash လင်းနေရင်လည်း ပိတ်မယ်
        }

        updateCameraButton()
    }

    private fun updateCameraButton() {
        binding.btnStopCamera.setImageResource(
            if (isCameraActive) R.drawable.ic_camera_off
            else R.drawable.ic_qr_scan // Use as camera on icon
        )
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

    private fun vibrateDevice() {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

//    private fun playBeep() {
//        try {
//            val mediaPlayer = MediaPlayer.create(this, R.raw.beep)
//            mediaPlayer?.start()
//            mediaPlayer?.setOnCompletionListener { it.release() }
//        } catch (e: Exception) {
//            // Beep sound not available
//        }
//    }

    private fun showQRCodeOverlay(value: String) {

        // 1. အရင်ဆုံး Camera ကို ရပ်လိုက်မယ်
        pauseScanning()

        // 2. ပြီးမှ Overlay Layout ကို ပြမယ်
        try {
            // Inflate the overlay layout
            val overlayView = layoutInflater.inflate(
                com.sweet.qr_scan_10_feb_26.R.layout.dialog_qr_overlay,
                null
            )

            val qrCard = overlayView.findViewById<com.google.android.material.card.MaterialCardView>(
                com.sweet.qr_scan_10_feb_26.R.id.qrCard
            )
            val ivQrCode = overlayView.findViewById<android.widget.ImageView>(
                com.sweet.qr_scan_10_feb_26.R.id.ivQrCode
            )
            val tvQrValue = overlayView.findViewById<android.widget.TextView>(
                com.sweet.qr_scan_10_feb_26.R.id.tvQrValue
            )

            // Generate QR code
            val qrBitmap = com.sweet.qr_scan_10_feb_26.utils.QRCodeGenerator.generateQRCode(value, 800, 800)

            if (qrBitmap != null) {
                ivQrCode.setImageBitmap(qrBitmap)
                tvQrValue.text = value

                // Add overlay to root layout
                val rootView = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
                rootView.addView(overlayView)

                // Animate in
                overlayView.alpha = 0f
                overlayView.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()

                // Click outside to dismiss
                overlayView.setOnClickListener {
                    dismissQROverlay(overlayView)
                }

                // Prevent click on card from dismissing
                qrCard.setOnClickListener {
                    // Do nothing - prevent propagation
                }
            } else {
                Toast.makeText(
                    this,
                    "Failed to generate QR code",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            resumeScanning() // Error တက်ရင် Camera ပြန်ဖွင့်ပေးရမယ်
            Toast.makeText(
                this,
                "Error displaying QR code",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun pauseScanning() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraProvider = cameraProviderFuture.get()

        // Camera Hardware ကို လုံးဝ ပိတ်ပစ်မယ် (ဘက်ထရီ သက်သာအောင်)
        cameraProvider.unbindAll()
        isCameraActive = false
        updateCameraButton()
    }

    private fun resumeScanning() {
        // User က Overlay ကို ပိတ်လိုက်တဲ့အခါ Camera ပြန်ဖွင့်မယ်
        if (!isCameraActive) {
            startCamera()
        }
    }

    private fun dismissQROverlay(overlayView: android.view.View) {
        try {
            overlayView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    try {
                        val rootView = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
                        rootView.removeView(overlayView)

                        // 3. Overlay ပိတ်သွားပြီဆိုတာနဲ့ Camera ကို အလိုအလျောက် ပြန်ဖွင့်မယ်
                        resumeScanning()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playBeep() {
        try {
            // TONE_PROP_BEEP ဆိုတာ Standard Scan ဖတ်တဲ့ အသံမျိုးပါ
            // 100 ဆိုတာ အသံအတိုးအကျယ် (Volume) ပါ
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 150) // 150 က အသံကြာချိန် (ms)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}