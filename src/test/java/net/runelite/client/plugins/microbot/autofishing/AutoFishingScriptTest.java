package net.runelite.client.plugins.microbot.autofishing;

import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
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
    void tickDismissesLevelUpScreenInsteadOfTryingToFish() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AutoFishingScript script = new AutoFishingScript() {{
            scheduledExecutorService = executor;
        }};

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            widget.when(() -> Rs2Widget.isWidgetVisible(InterfaceID.LevelupDisplay.CONTINUE)).thenReturn(true);

            script.run(mock(AutoFishingConfig.class));
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            widget.verify(() -> Rs2Widget.clickWidget(InterfaceID.LevelupDisplay.CONTINUE));
            rs2Player.verify(Rs2Player::getWorldLocation, never());
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
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
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
    void sandwormsAreAcceptedWhereFishingBaitIsRequired() {
        assertArrayEquals(new String[]{"Fishing bait", "Sandworms"}, AutoFishingScript.acceptedConsumables("Fishing bait"));
        assertArrayEquals(new String[]{"Feather"}, AutoFishingScript.acceptedConsumables("Feather"));
        assertArrayEquals(new String[]{"Dark fishing bait"}, AutoFishingScript.acceptedConsumables("Dark fishing bait"));
    }

    @Test
    void tackleBoxOnlyStandsInForBaitItHolds() {
        assertTrue(AutoFishingScript.isTackleBoxBait("Fishing bait"));
        assertTrue(AutoFishingScript.isTackleBoxBait("Sandworms"));
        assertFalse(AutoFishingScript.isTackleBoxBait("Feather"));
        assertFalse(AutoFishingScript.isTackleBoxBait("Raw karambwanji"));
        assertFalse(AutoFishingScript.isTackleBoxBait("Dark fishing bait"));
    }

    @Test
    void outOfBaitGameMessagesAreRecognised() {
        assertTrue(AutoFishingScript.isOutOfBaitMessage("You don't have any fishing bait left."));
        assertTrue(AutoFishingScript.isOutOfBaitMessage("You don't have any feathers left."));
        assertTrue(AutoFishingScript.isOutOfBaitMessage("You have no bait."));
        assertFalse(AutoFishingScript.isOutOfBaitMessage("You catch a sacred eel."));
        assertFalse(AutoFishingScript.isOutOfBaitMessage("You cast out your line..."));
        assertFalse(AutoFishingScript.isOutOfBaitMessage(null));
    }

    @Test
    void stopReasonNamesAreJoinedForPlayers() {
        assertEquals("Fishing bait", AutoFishingScript.joinNames(List.of("Fishing bait"), "or"));
        assertEquals("Fishing bait, Sandworms or Tackle box", AutoFishingScript.joinNames(List.of("Fishing bait", "Sandworms", "Tackle box"), "or"));
        assertEquals("Fishing rod and Knife", AutoFishingScript.joinNames(List.of("Fishing rod", "Knife"), "and"));
    }

    @Test
    void sacredEelKeepsFishingWithBaitInTackleBox() {
        runFishingTick(Fish.SACRED_EEL, inventory -> inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true), null, null);
    }

    @Test
    void sacredEelKeepsFishingWithSandwormsInInventory() {
        runFishingTick(Fish.SACRED_EEL, inventory -> inventory.when(() -> Rs2Inventory.hasItem("Fishing bait", "Sandworms")).thenReturn(true), null, null);
    }

    @Test
    void sacredEelStillLogsOutWithoutAnyBait() {
        runFishingTick(Fish.SACRED_EEL, inventory -> { }, null,
                "AutoFishing stopped: no Fishing bait, Sandworms or Tackle box in your inventory for Sacred eel. Add Fishing bait and start again.");
    }

    @Test
    void sacredEelStopsWhenGameSaysTackleBoxIsOutOfBait() {
        runFishingTick(Fish.SACRED_EEL, inventory -> inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true),
                "You don't have any fishing bait left.",
                "AutoFishing stopped: out of Fishing bait (your Tackle box is empty too). Refill the bait or put it in your inventory, then start again.");
    }

    @Test
    void tackleBoxDoesNotStandInForFeathers() {
        runFishingTick(Fish.TROUT_AND_SALMON, inventory -> inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true), null,
                "AutoFishing stopped: no Feathers in your inventory for Trout + Salmon. Add Feathers and start again.");
    }

    @Test
    void emptyTackleBoxWithBankingSuggestsWithdrawingBait() {
        runFishingTick(Fish.SACRED_EEL, true, inventory -> inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true),
                "You don't have any fishing bait left.",
                "AutoFishing stopped: out of Fishing bait (your Tackle box is empty too). Refill the bait, withdraw Fishing bait from your bank, or put it in your inventory, then start again.");
    }

    @Test
    void emptyTackleBoxStopsAfterCastsWithoutXpEvenWithoutChatMessage() {
        assertEquals(AutoFishingScript.MAX_TACKLE_BOX_CASTS_WITHOUT_XP, castsUntilStop(false, false, 10));
    }

    @Test
    void tackleBoxCastsThatGiveXpKeepFishing() {
        assertEquals(-1, castsUntilStop(true, false, 10));
    }

    @Test
    void castsWithoutXpAreNotCountedWhenLooseBaitIsInInventory() {
        assertEquals(-1, castsUntilStop(false, true, 10));
    }

    @Test
    void bankStopReasonListsAcceptedBaitAndWhereToPutIt() {
        assertEquals("AutoFishing stopped: could not find Fishing rod in your bank. Put it in your bank or inventory (buy one if you have none), then start again.",
                AutoFishingScript.missingFromBankReason(List.of("Fishing rod")));
        assertEquals("AutoFishing stopped: could not find Fishing bait or Sandworms in your bank. Put them in your bank or inventory (buy some if you have none), then start again.",
                AutoFishingScript.missingFromBankReason(List.of("Fishing bait")));
        assertEquals("AutoFishing stopped: could not find Fly fishing rod and Feathers in your bank. Put them in your bank or inventory (buy some if you have none), then start again.",
                AutoFishingScript.missingFromBankReason(List.of("Fly fishing rod", "Feather")));
    }

    @Test
    void gettingGearWithdrawsSandwormsWhenBankHasNoFishingBait() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AutoFishingScript script = new AutoFishingScript() {
            {
                scheduledExecutorService = executor;
            }

            @Override
            Rs2NpcModel findNearestFishingSpot() {
                return null;
            }
        };
        AutoFishingConfig config = mock(AutoFishingConfig.class);
        when(config.fishToCatch()).thenReturn(Fish.SACRED_EEL);
        when(config.harpoonSpec()).thenReturn(HarpoonType.NONE);
        when(config.useBank()).thenReturn(true);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(1700, 3700, 0));
            inventory.when(() -> Rs2Inventory.hasItem("Fishing rod")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem("Knife")).thenReturn(true);
            bank.when(Rs2Bank::walkToBankAndUseBank).thenReturn(true);
            bank.when(() -> Rs2Bank.hasItem("Sandworms")).thenReturn(true);

            script.run(config);
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            assertEquals(AutoFishingState.GETTING_GEAR, script.getCurrentState());
            bank.verify(() -> Rs2Bank.withdrawAll("Sandworms"));
            bank.verify(() -> Rs2Bank.withdrawAll("Fishing bait"), never());
            microbot.verify(() -> Microbot.showMessage("AutoFishing stopped: could not find Fishing bait or Sandworms in your bank. Put them in your bank or inventory (buy some if you have none), then start again."));
        }
    }

    private int castsUntilStop(boolean castsGiveXp, boolean looseBait, int maxTicks) {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Rs2NpcModel spot = mock(Rs2NpcModel.class);
        NPC npc = mock(NPC.class);
        NPCComposition composition = mock(NPCComposition.class);
        when(spot.getNpc()).thenReturn(npc);
        when(npc.getIndex()).thenReturn(7);
        when(npc.getTransformedComposition()).thenReturn(composition);
        when(composition.getActions()).thenReturn(new String[]{"Bait", null, null, null, null});
        when(spot.click("Bait")).thenReturn(true);
        AutoFishingScript script = new AutoFishingScript() {
            {
                scheduledExecutorService = executor;
            }

            @Override
            Rs2NpcModel findNearestFishingSpot() {
                return spot;
            }
        };
        AutoFishingConfig config = mock(AutoFishingConfig.class);
        when(config.fishToCatch()).thenReturn(Fish.SACRED_EEL);
        when(config.harpoonSpec()).thenReturn(HarpoonType.NONE);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2190, 3076, 0));
            rs2Player.when(() -> Rs2Player.waitForXpDrop(Skill.FISHING, true)).thenReturn(castsGiveXp);
            inventory.when(() -> Rs2Inventory.hasItem("Fishing rod")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem("Knife")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem("Fishing bait", "Sandworms")).thenReturn(looseBait);

            script.run(config);
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));

            AtomicReference<String> stopReason = new AtomicReference<>();
            microbot.when(() -> Microbot.showMessage(anyString())).thenAnswer(call -> {
                stopReason.set(call.getArgument(0));
                return null;
            });

            for (int casts = 0; casts < maxTicks; casts++) {
                tick.getValue().run();
                if (stopReason.get() != null) {
                    assertEquals("AutoFishing stopped: out of Fishing bait (your Tackle box is empty too). Refill the bait or put it in your inventory, then start again.", stopReason.get());
                    rs2Player.verify(Rs2Player::logout, times(1));
                    verify(spot, times(casts)).click("Bait");
                    return casts;
                }
            }
            verify(spot, times(maxTicks)).click("Bait");
            return -1;
        }
    }

    @Test
    void depositingKeepsTackleBoxAndSandworms() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AutoFishingScript script = new AutoFishingScript() {{
            scheduledExecutorService = executor;
        }};
        AutoFishingConfig config = mock(AutoFishingConfig.class);
        when(config.fishToCatch()).thenReturn(Fish.PIKE);
        when(config.harpoonSpec()).thenReturn(HarpoonType.NONE);
        when(config.useBank()).thenReturn(true);
        Rs2ItemModel rod = mock(Rs2ItemModel.class);
        when(rod.getId()).thenReturn(ItemID.FISHING_ROD);
        Rs2ItemModel sandworms = mock(Rs2ItemModel.class);
        when(sandworms.getId()).thenReturn(ItemID.PISCARILIUS_SANDWORMS);
        Set<Integer> kept = new HashSet<>();

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3105, 3430, 0));
            inventory.when(Rs2Inventory::isFull).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem("Fishing rod")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.get("Fishing rod")).thenReturn(rod);
            inventory.when(() -> Rs2Inventory.hasItem("Sandworms")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.get("Sandworms")).thenReturn(sandworms);
            bank.when(Rs2Bank::walkToBankAndUseBank).thenReturn(true);
            bank.when(() -> Rs2Bank.depositAllExcept(any(Integer[].class))).thenAnswer(call -> {
                kept.addAll(Arrays.asList((Integer[]) call.getRawArguments()[0]));
                return true;
            });

            script.run(config);
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            assertEquals(AutoFishingState.DEPOSITING, script.getCurrentState());
            assertEquals(Set.of(ItemID.TACKLE_BOX, ItemID.FISHING_ROD, ItemID.PISCARILIUS_SANDWORMS), kept);
            bank.verify(Rs2Bank::depositAll, never());
        }
    }

    private void runFishingTick(Fish fish, Consumer<MockedStatic<Rs2Inventory>> baitSetup, String chatMessage, String expectedStopReason) {
        runFishingTick(fish, false, baitSetup, chatMessage, expectedStopReason);
    }

    private void runFishingTick(Fish fish, boolean useBank, Consumer<MockedStatic<Rs2Inventory>> baitSetup, String chatMessage, String expectedStopReason) {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Rs2NpcModel spot = mock(Rs2NpcModel.class);
        AutoFishingScript script = new AutoFishingScript() {
            {
                scheduledExecutorService = executor;
            }

            @Override
            Rs2NpcModel findNearestFishingSpot() {
                return spot;
            }
        };
        AutoFishingConfig config = mock(AutoFishingConfig.class);
        when(config.fishToCatch()).thenReturn(fish);
        when(config.harpoonSpec()).thenReturn(HarpoonType.NONE);
        when(config.useBank()).thenReturn(useBank);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2190, 3076, 0));
            for (String item : fish.getRequiredItems()) {
                if (!AutoFishingScript.isConsumable(item)) {
                    inventory.when(() -> Rs2Inventory.hasItem(item)).thenReturn(true);
                }
            }
            baitSetup.accept(inventory);

            script.run(config);
            if (chatMessage != null) {
                ChatMessage message = mock(ChatMessage.class);
                when(message.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
                when(message.getMessage()).thenReturn(chatMessage);
                script.onChatMessage(message);
            }
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            assertEquals(AutoFishingState.FISHING, script.getCurrentState());
            if (expectedStopReason == null) {
                rs2Player.verify(Rs2Player::logout, never());
                microbot.verify(() -> Microbot.showMessage(anyString()), never());
            } else {
                rs2Player.verify(Rs2Player::logout, times(1));
                microbot.verify(() -> Microbot.showMessage(expectedStopReason));
            }
        }
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
