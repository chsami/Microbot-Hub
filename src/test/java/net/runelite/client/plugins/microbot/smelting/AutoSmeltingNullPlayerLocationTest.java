package net.runelite.client.plugins.microbot.smelting;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AutoSmeltingNullPlayerLocationTest {

    private static final WorldPoint START = new WorldPoint(3109, 3499, 0);
    private static final WorldPoint FAR_AWAY = new WorldPoint(3185, 3436, 0);

    private final AutoSmeltingConfig config = mock(AutoSmeltingConfig.class);
    private final AtomicReference<Runnable> loop = new AtomicReference<>();
    private boolean overwriteScriptSettings;
    private boolean disableTeleports;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Antiban> antiban;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<SessionFatigue> fatigue;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Bank> bank;
    private MockedStatic<Rs2Walker> walker;

    @BeforeEach
    void setUp() {
        overwriteScriptSettings = Rs2AntibanSettings.overwriteScriptSettings;
        disableTeleports = Rs2Walker.disableTeleports;
        Rs2AntibanSettings.overwriteScriptSettings = true;
        microbot = mockStatic(Microbot.class);
        antiban = mockStatic(Rs2Antiban.class);
        player = mockStatic(Rs2Player.class);
        fatigue = mockStatic(SessionFatigue.class);
        inventory = mockStatic(Rs2Inventory.class);
        bank = mockStatic(Rs2Bank.class);
        walker = mockStatic(Rs2Walker.class);
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        player.when(() -> Rs2Player.getRealSkillLevel(Skill.SMITHING)).thenReturn(50);
        when(config.SELECTED_BAR_TYPE()).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() throws Exception {
        walker.close();
        bank.close();
        inventory.close();
        fatigue.close();
        player.close();
        antiban.close();
        microbot.close();
        Rs2AntibanSettings.overwriteScriptSettings = overwriteScriptSettings;
        Rs2Walker.disableTeleports = disableTeleports;
        write(null, Script.class, "initialPlayerLocation", null);
    }

    @Test
    void tickWaitsWithoutCapturingTheStartTileWhileThePlayerLocationIsNull() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(null);
        readyToSmeltWithNoFurnaceInRange();

        Runnable tick = startLoop();
        tick.run();
        tick.run();

        assertNull(read(null, Script.class, "initialPlayerLocation"));
        microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
        bank.verify(Rs2Bank::openBank, never());
        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
    }

    @Test
    void startTileIsCapturedOnceThePlayerLocationLoads() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(null);
        Runnable tick = startLoop();
        tick.run();

        player.when(Rs2Player::getWorldLocation).thenReturn(START);
        tick.run();

        assertEquals(START, read(null, Script.class, "initialPlayerLocation"));
        microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
    }

    @Test
    void walksBackToTheStartTileWhenNoFurnaceIsInRange() throws Exception {
        player.when(Rs2Player::getWorldLocation).thenReturn(START);
        Runnable tick = startLoop();
        tick.run();

        readyToSmeltWithNoFurnaceInRange();
        player.when(Rs2Player::getWorldLocation).thenReturn(FAR_AWAY);
        tick.run();

        walker.verify(() -> Rs2Walker.walkTo(eq(START), eq(4)));
        microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
    }

    private void readyToSmeltWithNoFurnaceInRange() {
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
        when(cache.query()).thenReturn(query);
        when(query.withNameContains(anyString())).thenReturn(query);
        when(query.nearestOnClientThread(any(), anyInt())).thenReturn(null);
        inventory.when(() -> Rs2Inventory.hasItemAmount(anyString(), anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
    }

    private Runnable startLoop() throws Exception {
        AutoSmeltingScript script = new AutoSmeltingScript();
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            loop.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        ((ScheduledExecutorService) read(script, Script.class, "scheduledExecutorService")).shutdownNow();
        write(script, Script.class, "scheduledExecutorService", executor);
        script.run(config);
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
