package net.runelite.client.plugins.microbot.autofishing;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import static org.mockito.Mockito.when;

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
    void returnsToSpotItWasFishingAfterBankingFarAway() {
        WorldPoint barbarianVillage = new WorldPoint(3105, 3430, 0);
        WorldPoint isleOfSoulsBank = new WorldPoint(2212, 2859, 0);
        assertFalse(AutoFishingScript.isWithin(Fish.TROUT_AND_SALMON.getClosestLocation(isleOfSoulsBank), barbarianVillage, 50));

        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Rs2NpcModel visibleSpot = mock(Rs2NpcModel.class);
        AtomicReference<Rs2NpcModel> nearestSpot = new AtomicReference<>(visibleSpot);
        AutoFishingScript script = new AutoFishingScript() {
            {
                scheduledExecutorService = executor;
            }

            @Override
            Rs2NpcModel findNearestFishingSpot() {
                return nearestSpot.get();
            }
        };
        AutoFishingConfig config = mock(AutoFishingConfig.class);
        when(config.fishToCatch()).thenReturn(Fish.TROUT_AND_SALMON);
        when(config.harpoonSpec()).thenReturn(HarpoonType.NONE);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(true);

            script.run(config);
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));

            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(barbarianVillage);
            tick.getValue().run();
            assertEquals(AutoFishingState.FISHING, script.getCurrentState());

            nearestSpot.set(null);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(isleOfSoulsBank);
            tick.getValue().run();
            assertEquals(AutoFishingState.TRAVELING, script.getCurrentState());
            walker.verify(() -> Rs2Walker.walkTo(barbarianVillage));
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
