package net.runelite.client.plugins.microbot.fourtify.core.tasks.combat;

import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.Area;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Attacks an NPC of these names inside the area when not already fighting: one attacking the player first, else
 * the nearest. It must have "Attack" and be alive. In single-way combat (most of the world) an NPC can fight only one
 * opponent, so one after another player is skipped; in multi-way combat it is not. The activity asks for the
 * {@link #MULTIWAY_VARBIT} in its surroundings; without it, combat reads as single-way. The attack worked when the NPC is the player's target, or the player walks, within
 * {@value #WAIT_TICKS} ticks.
 */
public class AttackNpc implements Task {
    /** 1 in multi-way combat areas ({@code MULTIWAY_INDICATOR}). */
    public static final int MULTIWAY_VARBIT = VarbitID.MULTIWAY_INDICATOR;
    static final int WAIT_TICKS = 3;
    static final String ATTACK = "Attack";

    private final Collection<String> names;
    private final Function<GameState, ? extends Area> area;

    public AttackNpc(Collection<String> names, Function<GameState, ? extends Area> area) {
        this.names = names;
        this.area = area;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return !WaitWhileFighting.fighting(state) && !state.isBankOpen() && target(state) != null;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        Npc target = target(state);
        if (!act.interact(target, ATTACK)) {
            throw new IllegalStateException("could not attack " + target.getName());
        }
        return new WaitFor("attack the " + target.getName(), WAIT_TICKS, (before, now) -> now.isMoving()
                || now.npcs().stream().anyMatch(n -> n.getIndex() == target.getIndex() && n.isPlayerTarget()));
    }

    /** The NPC to attack: one attacking the player first, else the nearest free one inside the area. */
    Npc target(GameState state) {
        Area here = area.apply(state);
        if (here == null) {
            return null;
        }
        boolean multiway = state.varbit(MULTIWAY_VARBIT) == 1;
        return state.npcs().stream()
                .filter(n -> names.contains(n.getName()) && n.hasAction(ATTACK) && n.getHealthPercent() != 0)
                .filter(n -> n.getLocation().distanceTo(here.getCentre()) <= here.getRadius())
                .filter(n -> multiway || !n.isInteractingWithOther())
                .min(Comparator.comparing((Npc n) -> !n.isInteractingWithPlayer())
                        .thenComparingInt(n -> state.distanceTo(n.getLocation())))
                .orElse(null);
    }
}
