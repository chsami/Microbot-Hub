package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AutoPrayerNullLocalPlayerTest {
    @Test
    void antiPkHandlerSkipsTickWhenLocalPlayerIsNull() throws Exception {
        Client client = proxy(Client.class, (p, method, args) -> null);
        QoLConfig config = proxy(QoLConfig.class, (p, method, args) -> {
            throw new AssertionError("config read after null local player: " + method.getName());
        });
        invokeAntiPkWithClient(client, config);
    }

    @Test
    void antiPkHandlerToleratesAttackerWithoutComposition() throws Exception {
        Player attacker = proxy(Player.class, AutoPrayerNullLocalPlayerTest::defaults);
        Player local = proxy(Player.class, (p, method, args) ->
            method.getName().equals("getInteracting") ? attacker : defaults(p, method, args));
        Client client = proxy(Client.class, (p, method, args) ->
            method.getName().equals("getLocalPlayer") ? local : defaults(p, method, args));
        QoLConfig config = proxy(QoLConfig.class, AutoPrayerNullLocalPlayerTest::defaults);
        invokeAntiPkWithClient(client, config);
    }

    @Test
    void gearChangeHandlerToleratesPlayerWithoutComposition() {
        Player player = proxy(Player.class, AutoPrayerNullLocalPlayerTest::defaults);
        QoLConfig config = proxy(QoLConfig.class, AutoPrayerNullLocalPlayerTest::defaults);
        assertDoesNotThrow(() -> new AutoPrayer().handleAggressivePrayerOnGearChange(player, config));
    }

    private static void invokeAntiPkWithClient(Client client, QoLConfig config) throws Exception {
        Field clientField = Microbot.class.getDeclaredField("client");
        clientField.setAccessible(true);
        Object previous = clientField.get(null);
        clientField.set(null, client);
        try {
            Method handler = AutoPrayer.class.getDeclaredMethod("handleAntiPkPrayers", QoLConfig.class);
            handler.setAccessible(true);
            AutoPrayer autoPrayer = new AutoPrayer();
            assertDoesNotThrow(() -> {
                try {
                    handler.invoke(autoPrayer, config);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
        } finally {
            clientField.set(null, previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaults(Object proxy, Method method, Object[] args) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return null;
    }
}
