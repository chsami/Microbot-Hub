package net.runelite.client.plugins.microbot.fourtifysbarrows.util.navigation;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShadesTeleportTest
{
    @Test
    public void questMustBeCompletedBeforeMinigameTeleport()
    {
        assertEquals("Complete Shades of Mort'ton for the minigame teleport",
            ShadesTeleport.unavailable(client(1, 0), 1_000));
        assertEquals("Complete Shades of Mort'ton for the minigame teleport",
            ShadesTeleport.unavailable(client(0, 0), 1_000));
        assertNull(ShadesTeleport.unavailable(client(2, 0), 1_000));
    }

    @Test
    public void sharedCooldownUsesMinutesAndExpiresAtTwentyMinutes()
    {
        assertEquals("Shades of Mort'ton teleport is on cooldown for 20 more minutes",
            ShadesTeleport.unavailable(client(2, 1_000), 1_000));
        assertEquals("Shades of Mort'ton teleport is on cooldown for 1 more minute",
            ShadesTeleport.unavailable(client(2, 1_000), 1_019));
        assertEquals(12, ShadesTeleport.cooldownMinutes(client(2, 1_000), 1_008));
        assertEquals(0, ShadesTeleport.cooldownMinutes(client(2, 1_000), 1_020));
        assertNull(ShadesTeleport.unavailable(client(2, 1_000), 1_020));
        assertNull(ShadesTeleport.unavailable(client(2, 1_000), 1_021));
        assertEquals("Shades of Mort'ton teleport is on cooldown for 21 more minutes",
            ShadesTeleport.unavailable(client(2, 1_001), 1_000));
    }

    @Test
    public void loggedOutClientDoesNotRunQuestScript()
    {
        assertEquals("Waiting for login", ShadesTeleport.unavailable(null));
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (proxy, method, arguments) ->
            {
                assertEquals("getGameState", method.getName());
                return GameState.LOGIN_SCREEN;
            });
        assertEquals("Waiting for login", ShadesTeleport.unavailable(client));
    }

    @Test
    public void selectionRequiresActualVisibleShadesButtonAndDefaultSelectAction()
    {
        int shades = (951 << 16) | 10;
        assertTrue(ShadesTeleport.usable(widget(shades, false, "Select", new Rectangle(1, 1, 100, 50)), shades, "Select"));
        assertFalse(ShadesTeleport.usable(widget(shades + 1, false, "Select", new Rectangle(1, 1, 100, 50)), shades, "Select"));
        assertFalse(ShadesTeleport.usable(widget(shades, true, "Select", new Rectangle(1, 1, 100, 50)), shades, "Select"));
        assertFalse(ShadesTeleport.usable(widget(shades, false, "Examine", new Rectangle(1, 1, 100, 50)), shades, "Select"));
        assertFalse(ShadesTeleport.usable(widget(shades, false, "Select", new Rectangle()), shades, "Select"));
        assertFalse(ShadesTeleport.usable(null, shades, "Select"));
    }

    @Test
    public void spellRequiresVisibleDefaultCastActionInEverySpellbook()
    {
        for (int child = 7; child <= 10; child++)
        {
            int spell = (218 << 16) | child;
            assertTrue(ShadesTeleport.usable(widget(spell, false, "Cast", new Rectangle(1, 1, 24, 24)), spell, "Cast"));
            assertFalse(ShadesTeleport.usable(widget(spell, false, "Select", new Rectangle(1, 1, 24, 24)), spell, "Cast"));
        }
    }

    @Test
    public void interruptedWorkerNeverTouchesSdkOrClearsCancellation()
    {
        Thread.currentThread().interrupt();
        try
        {
            assertFalse(ShadesTeleport.teleport());
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally
        {
            Thread.interrupted();
        }
    }

    @Test
    public void clippedOrOffCanvasDestinationMustBeScrolledBeforeClick()
    {
        Rectangle viewport = new Rectangle(20, 40, 482, 270);
        assertFalse(ShadesTeleport.visible(new Rectangle(260, 624, 226, 72), viewport));
        assertFalse(ShadesTeleport.visible(new Rectangle(260, 270, 226, 72), viewport));
        assertFalse(ShadesTeleport.visible(new Rectangle(10, 50, 226, 72), viewport));
        assertTrue(ShadesTeleport.visible(new Rectangle(260, 238, 226, 72), viewport));
        assertFalse(ShadesTeleport.visible(new Rectangle(), viewport));
        assertFalse(ShadesTeleport.visible(new Rectangle(260, 238, 226, 72), new Rectangle()));
    }

    private static Client client(int questState, int lastMinute)
    {
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (proxy, method, arguments) ->
            {
                switch (method.getName())
                {
                    case "getGameState": return GameState.LOGGED_IN;
                    case "runScript":
                        Object[] script = (Object[]) arguments[0];
                        assertEquals(4029, script[0]);
                        assertEquals(Quest.SHADES_OF_MORTTON.getId(), script[1]);
                        return null;
                    case "getIntStack": return new int[] {questState};
                    case "getVarpValue":
                        assertEquals(888, arguments[0]);
                        return lastMinute;
                    default: throw new AssertionError(method.getName());
                }
            });
    }

    private static Widget widget(int id, boolean hidden, String action, Rectangle bounds)
    {
        return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
            (proxy, method, arguments) ->
            {
                switch (method.getName())
                {
                    case "getId": return id;
                    case "isHidden": return hidden;
                    case "getBounds": return bounds;
                    case "getActions": return new String[] {action};
                    default: throw new AssertionError(method.getName());
                }
            });
    }
}
