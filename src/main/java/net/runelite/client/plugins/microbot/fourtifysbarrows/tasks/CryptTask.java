package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.PuzzleModels;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.TunnelMap;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;

public final class CryptTask implements FourtifysTask
{
    private final BarrowsActions actions;
    private Brother tunnel;
    private static final int RETRY_TICKS = 8;
    private int nextAttempt;
    private int attempts;
    private String operation = "";
    private WorldPoint lastPosition;
    private int positionTick = Integer.MIN_VALUE / 2;
    private int roomTick = Integer.MIN_VALUE / 2;
    private WorldPoint crossingDoor;
    private int crossingTick;
    private int lastKills;
    private int lastPotential;
    private long chests;
    private boolean looted;
    private boolean rewardSeen;
    private static final int[] CORNER_ROOMS = {0, 2, 8, 6};
    private final EnumSet<Brother> checkedCrypts = EnumSet.noneOf(Brother.class);
    private Brother searchedCrypt;
    private int searchedTick;
    private int exitCorner;
    private int exitRoomsSearched;
    private int farmingCorner;
    private NPC farmingNpc;
    private int farmingHealth = -1;
    private int farmingProgressTick;
    private ActionProgress lastActionProgress;
    private int lastRequestTick = Integer.MIN_VALUE / 2;

    /** A snapshot of crypt progress; two snapshots are equal when every value matches. */
    private static final class ActionProgress
    {
        private final int room;
        private final List<Object> values;

        private ActionProgress(Brother tomb, int room, int kills, int potential, int chest,
            int freeSlots, int protections, int equipped, boolean rewards,
            boolean choices, boolean puzzle, int first, int a, int b, int c, int tab,
            int target, int rejected)
        {
            this.room = room;
            this.values = Arrays.asList(tomb, room, kills, potential, chest, freeSlots, protections, equipped,
                rewards, choices, puzzle, first, a, b, c, tab, target, rejected);
        }

        private int room()
        {
            return room;
        }

        @Override
        public boolean equals(Object other)
        {
            return other instanceof ActionProgress && values.equals(((ActionProgress) other).values);
        }

        @Override
        public int hashCode()
        {
            return values.hashCode();
        }
    }

    public CryptTask(BarrowsActions actions)
    {
        this.actions = actions;
    }

    @Override
    public boolean isValid()
    {
        return actions.ready();
    }

    @Override
    public String getStatus()
    {
        return actions.getStatus();
    }

    public long getChests()
    {
        return chests;
    }

    public void observe()
    {
        boolean rewards = actions.visible((155 << 16) | 0);
        if (rewards && !rewardSeen && !looted)
        {
            chests++;
            looted = true;
        }
        rewardSeen = rewards;
        if (looted && !rewards && actions.inside() && actions.config().bankEachChest() && !actions.lootOnGround())
        {
            actions.requestBank();
        }
        if (!actions.inside() && looted)
        {
            looted = false;
            tunnel = null;
            checkedCrypts.clear();
            searchedCrypt = null;
            exitCorner = 0;
            exitRoomsSearched = 0;
            if (actions.config().bankEachChest())
            {
                actions.requestBank();
            }
        }
        if (lastPosition != null && !lastPosition.equals(actions.position()))
        {
            positionTick = actions.tick();
        }
        if (lastPosition == null || !lastPosition.equals(actions.position()) || lastKills != actions.killedCount()
            || lastPotential != actions.potential())
        {
            attempts = 0;
            operation = "";
            lastPosition = actions.position();
            lastKills = actions.killedCount();
            lastPotential = actions.potential();
        }
        if (actions.ready())
        {
            observeActionProgress(rewards);
        }
    }

    private void observeActionProgress(boolean rewards)
    {
        int protections = (actions.prayerActive(Prayer.PROTECT_FROM_MAGIC) ? 1 : 0)
            | (actions.prayerActive(Prayer.PROTECT_FROM_MISSILES) ? 2 : 0)
            | (actions.prayerActive(Prayer.PROTECT_FROM_MELEE) ? 4 : 0);
        int equipped = 0;
        for (int id : BarrowsActions.gearIds(actions.config().meleeGear()))
        {
            if (actions.wearing(id))
            {
                equipped++;
            }
        }
        boolean puzzle = actions.visible(PuzzleModels.FIRST_MODEL_WIDGET);
        ActionProgress progress = new ActionProgress(Brother.inTomb(actions.position()),
            TunnelMap.contains(actions.position()) ? actions.currentTunnelRoom() : -2,
            actions.killedCount(), actions.potential(), actions.varbit(1394), actions.freeSlots(), protections, equipped,
            rewards, actions.visible(BarrowsActions.DIALOGUE_OPTIONS), puzzle,
            puzzle ? actions.model(PuzzleModels.FIRST_MODEL_WIDGET) : -1,
            puzzle ? actions.model(PuzzleModels.modelId(0)) : -1,
            puzzle ? actions.model(PuzzleModels.modelId(1)) : -1,
            puzzle ? actions.model(PuzzleModels.modelId(2)) : -1,
            actions.openTab(),
            actions.player().getInteracting() instanceof NPC ? ((NPC) actions.player().getInteracting()).getIndex() : -1,
            actions.rejectedTargetIndex());
        if (lastActionProgress != null && lastActionProgress.room() != progress.room())
        {
            roomTick = actions.tick();
        }
        if (lastActionProgress != null && !lastActionProgress.equals(progress))
        {
            nextAttempt = Math.min(nextAttempt, Math.max(actions.tick(), lastRequestTick + 1));
            attempts = 0;
            operation = "";
        }
        lastActionProgress = progress;
    }

    @Override
    public void execute()
    {
        observe();
        if (!actions.ready() || actions.tick() < nextAttempt)
        {
            return;
        }
        if (actions.bankPending())
        {
            actions.status("Return to Ferox bank with Ring of dueling");
            return;
        }
        if (looted)
        {
            escape();
            return;
        }
        if (actions.visible(BarrowsActions.DIALOGUE_OPTIONS))
        {
            Brother crypt = Brother.inTomb(actions.position());
            if (crypt != null)
            {
                tunnel = crypt;
                if (actions.killedCount() >= 5)
                {
                    request("Enter tunnels", () -> actions.dialogueOption(1, () -> actions.killedCount() >= 5, this::dispatched));
                }
                else
                {
                    request("Decline tunnel entry", () -> actions.dialogueOption(2, () -> true, this::dispatched));
                }
                return;
            }
        }
        if (TunnelMap.contains(actions.position()))
        {
            tunnels();
            return;
        }
        Brother crypt = Brother.inTomb(actions.position());
        if (crypt != null)
        {
            if (tunnel == null && actions.killedCount() == 6)
            {
                if (searchedCrypt == crypt && actions.tick() >= searchedTick + 8)
                {
                    checkedCrypts.add(crypt);
                    searchedCrypt = null;
                    climb(crypt);
                }
                else if (searchedCrypt != crypt)
                {
                    GameObject coffin = actions.namedObject("Sarcophagus");
                    request("Find tunnel in " + crypt.getNpcName() + " crypt", () -> actions.objectAction(coffin, "Search", () ->
                    {
                        searchedCrypt = crypt;
                        searchedTick = actions.tick();
                        dispatched();
                    }));
                }
            }
            else if (actions.killed(crypt) && crypt != tunnel || crypt == tunnel && actions.killedCount() < 5)
            {
                climb(crypt);
            }
            else
            {
                GameObject coffin = actions.namedObject("Sarcophagus");
                if (coffin == null)
                {
                    coffin = actions.namedObject("Coffin");
                }
                GameObject target = coffin;
                request("Search " + crypt.getNpcName() + " sarcophagus", () -> actions.objectAction(target, "Search", this::dispatched));
            }
            return;
        }
        if (disableProtection() || dropEmptyVial())
        {
            return;
        }
        Brother next = null;
        for (Brother brother : Brother.values())
        {
            if (!actions.killed(brother) && brother != tunnel)
            {
                next = brother;
                break;
            }
        }
        if (next == null)
        {
            next = tunnel;
        }
        if (next == null)
        {
            for (Brother brother : Brother.values())
            {
                if (!checkedCrypts.contains(brother))
                {
                    next = brother;
                    break;
                }
            }
            if (next == null)
            {
                actions.fail("No tunnel found after searching all six sarcophagi; enable the plugin again to retry");
                return;
            }
        }
        Brother destination = next;
        if (actions.position().distanceTo(next.getMound()) > 1)
        {
            actions.walk(next.getMound(), next.getNpcName() + " mound");
            return;
        }
        if (actions.tick() <= positionTick)
        {
            // Digging needs only a stopped player; the walking pose can lag a tick behind.
            actions.status("Stop on " + destination.getNpcName() + " mound");
            return;
        }
        request("Dig into " + destination.getNpcName() + " crypt", () -> actions.inventory(952, "Dig", this::dispatched));
    }

    private void tunnels()
    {
        if (solvePuzzle())
        {
            return;
        }
        if (actions.killedCount() < 5)
        {
            escape();
            return;
        }
        if (actions.killedCount() == 6 && needsPotential())
        {
            gatherPotential();
            return;
        }
        int room = actions.currentTunnelRoom();
        if (room != TunnelMap.CHEST_ROOM)
        {
            route(TunnelMap.CHEST_ROOM);
            return;
        }
        if (settling("Wait to stop at the chest"))
        {
            return;
        }
        GameObject chest = actions.object(20973, 20723, 20724);
        if (actions.killedCount() < 6)
        {
            if (actions.varbit(1394) == 0)
            {
                request("Open chest to summon final brother", () -> actions.objectAction(chest, "Open", this::dispatched));
            }
            else
            {
                request("Wait for final brother", () -> false);
            }
            return;
        }
        if (dropEmptyVial())
        {
            return;
        }
        String option = actions.varbit(1394) == 0 ? "Open" : "Search";
        request(option + " Barrows chest", () -> actions.objectAction(chest, option, this::dispatched));
    }

    private void route(int goal)
    {
        int room = actions.currentTunnelRoom();
        if (room == goal)
        {
            actions.walk(TunnelMap.roomCenter(goal), "tunnel room centre");
            return;
        }
        boolean[] open = new boolean[TunnelMap.DOOR_COUNT];
        for (int index = 0; index < open.length; index++)
        {
            open[index] = actions.varbit(TunnelMap.doorVarbit(index)) == 0;
        }
        int door = TunnelMap.firstDoorOnRoute(room, goal, index -> open[index]);
        int destinationRoom = door < 0 ? -1 : TunnelMap.otherRoom(door, room);
        if (room < 0)
        {
            int best = Integer.MAX_VALUE;
            int bestDistance = Integer.MAX_VALUE;
            for (int index = 0; index < TunnelMap.DOOR_COUNT; index++)
            {
                if (!open[index])
                {
                    continue;
                }
                for (int endpoint = 0; endpoint < TunnelMap.ROOM_COUNT; endpoint++)
                {
                    if (TunnelMap.otherRoom(index, endpoint) < 0)
                    {
                        continue;
                    }
                    int remaining = routeLength(endpoint, goal, open);
                    if (remaining == Integer.MAX_VALUE)
                    {
                        continue;
                    }
                    WorldPoint candidate = actions.doorDestination(index, endpoint);
                    int distance = candidate == null ? Integer.MAX_VALUE : actions.position().distanceTo(candidate);
                    if (candidate != null && (remaining < best || remaining == best && distance < bestDistance))
                    {
                        best = remaining;
                        bestDistance = distance;
                        door = index;
                        destinationRoom = endpoint;
                    }
                }
            }
        }
        if (door < 0 || destinationRoom < 0)
        {
            request("Find a traversable tunnel route", () -> false);
            return;
        }
        int selectedDoor = door;
        int selectedRoom = destinationRoom;
        WorldPoint point = actions.doorDestination(selectedDoor, selectedRoom);
        if (point == null)
        {
            request("Find the next reachable tunnel door", () -> false);
        }
        else if (actions.position().distanceTo(point) > 20)
        {
            actions.walk(point, "tunnel door");
        }
        else if (!settling("Wait for door crossing to finish"))
        {
            request("Open tunnel door", () -> crossDoor(selectedDoor, selectedRoom, point));
        }
    }

    private int routeLength(int from, int goal, boolean[] open)
    {
        int room = from;
        for (int length = 0; length < TunnelMap.ROOM_COUNT; length++)
        {
            if (room == goal)
            {
                return length;
            }
            int door = TunnelMap.firstDoorOnRoute(room, goal, index -> open[index]);
            if (door < 0)
            {
                return Integer.MAX_VALUE;
            }
            room = TunnelMap.otherRoom(door, room);
        }
        return Integer.MAX_VALUE;
    }

    public boolean escape()
    {
        if (actions.visible((155 << 16) | 0))
        {
            if (!actions.rewardsCloseVisible())
            {
                actions.status("Wait for the rewards Close button");
            }
            else if (actions.tick() >= nextAttempt)
            {
                request("Close Barrows rewards", () -> actions.closeRewards(this::dispatched));
            }
            return false;
        }
        if (actions.lootOnGround())
        {
            pickUpOverflow();
            return false;
        }
        if (!actions.inside())
        {
            return !disableProtection();
        }
        if (actions.tick() < nextAttempt)
        {
            return false;
        }
        Brother crypt = Brother.inTomb(actions.position());
        if (crypt != null)
        {
            climb(crypt);
            return false;
        }
        if (solvePuzzle())
        {
            return false;
        }
        GameObject ladder = actions.namedObject("Ladder");
        if (ladder != null)
        {
            int ladderRoom = TunnelMap.nearestRoom(ladder.getWorldLocation());
            if (ladderRoom >= 0 && actions.currentTunnelRoom() != ladderRoom)
            {
                // A ladder seen from a corridor or another room lies behind a door.
                route(ladderRoom);
                return false;
            }
            if (actions.position().distanceTo(ladder.getWorldLocation()) > 20)
            {
                actions.walk(ladder.getWorldLocation(), "exit ladder");
                return false;
            }
            GameObject target = ladder;
            request("Climb out of tunnels", () -> actions.objectAction(target, "Climb-up", this::dispatched));
            return false;
        }
        int goal = CORNER_ROOMS[exitCorner];
        if (actions.currentTunnelRoom() == goal)
        {
            if (actions.position().distanceTo(TunnelMap.roomCenter(goal)) > 2)
            {
                actions.walk(TunnelMap.roomCenter(goal), "search for tunnel exit ladder");
                return false;
            }
            exitCorner = (exitCorner + 1) % CORNER_ROOMS.length;
            exitRoomsSearched++;
            if (exitRoomsSearched >= CORNER_ROOMS.length)
            {
                actions.fail("Could not find the tunnel exit ladder; enable the plugin again to retry");
                return false;
            }
            goal = CORNER_ROOMS[exitCorner];
        }
        route(goal);
        return false;
    }

    /** Rewards that did not fit fell on our tile; make room by eating or dropping a vial, then take them. */
    private void pickUpOverflow()
    {
        if (actions.tick() < nextAttempt)
        {
            return;
        }
        if (actions.freeSlots() > 0)
        {
            request("Pick up chest rewards", () -> actions.pickUpLoot(this::dispatched));
        }
        else if (actions.count(BarrowsActions.EMPTY_VIAL) > 0)
        {
            request("Drop empty vial for loot", () -> actions.inventory(BarrowsActions.EMPTY_VIAL, "Drop", this::dispatched));
        }
        else if (actions.foodCount() > 0)
        {
            request("Eat for loot space", () -> actions.inventory(actions.config().foodId(), "Eat", this::dispatched));
        }
        else
        {
            // Tunnel monsters attack an idle player, so leave the rewards rather than stop in the tunnels.
            actions.fail("Chest rewards were left on the ground because your inventory was full; "
                + "bring fewer supplies or more food so the rewards fit, then enable the plugin again");
        }
    }

    /** Drops one empty vial when not fighting; an inventory action would interrupt an attack. */
    private boolean dropEmptyVial()
    {
        if (actions.count(BarrowsActions.EMPTY_VIAL) == 0 || actions.player().getInteracting() != null || actions.brotherNpc() != null)
        {
            return false;
        }
        request("Drop empty vial", () -> actions.inventory(BarrowsActions.EMPTY_VIAL, "Drop", this::dispatched));
        return true;
    }

    private boolean crossDoor(int door, int room, WorldPoint doorTile)
    {
        boolean queued = actions.door(door, room, this::dispatched);
        if (queued)
        {
            crossingDoor = doorTile;
            crossingTick = actions.tick();
        }
        return queued;
    }

    private boolean settling(String label)
    {
        // The game drops clicks while a door is being crossed: it removes the door, then sends it again
        // when the crossing ends. A room change can show up before that, so wait for the door as well.
        if (crossingDoor != null)
        {
            if (actions.tick() - crossingTick < RETRY_TICKS && !actions.doorLoaded(crossingDoor))
            {
                actions.status(label);
                return true;
            }
            crossingDoor = null;
        }
        // Clicks sent during another action animation are dropped too; walking is fine.
        if (actions.tick() > roomTick && actions.idle())
        {
            return false;
        }
        actions.status(label);
        return true;
    }

    private void climb(Brother crypt)
    {
        // A crypt holds only its brother, so protection can end before the climb once he is dead.
        if (actions.killed(crypt) && actions.brotherNpc() == null && disableProtection())
        {
            return;
        }
        GameObject stairs = actions.namedObject("Staircase");
        request("Leave " + crypt.getNpcName() + " crypt", () -> actions.objectAction(stairs, "Climb-up", this::dispatched));
    }

    private boolean disableProtection()
    {
        Prayer prayer = actions.activeProtection();
        if (prayer == null)
        {
            return false;
        }
        if (actions.tick() >= nextAttempt)
        {
            request("Disable protection prayer", () -> actions.protect(prayer, false, this::dispatched));
        }
        return true;
    }

    private void request(String label, BooleanSupplier action)
    {
        actions.status(label);
        if (!operation.equals(label))
        {
            operation = label;
            attempts = 0;
        }
        if (attempts >= 5)
        {
            actions.fail("Gave up after five tries: " + Character.toLowerCase(label.charAt(0)) + label.substring(1)
                + "; enable the plugin again to retry");
            return;
        }
        attempts++;
        lastRequestTick = actions.tick();
        nextAttempt = actions.tick() + RETRY_TICKS;
        action.getAsBoolean();
    }

    private boolean solvePuzzle()
    {
        if (!actions.visible(PuzzleModels.FIRST_MODEL_WIDGET))
        {
            return false;
        }
        int first = actions.model(PuzzleModels.FIRST_MODEL_WIDGET);
        int answer = PuzzleModels.answerIndex(first, actions.model(PuzzleModels.modelId(0)),
            actions.model(PuzzleModels.modelId(1)), actions.model(PuzzleModels.modelId(2)));
        if (answer < 0)
        {
            request("Wait for complete puzzle models", () -> false);
            return true;
        }
        int expected = first - 3;
        request("Solve tunnel puzzle", () -> actions.widget(PuzzleModels.buttonId(answer), "Select", () -> actions.visible(PuzzleModels.FIRST_MODEL_WIDGET)
                && actions.model(PuzzleModels.FIRST_MODEL_WIDGET) == first
                && actions.model(PuzzleModels.modelId(answer)) == expected, this::dispatched));
        return true;
    }

    private void gatherPotential()
    {
        NPC attacker = actions.tunnelAttacker();
        if (actions.player().getInteracting() instanceof NPC
            && !actions.player().getInteracting().isDead()
            && (attacker == null || attacker == actions.player().getInteracting()))
        {
            NPC npc = (NPC) actions.player().getInteracting();
            int health = npc.getHealthRatio();
            if (npc != farmingNpc || health >= 0 && (farmingHealth < 0 || health < farmingHealth))
            {
                farmingNpc = npc;
                farmingHealth = health;
                farmingProgressTick = actions.tick();
            }
            else if (actions.tick() - farmingProgressTick >= (health >= 0 ? 100 : 150))
            {
                actions.fail("No observed combat progress while gathering reward potential; check weapon and ammunition");
                return;
            }
            actions.status("Fighting " + npc.getName() + " for reward potential");
            return;
        }
        farmingNpc = null;
        farmingHealth = -1;
        if (!actions.gear(actions.config().meleeGear(), this::dispatched))
        {
            return;
        }
        if (settling("Wait for door crossing to finish"))
        {
            return;
        }
        if (actions.tunnelMonster(this::dispatched))
        {
            actions.status(attacker == null ? "Attacking a tunnel monster for reward potential" : "Attacking " + attacker.getName() + " for reward potential");
            return;
        }
        int room = actions.currentTunnelRoom();
        if (room == CORNER_ROOMS[farmingCorner])
        {
            farmingCorner = (farmingCorner + 1) % CORNER_ROOMS.length;
        }
        route(CORNER_ROOMS[farmingCorner]);
    }

    private boolean needsPotential()
    {
        return actions.potential() < actions.config().rewardTarget() && actions.potential() + 43 <= 880;
    }

    private void dispatched()
    {
        lastRequestTick = actions.tick();
        nextAttempt = actions.tick() + RETRY_TICKS;
    }
}
