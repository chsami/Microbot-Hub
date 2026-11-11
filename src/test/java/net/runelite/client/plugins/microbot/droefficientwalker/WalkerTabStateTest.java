package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.MouseMotionScope;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalkerTabStateTest {
    @Test void staleEventCacheCannotHideAnActuallySelectedEquipmentTab() throws Exception {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any(Callable.class)))
                .thenAnswer(call -> Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        try (var dro = mockStatic(Microbot.class); var tabs = mockStatic(Rs2Tab.class)) {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.EQUIPMENT.getVarcIntIndex());
            assertFalse(Rs2Tab.isCurrentTab(InterfaceTab.EQUIPMENT));
            assertTrue(WalkerTabState.isCurrentTab(InterfaceTab.EQUIPMENT));
            when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.INVENTORY.getVarcIntIndex());
            tabs.when(() -> Rs2Tab.isCurrentTab(InterfaceTab.EQUIPMENT)).thenReturn(true);
            assertFalse(WalkerTabState.isCurrentTab(InterfaceTab.EQUIPMENT), "An old Equipment event cannot authorize the wrong live panel");
            assertTrue(WalkerTabState.isCurrentTab(InterfaceTab.INVENTORY));
            verify(client, never()).runScript(anyInt(), any());
        }
    }

    @Test void unavailableLiveTabReceiptCannotAuthorizeActivation() {
        try (var dro = mockStatic(Microbot.class); var tabs = mockStatic(Rs2Tab.class)) {
            tabs.when(() -> Rs2Tab.isCurrentTab(InterfaceTab.EQUIPMENT)).thenReturn(true);
            assertFalse(WalkerTabState.isCurrentTab(InterfaceTab.EQUIPMENT));
            dro.when(Microbot::getClient).thenReturn(mock(Client.class));
            dro.when(Microbot::getClientThread).thenReturn(mock(ClientThread.class));
            assertFalse(WalkerTabState.isCurrentTab(InterfaceTab.EQUIPMENT));
        }
    }

    @Disabled("Relies on the droplugins client's Rs2Equipment tab helper, which the Hub client does not have")
    @Test void livePanelReceiptAlsoUnblocksTheExistingEquipmentHelperWithoutAnotherTabEvent() throws Exception {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any(Callable.class)))
                .thenAnswer(call -> Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.EQUIPMENT.getVarcIntIndex());
        Field cached = WalkerTestReflection.declaredField(Rs2Tab.class, "cachedTab"); cached.setAccessible(true);
        Object previous = cached.get(null);
        try (var dro = mockStatic(Microbot.class); var profile = MouseMotionScope.open(null)) {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            cached.set(null, InterfaceTab.INVENTORY);
            assertFalse(Rs2Tab.isCurrentTab(InterfaceTab.EQUIPMENT));
            assertTrue(WalkerTabState.isCurrentTab(InterfaceTab.EQUIPMENT));
            assertEquals(InterfaceTab.EQUIPMENT, Rs2Tab.getCurrentTab());
            assertTrue(Rs2Tab.switchTo(InterfaceTab.EQUIPMENT), "The native item helper must reuse the observed panel");
            verify(client, never()).runScript(anyInt(), any());
        } finally {
            cached.set(null, previous);
        }
    }

    @Test void realWornTeleportUsesTheLivePanelDespiteAStaleEventCache() throws Exception {
        Client client = mock(Client.class);
        ClientThread thread = mock(ClientThread.class);
        Player player = mock(Player.class);
        WorldPoint start = new WorldPoint(1625, 3938, 0), landing = new WorldPoint(1248, 3727, 0);
        when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTickCount()).thenReturn(100);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.getItemDefinition(ItemID.JEWL_NECKLACE_OF_SKILLS_4)).thenReturn(mock(ItemComposition.class));
        when(player.getWorldLocation()).thenReturn(start);
        when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.EQUIPMENT.getVarcIntIndex());
        when(thread.runOnClientThreadOptional(any(Callable.class)))
                .thenAnswer(call -> Optional.ofNullable(((Callable<?>) call.getArgument(0)).call()));
        try (var dro = mockStatic(Microbot.class); var tabs = mockStatic(Rs2Tab.class);
             var inventory = mockStatic(Rs2Inventory.class); var worn = mockStatic(TravelEquipment.class);
             var equipment = mockStatic(Rs2Equipment.class)) {
            dro.when(Microbot::getClient).thenReturn(client);
            dro.when(Microbot::getClientThread).thenReturn(thread);
            dro.when(Microbot::isLoggedIn).thenReturn(true);
            worn.when(() -> TravelEquipment.isWearing(any(int[].class))).thenReturn(true);
            equipment.when(() -> Rs2Equipment.interact(ItemID.JEWL_NECKLACE_OF_SKILLS_4, "Farming Guild")).thenReturn(true);

            Class<?> loaderType = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader");
            Constructor<?> loaderConstructor = loaderType.getDeclaredConstructor(ClassLoader.class);
            loaderConstructor.setAccessible(true);
            ClassLoader loader = (ClassLoader) loaderConstructor.newInstance(getClass().getClassLoader());
            String shadow = "net.runelite.client.plugins.microbot.droefficientwalker.engine";
            Class<?> engineType = Class.forName(shadow + ".WalkerEngine", true, loader);
            Class<?> configType = Class.forName(shadow + ".WalkerEngineConfig", true, loader);
            EfficientWalkerConfig defaults = new EfficientWalkerConfig() {};
            Object config = Proxy.newProxyInstance(loader, new Class<?>[] {configType}, (proxy, method, args) ->
                    EfficientWalkerConfig.class.getMethod(method.getName(), method.getParameterTypes()).invoke(defaults, args));
            Constructor<?> engineConstructor = engineType.getDeclaredConstructor(Client.class, configType, List.class,
                    Class.forName(shadow + ".CollisionRoutePlanner", true, loader), Executor.class, Executor.class);
            engineConstructor.setAccessible(true);
            Executor direct = Runnable::run;
            Object engine = engineConstructor.newInstance(client, config, List.of(), null, direct, direct);
            Class<?> providerType = Class.forName(shadow + ".TeleportProviders$TeleportProvider", true, loader);
            Constructor<?> providerConstructor = providerType.getDeclaredConstructor(String.class, Supplier.class, Spell.class,
                    int.class, String.class, IntSupplier.class, int.class, int[].class, Supplier.class, BooleanSupplier.class, boolean.class);
            providerConstructor.setAccessible(true);
            Object provider = providerConstructor.newInstance("Skills necklace: Farming Guild", (Supplier<WorldPoint>) () -> landing,
                    null, -1, null, (IntSupplier) () -> 1, -1, new int[] {ItemID.JEWL_NECKLACE_OF_SKILLS_4},
                    (Supplier<String>) () -> "Farming Guild", (BooleanSupplier) () -> true, true);
            Field selected = WalkerTestReflection.declaredField(engineType, "activeTeleportProvider"); selected.setAccessible(true); selected.set(engine, provider);
            Field target = WalkerTestReflection.declaredField(engineType, "expectedTeleportDestination"); target.setAccessible(true); target.set(engine, landing);
            Method wornProvider = WalkerTestReflection.declaredMethod(providerType, "requiresEquipmentTab"); wornProvider.setAccessible(true);
            assertEquals(true, wornProvider.invoke(provider), "Fixture must offer a usable worn members-world teleport");
            Method activate = WalkerTestReflection.declaredMethod(engineType, "tickTeleport", Class.forName(shadow + ".WalkerEngine$RouteRequest", true, loader), Player.class);
            activate.setAccessible(true);
            activate.invoke(engine, null, player);
            equipment.verify(() -> Rs2Equipment.interact(ItemID.JEWL_NECKLACE_OF_SKILLS_4, "Farming Guild"), times(1));
            tabs.verify(() -> Rs2Tab.onVarClientIntChanged(any(VarClientIntChanged.class)), times(1));
            tabs.verify(() -> Rs2Tab.isCurrentTab(any()), never());
            tabs.verifyNoMoreInteractions();
            verify(client, never()).runScript(anyInt(), any());
            assertSame(provider, selected.get(engine), "Dispatch must still wait for the actual teleport landing");
        }
    }
}
