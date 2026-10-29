package net.runelite.client.plugins.microbot.fourtify.core.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups a plugin's gate rows by spot. The rows are the constants of the plugin's hand-kept gate enum (it implements
 * {@link GateRow}); the spot rows are generated, so a gate is not a column of them. Each row is one requirement
 * of a spot, named by its kind or tree type and the x and y of its centre as in the plugin's spot enum, and is made
 * with {@link Gate#of} (a quest finished, or started) or {@link Gate#skill} (a real, unboosted level). A spot with
 * several requirements has several rows and needs all of them. A row's types are checked when the plugin compiles;
 * {@code NOT_STARTED} and a level outside 1 to 126 stop the plugin from loading, so a typo never hides a spot quietly.
 */
public final class Gates {
    private Gates() {
    }

    /** The key of a spot in the gate rows: its type, x and y, as {@code type,x,y}. */
    public static String key(String type, int x, int y) {
        return type + "," + x + "," + y;
    }

    /** The gates of each spot by {@link #key}, from the rows of a plugin's gate enum (none: no gates). */
    public static Map<String, List<Gate>> load(GateRow[] rows) {
        Map<String, List<Gate>> gates = new HashMap<>();
        for (GateRow row : rows) {
            gates.computeIfAbsent(key(row.typeName(), row.x(), row.y()), k -> new ArrayList<>()).add(row.gate());
        }
        return gates;
    }
}
