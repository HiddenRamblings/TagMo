package com.hiddenramblings.tagmo.amiibo.games

import android.content.Context
import android.net.Uri
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
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

    fun getGamesCompatibility(amiiboId: Long): String {
        val usage = StringBuilder()
        val amiibo3DS = games3DS[amiiboId]
        if (null != amiibo3DS && amiibo3DS.games!!.isNotEmpty()) {
            usage.append("\n3DS:")
            usage.append(amiibo3DS.stringList)
            usage.append("\n")
        }
        val amiiboWiiU = gamesWiiU[amiiboId]
        if (null != amiiboWiiU && amiiboWiiU.games!!.isNotEmpty()) {
            usage.append("\nWiiU:")
            usage.append(amiiboWiiU.stringList)
            usage.append("\n")
        }
        val amiiboSwitch = gamesSwitch[amiiboId]
        if (null != amiiboSwitch && amiiboSwitch.games!!.isNotEmpty()) {
            usage.append("\nSwitch:")
            usage.append(amiiboSwitch.stringList)
            usage.append("\n")
        }
        return usage.toString()
    }

    val gameTitles: Collection<GameTitles>
        get() = games.values

    fun getGameAmiiboIds(manager: AmiiboManager, name: String?): ArrayList<Long> {
        val amiiboIds = ArrayList<Long>()
        for (amiibo in manager.amiibos.values) {
            val amiibo3DS = games3DS[amiibo.id]
            val amiiboWiiU = gamesWiiU[amiibo.id]
            val amiiboSwitch = gamesSwitch[amiibo.id]
            if (null != amiibo3DS && amiibo3DS.hasUsage(name)) {
                amiiboIds.add(amiibo.id)
                continue
            }
            if (null != amiiboWiiU && amiiboWiiU.hasUsage(name)) {
                amiiboIds.add(amiibo.id)
                continue
            }
            if (null != amiiboSwitch && amiiboSwitch.hasUsage(name)) {
                amiiboIds.add(amiibo.id)
            }
        }
        return amiiboIds
    }

    fun isGameSupported(amiibo: Amiibo, name: String?): Boolean {
        val amiibo3DS = games3DS[amiibo.id]
        if (null != amiibo3DS && amiibo3DS.hasUsage(name)) return true
        val amiiboWiiU = gamesWiiU[amiibo.id]
        if (null != amiiboWiiU && amiiboWiiU.hasUsage(name)) return true
        val amiiboSwitch = gamesSwitch[amiibo.id]
        return null != amiiboSwitch && amiiboSwitch.hasUsage(name)
    }

    companion object {
        private const val GAMES_DATABASE_FILE = "games_info.json"

        @Throws(IOException::class, JSONException::class, ParseException::class)
        fun parse(context: Context, uri: Uri?): GamesManager {
            context.contentResolver.openInputStream(uri!!).use { inputSteam ->
                return parse(
                    inputSteam!!
                )
            }
        }

        @Throws(IOException::class, JSONException::class, ParseException::class)
        fun parse(inputStream: InputStream): GamesManager {
            val data = ByteArray(inputStream.available())
            inputStream.read(data)
            inputStream.close()
            return parse(String(data))
        }

        @Throws(JSONException::class)
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
            if (File(Storage.getDownloadDir("TagMo"), GAMES_DATABASE_FILE).exists()) {
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
}