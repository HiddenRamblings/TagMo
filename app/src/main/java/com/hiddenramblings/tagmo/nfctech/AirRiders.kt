/*
 * ====================================================================
 * https://gist.github.com/xSke/d8bd2bdf6704760517363ca80072fe16
 * ====================================================================
 */
package com.hiddenramblings.tagmo.nfctech

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcA
import kotlin.experimental.and
import kotlin.experimental.xor

class AirRiders {
//        onSaveExt = { promptSaveBin(uiState.bytes!!.slice(0xf0*4..<0x100*4).toByteArray(), "ext") },
//        onSaveFull = { promptSaveBin(uiState.bytes!!, "bin")}

    fun onTagDiscovered(tag: Tag?) {
        val nfca = NfcA.get(tag)
        if (nfca == null) {
            return
        }

        val log = StringBuilder()
        val uidHex = tag?.id?.toHexSpaced() ?: "—"

        try {
            nfca.connect()
            nfca.timeout = 800

            val atqaHex = nfca.atqa.toHexSpaced()
            val sakHex = "0x%02X".format(nfca.sak)

            log.appendLine("UID : $uidHex")
            log.appendLine("ATQA: $atqaHex")
            log.appendLine("SAK : $sakHex")
            log.appendLine()

            unlock(nfca, log)

            Thread.sleep(100)

            requestSram(nfca, log)
            pollSramReady(nfca, log)

            // must be read first, otherwise TRANSFER_DIR bit is wrong
            val sram = readPagesRange(nfca, 0xf0, 0xff, log)

            var dump = byteArrayOf()
            dump += readPagesRange(nfca, 0x0, 0x1f, log)
            dump += readPagesRange(nfca, 0x20, 0x3f, log)
            dump += readPagesRange(nfca, 0x40, 0x5f, log)
            dump += readPagesRange(nfca, 0x60, 0x7f, log)
            dump += readPagesRange(nfca, 0x80, 0x9f, log)
            dump += readPagesRange(nfca, 0xa0, 0xbf, log)
            dump += readPagesRange(nfca, 0xc0, 0xdf, log)
            dump += readPagesRange(nfca, 0xe0, 0xe9, log)
            dump += ByteArray(8) // skip ea, eb
            dump += readPagesRange(nfca, 0xec, 0xed, log)
            dump += ByteArray(8) // skip ee, ef
            dump += sram

            sectorSelect(nfca, 1, log)
            unlock(nfca, log)
            Thread.sleep(100)

            dump += readPagesRange(nfca, 0x0, 0x1f, log)
            dump += readPagesRange(nfca, 0x20, 0x3f, log)
            dump += readPagesRange(nfca, 0x40, 0x5f, log)
            dump += readPagesRange(nfca, 0x60, 0x7f, log)
            dump += readPagesRange(nfca, 0x80, 0x9f, log)
            dump += readPagesRange(nfca, 0xa0, 0xbf, log)
            dump += readPagesRange(nfca, 0xc0, 0xdf, log)
            dump += readPagesRange(nfca, 0xe0, 0xff, log)


        } catch (e: Exception) {
            // failed
        } finally {
            try { nfca.close() } catch (_: Exception) {}
        }
    }

    private fun requestSram(nfca: NfcA, log: StringBuilder) {
        val buffer = ByteArray(64)
        buffer[0] = 1

        val crc = crc16Mcrf4xx(0xffff, buffer.slice(0..61).toByteArray(), 0)
        buffer[62] = crc.shr(8).toByte()
        buffer[63] = crc.and(0xff).toByte()

        fastWriteSram(nfca, buffer, log)
    }

    private fun sectorSelect(nfca: NfcA, sector: Int, log: StringBuilder) {
        log.appendLine("Sending sector select pt1")
        val sectorSelectCmd = byteArrayOf(0xC2.toByte(), 0xFF.toByte())
        val res1 = nfca.transceive(sectorSelectCmd)
        log.appendLine("Sector select pt1: ${res1.toHexSpaced()}")

        val sectorData = byteArrayOf(sector.toByte(), 0, 0, 0)
        try {
            nfca.timeout = 10
            val res2 = nfca.transceive(sectorData)
            log.appendLine("Sector select pt2: ${res2.toHexSpaced()}")
        } catch (e: TagLostException) {
            log.appendLine("Sector select pt2: tag lost (expected)")
            nfca.timeout = 800
        }
    }

    private fun pollSramReady(nfca: NfcA, log: StringBuilder) {
        while (true) {
            log.appendLine("Polling for SRAM ready...")
            val regs = readPagesRange(nfca, 0xed, 0xed, log)
            if (regs[2].and(0b1000) != 0.toByte()) return
        }
    }

    private fun unlock(nfca: NfcA, log: StringBuilder) {
        val id = nfca.tag.id

        val authCmd = byteArrayOf(0x1B.toByte(),
            id[1].xor(id[3]).xor(0xAA.toByte()),
            id[2].xor(id[4]).xor(0x55.toByte()),
            id[3].xor(id[5]).xor(0xAA.toByte()),
            id[4].xor(id[6]).xor(0x55.toByte()),
        )
        val resp = nfca.transceive(authCmd)
        log.appendLine("PWD_AUTH: ${resp.toHexSpaced()}")
    }

    private fun fastWriteSram(nfca: NfcA, buffer: ByteArray, log: StringBuilder) {
        require(buffer.size == 64)

        val fastWriteCmd = byteArrayOf(0xA6.toByte(), 0xf0.toByte(), 0xff.toByte()) + buffer
        log.appendLine("FAST_WRITE TX: ${fastWriteCmd.toHexSpaced()}")

        val resp = nfca.transceive(fastWriteCmd)
        log.appendLine("FAST_WRITE RX: ${resp.toHexSpaced()}")
    }

    private fun readPagesRange(nfca: NfcA, startPage: Int, endPage: Int, log: StringBuilder): ByteArray {
        require(startPage in 0..0xFF && endPage in 0..0xFF && startPage <= endPage)

        val fastReadCmd = byteArrayOf(0x3A.toByte(), startPage.toByte(), endPage.toByte())
        val resp = nfca.transceive(fastReadCmd)
        log.appendLine("FAST_READ ${startPage}-${endPage} OK (${resp.size} bytes).")
        return resp
    }
}

private fun ByteArray.toHexSpaced(): String = joinToString(" ") { "%02X".format(it) }

fun crc16Mcrf4xx(
    crcInit: Int,
    data: ByteArray,
    offset: Int = 0,
    len: Int = data.size - offset
): Int {
    require(offset in 0..data.size) { "offset out of range" }
    require(len >= 0 && offset + len <= data.size) { "len out of range" }

    var crc = crcInit and 0xFFFF
    var idx = offset
    var remaining = len

    while (remaining-- > 0) {
        crc = crc xor (data[idx++].toInt() and 0xFF)
        repeat(8) {
            crc = if ((crc and 1) != 0) {
                (crc ushr 1) xor 0x8408
            } else {
                crc ushr 1
            }
            crc = crc and 0xFFFF
        }
    }

    return crc and 0xFFFF
}