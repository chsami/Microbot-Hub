/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import net.runelite.client.config.ConfigItem;

/** RuneLite proxies require ConfigItem; removed settings are fixed Java defaults. */
final class FcRuntimeConfig {
    private FcRuntimeConfig() {}

    static DroFirecapeConfig resolve(DroFirecapeConfig settings) {
        return (DroFirecapeConfig) Proxy.newProxyInstance(DroFirecapeConfig.class.getClassLoader(),
                new Class<?>[]{DroFirecapeConfig.class}, (proxy, method, args) -> {
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
                });
    }
}
