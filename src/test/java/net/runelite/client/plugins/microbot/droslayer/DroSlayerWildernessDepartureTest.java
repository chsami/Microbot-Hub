package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerWildernessDepartureTest {
    @Test public void successfulTeleportReturnsToDeployedCannonWithoutRecoveryOrRegear() throws Exception {
        assertTeleportReturnsToTask(true, 1200, DroSlayerState.TRAVELING);
    }

    @Test public void successfulTeleportWithoutCannonReturnsWithoutForcedHop() throws Exception {
        assertTeleportReturnsToTask(false, 0, DroSlayerState.TRAVELING);
    }

    @Test public void successfulTeleportWithLowAmmoRestocksWithoutClearingDeployedCannonOrGear() throws Exception {
        assertTeleportReturnsToTask(true, 99, DroSlayerState.BANKING);
    }

    @Test public void successfulTeleportAtAmmoThresholdReturnsWithoutUnnecessaryBanking() throws Exception {
        assertTeleportReturnsToTask(true, 100, DroSlayerState.TRAVELING);
    }

    private void assertTeleportReturnsToTask(boolean cannonPlaced, int carriedAmmo,
                                            DroSlayerState expectedState) throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessPreferCannon()).thenReturn(true);
        when(config.cannonballThreshold()).thenReturn(100);
        WorldPoint task = new WorldPoint(3106, 3651, 0);
        field(script, "config", config);
        field(script, "taskDestination", task);
        field(script, "cannonPlaced", cannonPlaced);
        field(script, "isUsingCannon", cannonPlaced);
        field(script, "cannonDeploymentWorld", 663);
        field(script, "lastCannonPlacementTile", task);
        field(script, "initialSetupDone", true);
        field(script, "baseBankLoadComplete", true);
        DroSlayerState oldState = DroSlayerPlugin.getState();
        try (MockedStatic<DroSlayerRuntime> runtime = mockStatic(DroSlayerRuntime.class);
             MockedStatic<Rs2Pvp> wilderness = mockStatic(Rs2Pvp.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Microbot> dro = mockStatic(Microbot.class)) {
            dro.when(() -> Microbot.getVarbitPlayerValue(net.runelite.api.VarPlayer.CANNON_AMMO)).thenReturn(30);
            inventory.when(() -> Rs2Inventory.itemQuantity("Steel cannonball", true)).thenReturn(carriedAmmo);
            runtime.when(DroSlayerRuntime::wildernessEscapeState).thenReturn("ESCAPED");
            DroSlayerPlugin.setState(DroSlayerState.WILDERNESS_ESCAPE);
            assertEquals(true, call(script, "handleWildernessLifecycle"));
            assertEquals(expectedState, DroSlayerPlugin.getState());
            assertEquals(expectedState == DroSlayerState.BANKING, read(script, "escapeAmmoRestock"));
            assertEquals(cannonPlaced, read(script, "isUsingCannon"));
            assertEquals(task, read(script, "taskDestination"));
            assertEquals(cannonPlaced, read(script, "cannonPlaced"));
            assertEquals(663, read(script, "cannonDeploymentWorld"));
            assertEquals(true, read(script, "initialSetupDone"));
            assertEquals(true, read(script, "baseBankLoadComplete"));
            assertEquals(false, read(script, "wildernessHopRequired"));
            assertFalse(((DroSlayerWildernessRecovery) read(script, "wildernessRecovery")).isActive());
            runtime.verify(DroSlayerRuntime::acknowledgeWildernessEscape);
            runtime.verify(DroSlayerRuntime::cancelWalk);
            runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(any(), anyString()), never());
            inventory.verify(() -> Rs2Inventory.itemQuantity("Steel cannonball", true), atLeastOnce());
            inventory.verify(() -> Rs2Inventory.itemQuantity("Cannonball", true), atLeastOnce());
            inventory.verify(() -> Rs2Inventory.itemQuantity("Granite cannonball", true), atLeastOnce());
            inventory.verifyNoMoreInteractions();
        } finally {
            DroSlayerPlugin.setState(oldState);
        }
    }

    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test public void deathRecoveryClearsOldBankTargetAndRouteFailureBudget() throws Exception {
        Field walker = DroSlayerRuntime.class.getDeclaredField("walker");
        Field bank = DroSlayerRuntime.class.getDeclaredField("bankTarget");
        walker.setAccessible(true);
        bank.setAccessible(true);
        Object oldWalker = walker.get(null), oldBank = bank.get(null);
        DroSlayerWalkerBridge bridge = mock(DroSlayerWalkerBridge.class);
        try {
            walker.set(null, bridge);
            bank.set(null, net.runelite.client.plugins.microbot.util.bank.enums.BankLocation.values()[0]);
            DroSlayerRuntime.resetRecoveryRoute();
            assertNull(bank.get(null));
            verify(bridge).cancel();
            verify(bridge).clearFailureBudget();
        } finally {
            walker.set(null, oldWalker);
            bank.set(null, oldBank);
        }
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    @Test public void normalDepartureKeepsProtectionAfterTaskResetUntilFeroxArrival() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.selectedProtectionPrayer()).thenReturn(DroSlayerPrayer.PROTECT_MAGIC);
        when(config.prayerFlickStyle()).thenReturn(PrayerFlickStyle.ALWAYS_ON);
        field(script, "config", config);
        try (MockedStatic<DroSlayerRuntime> runtime = mockStatic(DroSlayerRuntime.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Pvp> wilderness = mockStatic(Rs2Pvp.class);
             MockedStatic<Rs2Prayer> prayers = mockStatic(Rs2Prayer.class)) {
            runtime.when(() -> DroSlayerRuntime.performAction(any(), anyBoolean(), any(BooleanSupplier.class)))
                    .thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(2)).getAsBoolean());
            wilderness.when(Rs2Pvp::isInWilderness).thenReturn(true);
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3237, 3622, 0));
            prayers.when(() -> Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)).thenReturn(true);
            call(script, "holdDepartureProtection");
            call(script, "deactivateProfilePrayer");
            call(script, "resetTravelData");
            call(script, "maintainDepartureProtection");
            prayers.verify(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false, true), never());
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3129, 3636, 0));
            call(script, "maintainDepartureProtection");
            prayers.verify(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false, true));
        } finally {
            DroSlayerPlugin.getFlickerScript().holdDeparturePrayer(null);
        }
    }

    @Test public void blockedPlayerEscapeRestoresProtectItemWithoutEatingOrCollectingCannon() throws Exception {
        DroSlayerScript script = new DroSlayerScript();
        DroSlayerConfig config = mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.drinkPrayerAt()).thenReturn(10);
        field(script, "config", config);
        field(script, "cannonPlaced", true);
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(client.getVarpValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF)).thenReturn(1);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
                Optional.ofNullable(((Callable<?>) invocation.getArgument(0)).call()));
        Rs2ItemModel restore = mock(Rs2ItemModel.class);
        try (MockedStatic<DroSlayerRuntime> runtime = mockStatic(DroSlayerRuntime.class);
             MockedStatic<Microbot> dro = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Pvp> wilderness = mockStatic(Rs2Pvp.class);
             MockedStatic<Rs2Prayer> prayers = mockStatic(Rs2Prayer.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            runtime.when(DroSlayerRuntime::wildernessEscapeState).thenReturn("BLOCKED");
            runtime.when(DroSlayerRuntime::isWildernessPlayerAttackActive).thenReturn(true);
            runtime.when(() -> DroSlayerRuntime.performAction(any(), anyBoolean(), any(BooleanSupplier.class)))
                    .thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(2)).getAsBoolean());
            wilderness.when(Rs2Pvp::isInWilderness).thenReturn(true);
            player.when(() -> Rs2Player.getRealSkillLevel(Skill.PRAYER)).thenReturn(50);
            player.when(() -> Rs2Player.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(5);
            prayers.when(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, true, true)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.get(any(java.util.function.Predicate.class))).thenReturn(restore);
            inventory.when(() -> Rs2Inventory.interact(restore, "Drink")).thenReturn(true);
            assertEquals(true, call(script, "handleWildernessLifecycle"));
            prayers.verify(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, true, true));
            prayers.verify(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, false, true), never());
            inventory.verify(() -> Rs2Inventory.interact(restore, "Drink"));
            inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), eq("Eat")), never());
            runtime.verify(() -> DroSlayerRuntime.walkFull(any(), any(), anyInt()), never());
            Field cannon = DroSlayerScript.class.getDeclaredField("cannonPlaced");
            cannon.setAccessible(true);
            assertTrue(cannon.getBoolean(script));
            runtime.when(DroSlayerRuntime::wildernessEscapeState).thenReturn("IDLE");
            runtime.when(DroSlayerRuntime::isWildernessPlayerAttackActive).thenReturn(false);
            assertEquals(false, call(script, "handleWildernessLifecycle"));
            assertEquals(DroSlayerState.AT_LOCATION, DroSlayerPlugin.getState());
        }
    }
}
