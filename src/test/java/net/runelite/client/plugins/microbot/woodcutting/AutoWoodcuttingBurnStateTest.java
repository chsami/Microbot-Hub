package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingPrimaryAction;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingScriptState;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static net.runelite.api.gameval.ItemID.TINDERBOX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoWoodcuttingBurnStateTest {

    private static final WoodcuttingTree TREE = WoodcuttingTree.OAK;

    private static AutoWoodcuttingScript script() {
        return mock(AutoWoodcuttingScript.class, CALLS_REAL_METHODS);
    }

    private static AutoWoodcuttingConfig firemakeOnlyConfig(boolean firemakeOnly) {
        AutoWoodcuttingConfig config = mock(AutoWoodcuttingConfig.class);
        when(config.firemakeOnly()).thenReturn(firemakeOnly);
        return config;
    }

    private static AutoWoodcuttingConfig burnConfig(WoodcuttingPrimaryAction action) {
        AutoWoodcuttingConfig config = firemakeOnlyConfig(false);
        when(config.primaryAction()).thenReturn(action);
        return config;
    }

    private static AutoWoodcuttingScript burningScript(AutoWoodcuttingConfig config, boolean usedCampfire) {
        AutoWoodcuttingScript script = script();
        doReturn(TREE).when(script).getActiveTree();
        doReturn(usedCampfire).when(script).burnLog(config);
        doNothing().when(script).walkBack(config);
        doNothing().when(script).shutdown();
        script.woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
        return script;
    }

    private static void resetWithInventory(AutoWoodcuttingScript script, AutoWoodcuttingConfig config, boolean logsLeft, boolean hasTinderbox) {
        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            inventory.when(() -> Rs2Inventory.contains(TREE.getLog())).thenReturn(logsLeft);
            inventory.when(() -> Rs2Inventory.hasItem(TINDERBOX)).thenReturn(hasTinderbox);
            script.resetInventory(config);
            microbot.verify(() -> Microbot.showMessage(anyString()), never());
        }
    }

    @Test
    void keepsBurningWhileLogsAreLeftDuringNormalCutting() {
        assertEquals(WoodcuttingScriptState.RESETTING, script().stateAfterBurning(firemakeOnlyConfig(false), true));
    }

    @Test
    void returnsToCuttingOnceLogsAreBurned() {
        assertEquals(WoodcuttingScriptState.WOODCUTTING, script().stateAfterBurning(firemakeOnlyConfig(false), false));
    }

    @Test
    void keepsBurningWhileLogsAreLeftInFiremakeOnlyMode() {
        assertEquals(WoodcuttingScriptState.RESETTING, script().stateAfterBurning(firemakeOnlyConfig(true), true));
    }

    @Test
    void fetchesMoreLogsOnceBurnedInFiremakeOnlyMode() {
        assertEquals(WoodcuttingScriptState.FIREMAKING, script().stateAfterBurning(firemakeOnlyConfig(true), false));
    }

    @Test
    void doesNotBankForLogsWhenFiremakeOnlyIsOff() {
        assertFalse(script().shouldBankForLogs(firemakeOnlyConfig(false), false));
    }

    @Test
    void banksForLogsInFiremakeOnlyModeWhenOutOfLogs() {
        assertTrue(script().shouldBankForLogs(firemakeOnlyConfig(true), false));
    }

    @Test
    void doesNotBankForLogsWhileLogsAreLeft() {
        assertFalse(script().shouldBankForLogs(firemakeOnlyConfig(true), true));
    }

    @Test
    void campfireBurnStoppedEarlyStaysInResettingInsteadOfFiremaking() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN_CAMPFIRE);
        AutoWoodcuttingScript script = burningScript(config, true);

        resetWithInventory(script, config, true, false);

        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
        verify(script, never()).withdrawTinderbox();
        verify(script, never()).walkBack(config);
    }

    @Test
    void tinderboxBurnWithLogsLeftStaysInResettingInsteadOfFiremaking() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        AutoWoodcuttingScript script = burningScript(config, false);

        resetWithInventory(script, config, true, true);

        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
        verify(script, never()).withdrawTinderbox();
    }

    @Test
    void burnWithNoLogsLeftWalksBackAndReturnsToCutting() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        AutoWoodcuttingScript script = burningScript(config, false);

        resetWithInventory(script, config, false, true);

        assertEquals(WoodcuttingScriptState.WOODCUTTING, script.woodcuttingScriptState);
        verify(script).walkBack(config);
    }

    @Test
    void missingTinderboxIsWithdrawnAndBurningContinues() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        AutoWoodcuttingScript script = burningScript(config, false);
        doReturn(true).when(script).withdrawTinderbox();

        resetWithInventory(script, config, true, false);

        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
        verify(script, never()).shutdown();
    }

    private static void assertStopsWithMessage(WoodcuttingPrimaryAction action, String expectedMessage) {
        AutoWoodcuttingConfig config = burnConfig(action);
        AutoWoodcuttingScript script = burningScript(config, false);
        doReturn(false).when(script).withdrawTinderbox();

        try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            inventory.when(() -> Rs2Inventory.contains(TREE.getLog())).thenReturn(true);
            script.resetInventory(config);
            microbot.verify(() -> Microbot.showMessage(expectedMessage));
        }

        verify(script).shutdown();
        assertEquals(WoodcuttingScriptState.RESETTING, script.woodcuttingScriptState);
    }

    @Test
    void missingTinderboxThatCannotBeWithdrawnStopsWithMessage() {
        assertStopsWithMessage(WoodcuttingPrimaryAction.BURN,
                "Auto Woodcutting stopped: no Tinderbox in your inventory or in a nearby bank. Put a Tinderbox in your inventory and start again, or set Primary action to 'Burn logs at campfire' and cut next to a lit fire.");
    }

    @Test
    void campfireModeWithNoFireNearbyStopsWithFireMessage() {
        assertStopsWithMessage(WoodcuttingPrimaryAction.BURN_CAMPFIRE,
                "Auto Woodcutting stopped: no lit fire or Forester's Campfire within 6 tiles, and no Tinderbox to light one. Cut closer to a fire, or put a Tinderbox in your inventory and start again.");
    }

    @Test
    void fireSpotSkipsPlayerTileOccupiedTilesAndTilesThatRefusedAFire() {
        AutoWoodcuttingScript script = new AutoWoodcuttingScript(null);
        WorldPoint player = new WorldPoint(3084, 3234, 0);
        WorldPoint refused = new WorldPoint(3083, 3234, 0);
        WorldPoint occupied = new WorldPoint(3085, 3234, 0);
        WorldPoint free = new WorldPoint(3084, 3233, 0);
        script.noFireTiles.add(refused);

        WorldPoint spot = script.nearestFireSpot(List.of(player, refused, occupied, free), player, occupied::equals);

        assertEquals(free, spot);
    }

    @Test
    void fireSpotIsNullWhenEveryCandidateIsExcluded() {
        AutoWoodcuttingScript script = new AutoWoodcuttingScript(null);
        WorldPoint player = new WorldPoint(3084, 3234, 0);
        WorldPoint refused = new WorldPoint(3083, 3234, 0);
        script.noFireTiles.add(refused);

        assertNull(script.nearestFireSpot(List.of(refused), player, point -> false));
    }

    @Test
    void unreachedFireSpotIsRememberedAndNextAttemptWalksToAnotherTile() {
        WorldPoint player = new WorldPoint(3084, 3234, 0);
        WorldPoint first = new WorldPoint(3083, 3234, 0);
        WorldPoint second = new WorldPoint(3085, 3234, 0);

        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(query.nearest(anyInt())).thenReturn(null);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);

        AutoWoodcuttingScript script = new AutoWoodcuttingScript(null);
        script.rs2TileObjectCache = cache;
        script.cannotLightFire = true;
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);

        try (MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Tile> tile = mockStatic(Rs2Tile.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(player);
            tile.when(() -> Rs2Tile.getWalkableTilesAroundPlayer(1)).thenReturn(List.of(first, second));
            walker.when(() -> Rs2Walker.walkFastCanvas(any(WorldPoint.class))).thenReturn(true);

            assertFalse(script.burnLog(config));
            assertTrue(script.noFireTiles.contains(first));
            assertTrue(script.cannotLightFire);

            script.burnLog(config);

            walker.verify(() -> Rs2Walker.walkFastCanvas(first));
            walker.verify(() -> Rs2Walker.walkFastCanvas(second));
            inventory.verify(() -> Rs2Inventory.use(anyString()), never());
        }
    }

    @Test
    void stopMessageNamesTheCampfireOptionAsShownInTheDropdown() {
        AutoWoodcuttingConfig config = burnConfig(WoodcuttingPrimaryAction.BURN);
        assertTrue(script().noTinderboxMessage(config).contains("'" + WoodcuttingPrimaryAction.BURN_CAMPFIRE + "'"));
    }
}
