package net.runelite.client.plugins.microbot.attackrangesplus;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.combat.weapons.Melee;
import net.runelite.client.plugins.microbot.util.combat.weapons.Weapon;
import net.runelite.client.plugins.microbot.util.combat.weapons.WeaponsGenerator;

import javax.inject.Inject;
import java.util.Map;

/**
 * Resolves the attack radius (in tiles) for the overlay.
 *
 * <p><b>Player:</b> AUTO delegates to {@link Rs2Combat#getAttackRange()}, the maintained Microbot
 * helper that reads the equipped weapon and the selected attack style and returns the real range:
 * accurate per weapon, long-range aware, the spell range while autocasting, halberds = 2, and 1 for
 * melee/unknown. The manual modes are fixed representative ranges for previewing a style you are not
 * currently using.</p>
 *
 * <p><b>Opponent:</b> another player's attack-style varbits are not readable, so their range is
 * looked up from their equipped weapon id against the same weapon data Microbot uses
 * ({@link WeaponsGenerator#generate()}). That gives the weapon's base reach (no style modifier);
 * melee weapons resolve to 1 since their stored range is the special-attack range.</p>
 */
@Slf4j
public class AttackRangesPlusCalc
{
    @Inject
    private AttackRangesPlusConfig config;

    private static final int MELEE_RADIUS = 1;
    private static final int RANGED_PREVIEW_RADIUS = 7;
    private static final int MAGIC_RADIUS = 10;

    /**
     * Initialization-on-demand holder: the map is built once, thread-safely, on first use.
     * Same data Rs2Combat uses; reused for opponent weapon lookups.
     */
    private static final class WeaponsHolder
    {
        private static final Map<Integer, Weapon> MAP = WeaponsGenerator.generate();
    }

    /** Set once when the AUTO range lookup fails, so the fallback is logged once rather than per tick. */
    private boolean autoRangeFailureLogged;

    /**
     * @return the local player's attack radius in tiles (>= 0). Never throws.
     */
    public int getPlayerRangeRadius()
    {
        switch (config.style())
        {
            case MELEE:
                return MELEE_RADIUS;
            case RANGED:
                return RANGED_PREVIEW_RADIUS;
            case MAGIC:
                return MAGIC_RADIUS;
            case AUTO:
            default:
                try
                {
                    return Math.max(Rs2Combat.getAttackRange(), 0);
                }
                catch (Exception e)
                {
                    if (!autoRangeFailureLogged)
                    {
                        autoRangeFailureLogged = true;
                        log.debug("Rs2Combat.getAttackRange failed; falling back to melee radius", e);
                    }
                    return MELEE_RADIUS;
                }
        }
    }

    /**
     * Threat radius for another player's equipped weapon. Their attack style is not knowable, so
     * this is the weapon's base reach.
     *
     * @param weaponId the opponent's equipped weapon item id
     * @return the radius in tiles (>= 1)
     */
    public int getWeaponRadius(int weaponId)
    {
        final Weapon w = WeaponsHolder.MAP.get(weaponId);
        if (w == null)
        {
            return MELEE_RADIUS; // unknown weapon -> assume melee reach
        }
        if (w instanceof Melee)
        {
            return MELEE_RADIUS; // Melee.range stores the special-attack range, not normal reach
        }
        // "Accurate" yields the weapon's base (non-long-range) reach across all Weapon subtypes:
        // Halberd=2, ranged base, magic/powered-staff spell range.
        return Math.max(w.getRange("Accurate"), MELEE_RADIUS);
    }
}
