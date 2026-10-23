package net.runelite.client.plugins.microbot.drozulrah;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.*;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZulrahInventoryWidgetTest {
    private Client client;
    private Widget cell;
    private Rs2ItemModel item;
    private DroZulrahScript script;
    private final List<MockedStatic<?>> mocks = new ArrayList<>();
    private <T> MockedStatic<T> open(Class<T> type) { var m = mockStatic(type); mocks.add(m); return m; }
    @BeforeEach void setUp() throws Exception {
        client = mock(Client.class); cell = mock(Widget.class); item = mock(Rs2ItemModel.class);
        script = mock(DroZulrahScript.class, CALLS_REAL_METHODS);
        var thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(i -> Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
        var micro = open(Microbot.class); micro.when(Microbot::getClient).thenReturn(client); micro.when(Microbot::getClientThread).thenReturn(thread);
        var inv = open(Rs2Inventory.class); inv.when(() -> Rs2Inventory.get(123)).thenReturn(item); inv.when(() -> Rs2Inventory.contains(123)).thenReturn(true);
        open(Rs2Equipment.class); open(Rs2Tab.class);
        var container = mock(Widget.class); when(client.getWidget(ComponentID.INVENTORY_CONTAINER)).thenReturn(container);
        when(container.getChild(2)).thenReturn(cell); when(cell.getItemId()).thenReturn(123);
        when(item.getId()).thenReturn(123); when(item.getSlot()).thenReturn(2); when(item.getName()).thenReturn("Item");
        when(item.getInventoryActions()).thenReturn(new String[]{"Eat", "Wear"});
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
    }
    @AfterEach void close() { Collections.reverse(mocks); mocks.forEach(MockedStatic::close); }
    private boolean consume() throws Exception {
        Method m = DroZulrahScript.class.getDeclaredMethod("dispatchConsumable",Rs2ItemModel.class,String.class);
        m.setAccessible(true); return (boolean)m.invoke(script,item,"Eat");
    }
    private int gear() throws Exception {
        Class<?> type = Class.forName(DroZulrahScript.class.getName()+"$GearChoice");
        Constructor<?> constructor = type.getDeclaredConstructor(int.class,String.class,int.class,boolean.class,int.class,int.class);
        constructor.setAccessible(true);
        Object choice = constructor.newInstance(123,"Item",0,false,0,0);
        Method m = DroZulrahScript.class.getDeclaredMethod("dispatchGearBatch",Map.class); m.setAccessible(true);
        return (int)m.invoke(script,Map.of(0,choice));
    }
    @Test void missingLiveActionsCannotEmitDefinitionBasedEatOrWear() throws Exception {
        assertFalse(consume()); assertEquals(0,gear());
        verify(client,never()).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
    }
    @Test void reorderedLiveActionsControlBothConsumableAndGearOperations() throws Exception {
        when(cell.getActions()).thenReturn(new String[]{"Drop", null, "Eat", "Wear"});
        assertTrue(consume()); assertEquals(1,gear());
        verify(client).menuAction(2,ComponentID.INVENTORY_CONTAINER,MenuAction.CC_OP,3,123,"Eat","Item");
        verify(client).menuAction(2,ComponentID.INVENTORY_CONTAINER,MenuAction.CC_OP,4,123,"Wear","Item");
    }
    @Test void missingActionAndChangedSlotDeclineWithoutDispatch() throws Exception {
        when(cell.getActions()).thenReturn(new String[]{"Drop"}); assertFalse(consume()); assertEquals(0,gear());
        when(cell.getActions()).thenReturn(new String[]{"Eat","Wear"}); when(cell.getItemId()).thenReturn(456);
        assertFalse(consume()); assertEquals(0,gear());
        verify(client,never()).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
    }
}
