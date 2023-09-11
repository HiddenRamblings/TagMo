/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */
package com.hiddenramblings.tagmo.eightbit.os

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.TagMo
import java.io.File

object Storage : Environment() {
    private const val PROVIDER = BuildConfig.APPLICATION_ID + ".provider"
    private const val STORAGE_ROOT = "/storage"
    private var storageFile: File? = null
    private var isInternalPreferred = false
    private var isPhysicalAvailable = false
    private fun getRootPath(directory: File?): File? {
        return directory?.parentFile?.parentFile?.parentFile?.parentFile
    }

    fun hasPhysicalStorage(): Boolean {
        return isPhysicalAvailable
    }

    private val externalMounts: HashSet<String>
        get() {
            val out = HashSet<String>()
            val reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4|fuse|sdfat).*rw.*".toRegex()
            val s = StringBuilder()
            try {
                val process = ProcessBuilder().command("mount").redirectErrorStream(true).start()
                process.waitFor()
                val inStream = process.inputStream
                val buffer = ByteArray(1024)
                while (inStream.read(buffer) != -1) s.append(String(buffer))
                inStream.close()
                val lines = s.toString().split("\n").toTypedArray()
                for (line in lines) {
                    if (line.contains("secure")) continue
                    if (line.contains("asec")) continue
                    if (line.matches(reg)) {
                        for (part in line.split(" ".toRegex()).toTypedArray()) {
                            if (part.startsWith(File.separator) && !part.contains("vold"))
                                out.add(part)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return out
        }

    private fun setFileMounts(): File {
        val extStorage = externalMounts
        extStorage.forEach {
            // Workaround for WRITE_MEDIA_STORAGE
            val sdCardPath = it.replace("mnt/media_rw", "storage")
            if (sdCardPath != getExternalStorageDirectory().absolutePath
                && File(sdCardPath).canRead()) {
                isPhysicalAvailable = true
                return File(sdCardPath)
            }
        }
        return getExternalStorageDirectory()
    }

    private fun setFileGeneric(internal: Boolean): File? {
        var emulated: File? = null
        var physical: File? = null
        try {
            File(STORAGE_ROOT).listFiles()?.forEach {
                if (it.absolutePath.endsWith("emulated"))
                    emulated = File(it, "0")
                else if (!it.absolutePath.endsWith("self"))
                    physical = it
            }
            // Force a possible failure to prevent crash later
            Log.d("EMULATED", (emulated as File).absolutePath)
            Log.d("PHYSICAL", physical?.absolutePath ?: "")
            if (null != physical && physical !== emulated) isPhysicalAvailable = true
        } catch (e: NullPointerException) {
            return if (internal)
                getExternalStorageDirectory()
            else setFileMounts().also { storageFile = it }
        }
        return if (internal)
            emulated ?: physical.also { storageFile = it }
        else physical ?: emulated.also { storageFile = it }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun setFileLollipop(internal: Boolean): File? {
        val storage = ContextCompat.getExternalFilesDirs(TagMo.appContext, null)
        val emulated: File? = try {
            if (null != storage[0] && storage[0]!!.canRead())
                getRootPath(storage[0])
            else null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NullPointerException) {
            null
        }
        val physical: File? = try {
            if (storage.size > 1 && storage[1].let { it.canRead() && !isExternalStorageEmulated(it) })
                getRootPath(storage[1])
            else null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NullPointerException) {
            null
        }
        if (null != physical && physical !== emulated) isPhysicalAvailable = true
        return if (internal)
            emulated ?: setFileGeneric(true).also { storageFile = it }
        else physical ?: (emulated ?: setFileGeneric(false))
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun setFileRedVelvet(internal: Boolean): File? {
        var emulated: File? = null
        var physical: File? = null
        try {
            getStorageDirectory().listFiles()?.forEach {
                if (it.absolutePath.endsWith("emulated"))
                    emulated = File(it, "0")
                else if (!it.absolutePath.endsWith("self"))
                    physical = it
            }
            // Force a possible failure to prevent crash later
            Log.d("EMULATED", (emulated as File).absolutePath)
            Log.d("PHYSICAL", physical?.absolutePath ?: "")
            if (null != physical && physical !== emulated) isPhysicalAvailable = true
        } catch (e: IllegalArgumentException) {
            return setFileLollipop(internal)
        } catch (e: NullPointerException) {
            return setFileLollipop(internal)
        }
        return if (internal)
            emulated ?: physical.also { storageFile = it }
        else physical ?: emulated.also { storageFile = it }
    }

    private fun setFile(internal: Boolean): File? {
        isInternalPreferred = internal
        return if (Version.isRedVelvet)
            setFileRedVelvet(internal)
        else if (Version.isLollipop)
            setFileLollipop(internal)
        else setFileGeneric(internal)
    }

    fun getFile(internal: Boolean): File? {
        return if (null != storageFile && internal == isInternalPreferred)
            storageFile!!
        else setFile(internal)
    }

    fun getPath(internal: Boolean): String? {
        return getFile(internal)?.absolutePath
    }

    fun getFileUri(file: File): Uri {
        return if (Version.isNougat)
            FileProvider.getUriForFile(TagMo.appContext, PROVIDER, file)
        else
            file.toUri()
    }

    fun getRelativePath(file: File?, internal: Boolean): String {
        val filePath = file?.absolutePath
        val storagePath =
            if (filePath?.contains("/Foomiibo/") == true)
                TagMo.appContext.filesDir.absolutePath
            else getPath(internal)
        return if (!storagePath.isNullOrEmpty() && filePath?.startsWith(storagePath) == true)
            filePath.substring(storagePath.length)
        else filePath ?: ""
    }

    fun getDownloadDir(directory: String?, subfolder: String?): File {
        val downloads = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)
        return if (null != directory && null != subfolder) {
            val destination = File(downloads, "$directory${File.separator}$subfolder")
            destination.mkdirs()
            destination
        } else if (null != directory) {
            val destination = File(downloads, directory)
            destination.mkdirs()
            destination
        } else {
            downloads
        }
    }

    fun getDownloadDir(directory: String?): File {
        return getDownloadDir(directory, null)
    }

    private fun unicodeString(unicode: String): String {
        return unicode
            .replace("%3A", ":")
            .replace("%2F", "/")
            .replace("%20", " ")
    }

    fun getRelativeDocument(uri: Uri?): String {
        val treeUri = unicodeString(
            if (uri.toString().contains("/tree/"))
                uri.toString().substringAfterLast("/tree/")
            else uri.toString()
        )
        return if (treeUri.contains("primary:"))
            "/" + treeUri.substringAfterLast("primary:")
        else treeUri
    }
}