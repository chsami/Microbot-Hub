/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.client.config.ConfigItem;

/** RuneLite proxies require ConfigItem; removed settings are fixed Java defaults. */
final class FcRuntimeConfig implements InvocationHandler {
    private final DroFirecapeConfig settings;
    private FcRuntimeConfig(DroFirecapeConfig settings) { this.settings=settings; }

    static DroFirecapeConfig resolve(DroFirecapeConfig settings) {
        if (Proxy.isProxyClass(settings.getClass()) && Proxy.getInvocationHandler(settings) instanceof FcRuntimeConfig) {
            return settings;
        }
        return (DroFirecapeConfig) Proxy.newProxyInstance(DroFirecapeConfig.class.getClassLoader(),
                new Class<?>[]{DroFirecapeConfig.class}, new FcRuntimeConfig(settings));
    }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return settings.toString();
                default: throw new IllegalStateException("Unexpected Object method: " + method);
            }
        }
        if (method.isDefault() && method.getAnnotation(ConfigItem.class) == null) {
            return MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
                    .unreflectSpecial(method, method.getDeclaringClass()).bindTo(proxy)
                    .invokeWithArguments(args == null ? new Object[0] : args);
        }
        try {
            return method.invoke(settings, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
