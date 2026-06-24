package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * Simulated transport for JVM tests: decodes each request, records it, and returns the response
 * produced by [responder]. Lets tests script success/error/unexpected-CMD without hardware.
 */
class FakeChameleonTransport(
    private val responder: (ChameleonFrame) -> ChameleonFrame,
) : ChameleonTransport {

    /** Decoded request frames, in send order. */
    val sent = mutableListOf<ChameleonFrame>()

    override suspend fun send(request: ByteArray): ChameleonFrame {
        val frame = ChameleonFrame.decode(request).getOrThrow()
        sent += frame
        return responder(frame)
    }

    companion object {
        /** Generic success response (STATUS=0x68) echoing the request CMD. */
        fun ok(request: ChameleonFrame, data: ByteArray = ByteArray(0)): ChameleonFrame =
            ChameleonFrame(request.cmd, ChameleonProtocol.STATUS_SUCCESS, data)

        /** Transport that always replies SUCCESS; special data for GET_APP_VERSION. */
        fun allSuccess(versionMajor: Int = 2, versionMinor: Int = 1): FakeChameleonTransport =
            FakeChameleonTransport { req ->
                if (req.cmd == Command.GET_APP_VERSION.code) {
                    ok(req, byteArrayOf(versionMajor.toByte(), versionMinor.toByte()))
                } else {
                    ok(req)
                }
            }
    }
}
