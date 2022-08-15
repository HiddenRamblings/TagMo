package com.hiddenramblings.tagmo.amiibo.games;

import java.util.ArrayList;

public class GamesWiiU {

    public final GamesManager manager;
    public final long id;
    public final ArrayList<String> games;

    public GamesWiiU(GamesManager manager, long id, ArrayList<String> games) {
        this.manager = manager;
        this.id = id;
        this.games = games;
    }

    public boolean hasGame(String name) {
        return !games.isEmpty() && games.contains(name);
    }
}
