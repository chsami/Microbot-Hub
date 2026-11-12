package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Item;

final class InfernoLoadout
{
    enum Mode { ICE, BLOOD, BLOWPIPE, LONG_RANGE, CHINS }
    private enum Ammunition { NONE, ARROWS, BOLTS }
    private static final int MAGIC = 1, RANGED = 2;

    private static final int[] MAGE_ORDER = {2, 4, 5, 0, 1, 7, 9, 10, 12, 13};
    private static final int[] RANGED_ORDER = {1, 2, 4, 7, 9, 13, 5, 0, 10, 12};
    private static final class Gear
    {
        final int slot, styles, preferredStyles, rank;
        final Mode weapon;
        final Ammunition ammunition;
        Gear(int slot, int styles, Mode weapon, Ammunition ammunition, int rank)
        { this(slot, styles, slot == 5 ? 0 : styles, weapon, ammunition, rank); }
        Gear(int slot, int styles, int preferredStyles, Mode weapon, Ammunition ammunition, int rank)
        { this.slot = slot; this.styles = styles; this.preferredStyles = preferredStyles;
            this.weapon = weapon; this.ammunition = ammunition; this.rank = rank; }
    }

    private static final Map<Integer, Gear> SUPPORTED = supportedGear();
    private final Map<Integer, Integer> observedSlots = new LinkedHashMap<>();
    private final Map<Integer, Item> known = new LinkedHashMap<>();

    private static Map<Integer, Gear> supportedGear()
    {
        Map<Integer, Gear> result = new LinkedHashMap<>();
        weapon(result, 4675, Mode.BLOOD, Ammunition.NONE, 20);
        weapon(result, 6914, Mode.BLOOD, Ammunition.NONE, 60);
        weapon(result, 27624, Mode.BLOOD, Ammunition.NONE, 70);
        weapon(result, 28262, Mode.BLOOD, Ammunition.NONE, 70);
        weapon(result, 28260, Mode.BLOOD, Ammunition.NONE, 80);
        weapon(result, 21006, Mode.BLOOD, Ammunition.NONE, 90);
        weapon(result, 12926, Mode.BLOWPIPE, Ammunition.NONE, 20);
        weapon(result, 20997, Mode.LONG_RANGE, Ammunition.ARROWS, 90);
        weapon(result, 21902, Mode.LONG_RANGE, Ammunition.BOLTS, 70);
        weapon(result, 21012, Mode.LONG_RANGE, Ammunition.BOLTS, 70);
        weapon(result, 11785, Mode.LONG_RANGE, Ammunition.BOLTS, 80);
        wearable(result, 5, MAGIC | RANGED, 21000, 11926, 12807, 12829, 12831, 12817,
            12608, 3840, 12610, 3844, 3842, 12612, 6889, 23991, 11924, 12825, 25985, 27251, 27253);
        shieldPreference(result, MAGIC, 12612, 6889, 11924, 12825, 25985, 27251, 27253);
        shieldPreference(result, RANGED, 21000, 11926, 12807, 12610, 23991);
        wearable(result, 4, MAGIC, 4091, 4111, 23050, 25404, 24291, 27125, 26243, 21021);
        wearable(result, 2, MAGIC, 19720);
        wearable(result, 9, MAGIC, 31106);
        wearable(result, 1, MAGIC, 21791, 24248, 24249, 24250);
        wearable(result, 4, RANGED, 27229, 27238);
        wearable(result, 7, RANGED, 27232, 27241);
        wearable(result, 9, RANGED, 26235);
        wearable(result, 1, RANGED, 27374);
        wearable(result, 2, RANGED, 33639);

        result.put(6585, new Gear(2, MAGIC | RANGED, 0, null, Ammunition.NONE, 0));
        result.put(11212, new Gear(13, RANGED, null, Ammunition.ARROWS, 70));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void wearable(Map<Integer, Gear> result, int slot, int styles, int... ids)
    { for (int id : ids) result.put(id, new Gear(slot, styles, null, Ammunition.NONE, 0)); }

    private static void weapon(Map<Integer, Gear> result, int id, Mode mode, Ammunition ammunition, int rank)
    { result.put(id, new Gear(3, mode == Mode.BLOOD ? MAGIC : RANGED, mode, ammunition, rank)); }

    private static void shieldPreference(Map<Integer, Gear> result, int style, int... ids)
    { for (int id : ids) result.put(id, new Gear(5, MAGIC | RANGED, style, null, Ammunition.NONE, 0)); }

    static final int ATTEMPTS = 6, BLOCK_TICKS = 25;
    private final Map<Integer, Integer> attempts = new HashMap<>();
    private final Map<Integer, Long> requestedAt = new HashMap<>(), blockedUntil = new HashMap<>();

    void reset() { observedSlots.clear(); known.clear(); attempts.clear(); requestedAt.clear(); blockedUntil.clear(); }
    void observe(InfernoSnapshot f)
    {
        for (Item item : f.equipment) { attempts.remove(item.id); requestedAt.remove(item.id); blockedUntil.remove(item.id); }
        blockedUntil.values().removeIf(until -> until <= f.tick);

        requestedAt.entrySet().removeIf(e -> { boolean stale = f.tick - e.getValue() > 5; if (stale) attempts.remove(e.getKey()); return stale; });
        for (Item item : f.equipment) { observedSlots.put(item.id, item.slot); known.put(item.id, item); }
        for (Item item : f.inventory) known.put(item.id, item);
        known.keySet().removeIf(id -> f.inventory.stream().noneMatch(i -> i.id == id) && !f.worn(id));
    }

    private int slot(Item item)
    {
        Integer seen = observedSlots.get(item.id);
        if (seen != null) return seen;
        if (item.gear != null && item.gear.has("eq")) return item.gear.get("eq").getAsInt();
        Gear supported = SUPPORTED.get(item.id);
        if (supported != null) return supported.slot;
        String n = item.lower;
        if (weaponType(item) != null) return 3;
        if (n.contains("bolts") || n.contains("arrow")) return 13;
        if (n.contains("book") || n.contains("shield") || n.contains("ward") || n.contains("buckler")) return 5;
        if (n.contains("amulet") || n.contains("necklace")) return 2;
        if (n.contains("cape") || n.startsWith("ava's") || n.contains("assembler")) return 1;
        if (n.contains("robe top") || n.contains("robe-top") || n.endsWith("robegarb")
            || n.contains("body") || n.contains("leathertop") || n.contains("brassard") || n.contains("vestment")) return 4;
        if (n.contains("robe bottom") || n.contains("robe-bottom") || n.contains("chaps") || n.contains("skirt")
            || n.contains("tassets") || n.contains("legs")) return 7;
        if (n.contains("hat") || n.contains("hood") || n.contains("mask") || n.contains("helm")) return 0;
        if (n.contains("gloves") || n.contains("vambraces") || n.contains("gauntlets")) return 9;
        if (n.contains("boots")) return 10;
        if (n.contains("ring")) return 12;
        return -1;
    }

    static Mode weaponType(Item item)
    {
        Gear supported = SUPPORTED.get(item.id);
        if (supported != null) return supported.weapon;
        String n = item.lower;
        if (n.contains("blowpipe")) return Mode.BLOWPIPE;
        if (n.contains("chinchompa")) return Mode.CHINS;
        if (n.contains("crossbow") || n.contains("twisted bow") || n.contains("bow of faerdhinen")
            || n.contains("crystal bow") || n.contains("shortbow") || n.contains("longbow")) return Mode.LONG_RANGE;
        if (n.contains("wand") || n.contains("staff") || n.contains("sceptre")) return Mode.BLOOD;
        return null;
    }

    Item weapon(Mode mode)
    {
        Mode type = mode == Mode.ICE ? Mode.BLOOD : mode;
        Item best = null;
        for (Item item : known.values()) if (weaponType(item) == type)
            if (best == null || tier(item) > tier(best)) best = item;
        return best;
    }

    boolean available(Mode mode) { return weapon(mode) != null; }
    static int range(Item weapon)
    {
        if (weapon == null) return 0;
        Mode mode = weaponType(weapon);
        if (mode == Mode.BLOWPIPE) return 5;
        if (mode == Mode.CHINS) return 9;
        if (mode == Mode.BLOOD) return 10;
        if (ammunition(weapon) == Ammunition.BOLTS) return 8;
        if (weapon.lower.contains("shortbow")) return 7;
        return mode == Mode.LONG_RANGE ? 10 : 0;
    }
    int range(Mode mode) { return mode == Mode.ICE ? 10 : range(weapon(mode)); }

    static int speed(Item item)
    {
        if (item == null) return 0;
        Mode mode = weaponType(item);
        if (mode == Mode.BLOWPIPE) return 2;
        if (mode == Mode.BLOOD || ammunition(item) == Ammunition.BOLTS || item.id == 20997 || item.lower.contains("twisted bow")) return 5;
        if (mode == Mode.CHINS || item.lower.contains("bow of faerdhinen") || item.lower.contains("crystal bow")) return 4;
        return 0;
    }

    boolean twoHanded(Mode mode)
    {
        Item w = weapon(mode);
        return w != null && (mode == Mode.BLOWPIPE || mode == Mode.LONG_RANGE && ammunition(w) != Ammunition.BOLTS);
    }

    List<Item> items(InfernoSnapshot f, Mode mode, boolean ruby)
    {
        Item weapon = weapon(mode);
        List<Item> result = new ArrayList<>();
        if (weapon == null) return result;
        result.add(weapon);
        boolean mage = mode == Mode.ICE || mode == Mode.BLOOD;
        Map<Integer, Item> selected = new LinkedHashMap<>();
        for (Item item : known.values())
        {
            int slot = slot(item);
            if (slot < 0 || slot == 3 || slot == 5 && twoHanded(mode) || slot == 13 || blockedUntil.containsKey(item.id)) continue;
            int suitability = suitability(item, mage);
            if (suitability <= 0) continue;
            Item old = selected.get(slot);
            if (old == null || suitability > suitability(old, mage)) selected.put(slot, item);
        }
        Ammunition required = mode == Mode.LONG_RANGE ? ammunition(weapon) : Ammunition.NONE;
        if (required != Ammunition.NONE)
        {
            Item ammo = null;
            for (Item item : known.values()) if (slot(item) == 13 && ammunition(item) == required && !blockedUntil.containsKey(item.id))
            {
                int score = (required == Ammunition.BOLTS && item.lower.contains(ruby ? "ruby" : "diamond") ? 100 : 0) + tier(item);
                int old = ammo == null ? -1 : (required == Ammunition.BOLTS && ammo.lower.contains(ruby ? "ruby" : "diamond") ? 100 : 0) + tier(ammo);
                if (score > old) ammo = item;
            }
            if (ammo != null) selected.put(13, ammo);
        }
        for (int slot : mage ? MAGE_ORDER : RANGED_ORDER)
        {
            Item item = selected.get(slot);
            if (item != null) result.add(item);
        }
        return result;
    }

    private static Ammunition ammunition(Item item)
    {
        Gear supported = SUPPORTED.get(item.id);
        if (supported != null) return supported.ammunition;
        String name = item.lower;
        if (name.contains("bolts") || name.contains("crossbow")) return Ammunition.BOLTS;
        if (name.contains("arrow") || name.contains("twisted bow") || name.contains("shortbow")
            || name.contains("longbow")) return Ammunition.ARROWS;

        return Ammunition.NONE;
    }

    boolean ready(InfernoSnapshot f, Mode mode, boolean ruby)
    {
        List<Item> items = items(f, mode, ruby);
        return !items.isEmpty() && items.stream().allMatch(item -> f.worn(item.id));
    }



    private int suitability(Item item, boolean mage)
    {
        String n = item.lower;

        if (n.startsWith("void mage helm")) return mage ? 10_000 : 0;
        if (n.startsWith("void ranger helm")) return mage ? 0 : 10_000;
        if (n.startsWith("void melee helm")) return 0;

        if (item.gear != null && item.gear.has("eq"))
        {
            int bonus = mage ? stat(item, "magic") + Math.round(3 * (item.gear.has("mdmg") ? item.gear.get("mdmg").getAsFloat() : 0))
                : stat(item, "range") + stat(item, "rstr");
            return bonus > 0 ? 100 + bonus * 10 + stat(item, "prayer") : 0;
        }
        boolean magic = n.contains("occult") || n.contains("magic") || n.contains("mage") || n.contains("wizard")
            || n.contains("mystic") || n.contains("ancestral") || n.contains("virtus") || n.contains("ahrim")
            || n.contains("elder chaos") || n.contains("robe") || n.contains("arcane") || n.contains("elidinis")
            || n.contains("god cape") || n.contains("saradomin cape") || n.contains("guthix cape") || n.contains("zamorak cape");
        boolean ranged = n.startsWith("ava's") || n.contains("anguish") || n.contains("d'hide") || n.contains("dragonhide")
            || n.contains("karil") || n.contains("crystal") || n.contains("masori") || n.contains("ranger")
            || n.contains("archer") || n.contains("robin hood") || n.contains("armadyl") || n.contains("zaryte")
            || n.contains("leather") || n.contains("coif") || n.contains("dizana") || n.contains("buckler")
            || n.contains("rupture") || n.contains("assembler");
        Gear supported = SUPPORTED.get(item.id);
        if (supported != null)
        {
            if ((supported.styles & (mage ? MAGIC : RANGED)) == 0) return 0;

            boolean shared = supported.styles == (MAGIC | RANGED);
            return (shared && (supported.preferredStyles & (mage ? MAGIC : RANGED)) == 0 ? 10 : 100) + tier(item);
        }
        if (mage && magic || !mage && ranged) return 100 + tier(item);
        if (!mage && magic && slot(item) == 5) return 10 + tier(item);
        return 0;
    }

    private static int stat(Item item, String key)
    {
        return item.gear == null || !item.gear.has(key) ? 0 : item.gear.get(key).getAsInt();
    }

    private static int tier(Item item)
    {
        Gear supported = SUPPORTED.get(item.id);
        if (supported != null && supported.rank > 0) return supported.rank;
        String n = item.lower;
        if (n.contains("twisted bow") || n.contains("ancestral") || n.contains("masori") || n.contains("kodai") || n.contains("zaryte")) return 90;
        if (n.contains("faerdhinen") || n.contains("virtus") || n.contains("armadyl") || n.contains("blood ancient")) return 80;
        if (n.contains("dragon") || n.contains("occult") || n.contains("anguish") || n.contains("sceptre")) return 70;
        if (n.contains("master") || n.contains("ahrim") || n.contains("karil") || n.contains("crystal") || n.contains("assembler")) return 60;
        if (n.contains("rune") || n.contains("mystic") || n.contains("elder chaos") || n.contains("accumulator")) return 50;
        return 20;
    }
}
