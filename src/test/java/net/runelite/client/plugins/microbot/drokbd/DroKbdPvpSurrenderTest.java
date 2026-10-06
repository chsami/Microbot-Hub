package net.runelite.client.plugins.microbot.drokbd;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroKbdPvpSurrenderTest
{
    @Test void followingWithoutAnimationDoesNotSurrender()
    {
        DroKbdPvpSurrender policy = new DroKbdPvpSurrender();
        assertFalse(policy.observe(true, DroKbdPvpSurrender.isAttackAction(true, -1), 100));
        assertFalse(DroKbdPvpSurrender.isAttackAction(false, 422));
        assertTrue(policy.observe(true, DroKbdPvpSurrender.isAttackAction(true, 422), 101));
    }

    @Test void stopsSurrenderAfterTenQuietGameTicks()
    {
        DroKbdPvpSurrender policy = new DroKbdPvpSurrender();
        assertTrue(policy.observe(true, true, 100));
        assertTrue(policy.observe(true, false, 109));
        assertFalse(policy.observe(true, false, 110));
    }

    @Test void repeatedWatcherPollsDoNotCountAsGameTicks()
    {
        DroKbdPvpSurrender policy = new DroKbdPvpSurrender();
        policy.observe(true, true, 100);
        for (int poll = 0; poll < 100; poll++) assertTrue(policy.observe(true, false, 101));
        assertFalse(policy.observe(true, false, 110));
    }

    @Test void newAttackRenewsTheWindow()
    {
        DroKbdPvpSurrender policy = new DroKbdPvpSurrender();
        policy.observe(true, true, 100);
        assertTrue(policy.observe(true, true, 109));
        assertTrue(policy.observe(true, false, 110));
        assertFalse(policy.observe(true, false, 119));
    }

    @Test void leavingWildernessClearsImmediately()
    {
        DroKbdPvpSurrender policy = new DroKbdPvpSurrender();
        policy.observe(true, true, 100);
        assertFalse(policy.observe(false, true, 101));
        assertFalse(policy.observe(true, false, 102));
    }

    @Test void deathLogoutOrStopResetAllowsAnotherTrip()
    {
        DroKbdPvpSurrender policy = new DroKbdPvpSurrender();
        policy.observe(true, true, 100);
        policy.reset();
        assertFalse(policy.observe(true, false, 101));
        assertTrue(policy.observe(true, true, 102));
    }

    @Test void worldTickResetDoesNotKeepStaleSurrender()
    {
        DroKbdPvpSurrender policy = new DroKbdPvpSurrender();
        policy.observe(true, true, 100);
        assertFalse(policy.observe(true, false, 1));
    }
}
