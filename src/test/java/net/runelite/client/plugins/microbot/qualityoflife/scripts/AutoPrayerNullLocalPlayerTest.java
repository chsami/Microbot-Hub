package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AutoPrayerNullLocalPlayerTest {
    @Test
    void antiPkHandlerSkipsTickWhenLocalPlayerIsNull() throws Exception {
        Client client = (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) -> null);
        QoLConfig config = (QoLConfig) Proxy.newProxyInstance(
            QoLConfig.class.getClassLoader(), new Class<?>[]{QoLConfig.class}, (proxy, method, args) -> {
                throw new AssertionError("config read after null local player: " + method.getName());
            });
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
}
