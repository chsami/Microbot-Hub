package net.runelite.client.plugins.microbot.accountstarter.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import org.junit.jupiter.api.Test;

class HarlanClaimTest {
    private static List<Need> rangedNeeds(boolean ready) {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.RANGED, 1)).bankKnown(true).harlanReady(ready).build();
        return Needs.forTask(ApTask.RANGED_5, s);
    }

    @Test
    void readyOnlyAfterThirtyMinutes() {
        assertTrue(HarlanClaim.ready(0, 1_000));
        assertFalse(HarlanClaim.ready(1_000, 1_000 + HarlanClaim.COOLDOWN_MS - 1));
        assertTrue(HarlanClaim.ready(1_000, 1_000 + HarlanClaim.COOLDOWN_MS));
    }

    @Test
    void aSavedClaimSurvivesALoadAndNewClaimsAreSaved() {
        long[] saved = {0};
        HarlanClaim.load(1_000, ms -> saved[0] = ms);
        assertFalse(HarlanClaim.ready(1_000 + HarlanClaim.COOLDOWN_MS - 1));
        HarlanClaim.claimed(5_000);
        assertEquals(5_000, saved[0]);
        HarlanClaim.load(0, ms -> { });
    }

    @Test
    void aReadyHarlanMakesBowAndArrowsFree() {
        Need bow = rangedNeeds(true).get(0);
        assertEquals(FreeSource.HARLAN, bow.getFree());
        Resolution r = NeedResolver.resolve(bow, AccountSnapshot.builder().bankKnown(true).build(), false, 0, id -> 0, 1);
        assertEquals(Resolution.Kind.FREE, r.getKind());
    }

    private static Need arrowsNeed(boolean lowe, int coins) {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.RANGED, 1)).bankKnown(true).shopsAllowed(lowe).coins(coins).build();
        return Needs.forTask(ApTask.RANGED_5, s).stream().filter(n -> n.getLabel().equals("arrows")).findFirst().orElseThrow();
    }

    @Test
    void lowesIsOnlyAskedWhenAllowedAndCoinsCanPay() {
        assertEquals(FreeSource.LOWE, arrowsNeed(true, 100).getFree());
        assertNull(arrowsNeed(false, 100).getFree());
        assertNull(arrowsNeed(true, 0).getFree());
    }

    @Test
    void runesComeFromAuburyBetweenHarlansClaims() {
        AccountSnapshot s = AccountSnapshot.builder().levels(Map.of(Skill.MAGIC, 1)).bankKnown(true).shopsAllowed(true).coins(100).build();
        assertTrue(Needs.forTask(ApTask.MAGIC_5, s).stream().filter(n -> n.getLabel().equals("runes"))
                .allMatch(n -> n.getFree() == FreeSource.AUBURY));
    }

    @Test
    void shopPricesMatchTheWiki() {
        assertEquals(1, Needs.shopPrice(882));
        assertEquals(3, Needs.shopPrice(GearPlan.MIND));
        assertEquals(4, Needs.shopPrice(GearPlan.AIR));
    }

    @Test
    void betweenClaimsTheNeedsAreBoughtAsBefore() {
        assertNull(rangedNeeds(false).get(0).getFree());
    }
}
