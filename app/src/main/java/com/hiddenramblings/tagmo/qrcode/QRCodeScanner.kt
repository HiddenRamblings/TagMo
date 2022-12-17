/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.qrcode

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.TagArray
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs


class QRCodeScanner : AppCompatActivity() {

    private var barcodeScanner: BarcodeScanner? = null
    private lateinit var qrTypeSpinner: Spinner
    private var captureUri: Uri? = null

    private lateinit var barcodePreview: AppCompatImageView
    private var cameraPreview: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null

    private enum class TYPE {
        UNKNOWN, WIFI, URL, PRODUCT, TEXT, CALENDAR,
        CONTACT, LICENSE, EMAIL, GEO, ISBN, PHONE, SMS
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code)

        val actionBar = supportActionBar
        if (null != actionBar) {
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }

        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC
            ).build()
        )

        barcodePreview = findViewById(R.id.barcodePreview)
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP))
            cameraPreview = findViewById(R.id.cameraPreview)
        qrTypeSpinner = findViewById(R.id.txtTypeValue)
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.qr_code_type, R.layout.spinner_text
        )
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        qrTypeSpinner.adapter = adapter
    }

    private fun processBarcode(barcode: Barcode) {
        runOnUiThread {
            val selection = when (barcode.valueType) {
                Barcode.TYPE_UNKNOWN -> { TYPE.UNKNOWN }
                Barcode.TYPE_WIFI -> { TYPE.WIFI }
                Barcode.TYPE_URL -> { TYPE.URL }
                Barcode.TYPE_PRODUCT -> { TYPE.PRODUCT }
                Barcode.TYPE_TEXT -> { TYPE.TEXT }
                Barcode.TYPE_CALENDAR_EVENT -> { TYPE.CALENDAR }
                Barcode.TYPE_CONTACT_INFO -> { TYPE.CONTACT }
                Barcode.TYPE_DRIVER_LICENSE -> { TYPE.LICENSE }
                Barcode.TYPE_EMAIL -> { TYPE.EMAIL }
                Barcode.TYPE_GEO -> { TYPE.GEO }
                Barcode.TYPE_ISBN -> { TYPE.ISBN }
                Barcode.TYPE_PHONE -> { TYPE.PHONE }
                Barcode.TYPE_SMS -> { TYPE.SMS }
                else -> { TYPE.UNKNOWN }
            }
            qrTypeSpinner.setSelection(selection.ordinal)
            findViewById<TextView>(R.id.txtRawValue).setText(
                barcode.rawValue, TextView.BufferType.EDITABLE
            )
            findViewById<TextView>(R.id.txtRawBytes).setText(
                TagArray.bytesToHex(barcode.rawBytes), TextView.BufferType.EDITABLE
            )
            qrTypeSpinner.requestFocus()
        }
        if (Debug.isNewer(Build.VERSION_CODES.KITKAT)) {
            try {
                decryptMii(barcode.rawBytes)
            } catch (ex: Exception) {
                Debug.Warn(ex)
            }
        }
    }

    private fun scanBarcodes(image: InputImage) {
        barcodeScanner?.process(image)!!.addOnSuccessListener { barcodes ->
            barcodes.forEach { processBarcode(it) }
        }.addOnFailureListener { Debug.Error(it) }
    }

    private val onPickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && (null != captureUri || null != result.data)) {
            val photoUri: Uri? = when {
                null != captureUri -> {
                    captureUri
                }
                null != result.data!!.clipData && result.data?.clipData!!.itemCount > 0 -> {
                    result.data!!.clipData!!.getItemAt(0)!!.uri
                }
                null != result.data!!.data -> {
                    result.data!!.data!!
                }
                else -> {
                    null
                }
            }
            captureUri = null
            if (null != photoUri) {
                Executors.newSingleThreadExecutor().execute {
                    var rotation = 0
                    val bitmap: Bitmap? = if (Debug.isNewer(Build.VERSION_CODES.P)) {
                        val source: ImageDecoder.Source = ImageDecoder.createSource(
                            this.contentResolver, photoUri
                        )
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
                    }
                    val cursor: Cursor? = contentResolver.query(
                        photoUri, arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
                        null, null, null
                    )
                    if (cursor?.count == 1) {
                        cursor.moveToFirst()
                        rotation = cursor.getInt(0)
                    }
                    if (null != bitmap) {
                        runOnUiThread {
                            barcodePreview.setImageBitmap(bitmap)
                        }
                        scanBarcodes(InputImage.fromBitmap(bitmap, rotation))
                    }
                    cursor?.close()
                }
            }
        }
    }

    private val screenAspectRatio: Int
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        get() {
            val metrics = DisplayMetrics()
            val width: Int
            val height: Int
            val mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            if (Debug.isNewer(Build.VERSION_CODES.S)) {
                val bounds: Rect = mWindowManager.currentWindowMetrics.bounds
                width = bounds.width()
                height = bounds.height()
            } else {
                if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1))
                    @Suppress("DEPRECATION")
                    mWindowManager.defaultDisplay.getRealMetrics(metrics)
                else
                    @Suppress("DEPRECATION")
                    mWindowManager.defaultDisplay.getMetrics(metrics)
                width = metrics.widthPixels
                height = metrics.heightPixels
            }
            val previewRatio = width.coerceAtLeast(height).toDouble() / width.coerceAtMost(height)
            if (abs(previewRatio - (4.0 / 3.0)) <= abs(previewRatio - (16.0 / 9.0))) {
                return AspectRatio.RATIO_4_3
            }
            return AspectRatio.RATIO_16_9
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private var onRequestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            ViewModelProvider(
                this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[CameraXViewModel::class.java].processCameraProvider.observe(this) {
                cameraProvider = it
                runOnUiThread {
                    barcodePreview.setImageResource(0)
                    cameraPreview?.isVisible = true
                }
                bindCameraUseCases()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun bindCameraUseCases() {
        bindPreviewUseCase()
        bindAnalyseUseCase()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun bindPreviewUseCase() {
        if (null == cameraProvider) return
        if (null != previewUseCase) cameraProvider!!.unbind(previewUseCase)

        previewUseCase = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(cameraPreview!!.display.rotation)
            .build()
        previewUseCase!!.setSurfaceProvider(cameraPreview!!.surfaceProvider)

        try {
            cameraProvider!!.bindToLifecycle(
                /* lifecycleOwner = */this, cameraSelector!!, previewUseCase
            )
        } catch (illegalStateException: IllegalStateException) {
            Debug.Error(illegalStateException)
        } catch (illegalArgumentException: IllegalArgumentException) {
            Debug.Error(illegalArgumentException)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun bindAnalyseUseCase() {
        if (null == cameraProvider) return
        if (null != analysisUseCase) cameraProvider!!.unbind(analysisUseCase)

        analysisUseCase = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(cameraPreview!!.display.rotation)
            .build()

        analysisUseCase?.setAnalyzer(Executors.newSingleThreadExecutor()) {
            processImageProxy(barcodeScanner, it)
        }

        try {
            cameraProvider!!.bindToLifecycle(
                /* lifecycleOwner = */this, cameraSelector!!, analysisUseCase
            )
        } catch (illegalStateException: IllegalStateException) {
            Debug.Error(illegalStateException)
        } catch (illegalArgumentException: IllegalArgumentException) {
            Debug.Error(illegalArgumentException)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    private fun processImageProxy(barcodeScanner: BarcodeScanner?, imageProxy: ImageProxy) {
        val image = InputImage.fromMediaImage(
            imageProxy.image!!, imageProxy.imageInfo.rotationDegrees
        )
        barcodeScanner?.process(image)!!.addOnSuccessListener { barcodes ->
            barcodes.forEach { processBarcode(it) }
        }.addOnFailureListener { Debug.Error(it) }.addOnCompleteListener { imageProxy.close() }
    }

    @SuppressLint("RestrictedApi")
    private fun setOptionalIconsVisible(menu: Menu) {
        if (menu is MenuBuilder) menu.setOptionalIconsVisible(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.qr_code_menu, menu)
        setOptionalIconsVisible(menu)
        menu.findItem(R.id.mnu_camera).isVisible = !BuildConfig.WEAR_OS
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
            R.id.mnu_camera -> {
                if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
                    onRequestCamera.launch(Manifest.permission.CAMERA)
                } else {
                    val values = ContentValues()
                    values.put(MediaStore.Images.Media.TITLE, "TagMo QR")
                    values.put(MediaStore.Images.Media.DESCRIPTION, "TagMo QR Code Capture")
                    captureUri = contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    )
                    onPickImage.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(
                        MediaStore.EXTRA_OUTPUT, captureUri
                    ))
                }
            }
            R.id.mnu_gallery -> {
                if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP) && null != cameraProvider) {
                    if (null != previewUseCase) cameraProvider!!.unbind(previewUseCase)
                    if (null != analysisUseCase) cameraProvider!!.unbind(analysisUseCase)
                    cameraPreview?.isVisible = false
                }
                onPickImage.launch(
                    Intent.createChooser(Intent(if (Debug.isNewer(Build.VERSION_CODES.KITKAT))
                        Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_GET_CONTENT
                    ).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        .putExtra("android.content.extra.SHOW_ADVANCED", true)
                        .putExtra("android.content.extra.FANCY", true), title))
            }
            R.id.mnu_generate -> {
                if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP) && null != cameraProvider) {
                    if (null != previewUseCase) cameraProvider!!.unbind(previewUseCase)
                    if (null != analysisUseCase) cameraProvider!!.unbind(analysisUseCase)
                    cameraPreview?.isVisible = false
                }
                val typeArray = resources.getStringArray(R.array.qr_code_type)
                val selection = when (qrTypeSpinner.selectedItem) {
                    typeArray[TYPE.UNKNOWN.ordinal] -> { QRGContents.Type.UNKNOWN }
                    typeArray[TYPE.WIFI.ordinal] -> { QRGContents.Type.WIFI }
                    typeArray[TYPE.URL.ordinal] -> { QRGContents.Type.URL }
                    typeArray[TYPE.PRODUCT.ordinal] -> { QRGContents.Type.PRODUCT }
                    typeArray[TYPE.TEXT.ordinal] -> { QRGContents.Type.TEXT }
                    typeArray[TYPE.CALENDAR.ordinal] -> { QRGContents.Type.CALENDAR }
                    typeArray[TYPE.CONTACT.ordinal] -> { QRGContents.Type.CONTACT }
                    typeArray[TYPE.LICENSE.ordinal] -> { QRGContents.Type.LICENSE }
                    typeArray[TYPE.EMAIL.ordinal] -> { QRGContents.Type.EMAIL }
                    typeArray[TYPE.GEO.ordinal] -> { QRGContents.Type.LOCATION }
                    typeArray[TYPE.ISBN.ordinal] -> { QRGContents.Type.ISBN }
                    typeArray[TYPE.PHONE.ordinal] -> { QRGContents.Type.PHONE }
                    typeArray[TYPE.SMS.ordinal] -> { QRGContents.Type.SMS }
                    else -> { QRGContents.Type.UNKNOWN }
                }
                val data = findViewById<EditText>(R.id.txtRawBytes).text
                val text = if (null != data) {
                    TagArray.hexToString(data.toString())
                } else {
                    findViewById<EditText>(R.id.txtRawValue).text.toString()
                }

                val metrics = DisplayMetrics()
                val mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                val dimension = if (Debug.isNewer(Build.VERSION_CODES.S)) {
                    val bounds: Rect = mWindowManager.currentWindowMetrics.bounds
                    val params = if (bounds.width() < bounds.height())
                        bounds.width()
                    else bounds.height()
                    ((params * 3 / 4) / (resources.configuration.densityDpi / 160)) + 0.5
                } else {
                    if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1))
                        @Suppress("DEPRECATION")
                        mWindowManager.defaultDisplay.getRealMetrics(metrics)
                    else
                        @Suppress("DEPRECATION")
                        mWindowManager.defaultDisplay.getMetrics(metrics)
                    val params = if (metrics.widthPixels < metrics.heightPixels)
                        metrics.widthPixels
                    else metrics.heightPixels
                    ((params * 3 / 4) / metrics.density) + 0.5
                }.toInt()

                val qrgEncoder = QRGEncoder(text, null, selection, dimension)
                try {
                    val bitmap = qrgEncoder.bitmap
                    runOnUiThread { barcodePreview.setImageBitmap(bitmap) }
                    scanBarcodes(InputImage.fromBitmap(bitmap, 0))
                } catch (ex: Exception) {
                    Debug.Warn(ex)
                    runOnUiThread {
                        qrTypeSpinner.setSelection(12)
                        findViewById<EditText>(R.id.txtRawValue).setText(
                            "", TextView.BufferType.EDITABLE
                        )
                        findViewById<TextView>(R.id.txtRawBytes).setText(
                            "", TextView.BufferType.EDITABLE
                        )
                        qrTypeSpinner.requestFocus()
                    }
                }
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    @Throws(Exception::class)
    private fun decryptMii(qrData: ByteArray?) {
        if (null == qrData) return
        val key = byteArrayOf(
            0x59, 0xFC.toByte(), 0x81.toByte(), 0x7E, 0x64, 0x46,
            0xEA.toByte(), 0x61, 0x90.toByte(), 0x34, 0x7B, 0x20,
            0xE9.toByte(), 0xBD.toByte(), 0xCE.toByte(), 0x52
        )
        val nonce = qrData.copyOfRange(0, 8)

        val cipher = Cipher.getInstance("AES/CCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(nonce.plus(ByteArray(4)))
        )
        val content = cipher.doFinal(qrData.copyOfRange(8, 0x58))
        Debug.Info(this.javaClass, TagArray.bytesToHex(
            content.copyOfRange(0, 12).plus(nonce).plus(content.copyOfRange(12, content.size))
        ))
    }
}