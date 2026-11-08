package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.config.PhosanisNightmareConfig;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Doses;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.DreamData;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripPhase;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.TripState;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;

/**
 * Banks at Ferox Enclave: deposits loot, withdraws each missing item and supply in one request, closes the bank
 * and drinks from the Pool of Refreshment. The pool turns prayers off and resets boosts, so it comes after
 * banking and the combat potion comes later.
 */
public final class BankTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 2;
    private static final int WALK_TICKS = 10;
    private static final int STILL_TICKS = 2;
    private static final int BANK_REACH = 8;
    private static final int POOL_REACH = 6;
    private final NightmareActions actions;
    private final PhosanisNightmareConfig config;
    private final TripState trip;
    private final Walking walking;
    private final IntSupplier tick;
    private final SuppliesTask supplies;
    private final Set<Integer> equipment;
    private final Set<Integer> keep;
    private final Consumer<String> tell;
    private boolean stocked;
    private boolean pinSent;
    private boolean pinAsked;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private WorldPoint lastPosition;
    private int lastMoveTick = Integer.MIN_VALUE / 2;
    private String status = "Banking";

    public BankTask(NightmareActions actions, PhosanisNightmareConfig config, TripState trip, Walking walking, IntSupplier tick,
        SuppliesTask supplies, Set<Integer> equipment, Set<Integer> keep, Consumer<String> tell)
    {
        this.actions = actions;
        this.config = config;
        this.trip = trip;
        this.walking = walking;
        this.tick = tick;
        this.supplies = supplies;
        this.equipment = equipment;
        this.keep = keep;
        this.tell = tell;
    }

    /** Items banking leaves in the inventory; every dose of the potions counts. */
    public Set<Integer> keptItems()
    {
        Set<Integer> result = new LinkedHashSet<>(keep);
        result.addAll(equipment);
        result.add(config.foodId());
        result.add(DreamData.MEDALLION);
        result.addAll(DreamData.DUELING_RINGS);
        result.addAll(Doses.SUPER_RESTORE);
        result.addAll(Doses.PRAYER_POTION);
        result.addAll(config.parasiteCure().getDoses());
        result.add(config.combatPotionId());
        result.addAll(supplies.combatPotions());
        if (config.ghostlyThrall())
        {
            result.add(DreamData.BOOK_OF_THE_DEAD);
        }
        return result;
    }

    @Override
    public boolean isValid()
    {
        return trip.getPhase() == TripPhase.BANKING && DreamData.atFerox(actions.position());
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
                    tell.accept("Waiting for your bank PIN: type it in the bank window, and check the PIN saved in your Microbot profile;"
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
            if (walkNear(DreamData.FEROX_BANK, BANK_REACH, "the Ferox Enclave bank") && !walkingToObject(now))
            {
                status = "Opening the bank";
                actions.openBank(() -> requestedAt = now);
            }
            return;
        }
        if (!actions.restored())
        {
            TileObject pool = actions.object(Set.of(DreamData.FEROX_POOL_OBJECT));
            if (walkNear(DreamData.FEROX_POOL, POOL_REACH, "the Pool of Refreshment") && pool != null && !walkingToObject(now))
            {
                status = "Drinking from the Pool of Refreshment";
                actions.object(pool, "Drink", () -> requestedAt = now);
            }
            return;
        }
        walking.cancel();
        stocked = false;
        trip.tripStarted();
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
            withdrawFirst(DreamData.DUELING_RINGS, "Ring of dueling", observed);
            return;
        }
        if (!actions.wearing(DreamData.MEDALLION) && actions.count(DreamData.MEDALLION) == 0)
        {
            withdrawFirst(List.of(DreamData.MEDALLION), "Drakan's medallion", observed);
            return;
        }
        List<Integer> absent = new ArrayList<>();
        int fetch = -1;
        for (int id : equipment)
        {
            if (!actions.wearing(id) && actions.count(id) == 0)
            {
                if (actions.bankCount(id) == 0)
                {
                    absent.add(id);
                }
                else if (fetch < 0)
                {
                    fetch = id;
                }
            }
        }
        if (!absent.isEmpty())
        {
            // Every item is checked first, so one stop names all that is missing.
            String them = absent.size() == 1 ? "it" : "them";
            actions.fail("Missing " + absent.stream().map(actions::describe).collect(Collectors.joining(", ")) + ": put " + them
                + " in your bank or remove " + them + " from your equipment IDs, then restart the plugin");
            return;
        }
        if (fetch > 0)
        {
            status = "Withdrawing equipment";
            actions.withdrawOne(fetch, observed);
            return;
        }
        if (withdraw(config.foodId(), config.foodCount() - actions.count(config.foodId()), actions.describe(config.foodId()), "Food count",
                observed)
            || withdrawDoses(List.of(Doses.SUPER_RESTORE, Doses.PRAYER_POTION), config.restoreDoses(), supplies.restoreDoses(),
                "Super restore(4) or Prayer potion(4)", "Restore doses", observed)
            || withdrawDoses(List.of(config.parasiteCure().getDoses()), config.cureDoses(), supplies.cureDoses(), config.parasiteCure() + "(4)",
                "Cure doses", observed)
            || supplies.combatPotions().isEmpty()
                && withdraw(config.combatPotionId(), 1, actions.describe(config.combatPotionId()), "Combat potion ID", observed))
        {
            return;
        }
        stocked = true;
        status = "Closing the bank";
        actions.closeBank(observed);
    }

    /** Withdraws the first of the IDs the bank has, or stops when it has none. */
    private void withdrawFirst(Collection<Integer> ids, String label, Runnable observed)
    {
        for (int id : ids)
        {
            if (actions.bankCount(id) > 0)
            {
                status = "Withdrawing " + label;
                actions.withdrawOne(id, observed);
                return;
            }
        }
        actions.fail("No " + label + " in the bank; add one, then restart the plugin");
    }

    /** Withdraws up to the missing amount of the item. Runs out quietly while some are carried. */
    private boolean withdraw(int id, int missing, String label, String setting, Runnable observed)
    {
        if (id <= 0 || missing <= 0)
        {
            return false;
        }
        int banked = actions.bankCount(id);
        if (banked > 0)
        {
            if (noRoom(id, label, setting))
            {
                return true;
            }
            status = "Withdrawing " + label;
            actions.withdraw(id, amount(id, missing, banked), observed);
            return true;
        }
        if (actions.count(id) == 0)
        {
            actions.fail("No " + label + " left in the bank; add more to the bank, then restart the plugin");
            return true;
        }
        return false;
    }

    /** Withdraws four-dose potions, the first kind the bank has, to cover the missing doses. */
    private boolean withdrawDoses(List<List<Integer>> potions, int wanted, int carried, String label, String setting, Runnable observed)
    {
        if (carried >= wanted)
        {
            return false;
        }
        for (List<Integer> potion : potions)
        {
            int full = potion.get(0);
            int banked = actions.bankCount(full);
            if (banked > 0)
            {
                if (noRoom(full, label, setting))
                {
                    return true;
                }
                status = "Withdrawing " + label;
                actions.withdraw(full, amount(full, (wanted - carried + 3) / 4, banked), observed);
                return true;
            }
        }
        if (carried == 0)
        {
            actions.fail("No " + label + " left in the bank; add more to the bank, then restart the plugin");
            return true;
        }
        return false;
    }

    /** With no slot for a missing supply, a withdrawal would be refused again each time, so the plugin stops once. */
    private boolean noRoom(int id, String label, String setting)
    {
        if (actions.freeSlots() > 0 || actions.stackable(id) && actions.count(id) > 0)
        {
            return false;
        }
        actions.fail("The inventory is full with " + label + " still missing; lower " + setting
            + " or carry fewer Keep items, then restart the plugin");
        return true;
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
