/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.qrcode

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
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
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidmads.library.qrgenearator.QRGEncoder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.browser.Preferences
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.widget.Toasty
import org.json.JSONException
import java.io.IOException
import java.text.ParseException
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs


class QRCodeScanner : AppCompatActivity() {

    private lateinit var prefs: Preferences

    private lateinit var qrTypeSpinner: Spinner
    private lateinit var txtRawValue: EditText
    private lateinit var txtRawBytes: EditText
    private lateinit var txtMiiLabel: TextView
    private lateinit var txtMiiValue: TextView

    private var amiiboManager: AmiiboManager? = null

    private lateinit var amiiboPreview: AppCompatImageView
    private lateinit var barcodePreview: AppCompatImageView
    private var barcodeScanner: BarcodeScanner? = null
    private var captureUri: Uri? = null
    private var cameraPreview: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null

    private val metrics = DisplayMetrics()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(applicationContext)
        setContentView(R.layout.activity_qr_code)

        val actionBar = supportActionBar
        if (null != actionBar) {
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }

        setResult(Activity.RESULT_CANCELED)

        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC
            ).build()
        )

        amiiboPreview = findViewById(R.id.amiiboPreview)
        barcodePreview = findViewById(R.id.barcodePreview)
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP))
            cameraPreview = findViewById(R.id.cameraPreview)
        qrTypeSpinner = findViewById(R.id.txtTypeValue)
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.qr_code_type, R.layout.spinner_text
        )
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        qrTypeSpinner.adapter = adapter
        txtRawValue = findViewById(R.id.txtRawValue)
        txtRawBytes = findViewById(R.id.txtRawBytes)
        txtMiiLabel = findViewById(R.id.txtMiiLabel)
        txtMiiValue = findViewById(R.id.txtMiiValue)

        Executors.newSingleThreadExecutor().execute {
            var amiiboManager: AmiiboManager? = null
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(applicationContext)
            } catch (e: IOException) {
                Debug.Warn(e)
            } catch (e: JSONException) {
                Debug.Warn(e)
            } catch (e: ParseException) {
                Debug.Warn(e)
            }
            if (Thread.currentThread().isInterrupted) return@execute
            this.amiiboManager = amiiboManager
            if (intent.hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
                val data = intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
                try {
                    val bitmap = encodeQR(TagArray.bytesToString(data), Barcode.TYPE_TEXT)
                    if (null != bitmap) {
                        runOnUiThread { barcodePreview.setImageBitmap(bitmap) }
                        scanBarcodes(InputImage.fromBitmap(bitmap, 0))
                    }
                } catch (ex: Exception) {
                    Debug.Warn(ex)
                }
            }
        }
    }

    private val isDocumentStorage: Boolean
    get() = if (
        Debug.isNewer(Build.VERSION_CODES.LOLLIPOP) && null != prefs.browserRootDocument()
    ) {
        try {
            DocumentFile.fromTreeUri(this, Uri.parse(prefs.browserRootDocument()))
            true
        } catch (iae: IllegalArgumentException) {
            false
        }
    } else false

    @Throws(Exception::class)
    private fun decodeAmiibo(qrData: ByteArray?) {
        if (null == qrData) return
        if (null != amiiboManager) {
            val amiibo = amiiboManager!!.amiibos[Amiibo.dataToId(qrData)]
            if (null != amiibo) { runOnUiThread {
                txtMiiLabel.text = getText(R.string.qr_amiibo)
                txtMiiValue.text = amiibo.name
                GlideApp.with(amiiboPreview).load(Amiibo.getImageUrl(amiibo.id)).into(amiiboPreview)
                amiiboPreview.setOnClickListener {
                    val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
                    val dialog = AlertDialog.Builder(this)
                    val input = view.findViewById<EditText>(R.id.save_item_entry)
                    input.setText(TagArray.decipherFilename(amiibo, qrData, true))
                    val backupDialog: Dialog = dialog.setView(view).create()
                    view.findViewById<View>(R.id.button_save).setOnClickListener {
                        try {
                            val fileName: String = input.text.toString()
                            if (isDocumentStorage) {
                                val rootDocument = DocumentFile.fromTreeUri(
                                    this, Uri.parse(prefs.browserRootDocument())
                                ) ?: throw NullPointerException()
                                TagArray.writeBytesToDocument(
                                    this, rootDocument, fileName, qrData
                                )
                            } else {
                                TagArray.writeBytesToFile(Storage.getDownloadDir(
                                    "TagMo", "Backups"
                                ), fileName, qrData)
                            }
                            Toasty(this@QRCodeScanner).Long(
                                getString(R.string.wrote_file, fileName)
                            )
                            setResult(Activity.RESULT_OK)
                        } catch (e: Exception) {
                            e.message?.let { Toasty(this).Short(it) }
                        }
                        backupDialog.dismiss()
                    }
                    view.findViewById<View>(R.id.button_cancel).setOnClickListener {
                        backupDialog.dismiss()
                    }
                    backupDialog.show()
                }
            }}
        }
    }

    private val secretKeySpec = SecretKeySpec(byteArrayOf(
        0x59, 0xFC.toByte(), 0x81.toByte(), 0x7E, 0x64, 0x46,
        0xEA.toByte(), 0x61, 0x90.toByte(), 0x34, 0x7B, 0x20,
        0xE9.toByte(), 0xBD.toByte(), 0xCE.toByte(), 0x52
    ), "AES")

    @Throws(Exception::class)
    private fun decryptMii(qrData: ByteArray?) {
        if (null == qrData) return
        val nonce = qrData.copyOfRange(0, 8)
        val empty = byteArrayOf(0, 0, 0, 0)
        val cipher = Cipher.getInstance("AES/CCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, secretKeySpec,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                GCMParameterSpec(nonce.size + empty.size, nonce.plus(empty))
            else IvParameterSpec(nonce.plus(empty))
        )
        val content = cipher.doFinal(qrData, 0, 0x58)
        runOnUiThread {
            txtMiiValue.text = TagArray.bytesToHex(
                content.copyOfRange(0, 12).plus(nonce).plus(content.copyOfRange(12, content.size))
            )
        }
    }

    private fun clearPreviews(barcode : Boolean) {
        amiiboPreview.setOnClickListener(null)
        GlideApp.with(amiiboPreview).clear(amiiboPreview)
        if (barcode) barcodePreview.setImageResource(0)
    }

    private fun processBarcode(barcode: Barcode) {
        runOnUiThread {
            qrTypeSpinner.setSelection(barcode.valueType)
            txtRawValue.setText(barcode.rawValue, TextView.BufferType.EDITABLE)
            txtRawBytes.setText(
                TagArray.bytesToHex(barcode.rawBytes), TextView.BufferType.EDITABLE
            )
            clearPreviews(false)
            qrTypeSpinner.requestFocus()
        }
        try {
            decodeAmiibo(barcode.rawBytes)
        } catch (ex: Exception) {
            Debug.Warn(ex)
        }
        if (txtMiiValue.text.isNullOrEmpty()) {
            txtMiiLabel.text = getText(R.string.qr_mii)
            try {
                decryptMii(barcode.rawBytes)
            } catch (ex: Exception) {
                Debug.Warn(ex)
                runOnUiThread {

                    txtMiiValue.text = ex.localizedMessage
                }
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
                null != captureUri -> { captureUri }
                null != result.data!!.clipData && result.data?.clipData!!.itemCount > 0 -> {
                    result.data!!.clipData!!.getItemAt(0)!!.uri
                }
                null != result.data!!.data -> { result.data!!.data!! }
                else -> { null }
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
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP) get() {
        val width: Int
        val height: Int
        if (Debug.isNewer(Build.VERSION_CODES.S)) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            if (Debug.isNewer(Build.VERSION_CODES.R))
                @Suppress("DEPRECATION")
                display?.getRealMetrics(metrics)
                    ?: windowManager.defaultDisplay.getRealMetrics(metrics)
            else if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1))
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
            else
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getMetrics(metrics)
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
    private var onRequestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            ViewModelProvider(
                this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[CameraXViewModel::class.java].processCameraProvider.observe(this) {
                cameraProvider = it
                runOnUiThread {
                    clearPreviews(true)
                    cameraPreview?.isVisible = true
                }
                bindPreviewUseCase()
                bindAnalyseUseCase()
            }
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

    @Throws(Exception::class)
    fun encodeQR(text: String, type: Int) : Bitmap? {
        val dimension = if (Debug.isNewer(Build.VERSION_CODES.S)) {
            val bounds: Rect = windowManager.currentWindowMetrics.bounds
            val params = if (bounds.width() < bounds.height())
                bounds.width()
            else bounds.height()
            ((params * 3 / 4) / (resources.configuration.densityDpi / 160)) + 0.5
        } else {
            if (Debug.isNewer(Build.VERSION_CODES.R))
                @Suppress("DEPRECATION")
                display?.getRealMetrics(metrics)
                    ?: windowManager.defaultDisplay.getRealMetrics(metrics)
            else if (Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR1))
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
            else
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getMetrics(metrics)
            val params = if (metrics.widthPixels < metrics.heightPixels)
                metrics.widthPixels
            else metrics.heightPixels
            ((params * 3 / 4) / metrics.density) + 0.5
        }.toInt()

        val qrgEncoder = QRGEncoder(text, null, type, dimension)
        return qrgEncoder.bitmap
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
                txtMiiValue.text = ""
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
                txtMiiValue.text = ""
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
                txtMiiValue.text = ""
                if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP) && null != cameraProvider) {
                    if (null != previewUseCase) cameraProvider!!.unbind(previewUseCase)
                    if (null != analysisUseCase) cameraProvider!!.unbind(analysisUseCase)
                    cameraPreview?.isVisible = false
                }
                val text = if (!txtRawBytes.text.isNullOrEmpty())
                    TagArray.hexToString(txtRawBytes.text.toString())
                else txtRawValue.text.toString()

                try {
                    val bitmap = encodeQR(text, qrTypeSpinner.selectedItemPosition)
                    if (null != bitmap) {
                        runOnUiThread { barcodePreview.setImageBitmap(bitmap) }
                        scanBarcodes(InputImage.fromBitmap(bitmap, 0))
                    }
                } catch (ex: Exception) {
                    Debug.Warn(ex)
                    runOnUiThread {
                        qrTypeSpinner.setSelection(0)
                        txtRawValue.setText("", TextView.BufferType.EDITABLE)
                        txtRawBytes.setText("", TextView.BufferType.EDITABLE)
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
}