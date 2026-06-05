package com.hiddenramblings.tagmo.amiibo.games

import android.content.Context
import android.net.Uri
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.ParseException

class GamesManager {
    private val games3DS = HashMap<Long, Games3DS>()
    private val gamesWiiU = HashMap<Long, GamesWiiU>()
    private val gamesSwitch = HashMap<Long, GamesSwitch>()
    private val games = HashMap<String, GameTitles>()

    fun getGamesCompatibility(prefs: Preferences, amiiboId: Long): String {
        val usage = StringBuilder()
        if (prefs.showCompat3DS()) {
            val amiibo3DS = games3DS[amiiboId]
            appendCompatibilitySection(
                usage,
                TagMo.appContext.getString(R.string.games_ds),
                TagMo.appContext.getString(R.string.no_games_ds),
                amiibo3DS?.stringList
            )
        }
        if (prefs.showCompatWiiU()) {
            val amiiboWiiU = gamesWiiU[amiiboId]
            appendCompatibilitySection(
                usage,
                TagMo.appContext.getString(R.string.games_wiiu),
                TagMo.appContext.getString(R.string.no_games_wiiu),
                amiiboWiiU?.stringList
            )
        }
        if (prefs.showCompatSwitch()) {
            val amiiboSwitch = gamesSwitch[amiiboId]
            appendCompatibilitySection(
                usage,
                TagMo.appContext.getString(R.string.games_nx),
                TagMo.appContext.getString(R.string.no_games_nx),
                amiiboSwitch?.stringList
            )
        }
        return usage.toString()
    }

    private fun appendCompatibilitySection(
        usage: StringBuilder,
        title: String,
        emptyTitle: String,
        gamesList: String?
    ) {
        if (usage.isNotEmpty()) usage.append(Debug.separator).append(Debug.separator)
        val games = gamesList
            ?.split(Debug.separator)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        if (games.isEmpty()) {
            usage.append(emptyTitle)
            return
        }
        usage.append(title)
        games.forEach { game ->
            usage.append(Debug.separator).append("- ").append(game)
        }
    }

    val gameTitles: Collection<GameTitles>
        get() = games.values

    fun getCompatibleAmiiboCount(manager: AmiiboManager, name: String?): Int {
        return getGameAmiiboIds(manager, name).size
    }

    fun getGameCompatibilityPlatforms(name: String?): Set<GamePlatform> {
        if (name.isNullOrBlank()) return emptySet()
        val platforms = linkedSetOf<GamePlatform>()
        if (games3DS.values.any { it.hasUsage(name) }) platforms.add(GamePlatform.THREE_DS)
        if (gamesWiiU.values.any { it.hasUsage(name) }) platforms.add(GamePlatform.WII_U)
        if (gamesSwitch.values.any { it.hasUsage(name) }) platforms.add(GamePlatform.SWITCH)
        return platforms
    }

    fun getGameAmiiboIds(manager: AmiiboManager, name: String?): ArrayList<Long> {
        val amiiboIds = ArrayList<Long>()
        for (amiibo in manager.amiibos.values) {
            val amiibo3DS = games3DS[amiibo.id]
            val amiiboWiiU = gamesWiiU[amiibo.id]
            val amiiboSwitch = gamesSwitch[amiibo.id]
            if (amiibo3DS?.hasUsage(name) == true) {
                amiiboIds.add(amiibo.id)
                continue
            }
            if (amiiboWiiU?.hasUsage(name) == true) {
                amiiboIds.add(amiibo.id)
                continue
            }
            if (amiiboSwitch?.hasUsage(name) == true) {
                amiiboIds.add(amiibo.id)
            }
        }
        return amiiboIds
    }

    fun isGameSupported(amiibo: Amiibo, name: String?): Boolean {
        val amiibo3DS = games3DS[amiibo.id]
        if (amiibo3DS?.hasUsage(name) == true) return true
        val amiiboWiiU = gamesWiiU[amiibo.id]
        if (amiiboWiiU?.hasUsage(name) == true) return true
        val amiiboSwitch = gamesSwitch[amiibo.id]
        return amiiboSwitch?.hasUsage(name) == true
    }

    companion object {
        private const val GAMES_DATABASE_FILE = "games_info.json"

        @Throws(IOException::class, JSONException::class, ParseException::class)
        fun parse(context: Context, uri: Uri?): GamesManager? {
            return uri?.let { stream ->
                context.contentResolver.openInputStream(stream).use { inputSteam ->
                    inputSteam?.let { parse(it) }
                }
            }
        }

        @Throws(IOException::class, JSONException::class, ParseException::class)
        fun parse(inputStream: InputStream): GamesManager {
            val data = ByteArray(inputStream.available())
            inputStream.read(data)
            inputStream.close()
            return parse(String(data))
        }

        @Throws(JSONException::class, OutOfMemoryError::class)
        fun parse(json: String): GamesManager {
            return parse(JSONObject(json))
        }

        @Throws(JSONException::class)
        fun parse(json: JSONObject): GamesManager {
            val manager = GamesManager()
            val amiibosJSON = json.getJSONObject("amiibos")
            val amiiboIterator = amiibosJSON.keys()
            while (amiiboIterator.hasNext()) {
                val amiiboKey = amiiboIterator.next()
                val amiiboId = java.lang.Long.decode(amiiboKey)
                val amiiboJSON = amiibosJSON.getJSONObject(amiiboKey)
                val amiibo3DS = ArrayList<String?>()
                val games3DSJSON = amiiboJSON.getJSONArray("games3DS")
                for (i in 0 until games3DSJSON.length()) {
                    val game = games3DSJSON.getJSONObject(i)
                    val name = game.getString("gameName")
                    amiibo3DS.add(name)
                    val gameTitles = GameTitles(
                        manager, name, game.getJSONArray("gameID")
                    )
                    if (!manager.games.containsKey(name)) manager.games[name] = gameTitles
                }
                val games3DS = Games3DS(manager, amiiboId, amiibo3DS)
                manager.games3DS[amiiboId] = games3DS
                val amiiboWiiU = ArrayList<String?>()
                val gamesWiiUJSON = amiiboJSON.getJSONArray("gamesWiiU")
                for (i in 0 until gamesWiiUJSON.length()) {
                    val game = gamesWiiUJSON.getJSONObject(i)
                    val name = game.getString("gameName")
                    amiiboWiiU.add(name)
                    val gameTitles = GameTitles(
                        manager, name, game.getJSONArray("gameID")
                    )
                    if (!manager.games.containsKey(name)) manager.games[name] = gameTitles
                }
                val gamesWiiU = GamesWiiU(manager, amiiboId, amiiboWiiU)
                manager.gamesWiiU[amiiboId] = gamesWiiU
                val amiiboSwitch = ArrayList<String?>()
                val gamesSwitchJSON = amiiboJSON.getJSONArray("gamesSwitch")
                for (i in 0 until gamesSwitchJSON.length()) {
                    val game = gamesSwitchJSON.getJSONObject(i)
                    val name = game.getString("gameName")
                    amiiboSwitch.add(name)
                    val gameTitles = GameTitles(
                        manager, name, game.getJSONArray("gameID")
                    )
                    if (!manager.games.containsKey(name)) manager.games[name] = gameTitles
                }
                val gamesSwitch2JSON = amiiboJSON.optJSONArray("gamesSwitch2")
                if (null != gamesSwitch2JSON) {
                    for (i in 0 until gamesSwitch2JSON.length()) {
                        val game = gamesSwitch2JSON.getJSONObject(i)
                        val name = game.getString("gameName")
                        amiiboSwitch.add(name)
                        val gameTitles = GameTitles(
                            manager, name, game.getJSONArray("gameID")
                        )
                        if (!manager.games.containsKey(name)) manager.games[name] = gameTitles
                    }
                }
                val gamesSwitch = GamesSwitch(manager, amiiboId, amiiboSwitch)
                manager.gamesSwitch[amiiboId] = gamesSwitch
            }
            return manager
        }

        @Throws(IOException::class, JSONException::class, ParseException::class)
        fun getDefaultGamesManager(context: Context): GamesManager {
            return parse(context.resources.openRawResource(R.raw.games_info))
        }

        @Throws(IOException::class, JSONException::class, ParseException::class)
        fun getGamesManager(context: Context): GamesManager {
            var gamesManager: GamesManager?
            if (File(TagMo.downloadDir, GAMES_DATABASE_FILE).exists()) {
                try {
                    gamesManager = parse(context.openFileInput(GAMES_DATABASE_FILE))
                } catch (e: IOException) {
                    gamesManager = null
                    Debug.warn(R.string.error_amiibo_parse, e)
                } catch (e: JSONException) {
                    gamesManager = null
                    Debug.warn(R.string.error_amiibo_parse, e)
                } catch (e: ParseException) {
                    gamesManager = null
                    Debug.warn(R.string.error_amiibo_parse, e)
                }
            } else {
                gamesManager = null
            }
            return gamesManager ?: getDefaultGamesManager(context)
        }
    }

    enum class GamePlatform {
        THREE_DS, WII_U, SWITCH
    }
}
