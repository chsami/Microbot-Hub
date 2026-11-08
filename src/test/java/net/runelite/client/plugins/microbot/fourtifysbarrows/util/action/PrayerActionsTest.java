package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrayerActionsTest
{
    @Test
    public void hiddenPrayerSwitchesTabAndTogglesInOneAction() throws Exception
    {
        Fixture fixture = new Fixture();
        int[] callbacks = {0};
        assertTrue(fixture.actions.protect(Prayer.PROTECT_FROM_MAGIC, true, () -> callbacks[0]++));
        assertTrue(fixture.scripts.isEmpty());
        fixture.actions.dispatch();
        assertEquals(List.of(915, 5), fixture.scripts, "the helper opens the Prayer tab itself");
        assertEquals(List.of(InterfaceID.Prayerbook.PRAYER13), fixture.widgets);
        assertEquals(1, callbacks[0]);
    }

    @Test
    public void observedPrayerChangeRejectsStaleQueuedToggle() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.show(InterfaceID.Prayerbook.PRAYER15);
        fixture.actions.protect(Prayer.PROTECT_FROM_MELEE, true, null);
        fixture.active = true;
        fixture.actions.dispatch();
        assertTrue(fixture.widgets.isEmpty());
    }

    @Test
    public void hiddenInventoryQueuesOnlyTheTabAndStopCancelsIt() throws Exception
    {
        Fixture fixture = new Fixture();
        int[] callbacks = {0};
        assertTrue(fixture.actions.inventory(385, "Eat", () -> callbacks[0]++));
        fixture.actions.reset();
        fixture.actions.dispatch();
        assertTrue(fixture.scripts.isEmpty());
        assertEquals(0, callbacks[0]);
        fixture.actions.inventory(385, "Eat", () -> callbacks[0]++);
        fixture.actions.dispatch();
        assertEquals(List.of(915, 3), fixture.scripts);
        assertEquals(0, callbacks[0]);
    }

    @Test
    public void queuedProtectionCountsOneStartedRequest() throws Exception
    {
        Fixture fixture = new Fixture();
        int[] callbacks = {0};
        assertTrue(fixture.actions.protect(Prayer.PROTECT_FROM_MAGIC, true, () -> callbacks[0]++));
        assertEquals(0, fixture.actions.getRequestsDispatched());
        fixture.actions.dispatch();
        fixture.actions.dispatch();
        assertEquals(1, fixture.actions.getRequestsDispatched());
        assertEquals(1, callbacks[0]);
    }

    @Test
    public void protectionQueuesWithoutReadinessAndRechecksAtDispatch() throws Exception
    {
        Fixture fixture = new Fixture();
        int[] callbacks = {0};
        fixture.ready = false;
        assertTrue(fixture.actions.protect(Prayer.PROTECT_FROM_MAGIC, true, () -> callbacks[0]++), "protection is queued without a readiness pre-check");
        fixture.actions.dispatch();
        assertTrue(fixture.widgets.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
        assertEquals(0, callbacks[0]);
    }

    @Test
    public void tabRequestIsPrecheckedAndRecheckedAtDispatch() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.ready = false;
        assertFalse(fixture.actions.inventory(385, "Eat", null));
        fixture.ready = true;
        assertTrue(fixture.actions.inventory(385, "Eat", null));
        fixture.ready = false;
        fixture.actions.dispatch();
        assertTrue(fixture.scripts.isEmpty());
        assertEquals(0, fixture.actions.getRequestsDispatched());
        fixture.ready = true;
        assertTrue(fixture.actions.inventory(385, "Eat", null));
        fixture.actions.dispatch();
        assertEquals(List.of(915, 3), fixture.scripts);
        assertEquals(1, fixture.actions.getRequestsDispatched());
    }

    private static final class Fixture
    {
        private final Map<Integer, Widget> visible = new HashMap<>();
        private final List<Integer> scripts = new ArrayList<>();
        private final List<Integer> widgets = new ArrayList<>();
        private boolean active;
        private boolean ready = true;
        private final BarrowsActions actions = new BarrowsActions()
        {
            @Override
            public boolean interactionReady()
            {
                return ready;
            }
        };

        private Fixture() throws Exception
        {
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class}, (proxy, method, arguments) ->
            {
                if (method.getName().equals("getWidget"))
                {
                    return visible.get((Integer) arguments[0]);
                }
                if (method.getName().equals("isPrayerActive"))
                {
                    return active;
                }
                if (method.getName().equals("getBoostedSkillLevel"))
                {
                    return 50;
                }
                if (method.getName().equals("runScript"))
                {
                    Object[] values = (Object[]) arguments[0];
                    for (Object value : values)
                    {
                        scripts.add((Integer) value);
                    }
                }
                if (method.getName().equals("menuAction"))
                {
                    widgets.add((Integer) arguments[1]);
                }
                return null;
            });
            Field field = BarrowsActions.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(actions, client);
            MicrobotTestSupport.attach(actions, client);
        }

        private void show(int id)
        {
            Widget widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class}, (proxy, method, arguments) ->
            {
                if (method.getName().equals("isHidden"))
                {
                    return false;
                }
                if (method.getName().equals("getId"))
                {
                    return id;
                }
                if (method.getName().equals("getIndex") || method.getName().equals("getItemId"))
                {
                    return -1;
                }
                return null;
            });
            visible.put(id, widget);
        }
    }
}
