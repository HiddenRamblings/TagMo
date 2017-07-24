package com.hiddenramblings.tagmo.amiibo;

import android.support.v4.util.LongSparseArray;
import android.support.v4.util.SparseArrayCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

public class AmiiboManager {
    public static final DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd");

    public final LongSparseArray<Amiibo> amiibos = new LongSparseArray<>();
    public final SparseArrayCompat<GameSeries> gameSeries = new SparseArrayCompat<>();
    public final SparseArrayCompat<Character> characters = new SparseArrayCompat<>();
    public final SparseArrayCompat<AmiiboType> amiiboTypes = new SparseArrayCompat<>();
    public final SparseArrayCompat<AmiiboSeries> amiiboSeries = new SparseArrayCompat<>();

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
        AmiiboManager manager = new AmiiboManager();
        JSONObject amiibosJSON = json.getJSONObject("amiibos");
        for (Iterator iterator = amiibosJSON.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
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
        for (Iterator iterator = gameSeriesJSON.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
            String name = gameSeriesJSON.getString(key);

            GameSeries gameSeries = new GameSeries(manager, key, name);
            manager.gameSeries.put(gameSeries.id, gameSeries);
        }

        JSONObject characterJSON = json.getJSONObject("characters");
        for (Iterator iterator = characterJSON.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
            String name = characterJSON.getString(key);

            Character character = new Character(manager, key, name);
            manager.characters.put(character.id, character);
        }

        JSONObject amiiboTypeJSON = json.getJSONObject("types");
        for (Iterator iterator = amiiboTypeJSON.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
            String name = amiiboTypeJSON.getString(key);

            AmiiboType amiiboType = new AmiiboType(manager, key, name);
            manager.amiiboTypes.put(amiiboType.id, amiiboType);
        }

        JSONObject amiiboSeriesJSON = json.getJSONObject("amiibo_series");
        for (Iterator iterator = amiiboSeriesJSON.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();
            String name = amiiboSeriesJSON.getString(key);

            AmiiboSeries amiiboSeries = new AmiiboSeries(manager, key, name);
            manager.amiiboSeries.put(amiiboSeries.id, amiiboSeries);
        }

        return manager;
    }
}
