package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.task.FourtifysTask;

public final class CombatTask implements FourtifysTask
{
    private final BarrowsActions actions;
    private int lastDispatchTick = Integer.MIN_VALUE / 2;
    private int preparationDeadline = -1;
    private Brother preparationBrother;
    private NPC trackedNpc;
    private int lastHealthRatio;
    private int progressTick;

    public CombatTask(BarrowsActions actions)
    {
        this.actions = actions;
    }

    @Override
    public boolean isValid()
    {
        if (actions.bankPending() || actions.player() == null)
        {
            observePreparation(null);
            return false;
        }
        NPC npc = actions.brotherNpc();
        if (npc != null)
        {
            observePreparation(Brother.fromNpcName(npc.getName()));
            return true;
        }
        Brother tomb = Brother.inTomb(actions.player().getWorldLocation());
        if (tomb == null || actions.killed(tomb))
        {
            observePreparation(null);
            return false;
        }
        observePreparation(tomb);
        return !actions.prayerActive(tomb.getPrayer());
    }

    private void observePreparation(Brother brother)
    {
        if (brother != preparationBrother || brother == null || actions.prayerActive(brother.getPrayer()))
        {
            preparationDeadline = -1;
        }
        preparationBrother = brother;
    }

    @Override
    public String getStatus()
    {
        NPC npc = actions.brotherNpc();
        return npc == null ? "Prepare protection prayer" : "Fight " + npc.getName();
    }

    @Override
    public void execute()
    {
        if (!isValid() || !actions.ready() || actions.tick() - lastDispatchTick < 1)
        {
            return;
        }
        NPC npc = actions.brotherNpc();
        Player player = actions.player();
        Brother brother = npc == null ? Brother.inTomb(player.getWorldLocation()) : Brother.fromNpcName(npc.getName());
        if (brother == null)
        {
            return;
        }
        if (npc != null && !observeProgress(npc))
        {
            return;
        }
        Prayer prayer = brother.getPrayer();
        if (!actions.prayerActive(prayer))
        {
            if (preparationDeadline < 0)
            {
                preparationDeadline = actions.tick() + 24;
            }
            if (actions.tick() >= preparationDeadline)
            {
                actions.status("Protection prayer unavailable; return to bank");
                actions.requestBank();
                return;
            }
            if (actions.prayerPoints() <= 0)
            {
                actions.status("Restore prayer before combat");
                return;
            }
            actions.protect(prayer, true, this::dispatched);
            return;
        }
        preparationDeadline = -1;
        if (npc == null)
        {
            return;
        }
        String gear = brother == Brother.AHRIM ? actions.config().rangedGear() : actions.config().magicGear();
        if (!actions.gear(gear, this::dispatched))
        {
            return;
        }
        if (npc.isDead() || player.getInteracting() == npc)
        {
            actions.status("Fighting " + brother.getNpcName());
            return;
        }
        actions.attack(npc, this::dispatched);
    }

    private boolean observeProgress(NPC npc)
    {
        int health = npc.getHealthRatio();
        if (trackedNpc != npc)
        {
            trackedNpc = npc;
            lastHealthRatio = health;
            progressTick = actions.tick();
        }
        else if (health >= 0 && (lastHealthRatio < 0 || health < lastHealthRatio))
        {
            lastHealthRatio = health;
            progressTick = actions.tick();
        }
        int timeout = health < 0 ? 150 : 100;
        if (!npc.isDead() && actions.tick() - progressTick >= timeout)
        {
            actions.fail("Combat stalled; check weapon charges, ammunition and autocast");
            return false;
        }
        return true;
    }

    private void dispatched()
    {
        lastDispatchTick = actions.tick();
    }
}
