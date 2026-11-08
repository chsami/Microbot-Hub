package net.runelite.client.plugins.microbot.fourtifystormenteddemons;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;

public final class NativeConfigTestSupport
{
    private NativeConfigTestSupport()
    {
    }

    public static ConfigManager create(File file) throws Exception
    {
        ScheduledExecutorService scheduler = (ScheduledExecutorService) Proxy.newProxyInstance(
            ScheduledExecutorService.class.getClassLoader(), new Class<?>[] {ScheduledExecutorService.class},
            (proxy, method, arguments) -> null);
        Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] arguments = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int i = 0; i < types.length; i++)
        {
            if (types[i] == ScheduledExecutorService.class)
            {
                arguments[i] = scheduler;
            }
            else if (types[i] == EventBus.class)
            {
                arguments[i] = new EventBus();
            }
            else if (types[i] == Gson.class)
            {
                arguments[i] = new Gson();
            }
        }
        ConfigManager manager = (ConfigManager) constructor.newInstance(arguments);
        Class<?> dataClass = Class.forName("net.runelite.client.config.ConfigData");
        Constructor<?> dataConstructor = dataClass.getDeclaredConstructor(File.class);
        dataConstructor.setAccessible(true);
        Object data = dataConstructor.newInstance(file);
        Field configProfile = ConfigManager.class.getDeclaredField("configProfile");
        configProfile.setAccessible(true);
        configProfile.set(manager, data);
        return manager;
    }
}
