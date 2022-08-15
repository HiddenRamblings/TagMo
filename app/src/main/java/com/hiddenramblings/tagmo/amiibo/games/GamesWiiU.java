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

    public String getStringList() {
        StringBuilder usage = new StringBuilder();
        for (String game : games) {
            if (usage.toString().endsWith(":"))
                usage.append("  ");
            else
                usage.append(", ");
            usage.append(game);
        }
        return usage.toString();
    }

    public boolean hasUsage(String name) {
        return !games.isEmpty() && games.contains(name);
    }
}
