package net.runelite.client.plugins.microbot.fourtifysagility.tasks;

import net.runelite.client.plugins.microbot.fourtifysagility.util.task.FourtifysTask;

import net.runelite.api.Client;
import net.runelite.api.GroundObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.config.PyramidTopTurnIn;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.Spellbook;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

public final class TurnInPyramidTopTask implements FourtifysTask
{
    private final Client client;
    private final AgilityConfig config;
    private final AgilityActions actions;
    private boolean turnInStarted;

    public TurnInPyramidTopTask(FourtifysAgilityPlugin plugin, Client client, AgilityConfig config,
        AgilityActions actions, Spellbook spells)
    {
        this.client = client;
        this.config = config;
        this.actions = actions;
    }

    public boolean isValid()
    {
        Player player = client.getLocalPlayer();
        if (player == null || config.area() != AgilityCourse.AGILITY_PYRAMID
            || player.getWorldLocation().getPlane() != 0)
        {
            turnInStarted = false;
            return false;
        }
        if (turnInStarted && player.getWorldLocation().getX() < 3352
            && actions.findNearest(5786) != null && dialogueVisible())
        {
            return true;
        }
        if (!Rs2Inventory.contains(6970))
        {
            turnInStarted = false;
            return false;
        }
        return config.turnInTops() == PyramidTopTurnIn.EVERY_LAP
            || config.turnInTops() == PyramidTopTurnIn.FULL_INVENTORY && Rs2Inventory.emptySlotCount() == 0;
    }

    private boolean dialogueVisible()
    {
        return actions.visible(14221312) || actions.visible(15138816)
            || actions.visible(14352384) || actions.visible(15007744);
    }

    public String getStatus()
    {
        return "Turning in tops";
    }

    public void execute()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }
        if (player.getWorldLocation().getX() >= 3352)
        {
            GroundObject entrance = actions.findNearestGroundObject(11949);
            if (entrance != null)
            {
                actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), entrance);
            }
        }
        else if (actions.visible(14221312))
        {
            turnInStarted = true;
            actions.continueDialogue(14221317, -1);
        }
        else if (actions.visible(15138816))
        {
            turnInStarted = true;
            actions.continueDialogue(15138821, -1);
        }
        else if (actions.visible(14352384))
        {
            turnInStarted = true;
            actions.continueDialogue(14352385, 1);
        }
        else if (actions.visible(15007744))
        {
            turnInStarted = true;
            actions.continueDialogue(15007748, -1);
        }
        else
        {
            NPC simon = actions.findNearest(5786);
            if (simon != null)
            {
                turnInStarted = true;
                actions.interactNpc(MenuAction.NPC_FIRST_OPTION.getId(), simon);
            }
        }
    }
}
