package com.hiddenramblings.tagmo.amiibo.games

import com.hiddenramblings.tagmo.eightbit.io.Debug
import org.json.JSONArray
import org.json.JSONException

class GameTitles(val manager: GamesManager, val name: String, jsonArray: JSONArray) {
    private val gameIds = ArrayList<Long>()

    init {
        for (i in 0 until jsonArray.length()) {
            try {
                val gameId = jsonArray.getString(i)
                gameIds.add(java.lang.Long.decode("0x$gameId"))
            } catch (e: JSONException) {
                Debug.warn(e)
            }
        }
    }
}