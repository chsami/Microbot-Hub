/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.droinfernoprayers.local;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

/**
 * Offensive prayer clock, the Colosseum controller's: the prayer is raised for the tick before each shot and
 * dropped after it. Only the player's attack animations establish the clock, and switching target or stepping
 * does not reset it (the game's attack timer is the player's, not the target's).
 */
final class InfernoOffense
{
    private long attack = -1000, tick = -1;
    private int speed = 5, animation = -1, weaponSpeed;

    void reset() { attack = -1000; tick = -1; speed = 5; animation = -1; weaponSpeed = 0; }

    void observe(InfernoSnapshot f)
    {
        weaponSpeed = InfernoLoadout.speed(f.weapon());
        boolean contiguous = tick == f.tick - 1;
        if (tick >= 0 && f.tick != tick + 1) { attack = -1000; animation = -1; }
        boolean observed = false;
        for (JsonElement e : f.events)
        {
            if (!e.isJsonObject()) continue;
            JsonObject event = e.getAsJsonObject();
            if ("anim".equals(InfernoSnapshot.string(event, "e", "")) && "player".equals(InfernoSnapshot.string(event, "actor", "")))
            {
                int next = attackSpeed(InfernoSnapshot.integer(event, "anim", -1));
                if (next > 0) { speed = next; observed = true; }
            }
        }
        int next = attackSpeed(f.animation);
        // A resumed snapshot can show an animation already underway; only a native event or a transition
        // across consecutive snapshots establishes onset.
        if (contiguous && f.animation != animation && next > 0) { speed = next; observed = true; }
        if (observed) attack = f.tick;
        animation = f.animation; tick = f.tick;
    }

    /** True when the next tick fires a shot: the prayer must be up now. */
    boolean needed(InfernoSnapshot f, boolean requestingAttack)
    {
        if (attack < 0) return requestingAttack;
        long elapsed = f.tick - attack;
        if (requestingAttack && elapsed >= speed - 1) return true;
        return f.interacting >= 0 && elapsed >= 0 && elapsed % speed == speed - 1;
    }

    /** True when the weapon can fire next tick (or no shot has been seen yet). */
    boolean readyNext(InfernoSnapshot f)
    {
        return attack < 0 || f.tick + 1 - attack >= speed;
    }

    /**
     * Player attack animations: bows, crossbows, toxic and rosewood blowpipe (13144 attack, 13145 special),
     * chinchompas, staff/wand casts and barrages.
     */
    private static final Set<Integer> ATTACKS = Set.of(
        426, 427, 4230, 7552, 11465, 9166, 9168, 5061, 13144, 13145, 10656, 2779, 7618, 9858, 11057,
        711, 727, 1162, 1167, 7855, 11431, 811, 1978, 1979, 7753, 10092, 9492, 9493);

    private int attackSpeed(int animation)
    {
        if (animation == 5061 || animation == 13144 || animation == 13145) return 2;
        if (animation == 10092 || animation == 1978 || animation == 1979) return 5;
        if (!ATTACKS.contains(animation)) return 0;
        return weaponSpeed > 0 ? weaponSpeed : 5;
    }
}
