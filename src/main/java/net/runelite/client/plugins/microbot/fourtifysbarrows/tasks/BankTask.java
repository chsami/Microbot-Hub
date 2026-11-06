package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import java.util.Map;
import net.runelite.api.Prayer;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;

public final class BankTask implements FourtifysTask
{
    private enum Waiting
    {
        NONE, CLOSE_REWARDS, TRAVEL, PRAYER_OFF, OPEN, DEPOSIT, WITHDRAW, CLOSE_FOR_FEROX, CLOSE_FOR_POOL, RESTORE, CLOSE
    }

    /** A Withdraw-X can wait for the amount prompt and the typed amount before the items arrive. */
    private static final int WITHDRAW_TICKS = 14;
    private final BarrowsActions actions;
    private Waiting waiting = Waiting.NONE;
    private String operation = "";
    private String status = "Resupplying";
    private int attempts;
    private int deadline;
    private int controlsDeadline = -1;
    private int before;
    private int itemId;
    private boolean failed;
    private boolean ringArrivalObserved;
    private boolean destinationRequested;
    private int destinationAttempts;
    private int nextDestinationAttempt;
    private int poolDeadline = -1;
    private boolean feroxRestorationObserved;

    public BankTask(BarrowsActions actions)
    {
        this.actions = actions;
    }

    @Override
    public boolean isValid()
    {
        return actions.ready() && actions.tripPending();
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        if (!isValid() || failed)
        {
            return;
        }
        update();
        actions.status(status);
    }

    public void suspend()
    {
        if (waiting != Waiting.NONE)
        {
            deadline = actions.tick() + (waiting == Waiting.TRAVEL ? 90 : 8);
        }
        if (controlsDeadline >= 0)
        {
            controlsDeadline = actions.tick() + 8;
        }
        if (poolDeadline >= 0)
        {
            poolDeadline = actions.tick() + 60;
        }
    }

    private void update()
    {
        if (waiting != Waiting.NONE)
        {
            if (observed())
            {
                waiting = Waiting.NONE;
                operation = "";
                attempts = 0;
                controlsDeadline = -1;
            }
            else if ((waiting == Waiting.CLOSE || waiting == Waiting.CLOSE_FOR_POOL || waiting == Waiting.CLOSE_FOR_FEROX)
                && !actions.bankingOpen() && !actions.bankCloseObserved())
            {
                if (actions.tick() >= deadline)
                {
                    if (attempts >= 3)
                    {
                        stop("Bank close not acknowledged; reopen the bank and close it, then enable the plugin again");
                        return;
                    }
                    attempts++;
                    deadline = actions.tick() + 8;
                }
                status = "Waiting for bank close acknowledgement";
                return;
            }
            else if (actions.tick() < deadline)
            {
                if (waiting == Waiting.TRAVEL && (!destinationRequested || actions.tick() >= nextDestinationAttempt))
                {
                    if (destinationAttempts >= 3)
                    {
                        if (actions.bankTravelDialogueVisible())
                        {
                            stop("The Ring of dueling's Ferox Enclave teleport failed three times; enable the plugin again to retry");
                            return;
                        }
                    }
                    else
                    {
                        actions.selectBankTravelDestination(() ->
                        {
                            destinationRequested = true;
                            destinationAttempts++;
                            nextDestinationAttempt = actions.tick() + 8;
                        });
                    }
                }
                status = waiting == Waiting.TRAVEL ? "Waiting for selected bank teleport arrival"
                    : waiting == Waiting.RESTORE ? "Waiting for Ferox pool restoration" : "Waiting for bank action";
                return;
            }
            else
            {
                waiting = Waiting.NONE;
                if (attempts >= 3)
                {
                    stop("Could not " + operation + " after three tries; enable the plugin again to retry");
                    return;
                }
            }
        }
        if (!actions.tripPending())
        {
            return;
        }
        if (actions.visible(155 << 16))
        {
            if (prepare("close Barrows rewards before banking"))
            {
                status = "Closing Barrows rewards before Ferox teleport";
                actions.closeRewards(() -> await(Waiting.CLOSE_REWARDS, 0));
            }
            return;
        }
        BarrowsConfig config = actions.config();
        if (!actions.bankingOpen())
        {
            if (!ringArrivalObserved)
            {
                if (actions.atBankTravelArrival() || actions.atSelectedBank())
                {
                    ringArrivalObserved = true;
                }
                else
                {
                    String problem = actions.bankTravelProblem();
                    if (problem != null)
                    {
                        stop(problem);
                        return;
                    }
                    if (prepare("use the Ring of dueling to Ferox Enclave"))
                    {
                        status = "Using Ring of dueling to Ferox Enclave";
                        actions.requestBankTravel(() -> await(Waiting.TRAVEL, 0));
                    }
                    return;
                }
            }
            Prayer protection = actions.activeProtection();
            if (protection != null)
            {
                if (prepare("turn off protection prayer"))
                {
                    status = "Turning off protection prayer outside Barrows";
                    actions.protect(protection, false, () -> await(Waiting.PRAYER_OFF, 0));
                }
                return;
            }
            if (needsFeroxRestoration())
            {
                restoreAtFeroxPool();
                return;
            }
            poolDeadline = -1;
            if (!actions.walkToBank())
            {
                status = "Walking to Ferox bank";
                return;
            }
            if (prepare("open the bank"))
            {
                status = "Opening bank";
                actions.openBank(() -> await(Waiting.OPEN, 0));
            }
            return;
        }
        if (actions.hasLoot())
        {
            if (prepare("deposit the chest rewards"))
            {
                status = "Depositing chest rewards";
                actions.depositLoot(() -> await(Waiting.DEPOSIT, 0));
            }
            return;
        }
        if (actions.count(952) == 0)
        {
            withdraw(952, "Spade", 1, "bank a spade");
            return;
        }
        if (actions.foodCount() < config.foodCount())
        {
            withdraw(config.foodId(), actions.itemName(config.foodId()), config.foodCount() - actions.foodCount(), "restock it or lower Food count");
            return;
        }
        if (actions.potionCount() < config.potionCount())
        {
            withdrawPrayerPotion(config.potionCount() - actions.potionCount());
            return;
        }
        for (Map.Entry<Integer, Integer> supply : actions.restockItems().entrySet())
        {
            if (actions.count(supply.getKey()) < supply.getValue())
            {
                withdraw(supply.getKey(), actions.itemName(supply.getKey()), supply.getValue() - actions.count(supply.getKey()),
                    "restock it or change Additional supplies/ammunition");
                return;
            }
        }
        if (!actions.hasBankTravelRing())
        {
            int ring = actions.bankReplacementRing();
            if (ring > 0)
            {
                withdraw(ring, "Ring of dueling", 1, "bank a charged one");
            }
            else if (prepare("withdraw charged bank ring", "Out of charged Rings of dueling in the bank: bank one, then enable the plugin again"))
            {
                status = "Waiting for a charged bank ring in the bank";
            }
            return;
        }
        String problem = actions.bankTravelProblem();
        if (problem != null)
        {
            stop(problem);
            return;
        }
        if (!actions.atSelectedBank())
        {
            if (prepare("close the bank before the ring teleport"))
            {
                status = "Closing bank before Ring of dueling travel to Ferox";
                actions.closeBank(() -> await(Waiting.CLOSE_FOR_FEROX, 0));
            }
            return;
        }
        if (needsFeroxRestoration())
        {
            if (prepare("close the bank before the Pool of Refreshment"))
            {
                status = "Closing bank before Ferox restoration";
                actions.closeBank(() -> await(Waiting.CLOSE_FOR_POOL, 0));
            }
            return;
        }
        if (prepare("close the bank"))
        {
            status = "Closing bank";
            actions.closeBank(() -> await(Waiting.CLOSE, 0));
        }
    }

    private boolean needsFeroxRestoration()
    {
        if (feroxRestorationObserved)
        {
            return false;
        }
        feroxRestorationObserved = actions.feroxRestored();
        return !feroxRestorationObserved;
    }

    private void restoreAtFeroxPool()
    {
        if (poolDeadline < 0)
        {
            poolDeadline = actions.tick() + 60;
        }
        if (actions.tick() >= poolDeadline)
        {
            stop("Ferox pool restoration timed out; check the path and depleted stats, then enable the plugin again");
            return;
        }
        if (!actions.walkToFeroxPool())
        {
            status = "Walking to Ferox Pool of Refreshment";
            return;
        }
        if (prepare("drink from the Ferox Pool of Refreshment"))
        {
            status = "Restoring at Ferox Pool of Refreshment";
            actions.drinkFeroxPool(() -> await(Waiting.RESTORE, 0));
        }
    }

    private void withdrawPrayerPotion(int missing)
    {
        int available = -1;
        for (int id : BarrowsActions.PRAYER_POTIONS)
        {
            if (actions.bankHas(id))
            {
                available = id;
                break;
            }
        }
        if (available < 0)
        {
            if (prepare("withdraw prayer potion", "Out of prayer potions in the bank: restock them or lower Prayer potion count"))
            {
                status = "Waiting for prayer potions in the bank";
            }
            return;
        }
        withdraw(available, "prayer potions", missing, "restock them or lower Prayer potion count");
    }

    /** Withdraws what is missing in one request, capped by the bank and, unstacked, by the free slots. */
    private void withdraw(int id, String name, int missing, String restock)
    {
        String stopReason = actions.bankCount(id) > 0
            ? "Could not withdraw " + name + "; free inventory space, then enable the plugin again"
            : "Out of " + name + " in the bank: " + restock;
        if (prepare("withdraw " + name, stopReason))
        {
            status = "Withdrawing " + name;
            int amount = Math.min(missing, actions.bankCount(id));
            if (!actions.stackable(id))
            {
                amount = Math.min(amount, actions.freeSlots());
            }
            actions.withdraw(id, Math.max(1, amount), () -> await(Waiting.WITHDRAW, id));
        }
    }

    private boolean prepare(String nextOperation)
    {
        return prepare(nextOperation, "Could not " + nextOperation + " after three tries; enable the plugin again to retry");
    }

    private boolean prepare(String nextOperation, String stopReason)
    {
        if (!nextOperation.equals(operation))
        {
            operation = nextOperation;
            attempts = 0;
            controlsDeadline = -1;
        }
        if (controlsDeadline < 0)
        {
            controlsDeadline = actions.tick() + 8;
        }
        if (actions.tick() >= controlsDeadline)
        {
            controlsDeadline = actions.tick() + 8;
            attempts++;
            if (attempts >= 3)
            {
                stop(stopReason);
                return false;
            }
        }
        return true;
    }

    private void await(Waiting next, int id)
    {
        waiting = next;
        destinationRequested = false;
        destinationAttempts = 0;
        nextDestinationAttempt = 0;
        itemId = id;
        before = next == Waiting.DEPOSIT ? actions.lootCount() : actions.count(id);
        deadline = actions.tick() + (next == Waiting.TRAVEL ? 90 : next == Waiting.WITHDRAW ? WITHDRAW_TICKS : 8);
        controlsDeadline = -1;
        attempts++;
    }

    private boolean observed()
    {
        if (waiting == Waiting.CLOSE_REWARDS)
        {
            return !actions.visible(155 << 16);
        }
        if (waiting == Waiting.TRAVEL)
        {
            if (actions.atBankTravelArrival())
            {
                ringArrivalObserved = true;
                return true;
            }
            return false;
        }
        if (waiting == Waiting.PRAYER_OFF)
        {
            return actions.activeProtection() == null;
        }
        if (waiting == Waiting.OPEN)
        {
            return actions.bankingOpen();
        }
        if (waiting == Waiting.DEPOSIT)
        {
            return actions.lootCount() < before;
        }
        if (waiting == Waiting.WITHDRAW)
        {
            return actions.count(itemId) > before;
        }
        if (waiting == Waiting.CLOSE_FOR_POOL || waiting == Waiting.CLOSE_FOR_FEROX)
        {
            return actions.bankCloseObserved();
        }
        if (waiting == Waiting.RESTORE)
        {
            feroxRestorationObserved = actions.feroxRestored();
            return feroxRestorationObserved;
        }
        if (waiting == Waiting.CLOSE && actions.bankCloseObserved() && !needsFeroxRestoration())
        {
            actions.completeTrip();
            ringArrivalObserved = false;
            feroxRestorationObserved = false;
            status = "Resupply complete";
            return true;
        }
        return false;
    }

    private void stop(String reason)
    {
        failed = true;
        status = reason;
        actions.fail(reason);
    }
}
