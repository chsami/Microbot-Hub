package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.PuzzleModels;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.TunnelMap;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptTaskTest
{
    @Test
    public void observedChestOpeningAllowsSearchOnNextTickWithoutRepeatingInTheSameTick()
    {
        FakeActions actions = new FakeActions();
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.open = true;
        task.execute();
        assertEquals(List.of("Open"), actions.options);
        actions.tick = 1;
        task.execute();
        assertEquals(List.of("Open", "Search"), actions.options);
    }

    @Test
    public void observedRewardsCloseOnNextTickInsteadOfWaitingForSearchRetry()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.rewards = true;
        actions.tick = 1;
        task.execute();
        assertEquals((155 << 16) | 11, actions.widget);
        assertEquals(1, task.getChests());
        assertEquals(List.of("Search"), actions.options);
    }

    @Test
    public void rewardsCloseWaitsForTheCloseButtonWithoutSpendingARetry()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.rewards = true;
        actions.closeButton = false;
        actions.tick = 1;
        task.execute();
        assertEquals(0, actions.widget);
        actions.closeButton = true;
        actions.tick = 2;
        task.execute();
        assertEquals((155 << 16) | 11, actions.widget);
    }

    @Test
    public void unkilledTunnelBrotherCryptKeepsProtectionForCombat()
    {
        FakeActions actions = new FakeActions();
        actions.kills = 0;
        actions.position = Brother.DHAROK.getSarcophagus();
        actions.protection = Prayer.PROTECT_FROM_MELEE;
        actions.choices = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.choices = false;
        actions.tick = 1;
        task.execute();
        actions.tick = 9;
        task.execute();
        assertEquals(Prayer.PROTECT_FROM_MELEE, actions.protection);
    }

    @Test
    public void tabSwitchCountsAsProgressSoTheActionRetriesNextTick()
    {
        FakeActions actions = new FakeActions();
        actions.accept = false;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(List.of("Open"), actions.options);
        actions.tab = 3;
        actions.tick = 1;
        task.execute();
        assertEquals(List.of("Open", "Open"), actions.options);
    }

    @Test
    public void walkingWithinARoomDoesNotHoldBackTheDoorClick()
    {
        FakeActions actions = new FakeActions();
        actions.position = TunnelMap.roomCenter(0);
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.position = TunnelMap.roomCenter(0).dx(1);
        actions.tick = 8;
        task.execute();
        assertEquals(2, actions.doors, "a retry while walking in the same room is not delayed");
    }

    @Test
    public void chestWaitsForTheDoorCrossingAnimationToFinish()
    {
        FakeActions actions = new FakeActions();
        actions.idle = false;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertTrue(actions.options.isEmpty());
        actions.idle = true;
        actions.tick = 1;
        task.execute();
        assertEquals(List.of("Open"), actions.options);
    }

    @Test
    public void observedTunnelPromptIsHandledOnNextTickAfterCoffinSearch()
    {
        FakeActions actions = new FakeActions();
        actions.position = Brother.DHAROK.getSarcophagus();
        actions.kills = 5;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.choices = true;
        actions.tick = 1;
        task.execute();
        assertEquals(1, actions.dialogueRequests);
        assertEquals(List.of("Search"), actions.options);
    }

    @Test
    public void observedRoomCrossingReleasesDoorWaitButApproachMovementDoesNot()
    {
        FakeActions actions = new FakeActions();
        actions.position = TunnelMap.roomCenter(0);
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(1, actions.doors);
        actions.position = actions.position.dx(1);
        actions.tick = 1;
        task.execute();
        assertEquals(1, actions.doors);
        actions.position = TunnelMap.roomCenter(1);
        actions.tick = 2;
        task.execute();
        assertEquals(1, actions.doors, "waits for the door crossing to settle");
        actions.idle = false;
        actions.tick = 3;
        task.execute();
        assertEquals(1, actions.doors, "waits for the crossing animation");
        actions.idle = true;
        actions.tick = 4;
        task.execute();
        assertEquals(2, actions.doors);
    }

    @Test
    public void aRoomChangeWhileTheCrossedDoorIsUnloadedDoesNotReleaseTheNextClick()
    {
        FakeActions actions = new FakeActions();
        actions.position = TunnelMap.roomCenter(0);
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(1, actions.doors);
        actions.doorLoaded = false;
        actions.position = TunnelMap.roomCenter(1);
        actions.tick = 1;
        task.execute();
        actions.tick = 2;
        task.execute();
        actions.tick = 3;
        task.execute();
        assertEquals(1, actions.doors, "the game still drops clicks during the crossing");
        actions.doorLoaded = true;
        actions.tick = 4;
        task.execute();
        assertEquals(2, actions.doors);
    }

    @Test
    public void aCrossedDoorThatNeverReturnsOnlyHoldsTheRetryWindow()
    {
        FakeActions actions = new FakeActions();
        actions.position = TunnelMap.roomCenter(0);
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.doorLoaded = false;
        actions.position = TunnelMap.roomCenter(1);
        actions.tick = 7;
        task.execute();
        assertEquals(1, actions.doors);
        actions.tick = 8;
        task.execute();
        assertEquals(2, actions.doors);
    }

    @Test
    public void digWaitsUntilTheLastStepOntoTheMoundLands()
    {
        FakeActions actions = new FakeActions();
        actions.kills = 5;
        actions.position = Brother.DHAROK.getMound().dx(5);
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(Brother.DHAROK.getMound(), actions.walked);
        actions.position = Brother.DHAROK.getMound().dx(1);
        actions.tick = 1;
        task.execute();
        assertEquals(0, actions.digs, "still moving onto the mound");
        actions.idle = false;
        actions.tick = 2;
        task.execute();
        assertEquals(1, actions.digs, "a lagging walk pose does not delay the dig");
    }

    @Test
    public void protectionEndsInsideTheCryptBeforeClimbingOnceTheBrotherIsGone()
    {
        FakeActions actions = new FakeActions();
        actions.kills = 5;
        actions.position = Brother.AHRIM.getSarcophagus();
        actions.protection = Prayer.PROTECT_FROM_MAGIC;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertNull(actions.protection);
        assertTrue(actions.options.isEmpty());
        actions.tick = 1;
        task.execute();
        assertEquals(List.of("Climb-up"), actions.options);
    }

    @Test
    public void emptyVialIsDroppedBeforeSearchingAndTheDropFreesTheNextTick()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        actions.vials = 1;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(1, actions.drops);
        assertTrue(actions.options.isEmpty());
        actions.vials = 0;
        actions.slots = 29;
        actions.tick = 1;
        task.execute();
        assertEquals(List.of("Search"), actions.options);
    }

    @Test
    public void emptyVialStaysWhileFighting()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        actions.vials = 1;
        actions.interacting = liveNpc();
        new CryptTask(actions).execute();
        assertEquals(0, actions.drops);
    }

    @Test
    public void unchangedChestStillWaitsEightTicksBeforeRetrying()
    {
        FakeActions actions = new FakeActions();
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.tick = 7;
        task.execute();
        assertEquals(List.of("Open"), actions.options);
        actions.tick = 8;
        task.execute();
        assertEquals(List.of("Open", "Open"), actions.options);
    }

    @Test
    public void completedPuzzleModelsCanBeAnsweredOnNextTick()
    {
        FakeActions actions = new FakeActions();
        actions.puzzle = true;
        actions.firstModel = 6716;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(0, actions.widget);
        actions.answerModel = 6713;
        actions.tick = 1;
        task.execute();
        assertEquals(PuzzleModels.buttonId(0), actions.widget);
    }

    @Test
    public void observedProtectionOffAllowsSurfaceTravelOnNextTick()
    {
        FakeActions actions = new FakeActions();
        actions.position = Brother.AHRIM.getMound();
        actions.protection = Prayer.PROTECT_FROM_MELEE;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertNull(actions.walked);
        actions.tick = 1;
        task.execute();
        assertEquals(Brother.DHAROK.getMound(), actions.walked);
    }

    @Test
    public void observedRewardClosesBeforeSelectedJewelleryReturnAndCountsOnlyOnce()
    {
        FakeActions actions = new FakeActions();
        actions.bankEach = true;
        actions.rewards = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(1, task.getChests());
        assertEquals((155 << 16) | 11, actions.widget);
        assertFalse(actions.bankPending());
        actions.tick = 8;
        task.execute();
        assertFalse(actions.bankPending());
        assertEquals(0, actions.doors);
        actions.rewards = false;
        actions.tick = 16;
        task.observe();
        assertTrue(actions.bankPending());
        task.execute();
        assertEquals(0, actions.doors);
        assertNull(actions.walked);
        actions.rewards = true;
        task.observe();
        assertEquals(1, task.getChests());
    }

    @Test
    public void bankingDisabledKeepsLadderRoutingAfterRewardClose()
    {
        FakeActions actions = new FakeActions();
        actions.rewards = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.rewards = false;
        actions.tick = 8;
        task.execute();
        assertEquals(1, task.getChests());
        assertFalse(actions.bankPending());
        assertTrue(actions.doors > 0);
    }

    @Test
    public void sceneLoadRetainsObservedChestAndPendingFeroxReturn()
    {
        FakeActions actions = new FakeActions();
        actions.bankEach = true;
        actions.rewards = true;
        CryptTask task = new CryptTask(actions);
        task.observe();
        actions.rewards = false;
        task.observe();
        actions.reset();
        task.observe();
        assertTrue(actions.bankPending());
        assertEquals(1, task.getChests());
        task.execute();
        assertEquals(0, actions.doors);
    }

    @Test
    public void summonsFinalBrotherAndNeverSearchesBeforeAllSixSlainBits()
    {
        FakeActions actions = new FakeActions();
        actions.kills = 5;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(List.of("Open"), actions.options);
        actions.open = true;
        actions.tick = 8;
        task.execute();
        assertEquals(List.of("Open"), actions.options);
        actions.kills = 6;
        actions.tick = 16;
        task.execute();
        assertEquals(List.of("Open", "Search"), actions.options);
    }

    @Test
    public void observesDelayedRewardsOnceAndClosesBeforeLeaving()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(0, task.getChests());
        actions.rewards = true;
        actions.tick = 2;
        task.observe();
        assertEquals(1, task.getChests());
        actions.rewards = false;
        task.observe();
        actions.rewards = true;
        task.observe();
        assertEquals(1, task.getChests());
        actions.tick = 8;
        task.execute();
        assertEquals((155 << 16) | 11, actions.widget);
        assertNull(actions.walked);
    }

    @Test
    public void unavailableActionsHaveBoundedRetriesWithoutAnyDispatch()
    {
        FakeActions actions = new FakeActions();
        actions.accept = false;
        actions.open = true;
        CryptTask task = new CryptTask(actions);
        for (int tick = 0; tick <= 40; tick += 8)
        {
            actions.tick = tick;
            task.execute();
        }
        assertNotNull(actions.failure);
        assertTrue(actions.failure.contains("made no progress after five tries"));
        assertEquals(5, actions.options.size());
    }

    @Test
    public void farmsExteriorRoomsAndTravelsWhenNoMonsterIsAvailable()
    {
        FakeActions actions = new FakeActions();
        actions.position = TunnelMap.roomCenter(0);
        actions.potential = 650;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(1, actions.monsterRequests);
        assertTrue(actions.doors > 0 || actions.walked != null);
        assertFalse(actions.options.contains("Search"));
    }

    @Test
    public void resumesAllSlainRunBySearchingCryptsForTheLostEntrance()
    {
        FakeActions actions = new FakeActions();
        actions.position = Brother.DHAROK.getSarcophagus();
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(List.of("Search"), actions.options);
        actions.tick = 8;
        task.execute();
        assertEquals(List.of("Search", "Climb-up"), actions.options);
        actions.position = Brother.DHAROK.getMound();
        actions.tick = 16;
        task.execute();
        assertEquals(Brother.AHRIM.getMound(), actions.walked);
        assertNull(actions.failure);
    }

    @Test
    public void revisitsRecordedEntranceAfterItsBrotherHasBeenKilled()
    {
        FakeActions actions = new FakeActions();
        actions.position = Brother.DHAROK.getSarcophagus();
        actions.kills = 5;
        actions.choices = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.choices = false;
        actions.kills = 6;
        actions.tick = 8;
        task.execute();
        assertEquals(List.of("Search"), actions.options);
    }

    @Test
    public void exitClosesRewardInterfaceBeforeRoutingAndNeverReadsOldEntranceBits()
    {
        FakeActions actions = new FakeActions();
        CryptTask task = new CryptTask(actions);
        actions.rewards = true;
        assertFalse(task.escape());
        assertEquals((155 << 16) | 11, actions.widget);
        assertEquals(0, actions.doors);
        actions.rewards = false;
        actions.tick = 8;
        assertFalse(task.escape());
        assertTrue(actions.doors > 0);
        assertFalse(actions.varbits.contains(4734));
    }

    @Test
    public void disablesOnlyProtectionBeforeSurfaceTravel()
    {
        FakeActions actions = new FakeActions();
        actions.position = Brother.DHAROK.getMound();
        actions.protection = Prayer.PROTECT_FROM_MELEE;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertNull(actions.protection);
        assertNull(actions.walked);
        assertTrue(actions.options.isEmpty());
    }

    @Test
    public void rewardTargetNeverRequiresExceedingTheRunePotentialCap()
    {
        FakeActions actions = new FakeActions();
        actions.target = 880;
        actions.potential = 850;
        actions.open = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(0, actions.monsterRequests);
        assertEquals(List.of("Search"), actions.options);
    }

    @Test
    public void stalledFarmingFailsAndActualDamageExtendsTheDeadline()
    {
        FakeActions actions = new FakeActions();
        actions.potential = 650;
        actions.monsterHealth = 20;
        actions.interacting = (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class},
            (proxy, method, args) -> method.getName().equals("getHealthRatio") ? actions.monsterHealth
                : method.getName().equals("getIndex") ? 1
                : method.getName().equals("isDead") ? false
                : method.getName().equals("getIndex") ? 1 : null);
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.tick = 90;
        actions.monsterHealth = 15;
        task.execute();
        actions.tick = 100;
        task.execute();
        assertNull(actions.failure);
        actions.tick = 190;
        task.execute();
        assertNotNull(actions.failure);
        assertTrue(actions.failure.contains("No observed combat progress"));
    }

    @Test
    public void retargetsTheMonsterAttackingInsteadOfWaitingOnAnotherTarget()
    {
        FakeActions actions = new FakeActions();
        actions.potential = 650;
        actions.interacting = liveNpc();
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(0, actions.monsterRequests, "fights its own target while nothing else attacks");
        actions.attacker = liveNpc();
        actions.tick = 1;
        task.execute();
        assertEquals(1, actions.monsterRequests);
    }

    @Test
    public void aNewCombatTargetCountsAsProgressSoTheNextStepIsNotHeldEightTicks()
    {
        FakeActions actions = new FakeActions();
        actions.accept = false;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(List.of("Open"), actions.options);
        actions.interacting = liveNpc();
        actions.tick = 1;
        task.execute();
        assertEquals(List.of("Open", "Open"), actions.options);
    }

    private static NPC liveNpc()
    {
        return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class}, (proxy, method, args) ->
        {
            switch (method.getName())
            {
                case "isDead":
                    return false;
                case "getHealthRatio":
                    return -1;
                case "getIndex":
                    return 1;
                default:
                    return method.getReturnType() == int.class ? (Object) 0 : null;
            }
        });
    }

    @Test
    public void aLadderBehindADoorIsReachedThroughTheDoorFirst()
    {
        FakeActions actions = new FakeActions();
        actions.position = TunnelMap.roomCenter(3);
        actions.ladder = ladder(new WorldPoint(3534, 9678, 0));
        new CryptTask(actions).escape();
        assertFalse(actions.options.contains("Climb-up"));
        assertEquals(10, actions.lastDoor);
        assertEquals(6, actions.doorRoom);
    }

    @Test
    public void aLadderInTheCurrentRoomIsClimbed()
    {
        FakeActions actions = new FakeActions();
        actions.position = TunnelMap.roomCenter(6);
        actions.ladder = ladder(new WorldPoint(3534, 9678, 0));
        new CryptTask(actions).escape();
        assertEquals(List.of("Climb-up"), actions.options);
    }

    private static GameObject ladder(WorldPoint location)
    {
        return (GameObject) Proxy.newProxyInstance(GameObject.class.getClassLoader(), new Class<?>[] {GameObject.class},
            (proxy, method, args) -> method.getName().equals("getWorldLocation") ? location
                : method.getReturnType() == int.class ? (Object) 0 : null);
    }

    @Test
    public void longCorridorChoosesTheDoorAtTheUsefulEnd()
    {
        FakeActions actions = new FakeActions();
        actions.position = new WorldPoint(3552, 9726, 0);
        actions.corridor = true;
        CryptTask task = new CryptTask(actions);
        task.execute();
        assertEquals(0, actions.lastDoor);
        assertEquals(2, actions.doorRoom);
        assertNull(actions.failure);
    }

    @Test
    public void aFullInventorySearchesTheChestWithoutEatingOrStopping()
    {
        FakeActions actions = new FakeActions();
        actions.slots = 0;
        actions.health = 99;
        actions.food = 10;
        actions.open = true;
        new CryptTask(actions).execute();
        assertEquals(List.of("Search"), actions.options);
        assertEquals(0, actions.eats);
        assertNull(actions.failure);
    }

    @Test
    public void overflowLootIsPickedUpAfterEatingAndOnlyThenBanks()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        actions.bankEach = true;
        actions.slots = 0;
        actions.food = 1;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.rewards = true;
        actions.tick = 1;
        task.observe();
        actions.rewards = false;
        actions.ground = 2;
        actions.tick = 2;
        task.execute();
        assertFalse(actions.bankPending(), "loot on the ground holds the bank trip");
        assertEquals(1, actions.eats);
        actions.slots = 1;
        actions.food = 0;
        actions.tick = 3;
        task.execute();
        assertEquals(1, actions.pickups);
        actions.slots = 0;
        actions.ground = 1;
        actions.vials = 1;
        actions.tick = 4;
        task.execute();
        assertEquals(1, actions.drops, "a vial goes before food");
        actions.vials = 0;
        actions.slots = 1;
        actions.tick = 5;
        task.execute();
        assertEquals(2, actions.pickups);
        actions.ground = 0;
        actions.tick = 6;
        task.execute();
        assertTrue(actions.bankPending());
        assertNull(actions.failure);
    }

    @Test
    public void overflowLootWithNothingToClearStopsInsteadOfLeavingIt()
    {
        FakeActions actions = new FakeActions();
        actions.open = true;
        actions.slots = 0;
        CryptTask task = new CryptTask(actions);
        task.execute();
        actions.rewards = true;
        actions.tick = 1;
        task.observe();
        actions.rewards = false;
        actions.ground = 1;
        actions.tick = 2;
        task.execute();
        assertTrue(actions.failure.contains("no food or vial"));
    }

    private static final class FakeActions extends BarrowsActions
    {
        private int tick;
        private int kills = 6;
        private int potential = 800;
        private int monsterRequests;
        private int dialogueRequests;
        private int firstModel = -1;
        private int answerModel = -1;
        private int doors;
        private boolean doorLoaded = true;
        private int digs;
        private int widget;
        private int target = 756;
        private int slots = 28;
        private int food;
        private int eats;
        private int vials;
        private int drops;
        private int ground;
        private int pickups;
        private int health = 50;
        private int monsterHealth;
        private int lastDoor = -1;
        private int doorRoom = -1;
        private boolean open;
        private boolean rewards;
        private boolean choices;
        private boolean puzzle;
        private boolean accept = true;
        private boolean bankEach;
        private boolean corridor;
        private GameObject ladder;
        private NPC interacting;
        private NPC attacker;
        private boolean idle = true;
        private int tab;
        private boolean closeButton = true;
        private String failure;
        private Prayer protection;
        private WorldPoint position = TunnelMap.roomCenter(4);
        private WorldPoint walked;
        private final List<String> options = new ArrayList<>();
        private final List<Integer> varbits = new ArrayList<>();
        private final BarrowsConfig config = new BarrowsConfig()
        {
            @Override
            public boolean bankEachChest()
            {
                return bankEach;
            }

            @Override
            public int rewardTarget()
            {
                return target;
            }
        };

        @Override
        public BarrowsConfig config()
        {
            return config;
        }

        @Override
        public boolean ready()
        {
            return failure == null;
        }

        @Override
        public int tick()
        {
            return tick;
        }

        @Override
        public WorldPoint position()
        {
            return position;
        }

        @Override
        public Player player()
        {
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> method.getName().equals("getInteracting") ? interacting : null);
        }

        @Override
        public boolean inside()
        {
            return Brother.inTomb(position) != null || TunnelMap.contains(position);
        }

        @Override
        public int killedCount()
        {
            return kills;
        }

        @Override
        public boolean killed(Brother brother)
        {
            return kills == 6 || brother != Brother.DHAROK;
        }

        @Override
        public int potential()
        {
            return potential;
        }

        @Override
        public int currentTunnelRoom()
        {
            return corridor ? -1 : TunnelMap.nearestRoom(position);
        }

        @Override
        public boolean visible(int id)
        {
            return rewards && id == (155 << 16) || choices && id == ((219 << 16) | 1)
                || puzzle && id == PuzzleModels.FIRST_MODEL_WIDGET;
        }

        @Override
        public int model(int id)
        {
            return id == PuzzleModels.FIRST_MODEL_WIDGET ? firstModel
                : id == PuzzleModels.modelId(0) ? answerModel : -1;
        }

        @Override
        public int varbit(int id)
        {
            varbits.add(id);
            return id == 1394 && open ? 1 : 0;
        }

        @Override
        public NPC brotherNpc()
        {
            return null;
        }

        @Override
        public int openTab()
        {
            return tab;
        }

        @Override
        public boolean rewardsCloseVisible()
        {
            return rewards && closeButton;
        }

        @Override
        public boolean closeRewards(Runnable callback)
        {
            widget = (155 << 16) | 11;
            callback.run();
            return true;
        }

        @Override
        public boolean idle()
        {
            return idle;
        }

        @Override
        public NPC tunnelAttacker()
        {
            return attacker;
        }

        @Override
        public boolean prayerActive(Prayer prayer)
        {
            return protection == prayer;
        }

        @Override
        public boolean protect(Prayer prayer, boolean active, Runnable callback)
        {
            protection = active ? prayer : null;
            callback.run();
            return true;
        }

        @Override
        public int freeSlots()
        {
            return slots;
        }

        @Override
        public int foodCount()
        {
            return food;
        }

        @Override
        public int count(int id)
        {
            return id == BarrowsActions.EMPTY_VIAL ? vials : 0;
        }

        @Override
        public boolean lootOnGround()
        {
            return ground > 0;
        }

        @Override
        public boolean pickUpLoot(Runnable callback)
        {
            pickups++;
            callback.run();
            return true;
        }

        @Override
        public boolean inventory(int id, String option, Runnable callback)
        {
            if (id == BarrowsActions.EMPTY_VIAL)
            {
                assertEquals("Drop", option);
                drops++;
                callback.run();
                return true;
            }
            if (id == 952)
            {
                assertEquals("Dig", option);
                digs++;
                callback.run();
                return true;
            }
            assertEquals(config.foodId(), id);
            assertEquals("Eat", option);
            eats++;
            callback.run();
            return true;
        }

        @Override
        public int hp()
        {
            return health;
        }

        @Override
        public int maxHp()
        {
            return 99;
        }

        @Override
        public GameObject object(int... ids)
        {
            return null;
        }

        @Override
        public GameObject namedObject(String name)
        {
            return name.equals("Ladder") ? ladder : null;
        }

        @Override
        public boolean objectAction(GameObject target, String option, Runnable callback)
        {
            options.add(option);
            if (accept)
            {
                callback.run();
            }
            return accept;
        }

        @Override
        public boolean widget(int id, String option, BooleanSupplier allowed, Runnable callback)
        {
            widget = id;
            callback.run();
            return true;
        }

        @Override
        public boolean dialogueOption(int index, BooleanSupplier allowed, Runnable callback)
        {
            dialogueRequests++;
            callback.run();
            return true;
        }

        @Override
        public boolean gear(String items, Runnable callback)
        {
            return true;
        }

        @Override
        public boolean tunnelMonster(Runnable callback)
        {
            monsterRequests++;
            return false;
        }

        @Override
        public WorldPoint doorDestination(int index)
        {
            return position;
        }

        @Override
        public WorldPoint doorDestination(int index, int room)
        {
            return corridor ? index == 0 ? TunnelMap.roomCenter(room) : null : position;
        }

        @Override
        public boolean doorLoaded(WorldPoint point)
        {
            return doorLoaded;
        }

        @Override
        public boolean door(int index, Runnable callback)
        {
            doors++;
            callback.run();
            return true;
        }

        @Override
        public boolean door(int index, int room, Runnable callback)
        {
            lastDoor = index;
            doorRoom = room;
            return door(index, callback);
        }

        @Override
        public boolean walk(WorldPoint point, String label)
        {
            walked = point;
            return false;
        }

        @Override
        public void fail(String text)
        {
            failure = text;
        }
    }
}
