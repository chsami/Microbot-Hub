package net.runelite.client.plugins.microbot.fourtifysbarrows.util.navigation;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public final class ShadesTeleport
{
    private static final int SHADES_BUTTON = (951 << 16) | 10;
    private static final int[] MINIGAME_SPELLS = {
        (218 << 16) | 7,
        (218 << 16) | 8,
        (218 << 16) | 9,
        (218 << 16) | 10
    };
    private static final int LAST_TELEPORT_VARP = 888;
    private static final int COOLDOWN_MINUTES = 20;

    private ShadesTeleport()
    {
    }

    /** Read on the client thread before queuing a teleport. */
    public static String unavailable(Client client)
    {
        return unavailable(client, System.currentTimeMillis() / 60_000L);
    }

    static String unavailable(Client client, long nowMinutes)
    {
        if (client == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return "Waiting for login";
        }
        if (Quest.SHADES_OF_MORTTON.getState(client) != QuestState.FINISHED)
        {
            return "Complete Shades of Mort'ton for the minigame teleport";
        }
        int lastMinute = client.getVarpValue(LAST_TELEPORT_VARP);
        long remaining = lastMinute <= 0 ? 0 : lastMinute + (long) COOLDOWN_MINUTES - nowMinutes;
        return remaining > 0 ? "Minigame teleport cooldown: " + remaining + " minute" + (remaining == 1 ? "" : "s") : null;
    }

    /**
     * Worker only: opens Minigame Teleport in the current spellbook and selects
     * Shades in the modern Minigames interface. True reports a click request;
     * the caller must observe arrival and leave the home teleport cast alone.
     */
    public static boolean teleport()
    {
        if (Thread.currentThread().isInterrupted() || Microbot.getClientThread() == null
            || Microbot.getClientThread().isClientThread() || !sessionReady())
        {
            return false;
        }
        Widget destination = readWidget(SHADES_BUTTON, "Select");
        if (destination == null)
        {
            if (!Rs2Tab.switchTo(InterfaceTab.MAGIC) || Thread.currentThread().isInterrupted())
            {
                return false;
            }
            Widget spell = awaitWidget(MINIGAME_SPELLS, "Cast", 2_000);
            if (spell == null || !click(spell, "Cast"))
            {
                return false;
            }
            destination = awaitWidget(new int[] {SHADES_BUTTON}, "Select", 5_000);
        }
        return destination != null && scrollToDestination() && click(destination, "Select");
    }

    private static boolean scrollToDestination()
    {
        for (int attempt = 0; attempt < 32 && sessionReady(); attempt++)
        {
            Rectangle[] bounds = Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                Widget target = Rs2Widget.getWidget(SHADES_BUTTON);
                Widget list = Rs2Widget.getWidget(951, 4);
                Widget content = Rs2Widget.getWidget(951, 3);
                if (!usable(target, SHADES_BUTTON, "Select") || list == null || content == null
                    || list.isHidden() || content.isHidden())
                {
                    return null;
                }
                Client client = Microbot.getClient();
                Rectangle viewport = list.getBounds().intersection(content.getBounds())
                    .intersection(new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight()));
                return new Rectangle[] {target.getBounds(), viewport};
            }).orElse(null);
            if (bounds == null || bounds[1].isEmpty())
            {
                return false;
            }
            if (visible(bounds[0], bounds[1]))
            {
                return true;
            }
            if (Thread.currentThread().isInterrupted())
            {
                return false;
            }
            Point point = new Point((int) bounds[1].getCenterX(), (int) bounds[1].getCenterY());
            if (bounds[0].y < bounds[1].y)
            {
                Microbot.getMouse().scrollUp(point);
            }
            else
            {
                Microbot.getMouse().scrollDown(point);
            }
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static Widget awaitWidget(int[] ids, String option, long timeoutMillis)
    {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (!Thread.currentThread().isInterrupted() && sessionReady())
        {
            for (int id : ids)
            {
                Widget widget = readWidget(id, option);
                if (widget != null)
                {
                    return widget;
                }
            }
            if (System.nanoTime() >= deadline)
            {
                return null;
            }
            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static boolean sessionReady()
    {
        return !Thread.currentThread().isInterrupted()
            && Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                Client client = Microbot.getClient();
                return client != null && client.getGameState() == GameState.LOGGED_IN
                    && !client.isWidgetSelected();
            }).orElse(false);
    }

    private static Widget readWidget(int id, String option)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = Rs2Widget.getWidget(id);
            return usable(widget, id, option) ? widget : null;
        }).orElse(null);
    }

    private static boolean click(Widget widget, String option)
    {
        if (!sessionReady())
        {
            return false;
        }
        boolean current = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Client client = Microbot.getClient();
            Rectangle viewport = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
            Widget parent = widget.getParent();
            for (int depth = 0; parent != null && depth < 16; depth++, parent = parent.getParent())
            {
                if (parent.isHidden())
                {
                    return false;
                }
                viewport = viewport.intersection(parent.getBounds());
            }
            return Rs2Widget.getWidget(widget.getId()) == widget && usable(widget, widget.getId(), option)
                && visible(widget.getBounds(), viewport);
        }).orElse(false);
        return current && !Thread.currentThread().isInterrupted() && Rs2Widget.clickWidget(widget);
    }

    static boolean visible(Rectangle target, Rectangle viewport)
    {
        return target != null && viewport != null && !target.isEmpty() && !viewport.isEmpty()
            && viewport.contains(target);
    }

    static boolean usable(Widget widget, int id, String option)
    {
        if (widget == null || widget.getId() != id || widget.isHidden())
        {
            return false;
        }
        Rectangle bounds = widget.getBounds();
        String[] actions = widget.getActions();
        return bounds != null && bounds.width > 0 && bounds.height > 0
            && actions != null && actions.length > 0 && option.equals(actions[0]);
    }
}
