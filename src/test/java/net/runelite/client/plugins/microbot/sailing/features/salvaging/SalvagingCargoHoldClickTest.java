package net.runelite.client.plugins.microbot.sailing.features.salvaging;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SalvagingCargoHoldClickTest {

    private static final int SALVAGE_ITEM_ID = 32847;

    private ExecutorService clientExecutor;
    private Thread clientThread;
    private Client client;
    private ClientThread clientThreadMock;
    private final List<Thread> widgetLookupThreads = new ArrayList<>();
    private final List<Thread> clickThreads = new ArrayList<>();
    private final List<Widget> clickedWidgets = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        clientExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-client-thread"));
        clientThread = clientExecutor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
        client = mock(Client.class);
        clientThreadMock = mock(ClientThread.class);
        when(clientThreadMock.runOnClientThreadOptional(any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            return Optional.ofNullable(clientExecutor.submit(callable).get(5, TimeUnit.SECONDS));
        });
        when(client.getWidget(anyInt())).thenAnswer(invocation -> {
            widgetLookupThreads.add(Thread.currentThread());
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        clientExecutor.shutdownNow();
    }

    @Test
    void depositClickRunsOnScriptThreadAfterLookupReturns() throws Exception {
        Widget universe = visibleWidget();
        Widget deposit = visibleWidget();
        stubWidget(InterfaceID.SailingBoatCargohold.UNIVERSE, universe);
        stubWidget(InterfaceID.SailingBoatCargohold.DEPOSITALL_INVENTORY, deposit);

        boolean clicked = runWithMocks("clickDepositInventoryInOpenCargoHold");

        assertTrue(clicked);
        assertEquals(1, clickedWidgets.size());
        assertSame(deposit, clickedWidgets.get(0));
        assertSame(Thread.currentThread(), clickThreads.get(0));
        assertFalse(widgetLookupThreads.isEmpty());
        widgetLookupThreads.forEach(thread -> assertSame(clientThread, thread));
    }

    @Test
    void depositReportsFailureWhenHoldIsClosed() throws Exception {
        Widget universe = visibleWidget();
        when(universe.isHidden()).thenReturn(true);
        stubWidget(InterfaceID.SailingBoatCargohold.UNIVERSE, universe);

        boolean clicked = runWithMocks("clickDepositInventoryInOpenCargoHold");

        assertFalse(clicked);
        assertTrue(clickedWidgets.stream().allMatch(w -> w == null));
    }

    @Test
    void withdrawClickRunsOnScriptThreadAfterLookupReturns() throws Exception {
        Widget items = visibleWidget();
        Widget salvageSlot = visibleWidget();
        when(salvageSlot.getItemId()).thenReturn(SALVAGE_ITEM_ID);
        when(items.getChildren()).thenReturn(new Widget[] { salvageSlot });
        stubWidget(InterfaceID.SailingBoatCargohold.ITEMS, items);
        ItemComposition definition = mock(ItemComposition.class);
        when(definition.getName()).thenReturn("Small salvage");
        when(client.getItemDefinition(SALVAGE_ITEM_ID)).thenReturn(definition);

        boolean clicked = runWithMocks("invokeWithdrawOneSalvageStackFromCargoHoldUi");

        assertTrue(clicked);
        assertEquals(1, clickedWidgets.size());
        assertSame(salvageSlot, clickedWidgets.get(0));
        assertSame(Thread.currentThread(), clickThreads.get(0));
        widgetLookupThreads.forEach(thread -> assertSame(clientThread, thread));
    }

    @Test
    void withdrawReportsFailureWhenNoSalvageStackIsShown() throws Exception {
        stubWidget(InterfaceID.SailingBoatCargohold.ITEMS, visibleWidget());

        boolean clicked = runWithMocks("invokeWithdrawOneSalvageStackFromCargoHoldUi");

        assertFalse(clicked);
        assertTrue(clickedWidgets.stream().allMatch(w -> w == null));
    }

    private boolean runWithMocks(String methodName) throws Exception {
        SalvagingScript script = new SalvagingScript(null, null, null);
        Method method = SalvagingScript.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        widgetLookupThreads.clear();
        Object previousClient = swapMicrobotField("client", client);
        Object previousClientThread = swapMicrobotField("clientThread", clientThreadMock);
        try (MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class)) {
            widgets.when(() -> Rs2Widget.clickWidget(any(Widget.class))).thenAnswer(invocation -> record(invocation.getArgument(0)));
            widgets.when(() -> Rs2Widget.clickWidget((Widget) null)).thenAnswer(invocation -> record(null));
            return (boolean) method.invoke(script);
        } finally {
            swapMicrobotField("client", previousClient);
            swapMicrobotField("clientThread", previousClientThread);
        }
    }

    private static Object swapMicrobotField(String name, Object value) throws Exception {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }

    private boolean record(Widget widget) {
        clickThreads.add(Thread.currentThread());
        clickedWidgets.add(widget);
        return widget != null;
    }

    private void stubWidget(int componentId, Widget widget) {
        when(client.getWidget(componentId)).thenAnswer(invocation -> {
            widgetLookupThreads.add(Thread.currentThread());
            return widget;
        });
    }

    private static Widget visibleWidget() {
        Widget widget = mock(Widget.class);
        when(widget.isHidden()).thenReturn(false);
        return widget;
    }
}
