package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ColosseumSnapshot
{
    final JsonObject raw, player;
    final long tick, time;
    final int x, y, hp, maxHp, prayer, maxPrayer, magic, baseMagic, ranged, baseRanged;
    final int runEnergy, animation, interacting, plane;
    final int mantimayhem;
    final boolean instance, moving, runOn;
    final List<Npc> npcs = new ArrayList<>();
    final List<Item> equipment = new ArrayList<>();
    final JsonArray events, projectiles;
    final Set<String> prayers = new HashSet<>();

    ColosseumSnapshot(JsonObject raw)
    {
        this.raw = raw;
        player = object(raw, "player");
        tick = integer(raw, "tick", -1);
        time = longValue(raw, "t", 0);
        x = integer(player, "tx", -1); y = integer(player, "ty", -1);
        hp = integer(player, "hp", 0); maxHp = integer(player, "maxHp", 0);
        JsonObject nativeModifiers = object(player, "modifiers");
        mantimayhem = Math.max(0, Math.min(3, integer(nativeModifiers, "mantimayhem", 0)));
        prayer = integer(player, "prayer", 0); plane = integer(player, "plane", 0);
        JsonObject skills = object(player, "skills");
        JsonObject boosted = object(skills, "boosted"), real = object(skills, "real");
        maxPrayer = integer(real, "prayer", 0);
        magic = integer(boosted, "magic", 0); baseMagic = integer(real, "magic", magic);
        ranged = integer(boosted, "ranged", 0); baseRanged = integer(real, "ranged", ranged);
        runEnergy = integer(player, "runEnergy", 0);
        animation = integer(player, "anim", -1); interacting = integer(player, "interacting", -1);
        instance = bool(player, "instance", false); moving = bool(player, "moving", false);
        runOn = bool(player, "runOn", false);
        for (JsonElement e : array(raw, "npcs")) if (e.isJsonObject()) npcs.add(new Npc(e.getAsJsonObject()));
        for (JsonElement e : array(raw, "equipment")) if (e.isJsonObject()) equipment.add(new Item(e.getAsJsonObject(), true));
        for (JsonElement e : array(raw, "prayers")) if (e.isJsonPrimitive()) prayers.add(e.getAsString());
        events = array(raw, "events"); projectiles = array(raw, "projectiles");
    }

    boolean playable()
    {
        return tick >= 0 && instance && hp > 0 && maxHp > 0 && plane == 0
            && x >= 1792 && x <= 1855 && y >= 3072 && y <= 3135;
    }

    Npc npc(int index)
    {
        for (Npc n : npcs) if (n.index == index) return n;
        return null;
    }

    Item weapon()
    {
        for (Item item : equipment) if (item.slot == 3) return item;
        return null;
    }

    boolean worn(int id)
    {
        for (Item item : equipment) if (item.id == id) return true;
        return false;
    }

    int attackStyle()
    {
        return integer(player, "attackStyle", -1);
    }

    String offensive(boolean magicMode)
    {
        return string(object(player, "bestOffensive"), magicMode ? "magic" : "range", "");
    }

    static final class Item
    {
        final int id, slot, qty;
        final String name, lower;
        final boolean worn;

        final JsonObject gear;
        Item(JsonObject item, boolean worn)
        {
            id = integer(item, "id", -1); slot = integer(item, "slot", -1);
            qty = integer(item, "qty", 1); name = string(item, "name", "");
            lower = name.toLowerCase(Locale.ROOT); this.worn = worn;
            JsonElement g = item.get("gear");
            gear = g != null && g.isJsonObject() ? g.getAsJsonObject() : null;
        }
    }

    enum Role
    {
        MINIMUS, BERSERKER, ARCHER, SEER, SHAMAN, JAGUAR, JAVELIN, MANTICORE,
        SHOCKWAVE, MINOTAUR, SOL, PILLAR, SCORPION, BEES, CRYSTAL, TOTEM, FLARE, SPECTATOR, UNKNOWN;

        static Role of(int id)
        {
            switch (id)
            {
                case 12808: return MINIMUS;
                case 12810: return JAGUAR;
                case 12811: return SHAMAN;
                case 12812: case 12813: return MINOTAUR;
                case 12814: return ARCHER;
                case 12815: return SEER;
                case 12816: return BERSERKER;
                case 12817: return JAVELIN;
                case 12818: return MANTICORE;
                case 12819: return SHOCKWAVE;
                case 12820: return PILLAR;
                case 12821: return SOL;
                case 12822: return SCORPION;
                case 12823: return BEES;
                case 12824: return CRYSTAL;
                case 12825: return TOTEM;
                case 12826: return FLARE;
                case 12827: return SPECTATOR;
                default: return UNKNOWN;
            }
        }
    }

    static final class Npc
    {
        final int index, id, x, y, size, animation, animationFrame, orientation, hpRatio, hpScale, level;
        final String name, say;
        final Role role;
        final boolean dead;
        final JsonElement interacting;
        final JsonArray spots;
        final String key;
        Npc(JsonObject npc)
        {
            index = integer(npc, "index", -1); id = integer(npc, "id", -1);
            level = integer(npc, "level", 0);
            x = integer(npc, "tx", -1); y = integer(npc, "ty", -1);
            size = Math.max(1, Math.min(16, integer(npc, "size", 1)));
            animation = integer(npc, "anim", -1); hpRatio = integer(npc, "hpRatio", -1);
            animationFrame = integer(npc, "animationFrame", -1);
            orientation = integer(npc, "orientation", -1); say = string(npc, "say", "");
            hpScale = integer(npc, "hpScale", -1); dead = bool(npc, "dead", false) || hpRatio == 0;
            name = string(npc, "name", ""); role = Role.of(id); interacting = npc.get("interacting");
            spots = array(npc, "spot"); key = string(npc, "key", Integer.toString(index));
        }
        boolean alive() { return !dead && index >= 0; }

        boolean dying()
        {
            switch (id)
            {
                case 12811: return animation == 10860;
                case 12816: return animation == 10858;
                case 12815: return animation == 10855;
                case 12814: return animation == 10852;
                case 12810: return animation == 10849;
                case 12817: return animation == 10894;
                case 12818: return animation == 10866;
                default: return false;
            }
        }
        boolean enemy() { return alive() && role != Role.PILLAR && role != Role.MINIMUS && role != Role.SPECTATOR && (role != Role.UNKNOWN || level > 0 || targetsPlayer()); }
        boolean warband() { return role == Role.BERSERKER || role == Role.ARCHER || role == Role.SEER; }
        boolean meleeOnly() { return role == Role.BERSERKER || role == Role.JAGUAR || role == Role.MINOTAUR; }
        boolean targetsPlayer() { return interacting != null && interacting.isJsonPrimitive() && "player".equals(interacting.getAsString()); }
        int distance(int tx, int ty)
        {
            return Math.max(0, Math.max(Math.max(x - tx, tx - x - size + 1), Math.max(y - ty, ty - y - size + 1)));
        }
    }

    static JsonObject object(JsonObject o, String key)
    {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }
    static JsonArray array(JsonObject o, String key)
    {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }
    static int integer(JsonObject o, String key, int fallback)
    {
        try { JsonElement e = o == null ? null : o.get(key); return e == null || e.isJsonNull() ? fallback : e.getAsInt(); }
        catch (RuntimeException ignored) { return fallback; }
    }
    static long longValue(JsonObject o, String key, long fallback)
    {
        try { JsonElement e = o == null ? null : o.get(key); return e == null || e.isJsonNull() ? fallback : e.getAsLong(); }
        catch (RuntimeException ignored) { return fallback; }
    }
    static boolean bool(JsonObject o, String key, boolean fallback)
    {
        try { JsonElement e = o == null ? null : o.get(key); return e == null || e.isJsonNull() ? fallback : e.getAsBoolean(); }
        catch (RuntimeException ignored) { return fallback; }
    }
    static String string(JsonObject o, String key, String fallback)
    {
        try { JsonElement e = o == null ? null : o.get(key); return e == null || e.isJsonNull() ? fallback : e.getAsString(); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
