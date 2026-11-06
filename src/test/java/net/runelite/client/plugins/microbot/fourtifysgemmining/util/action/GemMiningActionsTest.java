package net.runelite.client.plugins.microbot.fourtifysgemmining.util.action;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import java.util.function.Consumer;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysgemmining.TestWorld;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GemMiningActionsTest
{
    @Test
    public void delayedOwnBagEventsAreRecognizedOnceAndUnmatchedActionsRemainManual()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1265, 1), new Item(12020, 1)};
        world.inventoryWidgets();
        AtomicReference<MenuEntry> submitted = new AtomicReference<>();
        world.onMenuRequest = submitted::set;
        assertTrue(world.actions.inventoryBagAction("Open", () -> { }));
        world.actions.dispatch();
        MenuOptionClicked own = new MenuOptionClicked(submitted.get());
        assertTrue(world.actions.consumeBagRequest(own));
        assertFalse(world.actions.consumeBagRequest(own));

        assertTrue(world.actions.inventoryBagAction("Check", () -> { }));
        world.actions.dispatch();
        MenuEntry check = submitted.get();
        assertFalse(world.actions.consumeBagRequest(own));
        assertTrue(world.actions.consumeBagRequest(new MenuOptionClicked(check)));

        int inventoryId = 149 << 16;
        String[] shiftedOptions = new String[9];
        shiftedOptions[8] = "Check";
        Widget shifted = world.widget(inventoryId, 1, 12020, shiftedOptions, null);
        world.widgets.put(inventoryId, world.widget(inventoryId, -1, -1, null, new Widget[] {world.widget(inventoryId, 0, 1265, null, null), shifted}));
        assertTrue(world.actions.inventoryBagAction("Check", () -> { }));
        world.actions.dispatch();
        assertEquals(MenuAction.CC_OP, submitted.get().getType());
        assertEquals(9, submitted.get().getIdentifier());
        assertTrue(world.actions.consumeBagRequest(new MenuOptionClicked(submitted.get())));
    }

    @Test
    public void bagEventCorrelationExpiresAndDoesNotSurviveReset()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(12020, 1)};
        world.inventoryWidgets();
        AtomicReference<MenuEntry> submitted = new AtomicReference<>();
        world.onMenuRequest = submitted::set;
        assertTrue(world.actions.inventoryBagAction("Check", () -> { }));
        world.actions.dispatch();
        world.gameCycle += 29;
        assertFalse(world.actions.consumeBagRequest(new MenuOptionClicked(submitted.get())));

        assertTrue(world.actions.inventoryBagAction("Check", () -> { }));
        world.actions.dispatch();
        world.actions.reset();
        assertFalse(world.actions.consumeBagRequest(new MenuOptionClicked(submitted.get())));
    }

    @Test
    public void nativeWorkerIsSingleFlightAndArmsObservationOnlyAtItsGuardedHandoff()
    {
        TestWorld world = new TestWorld();
        world.deferNativeWork = true;
        GameObject rock = world.rock(2825, 3000);
        AtomicInteger submitted = new AtomicInteger();
        assertTrue(world.actions.mine(GemMine.SURFACE, rock, submitted::incrementAndGet));
        world.actions.dispatch();
        assertTrue(world.actions.isBusy());
        assertTrue(world.requests.isEmpty());
        assertEquals(0, submitted.get());
        assertFalse(world.actions.mine(GemMine.SURFACE, rock, submitted::incrementAndGet));
        world.runNativeWork();
        assertEquals(1, submitted.get());
        assertEquals(1, world.requests.size());
        assertFalse(world.actions.isBusy());
    }

    @Test
    public void deferredNativeWorkerRejectsResetExpiryMovementAndRemovedTarget()
    {
        for (int scenario = 0; scenario < 4; scenario++)
        {
            TestWorld world = new TestWorld();
            world.deferNativeWork = true;
            GameObject rock = world.rock(2825, 3000);
            AtomicInteger submitted = new AtomicInteger();
            assertTrue(world.actions.mine(GemMine.SURFACE, rock, submitted::incrementAndGet));
            world.actions.dispatch();
            if (scenario == 0)
            {
                world.actions.reset();
            }
            else if (scenario == 1)
            {
                world.gameCycle = 29;
            }
            else if (scenario == 2)
            {
                world.position = new WorldPoint(2823, 3000, 0);
            }
            else
            {
                world.objects.clear();
            }
            world.runNativeWork();
            assertEquals(0, submitted.get(), "Scenario " + scenario);
            assertTrue(world.requests.isEmpty(), "Scenario " + scenario);
            assertFalse(world.actions.isBusy());
        }
    }

    @Test
    public void depositsOnlyGemWithDepositAllAndCorrectSlot()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1), new Item(1755, 1)};
        world.bankWithGems();
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.depositGem(dispatched::incrementAndGet));
        assertTrue(world.requests.isEmpty());
        assertEquals(0, dispatched.get());
        world.actions.dispatch();
        assertEquals(1, dispatched.get());
        assertEquals(1, world.actions.getRequestsDispatched());
        assertEquals(1, world.requests.size());
        Object[] request = world.requests.get(0);
        assertEquals(1, request[0]);
        assertEquals((15 << 16) | 3, request[1]);
        assertEquals(MenuAction.CC_OP, request[2]);
        assertEquals(4, request[3]);
        assertEquals(1625, request[4]);
        assertEquals("Deposit-All", request[5]);
    }

    @Test
    public void depositBoxDepositAllUsesClickableWidgetOperationSix()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        world.depositBoxWithGems();
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.depositGem(dispatched::incrementAndGet));
        world.actions.dispatch();
        assertEquals(1, dispatched.get());
        assertEquals(1, world.requests.size());
        Object[] request = world.requests.get(0);
        assertEquals(1, request[0]);
        assertEquals((192 << 16) | 24, request[1]);
        assertEquals(MenuAction.CC_OP, request[2]);
        assertEquals(6, request[3]);
        assertEquals(1625, request[4]);
        assertEquals("Deposit-All", request[5]);
    }

    @Test
    public void staleBankSlotsAndUnsupportedActionsNeverDispatch()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1625, 1)};
        world.bankWithGems();
        world.inventory[0] = new Item(1265, 1);
        assertFalse(world.actions.depositGem(() -> { }));
        int id = (15 << 16) | 3;
        world.inventory[0] = new Item(1625, 1);
        Widget child = world.widget(id, 0, 1625, new String[] {"Deposit-1"}, null);
        world.widgets.put(id, world.widget(id, -1, -1, null, new Widget[] {child}));
        assertFalse(world.actions.depositGem(() -> { }));
        world.actions.dispatch();
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void inventoryReplacementBetweenAdmissionAndDispatchIsRejected()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1625, 1)};
        world.bankWithGems();
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.depositGem(dispatched::incrementAndGet));
        world.inventory[0] = new Item(1265, 1);
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertEquals(0, world.actions.getRequestsDispatched());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void replacedBankWidgetBetweenAdmissionAndDispatchIsRejected()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1625, 1)};
        world.bankWithGems();
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.depositGem(dispatched::incrementAndGet));
        int id = (15 << 16) | 3;
        Widget replacement = world.widget(id, 0, 1625, new String[] {"Deposit-1"}, null);
        world.widgets.put(id, world.widget(id, -1, -1, null, new Widget[] {replacement}));
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void removedCloseWidgetCannotDispatchFromAnOldTree()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1265, 1)};
        world.bankWithGems();
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.closeBank(dispatched::incrementAndGet));
        world.roots = new Widget[0];
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void liveNonAdjacentRocksWithinRangeUseTheNativeMineAction()
    {
        TestWorld world = new TestWorld();
        GameObject adjacent = world.rock(2825, 3000);
        GameObject far = world.rock(2829, 3000);
        assertTrue(world.actions.mine(GemMine.SURFACE, far, () -> { }));
        world.actions.dispatch();
        assertEquals(1, world.requests.size());
        world.requests.clear();
        assertTrue(world.actions.mine(GemMine.SURFACE, adjacent, () -> { }));
        world.actions.dispatch();
        assertEquals(MenuAction.GAME_OBJECT_FIRST_OPTION, world.requests.get(0)[2]);
        assertEquals("Mine", world.requests.get(0)[5]);
        assertTrue(world.actions.mine(GemMine.SURFACE, adjacent, () -> { }));
        world.objects.remove(adjacent);
        world.actions.dispatch();
        assertFalse(world.actions.mine(GemMine.SURFACE, adjacent, () -> { }));
        assertEquals(1, world.requests.size());
    }

    @Test
    public void avoidsPlayersNearRocksAndRechecksBeforeDispatch()
    {
        TestWorld world = new TestWorld();
        GameObject near = world.rock(2825, 3000);
        GameObject far = world.rock(2829, 3000);
        world.players.add(world.player);
        world.otherPlayer(new WorldPoint(2826, 3001, 0));
        world.otherPlayer(new WorldPoint(2829, 3000, 1));
        world.otherPlayer(new WorldPoint(2831, 3000, 0));
        assertEquals(far, world.actions.nearestAvailableRock(GemMine.SURFACE));
        assertFalse(world.actions.mine(GemMine.SURFACE, near, () -> { }));
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.mine(GemMine.SURFACE, far, dispatched::incrementAndGet));
        world.otherPlayer(new WorldPoint(2828, 3000, 0));
        assertEquals(null, world.actions.nearestAvailableRock(GemMine.SURFACE));
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void queuedMiningRechecksSamePositionActivityPreconditions()
    {
        List<Consumer<TestWorld>> invalidate = List.of(
            world -> world.animation = 625,
            TestWorld::fullInventory,
            TestWorld::bankWithGems,
            world -> world.inventory = new Item[] {new Item(1755, 1)},
            world -> world.level = 39,
            world -> world.questState = 1);
        for (int scenario = 0; scenario < invalidate.size(); scenario++)
        {
            TestWorld world = new TestWorld();
            GameObject rock = world.rock(2825, 3000);
            WorldPoint origin = world.position;
            AtomicInteger dispatched = new AtomicInteger();
            assertTrue(world.actions.mine(GemMine.SURFACE, rock, dispatched::incrementAndGet));
            invalidate.get(scenario).accept(world);
            world.actions.dispatch();
            assertEquals(origin, world.position);
            assertEquals(0, dispatched.get(), "Scenario " + scenario);
            assertEquals(0, world.actions.getRequestsDispatched(), "Scenario " + scenario);
            assertTrue(world.requests.isEmpty(), "Scenario " + scenario);
        }
    }

    @Test
    public void queuedUndergroundMiningRechecksDiaryAndSelectedMine()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2824, 9380, 0);
        GameObject rock = world.rock(2825, 9380);
        AtomicInteger dispatched = new AtomicInteger();
        assertFalse(world.actions.mine(GemMine.SURFACE, rock, dispatched::incrementAndGet));
        assertTrue(world.actions.mine(GemMine.UNDERGROUND, rock, dispatched::incrementAndGet));
        world.diary = 1;
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertEquals(0, world.actions.getRequestsDispatched());
        assertTrue(world.requests.isEmpty());
        world.diary = 2;
        assertTrue(world.actions.mine(GemMine.UNDERGROUND, rock, dispatched::incrementAndGet));
        world.actions.dispatch();
        assertEquals(1, dispatched.get());
        assertEquals(1, world.requests.size());
    }

    @Test
    public void undergroundRequiresCompletedMediumDiaryRatherThanStartedDiary()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2824, 9380, 0);
        GameObject rock = world.rock(2825, 9380);
        for (int incomplete : new int[] {0, 1})
        {
            world.diary = incomplete;
            assertEquals("Underground mine needs the medium Karamja Diary. Set Mine to Surface or complete the diary", world.actions.prerequisite(GemMine.UNDERGROUND));
            assertNull(world.actions.prerequisite(GemMine.SURFACE));
            assertFalse(world.actions.mine(GemMine.UNDERGROUND, rock, () -> { }));
            world.actions.dispatch();
            assertTrue(world.requests.isEmpty());
        }
        world.diary = 2;
        assertNull(world.actions.prerequisite(GemMine.UNDERGROUND));
        assertTrue(world.actions.mine(GemMine.UNDERGROUND, rock, () -> { }));
        world.actions.dispatch();
        assertEquals(1, world.requests.size());
        assertEquals("Mine", world.requests.get(0)[5]);
    }

    @Test
    public void queuedCloseDoesNotCloseTheBankIfGemsAppear()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1265, 1)};
        world.bankWithGems();
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.closeBank(dispatched::incrementAndGet));
        world.inventory = new Item[] {new Item(1265, 1), new Item(1625, 1)};
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertEquals(0, world.actions.getRequestsDispatched());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void queuedBankOpeningDoesNotReopenAnAlreadyOpenBank()
    {
        for (boolean underground : new boolean[] {false, true})
        {
            TestWorld world = new TestWorld();
            GemMine mine = underground ? GemMine.UNDERGROUND : GemMine.SURFACE;
            if (underground)
            {
                world.position = new WorldPoint(2840, 9385, 0);
                world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2841, 9385, 0));
            }
            else
            {
                world.position = new WorldPoint(2852, 2951, 0);
                world.banker(7, new WorldPoint(2853, 2951, 0));
            }
            AtomicInteger dispatched = new AtomicInteger();
            assertTrue(world.actions.openBank(mine, dispatched::incrementAndGet));
            world.bankWithGems();
            world.actions.dispatch();
            assertEquals(0, dispatched.get());
            assertEquals(0, world.actions.getRequestsDispatched());
            assertTrue(world.requests.isEmpty());
        }
    }

    @Test
    public void undergroundDepositChestIsAvailableAtTwentyButNotTwentyOneTiles()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2820, 9385, 0);
        world.object(10530, "Bank deposit chest", new String[] {"Deposit"}, new WorldPoint(2841, 9385, 0));
        assertFalse(world.actions.openBank(GemMine.UNDERGROUND, () -> { }));
        assertTrue(world.requests.isEmpty());
        world.position = new WorldPoint(2821, 9385, 0);
        assertTrue(world.actions.openBank(GemMine.UNDERGROUND, () -> { }));
        world.actions.dispatch();
        assertEquals("Deposit", world.requests.get(0)[5]);
    }

    @Test
    public void replacedNpcIndexDoesNotDispatchToAnotherNpc()
    {
        TestWorld world = new TestWorld();
        world.position = new WorldPoint(2852, 2951, 0);
        NPC banker = world.banker(7, new WorldPoint(2853, 2951, 0));
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.openBank(GemMine.SURFACE, dispatched::incrementAndGet));
        world.npcs.remove(banker);
        world.banker(7, new WorldPoint(2853, 2951, 0));
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void pickaxeRequirementsAndEquipmentAreChecked()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1275, 1)};
        world.level = 40;
        assertEquals("Wear or carry a pickaxe you can use", world.actions.prerequisite(GemMine.SURFACE));
        world.inventory = new Item[0];
        world.equipment = new Item[] {new Item(1265, 1)};
        assertNull(world.actions.prerequisite(GemMine.SURFACE));
        world.diary = 0;
        assertEquals("Underground mine needs the medium Karamja Diary. Set Mine to Surface or complete the diary", world.actions.prerequisite(GemMine.UNDERGROUND));
        world.questState = 1;
        assertEquals("Complete the Shilo Village quest to use this mine", world.actions.prerequisite(GemMine.SURFACE));
    }

    @Test
    public void depositActionIndexFollowsLiveWidgetAcrossQuantityModes()
    {
        TestWorld world = new TestWorld();
        world.inventory = new Item[] {new Item(1625, 1)};
        world.bankWithGems();
        int id = (15 << 16) | 3;
        for (int index : new int[] {0, 7})
        {
            String[] options = new String[8];
            options[index] = "Deposit-All";
            Widget child = world.widget(id, 0, 1625, options, null);
            world.widgets.put(id, world.widget(id, -1, -1, null, new Widget[] {child}));
            assertTrue(world.actions.depositGem(() -> { }));
            world.actions.dispatch();
            Object[] request = world.requests.get(world.requests.size() - 1);
            assertEquals(index + 1, request[3]);
            assertEquals(MenuAction.CC_OP, request[2]);
        }
    }

    @Test
    public void queueIsBoundedFifoAndDispatchesOneRequestAtATime()
    {
        TestWorld world = new TestWorld();
        List<Integer> observed = new ArrayList<>();
        for (int i = 0; i < 50; i++)
        {
            int index = i;
            assertTrue(world.actions.enqueue(() -> observed.add(index)));
        }
        assertFalse(world.actions.enqueue(() -> observed.add(50)));
        for (int i = 0; i < 50; i++)
        {
            world.actions.dispatch();
            assertEquals(i + 1, observed.size());
            assertEquals(Integer.valueOf(i), observed.get(i));
        }
        world.actions.dispatch();
        assertEquals(50, observed.size());
    }

    @Test
    public void nextTickAndResetExpireQueuedRequestsWithoutCallbacks()
    {
        TestWorld world = new TestWorld();
        GameObject rock = world.rock(2825, 3000);
        AtomicInteger dispatched = new AtomicInteger();
        assertTrue(world.actions.mine(GemMine.SURFACE, rock, dispatched::incrementAndGet));
        world.actions.nextTick();
        world.actions.dispatch();
        assertTrue(world.actions.mine(GemMine.SURFACE, rock, dispatched::incrementAndGet));
        world.actions.reset();
        world.actions.dispatch();
        assertEquals(0, dispatched.get());
        assertEquals(0, world.actions.getRequestsDispatched());
        assertTrue(world.requests.isEmpty());
    }

    @Test
    public void invalidPlayerStateRejectsQueuedActions()
    {
        for (int scenario = 0; scenario < 5; scenario++)
        {
            TestWorld world = new TestWorld();
            AtomicInteger ran = new AtomicInteger();
            assertTrue(world.actions.enqueue(ran::incrementAndGet));
            switch (scenario)
            {
                case 0:
                    world.state = GameState.LOADING;
                    break;
                case 1:
                    world.dead = true;
                    break;
                case 2:
                    world.playerPresent = false;
                    break;
                case 3:
                    world.topLevel = false;
                    break;
                default:
                    world.position = new WorldPoint(2825, 3000, 0);
                    break;
            }
            world.actions.dispatch();
            assertEquals(0, ran.get(), "Scenario " + scenario);
        }
    }
}
