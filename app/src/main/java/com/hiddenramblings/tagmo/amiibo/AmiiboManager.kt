package com.hiddenramblings.tagmo.amiibo

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.nfctech.TagArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object AmiiboManager {
    const val AMIIBO_DATABASE_FILE = "amiibo.json"
    const val RENDER_RAW = "https://raw.githubusercontent.com/8bitDream/AmiiboAPI/render"
    const val AMIIBO_API = "https://amiiboapi.com/api"
    const val AMIIBO_RAW = "https://raw.githubusercontent.com/N3evin/AmiiboAPI/master"

    val amiibos = HashMap<Long, Amiibo>()
    val characters = HashMap<Long, Character>()
    val gameSeries = HashMap<Long, GameSeries>()
    val amiiboTypes = HashMap<Long, AmiiboType>()
    val amiiboSeries = HashMap<Long, AmiiboSeries>()

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val iso8601: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputJSON = JSONObject()
        val amiibosJSON = JSONObject()
        for ((_, amiibo) in amiibos) {
            val amiiboJSON = JSONObject()
            amiiboJSON.put("name", amiibo.name)
            val releaseJSON = JSONObject()
            releaseJSON.put("na",
                amiibo.releaseDates?.northAmerica?.let { iso8601.format(it) }
            )
            releaseJSON.put("jp",
                amiibo.releaseDates?.japan?.let { iso8601.format(it) }
            )
            releaseJSON.put("eu",
                amiibo.releaseDates?.europe?.let { iso8601.format(it) }
            )
            releaseJSON.put("au",
                amiibo.releaseDates?.australia?.let { iso8601.format(it) }
            )
            amiiboJSON.put("release", releaseJSON)
            amiibosJSON.put(String.format("0x%016X", amiibo.id), amiiboJSON)
        }
        outputJSON.put("amiibos", amiibosJSON)
        val gameSeriesJSON = JSONObject()
        for ((_, gameSeries1: GameSeries) in gameSeries) {
            gameSeriesJSON.put(
                String.format("0x%03X", gameSeries1.id shr GameSeries.BITSHIFT), gameSeries1.name
            )
        }
        outputJSON.put("game_series", gameSeriesJSON)
        val charactersJSON = JSONObject()
        for ((_, character) in characters) {
            charactersJSON.put(
                String.format("0x%04X", character.id shr Character.BITSHIFT), character.name
            )
        }
        outputJSON.put("characters", charactersJSON)
        val amiiboTypesJSON = JSONObject()
        for ((_, amiiboType) in amiiboTypes) {
            amiiboTypesJSON.put(
                String.format("0x%02X", amiiboType.id shr AmiiboType.BITSHIFT), amiiboType.name
            )
        }
        outputJSON.put("types", amiiboTypesJSON)
        val amiiboSeriesJSON = JSONObject()
        for ((_, amiiboSeries1) in amiiboSeries) {
            amiiboSeriesJSON.put(
                String.format("0x%02X", amiiboSeries1.id shr AmiiboSeries.BITSHIFT), amiiboSeries1.name
            )
        }
        outputJSON.put("amiibo_series", amiiboSeriesJSON)
        return outputJSON
    }

    @Throws(IOException::class, JSONException::class, ParseException::class)
    fun parse(context: Context, uri: Uri?): AmiiboManager? {
        return uri?.let { stream ->
            context.contentResolver.openInputStream(stream).use { inputSteam ->
                inputSteam?.let { parse(it) }
            }
        }
    }

    @Throws(JSONException::class, ParseException::class)
    fun parse(json: JSONObject): AmiiboManager {
        val iso8601: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val amiibosJSON = json.getJSONObject("amiibos")
        run {
            val iterator = amiibosJSON.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val amiiboJSON = amiibosJSON.getJSONObject(key)
                val name = amiiboJSON.getString("name")
                val releaseDatesJSON = amiiboJSON.getJSONObject("release")
                val naDate = if (releaseDatesJSON.isNull("na")) null else iso8601.parse(
                    releaseDatesJSON.getString("na")
                )
                val jpDate = if (releaseDatesJSON.isNull("jp")) null else iso8601.parse(
                    releaseDatesJSON.getString("jp")
                )
                val euDate = if (releaseDatesJSON.isNull("eu")) null else iso8601.parse(
                    releaseDatesJSON.getString("eu")
                )
                val auDate = if (releaseDatesJSON.isNull("au")) null else iso8601.parse(
                    releaseDatesJSON.getString("au")
                )
                val releaseDates = AmiiboReleaseDates(naDate, jpDate, euDate, auDate)
                val amiibo = Amiibo(this, key, name, releaseDates)
                amiibos[amiibo.id] = amiibo
            }
        }
        val gameSeriesJSON = json.getJSONObject("game_series")
        run {
            val iterator = gameSeriesJSON.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val name = gameSeriesJSON.getString(key)
                val series = GameSeries(this, key, name)
                gameSeries[series.id] = series
            }
        }
        val characterJSON = json.getJSONObject("characters")
        run {
            val iterator = characterJSON.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val name = characterJSON.getString(key)
                val character = Character(this, key, name)
                characters[character.id] = character
            }
        }
        val amiiboTypeJSON = json.getJSONObject("types")
        run {
            val iterator = amiiboTypeJSON.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val name = amiiboTypeJSON.getString(key)
                val amiiboType = AmiiboType(this, key, name)
                amiiboTypes[amiiboType.id] = amiiboType
            }
        }
        val amiiboSeriesJSON = json.getJSONObject("amiibo_series")
        val iterator = amiiboSeriesJSON.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val name = amiiboSeriesJSON.getString(key)
            val series = AmiiboSeries(this, key, name)
            amiiboSeries[series.id] = series
        }
        return this
    }

    @Throws(IOException::class, JSONException::class, ParseException::class)
    fun parse(inputStream: InputStream): AmiiboManager {
        val data = ByteArray(inputStream.available())
        inputStream.read(data)
        inputStream.close()
        return parse(JSONObject(String(data)))
    }

    @Throws(JSONException::class, ParseException::class)
    fun parse(string: String): AmiiboManager {
        return parse(JSONObject(string))
    }

    @Throws(JSONException::class, ParseException::class)
    fun parseAmiiboAPI(json: JSONObject): AmiiboManager {
        val iso8601: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val amiibosJSON = json.getJSONArray("amiibo")
        for (i in 0 until amiibosJSON.length()) {
            val amiiboJSON = amiibosJSON.getJSONObject(i)
            val key = ("0x" + amiiboJSON.getString("head")
                    + amiiboJSON.getString("tail"))
            val name = amiiboJSON.getString("name")
            val releaseDatesJSON = amiiboJSON.getJSONObject("release")
            val naDate = if (releaseDatesJSON.isNull("na")) null else iso8601.parse(
                releaseDatesJSON.getString("na")
            )
            val jpDate = if (releaseDatesJSON.isNull("jp")) null else iso8601.parse(
                releaseDatesJSON.getString("jp")
            )
            val euDate = if (releaseDatesJSON.isNull("eu")) null else iso8601.parse(
                releaseDatesJSON.getString("eu")
            )
            val auDate = if (releaseDatesJSON.isNull("au")) null else iso8601.parse(
                releaseDatesJSON.getString("au")
            )
            val releaseDates = AmiiboReleaseDates(naDate, jpDate, euDate, auDate)
            val amiibo = Amiibo(this, key, name, releaseDates)
            amiibos[amiibo.id] = amiibo
            val characterId = amiibo.characterId
            if (!characters.containsKey(characterId)) {
                val character = Character(
                    this, characterId, amiiboJSON.getString("character")
                )
                characters[characterId] = character
            }
            val gameSeriesId = amiibo.gameSeriesId
            if (!gameSeries.containsKey(gameSeriesId)) {
                val series = GameSeries(
                    this, gameSeriesId, amiiboJSON.getString("gameSeries")
                )
                gameSeries[gameSeriesId] = series
            }
            val amiiboTypeId = amiibo.amiiboTypeId
            if (!amiiboTypes.containsKey(amiiboTypeId)) {
                val amiiboType = AmiiboType(
                    this, amiiboTypeId, amiiboJSON.getString("type")
                )
                amiiboTypes[amiiboTypeId] = amiiboType
            }
            val amiiboSeriesId = amiibo.amiiboSeriesId
            if (!amiiboSeries.containsKey(amiiboSeriesId)) {
                val series = AmiiboSeries(
                    this, amiiboSeriesId, amiiboJSON.getString("amiiboSeries")
                )
                amiiboSeries[amiiboSeriesId] = series
            }
        }
        return this
    }

    @Throws(JSONException::class, ParseException::class)
    fun parseAmiiboAPI(string: String): AmiiboManager {
        return parseAmiiboAPI(JSONObject(string))
    }

    @Throws(JSONException::class, IOException::class)
    fun saveDatabase(amiiboManager: AmiiboManager, outputStream: OutputStream?) {
        OutputStreamWriter(outputStream).use {
            it.write(amiiboManager.toJSON().toString())
            it.flush()
        }
    }

    @Throws(IOException::class, JSONException::class)
    fun saveDatabase(amiiboManager: AmiiboManager, context: Context) {
        context.openFileOutput(AMIIBO_DATABASE_FILE, Context.MODE_PRIVATE).use {
            saveDatabase(amiiboManager, it)
        }
    }

    private fun readDatabase(context: Context): String? {
        val database = StringBuilder()
        try {
            context.openFileInput(AMIIBO_DATABASE_FILE).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        database.append(line).append('\n')
                    }
                }
            }
        } catch (ex: IOException) {
            return null
        }
        return if (database.isNotEmpty()) database.toString() else null
    }

    @Throws(IOException::class, JSONException::class, ParseException::class)
    fun getDefaultAmiiboManager(context: Context): AmiiboManager {
        return parse(context.resources.openRawResource(R.raw.amiibo))
    }

    @Throws(IOException::class, JSONException::class, ParseException::class)
    fun getAmiiboManager(context: Context): AmiiboManager {
        var amiiboManager: AmiiboManager?
        if (File(Storage.getDownloadDir("TagMo"), AMIIBO_DATABASE_FILE).exists()) {
            try {
                amiiboManager = parse(context.openFileInput(AMIIBO_DATABASE_FILE))
            } catch (e: IOException) {
                amiiboManager = null
                Debug.warn(R.string.error_amiibo_parse, e)
            } catch (e: JSONException) {
                amiiboManager = null
                Debug.warn(R.string.error_amiibo_parse, e)
            } catch (e: ParseException) {
                amiiboManager = null
                Debug.warn(R.string.error_amiibo_parse, e)
            }
        } else {
            val database = readDatabase(context)
            amiiboManager = if (null != database) parse(database) else null
        }
        if (null == amiiboManager) {
            amiiboManager = getDefaultAmiiboManager(context)
        }
        return amiiboManager
    }

    fun binFileMatches(name: String): Boolean {
        return name.lowercase().endsWith(".bin")
    }

    fun hasSpoofData(amiiboHexId: String?): Boolean {
        if (null == amiiboHexId || amiiboHexId.length < 12) return false
        val spoofRange = amiiboHexId.substring(8, 12).lowercase()
        return (!amiiboHexId.startsWith("00000000")
                && (spoofRange == "0000" || spoofRange == "ffff"))
    }

    fun listAmiiboFiles(
        keyManager: KeyManager?, rootFolder: File?, recursiveFiles: Boolean
    ): ArrayList<AmiiboFile?> {
        val amiiboFiles = ArrayList<AmiiboFile?>()
        val files = rootFolder?.listFiles { _: File?, name: String -> binFileMatches(name) }
        if (!files.isNullOrEmpty()) {
            files.forEach { file ->
                try {
                    TagArray.getValidatedFile(keyManager, file).also { data ->
                        data?.let { amiiboFiles.add(AmiiboFile(file, Amiibo.dataToId(it), it)) }
                    }
                } catch (e: Exception) {
                    Debug.info(e)
                }
            }
        } else if (recursiveFiles) {
            val directories = rootFolder?.listFiles()
            if (directories.isNullOrEmpty()) return amiiboFiles
            directories.forEach {
                if (it.isDirectory)
                    amiiboFiles.addAll(listAmiiboFiles(keyManager, it, true))
            }
        }
        return amiiboFiles
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    fun listAmiiboDocuments(
        context: Context?, keyManager: KeyManager?,
        rootFolder: DocumentFile, recursiveFiles: Boolean
    ): ArrayList<AmiiboFile?> {
        val amiiboFiles = ArrayList<AmiiboFile?>()
        val uris = context?.let { AmiiboDocument(it).listFiles(rootFolder.uri, recursiveFiles) }
        if (uris.isNullOrEmpty()) return amiiboFiles
        uris.forEach { uri ->
            try {
                TagArray.getValidatedDocument(keyManager, uri).also { data ->
                    data?.let {
                        amiiboFiles.add(AmiiboFile(
                            DocumentFile.fromSingleUri(context, uri), Amiibo.dataToId(it), it
                        ))
                    }
                }
            } catch (e: Exception) {
                Debug.info(e)
            }
        }
        return amiiboFiles
    }
}