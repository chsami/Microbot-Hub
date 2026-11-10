package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Preferences observed on Zuk firing ticks, including the user's confirmed-cape reference. */
public final class InfernoZukReference
{
    private final Map<String, JsonObject> cases = new HashMap<>();
    private String loadError;

    public static final class Hint
    {
        public static final Hint NONE = new Hint(false, null, -1, 0);
        public final boolean matched;
        /** Matched + null is an observed wait on this firing tick. */
        public final int[] movement;
        public final int specificity;
        public final int support;

        private Hint(boolean matched, int[] movement, int specificity, int support)
        {
            this.matched = matched;
            this.movement = movement;
            this.specificity = specificity;
            this.support = support;
        }
    }

    /** Load lazily on the decision worker. No game-client or network dependencies. */
    public static InfernoZukReference loadDefault()
    {
        InfernoZukReference reference = new InfernoZukReference();
        try
        {
            reference.load("zuk-reference.json.gz");
            reference.load("zuk-completion-reference.json.gz");
        }
        catch (Exception ex)
        {
            reference.cases.clear();
            reference.loadError = "Zuk reference preferences could not be loaded";
        }
        return reference;
    }

    private void load(String name) throws Exception
    {
        String path = "/net/runelite/client/plugins/microbot/droinferno/local/" + name;
        try (InputStream raw = InfernoZukReference.class.getResourceAsStream(path))
        {
            if (raw == null) throw new IllegalArgumentException("Missing Zuk reference");
            try (GZIPInputStream gzip = new GZIPInputStream(raw);
                 InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8))
            {
                JsonObject document = new JsonParser().parse(reader).getAsJsonObject();
                if (number(document, "schemaVersion", -1) != 1)
                    throw new IllegalArgumentException("Unsupported Zuk reference");
                JsonObject entries = document.getAsJsonObject("cases");
                if (entries.size() > 10000 || cases.size() + entries.size() > 10000)
                    throw new IllegalArgumentException("Invalid Zuk reference size");
                for (Map.Entry<String, JsonElement> entry : entries.entrySet())
                    cases.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
        }
    }

    /** Caller establishes the live firing tick; this method never drives an elapsed-time replay. */
    public Hint suggest(JsonObject snapshot, int period, int direction, int stationaryTicks)
    {
        String[] keys = keys(snapshot, period, direction, stationaryTicks);
        if (keys == null) return Hint.NONE;
        for (int i = 0; i < keys.length; i++)
        {
            JsonObject match = cases.get(keys[i]);
            if (match != null) return decode(match, i);
        }
        return Hint.NONE;
    }

    /** Broad modes only; callers with a full snapshot should use suggest. */
    public int[] movement(int period, int glyphX, int direction)
    {
        JsonObject match = cases.get("3|" + period + "|" + glyphX + "|" + direction);
        return match == null ? null : decode(match, 3).movement;
    }

    private static Hint decode(JsonObject entry, int specificity)
    {
        JsonArray value = entry.getAsJsonArray("movement");
        if (value.size() == 0)
            return new Hint(true, null, specificity, number(entry, "support", 0));
        if (value.size() != 2) return Hint.NONE;
        int x = value.get(0).getAsInt(), y = value.get(1).getAsInt();
        if (x < 2240 || x > 2303 || y < 5312 || y > 5375) return Hint.NONE;
        return new Hint(true, new int[]{x, y}, specificity, number(entry, "support", 0));
    }

    public static String[] keys(JsonObject snapshot, int period, int direction, int stationaryTicks)
    {
        if (snapshot == null || !snapshot.has("player") || !snapshot.has("npcs")) return null;
        if (period != 7 && period != 10 || direction < -1 || direction > 1) return null;
        JsonObject player = snapshot.getAsJsonObject("player"), glyph = null;
        int interaction = number(player, "interacting", -1), target = 0;
        List<JsonObject> adds = new ArrayList<>();
        boolean zukPresent = false;
        for (JsonElement e : snapshot.getAsJsonArray("npcs"))
        {
            JsonObject npc = e.getAsJsonObject();
            if (npc.has("dead") && npc.get("dead").getAsBoolean()) continue;
            int id = number(npc, "id", -1);
            if (number(npc, "index", -2) == interaction) target = id;
            if (id == 7707) glyph = npc;
            else if (id == 7706) zukPresent = true;
            else adds.add(npc);
        }
        if (glyph == null || !zukPresent || number(player, "hp", 0) <= 0) return null;
        adds.sort(Comparator.comparingInt((JsonObject n) -> number(n, "id", -1))
            .thenComparingInt(n -> number(n, "tx", -1)).thenComparingInt(n -> number(n, "ty", -1))
            .thenComparingInt(n -> number(n, "anim", -1)));
        StringBuilder pose = new StringBuilder();
        for (JsonObject npc : adds)
        {
            if (pose.length() > 0) pose.append(';');
            pose.append(number(npc, "id", -1)).append(',').append(number(npc, "tx", -1))
                .append(',').append(number(npc, "ty", -1)).append(',').append(number(npc, "anim", -1));
        }
        String base = period + "|" + number(glyph, "tx", -1) + "|" + direction;
        int px = number(player, "tx", -1), py = number(player, "ty", -1);
        return new String[]{"0|" + base + "|" + stationaryTicks + "|" + target + "|" + px + "|" + py + "|" + pose,
            "1|" + base + "|" + stationaryTicks + "|" + target + "|" + py,
            "2|" + base + "|" + target, "3|" + base};
    }

    private static int number(JsonObject o, String key, int fallback)
    {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return fallback;
        try { return o.get(key).getAsInt(); }
        catch (RuntimeException invalid) { return fallback; }
    }

    public int caseCount() { return cases.size(); }
    public String loadError() { return loadError; }
}
