package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CharterMenuRegressionTest {
    private Widget node(Widget... children) {
        Widget widget = mock(Widget.class);
        when(widget.getChildren()).thenReturn(children);
        return widget;
    }

    private Widget row(String label, String... actions) {
        Widget widget = node();
        when(widget.getText()).thenReturn(label);
        when(widget.getActions()).thenReturn(actions);
        return widget;
    }

    @Test public void nestedListLabelUsesItsClickableParentAndLegacyOperation() {
        Client client = mock(Client.class);
        Widget label = row("<col=ff981f>B: Port Piscarilius</col> (2,600 coins)");
        Widget button = node(label), root = node();
        doReturn(new Widget[]{node(button)}).when(root).getStaticChildren();
        when(label.getParent()).thenReturn(button);
        when(button.getActions()).thenReturn(new String[]{null, "Select"});
        when(button.getId()).thenReturn(885 << 16 | 4);
        when(button.getIndex()).thenReturn(11);
        when(button.getBounds()).thenReturn(new Rectangle(100, 200, 120, 25));
        when(client.getWidget(885, 4)).thenReturn(root);

        assertSame(button, CharterMenu.destination(client, "Port Piscarilius"));
        CharterMenu.Selection click = CharterMenu.selection(client, "Port Piscarilius");
        assertEquals("Select", click.option);
        assertEquals(1, click.actionIndex);
        assertEquals(885 << 16 | 4, click.widgetId);
        assertEquals(11, click.index);
        assertEquals(new Rectangle(100, 200, 120, 25), click.bounds);
    }

    @Test public void staticDynamicAndNestedChildrenRemainSearchable() {
        Client client = mock(Client.class);
        Widget root = node(), panel = node(), button = node(), label = row("Catherby");
        when(root.getStaticChildren()).thenReturn(new Widget[]{panel});
        when(panel.getDynamicChildren()).thenReturn(new Widget[]{button});
        when(button.getNestedChildren()).thenReturn(new Widget[]{label});
        when(button.getActions()).thenReturn(new String[]{"Travel"});
        when(label.getParent()).thenReturn(button);
        when(client.getWidget(885, 4)).thenReturn(root);
        assertSame(button, CharterMenu.destination(client, "Catherby"));
        assertEquals("Travel", CharterMenu.selection(client, "Catherby").option);
    }

    @Test public void legacyImplicitFirstOperationIsPreserved() {
        Client client = mock(Client.class);
        Widget label = row("Catherby");
        when(label.getActions()).thenReturn(null);
        doReturn(node(label)).when(client).getWidget(885, 4);
        CharterMenu.Selection click = CharterMenu.selection(client, "Catherby");
        assertNotNull(click);
        assertEquals("Catherby", click.option);
        assertEquals(1, click.actionIndex);
    }

    @Test public void listRepresentationIsPreferredOverDuplicateMapRepresentation() {
        Client client = mock(Client.class);
        Widget list = row("Catherby", "Select"), map = row("", "Catherby");
        doReturn(node(list)).when(client).getWidget(885, 4);
        doReturn(node(map)).when(client).getWidget(72, 3);
        assertSame(list, CharterMenu.destination(client, "Catherby"));
    }

    @Test public void theSameWidgetInMultipleChildArraysIsNotAmbiguous() {
        Client client = mock(Client.class);
        Widget destination = row("Catherby", "Select"), root = node(destination);
        when(root.getDynamicChildren()).thenReturn(new Widget[]{destination});
        when(client.getWidget(885, 4)).thenReturn(root);
        assertSame(destination, CharterMenu.destination(client, "Catherby"));
    }

    @Test public void interfaceShellAndDelayedEntriesAreNotMissingDestinations() {
        Client client = mock(Client.class);
        doReturn(node()).when(client).getWidget(885, 0);
        assertFalse(CharterMenu.open(client));
        Widget root = node();
        when(client.getWidget(885, 4)).thenReturn(root);
        assertTrue(CharterMenu.open(client));
        assertFalse(CharterMenu.populated(client));
        assertFalse(CharterMenu.missing(client, "Port Piscarilius"));
        doReturn(new Widget[]{row("Port Piscarilius", "Select")}).when(root).getChildren();
        assertNotNull(CharterMenu.selection(client, "Port Piscarilius"));
        assertFalse(CharterMenu.missing(client, "Port Piscarilius"));
        assertTrue(CharterMenu.missing(client, "Catherby"));
    }

    @Test public void duplicateListLabelsSelectFirstAndHiddenEntriesAreIgnored() {
        Client client = mock(Client.class);
        doReturn(node(row("Catherby", "Select"), row("Catherby", "Select"))).when(client).getWidget(885, 4);
        assertNotNull(CharterMenu.selection(client, "Catherby"));
        assertFalse(CharterMenu.missing(client, "Catherby"));
        Widget hidden = row("Port Piscarilius", "Select");
        when(hidden.isHidden()).thenReturn(true);
        doReturn(node(hidden)).when(client).getWidget(885, 4);
        assertFalse(CharterMenu.populated(client));
        assertFalse(CharterMenu.missing(client, "Port Piscarilius"));
    }

    @Test public void summerShorePandemoniumAndShipyardListLabelsRemainSupported() {
        Client client = mock(Client.class);
        for (String[] pair : List.of(new String[]{"J: The Summer Shore", "The Summer Shore"},
            new String[]{"I: The Pandemonium", "The Pandemonium"},
            new String[]{"7: Karamja Shipyard", "Shipyard"})) {
            Widget destination = row(pair[0], "Select");
            doReturn(node(destination)).when(client).getWidget(885, 4);
            assertSame(destination, CharterMenu.destination(client, pair[1]), pair[0]);
        }
        doReturn(node(row("", "Catherby extra"))).when(client).getWidget(885, 4);
        assertNotNull(CharterMenu.selection(client, "Catherby"));
    }

    @Test public void listRootCanOwnTheClickAndStaticLabelWinsOverGenericChildren() {
        Client client = mock(Client.class);
        Widget root = node(row("Catherby", "Wrong generic child"));
        Widget label = row("Catherby");
        when(root.getStaticChildren()).thenReturn(new Widget[]{label});
        when(label.getParent()).thenReturn(root);
        when(root.getActions()).thenReturn(new String[]{null, "Select"});
        when(root.getId()).thenReturn(885 << 16 | 4);
        when(root.getIndex()).thenReturn(-1);
        when(client.getWidget(885, 4)).thenReturn(root);
        assertSame(root, CharterMenu.destination(client, "Catherby"));
        CharterMenu.Selection click = CharterMenu.selection(client, "Catherby");
        assertEquals(1, click.actionIndex);
        assertEquals("Select", click.option);
        assertEquals(-1, click.index);
    }

    private NetworkTransportSupport bridge(Client client) throws Exception {
        NetworkTransportSupport bridge = new NetworkTransportSupport(client, mock(EfficientWalkerConfig.class),
            getClass().getClassLoader(), Object.class, new Object(), "unused", mock(CatalogTeleportSupport.class));
        Method rows = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "readCharterRows");
        rows.setAccessible(true);
        field("charterRows").set(bridge, rows.invoke(bridge));
        return bridge;
    }

    private Field field(String name) throws Exception {
        Field field = WalkerTestReflection.declaredField(NetworkTransportSupport.class, name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object edge(WorldPoint origin, WorldPoint destination, String label) throws Exception {
        Class type = Class.forName(NetworkTransportSupport.class.getName() + "$EdgeType");
        Class<?> edge = Class.forName(NetworkTransportSupport.class.getName() + "$NetworkEdge");
        Constructor<?> constructor = edge.getDeclaredConstructor(type, WorldPoint.class, WorldPoint.class,
            String.class, String.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(Enum.valueOf(type, "CHARTER"), origin, destination, label,
            "Trader Crewmember", -1, 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test public void menuFailureSuppressesOnlyThisDockForThisRequest() throws Exception {
        NetworkTransportSupport bridge = bridge(mock(Client.class));
        WorldPoint origin = new WorldPoint(3063, 3000, 0), destination = new WorldPoint(1808, 3679, 0);
        Object edge = edge(origin, destination, "Port Piscarilius");
        Class result = Class.forName(NetworkTransportSupport.class.getName() + "$ExecutionResult");
        Method fail = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "failEdge", edge.getClass(), result, String.class);
        fail.setAccessible(true);
        fail.invoke(bridge, edge, Enum.valueOf(result, "CHARTER_MENU_UNAVAILABLE"), "test");

        Set<String> blocked = (Set<String>) field("requestCharterBlacklist").get(bridge);
        assertTrue(blocked.size() > 1, "Other destinations at the failing menu cannot trigger more NPC clicks");
        for (String key : blocked) assertTrue(key.startsWith(origin.toString()), key);
        assertTrue(((Set<?>) field("sessionCharterBlacklist").get(bridge)).isEmpty(), "Temporary UI failure must not poison the session");
    }

    @SuppressWarnings("unchecked")
    @Test public void openListMenuSelectsOnceWithoutReclickingTheNpc() throws Exception {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        WorldPoint origin = new WorldPoint(3063, 3000, 0), destination = new WorldPoint(1808, 3679, 0);
        AtomicReference<WorldPoint> location = new AtomicReference<>(origin);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenAnswer(ignored -> location.get());
        doReturn(node(row("B: Port Piscarilius", "Select"))).when(client).getWidget(885, 4);
        ClientThread thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
        when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(i ->
            Optional.ofNullable(((Callable<?>) i.getArgument(0)).call()));
        NetworkTransportSupport bridge = bridge(client);
        Object edge = edge(origin, destination, "Port Piscarilius");
        Method execute = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "executeCharter", edge.getClass());
        execute.setAccessible(true);
        try (var micro = mockStatic(Microbot.class); var npc = mockStatic(Rs2Npc.class);
             var inventory = mockStatic(Rs2Inventory.class); var state = mockStatic(Rs2Player.class);
             var worn = mockStatic(TravelEquipment.class); var dialogue = mockStatic(Rs2Dialogue.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            micro.when(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)))
                .thenAnswer(ignored -> { location.set(destination); return null; });
            inventory.when(() -> Rs2Inventory.itemQuantity(995)).thenReturn(100000);
            state.when(() -> Rs2Player.getQuestState(any())).thenReturn(QuestState.FINISHED);
            dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(true);
            dialogue.when(() -> Rs2Dialogue.clickOption(false, "ask again")).thenReturn(true);
            assertEquals("SUCCESS", execute.invoke(bridge, edge).toString());
            micro.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), times(1));
            npc.verifyNoInteractions();
            dialogue.verify(() -> Rs2Dialogue.clickOption(false, "ask again"), times(1));
        }
    }
}
