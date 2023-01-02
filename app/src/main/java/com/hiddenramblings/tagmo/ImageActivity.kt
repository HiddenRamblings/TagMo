package com.hiddenramblings.tagmo

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.browser.Preferences
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.widget.Toasty
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.util.concurrent.Executors

class ImageActivity : AppCompatActivity() {
    private lateinit var imageView: AppCompatImageView
    private lateinit var bottomSheet: View
    private lateinit var toggle: AppCompatImageView
    private lateinit var txtTagId: TextView
    private lateinit var txtName: TextView
    private lateinit var txtGameSeries: TextView
    // private TextView txtCharacter;
    private lateinit var txtAmiiboType: TextView
    private lateinit var txtAmiiboSeries: TextView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View?>

    private var amiiboId: Long = 0
    private var amiiboManager: AmiiboManager? = null
    private var amiibo: Amiibo? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Preferences(applicationContext)
        setContentView(R.layout.activity_image)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        imageView = findViewById(R.id.imageAmiibo)
        bottomSheet = findViewById(R.id.bottom_sheet)
        toggle = findViewById(R.id.toggle)
        txtTagId = findViewById(R.id.txtTagId)
        txtName = findViewById(R.id.txtName)
        txtGameSeries = findViewById(R.id.txtGameSeries)
        // txtCharacter = findViewById(R.id.txtCharacter);
        txtAmiiboType = findViewById(R.id.txtAmiiboType)
        txtAmiiboSeries = findViewById(R.id.txtAmiiboSeries)
        amiiboId = intent.getLongExtra(NFCIntent.EXTRA_AMIIBO_ID, -1)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.imageview_amiibo)
        toolbar.inflateMenu(R.menu.save_menu)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.mnu_save) {
                onSaveClicked(prefs, amiiboId)
                return@setOnMenuItemClickListener true
            }
            false
        }
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.addBottomSheetCallback(
            object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        toggle.setImageResource(R.drawable.ic_expand_less_white_24dp)
                    } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        toggle.setImageResource(R.drawable.ic_expand_more_white_24dp)
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        findViewById<View>(R.id.group0).addOnLayoutChangeListener {
                view: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
            val height = view.height + bottomSheet.paddingTop
            bottomSheetBehavior.peekHeight = height
            imageView.setPadding(
                imageView.paddingLeft, imageView.paddingTop,
                imageView.paddingRight, imageView.paddingTop + height
            )
        }
        Executors.newSingleThreadExecutor().execute {
            var amiiboManager: AmiiboManager? = null
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(applicationContext)
            } catch (e: IOException) {
                Debug.warn(e)
            } catch (e: JSONException) {
                Debug.warn(e)
            } catch (e: ParseException) {
                Debug.warn(e)
            }
            if (Thread.currentThread().isInterrupted) return@execute
            this.amiiboManager = amiiboManager
            runOnUiThread { updateView(amiiboId) }
        }
        GlideApp.with(imageView).load(getImageUrl(amiiboId)).into(imageView)
        findViewById<View>(R.id.toggle).setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
    }

    private fun updateView(amiiboId: Long) {
        var tagInfo: String? = null
        var amiiboHexId: String? = ""
        var amiiboName: String? = ""
        var amiiboSeries: String? = ""
        var amiiboType: String? = ""
        var gameSeries: String? = ""
        // String character = "";
        if (amiiboId == -1L) {
            tagInfo = getString(R.string.read_error)
        } else if (amiiboId == 0L) {
            tagInfo = getString(R.string.blank_tag)
        } else {
            if (null != amiiboManager) {
                amiibo = amiiboManager!!.amiibos[amiiboId]
                    ?: Amiibo(amiiboManager, amiiboId, null, null)
            }
            if (null != amiibo) {
                amiiboHexId = Amiibo.idToHex(amiibo!!.id)
                if (null != amiibo!!.name) amiiboName = amiibo!!.name
                if (null != amiibo!!.amiiboSeries) amiiboSeries = amiibo!!.amiiboSeries!!.name
                if (null != amiibo!!.amiiboType) amiiboType = amiibo!!.amiiboType!!.name
                if (null != amiibo!!.gameSeries) gameSeries = amiibo!!.gameSeries!!.name
            } else {
                tagInfo = "ID: " + Amiibo.idToHex(amiiboId)
            }
        }
        val hasTagInfo = null != tagInfo
        if (hasTagInfo) {
            setAmiiboInfoText(txtName, tagInfo, false)
        } else {
            setAmiiboInfoText(txtName, amiiboName, false)
        }
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo)
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo)
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo)
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo)
        // setAmiiboInfoText(txtCharacter, character, hasTagInfo);
    }

    private fun setAmiiboInfoText(textView: TextView, text: CharSequence?, hasTagInfo: Boolean) {
        if (hasTagInfo) {
            textView.visibility = View.GONE
        } else {
            textView.visibility = View.VISIBLE
            if (!text.isNullOrEmpty()) {
                textView.text = text
                textView.isEnabled = true
            } else {
                textView.setText(R.string.unknown)
                textView.isEnabled = false
            }
        }
    }

    private fun getImageUrl(amiiboId: Long): String {
        return Amiibo.getImageUrl(amiiboId)
    }

    private fun onSaveClicked(prefs: Preferences, amiiboId: Long) {
        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
        val dialog = AlertDialog.Builder(this)
        view.findViewById<TextView>(R.id.save_item_label).setText(R.string.save_image)
        val input = view.findViewById<EditText>(R.id.save_item_entry)
        if (null != amiibo) {
            input.setText(amiibo!!.name)
        } else {
            input.setText(Amiibo.idToHex(amiiboId))
        }
        val imageDialog: Dialog = dialog.setView(view).create()
        imageDialog.setCancelable(false)
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            val saveImageTarget: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                    saveImageToFile(prefs, resource, input.text.toString())
                }

                override fun onLoadCleared(placeholder: Drawable?) { }
            }
            GlideApp.with(this@ImageActivity).asBitmap()
                .load(getImageUrl(amiiboId))
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(saveImageTarget)
            imageDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel)
            .setOnClickListener { imageDialog.dismiss() }
        imageDialog.show()
    }

    private fun saveImageToFile(prefs: Preferences, resource: Bitmap, filename: String) {
        val dir = File(Storage.getDownloadDir("TagMo"), "Images")
        if (!dir.exists() && !dir.mkdirs()) {
            Toasty(this@ImageActivity).Short(
                getString(R.string.mkdir_failed, dir.name)
            )
            return
        }
        val file = File(dir, "$filename.png")
        try {
            FileOutputStream(file).use { fos ->
                resource.compress(Bitmap.CompressFormat.PNG, 100, fos)
                Toasty(this@ImageActivity).Short(
                    getString(
                        R.string.wrote_file, Storage.getRelativePath(file, prefs.preferEmulated())
                    )
                )
            }
        } catch (e: IOException) {
            Debug.warn(e)
        }
    }
}