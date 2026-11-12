package net.runelite.client.plugins.microbot.chillRunecraft;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.SessionFatigue;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AutoRunecraftScriptBankTest {

    private final AtomicReference<Runnable> loop = new AtomicReference<>();
    private boolean actionCooldownActive;
    private boolean enableAutoRunOn;
    private String status;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Global> global;
    private MockedStatic<Rs2Antiban> antiban;
    private MockedStatic<Rs2Camera> camera;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<SessionFatigue> fatigue;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Equipment> equipment;
    private MockedStatic<Rs2Bank> bank;

    @BeforeEach
    void setUp() {
        actionCooldownActive = Rs2AntibanSettings.actionCooldownActive;
        enableAutoRunOn = Microbot.enableAutoRunOn;
        status = Microbot.status;
        Rs2AntibanSettings.actionCooldownActive = false;
        microbot = mockStatic(Microbot.class);
        global = mockStatic(Global.class);
        antiban = mockStatic(Rs2Antiban.class);
        camera = mockStatic(Rs2Camera.class);
        player = mockStatic(Rs2Player.class);
        fatigue = mockStatic(SessionFatigue.class);
        inventory = mockStatic(Rs2Inventory.class);
        equipment = mockStatic(Rs2Equipment.class);
        bank = mockStatic(Rs2Bank.class);
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
    }

    @AfterEach
    void tearDown() {
        bank.close();
        equipment.close();
        inventory.close();
        fatigue.close();
        player.close();
        camera.close();
        antiban.close();
        global.close();
        microbot.close();
        Rs2AntibanSettings.actionCooldownActive = actionCooldownActive;
        Microbot.enableAutoRunOn = enableAutoRunOn;
        Microbot.status = status;
    }

    @Test
    void freeToPlayAltarsBankAtAFixedFreeToPlayBank() {
        assertEquals(BankLocation.FALADOR_EAST, Altars.AIR_ALTAR.getBank());
        assertEquals(BankLocation.VARROCK_EAST, Altars.EARTH_ALTAR.getBank());
        assertEquals(BankLocation.LUMBRIDGE_TOP, Altars.WATER_ALTAR.getBank());
        assertEquals(BankLocation.AL_KHARID, Altars.FIRE_ALTAR.getBank());
        assertEquals(BankLocation.EDGEVILLE, Altars.BODY_ALTAR.getBank());
        assertNull(Altars.NATURE_ALTAR.getBank());
        for (Altars altar : Altars.values()) {
            if (altar.getBank() != null) {
                assertFalse(altar.getBank().isMembers(), altar.name());
            }
        }
    }

    @Test
    void bodyAltarBanksAtEdgevilleInsteadOfTheNearestBank() throws Exception {
        Runnable tick = startLoop(Altars.BODY_ALTAR);

        tick.run();
        tick.run();

        bank.verify(() -> Rs2Bank.walkToBankAndUseBank(BankLocation.EDGEVILLE), times(1));
        bank.verify(() -> Rs2Bank.walkToBankAndUseBank(), never());
        bank.verify(Rs2Bank::getNearestBank, never());
        microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
    }

    @Test
    void altarWithoutAFixedBankKeepsUsingTheNearestBank() throws Exception {
        Runnable tick = startLoop(Altars.NATURE_ALTAR);

        tick.run();
        tick.run();

        bank.verify(() -> Rs2Bank.walkToBankAndUseBank(), times(1));
        bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any(BankLocation.class)), never());
        microbot.verify(() -> Microbot.logStackTrace(any(), any()), never());
    }

    private Runnable startLoop(Altars altar) throws Exception {
        AutoRunecraftConfig config = mock(AutoRunecraftConfig.class);
        when(config.ALTAR()).thenReturn(altar);
        AutoRunecraftScript script = new AutoRunecraftScript();
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            loop.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        Field field = Script.class.getDeclaredField("scheduledExecutorService");
        field.setAccessible(true);
        ((ScheduledExecutorService) field.get(script)).shutdownNow();
        field.set(script, executor);
        script.run(config);
        return loop.get();
    }
}
