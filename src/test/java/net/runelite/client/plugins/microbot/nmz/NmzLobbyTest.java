package net.runelite.client.plugins.microbot.nmz;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual lobby orchestration and transaction methods with simulated game/UI IO.
 * No client launch, points spent or account input. */
class NmzLobbyTest {
    @Test
    void initialRepeatSettingRetainsTheExistingStopPreference() {
        NmzConfig config = mock(NmzConfig.class, CALLS_REAL_METHODS);
        assertFalse(config.repeatRounds());

        when(config.stopAfterDeath()).thenReturn(false);
        assertTrue(config.repeatRounds());
    }

    static final int OVERLOAD = VarbitID.NZONE_POTION_3, ABSORPTION = VarbitID.NZONE_POTION_4;
    final class Fixture implements AutoCloseable {
        final NmzConfig config = mock(NmzConfig.class);
        final NmzPlugin plugin = mock(NmzPlugin.class);
        final ClientThread thread = mock(ClientThread.class);
        final Map<String,Integer> inventory = new HashMap<>();
        final Map<Integer,Integer> stock = new HashMap<>();
        int dream = 1, points = 1_000_000;
        String promptText = "";
        boolean promptVisible;
        final Widget quantityPrompt = mock(Widget.class);
        final MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
        final MockedStatic<Rs2Inventory> inv = mockStatic(Rs2Inventory.class);
        final MockedStatic<Global> waits = mockStatic(Global.class);
        final MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class);
        final MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class);
        final NmzScript script;
        Fixture() {
            when(config.overloadPotionAmount()).thenReturn(8);
            when(config.absorptionPotionAmount()).thenReturn(19);
            when(config.repeatRounds()).thenReturn(true);
            microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenAnswer(i -> (int)i.getArgument(0) == VarbitID.NZONE_PURCHASEDDREAM ? dream : stock.getOrDefault(i.getArgument(0), 0));
            microbot.when(() -> Microbot.getVarbitPlayerValue(VarPlayerID.NZONE_REWARDPOINTS)).thenAnswer(i -> points);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            when(thread.invoke(any(Supplier.class))).thenAnswer(i -> ((Supplier<?>)i.getArgument(0)).get());
            inv.when(() -> Rs2Inventory.count(anyString())).thenAnswer(i -> inventory.entrySet().stream().filter(e -> e.getKey().contains(((String)i.getArgument(0)).toLowerCase(Locale.ROOT))).mapToInt(Map.Entry::getValue).sum());
            inv.when(Rs2Inventory::emptySlotCount).thenAnswer(i -> 28 - inventory.values().stream().mapToInt(Integer::intValue).sum());
            inv.when(() -> Rs2Inventory.hasItem("prayer potion")).thenAnswer(i -> inventory.getOrDefault("prayer potion(4)", 0) > 0);
            waits.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenAnswer(i -> ((BooleanSupplier)i.getArgument(0)).getAsBoolean());
            waits.when(() -> Global.sleepUntil(any(BooleanSupplier.class))).thenAnswer(i -> ((BooleanSupplier)i.getArgument(0)).getAsBoolean());
            // Observed game layout, not the old implementation's incorrect child 42.
            widgets.when(() -> Rs2Widget.getWidget((162 << 16) | 43)).thenReturn(quantityPrompt);
            when(quantityPrompt.getText()).thenAnswer(i -> promptText);
            when(quantityPrompt.isHidden()).thenAnswer(i -> !promptVisible);
            script = new NmzScript(plugin, config);
        }
        void objects(Rs2TileObjectModel object) throws Exception {
            Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class, RETURNS_DEEP_STUBS);
            when(cache.query().withId(anyInt()).nearest()).thenReturn(object);
            Field field = NmzScript.class.getDeclaredField("tileObjectCache");field.setAccessible(true);field.set(script, cache);
        }
        @Override public void close() { keyboard.close();widgets.close();waits.close();inv.close();microbot.close(); }
    }

    @Test void requiresBothPotionTargetsEvenWhenOverloadsOrPrayerAreReady() { try (Fixture f = new Fixture()) {
        f.inventory.put("overload (4)", 8);
        assertFalse(f.script.canStartNmz());
        when(f.config.togglePrayerPotions()).thenReturn(true);f.inventory.put("prayer potion(4)", 1);
        assertFalse(f.script.canStartNmz());
        f.inventory.put("absorption (4)", 19);assertTrue(f.script.canStartNmz());
    }}

    @Test void zeroOverloadsDoesNotBypassAbsorptionRequirement() { try (Fixture f = new Fixture()) {
        when(f.config.overloadPotionAmount()).thenReturn(0);
        assertFalse(f.script.canStartNmz());f.inventory.put("absorption (4)", 19);assertTrue(f.script.canStartNmz());
    }}

    @Test void doseShortfallIncludesCarriedFullBottlesAndStoredDoses() { try (Fixture f = new Fixture()) {
        f.inventory.put("overload (4)", 3);f.stock.put(OVERLOAD, 17);
        assertEquals(3, f.script.dosesToBuy(OVERLOAD, "overload", 8));
        f.stock.put(OVERLOAD, 100);assertEquals(0, f.script.dosesToBuy(OVERLOAD, "overload", 8));
    }}

    @Test void continuousModeAllowsRepeatedInstanceToLobbyTransitions() { try (Fixture f = new Fixture()) {
        for (int round = 0; round < 3; round++) {
            assertTrue(f.script.handleRoundTransition(false));
            assertTrue(f.script.handleRoundTransition(true));
        }
        f.microbot.verify(() -> Microbot.stopPlugin(f.plugin), never());
    }}

    @Test void singleRoundModeStopsOnExitButStillAllowsFirstEntry() { try (Fixture f = new Fixture()) {
        when(f.config.repeatRounds()).thenReturn(false);
        assertTrue(f.script.handleRoundTransition(true));assertTrue(f.script.handleRoundTransition(false));
        assertFalse(f.script.handleRoundTransition(true));assertFalse(f.script.handleRoundTransition(true));
        f.microbot.verify(() -> Microbot.stopPlugin(f.plugin), times(1));
    }}

    @Test void repeatedFailedDreamPurchaseStopsInsteadOfIdlingForever() { try (Fixture f = new Fixture()) {
        f.dream = 0;
        NmzScript script = new NmzScript(f.plugin, f.config) { @Override public void startNmzDream() {} };
        for (int i = 0; i < 5; i++) script.handleOutsideNmz();
        f.microbot.verify(() -> Microbot.stopPlugin(f.plugin), times(1));
    }}

    @Test void repeatedUnacknowledgedShopTransactionsStopInsteadOfLooping() { try (Fixture f = new Fixture()) {
        NmzScript script = new NmzScript(f.plugin, f.config) { @Override public boolean handleStore() { return false; } };
        for (int i = 0; i < 5; i++) script.handleOutsideNmz();
        f.microbot.verify(() -> Microbot.stopPlugin(f.plugin), times(1));
        assertTrue(Microbot.status.contains("buying potion doses"));
    }}

    @Test void repeatModeOverridesExistingStopAfterDeathPreference() throws Exception { try (Fixture f = new Fixture()) {
        NmzPlugin plugin = new NmzPlugin();
        Field field = NmzPlugin.class.getDeclaredField("config");field.setAccessible(true);field.set(plugin, f.config);
        when(f.config.stopAfterDeath()).thenReturn(true);
        plugin.onActorDeath(mock(net.runelite.api.events.ActorDeath.class));
        // In repeat mode the death subscriber must not even create its delayed stop task.
        f.microbot.verify(Microbot::getClientThread, never());
    }}

    @Test void twoRoundLobbySimulationStoresLeftoversRestocksAndReenters() { try (Fixture f = new Fixture()) {
        int[] entries = {0}, purchases = {0};
        NmzScript script = new NmzScript(f.plugin, f.config) {
            @Override public void startNmzDream() { f.dream = 1; }
            @Override boolean storePotions(int object, String name) {
                int key = name.equals("overload") ? OVERLOAD : ABSORPTION;
                int doses = 4 * f.inventory.getOrDefault(name + " (4)", 0) + 2 * f.inventory.getOrDefault(name + " (2)", 0);
                f.stock.merge(key, doses, Integer::sum);f.inventory.keySet().removeIf(n -> n.contains(name));return true;
            }
            @Override public boolean handleStore() {
                purchases[0]++;
                f.stock.merge(OVERLOAD, dosesToBuy(OVERLOAD, "overload", 8), Integer::sum);
                f.stock.merge(ABSORPTION, dosesToBuy(ABSORPTION, "absorption", 19), Integer::sum);return true;
            }
            @Override boolean fetchPotions(int object, String name, int required) {
                int key = object == ObjectID.NZONE_BARREL_3 ? OVERLOAD : ABSORPTION;
                String lower = name.toLowerCase(Locale.ROOT);int missing = required - f.inventory.getOrDefault(lower, 0);
                assertTrue(f.stock.getOrDefault(key, 0) >= missing * 4);
                f.stock.merge(key, -missing * 4, Integer::sum);f.inventory.put(lower, required);return true;
            }
            @Override public boolean consumeEmptyVial() {
                assertTrue(canStartNmz());assertEquals(1, f.dream);entries[0]++;f.dream = 0;return true;
            }
        };
        for (int round = 0; round < 2; round++) {
            script.handleRoundTransition(false);script.handleRoundTransition(true);
            f.inventory.clear();f.inventory.put("overload (2)", 1);f.inventory.put("absorption (4)", 2);
            f.inventory.put("absorption (2)", 1);f.dream = 0;
            for (int step = 0; step < 8 && entries[0] <= round; step++) script.handleOutsideNmz();
            assertEquals(round + 1, entries[0]);
        }
        assertEquals(2, purchases[0]);f.microbot.verify(() -> Microbot.stopPlugin(f.plugin), never());
    }}

    @Test void withdrawalRequestsOnlyMissingBottlesAsDoses() throws Exception { try (Fixture f = new Fixture()) {
        f.inventory.put("absorption (4)", 5);
        Rs2TileObjectModel barrel = mock(Rs2TileObjectModel.class);when(barrel.click("Take")).thenReturn(true);f.objects(barrel);
        f.widgets.when(() -> Rs2Widget.hasWidget("How many doses of ")).thenReturn(true);
        f.keyboard.when(Rs2Keyboard::enter).thenAnswer(i -> { f.inventory.put("absorption (4)", 19);return null; });
        assertTrue(f.script.fetchPotions(ObjectID.NZONE_BARREL_4, "Absorption (4)", 19));
        f.keyboard.verify(() -> Rs2Keyboard.typeString("56"));
    }}

    @Test void failedDepositDoesNotDropPotionsOrReportSuccess() throws Exception { try (Fixture f = new Fixture()) {
        f.inventory.put("overload (2)", 1);
        Rs2TileObjectModel barrel = mock(Rs2TileObjectModel.class);when(barrel.click("Store")).thenReturn(true);f.objects(barrel);
        f.widgets.when(() -> Rs2Widget.hasWidget("Store all your ")).thenReturn(true);
        assertFalse(f.script.storePotions(ObjectID.NZONE_BARREL_3, "overload"));
        f.inv.verify(() -> Rs2Inventory.dropAll("overload"), never());
    }}

    @Test void insufficientPointsUsesPerDoseCostBeforeOpeningShop() { try (Fixture f = new Fixture()) {
        f.points = 50_000; // Enough under the old incorrect bottle pricing, not 32*1500 + 76*1000.
        assertFalse(f.script.handleStore());
        assertTrue(Microbot.status.contains("124000"));f.microbot.verify(() -> Microbot.stopPlugin(f.plugin));
    }}

    @Test void buyXEntersExactDoseQuantityAndChecksBarrelAcknowledgement() { try (Fixture f = new Fixture()) {
        Widget parent = mock(Widget.class), item = mock(Widget.class);
        f.widgets.when(() -> Rs2Widget.getWidget(206, 6)).thenReturn(parent);when(parent.getChild(6)).thenReturn(item);
        when(item.getActions()).thenReturn(new String[]{"Buy-1", "Buy-5", "Buy-10", "Buy-50", "Buy-X"});
        f.promptVisible = true;f.promptText = "Enter amount:";
        f.keyboard.when(Rs2Keyboard::enter).thenAnswer(i -> { f.stock.put(OVERLOAD, 32);return null; });
        assertTrue(f.script.buyDoses(6, OVERLOAD, 32));
        f.widgets.verify(() -> Rs2Widget.clickWidgetFast(item, 6, 5), times(1));
        f.keyboard.verify(() -> Rs2Keyboard.typeString("32"));
    }}

    @Test void rewardShopStillBuysAbsorptionsWhenOverloadsAreAlreadyCarried() throws Exception { try (Fixture f = new Fixture()) {
        f.inventory.put("overload (4)", 8);f.points = 76_000;
        Rs2TileObjectModel chest = mock(Rs2TileObjectModel.class);when(chest.click()).thenReturn(true);f.objects(chest);
        f.widgets.when(() -> Rs2Widget.isWidgetVisible(13500418)).thenReturn(true);
        boolean[] benefits = {false};
        f.widgets.when(() -> Rs2Widget.isWidgetVisible(206, 6)).thenAnswer(i -> benefits[0]);
        f.widgets.when(() -> Rs2Widget.clickWidget("Benefits", Optional.of(206), 2, false)).thenAnswer(i -> { benefits[0] = true;return true; });
        Widget parent = mock(Widget.class), item = mock(Widget.class);
        f.widgets.when(() -> Rs2Widget.getWidget(206, 6)).thenReturn(parent);when(parent.getChild(9)).thenReturn(item);
        when(item.getActions()).thenReturn(new String[]{"Buy-1", "Buy-5", "Buy-10", "Buy-50", "Buy-X"});
        f.promptVisible = true;f.promptText = "Enter amount:";
        f.keyboard.when(Rs2Keyboard::enter).thenAnswer(i -> { f.stock.put(ABSORPTION, 76);return null; });
        assertTrue(f.script.handleStore());assertEquals(76, f.stock.get(ABSORPTION));
        f.keyboard.verify(() -> Rs2Keyboard.typeString("76"));
        f.widgets.verify(() -> Rs2Widget.clickWidgetFast(item, 9, 5), times(1));
        f.widgets.verify(() -> Rs2Widget.clickWidget("Benefits", Optional.of(206), 2, false), times(1));
    }}

    @Test void unacknowledgedPurchaseStopsWithoutSendingAnotherBuy() { try (Fixture f = new Fixture()) {
        Widget parent = mock(Widget.class), item = mock(Widget.class);
        f.widgets.when(() -> Rs2Widget.getWidget(206, 6)).thenReturn(parent);when(parent.getChild(6)).thenReturn(item);
        when(item.getActions()).thenReturn(new String[]{"Buy-1"});
        assertFalse(f.script.buyDoses(6, OVERLOAD, 32));
        f.widgets.verify(() -> Rs2Widget.clickWidgetFast(item, 6, 1), times(1));
    }}

    @Test void buyOneFallbackWaitsForEachDoseAndDoesNotOverspend() { try (Fixture f = new Fixture()) {
        Widget parent = mock(Widget.class), item = mock(Widget.class);
        f.widgets.when(() -> Rs2Widget.getWidget(206, 6)).thenReturn(parent);when(parent.getChild(6)).thenReturn(item);
        when(item.getActions()).thenReturn(new String[]{"Buy-1"});
        f.widgets.when(() -> Rs2Widget.clickWidgetFast(item, 6, 1)).thenAnswer(i -> { f.stock.merge(OVERLOAD, 1, Integer::sum);return null; });
        assertTrue(f.script.buyDoses(6, OVERLOAD, 3));assertEquals(3, f.stock.get(OVERLOAD));
        f.widgets.verify(() -> Rs2Widget.clickWidgetFast(item, 6, 1), times(3));
    }}

    @Test void missingEntryWidgetIsRetriedWithoutNullDereference() throws Exception { try (Fixture f = new Fixture()) {
        Rs2TileObjectModel vial = mock(Rs2TileObjectModel.class);when(vial.click("Drink")).thenReturn(true);f.objects(vial);
        NmzScript script = f.script;
        try (MockedStatic<net.runelite.client.plugins.microbot.util.player.Rs2Player> player = mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Player.class)) {
            player.when(net.runelite.client.plugins.microbot.util.player.Rs2Player::getWorldLocation).thenReturn(new net.runelite.api.coords.WorldPoint(2602, 3116, 0));
            assertFalse(script.consumeEmptyVial());
        }
    }}

    @Test void pluginOwnsOnePrayerWorkerAndStopsItOnShutdown() throws Exception {
        NmzPlugin plugin = new NmzPlugin();NmzConfig config = mock(NmzConfig.class);
        NmzScript main = mock(NmzScript.class);PrayerPotionScript prayer = mock(PrayerPotionScript.class);
        Object[][] fields = {{"config", config}, {"nmzScript", main}, {"prayerPotionScript", prayer},
                {"overlayManager", mock(net.runelite.client.ui.overlay.OverlayManager.class)}, {"nmzOverlay", mock(NmzOverlay.class)}};
        for (Object[] value : fields) { Field field = NmzPlugin.class.getDeclaredField((String)value[0]);field.setAccessible(true);field.set(plugin, value[1]); }
        plugin.startUp();
        org.mockito.ArgumentCaptor<BooleanSupplier> guard = org.mockito.ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(prayer, times(1)).run(eq(config), guard.capture());
        when(main.isRunning()).thenReturn(true);when(main.isOutside()).thenReturn(true);assertFalse(guard.getValue().getAsBoolean());
        when(main.isOutside()).thenReturn(false);assertTrue(guard.getValue().getAsBoolean());
        plugin.shutDown();verify(main).shutdown();verify(prayer).shutdown();
    }

    @Test void buyPromptUsesLiveTitleAndRejectsWithdrawalText() { try (Fixture f = new Fixture()) {
        f.promptVisible = true;f.promptText = "Enter amount:";
        assertTrue(f.script.purchasePromptOpen());
        f.promptText = "How many doses of absorption potion will you withdraw? (255)";
        assertFalse(f.script.purchasePromptOpen());
        f.promptText = "Enter amount:";f.promptVisible = false;
        assertFalse(f.script.purchasePromptOpen());
        f.widgets.verify(() -> Rs2Widget.getWidget((162 << 16) | 42), never());
    }}

    @Test void barrelRefillTypesOnlyTheDifferenceTo255() throws Exception { try (Fixture f = new Fixture()) {
        when(f.config.fillPotionBarrels()).thenReturn(true);
        f.stock.put(OVERLOAD, 132);f.stock.put(ABSORPTION, 35);
        Rs2TileObjectModel chest = mock(Rs2TileObjectModel.class);when(chest.click()).thenReturn(true);f.objects(chest);
        f.widgets.when(() -> Rs2Widget.isWidgetVisible(13500418)).thenReturn(true);
        f.widgets.when(() -> Rs2Widget.isWidgetVisible(206, 6)).thenReturn(true);
        Widget parent = mock(Widget.class), item = mock(Widget.class);
        f.widgets.when(() -> Rs2Widget.getWidget(206, 6)).thenReturn(parent);when(parent.getChild(9)).thenReturn(item);
        when(item.getActions()).thenReturn(new String[]{"Buy-1", "Buy-5", "Buy-10", "Buy-50", "Buy-X"});
        f.promptVisible = true;f.promptText = "Enter amount:";
        f.keyboard.when(Rs2Keyboard::enter).thenAnswer(i -> { f.stock.put(ABSORPTION, 255);return null; });
        assertTrue(f.script.handleStore());
        f.keyboard.verify(() -> Rs2Keyboard.typeString("220"));
        assertEquals(132, f.stock.get(OVERLOAD));assertEquals(255, f.stock.get(ABSORPTION));
        assertEquals(0, f.script.dosesToBuy(ABSORPTION, "absorption", 19));
    }}

    @Test void buy50FallbackStopsExactlyAtTheBarrelCap() { try (Fixture f = new Fixture()) {
        f.stock.put(ABSORPTION, 35);
        Widget parent = mock(Widget.class), item = mock(Widget.class);
        f.widgets.when(() -> Rs2Widget.getWidget(206, 6)).thenReturn(parent);when(parent.getChild(9)).thenReturn(item);
        when(item.getActions()).thenReturn(new String[]{"Buy-1", "Buy-5", "Buy-10", "Buy-50"});
        f.widgets.when(() -> Rs2Widget.clickWidgetFast(item, 9, 4)).thenAnswer(i -> { f.stock.merge(ABSORPTION, 50, Integer::sum);return null; });
        f.widgets.when(() -> Rs2Widget.clickWidgetFast(item, 9, 3)).thenAnswer(i -> { f.stock.merge(ABSORPTION, 10, Integer::sum);return null; });
        assertTrue(f.script.buyDoses(9, ABSORPTION, 220));assertEquals(255, f.stock.get(ABSORPTION));
        f.widgets.verify(() -> Rs2Widget.clickWidgetFast(item, 9, 4), times(4));
        f.widgets.verify(() -> Rs2Widget.clickWidgetFast(item, 9, 3), times(2));
        f.keyboard.verify(Rs2Keyboard::enter, never());
    }}

    @Test void withdrawalPromptCannotReceiveTheBuyQuantity() { try (Fixture f = new Fixture()) {
        Widget parent = mock(Widget.class), item = mock(Widget.class);
        f.widgets.when(() -> Rs2Widget.getWidget(206, 6)).thenReturn(parent);when(parent.getChild(9)).thenReturn(item);
        when(item.getActions()).thenReturn(new String[]{"Buy-X"});
        f.promptVisible = true;f.promptText = "How many doses of absorption potion will you withdraw? (255)";
        assertFalse(f.script.buyDoses(9, ABSORPTION, 220));
        f.keyboard.verify(() -> Rs2Keyboard.typeString(anyString()), never());
    }}
}
