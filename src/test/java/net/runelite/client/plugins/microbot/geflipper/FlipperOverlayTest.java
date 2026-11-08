package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlipperOverlayTest {
    @Test
    void loggedOutReceiverShowsListeningAndReceivedWithoutCopilotFinancialData() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.MULE_RECEIVER, GameState.LOGIN_SCREEN, true)) {
            fixture.script.connection = snapshot(MuleConnectionMonitor.State.LISTENING, false);
            fixture.overlay.publishStats(0, FlipperOverlayPrivacyTest.syntheticStats());

            assertNotNull(fixture.render());
            assertEquals("Listening (no sender contact)", fixture.right("Flipper link:"));
            assertEquals(Color.ORANGE, fixture.rightColor("Flipper link:"));
            assertEquals("+2.00M gp", fixture.right("Muled (session):"));
            assertEquals("+2.00M gp/hr", fixture.right("Muled GP/hr:"));
            assertEquals("+9.00B gp", fixture.right("Muled (all time):"));
            assertEquals("3.00M gp (last seen)", fixture.right("Total held:"),
                "A coin count seen before logout is labelled, not passed off as live");
            assertFalse(fixture.labels().contains("Mule Received:"));
            assertFalse(fixture.labels().contains("GP/hr:"));
            assertFalse(fixture.labels().contains("Copilot Profit:"));
            assertFalse(fixture.labels().contains("Session Time:"));
            assertEquals(0, fixture.script.capitalReads);
            FlipperOverlayPrivacyTest.assertEmpty(fixture.overlay);
            assertFalse(fixture.overlay.publishStats(0, FlipperOverlayPrivacyTest.syntheticStats()),
                "A delayed sender/Copilot read must not republish financial data into the receiver overlay");
        }
    }

    @Test
    void receiverDistinguishesRecentValidatedSenderContactFromMerelyListening() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.MULE_RECEIVER, GameState.LOGIN_SCREEN, true)) {
            fixture.script.connection = snapshot(MuleConnectionMonitor.State.LISTENING, false);
            fixture.render();
            assertEquals(Color.ORANGE, fixture.rightColor("Flipper link:"));

            fixture.script.connection = snapshot(MuleConnectionMonitor.State.SENDER_SEEN, true);
            fixture.render();
            assertEquals("Sender connected", fixture.right("Flipper link:"));
            assertEquals(new Color(0x52D273), fixture.rightColor("Flipper link:"));
            assertEquals(2, fixture.script.connectionReads);
        }
    }

    @Test
    void loggedInReceiverShowsTheLiveTotalHeldAndTheStatusMessageLast() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.MULE_RECEIVER, GameState.LOGGED_IN, true)) {
            fixture.script.connection = snapshot(MuleConnectionMonitor.State.SENDER_SEEN, true);
            assertNotNull(fixture.render());
            assertEquals("3.00M gp", fixture.right("Total held:"));
            assertEquals(Color.ORANGE, fixture.leftColor("Mule: standby."));
            List<String> labels = fixture.labels();
            assertEquals("Mule: standby.", labels.get(labels.size() - 1));
            assertEquals(List.of("Flipper link:", "Total held:", "Muled (session):", "Muled GP/hr:",
                "Muled (all time):", "Mule: standby."), labels);
        }
    }

    @Test
    void receiverBeforeAnyTradeShowsMutedZerosAndAnUnknownCoinCount() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.MULE_RECEIVER, GameState.LOGIN_SCREEN, true)) {
            fixture.script.ledger = MuleLedger.Snapshot.EMPTY;
            fixture.render();
            assertEquals("Unknown", fixture.right("Total held:"));
            assertEquals("0 gp", fixture.right("Muled (session):"));
            assertEquals("0 gp/hr", fixture.right("Muled GP/hr:"));
            assertEquals("0 gp", fixture.right("Muled (all time):"));
            assertEquals(Color.LIGHT_GRAY, fixture.rightColor("Muled (session):"));
        }
    }

    @Test
    void senderRetainsCopilotStatsAndAddsCachedLinkAndCapitalCoverage() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.FLIPPER_SENDER, GameState.LOGGED_IN, true)) {
            fixture.script.connection = snapshot(MuleConnectionMonitor.State.READY, true);
            fixture.script.capitalStatus = "Capital: partial estimate";
            fixture.overlay.publishStats(0, new FlipperOverlay.DisplayStats("+12.3K gp", "+4.1K gp/hr", "03:00:00"));
            field(FlipperOverlay.class, "lastFetchTime").setLong(fixture.overlay, System.currentTimeMillis());

            fixture.render();
            assertEquals("Connected (ready)", fixture.right("Mule link:"));
            assertEquals(new Color(0x52D273), fixture.rightColor("Mule link:"));
            assertEquals("+4.1K gp/hr", fixture.right("GP/hr:"));
            assertEquals("+12.3K gp", fixture.right("Copilot Profit:"));
            assertEquals("03:00:00", fixture.right("Session Time:"));
            assertEquals("+2.00M gp", fixture.right("Mule Sent:"));
            assertFalse(fixture.labels().contains("Total held:"), "Receiver rows never appear on a sender");
            assertTrue(fixture.labels().contains("Capital: partial estimate"));
            assertEquals(1, fixture.script.connectionReads);
            assertEquals(1, fixture.script.capitalReads);
        }
    }

    @Test
    void staleOfflineAndWrongMuleStatusesAreNotShownAsHealthyConnections() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.FLIPPER_SENDER, GameState.LOGGED_IN, true)) {
            for (MuleConnectionMonitor.State state : new MuleConnectionMonitor.State[]{
                MuleConnectionMonitor.State.OFFLINE, MuleConnectionMonitor.State.WRONG_MULE,
                MuleConnectionMonitor.State.STALE}) {
                fixture.script.connection = snapshot(state, false);
                fixture.render();
                assertEquals(fixture.script.connection.label(), fixture.right("Mule link:"));
                assertEquals(new Color(0xFF6666), fixture.rightColor("Mule link:"));
            }
        }
    }

    @Test
    void offModeDoesNotQueryOrDisplayMuleConnectionAndLoggedOutSenderStaysHidden() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.OFF, GameState.LOGGED_IN, true)) {
            fixture.render();
            assertFalse(fixture.labels().contains("Mule link:"));
            assertFalse(fixture.labels().contains("Flipper link:"));
            assertEquals(0, fixture.script.connectionReads);
        }
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.FLIPPER_SENDER, GameState.LOGIN_SCREEN, true)) {
            assertNull(fixture.render());
            assertTrue(fixture.labels().isEmpty());
            assertEquals(0, fixture.script.connectionReads);
        }
    }

    @Test
    void overlayOffRemainsHiddenForReceiverAndErasesCachedFinancialStrings() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.MULE_RECEIVER, GameState.LOGIN_SCREEN, false)) {
            fixture.overlay.publishStats(0, FlipperOverlayPrivacyTest.syntheticStats());
            assertNull(fixture.render());
            assertTrue(fixture.labels().isEmpty());
            assertEquals(0, fixture.script.connectionReads);
            FlipperOverlayPrivacyTest.assertEmpty(fixture.overlay);
        }
    }

    @Test
    void unavailableCachedConnectionShowsCheckingWithoutInventingAHealthyPeer() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.MULE_RECEIVER, GameState.LOGIN_SCREEN, true)) {
            fixture.script.connection = null;
            fixture.render();
            assertEquals("Checking", fixture.right("Flipper link:"));
            assertEquals(Color.ORANGE, fixture.rightColor("Flipper link:"));
        }
    }

    // ---- Placement over the inventory ----

    /** A fixed-mode-like inventory: a 4 x 7 grid of 36 x 32 slots on a 42 x 36 pitch, inside a 190 x 261 tab. */
    private static final Rectangle TAB = new Rectangle(548, 205, 190, 261);
    private static final Rectangle ITEMS = new Rectangle(563, 213, 162, 248);
    private static final int TOP_ROW_BOTTOM = ITEMS.y + 32;

    @Test
    void theSpotStartsBelowTheTopRowAndFillsTheInventoryTab() {
        Rectangle spot = FlipperOverlay.belowTopRow(ITEMS, TOP_ROW_BOTTOM, TAB);
        assertNotNull(spot);
        assertTrue(spot.y >= TOP_ROW_BOTTOM, "The top row, where the coins are, stays in view");
        assertTrue(TAB.contains(spot), "Stays inside the inventory tab");
        assertTrue(spot.width > ITEMS.width, "Uses the tab's width for longer rows");
        assertEquals(TAB.y + TAB.height, spot.y + spot.height, 2.0);
    }

    @Test
    void theSpotFallsBackToTheGridWhenSlotsOrTabCantBeUsed() {
        Rectangle spot = FlipperOverlay.belowTopRow(ITEMS, -1, null);
        assertNotNull(spot);
        assertTrue(spot.y >= ITEMS.y + ITEMS.height / 7, "Unknown slots: skip one row's share of the grid");
        assertEquals(ITEMS.x, spot.x);
        assertEquals(ITEMS.width, spot.width);

        Rectangle hugeTab = new Rectangle(0, 0, 765, 503);
        assertEquals(ITEMS.width, FlipperOverlay.belowTopRow(ITEMS, TOP_ROW_BOTTOM, hugeTab).width,
            "A container far bigger than the grid isn't the inventory tab");
        assertNull(FlipperOverlay.belowTopRow(new Rectangle(0, 0, 0, 0), -1, null), "Not laid out yet");
        assertNull(FlipperOverlay.belowTopRow(new Rectangle(10, 10, 90, 248), -1, null), "Too narrow for the rows");
        assertNull(FlipperOverlay.belowTopRow(null, -1, TAB));
    }

    @Test
    void thePanelSitsOverTheInventoryBelowItsTopRow() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.OFF, GameState.LOGGED_IN, true)) {
            fixture.showInventory(InterfaceID.Inventory.ITEMS, ITEMS);
            assertNull(fixture.render(), "The frame the placement changes is skipped");
            assertEquals(OverlayPosition.DETACHED, fixture.overlay.getPosition());

            Dimension drawn = fixture.render();
            Rectangle spot = FlipperOverlay.belowTopRow(ITEMS, TOP_ROW_BOTTOM, TAB);
            assertNotNull(drawn);
            assertEquals(spot.getLocation(), fixture.overlay.getBounds().getLocation());
            assertEquals(spot.width, drawn.width);
            assertTrue(drawn.height <= spot.height, "Fits below the top row");
            assertTrue(fixture.labels().contains("GP/hr:"));
        }
    }

    @Test
    void theGrandExchangeInventoryIsUsedWhileTheExchangeIsOpen() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.OFF, GameState.LOGGED_IN, true)) {
            Rectangle geItems = new Rectangle(ITEMS.x, ITEMS.y + 4, ITEMS.width, ITEMS.height);
            fixture.showInventory(InterfaceID.Inventory.ITEMS, ITEMS);
            fixture.showInventory(InterfaceID.GeOffersSide.ITEMS, geItems);
            fixture.render();
            fixture.render();
            assertEquals(FlipperOverlay.belowTopRow(geItems, geItems.y + 32, TAB).getLocation(),
                fixture.overlay.getBounds().getLocation());
        }
    }

    @Test
    void withNoInventoryShowingThePanelGoesBackToTheTopLeftCorner() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.OFF, GameState.LOGGED_IN, true)) {
            fixture.showInventory(InterfaceID.Inventory.ITEMS, ITEMS);
            fixture.render();
            fixture.render();
            fixture.hidden = true; // e.g. the prayer tab is open
            assertNull(fixture.render());
            assertEquals(OverlayPosition.TOP_LEFT, fixture.overlay.getPosition());
            fixture.render(); // a panel takes its size from the rows it laid out the frame before
            assertEquals(210, fixture.render().width, "Normal size in the corner");
        }
    }

    @Test
    void aPanelThePlayerMovedStaysWhereTheyPutIt() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.OFF, GameState.LOGGED_IN, true)) {
            fixture.showInventory(InterfaceID.Inventory.ITEMS, ITEMS);
            fixture.overlay.setPreferredLocation(new Point(40, 60)); // Alt+drag, or a saved spot from before
            fixture.render();
            Dimension drawn = fixture.render();
            assertNotNull(drawn);
            assertEquals(OverlayPosition.TOP_LEFT, fixture.overlay.getPosition());
            assertEquals(210, drawn.width);

            fixture.overlay.setPreferredLocation(null); // Alt+right-click reset
            fixture.render();
            fixture.render();
            assertEquals(OverlayPosition.DETACHED, fixture.overlay.getPosition());
        }
    }

    @Test
    void aRightClickMenuOverThePanelHidesItUntilClosed() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.OFF, GameState.LOGGED_IN, true)) {
            fixture.showInventory(InterfaceID.Inventory.ITEMS, ITEMS);
            fixture.render();
            fixture.menu = new Rectangle(600, 300, 120, 80);
            assertNull(fixture.render());
            fixture.menu = new Rectangle(100, 100, 120, 80); // a menu over the game world
            assertNotNull(fixture.render());
            fixture.menu = null;
            assertNotNull(fixture.render());
        }
    }

    @Test
    void whenSpaceIsShortDetailRowsAreLeftOutFirst() throws Exception {
        try (Fixture fixture = new Fixture(FlipperConfig.MuleMode.FLIPPER_SENDER, GameState.LOGGED_IN, true)) {
            fixture.world = 330;
            fixture.script.connection = snapshot(MuleConnectionMonitor.State.READY, true);
            fixture.script.capitalStatus = "Capital: partial estimate";
            fixture.overlay.publishStats(0, new FlipperOverlay.DisplayStats("+12.3K gp", "+4.1K gp/hr", "03:00:00"));
            field(FlipperOverlay.class, "lastFetchTime").setLong(fixture.overlay, System.currentTimeMillis());
            Rectangle shortItems = new Rectangle(ITEMS.x, ITEMS.y, ITEMS.width, 200);
            fixture.tab = new Rectangle(TAB.x, ITEMS.y - 4, TAB.width, 208);
            fixture.showInventory(InterfaceID.Inventory.ITEMS, shortItems);
            fixture.render();
            Dimension drawn = fixture.render();

            List<String> labels = fixture.labels();
            for (String kept : new String[]{"Status:", "GP/hr:", "Copilot Profit:", "Mule link:", "Mule Sent:"}) {
                assertTrue(labels.contains(kept), kept + " is kept");
            }
            assertFalse(labels.contains("World:"), "World goes first");
            assertFalse(labels.contains("Session Time:"), "Session time goes first");
            Rectangle spot = FlipperOverlay.belowTopRow(shortItems, shortItems.y + 32, fixture.tab);
            assertTrue(drawn.height <= spot.height, "Fits: " + drawn.height + " <= " + spot.height);
        }
    }

    private static MuleConnectionMonitor.Snapshot snapshot(MuleConnectionMonitor.State state, boolean connected) {
        return new MuleConnectionMonitor.Snapshot(state, connected, connected, 1_000L);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class ScriptStub extends FlipperScript {
        MuleConnectionMonitor.Snapshot connection;
        String capitalStatus = "";
        MuleLedger.Snapshot ledger = new MuleLedger.Snapshot(2_000_000L, 9_000_000_000L, 3_000_000L, 3_600_000L);
        int connectionReads;
        int capitalReads;

        @Override public MuleConnectionMonitor.Snapshot getMuleConnectionStatus() { connectionReads++; return connection; }
        @Override public String getMuleCapitalStatus() { capitalReads++; return capitalStatus; }
        @Override public long getTotalMuledGp() { return 2_000_000L; }
        @Override public MuleLedger.Snapshot getMuleLedgerSnapshot() { return ledger; }
        @Override public String getSlotActionStatus() { return "Mule: standby."; }
    }

    private static final class Fixture implements AutoCloseable {
        final ScriptStub script = new ScriptStub();
        final FlipperOverlay overlay;
        private final Field clientField;
        private final Field managerField;
        private final Object oldClient;
        private final Object oldManager;
        private final Graphics2D graphics;
        final Map<Integer, Widget> widgets = new HashMap<>();
        Rectangle tab = TAB;
        boolean hidden;
        Rectangle menu;
        int world;

        Fixture(FlipperConfig.MuleMode mode, GameState gameState, boolean visible) throws Exception {
            clientField = field(Microbot.class, "client");
            managerField = field(Microbot.class, "pluginManager");
            oldClient = clientField.get(null);
            oldManager = managerField.get(null);
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getGameState": return gameState;
                        case "getWorld": return world;
                        case "isMenuOpen": return menu != null;
                        case "getMenuX": return menu == null ? 0 : menu.x;
                        case "getMenuY": return menu == null ? 0 : menu.y;
                        case "getMenuWidth": return menu == null ? 0 : menu.width;
                        case "getMenuHeight": return menu == null ? 0 : menu.height;
                        case "getWidget":
                            if (args != null && args.length == 1 && args[0] instanceof Integer) return widgets.get(args[0]);
                            break;
                        default:
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == float.class) return 0F;
                    if (type == double.class) return 0D;
                    return null;
                });
            clientField.set(null, client);
            managerField.set(null, null); // Receiver rendering must not depend on a Copilot/plugin manager.
            FlipperConfig config = new FlipperConfig() {
                @Override public MuleMode muleMode() { return mode; }
                @Override public boolean showOverlay() { return visible; }
            };
            FlipperPlugin plugin = new FlipperPlugin();
            field(FlipperPlugin.class, "flipperScript").set(plugin, script);
            overlay = new FlipperOverlay(plugin, config);
            overlay.setClearChildren(false);
            graphics = new BufferedImage(640, 480, BufferedImage.TYPE_INT_ARGB).createGraphics();
        }

        Dimension render() { return overlay.render(graphics); }

        /** An inventory item grid with 28 slots laid out like the game's, inside the fixture's tab. */
        void showInventory(int id, Rectangle items) {
            Widget[] slots = new Widget[28];
            for (int i = 0; i < slots.length; i++) {
                Rectangle slot = new Rectangle(items.x + (i % 4) * 42, items.y + (i / 4) * 36, 36, 32);
                slots[i] = widget(() -> slot, null, null);
            }
            Widget tabWidget = widget(() -> tab, null, null);
            widgets.put(id, widget(() -> items, tabWidget, slots));
        }

        private Widget widget(java.util.function.Supplier<Rectangle> bounds, Widget parent, Widget[] children) {
            return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getBounds": return bounds.get();
                        case "isHidden": return hidden;
                        case "getParent": return parent;
                        case "getDynamicChildren": return children;
                        default:
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    return null;
                });
        }

        List<String> labels() throws Exception {
            return overlay.getPanelComponent().getChildren().stream().filter(LineComponent.class::isInstance)
                .map(LineComponent.class::cast).map(line -> {
                    try { return (String) field(LineComponent.class, "left").get(line); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                }).collect(Collectors.toList());
        }

        LineComponent line(String label) throws Exception {
            for (Object child : overlay.getPanelComponent().getChildren()) {
                if (child instanceof LineComponent && label.equals(field(LineComponent.class, "left").get(child))) {
                    return (LineComponent) child;
                }
            }
            fail("Missing overlay label: " + label);
            return null;
        }

        String right(String label) throws Exception { return (String) field(LineComponent.class, "right").get(line(label)); }
        Color leftColor(String label) throws Exception { return (Color) field(LineComponent.class, "leftColor").get(line(label)); }
        Color rightColor(String label) throws Exception { return (Color) field(LineComponent.class, "rightColor").get(line(label)); }

        @Override public void close() throws Exception {
            graphics.dispose();
            clientField.set(null, oldClient);
            managerField.set(null, oldManager);
        }
    }
}
