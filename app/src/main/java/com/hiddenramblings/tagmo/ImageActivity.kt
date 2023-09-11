package com.hiddenramblings.tagmo

import android.annotation.SuppressLint
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
import androidx.core.view.isGone
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.widget.Toasty
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException

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
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
            addBottomSheetCallback(
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
        }
        findViewById<View>(R.id.group0).addOnLayoutChangeListener {
                view: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
            val height = view.height + bottomSheet.paddingTop
            bottomSheetBehavior.peekHeight = height
            imageView.setPadding(
                imageView.paddingLeft, imageView.paddingTop,
                imageView.paddingRight, imageView.paddingTop + height
            )
        }

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
        this@ImageActivity.amiiboManager = amiiboManager
        updateView(amiiboId)
        Glide.with(imageView).load(Amiibo.getImageUrl(amiiboId)).into(imageView)
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
        when (amiiboId) {
            -1L -> { tagInfo = getString(R.string.read_error) }
            0L -> { tagInfo = getString(R.string.blank_tag) }
            else -> {
                amiiboManager?.let {
                    amiibo = it.amiibos[amiiboId] ?: Amiibo(it, amiiboId, null, null)
                }
                amiibo?.let {
                    amiiboHexId = Amiibo.idToHex(it.id)
                    it.name?.let { name -> amiiboName = name }
                    it.amiiboSeries?.let { series -> amiiboSeries = series.name }
                    it.amiiboType?.let { type -> amiiboType = type.name }
                    it.gameSeries?.let { series -> gameSeries = series.name }
                } ?: amiiboId.let {
                    tagInfo = "ID: " + Amiibo.idToHex(it)
                }
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
        textView.isGone = hasTagInfo
        if (!hasTagInfo) {
            if (!text.isNullOrEmpty()) {
                textView.text = text
                textView.isEnabled = true
            } else {
                textView.setText(R.string.unknown)
                textView.isEnabled = false
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun onSaveClicked(prefs: Preferences, amiiboId: Long) {
        layoutInflater.inflate(R.layout.dialog_save_item, null).run {
            findViewById<TextView>(R.id.save_item_label).setText(R.string.save_image)
            val input = findViewById<EditText>(R.id.save_item_entry)
            input.setText(amiibo?.name ?: Amiibo.idToHex(amiiboId))
            AlertDialog.Builder(this@ImageActivity).setView(this).create().also { dialog ->
                dialog.setCancelable(false)
                findViewById<View>(R.id.button_save).setOnClickListener {
                    val saveImageTarget: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                            saveImageToFile(prefs, resource, input.text.toString())
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {}
                    }
                    Glide.with(this@ImageActivity).asBitmap()
                        .load(Amiibo.getImageUrl(amiiboId))
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(saveImageTarget)
                    dialog.dismiss()
                }
                findViewById<View>(R.id.button_cancel).setOnClickListener { dialog.dismiss() }
            }
        }.show()
    }

    private fun saveImageToFile(prefs: Preferences, resource: Bitmap, filename: String) {
        val dir = File(TagMo.downloadDir, "Images")
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
                Toasty(this@ImageActivity).Short(getString(
                    R.string.wrote_file, Storage.getRelativePath(file, prefs.preferEmulated())
                ))
            }
        } catch (e: IOException) {
            Debug.warn(e)
        }
    }
}