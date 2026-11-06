package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.CombatStyle;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;

/**
 * Banks at Ferox Enclave: deposits loot, withdraws each missing supply in one request, closes the bank and drinks from
 * the Pool of Refreshment. The pool turns prayers off, so it comes after banking.
 */
public final class BankTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 2;
    private static final int WALK_TICKS = 10;
    private static final int STILL_TICKS = 2;
    private static final int BANK_REACH = 8;
    private static final int POOL_REACH = 6;
    private static final List<Integer> RESTORE_WITHDRAW_ORDER = List.of(2434, 3024, 139, 3026, 141, 3028, 143, 3030);
    private final DemonActions actions;
    private final TormentedDemonsConfig config;
    private final TripState trip;
    private final Walking walking;
    private final IntSupplier tick;
    private final Map<CombatStyle, Set<Integer>> sets;
    private final Set<Integer> keep;
    private final Map<Integer, Integer> restock;
    private final Consumer<String> tell;
    private boolean stocked;
    private boolean pinSent;
    private boolean pinAsked;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private WorldPoint lastPosition;
    private int lastMoveTick = Integer.MIN_VALUE / 2;
    private String status = "Banking";

    public BankTask(DemonActions actions, TormentedDemonsConfig config, TripState trip, Walking walking, IntSupplier tick,
                    Map<CombatStyle, Set<Integer>> sets, Set<Integer> keep, Map<Integer, Integer> restock,
                    Consumer<String> tell)
    {
        this.actions = actions;
        this.config = config;
        this.trip = trip;
        this.walking = walking;
        this.tick = tick;
        this.sets = sets;
        this.keep = keep;
        this.restock = restock;
        this.tell = tell;
    }

    /** Items banking leaves in the inventory. */
    public Set<Integer> keptItems()
    {
        Set<Integer> result = new LinkedHashSet<>(keep);
        result.add(config.foodId());
        result.add(TempleData.TEMPLE_TELEPORT);
        result.addAll(TempleData.PRAYER_RESTORES);
        result.addAll(TempleData.DUELING_RINGS);
        result.addAll(restock.keySet());
        for (Set<Integer> ids : sets.values())
        {
            result.addAll(ids);
        }
        return result;
    }

    @Override
    public boolean isValid()
    {
        return trip.getPhase() == TripPhase.BANKING && TempleData.atFerox(actions.position());
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        int now = tick.getAsInt();
        WorldPoint position = actions.position();
        if (position != null && !position.equals(lastPosition))
        {
            if (lastPosition != null)
            {
                lastMoveTick = now;
            }
            lastPosition = position;
        }
        if (now - requestedAt < OBSERVE_TICKS)
        {
            return;
        }
        if (actions.bankPinShown())
        {
            // One try with the saved PIN: a wrong one typed again would lock the bank for a while.
            if (pinSent)
            {
                // The overlay may be off or the player away, so the wait is also told once in the chat.
                if (!pinAsked)
                {
                    pinAsked = true;
                    tell.accept("Waiting for your bank PIN: type it in the bank window or save it in your Microbot profile;"
                        + " the plugin continues after the bank opens");
                }
                status = "Enter your bank PIN to continue";
                return;
            }
            status = "Entering the bank PIN";
            actions.enterBankPin(() ->
            {
                requestedAt = now;
                pinSent = true;
            });
            return;
        }
        if (actions.bankOpen())
        {
            pinSent = false;
            pinAsked = false;
            restockStep(now);
            return;
        }
        if (!stocked)
        {
            if (walkNear(TempleData.FEROX_BANK, BANK_REACH, "the Ferox Enclave bank") && !walkingToObject(now))
            {
                status = "Opening the bank";
                actions.openBank(() -> requestedAt = now);
            }
            return;
        }
        if (!actions.restored())
        {
            TileObject pool = actions.object(Set.of(TempleData.FEROX_POOL_OBJECT));
            if (walkNear(TempleData.FEROX_POOL, POOL_REACH, "the Pool of Refreshment") && pool != null && !walkingToObject(now))
            {
                status = "Drinking from the Pool of Refreshment";
                actions.object(pool, "Drink", () -> requestedAt = now);
            }
            return;
        }
        walking.cancel();
        stocked = false;
        trip.tripStarted();
        trip.setPhase(TripPhase.TRAVELLING);
        status = "Supplies ready";
    }

    /** Opening the bank or drinking walks to the object first; the request is not sent again during that walk. */
    private boolean walkingToObject(int now)
    {
        return now - requestedAt < WALK_TICKS && now - lastMoveTick < STILL_TICKS;
    }

    private boolean walkNear(WorldPoint point, int reach, String label)
    {
        WorldPoint position = actions.position();
        if (position != null && position.distanceTo(point) <= reach)
        {
            walking.cancel();
            return true;
        }
        status = "Walking to " + label;
        Walking.State state = walking.walkTo(point);
        if (state == Walking.State.FAILED)
        {
            actions.fail("Could not walk to " + label + "; walk there yourself, then restart the plugin");
        }
        return false;
    }

    private void restockStep(int now)
    {
        Runnable observed = () -> requestedAt = now;
        Set<Integer> kept = keptItems();
        for (int id : actions.carriedIds())
        {
            if (!kept.contains(id))
            {
                status = "Depositing loot";
                actions.deposit(id, observed);
                return;
            }
        }
        if (actions.duelingRing() <= 0)
        {
            int ring = TempleData.DUELING_RINGS.stream().filter(id -> actions.bankCount(id) > 0).findFirst().orElse(-1);
            if (ring <= 0)
            {
                actions.fail("No Ring of dueling in the bank; add one, then restart the plugin");
                return;
            }
            status = "Withdrawing a Ring of dueling";
            actions.withdrawOne(ring, observed);
            return;
        }
        for (Set<Integer> ids : sets.values())
        {
            for (int id : ids)
            {
                if (!actions.wearing(id) && actions.count(id) == 0)
                {
                    if (actions.bankCount(id) == 0)
                    {
                        actions.fail("Missing " + actions.describe(id) + ": put it in your bank or remove it from your equipment IDs, then restart the plugin");
                        return;
                    }
                    status = "Withdrawing equipment";
                    actions.withdrawOne(id, observed);
                    return;
                }
            }
        }
        if (withdraw(config.foodId(), config.foodCount(), "food", observed)
            || withdrawRestore(observed)
            || withdraw(TempleData.TEMPLE_TELEPORT, config.scrollCount(), "Guthixian temple teleports", observed))
        {
            return;
        }
        for (Map.Entry<Integer, Integer> item : restock.entrySet())
        {
            if (withdraw(item.getKey(), item.getValue(), actions.describe(item.getKey()), observed))
            {
                return;
            }
        }
        stocked = true;
        status = "Closing the bank";
        actions.closeBank(observed);
    }

    /** Withdraws what is missing of the item, or what the bank has. Runs out quietly while some are carried. */
    private boolean withdraw(int id, int wanted, String label, Runnable observed)
    {
        int carried = actions.count(id);
        if (carried >= wanted)
        {
            return false;
        }
        int banked = actions.bankCount(id);
        if (banked > 0)
        {
            status = "Withdrawing " + label;
            actions.withdraw(id, amount(id, wanted - carried, banked), observed);
            return true;
        }
        if (carried == 0)
        {
            actions.fail("No " + label + " left in the bank; add more to the bank, then restart the plugin");
            return true;
        }
        return false;
    }

    private boolean withdrawRestore(Runnable observed)
    {
        int carried = actions.count(TempleData.PRAYER_RESTORES);
        if (carried >= config.restoreCount())
        {
            return false;
        }
        for (int id : RESTORE_WITHDRAW_ORDER)
        {
            int banked = actions.bankCount(id);
            if (banked > 0)
            {
                status = "Withdrawing prayer restores";
                actions.withdraw(id, amount(id, config.restoreCount() - carried, banked), observed);
                return true;
            }
        }
        if (carried == 0)
        {
            actions.fail("No prayer restores left in the bank; add more to the bank, then restart the plugin");
            return true;
        }
        return false;
    }

    /** The amount for one request: what is missing, no more than the bank has or, unstacked, the free slots. */
    private int amount(int id, int missing, int banked)
    {
        int amount = Math.min(missing, banked);
        return actions.stackable(id) ? amount : Math.max(1, Math.min(amount, actions.freeSlots()));
    }

    public void reset()
    {
        stocked = false;
        pinSent = false;
        pinAsked = false;
        requestedAt = Integer.MIN_VALUE / 2;
        lastPosition = null;
        lastMoveTick = Integer.MIN_VALUE / 2;
    }
}
