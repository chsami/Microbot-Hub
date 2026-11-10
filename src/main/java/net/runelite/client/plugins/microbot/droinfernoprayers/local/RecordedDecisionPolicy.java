package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Recorded destination and target preferences for an identical observed context.
 * The live controller still owns timing, path validation, supplies and protection.
 * Neither recorded NPC indices nor recorded inventory slots can escape this class.
 */
public final class RecordedDecisionPolicy
{
    private static final String RESOURCE =
        "/net/runelite/client/plugins/microbot/droinferno/local/recorded-policy.json.gz";
    private final Map<String, JsonObject> cases = new HashMap<>();
    private String loadError;
    private long previousTick = -1;
    private Integer previousShieldX;
    private int shieldDirection;
    private long queries;
    private long matches;

    public static final class Hint
    {
        public static final Hint NONE = new Hint(null, null, false);
        public final int[] movement;
        public final Integer target;
        public final boolean referenceOnly;

        private Hint(int[] movement, Integer target, boolean referenceOnly)
        {
            this.movement = movement;
            this.target = target;
            this.referenceOnly = referenceOnly;
        }
    }

    /** Call from the decision worker; resource decompression is never a client-thread operation. */
    public static RecordedDecisionPolicy loadDefault()
    {
        RecordedDecisionPolicy policy = new RecordedDecisionPolicy();
        try (InputStream raw = RecordedDecisionPolicy.class.getResourceAsStream(RESOURCE))
        {
            if (raw == null)
            {
                policy.loadError = "Recording preference resource is missing";
                return policy;
            }
            try (GZIPInputStream gzip = new GZIPInputStream(raw);
                 InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8))
            {
                policy.read(new JsonParser().parse(reader).getAsJsonObject());
            }
        }
        catch (IOException | RuntimeException ex)
        {
            policy.cases.clear();
            policy.loadError = "Recording preference resource could not be read";
        }
        return policy;
    }

    /** Public for deterministic fixture validation without touching the game client. */
    public static RecordedDecisionPolicy fromJson(JsonObject document)
    {
        RecordedDecisionPolicy policy = new RecordedDecisionPolicy();
        policy.read(document);
        return policy;
    }

    private void read(JsonObject document)
    {
        if (integer(document, "schemaVersion", -1) != 1)
        {
            throw new IllegalArgumentException("Unsupported recording preference version");
        }
        JsonObject entries = object(document, "cases");
        if (entries == null || entries.size() > 100_000)
        {
            throw new IllegalArgumentException("Invalid recording preference count");
        }
        for (Map.Entry<String, JsonElement> entry : entries.entrySet())
        {
            if (entry.getKey().length() > 8192 || !entry.getValue().isJsonObject())
            {
                throw new IllegalArgumentException("Invalid recording preference entry");
            }
            cases.put(entry.getKey(), entry.getValue().getAsJsonObject());
        }
    }

    public void reset()
    {
        previousTick = -1;
        previousShieldX = null;
        shieldDirection = 0;
        queries = 0;
        matches = 0;
    }

    public Hint suggest(JsonObject snapshot)
    {
        long tick = integer(snapshot, "tick", -1);
        if (tick <= previousTick)
        {
            previousShieldX = null;
            shieldDirection = 0;
        }
        previousTick = tick;
        Integer shieldX = null;
        for (JsonObject npc : npcs(snapshot))
        {
            if (integer(npc, "id", -1) == 7707)
            {
                shieldX = integer(npc, "tx", -1);
                break;
            }
        }
        if (shieldX == null)
        {
            previousShieldX = null;
            shieldDirection = 0;
        }
        else
        {
            if (previousShieldX != null && !shieldX.equals(previousShieldX))
            {
                shieldDirection = Integer.compare(shieldX, previousShieldX);
            }
            previousShieldX = shieldX;
        }
        return suggest(snapshot, shieldDirection);
    }

    /** Explicit direction makes Python/Java cross-language feature tests exact. */
    public Hint suggest(JsonObject snapshot, int direction)
    {
        queries++;
        String key = fingerprint(snapshot, direction);
        JsonObject entry = key == null ? null : cases.get(key);
        if (entry == null)
        {
            return Hint.NONE;
        }
        int[] movement = null;
        Integer target = null;
        boolean referenceOnly = false;
        JsonObject move = object(entry, "movement");
        if (move != null)
        {
            JsonArray value = array(move, "value");
            if (value != null && value.size() == 2)
            {
                int x = value.get(0).getAsInt();
                int y = value.get(1).getAsInt();
                if (x >= 2240 && x <= 2303 && y >= 5312 && y <= 5375)
                {
                    movement = new int[]{x, y};
                    referenceOnly |= bool(move, "referenceOnly");
                }
            }
        }
        JsonObject attack = object(entry, "target");
        if (attack != null)
        {
            JsonArray value = array(attack, "value");
            if (value != null && value.size() == 4)
            {
                int count = 0;
                for (JsonObject npc : npcs(snapshot))
                {
                    if (integer(npc, "id", -1) == value.get(0).getAsInt()
                        && integer(npc, "tx", -1) == value.get(1).getAsInt()
                        && integer(npc, "ty", -1) == value.get(2).getAsInt()
                        && integer(npc, "size", 1) == value.get(3).getAsInt())
                    {
                        target = integer(npc, "index", -1);
                        count++;
                    }
                }
                if (count != 1 || target == null || target < 0)
                {
                    target = null; // Stacked identical NPCs have no unique recorded binding.
                }
                else
                {
                    referenceOnly |= bool(attack, "referenceOnly");
                }
            }
        }
        if (movement == null && target == null)
        {
            return Hint.NONE;
        }
        matches++;
        return new Hint(movement, target, referenceOnly);
    }

    public static String fingerprint(JsonObject snapshot, int direction)
    {
        JsonObject player = object(snapshot, "player");
        if (player == null || !bool(player, "instance") || integer(player, "hp", 0) <= 0)
        {
            return null;
        }
        int x = integer(player, "tx", -1), y = integer(player, "ty", -1);
        if (x < 2240 || x > 2303 || y < 5312 || y > 5375)
        {
            return null;
        }
        for (String required : new String[]{"npcs", "equipment", "inventory", "prayers"})
        {
            if (array(snapshot, required) == null)
            {
                return null;
            }
        }
        List<JsonObject> live = npcs(snapshot);
        for (JsonObject npc : live)
        {
            int id = integer(npc, "id", -1);
            if (id < 7691 || id > 7710)
            {
                return null;
            }
        }
        int hpBand = Math.min(4, integer(player, "hp", 0) * 4
            / Math.max(1, integer(player, "maxHp", 1)));
        StringBuilder key = new StringBuilder("v1|").append(x).append('|').append(y)
            .append('|').append(hpBand).append('|').append(weaponKind(snapshot))
            .append('|').append(bool(player, "spellSelected") ? 1 : 0)
            .append('|').append(direction);
        for (JsonObject npc : live)
        {
            int ratio = integer(npc, "hpRatio", -1), scale = integer(npc, "hpScale", -1);
            int health = ratio < 0 || scale <= 0 ? -1 : Math.min(3, ratio * 3 / scale);
            key.append('|').append(integer(npc, "id", -1))
                .append(',').append(integer(npc, "tx", -1))
                .append(',').append(integer(npc, "ty", -1))
                .append(',').append(integer(npc, "size", 1))
                .append(',').append(integer(npc, "anim", -1)).append(',').append(health);
        }
        return key.toString();
    }

    private static int weaponKind(JsonObject snapshot)
    {
        JsonArray equipment = array(snapshot, "equipment");
        if (equipment != null)
        {
            for (JsonElement element : equipment)
            {
                JsonObject item = element.getAsJsonObject();
                if (integer(item, "slot", -1) != 3)
                {
                    continue;
                }
                String name = item.has("name") && !item.get("name").isJsonNull()
                    ? item.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
                if (name.contains("blowpipe")) return 2;
                if (name.contains("wand") || name.contains("staff") || name.contains("sceptre")) return 1;
                if (name.contains("bow") || name.contains("ballista") || name.contains("atlatl")) return 3;
            }
        }
        return 0;
    }

    private static List<JsonObject> npcs(JsonObject snapshot)
    {
        List<JsonObject> result = new ArrayList<>();
        JsonArray list = array(snapshot, "npcs");
        if (list != null)
        {
            for (JsonElement element : list)
            {
                if (element.isJsonObject() && !bool(element.getAsJsonObject(), "dead"))
                {
                    result.add(element.getAsJsonObject());
                }
            }
        }
        result.sort(Comparator.comparingInt((JsonObject n) -> integer(n, "id", -1))
            .thenComparingInt(n -> integer(n, "tx", -1))
            .thenComparingInt(n -> integer(n, "ty", -1))
            .thenComparingInt(n -> integer(n, "size", 1)));
        return result;
    }

    private static JsonObject object(JsonObject o, String name)
    {
        return o != null && o.has(name) && o.get(name).isJsonObject() ? o.getAsJsonObject(name) : null;
    }

    private static JsonArray array(JsonObject o, String name)
    {
        return o != null && o.has(name) && o.get(name).isJsonArray() ? o.getAsJsonArray(name) : null;
    }

    private static int integer(JsonObject o, String name, int fallback)
    {
        return o != null && o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsInt() : fallback;
    }

    private static boolean bool(JsonObject o, String name)
    {
        return o != null && o.has(name) && !o.get(name).isJsonNull() && o.get(name).getAsBoolean();
    }

    public int caseCount() { return cases.size(); }
    public String loadError() { return loadError; }
    public long queries() { return queries; }
    public long matches() { return matches; }
}
