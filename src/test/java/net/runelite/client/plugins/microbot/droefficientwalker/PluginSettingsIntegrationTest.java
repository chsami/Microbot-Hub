package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.config.*;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Saved configuration, optional lifecycle and map behavior of the imported module. */
public class PluginSettingsIntegrationTest {
    private static final String GROUP = EfficientWalkerConfig.GROUP, OLD = EfficientSailingConfig.LEGACY_GROUP;
    private static final class Fixture {
        final Map<String, String> values = new HashMap<>();
        final ConfigManager manager = mock(ConfigManager.class);
        final EfficientWalkerConfig config = mock(EfficientWalkerConfig.class, CALLS_REAL_METHODS);
        final Client client = mock(Client.class);
        final KeyManager keys = mock(KeyManager.class);
        final OverlayManager overlays = mock(OverlayManager.class);
        final WorldMapOverlay mapOverlay = mock(WorldMapOverlay.class);
        final EfficientWalker walker = mock(EfficientWalker.class);
        final EfficientSailingModule module = new EfficientSailingModule(client, config,
                mock(PluginManager.class), manager, keys, overlays, mapOverlay, walker);
        Fixture() {
            when(manager.getConfiguration(anyString(), anyString())).thenAnswer(i -> values.get(i.getArgument(0) + "." + i.getArgument(1)));
            doAnswer(i -> { values.put(i.getArgument(0) + "." + i.getArgument(1), i.getArgument(2)); return null; })
                    .when(manager).setConfiguration(anyString(), anyString(), anyString());
            when(config.knownBoatPort()).thenAnswer(i -> values.getOrDefault(GROUP + ".knownBoatPort", ""));
        }
        void put(String group, String key, String value) { values.put(group + "." + key, value); }
        String get(String key) { return values.get(GROUP + "." + key); }
        void migrate() { EfficientSailingModule.migrateSettings(manager, config); }
    }

    @Test public void allSettingsIncludingHiddenTuningMigrateWithoutReplacingWalkerSelections() {
        Fixture f = new Fixture();
        f.put(OLD, "boatName", "Giant Mind"); f.put(OLD, "currentBoatPort", "SUMMER_SHORE");
        f.put(OLD, "launchPort", "VOID_KNIGHTS_OUTPOST"); f.put(OLD, "portSettingsVersion", "3");
        f.put(OLD, "commandMillis", "700"); f.put(OLD, "useSummonBoatTablet", "true");
        f.put(GROUP, "launchPort", "PORT_KHAZARD");
        f.migrate();
        assertEquals("Giant Mind", f.get("boatName")); assertEquals("SUMMER_SHORE", f.get("currentBoatPort"));
        assertEquals("PORT_KHAZARD", f.get("launchPort")); assertEquals("700", f.get("commandMillis"));
        assertEquals("true", f.get("useSummonBoatTablet"));
        assertNull(f.get("enableSailing"), "Import must remain optional");
        assertEquals("Giant Mind", f.values.get(OLD + ".boatName"));
    }

    @Test public void olderPortNamesUseTheRetainedNativePortMigration() {
        Fixture f = new Fixture();
        f.put(OLD, "knownBoatPort", "Summer Shore"); f.put(OLD, "preferredLaunchPort", "Pest Control");
        f.migrate();
        assertEquals("SUMMER_SHORE", f.get("currentBoatPort"));
        assertEquals("VOID_KNIGHTS_OUTPOST", f.get("launchPort")); assertEquals("3", f.get("portSettingsVersion"));
    }

    @Test public void migrationIsIdempotentAndDoesNotRestoreClearedSettings() {
        Fixture f = new Fixture(); f.put(OLD, "boatName", "Giant Mind"); f.migrate();
        f.put(GROUP, "boatName", ""); clearInvocations(f.manager); f.migrate();
        assertEquals("", f.get("boatName"));
        verify(f.manager, never()).setConfiguration(anyString(), anyString(), anyString());
    }

    @Test public void sailingFollowsWildernessAndDeclaresEveryDefaultForThePanel() throws Exception {
        ConfigSection wilderness = EfficientWalkerConfig.class.getField("wildernessSection").getAnnotation(ConfigSection.class);
        ConfigSection sailing = EfficientWalkerConfig.class.getField("sailingSection").getAnnotation(ConfigSection.class);
        ConfigSection debug = EfficientWalkerConfig.class.getField("debugSection").getAnnotation(ConfigSection.class);
        assertEquals(wilderness.position() + 1, sailing.position()); assertEquals(sailing.position() + 1, debug.position());
        assertEquals("Sailing", sailing.name()); assertFalse(new EfficientWalkerConfig() { }.enableSailing());
        for (Method original : EfficientSailingConfig.class.getDeclaredMethods()) {
            ConfigItem item = original.getAnnotation(ConfigItem.class); if (item == null) continue;
            Method imported = WalkerTestReflection.declaredMethod(EfficientWalkerConfig.class, original.getName());
            assertEquals(item, imported.getAnnotation(ConfigItem.class)); assertEquals(original.getReturnType(), imported.getReturnType());
            assertEquals(original.getAnnotation(Range.class), imported.getAnnotation(Range.class));
            Object expected = original.invoke(new EfficientSailingConfig() { });
            Object actual = imported.invoke(new EfficientWalkerConfig() { });
            if (!(expected instanceof ConfigButton)) assertEquals(expected, actual, original.getName());
        }
    }
    @Test public void simplifiedSailingShowsOneRecoveryChoiceAndHidesRetiredSelectors() throws Exception {
        for(Class<?> type:new Class<?>[]{EfficientSailingConfig.class,EfficientWalkerConfig.class}) {
            for(String method:new String[]{"currentBoatPort","launchPort","allowRecovery"})
                assertTrue(WalkerTestReflection.declaredMethod(type, method).getAnnotation(ConfigItem.class).hidden());
            ConfigItem recall=WalkerTestReflection.declaredMethod(type, "useSummonBoatTablet").getAnnotation(ConfigItem.class);
            assertFalse(recall.hidden());assertEquals("Recall boat with tablet",recall.name());
        }
    }

    @Test public void disabledSailingCreatesNoControllerOverlaysHotkeyOrWalkerInput() {
        Fixture f = new Fixture();
        try (MockedConstruction<EfficientSailingController> controllers = mockConstruction(EfficientSailingController.class)) {
            f.module.start(); f.module.onMenuOpened(new MenuOpened()); f.module.close();
            assertTrue(controllers.constructed().isEmpty()); assertNull(f.module.getNavigationService());
            verifyNoInteractions(f.walker, f.keys, f.overlays, f.client);
        }
    }

    @Test public void toggleClosesOnlySailingAndReenableProvidesAFreshService() {
        Fixture f = new Fixture(); AtomicBoolean enabled = new AtomicBoolean(true);
        when(f.config.enableSailing()).thenAnswer(i -> enabled.get());
        ConfigChanged change = new ConfigChanged(); change.setGroup(GROUP); change.setKey("enableSailing");
        try (MockedConstruction<EfficientSailingController> controllers = mockConstruction(EfficientSailingController.class)) {
            f.module.start(); f.module.start(); assertEquals(1, controllers.constructed().size());
            EfficientSailingController first = controllers.constructed().get(0);
            assertSame(first, f.module.getNavigationService()); verify(first, times(1)).start();
            enabled.set(false); f.module.onConfigChanged(change); assertNull(f.module.getNavigationService()); verify(first).close();
            enabled.set(true); f.module.onConfigChanged(change); assertEquals(2, controllers.constructed().size());
            assertSame(controllers.constructed().get(1), f.module.getNavigationService());
            f.module.close(); f.module.close(); verify(controllers.constructed().get(1), times(1)).close();
            verifyNoInteractions(f.walker); verify(f.overlays, times(6)).add(any()); verify(f.overlays, times(6)).remove(any());
            verify(f.keys, times(2)).registerKeyListener(any()); verify(f.keys, times(2)).unregisterKeyListener(any());
        }
    }

    @Test public void diagnosticsButtonRespondsOnlyToWalkerGroup() throws Exception {
        Fixture f = new Fixture(); EfficientSailingController navigator = mock(EfficientSailingController.class);
        ControllerRecoveryIntegrationTest.set(f.module, "navigator", navigator);
        assertEquals(ConfigButton.class, EfficientWalkerConfig.class.getMethod("exportDiagnostics").getReturnType());
        ConfigChanged event = new ConfigChanged(); event.setGroup(OLD); event.setKey("exportDiagnostics");
        f.module.onConfigChanged(event); verify(navigator, never()).exportDiagnostics();
        event.setGroup(GROUP); f.module.onConfigChanged(event); verify(navigator).exportDiagnostics();
    }

    @Test public void mapMenuRetainsSailHereWithoutStartingAVoyageOrExportingDiagnostics() throws Exception {
        Fixture f = new Fixture(); EfficientSailingController navigator = mock(EfficientSailingController.class);
        ControllerRecoveryIntegrationTest.set(f.module, "navigator", navigator);
        WorldMap map = mock(WorldMap.class); Widget widget = mock(Widget.class); MenuEntry entry = mock(MenuEntry.class, RETURNS_SELF);
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(f.client.getWidget(MicrobotSailingFacade.WORLD_MAP_ROOT)).thenReturn(widget);
        when(widget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));
        when(f.client.getMouseCanvasPosition()).thenReturn(new Point(200, 150)); when(f.client.getMenuEntries()).thenReturn(new MenuEntry[0]);
        when(f.client.getWorldMap()).thenReturn(map); when(map.getWorldMapPosition()).thenReturn(new Point(2685, 3100));
        when(map.getWorldMapZoom()).thenReturn(2f);
        when(f.mapOverlay.mapWorldPointToGraphicsPoint(any(WorldPoint.class))).thenReturn(new Point(200, 150));
        when(navigator.isOceanDestination(new WorldPoint(2685, 3100, 0))).thenReturn(true);
        when(f.client.createMenuEntry(-1)).thenReturn(entry); f.module.onMenuOpened(new MenuOpened());
        verify(entry).setOption("Sail here"); verify(entry, never()).setOption("Export sailing diagnostics");
        verify(f.client, times(1)).createMenuEntry(-1); verify(navigator, never()).sailTo(any()); verifyNoInteractions(f.walker);
    }
    @Test public void anUnchartedMapRequestStillGivesOnlyCancelWhileAVoyageIsActive() throws Exception {
        Fixture f = new Fixture(); EfficientSailingController navigator = mock(EfficientSailingController.class);
        ControllerRecoveryIntegrationTest.set(f.module, "navigator", navigator);
        WorldMap map = mock(WorldMap.class); Widget widget = mock(Widget.class);
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(f.client.getWidget(MicrobotSailingFacade.WORLD_MAP_ROOT)).thenReturn(widget);
        when(widget.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));
        when(f.client.getMouseCanvasPosition()).thenReturn(new Point(200, 150));
        when(f.client.getMenuEntries()).thenReturn(new MenuEntry[0]);
        when(f.client.getWorldMap()).thenReturn(map);
        when(map.getWorldMapPosition()).thenReturn(new Point(3200, 3200));
        when(map.getWorldMapZoom()).thenReturn(2f);
        when(f.mapOverlay.mapWorldPointToGraphicsPoint(any())).thenReturn(new Point(200, 150));
        MenuEntry requestEntry = mock(MenuEntry.class, RETURNS_SELF);
        when(f.client.createMenuEntry(-1)).thenReturn(requestEntry);
        f.module.onMenuOpened(new MenuOpened());
        verify(requestEntry).setOption("Sail here");
        MenuEntry entry = mock(MenuEntry.class, RETURNS_SELF);
        when(f.client.createMenuEntry(-1)).thenReturn(entry);
        when(navigator.isBusy()).thenReturn(true);
        f.module.onMenuOpened(new MenuOpened());
        verify(entry).setOption("Cancel sailing");
        verify(entry, never()).setOption("Sail here");
    }

    @Test public void obsoleteSalvageButtonCannotStartWalker() throws Exception {
        Fixture f = new Fixture(); EfficientSailingController navigator = mock(EfficientSailingController.class);
        ControllerRecoveryIntegrationTest.set(f.module, "navigator", navigator);
        ConfigChanged event = new ConfigChanged(); event.setGroup(OLD); event.setKey("sailToSalvageDestination");
        f.module.onConfigChanged(event); verify(navigator, never()).sailTo(any());
        event.setGroup(GROUP); f.module.onConfigChanged(event);
        verify(navigator, never()).sailTo(any());
        for (Method method : EfficientWalkerConfig.class.getMethods()) {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null) assertFalse(item.keyName().equals("salvageDestination") || item.keyName().equals("sailToSalvageDestination"));
        }
    }

}
