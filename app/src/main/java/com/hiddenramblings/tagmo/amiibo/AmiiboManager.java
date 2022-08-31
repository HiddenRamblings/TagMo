package com.hiddenramblings.tagmo.amiibo;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class AmiiboManager {

    public static final String AMIIBO_DATABASE_FILE = "amiibo.json";

    public final HashMap<Long, Amiibo> amiibos = new HashMap<>();
    public final HashMap<Long, GameSeries> gameSeries = new HashMap<>();
    public final HashMap<Long, Character> characters = new HashMap<>();
    public final HashMap<Long, AmiiboType> amiiboTypes = new HashMap<>();
    public final HashMap<Long, AmiiboSeries> amiiboSeries = new HashMap<>();

    public static AmiiboManager parse(Context context, Uri uri)
            throws IOException, JSONException, ParseException {
        try (InputStream inputSteam = context.getContentResolver().openInputStream(uri)) {
            return parse(inputSteam);
        }
    }

    static AmiiboManager parse(InputStream inputStream) throws IOException, JSONException, ParseException {
        byte[] data = new byte[inputStream.available()];
        //noinspection ResultOfMethodCallIgnored
        inputStream.read(data);
        inputStream.close();

        return parse(new String(data));
    }

    static AmiiboManager parse(String json) throws JSONException, ParseException {
        return parse(new JSONObject(json));
    }

    static AmiiboManager parse(JSONObject json) throws JSONException, ParseException {
        final DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        AmiiboManager manager = new AmiiboManager();
        JSONObject amiibosJSON = json.getJSONObject("amiibos");
        for (Iterator<String> iterator = amiibosJSON.keys(); iterator.hasNext();) {
            String key = iterator.next();
            JSONObject amiiboJSON = amiibosJSON.getJSONObject(key);

            String name = amiiboJSON.getString("name");

            JSONObject releaseDatesJSON = amiiboJSON.getJSONObject("release");
            Date naDate = releaseDatesJSON.isNull("na") ? null : iso8601.parse(releaseDatesJSON.getString("na"));
            Date jpDate = releaseDatesJSON.isNull("jp") ? null : iso8601.parse(releaseDatesJSON.getString("jp"));
            Date euDate = releaseDatesJSON.isNull("eu") ? null : iso8601.parse(releaseDatesJSON.getString("eu"));
            Date auDate = releaseDatesJSON.isNull("au") ? null : iso8601.parse(releaseDatesJSON.getString("au"));
            AmiiboReleaseDates releaseDates = new AmiiboReleaseDates(naDate, jpDate, euDate, auDate);

            Amiibo amiibo = new Amiibo(manager, key, name, releaseDates);
            manager.amiibos.put(amiibo.id, amiibo);
        }

        JSONObject gameSeriesJSON = json.getJSONObject("game_series");
        for (Iterator<String> iterator = gameSeriesJSON.keys(); iterator.hasNext();) {
            String key = iterator.next();
            String name = gameSeriesJSON.getString(key);

            GameSeries gameSeries = new GameSeries(manager, key, name);
            manager.gameSeries.put(gameSeries.id, gameSeries);
        }

        JSONObject characterJSON = json.getJSONObject("characters");
        for (Iterator<String> iterator = characterJSON.keys(); iterator.hasNext();) {
            String key = iterator.next();
            String name = characterJSON.getString(key);

            Character character = new Character(manager, key, name);
            manager.characters.put(character.id, character);
        }

        JSONObject amiiboTypeJSON = json.getJSONObject("types");
        for (Iterator<String> iterator = amiiboTypeJSON.keys(); iterator.hasNext();) {
            String key = iterator.next();
            String name = amiiboTypeJSON.getString(key);

            AmiiboType amiiboType = new AmiiboType(manager, key, name);
            manager.amiiboTypes.put(amiiboType.id, amiiboType);
        }

        JSONObject amiiboSeriesJSON = json.getJSONObject("amiibo_series");
        for (Iterator<String> iterator = amiiboSeriesJSON.keys(); iterator.hasNext();) {
            String key = iterator.next();
            String name = amiiboSeriesJSON.getString(key);

            AmiiboSeries amiiboSeries = new AmiiboSeries(manager, key, name);
            manager.amiiboSeries.put(amiiboSeries.id, amiiboSeries);
        }

        return manager;
    }

    public static AmiiboManager parseAmiiboAPI(JSONObject json) throws JSONException, ParseException {
        final DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        AmiiboManager manager = new AmiiboManager();
        JSONArray amiibosJSON = json.getJSONArray("amiibo");
        for (int i = 0; i < amiibosJSON.length(); i++) {
            JSONObject amiiboJSON = amiibosJSON.getJSONObject(i);

            String key = "0x" + amiiboJSON.getString("head") + amiiboJSON.getString("tail");
            String name = amiiboJSON.getString("name");

            JSONObject releaseDatesJSON = amiiboJSON.getJSONObject("release");
            Date naDate = releaseDatesJSON.isNull("na") ? null : iso8601.parse(releaseDatesJSON.getString("na"));
            Date jpDate = releaseDatesJSON.isNull("jp") ? null : iso8601.parse(releaseDatesJSON.getString("jp"));
            Date euDate = releaseDatesJSON.isNull("eu") ? null : iso8601.parse(releaseDatesJSON.getString("eu"));
            Date auDate = releaseDatesJSON.isNull("au") ? null : iso8601.parse(releaseDatesJSON.getString("au"));
            AmiiboReleaseDates releaseDates = new AmiiboReleaseDates(naDate, jpDate, euDate, auDate);

            Amiibo amiibo = new Amiibo(manager, key, name, releaseDates);
            manager.amiibos.put(amiibo.id, amiibo);

            long gameSeriesId = amiibo.getGameSeriesId();
            if (!manager.gameSeries.containsKey(gameSeriesId)) {
                GameSeries gameSeries = new GameSeries(manager, gameSeriesId, amiiboJSON.getString("gameSeries"));
                manager.gameSeries.put(gameSeriesId, gameSeries);
            }

            long characterId = amiibo.getCharacterId();
            if (!manager.characters.containsKey(characterId)) {
                Character character = new Character(manager, characterId, amiiboJSON.getString("character"));
                manager.characters.put(characterId, character);
            }

            long amiiboTypeId = amiibo.getAmiiboTypeId();
            if (!manager.amiiboTypes.containsKey(amiiboTypeId)) {
                AmiiboType amiiboType = new AmiiboType(manager, amiiboTypeId, amiiboJSON.getString("type"));
                manager.amiiboTypes.put(amiiboTypeId, amiiboType);
            }

            long amiiboSeriesId = amiibo.getAmiiboSeriesId();
            if (!manager.amiiboSeries.containsKey(amiiboSeriesId)) {
                AmiiboSeries amiiboSeries = new AmiiboSeries(manager, amiiboSeriesId, amiiboJSON.getString("amiiboSeries"));
                manager.amiiboSeries.put(amiiboSeriesId, amiiboSeries);
            }
        }

        return manager;
    }

    public JSONObject toJSON() throws JSONException {
        final DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        JSONObject outputJSON = new JSONObject();

        JSONObject amiibosJSON = new JSONObject();
        for (Map.Entry<Long, Amiibo> entry : this.amiibos.entrySet()) {
            Amiibo amiibo = entry.getValue();

            JSONObject amiiboJSON = new JSONObject();
            amiiboJSON.put("name", amiibo.name);

            JSONObject releaseJSON = new JSONObject();
            releaseJSON.put("na", null == amiibo.releaseDates.northAmerica ? null : iso8601.format(amiibo.releaseDates.northAmerica));
            releaseJSON.put("jp", null == amiibo.releaseDates.japan ? null : iso8601.format(amiibo.releaseDates.japan));
            releaseJSON.put("eu", null == amiibo.releaseDates.europe ? null : iso8601.format(amiibo.releaseDates.europe));
            releaseJSON.put("au", null == amiibo.releaseDates.australia ? null : iso8601.format(amiibo.releaseDates.australia));
            amiiboJSON.put("release", releaseJSON);

            amiibosJSON.put(String.format("0x%016X", amiibo.id), amiiboJSON);
        }
        outputJSON.put("amiibos", amiibosJSON);

        JSONObject gameSeriesJSON = new JSONObject();
        for (Map.Entry<Long, GameSeries> entry : this.gameSeries.entrySet()) {
            GameSeries gameSeries = entry.getValue();
            gameSeriesJSON.put(String.format("0x%03X", gameSeries.id >> GameSeries.BITSHIFT), gameSeries.name);
        }
        outputJSON.put("game_series", gameSeriesJSON);

        JSONObject charactersJSON = new JSONObject();
        for (Map.Entry<Long, Character> entry : this.characters.entrySet()) {
            Character characters = entry.getValue();
            charactersJSON.put(String.format("0x%04X", characters.id >> Character.BITSHIFT), characters.name);
        }
        outputJSON.put("characters", charactersJSON);

        JSONObject amiiboTypesJSON = new JSONObject();
        for (Map.Entry<Long, AmiiboType> entry : this.amiiboTypes.entrySet()) {
            AmiiboType amiiboType = entry.getValue();
            amiiboTypesJSON.put(String.format("0x%02X", amiiboType.id >> AmiiboType.BITSHIFT), amiiboType.name);
        }
        outputJSON.put("types", amiiboTypesJSON);

        JSONObject amiiboSeriesJSON = new JSONObject();
        for (Map.Entry<Long, AmiiboSeries> entry : this.amiiboSeries.entrySet()) {
            AmiiboSeries amiiboSeries = entry.getValue();
            amiiboSeriesJSON.put(String.format("0x%02X",
                    amiiboSeries.id >> AmiiboSeries.BITSHIFT), amiiboSeries.name);
        }
        outputJSON.put("amiibo_series", amiiboSeriesJSON);

        return outputJSON;
    }

    public static AmiiboManager getDefaultAmiiboManager(Context context)
            throws IOException, JSONException, ParseException {
        return AmiiboManager.parse(context.getResources().openRawResource(R.raw.amiibo));
    }

    public static AmiiboManager getAmiiboManager(Context context)
            throws IOException, JSONException, ParseException {
        AmiiboManager amiiboManager;
        if (new File(Storage.getDownloadDir("TagMo"), AMIIBO_DATABASE_FILE).exists()) {
            try {
                amiiboManager = AmiiboManager.parse(context.openFileInput(AMIIBO_DATABASE_FILE));
            } catch (IOException | JSONException | ParseException e) {
                amiiboManager = null;
                Debug.Log(R.string.error_amiibo_parse, e);
            }
        } else {
            amiiboManager = null;
        }
        if (null == amiiboManager) {
            amiiboManager = getDefaultAmiiboManager(context);
        }

        return amiiboManager;
    }

    public static void saveDatabase(AmiiboManager amiiboManager, OutputStream outputStream)
            throws JSONException, IOException {
        OutputStreamWriter streamWriter = null;
        try {
            streamWriter = new OutputStreamWriter(outputStream);
            streamWriter.write(amiiboManager.toJSON().toString());
            outputStream.flush();
        } finally {
            if (null != streamWriter) {
                try {
                    streamWriter.close();
                } catch (IOException e) {
                    Debug.Log(e);
                }
            }
        }
    }

    public static void saveDatabase(AmiiboManager amiiboManager, Context context)
            throws IOException, JSONException {
        OutputStream outputStream = null;
        try {
            outputStream = context.openFileOutput(AMIIBO_DATABASE_FILE, Context.MODE_PRIVATE);
            saveDatabase(amiiboManager, outputStream);
        } finally {
            if (null != outputStream) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Debug.Log(e);
                }
            }
        }
    }

    public static boolean binFileMatcher(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".bin");
    }

    public static ArrayList<AmiiboFile> listAmiibos(
            KeyManager keyManager, File rootFolder, boolean recursiveFiles) {
        ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();
        File[] files = rootFolder.listFiles();
        if (null == files || files.length == 0)
            return amiiboFiles;
        for (File file : files) {
            if (file.isDirectory() && recursiveFiles) {
                amiiboFiles.addAll(listAmiibos(keyManager, file, true));
            } else if (binFileMatcher(file.getName())) {
                try {
                    byte[] data = TagUtils.getValidatedFile(keyManager, file);
                    if (null != data) {
                        amiiboFiles.add(new AmiiboFile(file,
                                TagUtils.amiiboIdFromTag(data), data));
                    }
                } catch (Exception e) {
                    Debug.Log(e);
                }
            }
        }
        return amiiboFiles;
    }

    public static ArrayList<AmiiboFile> listAmiiboDocuments(
            Context context, KeyManager keyManager,
            DocumentFile rootFolder, boolean recursiveFiles) {
        ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();
        ArrayList<Uri> uris = new AmiiboDocument(context)
                .listFiles(rootFolder.getUri(), recursiveFiles);
        if (uris.isEmpty()) return amiiboFiles;
        for (Uri uri : uris) {
            try {
                byte[] data = TagUtils.getValidatedDocument(keyManager, uri);
                if (null != data) {
                    amiiboFiles.add(new AmiiboFile(
                            DocumentFile.fromSingleUri(context, uri),
                            TagUtils.amiiboIdFromTag(data), data
                    ));
                }
            } catch (Exception e) {
                Debug.Log(e);
            }
        }
        return amiiboFiles;
    }
}
