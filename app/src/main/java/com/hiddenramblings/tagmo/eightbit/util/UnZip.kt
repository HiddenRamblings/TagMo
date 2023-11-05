package com.hiddenramblings.tagmo.eightbit.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.widget.ProgressAlert
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile

class UnZip(var context: Context, var archive: File, var outputDir: File) : Runnable {
    private val zipHandler = Handler(Looper.getMainLooper())
    private var dialog: ProgressAlert? = null

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun decompress() {
        ZipFile(archive).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                // get the zip entry
                val finalEntry = entries.nextElement()
                zipHandler.post {
                    dialog?.setMessage(context.getString(R.string.unzip_item, finalEntry.name))
                }
                if (finalEntry.isDirectory) {
                    val dir = File(
                        outputDir, finalEntry.name.replace(File.separator, "")
                    )
                    if (!dir.exists() && !dir.mkdirs())
                        throw RuntimeException(context.getString(R.string.mkdir_failed, dir.name))
                } else {
                    zipFile.getInputStream(finalEntry).use { zipInStream ->
                        if (Version.isOreo) {
                            Files.copy(
                                zipInStream,
                                Paths.get(outputDir.absolutePath, finalEntry.name)
                            )
                        } else {
                            FileOutputStream(File(outputDir, finalEntry.name)).use { fileOut ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (zipInStream.read(buffer).also { len = it } != -1)
                                    fileOut.write(buffer, 0, len)
                            }
                        }
                    }
                }
            }
        }
    }
    @Throws(IOException::class)
    private fun decompressLegacy() {
        val zipFile = ZipFile(archive)
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            // get the zip entry
            val finalEntry = entries.nextElement()
            zipHandler.post {
                dialog?.setMessage(context.getString(R.string.unzip_item, finalEntry.name))
            }
            if (finalEntry.isDirectory) {
                val dir = File(
                    outputDir, finalEntry.name.replace(File.separator, "")
                )
                if (!dir.exists() && !dir.mkdirs())
                    throw RuntimeException(context.getString(R.string.mkdir_failed, dir.name))
            } else {
                val zipInStream = zipFile.getInputStream(finalEntry)
                if (Version.isOreo) {
                    Files.copy(zipInStream, Paths.get(outputDir.absolutePath, finalEntry.name))
                } else {
                    val fileOut = FileOutputStream(File(outputDir, finalEntry.name))
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (zipInStream.read(buffer).also { len = it } != -1)
                        fileOut.write(buffer, 0, len)
                    fileOut.close()
                }
                zipInStream.close()
            }
        }
        zipFile.close()
    }

    override fun run() {
        zipHandler.post { dialog = ProgressAlert.show(context, "") }
        try {
            if (Version.isKitKat)
                decompress()
            else
                decompressLegacy()
        } catch (e: IOException) {
            Debug.warn(e)
        } finally {
            zipHandler.post { dialog?.dismiss() }
            archive.delete()
        }
    }
}