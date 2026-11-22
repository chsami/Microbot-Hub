package net.runelite.client.plugins.microbot.revkiller;

import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemQueryable;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class RevKillerFightLootBankTest {

    @Test
    void fallbackGoblinSpotFightsGoblinsInsteadOfWalkingBackToImps() {
        WorldPoint goblinSpot = new WorldPoint(3226, 10067, 0);
        revKillerScript script = spy(new revKillerScript());
        script.selectedRev = "Revenant goblin";
        script.selectedWP = goblinSpot;
        doReturn(true).when(script).playerCheck();
        revKillerConfig config = mock(revKillerConfig.class);
        when(config.selectedRev()).thenReturn(revKillerConfig.RevSelections.IMP);
        Rs2NpcCache cache = mock(Rs2NpcCache.class);
        Rs2NpcQueryable query = mock(Rs2NpcQueryable.class);
        Rs2NpcQueryable goblins = mock(Rs2NpcQueryable.class);
        Rs2NpcQueryable imps = mock(Rs2NpcQueryable.class);
        when(cache.query()).thenReturn(query);
        when(query.withName("Revenant goblin")).thenReturn(goblins);
        when(query.withName("Revenant imp")).thenReturn(imps);
        when(goblins.nearest()).thenReturn(mock(Rs2NpcModel.class));
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getRs2NpcCache).thenReturn(cache);

            script.fightrev(config);
        }

        assertEquals("Revenant goblin", script.selectedRev);
        assertEquals(goblinSpot, script.selectedWP);
    }

    @Test
    @Timeout(10)
    void valuableLootThatCannotBeTakenIsTriedOnceThenLeftForTheNextTick() {
        revKillerScript script = spy(new revKillerScript());
        doReturn(true).when(script).isRunning();
        Rs2TileItemCache cache = mock(Rs2TileItemCache.class);
        Rs2TileItemQueryable query = mock(Rs2TileItemQueryable.class);
        when(cache.query()).thenReturn(query);
        when(query.where(any())).thenReturn(query);
        when(query.toList()).thenReturn(List.of());
        AtomicInteger attempts = new AtomicInteger();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2GroundItem> ground = mockStatic(Rs2GroundItem.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            microbot.when(Microbot::getRs2TileItemCache).thenReturn(cache);
            ground.when(() -> Rs2GroundItem.isItemBasedOnValueOnGround(500, 12)).thenAnswer(i -> attempts.get() < 3);
            ground.when(() -> Rs2GroundItem.lootItemBasedOnValue(500, 12)).thenAnswer(i -> {
                attempts.incrementAndGet();
                return false;
            });

            script.loot();
        }

        assertEquals(1, attempts.get());
    }

    @Test
    @Timeout(10)
    void outOfAmmoTurnsThePluginOffAndSaysWhatToRestock() {
        List<String> events = bankRunWithEmptyBank(false);

        assertStoppedWith(events,
                "Revenant Killer stopped: 100 or fewer of your selected ammo in your bank. Put more than 100 of your selected ammo in your bank and start the plugin again.",
                "<html>Revenant Killer stopped: 100 or fewer of your selected ammo in your bank.<br>Put more than 100 of your selected ammo in your bank and start the plugin again.</html>");
        assertFalse(events.contains("log:Withdrawing Stamina potion"));
    }

    @Test
    @Timeout(10)
    void outOfSharksTurnsThePluginOffAndSaysWhatToRestock() {
        List<String> events = bankRunWithEmptyBank(true);

        assertStoppedWith(events,
                "Revenant Killer stopped: fewer than 10 Sharks in your bank. Put at least 10 Sharks in your bank and start the plugin again.",
                "<html>Revenant Killer stopped: fewer than 10 Sharks in your bank.<br>Put at least 10 Sharks in your bank and start the plugin again.</html>");
    }

    private static void assertStoppedWith(List<String> events, String message, String popup) {
        int logged = events.indexOf("log:" + message);
        int stopped = events.indexOf("stop");
        int shown = events.indexOf("popup:" + popup);
        assertTrue(logged >= 0, events.toString());
        assertTrue(stopped > logged, events.toString());
        assertTrue(shown > stopped, events.toString());
        assertEquals(1, events.stream().filter("stop"::equals).count());
    }

    private static List<String> bankRunWithEmptyBank(boolean ammoAndPotionsInBank) {
        List<String> events = new ArrayList<>();
        revKillerScript script = spy(new revKillerScript());
        script.selectedRev = "Revenant imp";
        script.selectedArrow = ItemID.RUNE_ARROW;
        doNothing().when(script).handleBreaks();
        doReturn(false).when(script).isItTimeToGo();
        doReturn(true).when(script).equipChargedGloryAtBank();
        doReturn(true).when(script).equipDuelingRingAtBank();
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Random> random = mockStatic(Rs2Random.class)) {
            microbot.when(() -> Microbot.log(anyString())).thenAnswer(i -> {
                events.add("log:" + i.getArgument(0));
                return null;
            });
            microbot.when(() -> Microbot.stopPlugin(revKillerPlugin.class)).thenAnswer(i -> {
                events.add("stop");
                return true;
            });
            microbot.when(() -> Microbot.showMessage(anyString())).thenAnswer(i -> {
                events.add("popup:" + i.getArgument(0));
                return null;
            });
            random.when(() -> Rs2Random.nextInt(anyInt(), anyInt(), anyDouble(), anyBoolean())).thenReturn(1);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            if (ammoAndPotionsInBank) {
                bank.when(() -> Rs2Bank.count(ItemID.RUNE_ARROW)).thenReturn(1000);
                bank.when(() -> Rs2Bank.count("Stamina potion(4)")).thenReturn(1);
                bank.when(() -> Rs2Bank.getBankItem("Ranging potion(4)")).thenReturn(mock(Rs2ItemModel.class));
            }

            script.Bankfortrip();
        }
        return events;
    }
}
