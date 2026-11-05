package net.runelite.client.plugins.microbot.fourtifysgemmining.tasks;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysgemmining.FourtifysGemMiningPlugin;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.action.GemMiningActions;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.navigation.Walking;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.task.FourtifysTask;

/** Mining destinations stay local; the shared walker owns route execution. */
public final class GemTripTask implements FourtifysTask
{
    private enum Phase
    {
        NONE, TO_BANK, BANKING, TO_MINE, FAILED
    }

    private final FourtifysGemMiningPlugin plugin;
    private final Client client;
    private final GemMiningConfig config;
    private final GemMiningActions actions;
    private final BankGemsTask banking;
    private final Walking walking;
    private Phase phase = Phase.NONE;
    private boolean moving;
    private int bankArrivalDeadline;
    private String status = "Walking to Shilo Village bank";

    public GemTripTask(FourtifysGemMiningPlugin plugin, Client client, GemMiningConfig config,
                            GemMiningActions actions, BankGemsTask banking, Walking walking)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.actions = actions;
        this.banking = banking;
        this.walking = walking;
    }

    @Override
    public boolean isValid()
    {
        if (isInProgress())
        {
            return true;
        }
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return false;
        }
        if (actions.bankingOpen() || (actions.freeSlots() == 0 && (actions.gemCount() > 0 || actions.hasGemBag())))
        {
            phase = actions.bankingOpen() ? Phase.BANKING : Phase.TO_BANK;
            return true;
        }
        if (actions.freeSlots() > 0 && (!actions.sceneLoaded() || actions.nearestRock(config.mine()) == null))
        {
            phase = Phase.TO_MINE;
            return true;
        }
        return false;
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    public boolean isInProgress()
    {
        return phase != Phase.NONE;
    }

    public boolean canTravel()
    {
        return phase == Phase.TO_MINE && actions.freeSlots() > 0
            || phase == Phase.TO_BANK && (actions.gemCount() > 0 || actions.hasGemBag() || banking.requiresBankAccess());
    }

    public void setMoving(boolean moving)
    {
        this.moving = moving;
    }

    public void reset()
    {
        walking.cancel();
        phase = Phase.NONE;
        moving = false;
        bankArrivalDeadline = 0;
        status = "Walking to Shilo Village bank";
    }

    @Override
    public void execute()
    {
        if (phase == Phase.NONE)
        {
            return;
        }
        if (phase == Phase.FAILED)
        {
            plugin.setStatus(status);
            return;
        }
        Player player = client.getLocalPlayer();
        WorldPoint point = player == null ? null : player.getWorldLocation();
        if (point == null)
        {
            fail("Lost track of your position. Disable and enable to retry");
            return;
        }
        String missing = actions.prerequisite(config.mine());
        if (missing != null)
        {
            walking.cancel();
            status = missing;
            plugin.setStatus(status);
            return;
        }
        if (walking.isActive() && actions.bankingOpen())
        {
            // The normal walker may open an intermediate bank for the user's selected transports.
            if (phase == Phase.TO_BANK && !actions.bankInRange(config.mine()))
            {
                walk(bankDestination(), bankName());
                return;
            }
            if (phase == Phase.TO_MINE && actions.nearestRock(config.mine()) == null)
            {
                WorldPoint rock = actions.rockDestination(config.mine());
                walk(rock == null ? config.mine().getMineDestination() : rock, config.mine().getPlaceName());
                return;
            }
        }
        if (phase == Phase.TO_MINE && (actions.freeSlots() == 0 || actions.bankingOpen()))
        {
            if (walking.isActive())
            {
                walking.cancel();
            }
            if (actions.bankingOpen())
            {
                phase = Phase.BANKING;
            }
            else if (actions.gemCount() > 0 || actions.hasGemBag())
            {
                phase = Phase.TO_BANK;
            }
            else
            {
                reset();
                status = "Inventory full of other items. Please make room";
                plugin.setStatus(status);
                return;
            }
        }
        if (phase == Phase.TO_BANK)
        {
            if (actions.gemCount() == 0 && !actions.bankingOpen() && actions.freeSlots() > 0
                && !banking.requiresBankAccess())
            {
                phase = Phase.TO_MINE;
            }
            else if (actions.bankingOpen() || actions.bankInRange(config.mine()))
            {
                walking.cancel();
                bankArrivalDeadline = 0;
                phase = Phase.BANKING;
            }
            else
            {
                walk(bankDestination(), bankName());
                return;
            }
        }
        if (phase == Phase.BANKING)
        {
            if (moving)
            {
                status = "Approaching bank";
                plugin.setStatus(status);
                return;
            }
            if (!actions.bankingOpen() && banking.requiresBankAccess() && !banking.isPending()
                && !actions.bankInRange(config.mine()))
            {
                phase = Phase.TO_BANK;
                walk(bankDestination(), bankName());
                return;
            }
            banking.isValid();
            banking.execute();
            status = banking.getStatus();
            if (banking.isPaused())
            {
                walking.cancel();
                phase = Phase.FAILED;
                return;
            }
            if (!banking.isValid() && !actions.bankingOpen() && actions.gemCount() == 0
                && actions.freeSlots() > 0)
            {
                phase = Phase.TO_MINE;
            }
            else
            {
                return;
            }
        }
        if (phase == Phase.TO_MINE)
        {
            if (!actions.sceneLoaded())
            {
                if (walking.isActive())
                {
                    walking.cancel();
                }
                status = "Waiting for scene";
                plugin.setStatus(status);
                return;
            }
            if (actions.nearestRock(config.mine()) != null)
            {
                reset();
                status = "Gem rocks are in range";
                plugin.setStatus(status);
                return;
            }
            WorldPoint rock = actions.rockDestination(config.mine());
            if (rock == null && config.mine().contains(point))
            {
                if (walking.isActive())
                {
                    walking.cancel();
                }
                status = actions.mineSceneLoaded(config.mine()) ? "Waiting for gem rocks" : "Waiting for scene";
                plugin.setStatus(status);
                return;
            }
            walk(rock == null ? config.mine().getMineDestination() : rock, config.mine().getPlaceName());
        }
    }

    private void walk(WorldPoint destination, String place)
    {
        Walking.State result = walking.walkTo(destination);
        if (result == Walking.State.FAILED)
        {
            fail("Could not walk to " + place + ": " + walking.getFailureReason()
                + ". Walk closer, then disable and enable to retry");
            return;
        }
        if (result == Walking.State.ARRIVED && phase == Phase.TO_BANK
            && !actions.bankingOpen() && !actions.bankInRange(config.mine()))
        {
            if (bankArrivalDeadline == 0)
            {
                bankArrivalDeadline = plugin.getTick() + 8;
            }
            if (plugin.getTick() >= bankArrivalDeadline)
            {
                fail("Found no banker or bank booth at " + bankName()
                    + ". Walk into the bank, then disable and enable to retry");
                return;
            }
            status = "Waiting for " + bankName();
            plugin.setStatus(status);
            return;
        }
        status = result == Walking.State.ARRIVED && phase == Phase.TO_MINE
            ? "Waiting for gem rocks to respawn" : "Walking to " + place;
        plugin.setStatus(status);
    }

    private WorldPoint bankDestination()
    {
        WorldPoint destination = actions.bankDestination(config.mine());
        return destination == null ? config.mine().getBankDestination() : destination;
    }

    private String bankName()
    {
        return config.mine().isUnderground() ? "the bank deposit chest in the underground mine" : "Shilo Village bank";
    }

    private void fail(String reason)
    {
        walking.cancel();
        phase = Phase.FAILED;
        status = reason;
        plugin.setStatus(status);
    }
}
