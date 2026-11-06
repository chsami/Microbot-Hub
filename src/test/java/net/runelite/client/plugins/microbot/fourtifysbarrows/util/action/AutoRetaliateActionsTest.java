package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AutoRetaliateActionsTest
{
    @Test
    public void hiddenControlQueuesCombatTabBeforeGuardedSdkAttempt() throws Exception
    {
        Fixture fixture = new Fixture();
        assertFalse(fixture.actions.disableAutoRetaliate(() -> fixture.callbacks++));
        assertEquals(0, fixture.tabs);
        fixture.actions.dispatch();
        assertEquals(1, fixture.tabs);
        assertEquals(0, fixture.helpers);
        fixture.show();
        assertTrue(fixture.actions.disableAutoRetaliate(() -> fixture.callbacks++));
        assertEquals(0, fixture.helpers);
        fixture.actions.dispatch();
        assertEquals(1, fixture.helpers);
        assertEquals(1, fixture.callbacks);
        assertFalse(fixture.actions.autoRetaliateDisabled());
    }

    @Test
    public void observedOffRejectsQueuedToggleAndAlreadyOffIsIdempotent() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.show();
        fixture.actions.disableAutoRetaliate(null);
        fixture.varp = 1;
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
        assertTrue(fixture.actions.disableAutoRetaliate(null));
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
    }

    @Test
    public void replacedOrHiddenWidgetCannotDispatch() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.show();
        fixture.actions.disableAutoRetaliate(null);
        fixture.show();
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
        fixture.actions.disableAutoRetaliate(null);
        fixture.hidden = true;
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
    }

    @Test
    public void wrongActionOrOffCanvasBoundsCannotDispatch() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.show();
        fixture.option = "Choose spell";
        fixture.actions.disableAutoRetaliate(null);
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
        fixture.option = "Auto retaliate";
        fixture.bounds = new Rectangle(-20, 20, 150, 47);
        fixture.actions.disableAutoRetaliate(null);
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
    }

    @Test
    public void bankOpeningOrStopInvalidatesPendingAttempt() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.show();
        fixture.actions.disableAutoRetaliate(null);
        fixture.bank = true;
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
        assertFalse(fixture.actions.disableAutoRetaliate(null));
        fixture.bank = false;
        fixture.actions.disableAutoRetaliate(null);
        fixture.actions.reset();
        fixture.actions.dispatch();
        assertEquals(0, fixture.helpers);
        assertEquals(0, fixture.callbacks);
    }

    @Test
    public void interruptedAdapterDoesNotEnterSdk()
    {
        Thread.currentThread().interrupt();
        try
        {
            assertFalse(new MicrobotApi().disableAutoRetaliate());
        }
        finally
        {
            Thread.interrupted();
        }
    }

    private static final class Fixture
    {
        private int varp;
        private int helpers;
        private int callbacks;
        private int tabs;
        private boolean hidden;
        private boolean bank;
        private String option = "Auto retaliate";
        private Rectangle bounds = new Rectangle(550, 300, 150, 47);
        private Widget widget;
        private final BarrowsActions actions = new BarrowsActions()
        {
            @Override
            public boolean interactionReady()
            {
                return true;
            }

            @Override
            public boolean bankingOpen()
            {
                return bank;
            }
        };

        private Fixture() throws Exception
        {
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class}, (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "getVarpValue": return varp;
                    case "getWidget": return (Integer) args[0] == InterfaceID.CombatInterface.RETALIATE ? widget : null;
                    case "getCanvasWidth": return 765;
                    case "getCanvasHeight": return 503;
                    default: return null;
                }
            });
            MicrobotTestSupport.attach(actions, client);
            Field api = BarrowsActions.class.getDeclaredField("api");
            api.setAccessible(true);
            api.set(actions, new MicrobotApi()
            {
                @Override
                public boolean disableAutoRetaliate()
                {
                    helpers++;
                    return true;
                }

                @Override
                public boolean tab(InterfaceTab tab)
                {
                    assertEquals(InterfaceTab.COMBAT, tab);
                    tabs++;
                    return true;
                }
            });
        }

        private void show()
        {
            widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class}, (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "getId": return InterfaceID.CombatInterface.RETALIATE;
                    case "isHidden": return hidden;
                    case "getActions": return new String[] {option};
                    case "getBounds": return bounds;
                    default: return null;
                }
            });
        }
    }
}
