package net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.tasks;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.dream.Hazards;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.action.NightmareActions;
import net.runelite.client.plugins.microbot.fourtifysphosanisnightmare.util.task.FourtifysTask;

/** Steps off a tile her rifts, spores, flowers or surge are about to hit. */
public final class DodgeTask implements FourtifysTask
{
    static final int REACH = 4;
    private final NightmareActions actions;
    private final Hazards hazards;
    private final IntSupplier tick;
    private final Function<WorldPoint, Collection<WorldPoint>> reachable;
    private int requestedAt = Integer.MIN_VALUE / 2;
    private String status = "Dodging";

    public DodgeTask(NightmareActions actions, Hazards hazards, IntSupplier tick, Function<WorldPoint, Collection<WorldPoint>> reachable)
    {
        this.actions = actions;
        this.hazards = hazards;
        this.tick = tick;
        this.reachable = reachable;
    }

    @Override
    public boolean isValid()
    {
        WorldPoint position = actions.position();
        return position != null && actions.inInstance() && requestedAt != tick.getAsInt() && hazards.isDangerous(position);
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        WorldPoint position = actions.position();
        NPC boss = actions.boss();
        WorldPoint tile = hazards.escape(position, reachable.apply(position), boss == null ? null : boss.getWorldArea());
        if (tile == null)
        {
            status = "No safe tile nearby";
            return;
        }
        status = "Dodging";
        int now = tick.getAsInt();
        actions.dodge(tile, () -> requestedAt = now);
    }
}
