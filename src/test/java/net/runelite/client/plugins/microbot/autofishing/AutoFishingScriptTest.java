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
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3105, 3430, 0));
            widget.when(() -> Rs2Widget.isWidgetVisible(InterfaceID.LevelupDisplay.CONTINUE)).thenReturn(true);

            script.run(mock(AutoFishingConfig.class));
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            widget.verify(() -> Rs2Widget.clickWidget(InterfaceID.LevelupDisplay.CONTINUE));
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
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(true);
            bank.when(Rs2Bank::isOpen).thenReturn(false);

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
    void sacredEelChecksBankBeforeStoppingWhenStartedWithoutBait() {
        runFishingTick(Fish.SACRED_EEL, false, inventory -> { }, null,
                "AutoFishing stopped: could not find Fishing bait or Sandworms in your bank. Put them in your bank or inventory (buy some if you have none), then start again.",
                AutoFishingState.GETTING_GEAR);
    }

    @Test
    void sacredEelStopsWhenGameSaysTackleBoxIsOutOfBait() {
        runFishingTick(Fish.SACRED_EEL, inventory -> inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true),
                "You don't have any fishing bait left.",
                "AutoFishing stopped: out of Fishing bait (your Tackle box is empty too). Refill the bait or put it in your inventory, then start again.");
    }

    @Test
    void tackleBoxDoesNotStandInForFeathers() {
        runFishingTick(Fish.TROUT_AND_SALMON, false, inventory -> inventory.when(() -> Rs2Inventory.hasItem(ItemID.TACKLE_BOX)).thenReturn(true), null,
                "AutoFishing stopped: could not find Feathers in your bank. Put them in your bank or inventory (buy some if you have none), then start again.",
                AutoFishingState.GETTING_GEAR);
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
    void startupNearFishingSpotWithdrawsRodThenSandwormsWhenBankHasNoFishingBait() {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Rs2NpcModel spot = mock(Rs2NpcModel.class);
        AtomicBoolean bankOpen = new AtomicBoolean(false);
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
            inventory.when(() -> Rs2Inventory.hasItem("Knife")).thenReturn(true);
            bank.when(Rs2Bank::isOpen).thenAnswer(call -> bankOpen.get());
            bank.when(Rs2Bank::walkToBankAndUseBank).thenAnswer(call -> {
                bankOpen.set(true);
                return true;
            });
            bank.when(() -> Rs2Bank.hasItem("Fishing rod")).thenReturn(true);
            bank.when(() -> Rs2Bank.hasItem("Sandworms")).thenReturn(true);

            script.run(config);
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            assertEquals(AutoFishingState.GETTING_GEAR, script.getCurrentState());
            bank.verify(Rs2Bank::walkToBankAndUseBank);
            bank.verify(() -> Rs2Bank.withdrawOne("Fishing rod"));
            bank.verify(() -> Rs2Bank.withdrawAll("Sandworms"), never());
            bank.verify(Rs2Bank::closeBank, never());

            inventory.when(() -> Rs2Inventory.hasItem("Fishing rod")).thenReturn(true);
            tick.getValue().run();

            assertEquals(AutoFishingState.GETTING_GEAR, script.getCurrentState());
            bank.verify(() -> Rs2Bank.withdrawAll("Sandworms"));
            bank.verify(() -> Rs2Bank.withdrawAll("Fishing bait"), never());
            bank.verify(Rs2Bank::closeBank, never());
            microbot.verify(() -> Microbot.showMessage(anyString()), never());

            inventory.when(() -> Rs2Inventory.hasItem("Fishing bait", "Sandworms")).thenReturn(true);
            tick.getValue().run();

            bank.verify(() -> Rs2Bank.withdrawAll("Sandworms"), times(1));
            bank.verify(Rs2Bank::closeBank);
            microbot.verify(() -> Microbot.showMessage(anyString()), never());
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
        AutoFishingScript script = new AutoFishingScript() {
            {
                scheduledExecutorService = executor;
            }

            @Override
            Set<BankLocation> accessibleBanks() {
                return Set.of();
            }

            @Override
            Set<DepositBoxLocation> accessibleDepositBoxes() {
                return Set.of();
            }
        };
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

    @Test
    void karamjaLobsterTripDepositsAtPortSarimBoxInsteadOfTeleportBank() {
        Set<WorldPoint> planned = new HashSet<>();
        Set<Integer> kept = new HashSet<>();
        runDepositTick(false, DepositBoxLocation.PORT_SARIM.getWorldPoint(), planned, kept, (bank, depositBox) -> {
            depositBox.verify(() -> Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.PORT_SARIM));
            depositBox.verify(Rs2DepositBox::closeDepositBox);
            bank.verify(Rs2Bank::walkToBankAndUseBank, never());
            bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any(BankLocation.class)), never());
            bank.verify(Rs2Bank::emptyFishBarrel, never());
            bank.verify(() -> Rs2Bank.depositAllExcept(any(Integer[].class)), never());
        });

        assertEquals(Set.of(BankLocation.LUMBRIDGE_TOP.getWorldPoint(), BankLocation.DRAYNOR_VILLAGE.getWorldPoint(),
                DepositBoxLocation.PORT_SARIM.getWorldPoint(), PORT_SARIM_BOX_APPROACH), planned);
        assertEquals(Set.of(ItemID.LOBSTER_POT, ItemID.COINS), kept);
    }

    @Test
    void reachingTheWalkableTileBesideTheDepositBoxPicksTheDepositBox() {
        Set<Integer> kept = new HashSet<>();
        runDepositTick(false, PORT_SARIM_BOX_APPROACH, new HashSet<>(), kept, (bank, depositBox) -> {
            depositBox.verify(() -> Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.PORT_SARIM));
            bank.verify(Rs2Bank::walkToBankAndUseBank, never());
            bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any(BankLocation.class)), never());
        });

        assertEquals(Set.of(ItemID.LOBSTER_POT, ItemID.COINS), kept);
    }

    @Test
    void depositBoxTargetsAddOnlyWalkableTilesBesideTheBox() {
        WorldPoint box = DepositBoxLocation.PORT_SARIM.getWorldPoint();
        AutoFishingScript script = new AutoFishingScript() {
            @Override
            boolean isWalkable(WorldPoint tile) {
                return tile.equals(box.dx(1)) || tile.equals(box.dy(-1));
            }
        };

        assertEquals(Set.of(box, box.dx(1), box.dy(-1)), script.depositBoxTargets(box));
    }

    private static final WorldPoint PORT_SARIM_BOX_APPROACH = DepositBoxLocation.PORT_SARIM.getWorldPoint().dy(-1);

    @Test
    void fishBarrelTripOnlyConsidersBanksSoTheBarrelCanBeEmptied() {
        Set<WorldPoint> planned = new HashSet<>();
        Set<Integer> kept = new HashSet<>();
        runDepositTick(true, BankLocation.DRAYNOR_VILLAGE.getWorldPoint(), planned, kept, (bank, depositBox) -> {
            bank.verify(() -> Rs2Bank.walkToBankAndUseBank(BankLocation.DRAYNOR_VILLAGE));
            bank.verify(Rs2Bank::emptyFishBarrel);
            bank.verify(Rs2Bank::walkToBankAndUseBank, never());
            depositBox.verify(() -> Rs2DepositBox.walkToAndUseDepositBox(any(DepositBoxLocation.class)), never());
        });

        assertEquals(Set.of(BankLocation.LUMBRIDGE_TOP.getWorldPoint(), BankLocation.DRAYNOR_VILLAGE.getWorldPoint()), planned);
        assertEquals(Set.of(ItemID.LOBSTER_POT, ItemID.COINS, ItemID.FISH_BARREL_OPEN), kept);
    }

    @Test
    void depositTripFallsBackToNearestBankWhenNothingIsReachableWithoutTeleports() {
        runDepositTick(false, null, new HashSet<>(), new HashSet<>(), (bank, depositBox) -> {
            bank.verify(Rs2Bank::walkToBankAndUseBank);
            bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any(BankLocation.class)), never());
            depositBox.verify(() -> Rs2DepositBox.walkToAndUseDepositBox(any(DepositBoxLocation.class)), never());
        });
    }

    @Test
    void depositLogLineNamesThePlaceAndWhetherItIsABankOrDepositBox() {
        assertEquals("Depositing at the Port Sarim deposit box (the closest one reachable without teleporting, so the walk back to the fishing spot stays short)",
                AutoFishingScript.depositPointMessage(DepositBoxLocation.PORT_SARIM.name(), true, false));
        assertEquals("Depositing at the Draynor Village bank (the closest one reachable without teleporting, so the walk back to the fishing spot stays short)"
                        + " (deposit boxes skipped: a Fish barrel can only be emptied at a bank)",
                AutoFishingScript.depositPointMessage(BankLocation.DRAYNOR_VILLAGE.name(), false, true));
    }

    @Test
    void stoppingClosesAnOpenDepositBox() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2DepositBox> depositBox = mockStatic(Rs2DepositBox.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class)) {
            depositBox.when(Rs2DepositBox::isOpen).thenReturn(true);

            new AutoFishingScript().shutdown();

            depositBox.verify(Rs2DepositBox::closeDepositBox);
            bank.verify(Rs2Bank::closeBank, never());
        }
    }

    private void runDepositTick(boolean fishBarrel, WorldPoint reachedOnFoot, Set<WorldPoint> planned, Set<Integer> kept,
                                BiConsumer<MockedStatic<Rs2Bank>, MockedStatic<Rs2DepositBox>> verification) {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AtomicBoolean inventoryFull = new AtomicBoolean(true);
        AutoFishingScript script = new AutoFishingScript() {
            {
                scheduledExecutorService = executor;
            }

            @Override
            Set<BankLocation> accessibleBanks() {
                return Set.of(BankLocation.LUMBRIDGE_TOP, BankLocation.DRAYNOR_VILLAGE);
            }

            @Override
            Set<DepositBoxLocation> accessibleDepositBoxes() {
                return Set.of(DepositBoxLocation.PORT_SARIM);
            }

            @Override
            boolean isWalkable(WorldPoint tile) {
                return tile.equals(PORT_SARIM_BOX_APPROACH);
            }

            @Override
            WorldPoint nearestOnFoot(WorldPoint from, Set<WorldPoint> targets) {
                assertEquals(new WorldPoint(2924, 3179, 0), from);
                planned.addAll(targets);
                return reachedOnFoot;
            }
        };
        AutoFishingConfig config = mock(AutoFishingConfig.class);
        when(config.fishToCatch()).thenReturn(Fish.LOBSTER);
        when(config.harpoonSpec()).thenReturn(HarpoonType.NONE);
        when(config.useBank()).thenReturn(true);
        Rs2ItemModel pot = mock(Rs2ItemModel.class);
        when(pot.getId()).thenReturn(ItemID.LOBSTER_POT);

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2DepositBox> depositBox = mockStatic(Rs2DepositBox.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2924, 3179, 0));
            inventory.when(Rs2Inventory::isFull).thenAnswer(call -> inventoryFull.get());
            inventory.when(() -> Rs2Inventory.hasItem("Lobster pot")).thenReturn(true);
            inventory.when(() -> Rs2Inventory.get("Lobster pot")).thenReturn(pot);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.COINS)).thenReturn(true);
            inventory.when(() -> Rs2Inventory.hasItem(ItemID.FISH_BARREL_OPEN)).thenReturn(fishBarrel);
            bank.when(Rs2Bank::walkToBankAndUseBank).thenReturn(true);
            bank.when(() -> Rs2Bank.walkToBankAndUseBank(any(BankLocation.class))).thenReturn(true);
            bank.when(() -> Rs2Bank.depositAllExcept(any(Integer[].class))).thenAnswer(call -> {
                kept.addAll(Arrays.asList((Integer[]) call.getRawArguments()[0]));
                inventoryFull.set(false);
                return true;
            });
            depositBox.when(() -> Rs2DepositBox.walkToAndUseDepositBox(any(DepositBoxLocation.class))).thenReturn(true);
            depositBox.when(Rs2DepositBox::isOpen).thenReturn(true);
            depositBox.when(() -> Rs2DepositBox.depositAllExcept(any(Integer[].class))).thenAnswer(call -> {
                kept.addAll(Arrays.asList((Integer[]) call.getRawArguments()[0]));
                inventoryFull.set(false);
                return true;
            });

            script.run(config);
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
            tick.getValue().run();

            assertEquals(AutoFishingState.DEPOSITING, script.getCurrentState());
            verification.accept(bank, depositBox);
        }
    }

    private void runFishingTick(Fish fish, Consumer<MockedStatic<Rs2Inventory>> baitSetup, String chatMessage, String expectedStopReason) {
        runFishingTick(fish, false, baitSetup, chatMessage, expectedStopReason);
    }

    private void runFishingTick(Fish fish, boolean useBank, Consumer<MockedStatic<Rs2Inventory>> baitSetup, String chatMessage, String expectedStopReason) {
        runFishingTick(fish, useBank, baitSetup, chatMessage, expectedStopReason, AutoFishingState.FISHING);
    }

    private void runFishingTick(Fish fish, boolean useBank, Consumer<MockedStatic<Rs2Inventory>> baitSetup, String chatMessage,
                                String expectedStopReason, AutoFishingState expectedState) {
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
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(2190, 3076, 0));
            if (expectedState == AutoFishingState.GETTING_GEAR) {
                bank.when(Rs2Bank::walkToBankAndUseBank).thenReturn(true);
            }
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

            assertEquals(expectedState, script.getCurrentState());
            if (expectedState == AutoFishingState.GETTING_GEAR) {
                bank.verify(Rs2Bank::walkToBankAndUseBank);
                bank.verify(() -> Rs2Bank.withdrawAll(anyString()), never());
            }
            if (expectedStopReason == null) {
                rs2Player.verify(Rs2Player::logout, never());
                microbot.verify(() -> Microbot.showMessage(anyString()), never());
            } else {
                rs2Player.verify(Rs2Player::logout, times(1));
                microbot.verify(() -> Microbot.showMessage(expectedStopReason));
            }
        }
    }

    private static final WorldPoint BARBARIAN_VILLAGE_SOUTH_SPOT = new WorldPoint(3109, 3433, 0);
    private static final WorldPoint BARBARIAN_VILLAGE_FIRE = new WorldPoint(3100, 3433, 0);

    @Test
    void cookingWalksRightUpToAFireNineTilesAway() {
        Rs2TileObjectModel fire = barbarianVillageFire();
        List<AutoFishingState> states = runCookingTicks(true, List.of(fire), 1, walker -> {
            walker.verify(() -> Rs2Walker.walkTo(BARBARIAN_VILLAGE_FIRE, AutoFishingScript.FIRE_REACH_DISTANCE));
            walker.verify(() -> Rs2Walker.walkTo(BARBARIAN_VILLAGE_FIRE), never());
        });

        assertEquals(List.of(AutoFishingState.COOKING), states);
    }

    @Test
    void emptyFireLookupsRightAfterReconnectAreRetriedInsteadOfStopping() {
        Rs2TileObjectModel fire = barbarianVillageFire();
        List<AutoFishingState> states = runCookingTicks(true, Arrays.asList(null, null, fire), 3,
                walker -> walker.verify(() -> Rs2Walker.walkTo(BARBARIAN_VILLAGE_FIRE, AutoFishingScript.FIRE_REACH_DISTANCE)));

        assertEquals(List.of(AutoFishingState.COOKING, AutoFishingState.COOKING, AutoFishingState.COOKING), states);
    }

    @Test
    void noFireAfterRetriesBanksOrDropsTheRawFishInsteadOfStopping() {
        for (boolean useBank : List.of(true, false)) {
            List<AutoFishingState> states = runCookingTicks(useBank, List.of(), AutoFishingScript.MAX_FIRE_LOOKUP_MISSES + 1,
                    walker -> walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never()));

            for (int tick = 0; tick < AutoFishingScript.MAX_FIRE_LOOKUP_MISSES; tick++) {
                assertEquals(AutoFishingState.COOKING, states.get(tick), "useBank=" + useBank + " tick " + tick);
            }
            assertEquals(useBank ? AutoFishingState.DEPOSITING : AutoFishingState.DROPPING,
                    states.get(AutoFishingScript.MAX_FIRE_LOOKUP_MISSES), "useBank=" + useBank);
        }
    }

    private static Rs2TileObjectModel barbarianVillageFire() {
        Rs2TileObjectModel fire = mock(Rs2TileObjectModel.class);
        when(fire.getWorldLocation()).thenReturn(BARBARIAN_VILLAGE_FIRE);
        return fire;
    }

    private List<AutoFishingState> runCookingTicks(boolean useBank, List<Rs2TileObjectModel> fireLookups, int ticks,
                                                   Consumer<MockedStatic<Rs2Walker>> verification) {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Iterator<Rs2TileObjectModel> lookups = fireLookups.iterator();
        AtomicBoolean stopped = new AtomicBoolean(false);
        AutoFishingScript script = new AutoFishingScript() {
            {
                scheduledExecutorService = executor;
            }

            @Override
            Rs2TileObjectModel getNearbyFireOrRange() {
                return lookups.hasNext() ? lookups.next() : null;
            }

            @Override
            Set<BankLocation> accessibleBanks() {
                return Set.of();
            }

            @Override
            Set<DepositBoxLocation> accessibleDepositBoxes() {
                return Set.of();
            }

            @Override
            public void shutdown() {
                stopped.set(true);
            }
        };
        AutoFishingConfig config = mock(AutoFishingConfig.class);
        when(config.fishToCatch()).thenReturn(Fish.TROUT_AND_SALMON);
        when(config.harpoonSpec()).thenReturn(HarpoonType.NONE);
        when(config.useBank()).thenReturn(useBank);
        when(config.cookFish()).thenReturn(true);
        Rs2ItemModel rawTrout = mock(Rs2ItemModel.class);
        when(rawTrout.getName()).thenReturn("Raw trout");

        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Player> rs2Player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2DepositBox> depositBox = mockStatic(Rs2DepositBox.class);
             MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
             MockedStatic<Global> global = mockStatic(Global.class);
             MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
             MockedStatic<Rs2Antiban> antiban = mockStatic(Rs2Antiban.class)) {
            microbot.when(Microbot::isLoggedIn).thenReturn(true);
            rs2Player.when(Rs2Player::getWorldLocation).thenReturn(BARBARIAN_VILLAGE_SOUTH_SPOT);
            rs2Player.when(() -> Rs2Player.getSkillRequirement(Skill.COOKING, 15)).thenReturn(true);
            inventory.when(Rs2Inventory::isFull).thenReturn(true);
            inventory.when(Rs2Inventory::all).thenReturn(List.of(rawTrout));
            inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(true);

            script.run(config);
            ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
            verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));

            List<AutoFishingState> states = new ArrayList<>();
            for (int i = 0; i < ticks; i++) {
                tick.getValue().run();
                states.add(script.getCurrentState());
            }

            assertFalse(stopped.get());
            rs2Player.verify(Rs2Player::logout, never());
            microbot.verify(() -> Microbot.showMessage(anyString()), never());
            verification.accept(walker);
            return states;
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
