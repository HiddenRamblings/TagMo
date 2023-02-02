/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-bit Dream", "TwistedUmbrella",
 * "TagMo" and "AbandonedCart" and these labels should be considered
 * the equivalent of any usage of the aforementioned phrase.
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All materials mentioning features or use of this software and
 *    redistributions of any form whatsoever must display the following
 *    acknowledgment unless made available by tagged, public "commits":
 *    "This product includes software developed for TagMo by AbandonedCart"
 *
 * 4. The TagMo labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the TagMo labels
 *    nor may these labels appear in their names or product information without
 *    prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND TagMo ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
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
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.TagMo.Companion.appContext
import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer
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
                val process = ProcessBuilder().command("mount")
                    .redirectErrorStream(true).start()
                process.waitFor()
                val inStream = process.inputStream
                val buffer = ByteArray(1024)
                while (inStream.read(buffer) != -1) {
                    s.append(String(buffer))
                }
                inStream.close()
                val lines = s.toString().split("\n").toTypedArray()
                for (line in lines) {
                    if (line.contains("secure")) continue
                    if (line.contains("asec")) continue
                    if (line.matches(reg)) {
                        for (part in line.split(" ".toRegex()).toTypedArray()) {
                            if (part.startsWith(File.separator) && !part.contains("vold")) out.add(
                                part
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        val storage = ContextCompat.getExternalFilesDirs(appContext, null)
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
            if (storage.size > 1 && null != storage[1] && storage[1]!!.canRead()
                && !isExternalStorageEmulated(storage[1]!!))
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
        return if (isNewer(Build.VERSION_CODES.R))
            setFileRedVelvet(internal)
        else if (isNewer(Build.VERSION_CODES.LOLLIPOP))
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

    fun getFileUri(file: File?): Uri {
        return if (isNewer(Build.VERSION_CODES.N))
            FileProvider.getUriForFile(appContext, PROVIDER, file!!)
        else Uri.fromFile(file)
    }

    fun getRelativePath(file: File?, internal: Boolean): String {
        val filePath = file?.absolutePath
        val storagePath =
            if (filePath?.contains("/Foomiibo/") == true)
                appContext.filesDir.absolutePath
            else getPath(internal)
        return if (!storagePath.isNullOrEmpty() && filePath?.startsWith(storagePath) == true)
            filePath.substring(storagePath.length)
        else filePath ?: ""
    }

    fun getDownloadDir(directory: String?, subfolder: String?): File {
        val downloads = getExternalStoragePublicDirectory(
            DIRECTORY_DOWNLOADS
        )
        return if (null != directory && null != subfolder) {
            val destination = File(downloads, directory + File.separator + subfolder)
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
                uri.toString().substring(uri.toString().lastIndexOf("/tree/") + 6)
            else uri.toString()
        )
        return if (treeUri.contains("primary:"))
            "/" + treeUri.substring(treeUri.lastIndexOf("primary:") + 8)
        else treeUri
    }
}