package com.hiddenramblings.tagmo.amiibo.tagdata

import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.charset.CharsetCompat
import com.hiddenramblings.tagmo.nfctech.NfcByte
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*

class AmiiboData(tagData: ByteArray) {
    private val context = TagMo.getContext()
    private val tagData: ByteBuffer
    fun array(): ByteArray {
        return tagData.array()
    }

    @set:Throws(NumberFormatException::class)
    var uID: ByteArray
        get() {
            val bytes = ByteArray(UID_LENGTH)
            tagData.position(UID_OFFSET)
            tagData[bytes, 0x0, UID_LENGTH - 1]
            bytes[0x8] = tagData[0x0]
            return bytes
        }
        set(value) {
            if (value.size != UID_LENGTH) throw NumberFormatException(context.getString(R.string.invalid_uid_length))
            tagData.put(0x0, value[0x8])
            tagData.position(UID_OFFSET)
            tagData.put(value, 0x0, UID_LENGTH - 1)
        }
    var amiiboID: Long
        get() = tagData.getLong(AMIIBO_ID_OFFSET)
        set(value) {
            tagData.putLong(AMIIBO_ID_OFFSET, value)
        }
    var settingFlags: BitSet
        get() = getBitSet(tagData, SETTING_FLAGS_OFFSET, SETTING_FLAGS_LENGTH)
        set(value) {
            putBitSet(tagData, SETTING_FLAGS_OFFSET, SETTING_FLAGS_LENGTH, value)
        }
    var isUserDataInitialized: Boolean
        get() = settingFlags[USER_DATA_INITIALIZED_OFFSET]
        set(value) {
            val bitSet = settingFlags
            bitSet[USER_DATA_INITIALIZED_OFFSET] = value
            settingFlags = bitSet
        }
    var isAppDataInitialized: Boolean
        get() = settingFlags[APP_DATA_INITIALIZED_OFFSET]
        set(value) {
            val bitSet = settingFlags
            bitSet[APP_DATA_INITIALIZED_OFFSET] = value
            settingFlags = bitSet
        }

    @Throws(NumberFormatException::class)
    fun checkCountryCode(value: Int) {
        if (value < 0 || value > 255) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var countryCode: Int
        get() {
            val value: Int = tagData[COUNTRY_CODE].toInt() and 0xFF
            checkCountryCode(value)
            return value
        }
        set(value) {
            checkCountryCode(value)
            tagData.put(COUNTRY_CODE, value.toByte())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var initializedDate: Date
        get() = getDate(tagData, INIT_DATA_OFFSET)
        set(value) {
            putDate(tagData, INIT_DATA_OFFSET, value)
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var modifiedDate: Date
        get() = getDate(tagData, MODIFIED_DATA_OFFSET)
        set(value) {
            putDate(tagData, MODIFIED_DATA_OFFSET, value)
        }

    @get:Throws(UnsupportedEncodingException::class)
    var nickname: String
        get() = getString(tagData, NICKNAME_OFFSET, NICKNAME_LENGTH, CharsetCompat.UTF_16BE)
        set(value) {
            putString(tagData, NICKNAME_OFFSET, NICKNAME_LENGTH, CharsetCompat.UTF_16BE, value)
        }

    @get:Throws(UnsupportedEncodingException::class)
    var miiName: String
        get() = getString(tagData, MII_NAME_OFFSET, MII_NAME_LENGTH, CharsetCompat.UTF_16LE)
        set(value) {
            putString(tagData, MII_NAME_OFFSET, MII_NAME_LENGTH, CharsetCompat.UTF_16LE, value)
        }
    var titleID: Long
        get() = tagData.getLong(TITLE_ID_OFFSET)
        set(value) {
            tagData.putLong(TITLE_ID_OFFSET, value)
        }

    @Throws(NumberFormatException::class)
    fun checkWriteCount(value: Int) {
        if (value < WRITE_COUNT_MIN_VALUE || value > WRITE_COUNT_MAX_VALUE) throw NumberFormatException()
    }

    @set:Throws(NumberFormatException::class)
    var writeCount: Int
        get() = tagData.getShort(WRITE_COUNT_OFFSET).toInt() and 0xFFFF
        set(value) {
            checkWriteCount(value)
            tagData.putShort(WRITE_COUNT_OFFSET, value.toShort())
        }
    var appId: Int
        get() = tagData.getInt(APP_ID_OFFSET)
        set(value) {
            tagData.putInt(APP_ID_OFFSET, value)
        }

    @set:Throws(Exception::class)
    var appData: ByteArray
        get() = getBytes(tagData, APP_DATA_OFFSET, APP_DATA_LENGTH)
        set(value) {
            if (value.size != APP_DATA_LENGTH) throw IOException(context.getString(R.string.invalid_app_data))
            putBytes(tagData, APP_DATA_OFFSET, value)
        }

    init {
        if (tagData.size < NfcByte.TAG_DATA_SIZE) throw IOException(
            context.getString(
                R.string.invalid_data_size,
                tagData.size, NfcByte.TAG_DATA_SIZE
            )
        )
        this.tagData = ByteBuffer.wrap(tagData)
    }

    companion object {
        private const val UID_OFFSET = 0x1D4
        private const val UID_LENGTH = 0x9
        private const val AMIIBO_ID_OFFSET = 0x54
        private const val SETTING_FLAGS_OFFSET = 0x38 - 0xC
        private const val SETTING_FLAGS_LENGTH = 0x1
        private const val USER_DATA_INITIALIZED_OFFSET = 0x4
        private const val APP_DATA_INITIALIZED_OFFSET = 0x5
        private const val COUNTRY_CODE = 0x2D
        private const val INIT_DATA_OFFSET = 0x30
        private const val MODIFIED_DATA_OFFSET = 0x32
        private const val NICKNAME_OFFSET = 0x38
        private const val NICKNAME_LENGTH = 0x14
        private const val MII_NAME_OFFSET = 0x4C + 0x1A
        private const val MII_NAME_LENGTH = 0x14
        private const val TITLE_ID_OFFSET = 0xB6 - 0x8A + 0x80
        const val WRITE_COUNT_MIN_VALUE = 0
        public val WRITE_COUNT_MAX_VALUE: Int = Short.MAX_VALUE.toInt() and 0xFFFF
        private const val WRITE_COUNT_OFFSET = 0xB4
        private const val APP_ID_OFFSET = 0xB6
        private const val APP_DATA_OFFSET = 0xDC // 0xED
        private const val APP_DATA_LENGTH = 0xD8
        public val countryCodes = HashMap<Int, String>()

        init {
            countryCodes[0] = "Unset"

            //Japan
            countryCodes[1] = "Japan"

            //Americas
            countryCodes[8] = "Anguilla"
            countryCodes[9] = "Antigua and Barbuda"
            countryCodes[10] = "Argentina"
            countryCodes[11] = "Aruba"
            countryCodes[12] = "Bahamas"
            countryCodes[13] = "Barbados"
            countryCodes[14] = "Belize"
            countryCodes[15] = "Bolivia"
            countryCodes[16] = "Brazil"
            countryCodes[17] = "British Virgin Islands"
            countryCodes[18] = "Canada"
            countryCodes[19] = "Cayman Islands"
            countryCodes[20] = "Chile"
            countryCodes[21] = "Colombia"
            countryCodes[22] = "Costa Rica"
            countryCodes[23] = "Dominica"
            countryCodes[24] = "Dominican Republic"
            countryCodes[25] = "Ecuador"
            countryCodes[26] = "El Salvador"
            countryCodes[27] = "French Guiana"
            countryCodes[28] = "Grenada"
            countryCodes[29] = "Guadeloupe"
            countryCodes[30] = "Guatemala"
            countryCodes[31] = "Guyana"
            countryCodes[32] = "Haiti"
            countryCodes[33] = "Honduras"
            countryCodes[34] = "Jamaica"
            countryCodes[35] = "Martinique"
            countryCodes[36] = "Mexico"
            countryCodes[37] = "Monsterrat"
            countryCodes[38] = "Netherlands Antilles"
            countryCodes[39] = "Nicaragua"
            countryCodes[40] = "Panama"
            countryCodes[41] = "Paraguay"
            countryCodes[42] = "Peru"
            countryCodes[43] = "St. Kitts and Nevis"
            countryCodes[44] = "St. Lucia"
            countryCodes[45] = "St. Vincent and the Grenadines"
            countryCodes[46] = "Suriname"
            countryCodes[47] = "Trinidad and Tobago"
            countryCodes[48] = "Turks and Caicos Islands"
            countryCodes[49] = "United States"
            countryCodes[50] = "Uruguay"
            countryCodes[51] = "US Virgin Islands"
            countryCodes[52] = "Venezuela"

            //Europe & Africa
            countryCodes[64] = "Albania"
            countryCodes[65] = "Australia"
            countryCodes[66] = "Austria"
            countryCodes[67] = "Belgium"
            countryCodes[68] = "Bosnia and Herzegovina"
            countryCodes[69] = "Botswana"
            countryCodes[70] = "Bulgaria"
            countryCodes[71] = "Croatia"
            countryCodes[72] = "Cyprus"
            countryCodes[73] = "Czech Republic"
            countryCodes[74] = "Denmark"
            countryCodes[75] = "Estonia"
            countryCodes[76] = "Finland"
            countryCodes[77] = "France"
            countryCodes[78] = "Germany"
            countryCodes[79] = "Greece"
            countryCodes[80] = "Hungary"
            countryCodes[81] = "Iceland"
            countryCodes[82] = "Ireland"
            countryCodes[83] = "Italy"
            countryCodes[84] = "Latvia"
            countryCodes[85] = "Lesotho"
            countryCodes[86] = "Lichtenstein"
            countryCodes[87] = "Lithuania"
            countryCodes[88] = "Luxembourg"
            countryCodes[89] = "F.Y.R of Macedonia"
            countryCodes[90] = "Malta"
            countryCodes[91] = "Montenegro"
            countryCodes[92] = "Mozambique"
            countryCodes[93] = "Namibia"
            countryCodes[94] = "Netherlands"
            countryCodes[95] = "New Zealand"
            countryCodes[96] = "Norway"
            countryCodes[97] = "Poland"
            countryCodes[98] = "Portugal"
            countryCodes[99] = "Romania"
            countryCodes[100] = "Russia"
            countryCodes[101] = "Serbia"
            countryCodes[102] = "Slovakia"
            countryCodes[103] = "Slovenia"
            countryCodes[104] = "South Africa"
            countryCodes[105] = "Spain"
            countryCodes[106] = "Swaziland"
            countryCodes[107] = "Sweden"
            countryCodes[108] = "Switzerland"
            countryCodes[109] = "Turkey"
            countryCodes[110] = "United Kingdom"
            countryCodes[111] = "Zambia"
            countryCodes[112] = "Zimbabwe"
            countryCodes[113] = "Azerbaijan"
            countryCodes[114] = "Mauritania (Islamic Republic of Mauritania)"
            countryCodes[115] = "Mali (Republic of Mali)"
            countryCodes[116] = "Niger (Republic of Niger)"
            countryCodes[117] = "Chad (Republic of Chad)"
            countryCodes[118] = "Sudan (Republic of the Sudan)"
            countryCodes[119] = "Eritrea (State of Eritrea)"
            countryCodes[120] = "Djibouti (Republic of Djibouti)"
            countryCodes[121] = "Somalia (Somali Republic)"

            //Southeast Asia
            countryCodes[128] = "Taiwan"
            countryCodes[136] = "South Korea"
            countryCodes[144] = "Hong Kong"
            countryCodes[145] = "Macao"
            countryCodes[152] = "Indonesia"
            countryCodes[153] = "Singapore"
            countryCodes[154] = "Thailand"
            countryCodes[155] = "Philippines"
            countryCodes[156] = "Malaysia"
            countryCodes[160] = "China"

            //Middle East
            countryCodes[168] = "U.A.E."
            countryCodes[169] = "India"
            countryCodes[170] = "Egypt"
            countryCodes[171] = "Oman"
            countryCodes[172] = "Qatar"
            countryCodes[173] = "Kuwait"
            countryCodes[174] = "Saudi Arabia"
            countryCodes[175] = "Syria"
            countryCodes[176] = "Bahrain"
            countryCodes[177] = "Jordan"
        }

        private fun getBytes(bb: ByteBuffer, offset: Int, length: Int): ByteArray {
            val bytes = ByteArray(length)
            bb.position(offset)
            bb[bytes]
            return bytes
        }

        private fun putBytes(bb: ByteBuffer, offset: Int, bytes: ByteArray) {
            bb.position(offset)
            bb.put(bytes)
        }

        private fun getBitSet(bb: ByteBuffer, offset: Int, length: Int): BitSet {
            val bitSet = BitSet(length * 8)
            val bytes = getBytes(bb, offset, length)
            for (i in bytes.indices) {
                for (j in 0..7) {
                    bitSet[i * 8 + j] = bytes[i].toInt() shr j and 1 == 1
                }
            }
            return bitSet
        }

        private fun putBitSet(bb: ByteBuffer, offset: Int, length: Int, bitSet: BitSet) {
            val bytes = ByteArray(length)
            for (i in bytes.indices) {
                for (j in 0..7) {
                    val set = bitSet[i * 8 + j]
                    bytes[i] =
                        (if (set) bytes[i].toInt() or (1 shl j)
                        else bytes[i].toInt() and (1 shl j).inv()).toByte()
                }
            }
            putBytes(bb, offset, bytes)
        }

        private fun getDate(bbuf: ByteBuffer, offset: Int): Date {
            val bb = ByteBuffer.wrap(getBytes(bbuf, offset, 0x2))
            val date = bb.short.toInt()
            val year = (date and 0xFE00 shr 9) + 2000
            val month = (date and 0x01E0 shr 5) - 1
            val day = date and 0x1F
            val calendar = Calendar.getInstance()
            calendar.isLenient = false
            calendar[Calendar.YEAR] = year
            calendar[Calendar.MONTH] = month
            calendar[Calendar.DAY_OF_MONTH] = day
            return calendar.time
        }

        private fun putDate(bbuf: ByteBuffer, offset: Int, date: Date) {
            val calendar: Calendar = GregorianCalendar()
            calendar.time = date
            val year = calendar[Calendar.YEAR] - 2000
            val month = calendar[Calendar.MONTH] + 1
            val day = calendar[Calendar.DAY_OF_MONTH]
            val bb = ByteBuffer.allocate(2)
            bb.putShort((year shl 9 or (month shl 5) or day).toShort())
            putBytes(bbuf, offset, bb.array())
        }

        private fun getString(bb: ByteBuffer, offset: Int, length: Int, charset: Charset): String {
            bb.position(offset)

            // Find the position of the first null terminator
            var i: Int
            if (charset === CharsetCompat.UTF_16BE || charset === CharsetCompat.UTF_16LE) {
                i = 0
                while (i < length / 2 && bb.short.toInt() != 0) {
                    i++
                }
                i *= 2
            } else {
                i = 0
                while (i < length && bb.get().toInt() != 0) {
                    i++
                }
            }
            return charset.decode(ByteBuffer.wrap(getBytes(bb, offset, i))).toString()
        }

        private fun putString(
            bb: ByteBuffer,
            offset: Int,
            length: Int,
            charset: Charset,
            text: String
        ) {
            val bytes = ByteArray(length)
            val bytes2 = charset.encode(text).array()
            System.arraycopy(bytes2, 0, bytes, 0, bytes2.size)
            putBytes(bb, offset, bytes)
        }
    }
}