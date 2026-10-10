package net.runelite.client.plugins.microbot.drozulrah;

import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroZulrahLiveBreakTest {
    private static Field field(Object o,String name)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
    @Test public void liveEnableStartsTimerAndActiveDisableKeepsOriginalDeadline() throws Exception {
        BaseProfileDro.Settings settings=new BaseProfileDro.Settings().customBreaksEnabled(false).breakIntervals(10,10);
        BaseProfileDro profile=new BaseProfileDro(settings);
        Object manager=field(profile,"breakManager").get(profile);
        Method update=manager.getClass().getDeclaredMethod("update",boolean.class,Runnable.class);update.setAccessible(true);
        Runnable noMouse=()->fail("Unexpected mouse action");
        assertEquals(false,update.invoke(manager,false,noMouse));
        settings.customBreaksEnabled(true);
        assertEquals(false,update.invoke(manager,false,noMouse));
        assertTrue(field(manager,"nextBreakIn").getInt(manager)>=599);
        settings.customBreaksEnabled(false);
        field(manager,"breakActive").setBoolean(manager,true);
        field(manager,"logoutBreakActive").setBoolean(manager,true);
        field(manager,"breakTimeRemaining").setInt(manager,90);
        field(manager,"nextBreakCheck").setLong(manager,Long.MAX_VALUE);
        assertEquals(true,update.invoke(manager,false,noMouse));
        assertEquals(90,field(manager,"breakTimeRemaining").getInt(manager));
        assertFalse(field(manager,"loginPending").getBoolean(manager));
    }
}
