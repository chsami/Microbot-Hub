/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FcRuntimeConfigTest {
    private DroFirecapeConfig realProxy(ConfigManager manager) throws Exception {
        Constructor<?> ctor=Class.forName("net.runelite.client.config.ConfigInvocationHandler")
                .getDeclaredConstructor(ConfigManager.class);
        ctor.setAccessible(true);
        return (DroFirecapeConfig)Proxy.newProxyInstance(DroFirecapeConfig.class.getClassLoader(),
                new Class<?>[]{DroFirecapeConfig.class},(InvocationHandler)ctor.newInstance(manager));
    }

    @Test void internalDefaultsWorkWithRealRuneLiteProxyAndIgnoreRemovedSavedSettings() throws Exception {
        ConfigManager manager=mock(ConfigManager.class);
        when(manager.getConfiguration(eq(DroFirecapeConfig.GROUP),anyString())).thenReturn("true");
        DroFirecapeConfig raw=realProxy(manager);
        assertThrows(NullPointerException.class,raw::observeOnly);
        DroFirecapeConfig resolved=FcRuntimeConfig.resolve(raw);
        assertSame(resolved,FcRuntimeConfig.resolve(resolved),"Repeat runs must not stack config adapters");
        DroFirecapeConfig defaults=new DroFirecapeConfig(){};
        for(Method method:DroFirecapeConfig.class.getDeclaredMethods()) {
            if(method.isDefault()&&method.getAnnotation(ConfigItem.class)==null&&!method.getName().equals("diagnostics")) {
                assertEquals(method.invoke(defaults),method.invoke(resolved),method.getName());
                verify(manager,never()).getConfiguration(DroFirecapeConfig.GROUP,method.getName());
            }
        }
    }

    @Test void visibleSettingsAndDiagnosticAliasRemainLive() throws Exception {
        ConfigManager manager=mock(ConfigManager.class);
        when(manager.getConfiguration(DroFirecapeConfig.GROUP,"recordRun")).thenReturn("false");
        Method conversion=ConfigManager.class.getDeclaredMethod("stringToObject",String.class,java.lang.reflect.Type.class);
        conversion.setAccessible(true);
        when(conversion.invoke(manager,"false",boolean.class)).thenReturn(false);
        DroFirecapeConfig resolved=FcRuntimeConfig.resolve(realProxy(manager));
        assertFalse(resolved.recordRun());
        assertFalse(resolved.diagnostics());
        verify(manager,atLeastOnce()).getConfiguration(DroFirecapeConfig.GROUP,"recordRun");
        assertFalse(resolved.pureMode());
        assertFalse(resolved.nativeTickPrayers());
    }

    @Test void regularAndOptionalControllerDefaultsAreUsableWithRealProxy() throws Exception {
        DroFirecapeConfig resolved=FcRuntimeConfig.resolve(realProxy(mock(ConfigManager.class)));
        for(DroFirecapeScript script:new DroFirecapeScript[]{new DroFirecapeScript(),new OptionalFirecapeScript()}) {
            script.config=resolved;
            assertFalse(script.observeOnly());
            assertFalse(script.strictZeroExposure());
            assertEquals(0,script.weaponRangeSetting());
            assertTrue(script.rangingPotion());
            assertTrue(script.demonstrationLures());
            assertTrue(script.blowpipeSpecial());
            assertTrue(script.useThralls());
            assertFalse(script.exitOnDamage());
            assertTrue(script.traceEnabled());
        }
    }

    @Test void adapterHasStableIdentityAndCanBeReusedForRepeatedStarts() throws Exception {
        DroFirecapeConfig raw=realProxy(mock(ConfigManager.class));
        DroFirecapeConfig resolved=FcRuntimeConfig.resolve(raw);
        assertEquals(resolved,resolved);
        assertNotEquals(resolved,raw);
        assertNotEquals(resolved,null);
        assertEquals(System.identityHashCode(resolved),resolved.hashCode());
        for(int i=0;i<100;i++)resolved=FcRuntimeConfig.resolve(resolved);
        assertFalse(resolved.observeOnly());
        assertEquals("DroFirecapeConfig",resolved.toString());
    }
}
