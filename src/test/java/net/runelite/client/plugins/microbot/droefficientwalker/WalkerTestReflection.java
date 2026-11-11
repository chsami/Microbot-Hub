package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small reflection helpers shared by the walker tests. */
final class WalkerTestReflection
{
    static final String SOURCE_PACKAGE = "net.runelite.client.plugins.microbot.droefficientwalker.engine";

    static Field declaredField(Class<?> owner, String name) throws NoSuchFieldException
    {
        return owner.getDeclaredField(name);
    }

    static Method declaredMethod(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException
    {
        return owner.getDeclaredMethod(name, parameters);
    }

    private WalkerTestReflection() { }
}
