/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
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

/** Immutable, game-API-free view of the existing arena wire snapshot. */
final class ColosseumSnapshot
{
    final JsonObject raw, player;
    final long tick, time;
    final int x, y, hp, maxHp, prayer, maxPrayer, magic, baseMagic, ranged, baseRanged, bloodLevel, baseDefence;
    final int runEnergy, specEnergy, animation, interacting, plane, poison;
    final int frailty, myopia, blasphemy, mantimayhem, effectiveMaxHp;
    final boolean instance, moving, runOn, specOn, spellSelected;
    final List<Npc> npcs = new ArrayList<>();
    final List<Item> inventory = new ArrayList<>(), equipment = new ArrayList<>();
    final JsonArray events, projectiles, ground;
    final Set<String> prayers = new HashSet<>();
    /** Molten sand tiles (Reentry/Volatility pools 50743-50745, Sol's 50746); standing on one burns every tick. */
    final Set<ColosseumCollision.Tile> sand = new HashSet<>();

    ColosseumSnapshot(JsonObject raw)
    {
        this(raw,Map.of());
    }

    ColosseumSnapshot(JsonObject raw,Map<String,Integer> inferredModifiers)
    {
        this.raw = raw;
        player = object(raw, "player");
        tick = integer(raw, "tick", -1);
        time = longValue(raw, "t", 0);
        x = integer(player, "tx", -1); y = integer(player, "ty", -1);
        hp = integer(player, "hp", 0); maxHp = integer(player, "maxHp", 0);
        JsonObject nativeModifiers=object(player,"modifiers");
        frailty=Math.max(0,Math.min(3,integer(nativeModifiers,"frailty",inferredModifiers.getOrDefault("frailty",0))));
        myopia=Math.max(0,Math.min(3,integer(nativeModifiers,"myopia",inferredModifiers.getOrDefault("myopia",0))));
        blasphemy=Math.max(0,Math.min(3,integer(nativeModifiers,"blasphemy",inferredModifiers.getOrDefault("blasphemy",0))));
        mantimayhem=Math.max(0,Math.min(3,integer(nativeModifiers,"mantimayhem",inferredModifiers.getOrDefault("mantimayhem",0))));
        int reduction=frailty==3?40:frailty*10;
        effectiveMaxHp=maxHp-maxHp*reduction/100;
        prayer = integer(player, "prayer", 0); plane = integer(player, "plane", 0);
        JsonObject skills = object(player, "skills");
        JsonObject boosted = object(skills, "boosted"), real = object(skills, "real");
        maxPrayer = integer(real, "prayer", 0);
        magic = integer(boosted, "magic", 0); baseMagic = integer(real, "magic", magic);
        bloodLevel = bool(player, "ancientSpellbook", false) && bool(player, "bloodAutocast", false)
            ? supportedBloodLevel(integer(player, "bloodAutocastLevel", 0)) : 0;
        ranged = integer(boosted, "ranged", 0); baseRanged = integer(real, "ranged", ranged);
        baseDefence = integer(real, "defence", 99);
        runEnergy = integer(player, "runEnergy", 0); specEnergy = integer(player, "specEnergy", 0);
        animation = integer(player, "anim", -1); interacting = integer(player, "interacting", -1);
        poison = integer(player, "poison", 0);
        instance = bool(player, "instance", false); moving = bool(player, "moving", false);
        runOn = bool(player, "runOn", false); specOn = bool(player, "specOn", false);
        spellSelected = bool(player, "spellSelected", false);
        for (JsonElement e : array(raw, "npcs")) if (e.isJsonObject()) npcs.add(new Npc(e.getAsJsonObject()));
        for (JsonElement e : array(raw, "inventory")) if (e.isJsonObject()) inventory.add(new Item(e.getAsJsonObject(), false));
        for (JsonElement e : array(raw, "equipment")) if (e.isJsonObject()) equipment.add(new Item(e.getAsJsonObject(), true));
        for (JsonElement e : array(raw, "prayers")) if (e.isJsonPrimitive()) prayers.add(e.getAsString());
        events = array(raw, "events"); projectiles = array(raw, "projectiles"); ground = array(raw, "ground");
        for (JsonElement e : array(raw, "objects"))
        {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();int id = integer(o, "id", -1);
            if (id >= 50743 && id <= 50746) sand.add(new ColosseumCollision.Tile(integer(o, "tx", -1), integer(o, "ty", -1)));
        }
    }

    boolean playable()
    {
        return tick >= 0 && instance && hp > 0 && maxHp > 0 && plane == 0
            && x >= 1792 && x <= 1855 && y >= 3072 && y <= 3135;
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

    /** Combat option index (varp COM_MODE); 1 is Rapid for a ranged weapon. */
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
        /** The game's equipment stats for this item, or null (not wearable, stats not loaded, or an older snapshot). */
        final JsonObject gear;
        Item(JsonObject item, boolean worn)
        {
            id = integer(item, "id", -1); slot = integer(item, "slot", -1);
            qty = integer(item, "qty", 1); name = string(item, "name", "");
            lower = name.toLowerCase(Locale.ROOT); this.worn = worn;
            JsonElement g = item.get("gear");
            gear = g != null && g.isJsonObject() ? g.getAsJsonObject() : null;
        }
        int stat(String key) { return gear == null ? 0 : integer(gear, key, 0); }
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
        /**
         * Playing its death animation: it despawns about three ticks later (231 of 235 in the local recordings,
         * 2026-11-03/04; the four misses ended with the recording) and its tile opens for whoever is waiting on it.
         */
        boolean dying()
        {
            switch (id)
            {
                case 12811: return animation == 10860; // Serpent shaman
                case 12816: return animation == 10858; // Fremennik warband berserker
                case 12815: return animation == 10855; // Fremennik warband seer
                case 12814: return animation == 10852; // Fremennik warband archer
                case 12810: return animation == 10849; // Jaguar warrior
                case 12817: return animation == 10894; // Javelin Colossus
                case 12818: return animation == 10866; // Manticore
                default: return false;
            }
        }
        boolean enemy() { return alive() && role != Role.PILLAR && role != Role.MINIMUS && role != Role.SPECTATOR && (role != Role.UNKNOWN || level > 0 || targetsPlayer()); }
        boolean warband() { return role == Role.BERSERKER || role == Role.ARCHER || role == Role.SEER; }
        boolean meleeOnly() { return role == Role.BERSERKER || role == Role.JAGUAR || role == Role.MINOTAUR; }
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
