package net.runelite.client.plugins.microbot.fourtifysgemmining.tasks;

import net.runelite.client.plugins.microbot.fourtifysgemmining.util.task.FourtifysTask;

import net.runelite.client.plugins.microbot.fourtifysgemmining.util.scene.TargetRange;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifysgemmining.FourtifysGemMiningPlugin;
import net.runelite.client.plugins.microbot.fourtifysgemmining.config.GemMiningConfig;
import net.runelite.client.plugins.microbot.fourtifysgemmining.mining.GemMine;
import net.runelite.client.plugins.microbot.fourtifysgemmining.util.action.GemMiningActions;

public final class MineGemsTask implements FourtifysTask
{
    private final FourtifysGemMiningPlugin plugin;
    private final Client client;
    private final GemMiningConfig config;
    private final GemMiningActions actions;
    private GameObject target;
    private int deadline;
    private int attempts;
    private int baseline;
    private long requestSequence;
    private boolean paused;
    private String status = "Mining gem rocks";

    public MineGemsTask(FourtifysGemMiningPlugin plugin, Client client, GemMiningConfig config, GemMiningActions actions)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.actions = actions;
    }

    public boolean isValid()
    {
        return client.getLocalPlayer() != null && (config.mine().contains(client.getLocalPlayer().getWorldLocation())
            || actions.nearestRock(config.mine()) != null);
    }

    public boolean isWithinRange()
    {
        Player player = client.getLocalPlayer();
        return player != null && (config.mine().contains(player.getWorldLocation())
            || target != null && TargetRange.objectDistance(client, target) <= TargetRange.GAME_OBJECT_SEARCH_RADIUS);
    }

    public String getStatus()
    {
        return status;
    }

    public void execute()
    {
        status = mine();
        plugin.setStatus(status);
    }

    public boolean isPending()
    {
        return target != null && !paused;
    }

    public long getRequestSequence()
    {
        return requestSequence;
    }

    public void observeAnimation(Player player, int tick)
    {
        if (isPending() && player.getAnimation() != -1)
        {
            deadline = tick + 10;
        }
    }

    public void observeMovement(int tick)
    {
        if (isPending())
        {
            deadline = tick + 10;
        }
    }

    public boolean observeCompletion()
    {
        if (target == null || (actions.gemCount() == baseline && actions.rocks(config.mine()).contains(target)))
        {
            return false;
        }
        reset();
        return true;
    }

    public void reset()
    {
        target = null;
        deadline = 0;
        attempts = 0;
        paused = false;
        status = "Mining gem rocks";
    }

    private String mine()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return "Waiting for login";
        }
        GemMine mine = config.mine();
        int tick = plugin.getTick();
        if (paused)
        {
            return "Mining did not start after three attempts. Disable and enable to retry";
        }
        if (target != null)
        {
            if (actions.gemCount() != baseline || !actions.rocks(mine).contains(target))
            {
                reset();
            }
            else if (player.getAnimation() != -1)
            {
                deadline = tick + 10;
                return "Mining gem rocks";
            }
            else if (tick < deadline)
            {
                return "Waiting for mining to start";
            }
            else
            {
                target = null;
                if (attempts >= 3)
                {
                    paused = true;
                    return "Mining did not start after three attempts. Disable and enable to retry";
                }
            }
        }
        if (player.getAnimation() != -1)
        {
            return "Waiting for current action";
        }
        GameObject rock = actions.nearestAvailableRock(mine);
        if (rock == null)
        {
            return actions.nearestRock(mine) == null ? "Walk closer to gem rocks or wait for a respawn"
                : "Waiting for an unoccupied gem rock";
        }
        if (!actions.mine(mine, rock, () -> {
            target = rock;
            requestSequence++;
            baseline = actions.gemCount();
            deadline = plugin.getTick() + 10;
            attempts++;
        }))
        {
            return "Gem rock changed. Waiting for the scene";
        }
        return "Starting mining";
    }
}
