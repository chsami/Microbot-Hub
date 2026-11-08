package net.runelite.client.plugins.microbot.fourtifystormenteddemons.tasks;

import java.util.Comparator;
import java.util.function.IntSupplier;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.config.TormentedDemonsConfig;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.DemonFight;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TempleData;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripPhase;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple.TripState;
import net.runelite.client.plugins.microbot.fourtifystormenteddemons.util.action.DemonActions;

/** Attacks the engaged demon, or the nearest free demon in the selected room that has finished summoning. */
public final class CombatTask implements FourtifysTask
{
    private static final int OBSERVE_TICKS = 3;
    private final Client client;
    private final DemonActions actions;
    private final TormentedDemonsConfig config;
    private final DemonFight fight;
    private final TripState trip;
    private final IntSupplier tick;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Waiting for a Tormented Demon";

    public CombatTask(Client client, DemonActions actions, TormentedDemonsConfig config, DemonFight fight, TripState trip, IntSupplier tick)
    {
        this.client = client;
        this.actions = actions;
        this.config = config;
        this.fight = fight;
        this.trip = trip;
        this.tick = tick;
    }

    /** The engaged demon, or the nearest demon in the room that no other player is fighting. */
    NPC choose()
    {
        if (trip.getTarget() != null)
        {
            return trip.getTarget();
        }
        Player player = actions.player();
        return actions.demons().stream()
            .filter(npc -> config.room().contains(npc.getWorldLocation()))
            .filter(npc -> npc.getAnimation() != TempleData.SUMMON_ANIMATION)
            .filter(npc -> npc.getInteracting() == null || npc.getInteracting() == player)
            .filter(npc -> TargetRange.npcDistance(client, npc) <= TargetRange.NPC_SEARCH_RADIUS)
            .min(Comparator.comparingInt(npc -> TargetRange.npcDistance(client, npc)))
            .orElse(null);
    }

    @Override
    public boolean isValid()
    {
        return trip.getPhase() == TripPhase.FIGHTING
            && (trip.getTarget() != null || config.room().contains(actions.position()));
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        int now = tick.getAsInt();
        if (fight.lockedOut(now))
        {
            status = "Waiting for the fire bombs to land";
            return;
        }
        if (trip.isWaitingForDrop(now))
        {
            // The drop is looted before the next demon is taken on.
            status = "Waiting for the drop";
            return;
        }
        NPC target = choose();
        Player player = actions.player();
        if (target == null)
        {
            status = "Waiting for a Tormented Demon";
            return;
        }
        if (target.getAnimation() == TempleData.SUMMON_ANIMATION)
        {
            status = "Waiting for the demon to finish summoning";
            return;
        }
        if (player != null && player.getInteracting() == target && !trip.isAttackNeeded(now))
        {
            status = "Fighting Tormented Demon";
            return;
        }
        status = "Attacking Tormented Demon";
        if (now - requestedAt >= OBSERVE_TICKS)
        {
            actions.attack(target, () ->
            {
                requestedAt = now;
                trip.attackSent();
            });
        }
    }
}
