/*
 * ====================================================================
 * Copyright (C) 2016 S&K Software Development Ltd.
 * https://github.com/snksoft/java-crc
 * ====================================================================
 */
package com.github.snksoft.crc

class CRC(crcParams: Parameters) {
    /**
     * Parameters represents set of parameters defining a particular CRC algorithm.
     */
    class Parameters {
        var width : Int // Width of the CRC expressed in bits
            private set
        var polynomial : Long // Polynomial used in this CRC calculation
            private set
        var isReflectIn : Boolean // Refin indicates whether input bytes should be reflected
            private set
        var isReflectOut : Boolean // Refout indicates whether output bytes should be reflected
        var init : Long // Init is initial value for CRC calculation
        var finalXor : Long // Xor is a value for final xor to be applied before returning result

        constructor(
                width: Int, polynomial: Long, init: Long,
                reflectIn: Boolean, reflectOut: Boolean, finalXor: Long
        ) {
            this.width = width
            this.polynomial = polynomial
            isReflectIn = reflectIn
            isReflectOut = reflectOut
            this.init = init
            this.finalXor = finalXor
        }

        /**
         * Constructs a new CRC processor for table based CRC calculations.
         * Underneath, it just calls finalCRC() method.
         * @param  parameters CRC algorithm parameters
         * @throws RuntimeException if CRC sum width is not divisible by 8
         */
        @Throws(RuntimeException::class)
        constructor(parameters: Parameters) {
            width = parameters.width
            polynomial = parameters.polynomial
            isReflectIn = parameters.isReflectIn
            isReflectOut = parameters.isReflectOut
            init = parameters.init
            finalXor = parameters.finalXor
        }

        companion object {
            /** CCITT CRC parameters  */
            val CCITT = Parameters(
                    16, 0x1021, 0x00FFFF,
                    reflectIn = false, reflectOut = false, finalXor = 0x0
            )

            /** CRC16 CRC parameters, also known as ARC  */
            val CRC16 = Parameters(
                    16, 0x8005, 0x0000
                    , reflectIn = true, reflectOut = true, finalXor = 0x0
            )

            /** XMODEM is a set of CRC parameters commonly referred as "XMODEM"  */
            val XMODEM = Parameters(
                    16, 0x1021, 0x0000,
                    reflectIn = false, reflectOut = false, finalXor = 0x0
            )

            /** XMODEM2 is another set of CRC parameters commonly referred as "XMODEM"  */
            val XMODEM2 = Parameters(
                    16, 0x8408, 0x0000,
                    reflectIn = true, reflectOut = true, finalXor = 0x0
            )

            /** CRC32 is by far the the most commonly used CRC-32 polynom and set of parameters  */
            val CRC32 = Parameters(
                    32, 0x04C11DB7, 0x00FFFFFFFFL,
                    reflectIn = true, reflectOut = true, finalXor = 0x00FFFFFFFFL
            )

            /** IEEE is an alias to CRC32  */
            val IEEE = CRC32

            /** Castagnoli polynomial. used in iSCSI. And also provided by hash/crc32 package.  */
            val Castagnoli = Parameters(
                    32, 0x1EDC6F41L, 0x00FFFFFFFFL,
                    reflectIn = true, reflectOut = true, finalXor = 0x00FFFFFFFFL
            )

            /** CRC32C is an alias to Castagnoli  */
            val CRC32C = Castagnoli

            /** Koopman polynomial  */
            val Koopman = Parameters(
                    32, 0x741B8CD7L, 0x00FFFFFFFFL,
                    reflectIn = true, reflectOut = true, finalXor = 0x00FFFFFFFFL
            )

            /** CRC64ISO is set of parameters commonly known as CRC64-ISO  */
            val CRC64ISO = Parameters(
                    64, 0x000000000000001BL, -0x1L,
                    reflectIn = true, reflectOut = true, finalXor = -0x1L
            )

            /** CRC64ECMA is set of parameters commonly known as CRC64-ECMA  */
            val CRC64ECMA = Parameters(
                    64, 0x42F0E1EBA9EA3693L, -0x1L,
                    reflectIn = true, reflectOut = true, finalXor = -0x1L
            )
        }
    }

    private val crcParams: Parameters
    private val initValue: Long
    private val crctable: LongArray
    private val mask: Long

    /**
     * Returns initial value for this CRC intermediate value
     * This method is used when starting a new iterative CRC calculation (using init, update
     * and finalCRC methods, possibly supplying data in chunks).
     * @return initial value for this CRC intermediate value
     */
    fun init(): Long {
        return initValue
    }
    /**
     * This method is used to feed data when performing iterative CRC calculation (using init, update
     * and finalCRC methods, possibly supplying data in chunks). It can be called multiple times per
     * CRC calculation to feed data to be processed in chunks.
     * @param value CRC intermediate value so far
     * @param chunk data chunk to b processed by this call
     * @param offset is 0-based offset of the data to be processed in the array supplied
     * @param length indicates number of bytes to be processed.
     * @return updated intermediate value for this CRC
     */
    @JvmOverloads
    fun update(value: Long, chunk: ByteArray, offset: Int = 0, length: Int = chunk.size): Long {
        var curValue = value
        if (crcParams.isReflectIn) {
            for (i in 0 until length) {
                val v = chunk[offset + i]
                curValue = crctable[curValue.toByte().toInt() xor v.toInt() and 0x00FF] xor (curValue ushr 8)
            }
        } else if (crcParams.width < 8) {
            for (i in 0 until length) {
                val v = chunk[offset + i]
                curValue = crctable[(curValue shl 8 - crcParams.width).toByte().toInt()
                        xor v.toInt() and 0xFF] xor (curValue shl 8)
            }
        } else {
            for (i in 0 until length) {
                val v = chunk[offset + i]
                curValue = crctable[(curValue ushr crcParams.width - 8).toByte().toInt()
                        xor v.toInt() and 0xFF] xor (curValue shl 8)
            }
        }
        return curValue
    }

    init {
        this.crcParams = Parameters(crcParams)
        initValue = if (crcParams.isReflectIn)
            reflect(crcParams.init, crcParams.width)
        else
            crcParams.init
        mask = (if (crcParams.width >= 64) 0 else 1L shl crcParams.width) - 1
        crctable = LongArray(256)
        val tmp = ByteArray(1)
        val tableParams = Parameters(crcParams)
        tableParams.init = 0
        tableParams.isReflectOut = tableParams.isReflectIn
        tableParams.finalXor = 0
        for (i in 0..255) {
            tmp[0] = i.toByte()
            crctable[i] = calculateCRC(tableParams, tmp)
        }
    }

    companion object {
        /**
         * Reverses order of last count bits.
         * @param value value from which bits need to be reversed
         * @param count indicates how many bits be rearranged
         * @return      the value with specified bits order reversed
         */
        private fun reflect(value: Long, count: Int): Long {
            var ret = value
            for (idx in 0 until count) {
                val srcbit = 1L shl idx
                val dstbit = 1L shl count - idx - 1
                ret = if (value and srcbit != 0L)
                    ret or dstbit
                else
                    ret and dstbit.inv()
            }
            return ret
        }

        /**
         * This method implements simple straight forward bit by bit calculation.
         * It is relatively slow for large amounts of data, but does not require
         * any preparation steps. As a result, it might be faster in some cases
         * then building a table required for faster calculation.
         *
         * Note: this implementation follows section 8 ("A Straightforward CRC Implementation")
         * of Ross N. Williams paper as even though final/sample implementation of this algorithm
         * provided near the end of that paper (and followed by most other implementations)
         * is a bit faster, it does not work for polynomials shorter then 8 bits.
         *
         * @param  crcParams CRC algorithm parameters
         * @param  data data for the CRC calculation
         * @return      the CRC value of the data provided
         */
        @JvmOverloads
        fun calculateCRC(
                crcParams: Parameters, data: ByteArray, init: Long = crcParams.init,
                offset: Int = 0, length: Int = data.size
        ): Long {
            var curValue = init
            val topBit = 1L shl crcParams.width - 1
            val mask = (topBit shl 1) - 1
            val end = offset + length
            for (i in offset until end) {
                var curByte = data[i].toLong() and 0x00FFL
                if (crcParams.isReflectIn) {
                    curByte = reflect(curByte, 8)
                }
                var j = 0x80
                while (j != 0) {
                    var bit = curValue and topBit
                    curValue = curValue shl 1
                    if (curByte and j.toLong() != 0L)
                        bit = bit xor topBit
                    if (bit != 0L)
                        curValue = curValue xor crcParams.polynomial
                    j = j shr 1
                }
            }
            if (crcParams.isReflectOut)
                curValue = reflect(curValue, crcParams.width)
            curValue = curValue xor crcParams.finalXor
            return curValue and mask
        }
    }
}