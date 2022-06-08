package com.hiddenramblings.tagmo.amiibo;

import android.content.Context;
import android.net.Uri;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class GamesManager {

    public static final String GAMES_DATABASE_FILE = "games_info.json";
    private final HashMap<Long, ArrayList<String>> games3DS = new HashMap<>();
    private final HashMap<Long, ArrayList<String>> gamesWiiU = new HashMap<>();
    private final HashMap<Long, ArrayList<String>> gamesSwitch = new HashMap<>();

    static long hexToId(String value) {
        return Long.decode(value);
    }

    public static GamesManager parse(Context context, Uri uri)
            throws IOException, JSONException, ParseException {
        try (InputStream inputSteam = context.getContentResolver().openInputStream(uri)) {
            return parse(inputSteam);
        }
    }

    static GamesManager parse(InputStream inputStream) throws IOException, JSONException, ParseException {
        byte[] data = new byte[inputStream.available()];
        //noinspection ResultOfMethodCallIgnored
        inputStream.read(data);
        inputStream.close();

        return parse(new String(data));
    }

    static GamesManager parse(String json) throws JSONException {
        return parse(new JSONObject(json));
    }

    static GamesManager parse(JSONObject json) throws JSONException {
        GamesManager manager = new GamesManager();
        JSONObject amiibosJSON = json.getJSONObject("amiibos");
        for (Iterator<String> amiiboIterator = amiibosJSON.keys(); amiiboIterator.hasNext();) {
            String amiiboKey = amiiboIterator.next();
            JSONObject amiiboJSON = amiibosJSON.getJSONObject(amiiboKey);

            ArrayList<String> amiibo3DS = new ArrayList<>();
            JSONArray games3DSJSON = amiiboJSON.getJSONArray("games3DS");
            for (int i = 0; i < games3DSJSON.length(); i++) {
                JSONObject game = games3DSJSON.getJSONObject(i);
                String name = game.getString("gameName");
                amiibo3DS.add(name);
            }
            manager.games3DS.put(hexToId(amiiboKey), amiibo3DS);

            ArrayList<String> amiiboWiiU = new ArrayList<>();
            JSONArray gamesWiiUJSON = amiiboJSON.getJSONArray("gamesWiiU");
            for (int i = 0; i < gamesWiiUJSON.length(); i++) {
                JSONObject game = gamesWiiUJSON.getJSONObject(i);
                String name = game.getString("gameName");
                amiiboWiiU.add(name);
            }
            manager.gamesWiiU.put(hexToId(amiiboKey), amiiboWiiU);

            ArrayList<String> amiiboSwitch = new ArrayList<>();
            JSONArray gamesSwitchJSON = amiiboJSON.getJSONArray("gamesSwitch");
            for (int i = 0; i < gamesSwitchJSON.length(); i++) {
                JSONObject game = gamesSwitchJSON.getJSONObject(i);
                String name = game.getString("gameName");
                amiiboSwitch.add(name);
            }
            manager.gamesSwitch.put(hexToId(amiiboKey), amiiboSwitch);
        }

        return manager;
    }

    public static GamesManager getDefaultGamesManager(Context context)
            throws IOException, JSONException, ParseException {
        return GamesManager.parse(context.getResources().openRawResource(R.raw.games_info));
    }

    public static GamesManager getGamesManager(Context context)
            throws IOException, JSONException, ParseException {
        GamesManager gamesManager;
        if (new File(Storage.getDownloadDir("TagMo"), GAMES_DATABASE_FILE).exists()) {
            try {
                gamesManager = GamesManager.parse(context.openFileInput(GAMES_DATABASE_FILE));
            } catch (IOException | JSONException | ParseException e) {
                gamesManager = null;
                Debug.Log(R.string.error_amiibo_parse, e);
            }
        } else {
            gamesManager = null;
        }
        if (null == gamesManager) {
            gamesManager = getDefaultGamesManager(context);
        }

        return gamesManager;
    }

    public ArrayList<String> get3DSGames(long id) {
        return games3DS.get(id);
    }

    public ArrayList<String> getWiiUGames(long id) {
        return gamesWiiU.get(id);
    }

    public ArrayList<String> getSwitchGames(long id) {
        return gamesSwitch.get(id);
    }

    public String getGamesCompatibility(long amiiboId) {
        StringBuilder usage = new StringBuilder();
        if (!get3DSGames(amiiboId).isEmpty()) {
            usage.append("\n3DS:");
            for (String game : get3DSGames(amiiboId)) {
                if (usage.toString().endsWith(":"))
                    usage.append("  ");
                else
                    usage.append(", ");
                usage.append(game);
            }
            usage.append("\n");
        }
        if (!getWiiUGames(amiiboId).isEmpty()) {
            usage.append("\nWiiU:");
            for (String game : getWiiUGames(amiiboId)) {
                if (usage.toString().endsWith(":"))
                    usage.append("  ");
                else
                    usage.append(", ");
                usage.append(game);
            }
            usage.append("\n");
        }
        if (!getSwitchGames(amiiboId).isEmpty()) {
            usage.append("\nSwitch:");
            for (String game : getSwitchGames(amiiboId)) {
                if (usage.toString().endsWith(":"))
                    usage.append("  ");
                else
                    usage.append(", ");
                usage.append(game);
            }
            usage.append("\n");
        }
        return usage.toString();
    }
}
