package com.hiddenramblings.tagmo.amiibo.games

class GamesWiiU(val manager: GamesManager, val id: Long, val games: ArrayList<String?>?) {
    val stringList: String
        get() {
            val usage = StringBuilder()
            for (game in games!!) {
                if (usage.isEmpty()) usage.append("  ") else usage.append(", ")
                usage.append(game)
            }
            return usage.toString()
        }

    fun hasUsage(name: String?): Boolean {
        return games?.isNotEmpty() == true && games.contains(name)
    }
}