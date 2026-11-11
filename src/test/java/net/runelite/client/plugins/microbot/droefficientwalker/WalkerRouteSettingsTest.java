package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import net.runelite.client.events.ConfigChanged;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalkerRouteSettingsTest {
    @Test void explicitSaveRefreshQueuesWorkAndToleratesAnInactivePlugin() throws Exception {
        EfficientWalkerPlugin plugin = new EfficientWalkerPlugin();
        plugin.refreshRouteSettings();
        EfficientWalker walker = mock(EfficientWalker.class);
        Field field = WalkerTestReflection.declaredField(EfficientWalkerPlugin.class, "walker");
        field.setAccessible(true); field.set(plugin, walker);
        plugin.refreshRouteSettings();
        verify(walker).routeSettingsChanged();
        verifyNoMoreInteractions(walker);
    }
    @Test void transportAndNetworkEditsInvalidateRoutingWhilePresentationAndActionButtonsDoNot() {
        for (String key : new String[] {"useTeleportations", "useItemsFromBank", "useFairyRings", "useSpiritTrees", "spiritTreeFarmingGuild", "useMaxPoh", "useFeroxTeleport", "allowWildernessTravel"})
            assertTrue(WalkerRouteSettings.affectsRoute(EfficientWalkerConfig.GROUP, key), key);
        for (String key : new String[] {"showStatusOverlay", "useMicrobotMouse", "walkToNamedDestination", "namedDestination", "exportDiagnostics", "wildernessThreatRadius"})
            assertFalse(WalkerRouteSettings.affectsRoute(EfficientWalkerConfig.GROUP, key), key);
        assertFalse(WalkerRouteSettings.affectsRoute("Herbrun", "useTeleportations"));
    }

    @Test void changesCoalesceAndAnEditAfterConsumptionRemainsPending() {
        WalkerRouteSettings settings = new WalkerRouteSettings();
        settings.changed(); settings.changed(); settings.changed();
        assertTrue(settings.consume());
        assertFalse(settings.consume());
        settings.changed();
        assertTrue(settings.pending());
        assertTrue(settings.consume());
        settings.changed(); settings.clear();
        assertFalse(settings.pending());
    }

    @Test void pluginQueuesARefreshWithoutCancellingOrStartingAJourneyOnTheSettingsThread() throws Exception {
        EfficientWalkerPlugin plugin = new EfficientWalkerPlugin();
        EfficientWalker walker = mock(EfficientWalker.class);
        Field field = WalkerTestReflection.declaredField(EfficientWalkerPlugin.class, "walker"); field.setAccessible(true); field.set(plugin, walker);
        ConfigChanged change = new ConfigChanged(); change.setGroup(EfficientWalkerConfig.GROUP); change.setKey("useTeleportations");
        plugin.onConfigChanged(change);
        verify(walker).routeSettingsChanged();
        verifyNoMoreInteractions(walker);
        change.setKey("showStatusOverlay"); plugin.onConfigChanged(change);
        verifyNoMoreInteractions(walker);
    }
}
