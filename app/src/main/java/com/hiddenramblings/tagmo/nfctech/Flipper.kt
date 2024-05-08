package com.hiddenramblings.tagmo.nfctech

import android.media.MediaScannerConnection
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import com.hiddenramblings.tagmo.nfctech.TagArray.toPages
import com.hiddenramblings.tagmo.nfctech.TagArray.toTagArray
import java.io.File
import java.io.FileOutputStream


object Flipper {
    private val String.hexFormat : String get() {
        return this.replace(Regex("..(?!$)"), "$0 ")
    }
    val directory = Storage.getDownloadDir("TagMo", "Flipper")

    fun ByteArray.toNFC(filename: String) {
        val pages = this.toTagArray().toPages()
        val uidHex = "${pages[0]?.toHex()}${pages[1]?.toHex()}"
        val signature = if (this.size == NfcByte.TAG_FULL_SIZE)
            this.copyOfRange(NfcByte.SIGNATURE, NfcByte.TAG_FULL_SIZE).toHex().hexFormat
        else
            ByteArray(32).toHex().hexFormat
        val contents = StringBuilder("Filetype: Flipper NFC device")
                .append(Debug.separator).append("Version: 2")
                .append(Debug.separator).append("Device type: NTAG215")
                .append(Debug.separator).append("UID: ")
                .append(uidHex.substring(0, uidHex.length - 2).hexFormat)
                .append(Debug.separator).append("ATQA: 44 00")
                .append(Debug.separator).append("SAK: 00")
                .append(Debug.separator).append("Signature: ").append(signature)
                .append(Debug.separator).append("Mifare version: 00 04 04 02 01 00 11 03")
                .append(Debug.separator).append("Counter 0: 0")
                .append(Debug.separator).append("Tearing 0: 00")
                .append(Debug.separator).append("Counter 1: 0")
                .append(Debug.separator).append("Tearing 1: 00")
                .append(Debug.separator).append("Counter 2: 0")
                .append(Debug.separator).append("Tearing 2: 00")
                .append(Debug.separator).append("Pages total: 135")
        pages.forEachIndexed{ index, bytes ->
            if (index == 133) {
                // turbospok/Flipper-NTAG215-password-converter
                // pwd.append(uid[1] ^ uid[3] ^ 0xAA)
                // pwd.append(uid[2] ^ uid[4] ^ 0x55)
                // pwd.append(uid[3] ^ uid[5] ^ 0xAA)
                // pwd.append(uid[4] ^ uid[6] ^ 0x55)
            }
            bytes?.let {
                contents.append(Debug.separator).append("Page $index: ${it.toHex().hexFormat}")
            }
        }
        val nfcFile = File(directory, "$filename.nfc")
        FileOutputStream(nfcFile).use { stream ->
            stream.write(contents.toString().toByteArray())
            stream.flush()
        }
        try {
            MediaScannerConnection.scanFile(
                    TagMo.appContext, arrayOf(nfcFile.canonicalPath), null, null
            )
        } catch (ignored: Exception) { }
    }
}
