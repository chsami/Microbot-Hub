package net.runelite.client.plugins.microbot.fourtifysagility.tasks;

import net.runelite.client.plugins.microbot.fourtifysagility.util.task.FourtifysTask;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.Spellbook;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

public final class MovingTask implements FourtifysTask
{
    private final FourtifysAgilityPlugin plugin;
    private final Client client;
    private final AgilityConfig config;
    private final AgilityActions actions;
    private final Spellbook spells;

    public MovingTask(FourtifysAgilityPlugin plugin, Client client, AgilityConfig config,
        AgilityActions actions, Spellbook spells)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.actions = actions;
        this.spells = spells;
    }

    public boolean isValid()
    {
        Player player = client.getLocalPlayer();
        return player != null && plugin.hasPlayerMoved() && player.getAnimation() != 713;
    }

    public String getStatus()
    {
        return plugin.getStatus();
    }

    public void execute()
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }
        if (config.highAlch() && Rs2Inventory.contains(config.alchId()) && Rs2Inventory.contains(561))
        {
            spells.alchemy(config.alchId());
        }
        actions.manageRun(20, 30);
        plugin.delayNextAction();
    }
}
