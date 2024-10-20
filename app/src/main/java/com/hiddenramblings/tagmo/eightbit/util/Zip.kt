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
    ) {
        File(destDirectory).run {
            if (!exists()) {
                mkdirs()
            }
        }

        ZipFile(archiveFile).use { zip ->
            var entryCount = 0
            val totalEntries = zip.entries().toList().size // files to unzip
            zip.entries().asSequence()
                .map {
                    val outputFile = File(destDirectory + File.separator + it.name)
                    ZipIO(it, outputFile)
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
                    entryCount++
                    updateProgress(((entryCount.toDouble() / totalEntries) * 100).toInt())
                }
        }
    }
}