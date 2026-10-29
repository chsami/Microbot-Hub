package net.runelite.client.plugins.microbot.autofishing;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AutoFishingScriptTest {
    @Test
    void karambwanjiBaitIsRequiredItemNotCatch() {
        assertTrue(AutoFishingScript.isRequiredItem(Fish.KARAMBWAN, "Raw karambwanji"));
        assertFalse(AutoFishingScript.isRequiredItem(Fish.KARAMBWAN, "Raw karambwan"));
    }

    @Test
    void caughtFishAreNeverRequiredItems() {
        for (Fish fish : Fish.values()) {
            for (String item : fish.getItemNames()) {
                assertFalse(AutoFishingScript.isRequiredItem(fish, item), fish + " " + item);
            }
        }
    }

    @Test
    void tickWithoutLocalPlayerLocationSkipsWithoutError() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AutoFishingScript script = new AutoFishingScript() {{
            scheduledExecutorService = executor;
        }};

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(null);

            script.run(mock(AutoFishingConfig.class));
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            rs2Player.verify(Rs2Player::getWorldLocation);
            inventory.verifyNoInteractions();
            microbot.verify(Microbot::getRs2NpcCache, never());
            assertEquals(AutoFishingState.IDLE, script.getCurrentState());
        }
    }

    @Test
    void isWithinIsFalseWhenEitherLocationIsMissing() {
        WorldPoint here = new WorldPoint(3200, 3200, 0);
        assertFalse(AutoFishingScript.isWithin(null, here, 5));
        assertFalse(AutoFishingScript.isWithin(here, null, 5));
        assertTrue(AutoFishingScript.isWithin(here, new WorldPoint(3205, 3200, 0), 5));
        assertFalse(AutoFishingScript.isWithin(here, new WorldPoint(3206, 3200, 0), 5));
    }

    @Test
    void consumableGearIsWithdrawnAsWholeStackWithoutWielding() {
        for (String consumable : List.of("Feather", "Fishing bait", "Dark fishing bait", "Sandworms", "Raw karambwanji")) {
            try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
                 MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
                 MockedStatic<Global> global = mockStatic(Global.class)) {
                bank.when(() -> Rs2Bank.hasItem(anyString())).thenReturn(true);
                global.when(() -> Global.sleepUntil(any())).thenReturn(true);

                new AutoFishingScript().withdrawAndEquipItem(consumable);

                bank.verify(() -> Rs2Bank.withdrawAll(consumable));
                bank.verify(() -> Rs2Bank.withdrawOne(anyString()), never());
                inventory.verifyNoInteractions();
            }
        }
    }
}
