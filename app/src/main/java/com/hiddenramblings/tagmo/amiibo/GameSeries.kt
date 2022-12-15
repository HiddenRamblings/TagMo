package com.hiddenramblings.tagmo.amiibo

class GameSeries(val manager: AmiiboManager, val id: Long, val name: String) :
    Comparable<GameSeries> {
    constructor(manager: AmiiboManager, id: String, name: String) : this(manager, hexToId(id), name)

    override fun compareTo(other: GameSeries): Int {
        return name.compareTo(other.name)
    }

    companion object {
        const val MASK = -0x40000000000000L
        const val BITSHIFT = 4 * 13
        fun hexToId(value: String): Long {
            return java.lang.Long.decode(value) shl BITSHIFT and MASK
        }
    }
}