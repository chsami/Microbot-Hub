package net.runelite.client.plugins.microbot.droefficientwalker;

import java.io.InputStream;
import java.util.Set;

/** Data ownership for the walker engine. */
public final class WalkerEngineResources {
    private static final String ENGINE = "net/runelite/client/plugins/microbot/droefficientwalker/engine/";
    private static final String CURRENT = "net/runelite/client/plugins/microbot/droefficientwalker/";
    private static final String SHADOW = "net/runelite/client/plugins/microbot/efficientwalkerez/";
    private static final String LEGACY = "net/runelite/client/plugins/microbot/efficientwalker/";
    private static final Set<String> FILES = Set.of("agility_shortcuts.tsv", "collision-map.zip",
        "fairy_rings.tsv", "quetzals.tsv", "transports.tsv", "shortest-path-LICENSE", "skretzo-revision");

    private WalkerEngineResources() { }

    public static InputStream open(String requested) {
        if (requested == null) return null;
        String name = requested.startsWith("/") ? requested.substring(1) : requested;
        String file;
        if (name.startsWith(ENGINE)) file = name.substring(ENGINE.length());
        else if (name.startsWith(CURRENT)) file = name.substring(CURRENT.length());
        else if (name.startsWith(SHADOW)) file = name.substring(SHADOW.length());
        else if (name.startsWith(LEGACY)) file = name.substring(LEGACY.length());
        else return null;
        if (!FILES.contains(file)) return null;
        if (file.equals("collision-map.zip")) {
            InputStream maintained = WalkerEngineResources.class.getResourceAsStream("/" + CURRENT + "support/" + file);
            if (maintained != null) return MotherlodeCollisionMap.openPreserved(maintained);
        }
        InputStream data = WalkerEngineResources.class.getResourceAsStream("/" + CURRENT + "engine-data/" + file);
        return file.equals("collision-map.zip") ? MotherlodeCollisionMap.openPreserved(data) : data;
    }
}
