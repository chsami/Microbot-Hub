package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CombatStyleTest
{
    @Test
    public void overheadSpritesNameTheProtectedStyle()
    {
        assertEquals(CombatStyle.MELEE, CombatStyle.fromOverhead(new short[] {0}));
        assertEquals(CombatStyle.RANGED, CombatStyle.fromOverhead(new short[] {1}));
        assertEquals(CombatStyle.MAGIC, CombatStyle.fromOverhead(new short[] {2}));
    }

    @Test
    public void emptySlotsAreSkipped()
    {
        assertEquals(CombatStyle.MAGIC, CombatStyle.fromOverhead(new short[] {-1, 2}));
        assertEquals(CombatStyle.RANGED, CombatStyle.fromOverhead(new short[] {-1, -1, 1}));
    }

    @Test
    public void noRecognisedSpriteIsNull()
    {
        assertNull(CombatStyle.fromOverhead(null));
        assertNull(CombatStyle.fromOverhead(new short[0]));
        assertNull(CombatStyle.fromOverhead(new short[] {-1, -1}));
        assertNull(CombatStyle.fromOverhead(new short[] {3, 12}));
    }

    @Test
    public void theFirstRecognisedSlotWins()
    {
        assertEquals(CombatStyle.MAGIC, CombatStyle.fromOverhead(new short[] {-1, 2, 0}));
    }

    @Test
    public void attackAnimationsMapToStyles()
    {
        assertEquals(CombatStyle.MELEE, CombatStyle.fromAttackAnimation(TempleData.MELEE_ANIMATION));
        assertEquals(CombatStyle.RANGED, CombatStyle.fromAttackAnimation(TempleData.RANGED_ANIMATION));
        assertEquals(CombatStyle.MAGIC, CombatStyle.fromAttackAnimation(TempleData.MAGIC_ANIMATION));
        assertNull(CombatStyle.fromAttackAnimation(TempleData.BIND_ANIMATION));
        assertNull(CombatStyle.fromAttackAnimation(TempleData.DEATH_ANIMATION));
        assertNull(CombatStyle.fromAttackAnimation(-1));
    }

    @Test
    public void projectilesMapToRangedAndMagicOnly()
    {
        assertEquals(CombatStyle.RANGED, CombatStyle.fromProjectile(TempleData.RANGED_PROJECTILE));
        assertEquals(CombatStyle.MAGIC, CombatStyle.fromProjectile(TempleData.MAGIC_PROJECTILE));
        assertNull(CombatStyle.fromProjectile(TempleData.BOMB_PROJECTILE));
        assertNull(CombatStyle.fromProjectile(TempleData.BIND_PROJECTILE));
        assertNull(CombatStyle.fromProjectile(-1));
    }

    @Test
    public void everyStyleHasItsOwnProtectionAndSprite()
    {
        assertEquals(3, CombatStyle.values().length);
        assertEquals(Rs2PrayerEnum.PROTECT_MELEE, CombatStyle.MELEE.getProtection());
        assertEquals(Rs2PrayerEnum.PROTECT_RANGE, CombatStyle.RANGED.getProtection());
        assertEquals(Rs2PrayerEnum.PROTECT_MAGIC, CombatStyle.MAGIC.getProtection());
        assertEquals("Melee", CombatStyle.MELEE.toString());
        assertEquals(0, CombatStyle.MELEE.getOverheadSprite());
        assertEquals(1, CombatStyle.RANGED.getOverheadSprite());
        assertEquals(2, CombatStyle.MAGIC.getOverheadSprite());
    }
}
