package com.hiddenramblings.tagmo.amiibo.games

import com.hiddenramblings.tagmo.eightbit.io.Debug

class Games3DS(val manager: GamesManager, val id: Long, val games: ArrayList<String?>?) {
    val stringList: String
        get() {
            val usage = StringBuilder()
            games?.forEach { game ->
                if (usage.isNotEmpty()) usage.append(Debug.separator)
                usage.append(game)
            }
            return usage.toString()
        }

    fun hasUsage(name: String?): Boolean {
        return games?.contains(name) == true
    }
}