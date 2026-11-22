package net.runelite.client.plugins.microbot.autofarmer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AutoFarmerFailRunTest {
    @TempDir
    Path dir;

    @Test
    public void failRunIgnoresClearedProfileWhileLatchIsStillSet() throws Exception {
        AutoFarmerScript script = new AutoFarmerScript(mock(AutoFarmerPlugin.class), mock(AutoFarmerConfig.class));
        Path file = dir.resolve("failure.properties");
        FailureLatch latch = new FailureLatch(file);
        set(script, "failureStop", latch);
        set(script, "failureProfile", null);

        Method failRun = AutoFarmerScript.class.getDeclaredMethod("failRun", String.class);
        failRun.setAccessible(true);
        assertDoesNotThrow(() -> failRun.invoke(script, "Custom inventory/equipment setup failed"));

        assertFalse(latch.active());
        assertFalse(Files.exists(file));
        Field stopping = AutoFarmerScript.class.getDeclaredField("stopping");
        stopping.setAccessible(true);
        assertFalse(stopping.getBoolean(script));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = AutoFarmerScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
