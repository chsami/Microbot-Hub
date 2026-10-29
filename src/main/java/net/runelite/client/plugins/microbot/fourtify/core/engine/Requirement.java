package net.runelite.client.plugins.microbot.fourtify.core.engine;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.function.Function;
import java.util.function.Predicate;

/** A check on the game state plus readable text, so a blocked task can say exactly what is missing. */
public final class Requirement {
    private final String text;
    private final Predicate<GameState> check;
    /** Text that depends on the state, or null: what is missing now (a spot is only gated for some of the accounts). */
    private final Function<GameState, String> explain;

    public Requirement(String text, Predicate<GameState> check) {
        this(text, check, null);
    }

    private Requirement(String text, Predicate<GameState> check, Function<GameState, String> explain) {
        this.text = text;
        this.check = check;
        this.explain = explain;
    }

    /** As the constructor, with a longer text for a state that does not meet it, such as what exactly is missing. */
    public static Requirement explained(String text, Predicate<GameState> check, Function<GameState, String> explain) {
        return new Requirement(text, check, explain);
    }

    /** Real (unboosted) level of at least {@code level}. */
    public static Requirement level(Skill skill, int level) {
        return new Requirement(skill.getName() + " " + level, state -> state.level(skill) >= level);
    }

    /** The quest finished; the snapshot must hold it ({@code Surroundings.quest}). */
    public static Requirement quest(Quest quest) {
        return new Requirement("the quest " + quest.getName(), state -> state.questState(quest) == QuestState.FINISHED);
    }

    /** The quest started or further on (a stage the game opens when it starts), not only finished. */
    public static Requirement questStarted(Quest quest) {
        return new Requirement("the quest " + quest.getName() + " started", state -> state.questState(quest) != QuestState.NOT_STARTED);
    }

    public boolean isMet(GameState state) {
        return check.test(state);
    }

    /** The text for a state that does not meet it: the same as {@link #toString()} unless the requirement explains itself. */
    public String text(GameState state) {
        return explain == null ? text : explain.apply(state);
    }

    @Override
    public String toString() {
        return text;
    }
}
