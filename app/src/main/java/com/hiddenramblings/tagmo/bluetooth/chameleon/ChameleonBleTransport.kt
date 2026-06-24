package com.hiddenramblings.tagmo.bluetooth.chameleon

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.hiddenramblings.tagmo.bluetooth.GattArray.toPortions
import com.hiddenramblings.tagmo.bluetooth.Nordic
import com.hiddenramblings.tagmo.bluetooth.chameleon.protocol.ChameleonFrame
import com.hiddenramblings.tagmo.bluetooth.chameleon.protocol.ChameleonProtocolException
import com.hiddenramblings.tagmo.bluetooth.chameleon.protocol.ChameleonTransport
import com.hiddenramblings.tagmo.bluetooth.chameleon.protocol.IncrementalFrameReader
import com.hiddenramblings.tagmo.eightbit.os.Version
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Real BLE transport to the ChameleonUltra. Implements [ChameleonTransport] over a dedicated GATT
 * link on the NUS service (shared with TagMo's other BLE devices: [Nordic.NUS]).
 *
 * - Send: the encoded frame is split into MTU-3 chunks written to [Nordic.TX] (6e400002).
 * - Receive: notifications on [Nordic.RX] (6e400003) feed an [IncrementalFrameReader] that
 *   reassembles frame(s); the first complete frame resolves the in-flight command.
 * - Correlation: one command in flight at a time (serialized by [mutex]) + per-command timeout.
 *
 * Kept separate from TagMo's legacy `GattService` (text-based Flask/Puck protocols) because the
 * Chameleon uses a distinct binary framing. BLE permissions are handled upstream (see BluetoothHandler).
 *
 * Timing constants (target MTU, inter-chunk delay, timeouts) are conservative and centralized below.
 *
 * Threading: GATT callbacks run on a binder thread, send()/connect() on a coroutine. Fields shared
 * across the two are @Volatile, and access to the non-thread-safe [reader] is synchronized on it.
 */
@SuppressLint("MissingPermission")
class ChameleonBleTransport(
    private val context: Context,
    private val onDisconnect: () -> Unit = {},
) : ChameleonTransport {

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var notifyChar: BluetoothGattCharacteristic? = null

    private val reader = IncrementalFrameReader()
    private val mutex = Mutex()

    @Volatile private var mtuPayload = DEFAULT_MTU_PAYLOAD       // MTU - 3
    @Volatile private var pending: CompletableDeferred<ChameleonFrame>? = null
    /** CMD of the in-flight request: a notification only resolves it if the response echoes this CMD. */
    @Volatile private var expectedCmd: Int = -1
    @Volatile private var writeAck: CompletableDeferred<Unit>? = null
    @Volatile private var connectDeferred: CompletableDeferred<Unit>? = null
    /** True when the write characteristic supports WRITE (with response): each chunk awaits its ack. */
    @Volatile private var useWriteResponse = false

    /** Connect, discover services, enable notifications and negotiate MTU. Suspends until ready. */
    suspend fun connect(device: BluetoothDevice) = mutex.withLock {
        val deferred = CompletableDeferred<Unit>()
        connectDeferred = deferred
        gatt = device.connectGatt(context, false, callback)
        withTimeout(CONNECT_TIMEOUT_MS) { deferred.await() }
    }

    override suspend fun send(request: ByteArray): ChameleonFrame = mutex.withLock {
        val g = gatt ?: throw ChameleonProtocolException("transport not connected")
        val tx = writeChar ?: throw ChameleonProtocolException("write characteristic missing")
        synchronized(reader) { reader.reset() }
        expectedCmd = ChameleonFrame.decode(request).getOrNull()?.cmd ?: -1
        val deferred = CompletableDeferred<ChameleonFrame>()
        pending = deferred
        try {
            for (chunk in request.toPortions(mtuPayload)) {
                writeChunk(g, tx, chunk)
            }
            withTimeout(COMMAND_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending = null
        }
    }

    fun close() {
        pending?.cancel()
        writeAck?.cancel()
        connectDeferred?.cancel()   // unblock a connect() still awaiting (e.g. user left mid-connect)
        try { gatt?.disconnect() } catch (_: Exception) { }
        try { gatt?.close() } catch (_: Exception) { }
        gatt = null
        writeChar = null
        notifyChar = null
        synchronized(reader) { reader.reset() }
    }

    private suspend fun writeChunk(g: BluetoothGatt, tx: BluetoothGattCharacteristic, chunk: ByteArray) {
        if (useWriteResponse) {
            // Write WITH response: wait for onCharacteristicWrite before the next chunk
            // (reliable, no guessed delay). Falls back to timeout if the ack never arrives.
            val ack = CompletableDeferred<Unit>()
            writeAck = ack
            doWrite(g, tx, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            try {
                withTimeout(WRITE_TIMEOUT_MS) { ack.await() }
            } finally {
                writeAck = null
            }
        } else {
            // Fallback WITHOUT response: small inter-chunk delay.
            doWrite(g, tx, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            delay(CHUNK_DELAY_MS)
        }
    }

    private fun doWrite(g: BluetoothGatt, tx: BluetoothGattCharacteristic, chunk: ByteArray, type: Int) {
        if (Version.isTiramisu) {
            g.writeCharacteristic(tx, chunk, type)
        } else @Suppress("deprecation") {
            tx.writeType = type
            tx.value = chunk
            g.writeCharacteristic(tx)
        }
    }

    private fun onNotify(value: ByteArray) {
        val frames = synchronized(reader) { reader.append(value) }
        val d = pending ?: return
        // Only resolve with a frame echoing the in-flight CMD; ignore stale/spurious frames
        // (e.g. a late response from a previously timed-out command).
        frames.firstOrNull { it.cmd == expectedCmd }?.let { if (!d.isCompleted) d.complete(it) }
    }

    private fun enableNotifications(g: BluetoothGatt) {
        val notify = notifyChar ?: run {
            connectDeferred?.takeIf { !it.isCompleted }?.completeExceptionally(
                ChameleonProtocolException("notification characteristic missing"),
            )
            return
        }
        g.setCharacteristicNotification(notify, true)
        val cccd = notify.getDescriptor(CCCD) ?: run {
            // No CCCD descriptor: best effort, consider the link ready.
            connectDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
            return
        }
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Version.isTiramisu) {
            g.writeDescriptor(cccd, enable)
        } else @Suppress("deprecation") {
            cccd.value = enable
            g.writeDescriptor(cccd)
        }
    }

    private fun failConnect(message: String) {
        connectDeferred?.takeIf { !it.isCompleted }
            ?.completeExceptionally(ChameleonProtocolException(message))
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    pending?.cancel()
                    writeAck?.cancel()
                    failConnect("BLE link lost")
                    onDisconnect()
                }
            }
        }

        // One GATT operation at a time: SERIALIZE
        // discoverServices -> requestMtu -> (onMtuChanged) CCCD -> (onDescriptorWrite) ready.
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnect("service discovery failed: status=$status")
                return
            }
            val service = g.getService(Nordic.NUS)
            writeChar = service?.getCharacteristic(Nordic.TX)
            notifyChar = service?.getCharacteristic(Nordic.RX)
            val write = writeChar
            if (write == null || notifyChar == null) {
                failConnect("NUS service/characteristic not found")
                return
            }
            // Prefer write WITH response if the characteristic advertises it (more reliable),
            // otherwise fall back to write WITHOUT response.
            useWriteResponse = (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            // 1) MTU first; notifications are enabled in onMtuChanged.
            if (!g.requestMtu(REQUESTED_MTU)) enableNotifications(g)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtuPayload = (mtu - 3).coerceAtLeast(MIN_MTU_PAYLOAD)
            }
            // 2) Once MTU is set, enable notifications (next GATT op) regardless of MTU outcome.
            enableNotifications(g)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int,
        ) {
            // 3) CCCD written -> notifications active -> connection ready.
            if (descriptor.uuid == CCCD) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    connectDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
                } else {
                    failConnect("CCCD write failed: status=$status")
                }
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
        ) {
            // Ack of a written chunk (WRITE_TYPE_DEFAULT mode): unblocks the next chunk.
            if (characteristic.uuid != Nordic.TX) return
            val ack = writeAck?.takeIf { !it.isCompleted } ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ack.complete(Unit)
            } else {
                ack.completeExceptionally(ChameleonProtocolException("chunk write failed: status=$status"))
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray,
        ) {
            if (characteristic.uuid == Nordic.RX) onNotify(value)
        }

        // API <= 32
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == Nordic.RX) {
                @Suppress("deprecation")
                characteristic.value?.let { onNotify(it) }
            }
        }
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val REQUESTED_MTU = 247
        private const val DEFAULT_MTU_PAYLOAD = 20   // MTU 23 - 3 (before negotiation)
        private const val MIN_MTU_PAYLOAD = 20
        private const val CHUNK_DELAY_MS = 15L
        private const val WRITE_TIMEOUT_MS = 3_000L
        private const val COMMAND_TIMEOUT_MS = 5_000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
