package com.hiddenramblings.tagmo.amiibo

class AmiiboSeries(val manager: AmiiboManager, val id: Long, val name: String) :
    Comparable<AmiiboSeries> {
    constructor(manager: AmiiboManager, id: String, name: String) : this(
        manager, hexToId(id), name
    )

    override fun compareTo(other: AmiiboSeries): Int {
        return name.compareTo(other.name)
    }

    companion object {
        const val MASK = 0x000000000000FF00L
        const val BITSHIFT = 4 * 2
        fun hexToId(value: String): Long {
            return java.lang.Long.decode(value) shl BITSHIFT and MASK
        }
    }
}