/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.lang.reflect.Field;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drofirecape.profile.BaseProfileDro;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PortableDependenciesTest {
    @Test public void releasedClientWithoutLocalGuardStillStartsAndClosesTheGuard() {
        ClassLoader released=new ClassLoader(Microbot.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.equals("net.runelite.client.plugins.microbot.util.input.CanvasPointerLease"))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        FcCanvasGuard guard=FcCanvasGuard.acquire(released);
        assertFalse(guard.nativeGuard());guard.close();guard.close();
    }
    @Test public void localClientRetainsItsNativeGuardAndCloseIsIdempotent()throws Exception {
        Class<?> nativeType;
        try{nativeType=Class.forName("net.runelite.client.plugins.microbot.util.input.CanvasPointerLease",true,Microbot.class.getClassLoader());}
        catch(ClassNotFoundException released){
            FcCanvasGuard guard=FcCanvasGuard.acquire();assertFalse(guard.nativeGuard());guard.close();return;
        }
        boolean before=(Boolean)nativeType.getMethod("active").invoke(null);
        FcCanvasGuard guard=FcCanvasGuard.acquire();assertTrue(guard.nativeGuard());
        assertEquals(true,nativeType.getMethod("active").invoke(null));
        guard.close();guard.close();assertEquals(before,nativeType.getMethod("active").invoke(null));
    }
    @Test public void scriptOwnsTheEmbeddedOriginalProfileWithoutOffscreenParking()throws Exception {
        DroFirecapeScript script=new DroFirecapeScript();
        Field profile=DroFirecapeScript.class.getDeclaredField("baseProfile");profile.setAccessible(true);
        Object embedded=profile.get(script);assertEquals(BaseProfileDro.class,embedded.getClass());
        Field settings=BaseProfileDro.class.getDeclaredField("settings");settings.setAccessible(true);
        assertEquals(BaseProfileDro.AfkParkSide.NONE,((BaseProfileDro.Settings)settings.get(embedded)).getParkSide());
    }
}
