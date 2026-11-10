/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonObject;
import net.runelite.client.plugins.microbot.droinfernoprayers.local.InfernoSnapshot.Item;

/** Receipt-driven three-brew / restore / range cycle observed in the reference. */
final class InfernoSupplies
{
    static final int DEFAULT_VITALS_PERCENT = 55;
    static final int DEFAULT_RANGING_FROM_WAVE = 35;
    enum Kind { BREW, RESTORE, RANGED, REGEN, STAMINA }
    private static final String[] COMBAT_SKILLS = {"magic", "ranged", "attack", "strength", "defence"};
    private Kind pending;
    private Item requestedItem;
    private int baseline, brewCount, phase;
    private int beforeHp, beforePrayer, beforeRegen;
    private final int[] beforeSkills = new int[COMBAT_SKILLS.length];
    private boolean beforeStamina;
    private long requested = -1000, next = -1000;
    private long beforeHealersSince = -1;
    private int vitals = DEFAULT_VITALS_PERCENT, rangingFrom = DEFAULT_RANGING_FROM_WAVE;
    private int bloodLevel;
    private boolean topoff;

    void configure(int vitals, int rangingFrom)
    {
        // Explicit local controls: nonpositive legacy values are invalid, not a
        // request to infer an unknown server baseline.
        this.vitals = vitals <= 0 ? DEFAULT_VITALS_PERCENT : Math.min(vitals, 99);
        this.rangingFrom = rangingFrom <= 0 ? DEFAULT_RANGING_FROM_WAVE : Math.min(rangingFrom, 69);
    }
    void reset() { pending = null; requestedItem = null; baseline = brewCount = phase = bloodLevel = 0; requested = next = -1000; beforeHealersSince = -1; topoff = false; }
    void bloodRequirement(int level) { bloodLevel = InfernoSnapshot.supportedBloodLevel(level); }

    void observe(InfernoSnapshot f)
    {
        if (pending == null || f.tick <= requested) return;
        int delta = baseline - doses(f, pending);
        if (delta == 1 && drinkReceipt(f))
        {
            Kind consumed = pending;
            pending = null; requestedItem = null; next = Math.max(requested + 4, f.tick);
            if (consumed == Kind.BREW) { brewCount++; if (brewCount >= 3 || topoff && f.hp >= f.maxHp) phase = 2; }
            if (consumed == Kind.RESTORE && phase == 2) { brewCount = 0; phase = 3; }
            if (consumed == Kind.RANGED && phase == 3) phase = 0;
        }
        else if (delta != 0)
        {
            // A loss elsewhere, or disappearance without a drink receipt, cannot advance the cycle.
            pending = null; requestedItem = null;
        }
    }

    JsonObject next(InfernoSnapshot f, int wave)
    {
        return next(f, wave, false, false);
    }

    JsonObject next(InfernoSnapshot f, int wave, boolean beforeHealers, boolean fightingHealers)
    {
        if (!beforeHealers) beforeHealersSince = -1;
        else if (beforeHealersSince < 0) beforeHealersSince = f.tick;
        // The reference starts its topoff on the snapshot after the quarter-health threshold.
        boolean topoffReady = beforeHealers && f.tick > beforeHealersSince;
        if (f.tick < next) return null;
        // A low-HP brew (or a prayer emergency restore) must not wait behind the retry of a lesser drink.
        if (pending != null && pending != Kind.BREW && pending != Kind.RESTORE && phase == 0
            && (f.hp * 100L <= f.maxHp * (long) vitals && first(f, Kind.BREW) != null
                || f.prayer <= 15 && first(f, Kind.RESTORE) != null))
        { pending = null; requestedItem = null; }
        if (pending != null)
        {
            if (f.tick - requested < 4) return null;
            Kind retry = pending; pending = null;
            return request(f, retry);
        }
        if (f.prayer <= 15 && f.prayer < f.maxPrayer && first(f, Kind.RESTORE) != null)
            return request(f, Kind.RESTORE);
        if (phase == 2)
        {
            JsonObject result = request(f, Kind.RESTORE);
            if (result != null) return result;
            phase = 3;
        }
        if (phase == 3)
        {
            if (rangingAllowed(wave) && f.ranged <= f.baseRanged && first(f, Kind.RANGED) != null && !rangingReserved(f, wave))
                return request(f, Kind.RANGED);
            phase = 0; topoff = false;
        }
        if (phase == 1)
        {
            JsonObject brew = request(f, Kind.BREW);
            if (brew != null) return brew;
            phase = 2;
            return request(f, Kind.RESTORE);
        }
        int recoveryPercent = fightingHealers ? Math.max(vitals, 65) : vitals;
        if ((f.hp * 100L <= f.maxHp * (long) recoveryPercent || topoffReady)
            && f.hp < f.maxHp && first(f, Kind.BREW) != null)
        {
            phase = 1; brewCount = 0; topoff = topoffReady;
            return request(f, Kind.BREW);
        }
        // Restores can recover a drained base level, but cannot sustain a manually boosted higher-tier spell.
        if ((f.magic < f.baseMagic - 5 || f.magic < Math.min(bloodLevel, f.baseMagic) || f.magic < f.iceRestoreLevel())
            && first(f, Kind.RESTORE) != null)
            return request(f, Kind.RESTORE);
        if (wave >= 35 && f.prayer <= 35 && InfernoSnapshot.integer(f.player, "prayerRegenTimer", 0) == 0)
        {
            JsonObject regen = request(f, Kind.REGEN);
            if (regen != null) return regen;
        }
        if (rangingAllowed(wave) && f.ranged <= f.baseRanged && !rangingReserved(f, wave))
        {
            JsonObject range = request(f, Kind.RANGED);
            if (range != null) return range;
        }
        if (f.runEnergy < 2500 && !InfernoSnapshot.bool(f.player, "staminaActive", false))
            return request(f, Kind.STAMINA);
        return null;
    }

    /**
     * The last ranging potion is kept for the Jad waves and Zuk: both long recordings stop ranging with exactly
     * four doses left (success run: none between waves 61 and 68 despite two brew cycles) and resume from wave 68.
     */
    static final int RANGING_RESERVE_DOSES = 4, RANGING_RESERVE_UNTIL_WAVE = 68;

    private static boolean rangingReserved(InfernoSnapshot f, int wave)
    {
        return wave < RANGING_RESERVE_UNTIL_WAVE && doses(f, Kind.RANGED) <= RANGING_RESERVE_DOSES;
    }

    private boolean rangingAllowed(int wave)
    {
        // Wave 1 means use potions from the start, including the opening before
        // the first wave message arrives. It does not fabricate an observed wave.
        return rangingFrom == 1 || wave >= rangingFrom;
    }

    private JsonObject request(InfernoSnapshot f, Kind kind)
    {
        Item item = first(f, kind);
        if (item == null) return null;
        pending = kind; baseline = doses(f, kind); requested = f.tick;
        requestedItem = item; beforeHp = f.hp; beforePrayer = f.prayer;
        beforeRegen = InfernoSnapshot.integer(f.player, "prayerRegenTimer", 0);
        beforeStamina = InfernoSnapshot.bool(f.player, "staminaActive", false);
        JsonObject boosted = InfernoSnapshot.object(InfernoSnapshot.object(f.player, "skills"), "boosted");
        for (int i = 0; i < COMBAT_SKILLS.length; i++)
            beforeSkills[i] = InfernoSnapshot.integer(boosted, COMBAT_SKILLS[i], Integer.MIN_VALUE);
        return InfernoDecisionEngine.command("inventory.interact", "slot", item.slot, "itemId", item.id, "option", "Drink");
    }

    private boolean drinkReceipt(InfernoSnapshot f)
    {
        if (requestedItem == null || requestedItem.slot < 0 || requestedItem.qty != 1) return false;
        Item current = null;
        for (Item item : f.inventory)
            if (item.slot == requestedItem.slot && item.id >= 0 && item.qty > 0) { current = item; break; }
        int beforeDose = itemDose(requestedItem);
        if (beforeDose > 1)
        {
            // Match the requested slot and potion, not only the aggregate supply kind.
            return current != null && current.qty == 1 && current.id != requestedItem.id
                && itemDose(current) == beforeDose - 1
                && potionName(current).equals(potionName(requestedItem));
        }
        if (beforeDose != 1) return false;
        if (current != null) return current.qty == 1 && current.lower.equals("vial");
        // Barbarian vial smashing leaves an empty slot. An observed effect is
        // needed to distinguish it from dropping the last-dose potion.
        return drinkEffect(f);
    }

    private boolean drinkEffect(InfernoSnapshot f)
    {
        if (pending == Kind.BREW && f.hp > beforeHp || pending == Kind.RESTORE && f.prayer > beforePrayer) return true;
        if (pending == Kind.REGEN)
            return InfernoSnapshot.integer(f.player, "prayerRegenTimer", 0) > beforeRegen;
        if (pending == Kind.STAMINA)
            return !beforeStamina && InfernoSnapshot.bool(f.player, "staminaActive", false);
        JsonObject skills = InfernoSnapshot.object(f.player, "skills");
        JsonObject boosted = InfernoSnapshot.object(skills, "boosted"), real = InfernoSnapshot.object(skills, "real");
        for (int i = 0; i < COMBAT_SKILLS.length; i++)
        {
            int before = beforeSkills[i], after = InfernoSnapshot.integer(boosted, COMBAT_SKILLS[i], Integer.MIN_VALUE);
            if (before == Integer.MIN_VALUE || after == Integer.MIN_VALUE) continue;
            if (pending == Kind.BREW && (i == 4 ? after > before : after < before)) return true;
            if (pending == Kind.RANGED && i == 1 && after > before) return true;
            if (pending == Kind.RESTORE && after > before
                && before < InfernoSnapshot.integer(real, COMBAT_SKILLS[i], before)) return true;
        }
        return false;
    }

    private static int itemDose(Item item)
    {
        int at = item.lower.lastIndexOf('(');
        if (at < 0 || at + 3 != item.lower.length() || item.lower.charAt(at + 2) != ')') return 0;
        int dose = item.lower.charAt(at + 1) - '0';
        return dose >= 1 && dose <= 4 ? dose : 0;
    }

    private static String potionName(Item item)
    {
        int at = item.lower.lastIndexOf('(');
        return at >= 0 ? item.lower.substring(0, at) : item.lower;
    }

    static boolean matches(Item item, Kind kind)
    {
        String n = item.lower;
        switch (kind)
        {
            case BREW: return n.startsWith("saradomin brew(");
            case RESTORE: return n.startsWith("super restore(") || n.startsWith("blighted super restore(");
            case RANGED: return n.startsWith("ranging potion(") || n.startsWith("bastion potion(")
                || n.startsWith("divine ranging potion(") || n.startsWith("divine bastion potion(");
            case REGEN: return n.startsWith("prayer regeneration potion(");
            case STAMINA: return n.startsWith("stamina potion(") || n.startsWith("extended stamina potion(");
            default: return false;
        }
    }

    private static Item first(InfernoSnapshot f, Kind kind)
    {
        Item selected = null;
        for (Item item : f.inventory)
            if (matches(item, kind) && item.qty > 0 && itemDose(item) > 0
                && !(item.lower.startsWith("divine ") && f.hp <= 10)
                && (selected == null || itemDose(item) < itemDose(selected))) selected = item;
        return selected;
    }

    static int doses(InfernoSnapshot f, Kind kind)
    {
        int count = 0;
        for (Item item : f.inventory) if (matches(item, kind))
        {
            int at = item.name.lastIndexOf('(');
            if (at >= 0 && at + 1 < item.name.length())
            {
                int dose = item.name.charAt(at + 1) - '0';
                if (dose >= 1 && dose <= 4) count += dose * item.qty;
            }
        }
        return count;
    }

    boolean recovering() { return phase == 1 || phase == 2; }
}
