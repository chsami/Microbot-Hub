package net.runelite.client.plugins.microbot.blackjack.entryswapper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.blackjack.BlackjackPlugin;
import net.runelite.client.plugins.microbot.blackjack.BlackjackTarget;

import javax.inject.Inject;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>JOJ</font>] Blackjack Entry Swap",
        description = "Selects Knock-Out or Pickpocket as left-click from the target animation",
        tags = {"thieving", "blackjack", "menu", "entry", "swap"},
        authors = {"jogonjohn"},
        version = BlackjackPlugin.VERSION,
        minClientVersion = "2.6.38",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BlackjackEntrySwapPlugin extends Plugin
{
    private static final String KNOCKOUT_ACTION = "Knock-Out";
    private static final String PICKPOCKET_ACTION = "Pickpocket";

    @Inject
    private Client client;

    @Provides
    BlackjackEntrySwapConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BlackjackEntrySwapConfig.class);
    }

    @Override
    protected void startUp()
    {
        log.info("Blackjack Entry Swap enabled: standing=Knock-Out unconscious=Pickpocket");
    }

    @Subscribe(priority = -100)
    public void onPostMenuSort(PostMenuSort event)
    {
        if (client.isMenuOpen())
        {
            return;
        }

        Menu menu = client.getMenu();
        MenuEntry[] entries = menu.getMenuEntries();
        NPC hoveredTarget = findHoveredSupportedTarget(entries);
        if (hoveredTarget == null)
        {
            return;
        }

        String desiredAction = hoveredTarget.getAnimation() == AnimationID.HUMAN_UNCONSCIOUS
                ? PICKPOCKET_ACTION
                : KNOCKOUT_ACTION;
        int desiredIndex = findAction(entries, hoveredTarget.getIndex(), desiredAction);
        if (desiredIndex < 0)
        {
            return;
        }

        MenuEntry desiredEntry = entries[desiredIndex];
        desiredEntry.setDeprioritized(false);
        desiredEntry.setForceLeftClick(true);
        if (desiredIndex == entries.length - 1)
        {
            return;
        }
        System.arraycopy(entries, desiredIndex + 1, entries, desiredIndex,
                entries.length - desiredIndex - 1);
        entries[entries.length - 1] = desiredEntry;
        menu.setMenuEntries(entries);
    }

    private NPC findHoveredSupportedTarget(MenuEntry[] entries)
    {
        for (int i = entries.length - 1; i >= 0; i--)
        {
            NPC npc = entries[i].getNpc();
            if (isSupportedTarget(npc))
            {
                return npc;
            }
        }
        return null;
    }

    private int findAction(MenuEntry[] entries, int npcIndex, String action)
    {
        for (int i = entries.length - 1; i >= 0; i--)
        {
            NPC npc = entries[i].getNpc();
            if (npc != null && npc.getIndex() == npcIndex
                    && action.equalsIgnoreCase(entries[i].getOption()))
            {
                return i;
            }
        }
        return -1;
    }

    private boolean isSupportedTarget(NPC npc)
    {
        if (npc == null || npc.getName() == null)
        {
            return false;
        }
        return BlackjackTarget.BANDIT_41.matches(npc.getName(), npc.getCombatLevel())
                || BlackjackTarget.BANDIT_56.matches(npc.getName(), npc.getCombatLevel())
                || BlackjackTarget.MENAPHITE_THUG.matches(npc.getName(), npc.getCombatLevel());
    }
}
