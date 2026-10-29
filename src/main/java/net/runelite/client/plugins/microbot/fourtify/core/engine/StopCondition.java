package net.runelite.client.plugins.microbot.fourtify.core.engine;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * A reason to end the run at once, checked by the {@link Runner} before any task runs, such as dying or
 * running out of food at low hitpoints. Stopping says why, so the verdict explains itself.
 */
public final class StopCondition {
    private final String reason;
    private final Predicate<GameState> when;

    public StopCondition(String reason, Predicate<GameState> when) {
        this.reason = reason;
        this.when = when;
    }

    /** Hitpoints at 0: the player is dying, and anything carried may be lost. */
    public static StopCondition died() {
        return new StopCondition("the player died", state -> state.hitpoints() <= 0 && state.level(Skill.HITPOINTS) > 0);
    }

    /**
     * Hitpoints at or below {@code percent} of the maximum with none of these foods carried: carrying on could
     * end in death.
     */
    public static StopCondition lowHitpointsWithoutFood(int percent, Collection<Integer> foodIds) {
        return new StopCondition("hitpoints low with no food left",
                state -> hitpointsPercent(state) <= percent && !state.hasAny(foodIds));
    }

    /**
     * The game said a deposit did not fit in the bank. Banking cannot go on, and the inventory stays full.
     * The text comes from the server.
     */
    public static StopCondition bankFull() {
        return new StopCondition("the bank is full",
                state -> state.messages().stream().anyMatch(message -> message.toLowerCase().contains("your bank cannot hold")));
    }

    /** Current hitpoints as a percentage of the maximum, the real Hitpoints level. */
    public static int hitpointsPercent(GameState state) {
        int max = state.level(Skill.HITPOINTS);
        return max <= 0 ? 100 : state.hitpoints() * 100 / max;
    }

    public boolean holds(GameState state) {
        return when.test(state);
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return reason;
    }
}
