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

class AmiiboManager {
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
            releaseJSON.put(
                "na",
                if (null == amiibo.releaseDates?.northAmerica)
                    null
                else
                    iso8601.format(amiibo.releaseDates.northAmerica)
            )
            releaseJSON.put(
                "jp",
                if (null == amiibo.releaseDates?.japan)
                    null
                else
                    iso8601.format(amiibo.releaseDates.japan)
            )
            releaseJSON.put(
                "eu",
                if (null == amiibo.releaseDates?.europe)
                    null
                else
                    iso8601.format(amiibo.releaseDates.europe)
            )
            releaseJSON.put(
                "au",
                if (null == amiibo.releaseDates?.australia)
                    null
                else
                    iso8601.format(amiibo.releaseDates.australia)
            )
            amiiboJSON.put("release", releaseJSON)
            amiibosJSON.put(String.format("0x%016X", amiibo.id), amiiboJSON)
        }
        outputJSON.put("amiibos", amiibosJSON)
        val gameSeriesJSON = JSONObject()
        for ((_, gameSeries1: GameSeries) in gameSeries) {
            gameSeriesJSON.put(
                String.format(
                    "0x%03X", gameSeries1.id shr GameSeries.BITSHIFT
                ), gameSeries1.name
            )
        }
        outputJSON.put("game_series", gameSeriesJSON)
        val charactersJSON = JSONObject()
        for ((_, character) in characters) {
            charactersJSON.put(
                String.format(
                    "0x%04X", character.id shr Character.BITSHIFT
                ), character.name
            )
        }
        outputJSON.put("characters", charactersJSON)
        val amiiboTypesJSON = JSONObject()
        for ((_, amiiboType) in amiiboTypes) {
            amiiboTypesJSON.put(
                String.format(
                    "0x%02X", amiiboType.id shr AmiiboType.BITSHIFT
                ), amiiboType.name
            )
        }
        outputJSON.put("types", amiiboTypesJSON)
        val amiiboSeriesJSON = JSONObject()
        for ((_, amiiboSeries1) in amiiboSeries) {
            amiiboSeriesJSON.put(
                String.format(
                    "0x%02X", amiiboSeries1.id shr AmiiboSeries.BITSHIFT
                ), amiiboSeries1.name
            )
        }
        outputJSON.put("amiibo_series", amiiboSeriesJSON)
        return outputJSON
    }

    companion object {
        const val AMIIBO_DATABASE_FILE = "amiibo.json"
        const val RENDER_RAW = "https://raw.githubusercontent.com/8bitDream/AmiiboAPI/render/"
        const val AMIIBO_API = "https://amiiboapi.com/api/"
        @Throws(IOException::class, JSONException::class, ParseException::class)
        fun parse(context: Context, uri: Uri?): AmiiboManager {
            context.contentResolver.openInputStream(uri!!).use { inputSteam ->
                return parse(
                    inputSteam!!
                )
            }
        }

        @Throws(JSONException::class, ParseException::class)
        fun parse(json: JSONObject): AmiiboManager {
            val iso8601: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val manager = AmiiboManager()
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
                    val amiibo = Amiibo(manager, key, name, releaseDates)
                    manager.amiibos[amiibo.id] = amiibo
                }
            }
            val gameSeriesJSON = json.getJSONObject("game_series")
            run {
                val iterator = gameSeriesJSON.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val name = gameSeriesJSON.getString(key)
                    val gameSeries = GameSeries(manager, key, name)
                    manager.gameSeries[gameSeries.id] = gameSeries
                }
            }
            val characterJSON = json.getJSONObject("characters")
            run {
                val iterator = characterJSON.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val name = characterJSON.getString(key)
                    val character = Character(manager, key, name)
                    manager.characters[character.id] = character
                }
            }
            val amiiboTypeJSON = json.getJSONObject("types")
            run {
                val iterator = amiiboTypeJSON.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val name = amiiboTypeJSON.getString(key)
                    val amiiboType = AmiiboType(manager, key, name)
                    manager.amiiboTypes[amiiboType.id] = amiiboType
                }
            }
            val amiiboSeriesJSON = json.getJSONObject("amiibo_series")
            val iterator = amiiboSeriesJSON.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val name = amiiboSeriesJSON.getString(key)
                val amiiboSeries = AmiiboSeries(manager, key, name)
                manager.amiiboSeries[amiiboSeries.id] = amiiboSeries
            }
            return manager
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
            val manager = AmiiboManager()
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
                val amiibo = Amiibo(manager, key, name, releaseDates)
                manager.amiibos[amiibo.id] = amiibo
                val characterId = amiibo.characterId
                if (!manager.characters.containsKey(characterId)) {
                    val character = Character(
                        manager, characterId, amiiboJSON.getString("character")
                    )
                    manager.characters[characterId] = character
                }
                val gameSeriesId = amiibo.gameSeriesId
                if (!manager.gameSeries.containsKey(gameSeriesId)) {
                    val gameSeries = GameSeries(
                        manager, gameSeriesId, amiiboJSON.getString("gameSeries")
                    )
                    manager.gameSeries[gameSeriesId] = gameSeries
                }
                val amiiboTypeId = amiibo.amiiboTypeId
                if (!manager.amiiboTypes.containsKey(amiiboTypeId)) {
                    val amiiboType = AmiiboType(manager, amiiboTypeId, amiiboJSON.getString("type"))
                    manager.amiiboTypes[amiiboTypeId] = amiiboType
                }
                val amiiboSeriesId = amiibo.amiiboSeriesId
                if (!manager.amiiboSeries.containsKey(amiiboSeriesId)) {
                    val amiiboSeries = AmiiboSeries(
                        manager, amiiboSeriesId, amiiboJSON.getString("amiiboSeries")
                    )
                    manager.amiiboSeries[amiiboSeriesId] = amiiboSeries
                }
            }
            return manager
        }

        @Throws(JSONException::class, ParseException::class)
        fun parseAmiiboAPI(string: String): AmiiboManager {
            return parseAmiiboAPI(JSONObject(string))
        }

        @Throws(JSONException::class, IOException::class)
        fun saveDatabase(amiiboManager: AmiiboManager, outputStream: OutputStream?) {
            var streamWriter: OutputStreamWriter? = null
            try {
                streamWriter = OutputStreamWriter(outputStream)
                streamWriter.write(amiiboManager.toJSON().toString())
            } finally {
                try {
                    streamWriter?.close()
                } catch (e: IOException) {
                    Debug.info(e)
                }
                outputStream?.flush()
            }
        }

        @Throws(IOException::class, JSONException::class)
        fun saveDatabase(amiiboManager: AmiiboManager, context: Context) {
            var outputStream: OutputStream? = null
            try {
                outputStream = context.openFileOutput(AMIIBO_DATABASE_FILE, Context.MODE_PRIVATE)
                saveDatabase(amiiboManager, outputStream)
            } finally {
                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    Debug.info(e)
                }
            }
        }

        private fun readDatabase(context: Context): String? {
            val database = StringBuilder()
            var inputStream: InputStream? = null
            var reader: BufferedReader? = null
            try {
                inputStream = context.openFileInput(AMIIBO_DATABASE_FILE)
                reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    database.append(line).append('\n')
                }
            } catch (ex: IOException) {
                return null
            } finally {
                try {
                    reader?.close()
                } catch (e: IOException) {
                    Debug.info(e)
                }
                try {
                    inputStream?.close()
                } catch (e: IOException) {
                    Debug.info(e)
                }
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
            if (amiiboHexId?.length!! < 12) return false
            val spoofRange = amiiboHexId.substring(8, 12).lowercase()
            return (!amiiboHexId.startsWith("00000000")
                    && (spoofRange == "0000" || spoofRange == "ffff"))
        }

        fun listAmiibos(
            keyManager: KeyManager?, rootFolder: File?, recursiveFiles: Boolean
        ): ArrayList<AmiiboFile?> {
            val amiiboFiles = ArrayList<AmiiboFile?>()
            val files = rootFolder?.listFiles { _: File?, name: String -> binFileMatches(name) }
            if (!files.isNullOrEmpty()) {
                files.forEach {
                    if (Thread.currentThread().isInterrupted) return amiiboFiles
                    try {
                        val data = TagArray.getValidatedFile(keyManager, it)
                        if (null != data)
                            amiiboFiles.add(AmiiboFile(it, Amiibo.dataToId(data), data))
                    } catch (e: Exception) {
                        Debug.info(e)
                    }
                }
            } else if (recursiveFiles) {
                val directories = rootFolder?.listFiles()
                if (directories == null || directories.isEmpty()) return amiiboFiles
                directories.forEach {
                    if (it.isDirectory)
                        amiiboFiles.addAll(listAmiibos(keyManager, it, true))
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
            val uris = AmiiboDocument(context!!).listFiles(rootFolder.uri, recursiveFiles)
            if (uris.isEmpty()) return amiiboFiles
            uris.forEach {
                if (Thread.currentThread().isInterrupted) return amiiboFiles
                try {
                    val data = TagArray.getValidatedDocument(keyManager, it)
                    if (null != data) {
                        amiiboFiles.add(AmiiboFile(
                            DocumentFile.fromSingleUri(context, it), Amiibo.dataToId(data), data
                        ))
                    }
                } catch (e: Exception) {
                    Debug.info(e)
                }
            }
            return amiiboFiles
        }
    }
}