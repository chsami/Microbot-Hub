package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.events.ConfigChanged;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FlipperOverlayPrivacyTest {
    @Test
    public void readsExistingFormattedPanelScopeEvenWhenSidebarIsClosed() throws Exception {
        AtomicReference<FlipperOverlay.DisplayStats> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(FlipperOverlay.DisplayStats.read(new Panel())));
        assertEquals("+12.3K gp", result.get().overall);
        assertEquals("+4.1K gp/hr", result.get().hourly);
        assertEquals("03:00:00", result.get().time);
    }

    @Test
    public void missingPanelLabelsUseEmptyDisplayWithoutQueryingAccountModels() {
        FlipperOverlay.DisplayStats stats = FlipperOverlay.DisplayStats.read(new Object());
        assertEquals("-", stats.overall);
        assertEquals("-", stats.hourly);
        assertEquals("-", stats.time);
    }

    @Test
    public void resetErasesCachedFinancialStringsAndRejectsQueuedOldRead() throws Exception {
        FlipperOverlay overlay = new FlipperOverlay(new FlipperPlugin(), new FlipperConfig() {});
        assertTrue(overlay.publishStats(0, syntheticStats()));
        overlay.clearStats();
        assertEmpty(overlay);
        assertFalse(overlay.publishStats(0, syntheticStats()));
        assertEmpty(overlay);
    }

    @Test
    public void logoutAndOverlayOffEraseCachedFinancialStringsAndTradingReferences() throws Exception {
        FlipperPlugin plugin = new FlipperPlugin();
        FlipperScript script = new FlipperScript();
        FlipperConfig config = new FlipperConfig() {
            @Override public boolean showOverlay() { return false; }
        };
        FlipperOverlay overlay = new FlipperOverlay(plugin, config);
        field(FlipperPlugin.class, "overlay").set(plugin, overlay);
        field(FlipperPlugin.class, "config").set(plugin, config);
        field(FlipperPlugin.class, "flipperScript").set(plugin, script);
        field(FlipperScript.class, "flippingCopilot").set(script, new FlipperPlugin());
        field(FlipperScript.class, "suggestionManager").set(script, new Object());
        field(FlipperScript.class, "highlightController").set(script, new Object());
        field(FlipperScript.class, "blockedSlotActionKey").set(script, "synthetic trade identity");
        overlay.publishStats(0, syntheticStats());
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        assertEmpty(overlay);
        for (String name : new String[]{"flippingCopilot", "suggestionManager", "highlightController", "blockedSlotActionKey"}) {
            assertNull(field(FlipperScript.class, name).get(script), name + " retained after logout");
        }
        long generation = field(FlipperOverlay.class, "statsGeneration").getLong(overlay);
        overlay.publishStats(generation, syntheticStats());
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup("Flipper Config");
        changed.setKey("showOverlay");
        plugin.onConfigChanged(changed);
        assertEmpty(overlay);
        assertFalse(overlay.publishStats(generation, syntheticStats()));
    }

    static void assertEmpty(FlipperOverlay overlay) throws Exception {
        FlipperOverlay.DisplayStats stats = (FlipperOverlay.DisplayStats) field(FlipperOverlay.class, "displayStats").get(overlay);
        assertEquals("-", stats.overall);
        assertEquals("-", stats.hourly);
        assertNull(stats.time);
    }

    static FlipperOverlay.DisplayStats syntheticStats() {
        return new FlipperOverlay.DisplayStats("synthetic profit", "synthetic hourly", "synthetic time");
    }

    static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** Synthetic UI only: deliberately offers no account/trade/statistics calculation API. */
    private static final class Panel {
        private final JLabel totalProfitVal = new JLabel("+12.3K gp");
        private final JLabel hourlyProfitVal = new JLabel("+4.1K gp/hr");
        private final JLabel sessionTimeVal = new JLabel("03:00:00");
    }
}
