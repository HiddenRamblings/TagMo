package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * Abstraction of the request->response link to the ChameleonUltra.
 *
 * The protocol is sequential: one command in flight at a time. The real implementation (BLE) lives
 * on the Android side and builds on the codec; a fake is used for JVM tests.
 * No Android dependency here: only `kotlinx.coroutines` (pure JVM) is required.
 */
interface ChameleonTransport {
    /**
     * Sends an already-encoded frame (see `ChameleonCommands` / `ChameleonFrame.encode`) and suspends
     * until the complete (reassembled) response frame is received, or throws on timeout/error.
     */
    suspend fun send(request: ByteArray): ChameleonFrame
}

/** ChameleonUltra protocol/transport error (unexpected CMD, non-success status, timeout, link lost). */
class ChameleonProtocolException(message: String) : Exception(message)
