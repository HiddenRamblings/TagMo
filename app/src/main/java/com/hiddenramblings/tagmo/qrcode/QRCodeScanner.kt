/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.qrcode

import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
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
import androidx.appcompat.widget.AppCompatImageView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.TagArray
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class QRCodeScanner : AppCompatActivity() {

    private lateinit var qrTypeSpinner: Spinner

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

        qrTypeSpinner = findViewById(R.id.txtTypeValue)
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.qr_code_type, R.layout.spinner_text
        )
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        qrTypeSpinner.adapter = adapter
    }

    private fun scanBarcodes(image: InputImage) {
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC
            ).build()
        )

        scanner.process(image).addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val bounds = barcode.boundingBox
                    val corners = barcode.cornerPoints

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
                        if (Debug.isNewer(Build.VERSION_CODES.KITKAT)) {
                            try {
                                decryptMii(barcode.rawBytes)
                            } catch (ex: Exception) {
                                Debug.Warn(ex)
                            }
                        }
                        qrTypeSpinner.requestFocus()
                    }

                }
            }
            .addOnFailureListener { Debug.Warn(it) }
    }

    private val onPickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && null != result.data) {
            var photoUri: Uri? = null
            if (null != result.data!!.clipData && result.data?.clipData!!.itemCount > 0) {
                photoUri = result.data!!.clipData!!.getItemAt(0)!!.uri
            } else if (null != result.data!!.data) {
                photoUri = result.data!!.data!!
            }
            if (null != photoUri) {
                Executors.newSingleThreadExecutor().execute {
                    var rotation = -1
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
                            findViewById<AppCompatImageView>(R.id.imageQR).setImageBitmap(bitmap)
                        }
                        scanBarcodes(InputImage.fromBitmap(bitmap, rotation))
                    }
                    cursor?.close()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.qr_code_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
            R.id.mnu_scanner -> {
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

                val qrgEncoder = QRGEncoder(text, null, selection, dimension)
                try {
                    val bitmap = qrgEncoder.bitmap
                    runOnUiThread {
                        val image = findViewById<AppCompatImageView>(R.id.imageQR)
                        image.setImageBitmap(bitmap)
                        image.requestFocus()
                    }
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

    private fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) {
            pos -> ints[pos].toByte()
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    @Throws(Exception::class)
    private fun decryptMii(qrData: ByteArray?) {
        if (null == qrData) return
        val key = byteArrayOfInts(
            0x59, 0xFC, 0x81, 0x7E, 0x64, 0x46, 0xEA, 0x61,
            0x90, 0x34, 0x7B, 0x20, 0xE9, 0xBD, 0xCE, 0x52
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