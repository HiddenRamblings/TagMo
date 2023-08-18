package com.hiddenramblings.tagmo.eightbit.util

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.*
import java.util.zip.ZipFile


/**
 * UnzipUtils class extracts files and sub-directories of a standard zip file to
 * a destination directory.
 *
 */
object Zip {
    /**
     * @param zipFile
     * @param destDirectory
     * @throws IOException
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    @Throws(IOException::class)
    fun extract(
            zipFile: File,
            destDirectory: String,
            updateProgress: (progress: Int) -> Unit
    ) {
        File(destDirectory).run {
            if (!exists()) {
                mkdirs()
            }
        }
        ZipFile(zipFile).use { zip ->
            var entryCount = 0
            val totalEntries = zip.entries().toList().size // files to unzip
            zip.entries().asSequence().forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val filePath = destDirectory + File.separator + entry.name
                    if (!entry.isDirectory) {
                        // if the entry is a file, extracts it
                        val parentFolder = File(getParentDirPath(filePath))
                        if (!parentFolder.exists()) {
                            parentFolder.mkdirs()
                        }
                        extractFile(input, filePath)
                    } else {
                        // if the entry is a directory, make the directory
                        val dir = File(filePath)
                        dir.mkdir()
                    }
                    entryCount++
                    updateProgress(((entryCount.toDouble() / totalEntries) * 100).toInt())
                }
            }
        }
    }

    private fun getParentDirPath(fileOrDirPath: String): String {
        val endsWithSlash = fileOrDirPath.endsWith(File.separator)
        return fileOrDirPath.substring(0, fileOrDirPath.lastIndexOf(
                File.separatorChar,
                if (endsWithSlash) fileOrDirPath.length - 2 else fileOrDirPath.length - 1)
        )
    }

    /**
     * Extracts a zip entry (file entry)
     * @param inputStream
     * @param destFilePath
     * @throws IOException
     */
    /**
     * Extracts a zip entry (file entry)
     * @param inputStream
     * @param destFilePath
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun extractFile(inputStream: InputStream, destFilePath: String) {
        val bos = BufferedOutputStream(FileOutputStream(destFilePath))
        val bytesIn = ByteArray(BUFFER_SIZE)
        var read: Int
        while (inputStream.read(bytesIn).also { read = it } != -1) {
            bos.write(bytesIn, 0, read)
        }
        bos.close()
    }

    /**
     * Size of the buffer to read/write data
     */
    private const val BUFFER_SIZE = 4096

}