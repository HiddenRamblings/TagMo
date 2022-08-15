package com.hiddenramblings.tagmo.amiibo.games;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class GameSeries {

    public final GamesManager manager;
    public final String name;
    private final ArrayList<Long> gameIds = new ArrayList<>();

    public GameSeries(GamesManager manager, String name, JSONArray jsonArray) {
        this.manager = manager;
        this.name = name;
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                String gameId = jsonArray.getString(i);
                gameIds.add(GamesManager.hexToId("0x" + gameId));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean hasId(long gameId) {
        return !gameIds.isEmpty() && gameIds.contains(gameId);
    }
}
