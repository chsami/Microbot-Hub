package net.runelite.client.plugins.microbot.colosseumprayer;

import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.annotation.Nullable;

/**
 * Maps Fortis Colosseum manticore orb projectiles ({@link SpotanimID}) to protection prayers.
 */
final class ManticoreProjectilePrayers {

    private ManticoreProjectilePrayers() {
    }

    @Nullable
    static Rs2PrayerEnum prayerForProjectileId(int projectileId) {
        if (projectileId == SpotanimID.VFX_MANTICORE_01_PROJECTILE_MAGIC_01) {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        if (projectileId == SpotanimID.VFX_MANTICORE_01_PROJECTILE_RANGED_01) {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        if (projectileId == SpotanimID.VFX_MANTICORE_01_PROJECTILE_MELEE_01) {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        return null;
    }
}
