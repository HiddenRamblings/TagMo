package com.hiddenramblings.tagmo.amiibo

class AmiiboType(val manager: AmiiboManager, val id: Long, val name: String) :
    Comparable<AmiiboType> {
    constructor(manager: AmiiboManager, id: String, name: String) : this(
        manager, hexToId(id), name
    )

    override fun compareTo(other: AmiiboType): Int {
        return id.compareTo(other.id)
    }

    companion object {
        const val MASK = 0x000000FF00000000L
        const val BITSHIFT = 4 * 8
        fun hexToId(value: String): Long {
            return java.lang.Long.decode(value) shl BITSHIFT and MASK
        }
    }
}