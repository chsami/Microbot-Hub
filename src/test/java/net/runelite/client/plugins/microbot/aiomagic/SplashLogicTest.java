package net.runelite.client.plugins.microbot.aiomagic;

import net.runelite.client.plugins.microbot.aiomagic.scripts.SplashGear;
import net.runelite.client.plugins.microbot.aiomagic.scripts.SplashLogic;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SplashLogicTest {
    private static Map<Runes, Integer> runes(Object... kv) {
        Map<Runes, Integer> m = new EnumMap<>(Runes.class);
        for (int i = 0; i < kv.length; i += 2) m.put((Runes) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private static final Map<Runes, Integer> PLENTY = runes(
            Runes.AIR, 5000, Runes.WATER, 5000, Runes.EARTH, 5000, Runes.FIRE, 5000, Runes.MIND, 5000,
            Runes.CHAOS, 5000, Runes.DEATH, 5000, Runes.BLOOD, 5000);

    @Test
    void bestSpellFollowsMagicLevel() {
        assertEquals(Rs2CombatSpells.WIND_STRIKE, SplashLogic.bestSpell(1, PLENTY, 1));
        assertEquals(Rs2CombatSpells.FIRE_STRIKE, SplashLogic.bestSpell(13, PLENTY, 1));
        assertEquals(Rs2CombatSpells.FIRE_BOLT, SplashLogic.bestSpell(40, PLENTY, 1));
        assertEquals(Rs2CombatSpells.FIRE_BLAST, SplashLogic.bestSpell(59, PLENTY, 1));
        assertEquals(Rs2CombatSpells.FIRE_BLAST, SplashLogic.bestSpell(60, PLENTY, 1));
        assertEquals(Rs2CombatSpells.FIRE_WAVE, SplashLogic.bestSpell(75, PLENTY, 1));
        assertEquals(Rs2CombatSpells.FIRE_SURGE, SplashLogic.bestSpell(99, PLENTY, 1));
        assertNull(SplashLogic.bestSpell(0, PLENTY, 1));
    }

    @Test
    void bestSpellFallsBackWhenRunesMissing() {
        Map<Runes, Integer> noDeath = runes(Runes.AIR, 5000, Runes.WATER, 5000, Runes.EARTH, 5000, Runes.FIRE, 5000,
                Runes.MIND, 5000, Runes.CHAOS, 5000);
        assertEquals(Rs2CombatSpells.FIRE_BOLT, SplashLogic.bestSpell(99, noDeath, 1));
        assertNull(SplashLogic.bestSpell(99, runes(Runes.FIRE, 100), 1));
    }

    @Test
    void staffAndMinCastsAreRespected() {
        Map<Runes, Integer> staff = SplashLogic.merge(runes(Runes.MIND, 50), SplashLogic.unlimited(List.of(Runes.AIR)));
        assertEquals(Rs2CombatSpells.WIND_STRIKE, SplashLogic.bestSpell(1, staff, 50));
        assertNull(SplashLogic.bestSpell(1, staff, 51));
        assertEquals(Integer.MAX_VALUE, SplashLogic.castsAvailable(Rs2CombatSpells.WIND_STRIKE.getRequiredRunes(),
                SplashLogic.unlimited(List.of(Runes.AIR, Runes.MIND))));
    }

    @Test
    void comboStaffProvidesBaseRunes() {
        Map<Runes, Integer> dust = SplashLogic.unlimited(List.of(Runes.DUST));
        assertEquals(Integer.MAX_VALUE, dust.get(Runes.AIR));
        assertEquals(Integer.MAX_VALUE, dust.get(Runes.EARTH));
    }

    @Test
    void runesToWithdrawForCastsPerTrip() {
        Map<Runes, Integer> req = Rs2CombatSpells.FIRE_STRIKE.getRequiredRunes(); // 3 fire, 2 air, 1 mind
        Map<Runes, Integer> need = SplashLogic.runesToWithdraw(req, 1000, runes(Runes.AIR, 500));
        assertEquals(3000, need.get(Runes.FIRE));
        assertEquals(1500, need.get(Runes.AIR));
        assertEquals(1000, need.get(Runes.MIND));
        Map<Runes, Integer> staff = SplashLogic.runesToWithdraw(req, 1000, SplashLogic.unlimited(List.of(Runes.FIRE, Runes.AIR)));
        assertEquals(Map.of(Runes.MIND, 1000), staff);
        assertTrue(SplashLogic.runesToWithdraw(req, 10, runes(Runes.FIRE, 99, Runes.AIR, 99, Runes.MIND, 99)).isEmpty());
    }

    @Test
    void affordableCastsRespectsGpCap() {
        Map<Runes, Integer> req = Rs2CombatSpells.WIND_STRIKE.getRequiredRunes(); // 1 air, 1 mind
        Map<Runes, Integer> prices = runes(Runes.AIR, 10, Runes.MIND, 20);
        assertEquals(1000, SplashLogic.affordableCasts(req, 1000, runes(), prices, 100_000));
        assertEquals(333, SplashLogic.affordableCasts(req, 1000, runes(), prices, 10_000));
        assertEquals(0, SplashLogic.affordableCasts(req, 1000, runes(), prices, 29));
        assertEquals(30_000, SplashLogic.estimateCost(SplashLogic.runesToWithdraw(req, 1000, runes()), prices));
    }

    @Test
    void splashSafetyThreshold() {
        assertTrue(SplashLogic.isSplashSafe(-65));
        assertTrue(SplashLogic.isSplashSafe(-80));
        assertFalse(SplashLogic.isSplashSafe(-64));
        assertFalse(SplashLogic.staffKeepsSplashSafe(-65, 0, 10));
        assertTrue(SplashLogic.staffKeepsSplashSafe(-75, 0, 10));
    }

    @Test
    void afkIntervalIsFifteenToNineteenMinutes() {
        Random r = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long ms = SplashLogic.nextAfkIntervalMs(r);
            assertTrue(ms >= 15 * 60_000L && ms <= 19 * 60_000L);
        }
        assertFalse(SplashLogic.isAfkClickDue(14 * 60_000L, 0, 15 * 60_000L));
        assertTrue(SplashLogic.isAfkClickDue(15 * 60_000L, 0, 15 * 60_000L));
    }

    @Test
    void targetAndDriftRules() {
        assertFalse(SplashLogic.isTargetTooFar(10));
        assertTrue(SplashLogic.isTargetTooFar(11));
        assertFalse(SplashLogic.hasDrifted(8));
        assertTrue(SplashLogic.hasDrifted(9));
        assertTrue(SplashLogic.isSafeDefaultNpc("Chicken", 1));
        assertFalse(SplashLogic.isSafeDefaultNpc("Chicken", 11));
        assertFalse(SplashLogic.isSafeDefaultNpc("Dark wizard", 7));
    }

    // ---- splash gear ----

    private static SplashGear.Piece p(int slot, String n, int bonus, long cost, boolean owned) {
        return new SplashGear.Piece(slot, n, n.hashCode(), bonus, cost, owned);
    }

    @Test
    void gearPicksCheapestSetReachingTarget() {
        List<SplashGear.Piece> c = new ArrayList<>();
        c.add(p(SplashGear.HEAD, "Iron full helm", -6, 100, false));
        c.add(p(SplashGear.BODY, "Iron platebody", -30, 300, false));
        c.add(p(SplashGear.LEGS, "Iron platelegs", -21, 200, false));
        c.add(p(SplashGear.SHIELD, "Iron kiteshield", -8, 150, false));
        c.add(p(SplashGear.HEAD, "Rune full helm", -6, 9000, false));
        c.add(p(SplashGear.CAPE, "Cheap cape", -2, 5, false));
        SplashGear.Plan plan = SplashGear.select(c, 0, -65, false);
        assertNotNull(plan);
        assertEquals(-65, plan.bonus);
        assertEquals(750, plan.cost);
        assertEquals(4, plan.pieces.size());
    }

    @Test
    void gearUsesOwnedPiecesFirstAndNeverPositive() {
        List<SplashGear.Piece> c = new ArrayList<>();
        c.add(p(SplashGear.BODY, "Owned platebody", -30, 0, true));
        c.add(p(SplashGear.HEAD, "Iron full helm", -6, 100, false));
        c.add(p(SplashGear.LEGS, "Iron platelegs", -21, 200, false));
        c.add(p(SplashGear.SHIELD, "Iron kiteshield", -8, 150, false));
        c.add(p(SplashGear.AMULET, "Amulet of magic", 10, 1, true));
        SplashGear.Plan plan = SplashGear.select(c, 0, -65, false);
        assertNotNull(plan);
        assertEquals(450, plan.cost);
        assertTrue(plan.pieces.stream().noneMatch(x -> x.bonus >= 0));
    }

    @Test
    void gearUnreachableOrStaffBaseOrTwoHandedShield() {
        List<SplashGear.Piece> c = new ArrayList<>();
        c.add(p(SplashGear.HEAD, "Iron full helm", -6, 100, false));
        c.add(p(SplashGear.BODY, "Iron platebody", -30, 300, false));
        c.add(p(SplashGear.LEGS, "Iron platelegs", -21, 200, false));
        c.add(p(SplashGear.SHIELD, "Iron kiteshield", -8, 150, false));
        assertNull(SplashGear.select(c, 10, -65, false)); // staff +10 needs -75
        assertNull(SplashGear.select(c, 0, -65, true));   // two-handed weapon blocks the shield
        assertNull(SplashGear.select(new ArrayList<>(), 0, -65, false));
    }

    @Test
    void gearMetalDefenceRequirements() {
        assertTrue(SplashGear.metalWearable(1, 1));   // iron
        assertFalse(SplashGear.metalWearable(2, 4));  // steel
        assertTrue(SplashGear.metalWearable(6, 40));  // rune
        assertEquals("Iron kiteshield", SplashGear.metalPieceName("Iron", "kiteshield"));
    }
}
