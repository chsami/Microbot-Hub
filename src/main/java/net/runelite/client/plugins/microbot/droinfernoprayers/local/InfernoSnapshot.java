/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable, game-API-free view of the existing arena wire snapshot. */
final class InfernoSnapshot
{
    final JsonObject raw, player;
    final long tick, time;
    final int x, y, hp, maxHp, prayer, maxPrayer, magic, baseMagic, ranged, baseRanged, bloodLevel;
    final int runEnergy, specEnergy, animation, interacting, plane;
    /** Ice tiers the carried runes support (ICE_* bits); -1 when the capture has no rune receipt. */
    final int iceRunes;
    static final int ICE_BURST = 1, ICE_BLITZ = 2, ICE_BARRAGE = 4;
    final boolean instance, moving, runOn, specOn, spellSelected;
    final List<Npc> npcs = new ArrayList<>();
    final List<Item> inventory = new ArrayList<>(), equipment = new ArrayList<>();
    final JsonArray events, projectiles, ground;
    final Set<String> prayers = new HashSet<>();

    InfernoSnapshot(JsonObject raw)
    {
        this.raw = raw;
        player = object(raw, "player");
        tick = integer(raw, "tick", -1);
        time = longValue(raw, "t", 0);
        x = integer(player, "tx", -1); y = integer(player, "ty", -1);
        hp = integer(player, "hp", 0); maxHp = integer(player, "maxHp", 0);
        prayer = integer(player, "prayer", 0); plane = integer(player, "plane", 0);
        JsonObject skills = object(player, "skills");
        JsonObject boosted = object(skills, "boosted"), real = object(skills, "real");
        maxPrayer = integer(real, "prayer", 0);
        magic = integer(boosted, "magic", 0); baseMagic = integer(real, "magic", magic);
        bloodLevel = bool(player, "ancientSpellbook", false) && bool(player, "bloodAutocast", false)
            ? supportedBloodLevel(integer(player, "bloodAutocastLevel", 0)) : 0;
        iceRunes = integer(player, "iceRunes", -1);
        ranged = integer(boosted, "ranged", 0); baseRanged = integer(real, "ranged", ranged);
        runEnergy = integer(player, "runEnergy", 0); specEnergy = integer(player, "specEnergy", 0);
        animation = integer(player, "anim", -1); interacting = integer(player, "interacting", -1);
        instance = bool(player, "instance", false); moving = bool(player, "moving", false);
        runOn = bool(player, "runOn", false); specOn = bool(player, "specOn", false);
        spellSelected = bool(player, "spellSelected", false);
        for (JsonElement e : array(raw, "npcs")) if (e.isJsonObject()) npcs.add(new Npc(e.getAsJsonObject()));
        for (JsonElement e : array(raw, "inventory")) if (e.isJsonObject()) inventory.add(new Item(e.getAsJsonObject(), false));
        for (JsonElement e : array(raw, "equipment")) if (e.isJsonObject()) equipment.add(new Item(e.getAsJsonObject(), true));
        for (JsonElement e : array(raw, "prayers")) if (e.isJsonPrimitive()) prayers.add(e.getAsString());
        events = array(raw, "events"); projectiles = array(raw, "projectiles"); ground = array(raw, "ground");
    }

    boolean playable()
    {
        return tick >= 0 && instance && hp > 0 && maxHp > 0 && plane == 0
            && x >= 2240 && x <= 2303 && y >= 5312 && y <= 5375;
    }

    /** Highest Ice spell the carried runes and boosted Magic can cast now; 0 when none can. */
    int iceLevel()
    {
        // Historical captures carry no rune receipt: keep the reference's Barrage, else Burst.
        int runes = iceRunes < 0 ? ICE_BURST | ICE_BARRAGE : iceRunes;
        if ((runes & ICE_BARRAGE) != 0 && magic >= 94) return 94;
        if ((runes & ICE_BLITZ) != 0 && magic >= 82) return 82;
        if ((runes & ICE_BURST) != 0 && magic >= 70) return 70;
        return 0;
    }

    /** Lowest carried Ice tier within base Magic: the level a restore must bring back after brews. */
    int iceRestoreLevel()
    {
        int runes = iceRunes < 0 ? ICE_BURST | ICE_BARRAGE : iceRunes;
        if ((runes & ICE_BURST) != 0 && baseMagic >= 70) return 70;
        if ((runes & ICE_BLITZ) != 0 && baseMagic >= 82) return 82;
        if ((runes & ICE_BARRAGE) != 0 && baseMagic >= 94) return 94;
        return 0;
    }

    static int supportedBloodLevel(int level)
    {
        return level == 56 || level == 68 || level == 80 || level == 92 ? level : 0;
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

    String offensive(boolean magicMode)
    {
        return string(object(player, "bestOffensive"), magicMode ? "magic" : "range", "");
    }

    static final class Item
    {
        final int id, slot, qty;
        final String name, lower;
        final boolean worn;
        /** The game's equipment stats for this item, or null (not wearable, not loaded, or a recorded snapshot). */
        final JsonObject gear;
        Item(JsonObject item, boolean worn)
        {
            id = integer(item, "id", -1); slot = integer(item, "slot", -1);
            qty = integer(item, "qty", 1); name = string(item, "name", "");
            lower = name.toLowerCase(Locale.ROOT); this.worn = worn;
            com.google.gson.JsonElement g = item.get("gear");
            gear = g != null && g.isJsonObject() ? g.getAsJsonObject() : null;
        }
    }

    enum Role
    {
        NIBBLER, BAT, BLOB, MINI_MAGE, MINI_RANGE, MINI_MELEE, MELEE, RANGER, MAGE,
        JAD, JAD_HEALER, ZUK, SHIELD, ZUK_HEALER, PILLAR, UNKNOWN;

        static Role of(int id)
        {
            switch (id)
            {
                case 7691: return NIBBLER;
                case 7692: return BAT;
                case 7693: return BLOB;
                case 7694: return MINI_MAGE;
                case 7695: return MINI_RANGE;
                case 7696: return MINI_MELEE;
                case 7697: return MELEE;
                case 7698: case 7702: return RANGER;
                case 7699: case 7703: return MAGE;
                case 7700: case 7704: return JAD;
                case 7701: case 7705: return JAD_HEALER;
                case 7706: return ZUK;
                case 7707: return SHIELD;
                case 7708: return ZUK_HEALER;
                case 7709: case 7710: return PILLAR;
                default: return UNKNOWN;
            }
        }
    }

    static final class Npc
    {
        final int index, id, x, y, size, animation, hpRatio, hpScale;
        final String name;
        final Role role;
        final boolean dead;
        final JsonElement interacting;
        Npc(JsonObject npc)
        {
            index = integer(npc, "index", -1); id = integer(npc, "id", -1);
            x = integer(npc, "tx", -1); y = integer(npc, "ty", -1);
            size = Math.max(1, Math.min(16, integer(npc, "size", 1)));
            animation = integer(npc, "anim", -1); hpRatio = integer(npc, "hpRatio", -1);
            hpScale = integer(npc, "hpScale", -1); dead = bool(npc, "dead", false) || hpRatio == 0;
            name = string(npc, "name", ""); role = Role.of(id); interacting = npc.get("interacting");
        }
        boolean alive() { return !dead && index >= 0; }
        boolean enemy() { return alive() && role != Role.PILLAR && role != Role.SHIELD && role != Role.UNKNOWN; }
        boolean targetsPlayer() { return interacting != null && interacting.isJsonPrimitive() && "player".equals(interacting.getAsString()); }
        int targetIndex()
        {
            try { return interacting == null || interacting.isJsonNull() ? -1 : interacting.getAsInt(); }
            catch (RuntimeException ignored) { return -1; }
        }
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
