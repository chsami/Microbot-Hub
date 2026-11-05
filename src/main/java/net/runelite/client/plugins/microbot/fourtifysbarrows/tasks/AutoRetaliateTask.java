package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;

/** Keep incidental tunnel attackers out of the reward-potential plan. */
public final class AutoRetaliateTask implements FourtifysTask
{
    private final BarrowsActions actions;
    private int deadline = -1;
    private int nextAttempt;
    private int attempts;

    public AutoRetaliateTask(BarrowsActions actions)
    {
        this.actions = actions;
    }

    public void suspend()
    {
        deadline = -1;
        nextAttempt = 0;
        attempts = 0;
    }

    @Override
    public boolean isValid()
    {
        observe();
        return actions.ready() && !actions.autoRetaliateDisabled()
            && !(actions.bankingOpen() && actions.tripPending());
    }

    public void observe()
    {
        if (!actions.ready() || actions.autoRetaliateDisabled()
            || actions.bankingOpen() && actions.tripPending())
        {
            suspend();
        }
    }

    @Override
    public String getStatus()
    {
        return actions.getStatus();
    }

    @Override
    public void execute()
    {
        if (!isValid())
        {
            return;
        }
        actions.status("Disable auto-retaliate (stays off)");
        if (deadline < 0)
        {
            deadline = actions.tick() + 24;
        }
        if (actions.tick() >= deadline || attempts >= 3 && actions.tick() >= nextAttempt)
        {
            actions.fail("Could not observe auto-retaliate off; turn it off in Combat Options and restart");
            return;
        }
        if (actions.tick() < nextAttempt)
        {
            return;
        }
        if (actions.visible(155 << 16))
        {
            actions.status("Close rewards before disabling auto-retaliate");
            actions.closeRewards(this::dispatched);
        }
        else if (actions.visible(BarrowsActions.DIALOGUE_OPTIONS) && actions.tripPending() && actions.bankTravelDialogueVisible())
        {
            actions.status("Complete bank teleport before disabling auto-retaliate");
            actions.selectBankTravelDestination(this::dispatched);
        }
        else if (actions.bankingOpen())
        {
            actions.closeBank(this::dispatched);
        }
        else
        {
            actions.disableAutoRetaliate(this::dispatched);
        }
    }

    private void dispatched()
    {
        attempts++;
        nextAttempt = actions.tick() + 8;
    }
}
