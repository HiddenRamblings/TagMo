package com.hiddenramblings.tagmo.bluetooth.chameleon

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.hiddenramblings.tagmo.bluetooth.chameleon.protocol.AppVersion
import com.hiddenramblings.tagmo.bluetooth.chameleon.protocol.ChameleonUploader

/**
 * High-level facade combining the BLE transport and the upload orchestrator.
 * Single entry point for the UI layer: connect, read the version, push a dump.
 *
 * Typical usage (from a coroutine):
 * ```
 * val client = ChameleonClient(context) { /* on disconnect */ }
 * client.connect(device)
 * val version = client.getAppVersion()        // compatibility gate
 * client.uploadAmiibo(dump540, slot) { done, total -> /* progress */ }
 * client.close()
 * ```
 */
class ChameleonClient(
    context: Context,
    onDisconnect: () -> Unit = {},
) {
    private val transport = ChameleonBleTransport(context, onDisconnect)
    private val uploader = ChameleonUploader(transport)

    suspend fun connect(device: BluetoothDevice) = transport.connect(device)

    /** Firmware application version (to compare against the minimum supported — compatibility gate). */
    suspend fun getAppVersion(): AppVersion = uploader.getAppVersion()

    /** Pushes an NTAG215 dump (540 bytes) into [slot] (0..7), preserving the original UID. */
    suspend fun uploadAmiibo(
        dump: ByteArray,
        slot: Int,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
    ) = uploader.uploadAmiibo(dump, slot, onProgress = onProgress)

    fun close() = transport.close()
}
