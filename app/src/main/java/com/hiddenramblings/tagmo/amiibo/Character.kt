package com.hiddenramblings.tagmo.amiibo

class Character(val manager: AmiiboManager, val id: Long, val name: String) :
    Comparable<Character> {
    constructor(manager: AmiiboManager, id: String, name: String) : this(
        manager, hexToId(id), name
    )

    private val gameSeriesId: Long
        get() = id and GameSeries.MASK
    val gameSeries: GameSeries?
        get() = manager.gameSeries[gameSeriesId]

    override fun compareTo(other: Character): Int {
        return name.compareTo(other.name)
    }

    companion object {
        const val MASK = -0x1000000000000L
        const val BITSHIFT = 4 * 12
        fun hexToId(value: String): Long {
            return java.lang.Long.decode(value) shl BITSHIFT and MASK
        }
    }
}