package net.runelite.client.plugins.microbot.frostyrc;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RcScriptWorldPointGuardTest {

    private final RcPlugin plugin = mock(RcPlugin.class);
    private final ClientThread clientThread = mock(ClientThread.class);
    private final AtomicReference<Runnable> loop = new AtomicReference<>();
    private boolean overwriteScriptSettings;
    private boolean enableAutoRunOn;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Global> global;
    private MockedStatic<Rs2Antiban> antiban;
    private MockedStatic<Rs2Camera> camera;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<SessionFatigue> fatigue;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Bank> bank;

    @BeforeEach
    void setUp() {
        overwriteScriptSettings = Rs2AntibanSettings.overwriteScriptSettings;
        enableAutoRunOn = Microbot.enableAutoRunOn;
        Rs2AntibanSettings.overwriteScriptSettings = true;
        microbot = mockStatic(Microbot.class);
        global = mockStatic(Global.class);
        antiban = mockStatic(Rs2Antiban.class);
        camera = mockStatic(Rs2Camera.class);
        player = mockStatic(Rs2Player.class);
        fatigue = mockStatic(SessionFatigue.class);
        inventory = mockStatic(Rs2Inventory.class);
        bank = mockStatic(Rs2Bank.class);
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        bank.close();
        inventory.close();
        fatigue.close();
        player.close();
        camera.close();
        antiban.close();
        global.close();
        microbot.close();
        Rs2AntibanSettings.overwriteScriptSettings = overwriteScriptSettings;
        Microbot.enableAutoRunOn = enableAutoRunOn;
    }

    @Test
    void loopSkipsTickUntilFirstGameTickSetsWorldPoint() throws Exception {
        when(plugin.getMyWorldPoint()).thenReturn(null);

        startLoop().run();

        verify(clientThread, never()).invoke(any(Runnable.class));
        inventory.verify(Rs2Inventory::anyPouchUnknown, never());
        microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
    }

    @Test
    void loopProceedsOnceWorldPointIsKnown() throws Exception {
        when(plugin.getMyWorldPoint()).thenReturn(new WorldPoint(3129, 3636, 0));

        startLoop().run();

        verify(clientThread, times(1)).invoke(any(Runnable.class));
        microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
    }

    private Runnable startLoop() throws Exception {
        RcScript script = new RcScript(plugin);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            loop.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        ((ScheduledExecutorService) read(script, Script.class, "scheduledExecutorService")).shutdownNow();
        write(script, Script.class, "scheduledExecutorService", executor);
        write(script, RcScript.class, "clientThread", clientThread);
        script.run();
        return loop.get();
    }

    private static Object read(Object owner, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void write(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
