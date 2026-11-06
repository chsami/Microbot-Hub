package net.runelite.client.plugins.microbot.fourtifysagility.tasks;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BrimhavenRecoveryTest
{
    @Test
    public void fallingDiscardsStaleMovementAndPendingTagButKeepsConfirmedTag() throws Exception
    {
        WorldPoint[] position = {new WorldPoint(2783, 9568, 0)};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> method.getName().equals("getWorldLocation") ? position[0] : null);
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (proxy, method, args) -> method.getName().equals("getLocalPlayer") ? player : null);
        BrimhavenTask task = new BrimhavenTask(new FourtifysAgilityPlugin(), client, new AgilityConfig() {}, new AgilityActions());
        WorldPoint tagged = new WorldPoint(2783, 9568, 3);
        set(task, "taggedTarget", tagged);
        set(task, "pendingTag", tagged);
        set(task, "lastAttemptFrom", tagged);
        set(task, "lastAttemptTo", tagged);
        set(task, "attempts", 3);

        task.observeHint();
        assertEquals(0, get(task, "attempts"));
        assertNull(get(task, "pendingTag"));
        assertNull(get(task, "lastAttemptFrom"));
        assertNull(get(task, "lastAttemptTo"));
        assertSame(tagged, get(task, "taggedTarget"));
        assertEquals(true, get(task, "recovering"));
    }

    private static void set(Object instance, String name, Object value) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object get(Object instance, String name) throws Exception
    {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
}
