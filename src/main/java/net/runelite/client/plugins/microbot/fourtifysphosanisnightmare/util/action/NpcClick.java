package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action;

import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/**
 * Sends one NPC option as a single menu click, without the camera turn the Microbot API NPC model makes first for an
 * NPC off screen. The turn holds the action queue for most of a tick, so in her dream a protection prayer waiting
 * behind it misses her attack. This click never walks either.
 */
public final class NpcClick
{
    private static final MenuAction[] OPTIONS = {MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
        MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION};

    private NpcClick()
    {
    }

    /** Clicks the NPC's option by its listed name; false when the option is missing. */
    public static boolean click(NPC npc, String option)
    {
        NPCComposition composition = npc.getTransformedComposition();
        if (composition == null)
        {
            composition = npc.getComposition();
        }
        String[] actions = composition == null ? null : composition.getActions();
        int index = -1;
        for (int candidate = 0; actions != null && index < 0
            && candidate < Math.min(actions.length, OPTIONS.length); candidate++)
        {
            if (actions[candidate] != null && option.equalsIgnoreCase(Rs2UiHelper.stripColTags(actions[candidate])))
            {
                index = candidate;
            }
        }
        if (index < 0)
        {
            return false;
        }
        Microbot.doInvoke(new NewMenuEntry().param0(0).param1(0).opcode(OPTIONS[index].getId())
            .identifier(npc.getIndex()).itemId(-1).option(option).target(composition.getName()).actor(npc),
            Rs2UiHelper.getActorClickbox(npc));
        return true;
    }
}
