package com.hiddenramblings.tagmo.eightbit.util

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.sequences.forEach

data class ZipIO (val entry: ZipEntry, val output: File)

object Zip {
    /**
     * @param archiveFile
     * @param destDirectory
     * @throws IOException
     */
    @Throws(IllegalArgumentException::class, IOException::class)
    fun extract(
        archiveFile: File,
        destDirectory: String,
        updateProgress: (progress: Int) -> Unit
    ): List<File> {
        val destination = File(destDirectory).canonicalFile
        destination.run {
            if (!exists()) mkdirs()
        }
        val extractedFiles = arrayListOf<File>()

        ZipFile(archiveFile).use { zip ->
            var entryCount = 0
            val totalEntries = zip.entries().toList().count { !it.isDirectory } // files to unzip
            if (totalEntries == 0) updateProgress(100)
            zip.entries().asSequence()
                .map {
                    val outputFile = File(destination, it.name).canonicalFile
                    ZipIO(it, outputFile)
                }
                .onEach {
                    if (!it.output.path.startsWith(destination.path + File.separator)) {
                        throw IOException("Zip entry escapes destination: ${it.entry.name}")
                    }
                }
                .map {
                    it.output.parentFile?.run{
                        if (!exists()) mkdirs()
                    }
                    it
                }
                .filter { !it.entry.isDirectory }
                .forEach { (entry, output) ->
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    extractedFiles.add(output)
                    entryCount++
                    updateProgress(((entryCount.toDouble() / totalEntries) * 100).toInt())
                }
        }
        return extractedFiles
    }
}
