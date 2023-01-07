package com.hiddenramblings.tagmo.browser.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.browser.adapter.FoldersAdapter.FolderViewHolder
import com.hiddenramblings.tagmo.eightbit.os.Storage
import java.io.File

class FoldersAdapter(var settings: BrowserSettings?) : RecyclerView.Adapter<FolderViewHolder>(),
    BrowserSettingsListener {
    var mPrefs = Preferences(TagMo.appContext)
    var data: ArrayList<File?>? = null
    private var rootFolder: File? = null
    private var showUpFolder = false
    private var firstRun = true
    @SuppressLint("NotifyDataSetChanged")
    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) {
        if (null == newBrowserSettings || null == oldBrowserSettings) return
        if (firstRun || !BrowserSettings.equals(
                newBrowserSettings.browserRootFolder,
                oldBrowserSettings.browserRootFolder
            )
        ) {
            rootFolder = newBrowserSettings.browserRootFolder
            showUpFolder = showParentFolder()
            notifyDataSetChanged()
        }
        if (firstRun || !BrowserSettings.equals(
                newBrowserSettings.folders,
                oldBrowserSettings.folders
            )
        ) {
            data = newBrowserSettings.folders
            notifyDataSetChanged()
        }
        firstRun = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        return when (viewType) {
            PARENT_FOLDER_VIEW_TYPE -> ParentFolderViewHolder(parent)
            CHILD_FOLDER_VIEW_TYPE -> ChildFolderViewHolder(parent)
            else -> throw RuntimeException()
        }
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        var target = position
        val folder: File?
        if (holder is ParentFolderViewHolder) {
            folder = rootFolder?.parentFile
        } else {
            if (showUpFolder) {
                target -= 1
            }
            folder = data?.get(target)
        }
        holder.bind(settings, folder as File)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && showUpFolder) {
            PARENT_FOLDER_VIEW_TYPE
        } else {
            CHILD_FOLDER_VIEW_TYPE
        }
    }

    private fun showParentFolder(): Boolean {
        val internal = mPrefs.preferEmulated()
        val storage = Storage.getPath(internal)
        return (null != rootFolder && Storage.getFile(internal) != rootFolder
                && (null != storage && rootFolder!!.absolutePath.startsWith(storage)))
    }

    override fun getItemCount(): Int {
        var count: Int = if (null == data) {
            0
        } else {
            data!!.size
        }
        if (showUpFolder) {
            count += 1
        }
        return count
    }

    abstract class FolderViewHolder(itemView: View?) : RecyclerView.ViewHolder(
        itemView!!
    ) {
        abstract fun bind(settings: BrowserSettings?, folder: File)
    }

    internal class ParentFolderViewHolder(itemView: View?) : FolderViewHolder(itemView) {
        var settings: BrowserSettings? = null
        var folder: File? = null

        constructor(parent: ViewGroup) : this(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.folder_view_parent, parent, false)
        )

        init {
            this.itemView.setOnClickListener {
                settings?.browserRootFolder = folder
                settings?.notifyChanges()
            }
        }

        override fun bind(settings: BrowserSettings?, folder: File) {
            this.settings = settings
            this.folder = folder
        }
    }

    private class ChildFolderViewHolder(itemView: View) : FolderViewHolder(itemView) {
        var settings: BrowserSettings? = null
        var folder: File? = null
        var txtFolderName: TextView

        constructor(parent: ViewGroup) : this(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.folder_view_child, parent, false)
        )

        init {
            txtFolderName = itemView.findViewById(R.id.text)
            this.itemView.setOnClickListener {
                settings?.browserRootFolder = folder
                settings?.notifyChanges()
            }
        }

        override fun bind(settings: BrowserSettings?, folder: File) {
            this.settings = settings
            this.folder = folder
            txtFolderName.text = folder.name
        }
    }

    companion object {
        const val PARENT_FOLDER_VIEW_TYPE = 0
        const val CHILD_FOLDER_VIEW_TYPE = 1
    }
}