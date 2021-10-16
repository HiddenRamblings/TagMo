package com.hiddenramblings.tagmo.amiibo;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class AmiiboManager {
    public final HashMap<Long, Amiibo> amiibos = new HashMap<>();
    public final HashMap<Long, GameSeries> gameSeries = new HashMap<>();
    public final HashMap<Long, Character> characters = new HashMap<>();
    public final HashMap<Long, AmiiboType> amiiboTypes = new HashMap<>();
    public final HashMap<Long, AmiiboSeries> amiiboSeries = new HashMap<>();

    public static AmiiboManager parse(Context context, Uri uri) throws IOException, JSONException, ParseException {
        InputStream inputSteam = null;
        try {
            inputSteam = context.getContentResolver().openInputStream(uri);
            return parse(inputSteam);
        } finally {
            if (inputSteam != null) {
                try {
                    inputSteam.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static AmiiboManager parse(InputStream inputStream) throws IOException, JSONException, ParseException {
        byte[] data = new byte[inputStream.available()];
        inputStream.read(data);
        inputStream.close();

        return parse(new String(data));
    }

    public static AmiiboManager parse(String json) throws JSONException, ParseException {
        return parse(new JSONObject(json));
    }

    public static AmiiboManager parse(JSONObject json) throws JSONException, ParseException {
        final DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        AmiiboManager manager = new AmiiboManager();
        JSONObject amiibosJSON = json.getJSONObject("amiibos");
        for (Iterator<String> iterator = amiibosJSON.keys(); iterator.hasNext(); ) {
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
        for (Iterator<String> iterator = gameSeriesJSON.keys(); iterator.hasNext(); ) {
            String key = iterator.next();
            String name = gameSeriesJSON.getString(key);

            GameSeries gameSeries = new GameSeries(manager, key, name);
            manager.gameSeries.put(gameSeries.id, gameSeries);
        }

        JSONObject characterJSON = json.getJSONObject("characters");
        for (Iterator<String> iterator = characterJSON.keys(); iterator.hasNext(); ) {
            String key = iterator.next();
            String name = characterJSON.getString(key);

            Character character = new Character(manager, key, name);
            manager.characters.put(character.id, character);
        }

        JSONObject amiiboTypeJSON = json.getJSONObject("types");
        for (Iterator<String> iterator = amiiboTypeJSON.keys(); iterator.hasNext(); ) {
            String key = iterator.next();
            String name = amiiboTypeJSON.getString(key);

            AmiiboType amiiboType = new AmiiboType(manager, key, name);
            manager.amiiboTypes.put(amiiboType.id, amiiboType);
        }

        JSONObject amiiboSeriesJSON = json.getJSONObject("amiibo_series");
        for (Iterator<String> iterator = amiiboSeriesJSON.keys(); iterator.hasNext(); ) {
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
            releaseJSON.put("na", amiibo.releaseDates.northAmerica == null ? null : iso8601.format(amiibo.releaseDates.northAmerica));
            releaseJSON.put("jp", amiibo.releaseDates.japan == null ? null : iso8601.format(amiibo.releaseDates.japan));
            releaseJSON.put("eu", amiibo.releaseDates.europe == null ? null : iso8601.format(amiibo.releaseDates.europe));
            releaseJSON.put("au", amiibo.releaseDates.australia == null ? null : iso8601.format(amiibo.releaseDates.australia));
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
            amiiboSeriesJSON.put(String.format("0x%02X", amiiboSeries.id >> AmiiboSeries.BITSHIFT), amiiboSeries.name);
        }
        outputJSON.put("amiibo_series", amiiboSeriesJSON);

        return outputJSON;
    }
}
