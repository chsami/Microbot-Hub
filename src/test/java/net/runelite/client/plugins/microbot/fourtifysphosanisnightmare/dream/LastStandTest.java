package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LastStandTest
{
    @Test
    public void thePlayerOutlastsHerWhenTheHitpointsLeftCoverHerRestAtThePaceSoFar()
    {
        LastStand stand = new LastStand();
        stand.observe(80, 30, 30);
        stand.observe(65, 20, 30);
        stand.observe(50, 20, 30);
        // 30 lost while she lost a third: 60 more before she dies, with 50 left.
        assertFalse(stand.outlasts(50));
        stand.observe(50, 3, 30);
        // 30 lost while she lost nine tenths: about 3 more.
        assertTrue(stand.outlasts(50));
    }

    @Test
    public void oneSleepwalkersHitIsKeptToSpare()
    {
        LastStand stand = new LastStand();
        stand.observe(60, 30, 30);
        stand.observe(45, 15, 30);
        assertFalse(stand.outlasts(30), "15 more expected and a hit to spare needs more than 30");
        assertTrue(stand.outlasts(31));
    }

    @Test
    public void beforeAnyHitOnlyOneSleepwalkersHitDecides()
    {
        LastStand stand = new LastStand();
        stand.observe(40, -1, -1);
        assertTrue(stand.outlasts(40));
        assertFalse(stand.outlasts(15));
    }

    @Test
    public void untilSheLosesHealthOnlyOneSleepwalkersHitDecides()
    {
        LastStand stand = new LastStand();
        stand.observe(99, -1, -1);
        stand.observe(84, -1, -1);
        stand.observe(69, 30, 30);
        assertTrue(stand.outlasts(69), "no pace yet while the player attacks a parasite");
        assertFalse(stand.outlasts(15));
    }

    @Test
    public void hitpointsHealedDoNotCountAgainstTheHitsTaken()
    {
        LastStand stand = new LastStand();
        stand.observe(30, 30, 30);
        stand.observe(15, 15, 30);
        stand.observe(40, 15, 30);
        // Only the 15 lost counts, so 15 more is expected.
        assertTrue(stand.outlasts(40));
        stand.observe(25, 15, 30);
        // 30 lost while she lost half: 30 more expected, which 25 Hitpoints do not cover.
        assertFalse(stand.outlasts(25), "the 25 healed does not offset the hits");
        stand.reset();
        stand.observe(40, -1, -1);
        assertTrue(stand.outlasts(40), "a reset starts the count again");
    }
}
