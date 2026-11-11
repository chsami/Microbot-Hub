package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare;

import java.util.ArrayList;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.AttackStyle;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.NightmareFight;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Stage;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PluginLifecycleTest
{
    private static final String DIED = "Player died; collect your items from Sister Senga before restarting";

    @Test
    public void startingInHerDreamAttacksHerOnTheFirstTick() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        assertNotNull(fixture.plugin.getStartTime());
        assertTrue(fixture.overlayShown());
        fixture.tick();
        assertEquals(1, fixture.requests());
        assertEquals("Attacking The Nightmare", fixture.plugin.getStatus());
    }

    @Test
    public void enablingLoggedOutWaitsForLoginBeforeStartingRuntimeAndOverlay() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.state = GameState.LOGIN_SCREEN;
        fixture.enableAndDrain();
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlayShown());
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertNotNull(fixture.plugin.getStartTime());
        assertTrue(fixture.overlayShown());
        assertEquals(1, fixture.requests());
    }

    @Test
    public void loggedInWithoutALocalPlayerStillWaits() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.playerPresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertNull(fixture.plugin.getStartTime());
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertFalse(fixture.overlayShown());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void disableImmediatelyStopsQueuedRequestsBeforeQueuedCleanup() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        assertEquals(0, fixture.requests());
        fixture.disable();
        fixture.clientTick();
        fixture.tick();
        assertEquals(0, fixture.requests());
        assertEquals(1, fixture.thread.pending.size());
        fixture.thread.drain();
        fixture.actions.dispatch();
        assertEquals(0, fixture.requests());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlayShown());
        assertEquals("Stopped", fixture.plugin.getStatus());
    }

    @Test
    public void queuedStartupCannotActAfterDisable() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enable();
        fixture.disable();
        fixture.thread.drain();
        fixture.tick();
        assertEquals(0, fixture.requests());
        assertNull(fixture.plugin.getStartTime());
        assertEquals("Stopped", fixture.plugin.getStatus());
        assertFalse(fixture.overlayShown());
    }

    @Test
    public void rapidReenableRejectsOldCleanup() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.disable();
        fixture.enable();
        fixture.plugin.setStatus("New enable");
        fixture.thread.runNext();
        assertEquals("New enable", fixture.plugin.getStatus());
        fixture.thread.runNext();
        assertNotNull(fixture.plugin.getStartTime());
        assertTrue(fixture.overlayShown());
        fixture.tick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void logoutCancelsQueuedRequestsAndLoginResumesTheSameSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Instant start = fixture.plugin.getStartTime();
        fixture.decisionOnly();
        fixture.world.state = GameState.LOGIN_SCREEN;
        stateChanged(fixture, GameState.LOGIN_SCREEN);
        fixture.clientTick();
        for (int index = 0; index < 5; index++)
        {
            fixture.tick();
        }
        assertEquals(0, fixture.requests());
        assertEquals("Waiting for login", fixture.plugin.getStatus());
        assertSame(start, fixture.plugin.getStartTime());
        fixture.world.state = GameState.LOGGED_IN;
        fixture.tick();
        assertEquals(1, fixture.requests());
        assertSame(start, fixture.plugin.getStartTime());
    }

    @Test
    public void loadingSceneCancelsQueuedRequests() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.world.state = GameState.LOADING;
        stateChanged(fixture, GameState.LOADING);
        fixture.clientTick();
        fixture.world.state = GameState.LOGGED_IN;
        fixture.actions.dispatch();
        assertEquals(0, fixture.requests());
        assertEquals("Waiting for scene", fixture.plugin.getStatus());
    }

    @Test
    public void stateChangesThatKeepTheLoginDoNotCancelQueuedRequests() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        stateChanged(fixture, GameState.LOGGED_IN);
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void aSettingChangeKeepsTheSessionAndItsFight() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        fight.setCursed(true);
        Instant started = fixture.plugin.getStartTime();
        Object trip = fixture.read("trip");
        for (String key : new String[] {"eatAt", "meleeGear", "keepItems", "lootAlways"})
        {
            configChanged(fixture, key);
            fixture.thread.drain();
        }
        assertSame(started, fixture.plugin.getStartTime());
        assertSame(trip, fixture.read("trip"));
        assertSame(TripPhase.FIGHTING, ((TripState) trip).getPhase());
        assertTrue(fight.isCursed(), "the curse is still known");
    }

    @Test
    public void changedItemSettingsApplyWithoutARestart() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.config.melee = "4587";
        fixture.config.keep = "1234";
        configChanged(fixture, "meleeGear");
        fixture.thread.drain();
        assertEquals(Set.of(4587), fixture.read("melee"));
        assertTrue(((Set<?>) fixture.read("kept")).contains(1234));
    }

    @Test
    public void anInvalidItemListEnteredWhileRunningStopsTheSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.config.melee = "Torva";
        configChanged(fixture, "meleeGear");
        assertEquals(1, fixture.thread.pending.size(), "the check waits for the client thread");
        fixture.thread.drain();
        fixture.decisionOnly();
        assertTrue(fixture.plugin.getStatus().startsWith("Melee gear IDs: "), fixture.plugin.getStatus());
        assertNotNull(fixture.plugin.getStartTime());
    }

    @Test
    public void fixingTheNamedSettingAfterAStopStartsANewSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        String melee = fixture.config.melee;
        fixture.config.melee = "Torva";
        configChanged(fixture, "meleeGear");
        fixture.thread.drain();
        fixture.decisionOnly();
        assertTrue(fixture.plugin.getStatus().startsWith("Melee gear IDs"));
        fixture.config.melee = melee;
        configChanged(fixture, "meleeGear");
        fixture.thread.drain();
        assertEquals("Starting", fixture.plugin.getStatus());
    }

    @Test
    public void aSettingChangeDoesNotRestartAStopTheSettingsDidNotCause() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.FOOD, 5);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Carry or wear a Ring of dueling, then restart the plugin", fixture.plugin.getStatus());
        Instant started = fixture.plugin.getStartTime();
        configChanged(fixture, "eatAt");
        fixture.thread.drain();
        fixture.decisionOnly();
        assertEquals("Carry or wear a Ring of dueling, then restart the plugin", fixture.plugin.getStatus());
        assertSame(started, fixture.plugin.getStartTime());
        assertEquals(1, fixture.world.chat.size(), "the stop is not reported a second time");
    }

    @Test
    public void showingOrHidingTheOverlayKeepsTheSessionAndItsCounters() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Instant started = fixture.plugin.getStartTime();
        fixture.decisionOnly();
        configChanged(fixture, "showOverlay");
        assertTrue(fixture.thread.pending.isEmpty());
        assertSame(started, fixture.plugin.getStartTime());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void settingsOfOtherGroupsAreIgnored() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        ConfigChanged event = new ConfigChanged();
        event.setGroup("someOtherPlugin");
        event.setKey("eatAt");
        fixture.plugin.onConfigChanged(event);
        assertTrue(fixture.thread.pending.isEmpty());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void movementAndForeignWorldViewCannotDispatch() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.standAt(new WorldPoint(4134, 4382, 0));
        fixture.clientTick();
        assertEquals(0, fixture.requests());
        fixture.world.topLevel = false;
        for (int index = 0; index < 5; index++)
        {
            fixture.tick();
        }
        assertEquals(0, fixture.requests());
    }

    @Test
    public void deathBeforeDispatchRejectsThePendingRequestAndStopsTheSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        fixture.world.dead = true;
        fixture.world.hitpoints = 0;
        fixture.clientTick();
        assertEquals(0, fixture.requests());
        fixture.tick();
        assertEquals(DIED, fixture.plugin.getStatus());
        fixture.world.dead = false;
        fixture.world.hitpoints = 99;
        fixture.tick();
        assertEquals(0, fixture.requests());
        assertEquals(DIED, fixture.plugin.getStatus());
    }

    @Test
    public void aDeathFlagLeftAfterTheRespawnDoesNotStopTheSession() throws Exception
    {
        // The flag can outlast the respawn until a hit refreshes it; full Hitpoints show the player is alive.
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.dead = true;
        fixture.enableAndDrain();
        fixture.tick();
        assertNotEquals(DIED, fixture.plugin.getStatus());
    }

    @Test
    public void requestsDispatchOnlyBeforeTheTwentyNinthClientCycle() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.decisionOnly();
        assertEquals(0, fixture.requests());
        fixture.world.gameCycle += 29;
        fixture.clientTick();
        assertEquals(0, fixture.requests());
        fixture.world.gameCycle--;
        fixture.clientTick();
        assertEquals(1, fixture.requests());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void nextGameTickExpiresOldRequestsAndAdmitsAFreshDecision() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        for (int index = 0; index < 5; index++)
        {
            fixture.decisionOnly();
        }
        assertEquals(0, fixture.requests());
        fixture.clientTick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void startingBeforeTheInventoryArrivesWaitsInsteadOfFailing() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventoryPresent = false;
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Waiting for inventory", fixture.plugin.getStatus());
        assertNull(fixture.plugin.getStartTime());
        assertFalse(fixture.overlayShown());
        assertEquals(0, fixture.requests());
        fixture.world.inventoryPresent = true;
        fixture.tick();
        assertNotNull(fixture.plugin.getStartTime());
        assertEquals(1, fixture.requests());
    }

    @Test
    public void aRunningSessionWaitsWhenTheInventoryDisappears() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.inventoryPresent = false;
        fixture.tick();
        assertEquals("Waiting for inventory", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void startingAwayFromFeroxTheSanctuaryAndTheDreamFailsWithAReason() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.instance = false;
        fixture.standAt(new WorldPoint(3200, 3200, 0));
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Walk to Ferox Enclave, the Sisterhood Sanctuary or into her dream, then restart the plugin", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void aBadItemIdNamesTheSettingAndTheFormat() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.melee = "Torva,";
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Melee gear IDs: \"Torva\" is not an item ID; use whole numbers separated by commas, like 4675,9185",
            fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void aWeaponSettingTakesOneItemId() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.husk = "34027,4151";
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Husk weapon ID: enter one item ID", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void aTrailingCommaInAnItemIdListIsIgnored() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.magic = PluginTestFixture.STAFF + ",";
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Attacking The Nightmare", fixture.plugin.getStatus());
    }

    @Test
    public void aStopIsSentToTheChatAndAsANotificationOnce() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.FOOD, 5);
        fixture.enableAndDrain();
        fixture.tick();
        fixture.tick();
        String message = "[Phosani's Nightmare] Stopped: Carry or wear a Ring of dueling, then restart the plugin";
        assertEquals(List.of(message), fixture.world.chat);
        assertEquals(List.of(message), fixture.notifications);
    }

    @Test
    public void pausedScriptsStopTheFightUntilResumed() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        Microbot.pauseAllScripts.set(true);
        try
        {
            fixture.tick();
            assertEquals("Paused", fixture.plugin.getStatus());
            assertEquals(0, fixture.requests());
        }
        finally
        {
            Microbot.pauseAllScripts.set(false);
        }
        fixture.tick();
        assertEquals(1, fixture.requests());
    }

    @Test
    public void startingWithoutBothGearSetsFailsWithAReason() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.magic = "";
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Fill in Melee gear IDs and Magic gear IDs in the Equipment section", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void startingInTheDreamWithoutARingFailsWithAReason() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.FOOD, 5, PluginTestFixture.STAFF, 1);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Carry or wear a Ring of dueling, then restart the plugin", fixture.plugin.getStatus());
        assertEquals(0, fixture.requests());
    }

    @Test
    public void startingAtFeroxWithoutARingBanksToFetchOne() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.FOOD, 5);
        fixture.world.instance = false;
        fixture.standAt(DreamData.FEROX_BANK);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Opening the bank", fixture.plugin.getStatus());
    }

    @Test
    public void aFullInventoryWithSuppliesMissingStopsOnceAndNamesWhatToLower() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.keep = "1234";
        int[] slots = new int[56];
        int[] carried = {PluginTestFixture.RING, PluginTestFixture.STAFF, DreamData.MEDALLION};
        for (int slot = 0; slot < 28; slot++)
        {
            slots[slot * 2] = slot < carried.length ? carried[slot] : 1234;
            slots[slot * 2 + 1] = 1;
        }
        fixture.world.inventory = TestWorld.items(slots);
        fixture.world.bank = TestWorld.items(PluginTestFixture.FOOD, 50);
        fixture.world.bankVisible = true;
        fixture.world.instance = false;
        fixture.standAt(DreamData.FEROX_BANK);
        fixture.enableAndDrain();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        String stop = "The inventory is full with item 385 still missing; lower Food count or carry fewer Keep items, then restart the plugin";
        assertEquals(stop, fixture.plugin.getStatus());
        assertEquals(List.of("[Phosani's Nightmare] Stopped: " + stop), fixture.world.chat, "told once");
        assertEquals(0, fixture.requests(), "no withdrawal the game would refuse");
    }

    @Test
    public void theBankPinWaitIsToldOnceInTheChatAndAsANotification() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.instance = false;
        fixture.world.pinVisible = true;
        fixture.standAt(DreamData.FEROX_BANK);
        fixture.enableAndDrain();
        fixture.plugin.setTickDelay(0);
        for (int tick = 0; tick < 8; tick++)
        {
            fixture.tick();
        }
        assertEquals("Enter your bank PIN to continue", fixture.plugin.getStatus());
        String message = "[Phosani's Nightmare] Waiting for your bank PIN: type it in the bank window, and check the PIN saved in your"
            + " Microbot profile; the plugin continues after the bank opens";
        assertEquals(List.of(message), fixture.world.chat);
        assertEquals(List.of(message), fixture.notifications);
    }

    @Test
    public void aThrallThatCannotBeCastIsToldOnce() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.thrall = true;
        fixture.boss.id = 9419;
        fixture.world.equipment = TestWorld.items(PluginTestFixture.STAFF, 1);
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.FOOD, 5, PluginTestFixture.WHIP, 1);
        fixture.inject("castable", (Predicate<Spell>) spell -> false);
        fixture.enableAndDrain();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        String message = "[Phosani's Nightmare] A ghostly thrall cannot be cast: use the Arceuus spellbook and carry its runes, or turn off"
            + " Ghostly thrall";
        assertEquals(List.of(message), fixture.world.chat);
        assertEquals(List.of(message), fixture.notifications);
    }

    @Test
    public void aDeathChargeThatCannotBeCastIsToldOnce() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.config.charge = true;
        fixture.config.spec = "13652";
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.FOOD, 5, PluginTestFixture.STAFF, 1, 13652, 1);
        fixture.inject("castable", (Predicate<Spell>) spell -> false);
        fixture.enableAndDrain();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        assertEquals(List.of("[Phosani's Nightmare] Death Charge cannot be cast: use the Arceuus spellbook and carry its runes, or turn off"
            + " Death Charge"), fixture.world.chat);
    }

    @Test
    public void withGhostlyThrallOnBankingKeepsTheBookOfTheDead() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        assertFalse(((Set<?>) fixture.read("kept")).contains(DreamData.BOOK_OF_THE_DEAD));
        fixture.config.thrall = true;
        configChanged(fixture, "ghostlyThrall");
        fixture.thread.drain();
        assertTrue(((Set<?>) fixture.read("kept")).contains(DreamData.BOOK_OF_THE_DEAD));
    }

    @Test
    public void breaksWaitFromHerDreamUntilFeroxEnclave() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        try
        {
            fixture.enableAndDrain();
            fixture.tick();
            assertTrue(BreakHandlerScript.isLockState(), "fighting in her dream");
            ((TripState) fixture.read("trip")).leave("Out of food");
            fixture.plugin.setTickDelay(0);
            fixture.tick();
            assertTrue(BreakHandlerScript.isLockState(), "leaving her dream");
            fixture.world.instance = false;
            fixture.standAt(DreamData.FEROX_BANK);
            fixture.tick();
            fixture.tick();
            assertFalse(BreakHandlerScript.isLockState(), "back at Ferox Enclave");
        }
        finally
        {
            BreakHandlerScript.setLockState(false);
        }
    }

    @Test
    public void stoppingThePluginReleasesBreaks() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        try
        {
            fixture.enableAndDrain();
            fixture.tick();
            assertTrue(BreakHandlerScript.isLockState());
            fixture.disable();
            fixture.thread.drain();
            assertFalse(BreakHandlerScript.isLockState());
        }
        finally
        {
            BreakHandlerScript.setLockState(false);
        }
    }

    @Test
    public void aMagicWeaponWithNoChargesEndsTheTripAndStopsAtTheBank() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        assertSame(TripPhase.FIGHTING, ((TripState) fixture.read("trip")).getPhase());
        // The game's own wording capitalises the weapon's name differently; either is read.
        fixture.plugin.onChatMessage(message("Tumeken's Shadow has no charges! You need to charge it with soul runes and chaos runes."));
        assertSame(TripPhase.LEAVING, ((TripState) fixture.read("trip")).getPhase());
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Teleporting to Ferox Enclave: Tumeken's Shadow has no charges", fixture.plugin.getStatus());
        fixture.world.instance = false;
        fixture.standAt(DreamData.FEROX_BANK);
        fixture.tick();
        fixture.tick();
        assertEquals("Tumeken's Shadow has no charges; charge it, then restart the plugin", fixture.plugin.getStatus(),
            "another trip cannot charge it");
    }

    @Test
    public void aMagicWeaponWithNoChargesAtTheBankStopsAtOnce() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.instance = false;
        fixture.standAt(DreamData.FEROX_BANK);
        fixture.enableAndDrain();
        fixture.tick();
        fixture.plugin.onChatMessage(message("Tumeken's shadow has no charges! You need to charge it with soul runes and chaos runes."));
        fixture.tick();
        assertEquals("Tumeken's shadow has no charges; charge it, then restart the plugin", fixture.plugin.getStatus());
    }

    @Test
    public void aCarriedMissingGearItemIsWornBeforeAttacking() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.equipment = TestWorld.items();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.WHIP, 1);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Switching to melee gear", fixture.plugin.getStatus());
    }

    @Test
    public void aGearItemMissingOnTheWayInStopsAtOnce() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.equipment = TestWorld.items();
        fixture.world.instance = false;
        fixture.standAt(DreamData.SLEPE_ARRIVAL);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Missing item 4151: carry it or remove it from your equipment IDs, then restart the plugin",
            fixture.plugin.getStatus());
    }

    @Test
    public void everyMissingGearItemIsNamedInOneStop() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.equipment = TestWorld.items();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, PluginTestFixture.FOOD, 5);
        fixture.world.instance = false;
        fixture.standAt(DreamData.SLEPE_ARRIVAL);
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals("Missing item 4151, item 4675: carry them or remove them from your equipment IDs, then restart the plugin",
            fixture.plugin.getStatus());
    }

    @Test
    public void everyGearItemMissingFromTheBankIsNamedInOneStop() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.equipment = TestWorld.items();
        fixture.world.inventory = TestWorld.items(PluginTestFixture.RING, 1, DreamData.MEDALLION, 1);
        fixture.world.bank = TestWorld.items(PluginTestFixture.FOOD, 50);
        fixture.world.bankVisible = true;
        fixture.world.instance = false;
        fixture.standAt(DreamData.FEROX_BANK);
        fixture.enableAndDrain();
        for (int tick = 0; tick < 3; tick++)
        {
            fixture.tick();
        }
        assertEquals("Missing item 4151, item 4675: put them in your bank or remove them from your equipment IDs, then restart the plugin",
            fixture.plugin.getStatus());
        assertEquals(1, fixture.world.chat.size(), "one stop");
    }

    @Test
    public void aGearItemMissingInTheFightEndsTheTripAndStopsAtTheBankWithItsId() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.world.equipment = TestWorld.items();
        fixture.enableAndDrain();
        fixture.tick();
        assertSame(TripPhase.LEAVING, ((TripState) fixture.read("trip")).getPhase(), "stopping here would leave the player in the fight");
        fixture.plugin.setTickDelay(0);
        fixture.tick();
        assertEquals("Teleporting to Ferox Enclave: Missing item 4151", fixture.plugin.getStatus());
        fixture.world.instance = false;
        fixture.standAt(DreamData.FEROX_BANK);
        fixture.tick();
        fixture.tick();
        assertEquals("Missing item 4151: carry it or remove it from your equipment IDs, then restart the plugin",
            fixture.plugin.getStatus());
    }

    @Test
    public void herKillCountMessageCountsAKill() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.plugin.onChatMessage(message(DreamData.KILL_COUNT + " <col=ff0000>12</col>."));
        assertEquals(1, fixture.plugin.getKills());
    }

    @Test
    public void herColouredCurseSporesAndParasiteMessagesAreRead() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        fixture.plugin.onChatMessage(message("<col=a53fff>" + DreamData.CURSED + "</col>"));
        assertTrue(fight.isCursed());
        fixture.plugin.onChatMessage(message("<col=229628>" + DreamData.CURSE_ENDED + "</col>"));
        assertFalse(fight.isCursed());
        fixture.plugin.onChatMessage(message("<col=ff289d>" + DreamData.DROWSY + "</col>"));
        assertTrue(fight.isDrowsy());
        fixture.plugin.onChatMessage(message("<col=229628>" + DreamData.WEAKENED + "</col>"));
        assertTrue(fight.isWeakened());
    }

    @Test
    public void herAttackAnimationIsTimedFromTheNextTick() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.boss.animation = DreamData.MAGIC_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        assertEquals(AttackStyle.MAGIC, fight.getLastAttack());
        assertEquals(fixture.plugin.getTick() + 1, fight.getAttackTick());
    }

    @Test
    public void herProtectionGoesOnBeforeTheActionDelay() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        fixture.boss.animation = DreamData.RANGED_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.plugin.setTickDelay(5);
        fixture.tick();
        assertEquals("Praying Protect From Missiles", fixture.plugin.getStatus());
    }

    @Test
    public void runningIsSwitchedBackOnInTheFightUnlessHerSporesMadeThePlayerDrowsy() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.varps.put(VarPlayerID.OPTION_RUN, 0);
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        fight.setDrowsy(true);
        fixture.tick();
        assertEquals(Integer.MIN_VALUE / 2, fixture.read("runRequestedAt"), "drowsy: running stays off");
        fight.setDrowsy(false);
        fixture.tick();
        assertEquals(fixture.plugin.getTick(), fixture.read("runRequestedAt"), "steps off her hazards are timed for running");
    }

    @Test
    public void aRestartedPluginSwitchesRunningOnWithoutWaitingOutTheLastSession() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.varps.put(VarPlayerID.OPTION_RUN, 0);
        for (int i = 0; i < 30; i++)
        {
            fixture.tick();
        }
        int last = (Integer) fixture.read("runRequestedAt");
        assertTrue(last > 20, "the first session asked for running late in its count of ticks");
        fixture.disable();
        fixture.enableAndDrain();
        fixture.tick();
        assertEquals(fixture.plugin.getTick(), fixture.read("runRequestedAt"),
            "the new session counts its ticks from 0, so a request from the last one must not hold it back");
    }

    @Test
    public void whileHusksHoldThePlayerHerProtectionIsOnOnlyAsTheGameReadsIt() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.npc(1).id = DreamData.MAGIC_HUSK;
        fixture.tick();
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        assertEquals("Praying Protect From Magic", prayer.getStatus(), "the magic husk attacks first");
        fixture.world.activeVarbits.add(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit());
        fixture.boss.animation = DreamData.RANGED_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        int lands = fight.getAttackTick() + AttackStyle.RANGED.getHitTicks();
        List<Integer> hers = new ArrayList<>();
        for (int tick = 0; tick < 6; tick++)
        {
            fixture.tick();
            boolean missiles = prayer.getStatus().contains("Missiles");
            if (missiles)
            {
                hers.add(fixture.plugin.getTick());
            }
            fixture.world.activeVarbits.clear();
            fixture.world.activeVarbits.add((missiles ? Rs2PrayerEnum.PROTECT_RANGE : Rs2PrayerEnum.PROTECT_MAGIC).getVarbit());
        }
        assertEquals(List.of(lands - 3, lands - 1), hers,
            "hers is on as her missiles launch and as they land, and the husk's in between");
    }

    @Test
    public void inHerThirdPhaseHerProtectionIsOnOnlyAsHerAttackLaunches() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.npc(1).id = DreamData.MAGIC_HUSK;
        fixture.tick();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        for (Stage stage : new Stage[] {Stage.TOTEMS, Stage.BLAST, Stage.SHIELD, Stage.TOTEMS, Stage.BLAST, Stage.SHIELD})
        {
            fight.observeBoss(stage);
        }
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        fixture.world.activeVarbits.add(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit());
        fixture.boss.animation = DreamData.RANGED_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        int lands = fight.getAttackTick() + AttackStyle.RANGED.getHitTicks();
        List<Integer> hers = new ArrayList<>();
        for (int tick = 0; tick < 6; tick++)
        {
            fixture.tick();
            boolean missiles = prayer.getStatus().contains("Missiles");
            if (missiles)
            {
                hers.add(fixture.plugin.getTick());
            }
            fixture.world.activeVarbits.clear();
            fixture.world.activeVarbits.add((missiles ? Rs2PrayerEnum.PROTECT_RANGE : Rs2PrayerEnum.PROTECT_MAGIC).getVarbit());
        }
        assertEquals(List.of(lands - 3), hers, "the husk's protection stays on as her missiles land");
    }

    @Test
    public void herMeleeProtectionIsOnAsItLands() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.npc(1).id = DreamData.MAGIC_HUSK;
        fixture.tick();
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        fixture.world.activeVarbits.add(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit());
        fixture.boss.animation = DreamData.MELEE_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.tick();
        assertEquals("Praying Protect From Melee", prayer.getStatus(), "her melee is read as it lands, two ticks on");
    }

    @Test
    public void withBothHusksUpTheMissileHuskIsPrayedAgainstAsItAttacks() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.npc(1).id = DreamData.MAGIC_HUSK;
        TestWorld.NpcTarget missile = fixture.world.npcAt(fixture.world.position.dx(-1));
        missile.id = DreamData.RANGED_HUSK;
        missile.animation = DreamData.HUSK_RISE;
        AnimationChanged event = new AnimationChanged();
        event.setActor(missile.npc);
        fixture.plugin.onAnimationChanged(event);
        int rose = fixture.plugin.getTick() + 1;
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        List<Integer> missiles = new ArrayList<>();
        for (int tick = 0; tick < 10; tick++)
        {
            fixture.tick();
            boolean ranged = prayer.getStatus().contains("Missiles");
            if (ranged)
            {
                missiles.add(fixture.plugin.getTick());
            }
            fixture.world.activeVarbits.clear();
            fixture.world.activeVarbits.add((ranged ? Rs2PrayerEnum.PROTECT_RANGE : Rs2PrayerEnum.PROTECT_MAGIC).getVarbit());
        }
        assertEquals(List.of(rose + 3, rose + 7), missiles,
            "its protection ends the tick before each attack, five ticks after rising and every four after");
    }

    @Test
    public void aRisenParasiteIsPrayedAgainstBeforeEachAttack() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.boss.id = DreamData.BLAST;
        TestWorld.NpcTarget parasite = fixture.world.npc(2);
        parasite.id = DreamData.WEAK_PARASITE;
        fixture.tick();
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        assertEquals("Praying Protect From Missiles", prayer.getStatus(), "its first attack is with missiles");
        fixture.world.activeVarbits.add(Rs2PrayerEnum.PROTECT_RANGE.getVarbit());
        parasiteShoots(fixture, parasite, DreamData.PARASITE_RANGED, fixture.world.player);
        fixture.tick();
        fixture.tick();
        assertEquals("Praying Protect From Magic", prayer.getStatus(), "then the other style");
    }

    @Test
    public void aParasiteAttacksThePlayerEvenWhileItCouldHealHerShield() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        TestWorld.NpcTarget parasite = fixture.world.npc(2);
        parasite.id = DreamData.WEAK_PARASITE;
        fixture.tick();
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        parasiteShoots(fixture, parasite, DreamData.PARASITE_RANGED, fixture.world.player);
        fixture.tick();
        parasiteShoots(fixture, parasite, DreamData.PARASITE_MAGIC, fixture.boss.npc);
        fixture.tick();
        fixture.world.activeVarbits.add(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit());
        fixture.tick();
        assertEquals("Praying Protect From Magic", prayer.getStatus(), "healing her is not its attack");
        parasiteShoots(fixture, parasite, DreamData.PARASITE_MAGIC, fixture.world.player);
        fixture.tick();
        fixture.tick();
        assertEquals("Praying Protect From Missiles", prayer.getStatus(),
            "with her shield full its magic is aimed at the player, so missiles come next");
    }

    /** The parasite animates, and its projectile starts moving once the animation has ended. */
    private static void parasiteShoots(PluginTestFixture fixture, TestWorld.NpcTarget parasite, int animation, Actor target)
        throws Exception
    {
        AnimationChanged changed = new AnimationChanged();
        changed.setActor(parasite.npc);
        parasite.animation = animation;
        fixture.plugin.onAnimationChanged(changed);
        fixture.tick();
        parasite.animation = -1;
        fixture.plugin.onAnimationChanged(changed);
        Projectile projectile = TestWorld.proxy(Projectile.class, (method, args) ->
            method.equals("getSourceActor") ? parasite.npc : method.equals("getTargetActor") ? target : null);
        ProjectileMoved event = new ProjectileMoved();
        event.setProjectile(projectile);
        fixture.plugin.onProjectileMoved(event);
        fixture.plugin.onProjectileMoved(event);
    }

    @Test
    public void whileAParasiteAttacksHerProtectionIsOnOnlyAsTheGameReadsIt() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.world.npc(2).id = DreamData.WEAK_PARASITE;
        fixture.tick();
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        assertEquals("Praying Protect From Missiles", prayer.getStatus(), "the parasite attacks with missiles first");
        fixture.world.activeVarbits.add(Rs2PrayerEnum.PROTECT_RANGE.getVarbit());
        fixture.boss.animation = DreamData.MAGIC_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        int lands = fight.getAttackTick() + AttackStyle.MAGIC.getHitTicks();
        int hers = -1;
        for (int tick = 0; tick < 8 && hers < 0; tick++)
        {
            fixture.tick();
            hers = prayer.getStatus().contains("Magic") ? fixture.plugin.getTick() : -1;
        }
        assertEquals(lands - 3, hers, "the parasite's protection holds until the tick before her magic launches");
    }

    @Test
    public void inHerThirdPhaseTheProtectionGoesOnTheTickHerAttackIsSeen() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        for (Stage stage : new Stage[] {Stage.TOTEMS, Stage.BLAST, Stage.SHIELD, Stage.TOTEMS, Stage.BLAST, Stage.SHIELD})
        {
            fight.observeBoss(stage);
        }
        fixture.boss.animation = DreamData.MELEE_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.tick();
        assertEquals(3, fight.getPhase());
        assertEquals("Praying Protect From Melee", fixture.plugin.getStatus());
    }

    @Test
    public void inHerThirdPhaseTheProtectionGoesOffOnceHerAttackLands() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        for (Stage stage : new Stage[] {Stage.TOTEMS, Stage.BLAST, Stage.SHIELD, Stage.TOTEMS, Stage.BLAST, Stage.SHIELD})
        {
            fight.observeBoss(stage);
        }
        fixture.boss.animation = DreamData.MELEE_ATTACK;
        AnimationChanged event = new AnimationChanged();
        event.setActor(fixture.boss.npc);
        fixture.plugin.onAnimationChanged(event);
        fixture.world.activeVarbits.add(Rs2PrayerEnum.PROTECT_MELEE.getVarbit());
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        for (int tick = 0; tick <= AttackStyle.MELEE.getHitTicks(); tick++)
        {
            fixture.tick();
            assertFalse(prayer.getStatus().startsWith("Turning off"), "her attack has not landed");
        }
        fixture.tick();
        assertEquals("Turning off Protect From Melee", prayer.getStatus());
    }

    @Test
    public void inHerThirdPhaseTheNextAttackIsPrayedAgainstOnTheTickItIsSeen() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        fixture.tick();
        NightmareFight fight = (NightmareFight) fixture.read("fight");
        for (Stage stage : new Stage[] {Stage.TOTEMS, Stage.BLAST, Stage.SHIELD, Stage.TOTEMS, Stage.BLAST, Stage.SHIELD})
        {
            fight.observeBoss(stage);
        }
        FourtifysTask prayer = (FourtifysTask) fixture.read("prayer");
        int melee = Rs2PrayerEnum.PROTECT_MELEE.getVarbit();
        for (int attack = 0; attack < 2; attack++)
        {
            fixture.boss.animation = DreamData.MELEE_ATTACK;
            AnimationChanged event = new AnimationChanged();
            event.setActor(fixture.boss.npc);
            fixture.plugin.onAnimationChanged(event);
            fixture.tick();
            assertEquals("Praying Protect From Melee", prayer.getStatus(), "attack " + attack);
            fixture.world.activeVarbits.add(melee);
            fixture.boss.animation = -1;
            for (int tick = 0; tick < 3; tick++)
            {
                fixture.tick();
            }
            assertEquals("Turning off Protect From Melee", prayer.getStatus(), "attack " + attack);
            fixture.world.activeVarbits.remove(melee);
            fixture.tick();
            fixture.tick();
        }
    }

    @Test
    public void aSleepwalkerKilledAsSheAbsorbsItStillCountsForHerBlast() throws Exception
    {
        PluginTestFixture fixture = new PluginTestFixture();
        fixture.enableAndDrain();
        TestWorld.NpcTarget missed = fixture.world.npc(4);
        missed.id = DreamData.SLEEPWALKER;
        missed.index = 20;
        TestWorld.NpcTarget absorbed = fixture.world.npc(5);
        absorbed.id = DreamData.SLEEPWALKER;
        absorbed.index = 21;
        fixture.tick();
        absorbed.animation = DreamData.SLEEPWALKER_ABSORBED;
        absorbed.dead = true;
        fixture.tick();
        assertEquals(77, ((NightmareFight) fixture.read("fight")).blastDamage());
    }

    private static ChatMessage message(String text)
    {
        return new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", text, "", 0);
    }

    private static void stateChanged(PluginTestFixture fixture, GameState state)
    {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        fixture.plugin.onGameStateChanged(event);
    }

    private static void configChanged(PluginTestFixture fixture, String key)
    {
        ConfigChanged event = new ConfigChanged();
        event.setGroup(PhosanisNightmareConfig.GROUP);
        event.setKey(key);
        fixture.plugin.onConfigChanged(event);
    }
}
