package com.hiddenramblings.tagmo.hexcode

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.collection.LruCache
import androidx.core.view.drawToBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.TagArray.toDecryptedTag
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import com.hiddenramblings.tagmo.nfctech.TagArray.toHexByteArray
import com.hiddenramblings.tagmo.widget.Toasty
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class HexCodeViewer : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Preferences(applicationContext)
        setContentView(R.layout.activity_hex_viewer)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val keyManager = KeyManager(this)
        if (keyManager.isKeyMissing) {
            showErrorDialog(R.string.no_decrypt_key)
            return
        }
        val tagData = intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
        val amiiboData = try {
            tagData.toDecryptedTag(keyManager)
        } catch (ex: Exception) {
            Debug.warn(ex)
            showErrorDialog(R.string.fail_display)
            return
        }
        val listView = findViewById<RecyclerView>(R.id.gridView).apply {
            layoutManager = LinearLayoutManager(this@HexCodeViewer)
            adapter = HexAdapter(amiiboData).apply {
                recycledViewPool.setMaxRecycledViews(0, itemCount * 18)
            }
        }
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.hex_code)
        toolbar.inflateMenu(R.menu.hex_menu)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.mnu_save) {
                saveHexViewToData(keyManager, listView)
                return@setOnMenuItemClickListener true
            }
            if (item.itemId == R.id.mnu_export) {
                try {
                    saveHexViewToFile(prefs, listView, Amiibo.idToHex(Amiibo.dataToId(tagData)))
                } catch (e: IOException) {
                    tagData?.let {
                        saveHexViewToFile(
                            prefs, listView, tagData.copyOfRange(0, 9).toHex()
                        )
                    }
                }
                return@setOnMenuItemClickListener true
            }
            false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun saveHexViewToData(keyManager: KeyManager, view: RecyclerView) {
        val hex = StringBuilder()
        (view.adapter as HexAdapter).let { adapter ->
            val size = adapter.itemCount
            for (i in 1 until size) {
                val holder = adapter.createViewHolder(
                    view, adapter.getItemViewType(i)
                )
                adapter.onBindViewHolder(holder, i)
                holder.textView.forEach { view ->
                    view?.let {
                        if (it.text.length == 2) {
                            hex.append(it.text)
                            Debug.warn(javaClass, it.text.toString())
                        }
                    }
                }
            }
        }
        if (hex.isNotBlank()) {
            val data = hex.toString()
            val hexData = keyManager.encrypt(
                data.substring(0, data.length - 24).toHexByteArray()
            )
            setResult(
                RESULT_OK, Intent(NFCIntent.ACTION_UPDATE_TAG)
                    .putExtra(NFCIntent.EXTRA_TAG_DATA, hexData)
            )
        }
        finish()
    }

    private fun saveHexViewToFile(prefs: Preferences, view: RecyclerView, filename: String?) {
        if (filename.isNullOrEmpty()) {
            Toasty(this@HexCodeViewer).Short(getString(R.string.fail_bitmap))
            return
        }
        var viewBitmap: Bitmap?
        (view.adapter as HexAdapter).let { adapter ->
            val size = adapter.itemCount
            var height = 0
            val paint = Paint()
            var iHeight = 0
            val cacheSize = (Runtime.getRuntime().maxMemory() / 1024).toInt() / 16
            val bitmapCache = LruCache<String, Bitmap>(cacheSize)
            for (i in 0 until size) {
                val holder = adapter.createViewHolder(
                    view, adapter.getItemViewType(i)
                )
                adapter.onBindViewHolder(holder, i)
                holder.itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                holder.itemView.layout(
                    0, 0,
                    holder.itemView.measuredWidth,
                    holder.itemView.measuredHeight
                )
                if (Version.isPie) {
                    bitmapCache.put(i.toString(), holder.itemView.drawToBitmap())
                } else @Suppress("deprecation") {
                    holder.itemView.isDrawingCacheEnabled = true
                    holder.itemView.buildDrawingCache()
                    val drawingCache = holder.itemView.drawingCache
                    if (drawingCache != null) bitmapCache.put(i.toString(), drawingCache)
                }
                height += holder.itemView.measuredHeight
            }
            viewBitmap = Bitmap.createBitmap(
                view.measuredWidth, height, Bitmap.Config.ARGB_8888
            )
            val bigCanvas = viewBitmap?.let { Canvas(it) }
            bigCanvas?.let { canvas ->
                canvas.drawColor(Color.BLACK)
                for (i in 0 until size) {
                    bitmapCache[i.toString()]?.let {
                        canvas.drawBitmap(it, 0f, iHeight.toFloat(), paint)
                        iHeight += it.height
                        it.recycle()
                    }
                }
            }
        }
        val dir = File(TagMo.downloadDir, "HexCode")
        if (!dir.exists() && !dir.mkdirs()) {
            Toasty(this@HexCodeViewer).Short(getString(R.string.mkdir_failed, dir.name))
            return
        }
        val file = File(dir, filename + "-" + System.currentTimeMillis() + ".png")
        try {
            FileOutputStream(file).use { fos ->
                viewBitmap?.compress(Bitmap.CompressFormat.PNG, 100, fos)
                Toasty(this@HexCodeViewer).Short(getString(
                    R.string.wrote_file, Storage.getRelativePath(file, prefs.preferEmulated())
                ))
            }
        } catch (e: IOException) {
            Debug.warn(e)
        } finally {
            viewBitmap?.recycle()
        }
    }

    private fun showErrorDialog(msgRes: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_caps)
            .setMessage(msgRes)
            .setPositiveButton(R.string.close) { _: DialogInterface?, _: Int -> finish() }
            .show()
        setResult(RESULT_OK, Intent(NFCIntent.ACTION_FIX_BANK_DATA))
    }
}