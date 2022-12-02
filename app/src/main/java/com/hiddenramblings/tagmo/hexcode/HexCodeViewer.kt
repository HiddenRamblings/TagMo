package com.hiddenramblings.tagmo.hexcode

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.collection.LruCache
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.browser.Preferences
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.nfctech.TagArray
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
        val tagData = intent.getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA)
        val keyManager = KeyManager(this)
        if (keyManager.isKeyMissing) {
            showErrorDialog(R.string.no_decrypt_key)
            return
        }
        val listView = findViewById<RecyclerView>(R.id.gridView)
        var adapter: HexAdapter
        try {
            adapter = HexAdapter(keyManager.decrypt(tagData))
            listView.layoutManager = LinearLayoutManager(this)
            listView.adapter = adapter
        } catch (e: Exception) {
            try {
                adapter = HexAdapter(TagArray.getValidatedData(keyManager, tagData))
                listView.layoutManager = LinearLayoutManager(this)
                listView.adapter = adapter
            } catch (ex: Exception) {
                Debug.Warn(e)
                showErrorDialog(R.string.fail_display)
            }
        }
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.hex_code)
        toolbar.inflateMenu(R.menu.save_menu)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { v: View? -> finish() }
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.mnu_save) {
                try {
                    saveHexViewToFile(prefs, listView, Amiibo.idToHex(Amiibo.dataToId(tagData)))
                } catch (e: IOException) {
                    saveHexViewToFile(
                        prefs, listView, TagArray.bytesToHex(
                            tagData?.copyOfRange(0, 9)
                        )
                    )
                }
                return@setOnMenuItemClickListener true
            }
            false
        }
    }

    private fun saveHexViewToFile(prefs: Preferences, view: RecyclerView, filename: String) {
        val adapter = view.adapter as HexAdapter?
        var viewBitmap: Bitmap? = null
        if (adapter != null) {
            val size = adapter.itemCount
            var height = 0
            val paint = Paint()
            var iHeight = 0
            val cacheSize = (Runtime.getRuntime().maxMemory() / 1024).toInt() / 16
            val bitmaCache = LruCache<String, Bitmap>(cacheSize)
            for (i in 0 until size) {
                val holder = adapter.createViewHolder(
                    view, adapter.getItemViewType(i)
                )
                adapter.onBindViewHolder(holder, i)
                holder.itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(
                        view.width, View.MeasureSpec.EXACTLY
                    ),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                holder.itemView.layout(
                    0, 0,
                    holder.itemView.measuredWidth,
                    holder.itemView.measuredHeight
                )
                @Suppress("DEPRECATION")
                if (Debug.isOlder(Build.VERSION_CODES.P)) {
                    holder.itemView.isDrawingCacheEnabled = true
                    holder.itemView.buildDrawingCache()
                    val drawingCache = holder.itemView.drawingCache
                    if (drawingCache != null)
                        bitmaCache.put(i.toString(), drawingCache)
                }
                height += holder.itemView.measuredHeight
            }
            viewBitmap = Bitmap.createBitmap(
                view.measuredWidth, height, Bitmap.Config.ARGB_8888
            )
            val bigCanvas = Canvas(viewBitmap)
            bigCanvas.drawColor(Color.BLACK)
            for (i in 0 until size) {
                val bitmap = bitmaCache[i.toString()]
                bigCanvas.drawBitmap(bitmap!!, 0f, iHeight.toFloat(), paint)
                iHeight += bitmap.height
                bitmap.recycle()
            }
        }
        if (null == viewBitmap) {
            Toasty(this@HexCodeViewer).Short(getString(R.string.fail_bitmap))
            return
        }
        val dir = File(Storage.getDownloadDir("TagMo"), "HexCode")
        if (!dir.exists() && !dir.mkdirs()) {
            Toasty(this@HexCodeViewer).Short(
                getString(R.string.mkdir_failed, dir.name)
            )
            return
        }
        val file = File(dir, filename + "-" + System.currentTimeMillis() + ".png")
        try {
            FileOutputStream(file).use { fos ->
                viewBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                Toasty(this@HexCodeViewer).Short(
                    getString(
                        R.string.wrote_file, Storage.getRelativePath(file, prefs.preferEmulated())
                    )
                )
            }
        } catch (e: IOException) {
            Debug.Warn(e)
        }
    }

    private fun showErrorDialog(msgRes: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_caps)
            .setMessage(msgRes)
            .setPositiveButton(R.string.close) { dialog: DialogInterface?, which: Int -> finish() }
            .show()
        setResult(RESULT_OK, Intent(NFCIntent.ACTION_FIX_BANK_DATA))
    }
}