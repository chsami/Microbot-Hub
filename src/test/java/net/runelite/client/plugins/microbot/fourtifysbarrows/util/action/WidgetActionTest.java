package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WidgetActionTest
{
    @Test
    public void admissionQueuesWithoutSendingAndDispatchCountsTheActualMenuRequest() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
        fixture.actions.dispatch();
        assertEquals(1, fixture.requests.size());
        Object[] request = fixture.requests.get(0);
        assertEquals(3, request[0]);
        assertEquals(Fixture.WIDGET_ID, request[1]);
        assertEquals(MenuAction.CC_OP, request[2]);
        assertEquals(1, request[3]);
        assertEquals(1625, request[4]);
        assertEquals("Select", request[5]);
        assertEquals(1, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void physicalDefaultClickRejectsANonDefaultWidgetOption() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.options = new String[] {null, null, null, null, null, "Select"};
        assertFalse(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void staleWidgetIdentityAndVisibilityRejectDispatchWithoutCounting() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.current = fixture.widget();
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.hidden = true;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void changedSlotItemOrOptionRejectsDispatchWithoutCounting() throws Exception
    {
        Fixture fixture = new Fixture();
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.index = 4;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.itemId = 1627;
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.options = new String[] {"Other", "Select"};
        fixture.actions.dispatch();
        assertTrue(fixture.requests.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
    }

    @Test
    public void rejectsMissingControlsAndRequestsBeyondTheQueueCapacity() throws Exception
    {
        Fixture fixture = new Fixture();
        assertFalse(fixture.interactions.widget(Fixture.WIDGET_ID, "Missing", () -> true, null));
        fixture.current = null;
        assertFalse(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        fixture.current = fixture.widget();
        for (int i = 0; i < 50; i++)
        {
            assertTrue(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        }
        assertFalse(fixture.interactions.widget(Fixture.WIDGET_ID, "Select", () -> true, null));
        assertTrue(fixture.requests.isEmpty());
    }

    private static final class Fixture
    {
        static final int WIDGET_ID = (123 << 16) | 4;
        final BarrowsActions actions = new BarrowsActions();
        BarrowsInteractions interactions;
        final List<Object[]> requests = new ArrayList<>();
        String[] options = {"Select"};
        int index = 3;
        int itemId = 1625;
        boolean hidden;
        Widget current = widget();

        Fixture() throws Exception
        {
            WorldView view = (WorldView) Proxy.newProxyInstance(WorldView.class.getClassLoader(), new Class<?>[] {WorldView.class},
                (proxy, method, args) -> null);
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) ->
                {
                    if (method.getName().equals("getWorldView"))
                    {
                        return view;
                    }
                    if (method.getName().equals("isDead"))
                    {
                        return false;
                    }
                    return null;
                });
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getWidget": return current;
                        case "getGameState": return GameState.LOGGED_IN;
                        case "getLocalPlayer": return player;
                        case "getTopLevelWorldView": return view;
                        case "menuAction":
                            requests.add(args.clone());
                            return null;
                        default: return null;
                    }
                });
            MicrobotTestSupport.attach(actions, client);
            Field field = BarrowsActions.class.getDeclaredField("interactions");
            field.setAccessible(true);
            interactions = (BarrowsInteractions) field.get(actions);
        }

        Widget widget()
        {
            return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getId": return WIDGET_ID;
                        case "getIndex": return index;
                        case "getItemId": return itemId;
                        case "getName": return "Control";
                        case "getActions": return options;
                        case "isHidden": return hidden;
                        default: return null;
                    }
                });
        }
    }
}
