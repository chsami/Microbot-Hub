package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;

public final class SuppliesTask implements FourtifysTask
{
    private static final int OBSERVATION_TICKS = 8;
    private static final int MAX_ATTEMPTS = 3;

    private final BarrowsActions actions;
    private int pendingItem = -1;
    private int quantityBefore;
    private int statBefore;
    private int deadline;
    private int attempts;
    private boolean healing;
    private boolean pending;
    private boolean suspendedForBank;
    private int controlsDeadline = -1;

    public SuppliesTask(BarrowsActions actions)
    {
        this.actions = actions;
    }

    @Override
    public boolean isValid()
    {
        if (actions.player() == null || actions.bankingOpen())
        {
            return false;
        }
        if (!actions.bankPending())
        {
            suspendedForBank = false;
        }
        if (suspendedForBank)
        {
            return false;
        }
        if (!pending && atSafeFeroxReturn())
        {
            attempts = 0;
            controlsDeadline = -1;
            return false;
        }
        return pending || needsFood() && actions.foodCount() > 0
            || needsPrayer() && actions.prayerRestoreId() > 0
            || actions.inside() && !actions.bankPending() && (needsFood() || needsPrayer());
    }

    @Override
    public String getStatus()
    {
        if (pending)
        {
            return healing ? "Wait for food consumption" : "Wait for prayer restoration";
        }
        return needsFood() ? "Restore health" : "Restore prayer";
    }

    @Override
    public void execute()
    {
        if (!isValid())
        {
            return;
        }
        if (pending)
        {
            int stat = healing ? actions.hp() : actions.prayerPoints();
            if (actions.count(pendingItem) < quantityBefore || stat > statBefore)
            {
                pending = false;
                attempts = 0;
                controlsDeadline = -1;
                return;
            }
            if (actions.tick() < deadline)
            {
                actions.status(getStatus());
                return;
            }
            pending = false;
            if (atSafeFeroxReturn())
            {
                attempts = 0;
                controlsDeadline = -1;
                return;
            }
            if (attempts >= MAX_ATTEMPTS)
            {
                actions.status("Supply consumption failed; return to bank");
                actions.requestBank();
                suspendedForBank = true;
                return;
            }
        }
        if (!actions.ready())
        {
            return;
        }
        healing = needsFood() && actions.foodCount() > 0;
        int item = healing ? actions.config().foodId() : needsPrayer() ? actions.prayerRestoreId() : -1;
        if (item < 0 || actions.count(item) <= 0)
        {
            actions.status(needsFood() ? "Food exhausted; return to bank" : "Prayer potions exhausted; return to bank");
            actions.requestBank();
            return;
        }
        if (controlsDeadline < 0)
        {
            controlsDeadline = actions.tick() + OBSERVATION_TICKS * MAX_ATTEMPTS;
        }
        if (actions.tick() >= controlsDeadline)
        {
            actions.requestBank();
            suspendedForBank = true;
            actions.status("Supply controls unavailable; return to bank");
            return;
        }
        String option = healing ? "Eat" : "Drink";
        actions.inventory(item, option, () -> observe(item));
    }

    private boolean needsFood()
    {
        return actions.hp() * 100 <= actions.config().eatAt() * actions.maxHp() && actions.hp() < actions.maxHp();
    }

    private boolean atSafeFeroxReturn()
    {
        WorldPoint point = actions.position();
        return actions.bankPending() && !actions.inside() && point != null && point.getPlane() == 0
            && point.getX() >= 3126 && point.getX() <= 3154 && point.getY() >= 3628 && point.getY() <= 3639;
    }

    private boolean needsPrayer()
    {
        return actions.prayerPoints() * 100 <= actions.config().drinkAt() * actions.maxPrayer()
            && actions.prayerPoints() < actions.maxPrayer();
    }

    private void observe(int item)
    {
        pendingItem = item;
        quantityBefore = actions.count(item);
        statBefore = healing ? actions.hp() : actions.prayerPoints();
        deadline = actions.tick() + OBSERVATION_TICKS;
        controlsDeadline = -1;
        attempts++;
        pending = true;
    }
}
