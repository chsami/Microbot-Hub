package net.runelite.client.plugins.microbot.fourtify.core.game;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Requirement;

import java.util.Collections;
import java.util.Set;

/**
 * What an account needs to use an area or a kind of work: a quest, or a real (unboosted) skill level. The same text
 * names it in a stop message ("the quest Royal Trouble", "Fishing 68"). A gate that reads quests lists them, and the
 * activity asks for them in its {@link Surroundings} so the snapshot holds them (a quest not in the snapshot counts as
 * not started, which shuts the gate).
 */
public final class Gate {
    private final Requirement requirement;
    private final Set<Quest> quests;

    private Gate(Requirement requirement, Set<Quest> quests) {
        this.requirement = requirement;
        this.quests = quests;
    }

    /** The quest finished. */
    public static Gate quest(Quest quest) {
        return new Gate(Requirement.quest(quest), Collections.singleton(quest));
    }

    /** The quest started or further on. */
    public static Gate questStarted(Quest quest) {
        return new Gate(Requirement.questStarted(quest), Collections.singleton(quest));
    }

    /**
     * A quest at a state, as the hand-kept gate enums write it: {@link QuestState#FINISHED} needs the quest finished,
     * {@link QuestState#IN_PROGRESS} needs it started or further on (finished counts). {@code NOT_STARTED} needs
     * nothing, so it is an error.
     */
    public static Gate of(Quest quest, QuestState state) {
        switch (state) {
            case FINISHED:
                return quest(quest);
            case IN_PROGRESS:
                return questStarted(quest);
            default:
                throw new IllegalArgumentException("a gate on " + quest + " in state " + state + " needs nothing");
        }
    }

    /** A real (unboosted) level of at least {@code level}, 1 to 126 (the highest level a player can show). */
    public static Gate skill(Skill skill, int level) {
        if (level < 1 || level > 126) {
            throw new IllegalArgumentException("a gate on " + skill + " needs a level from 1 to 126, not " + level);
        }
        return new Gate(Requirement.level(skill, level), Collections.emptySet());
    }

    public boolean isMet(GameState state) {
        return requirement.isMet(state);
    }

    public Requirement requirement() {
        return requirement;
    }

    /** The quests this gate reads. */
    public Set<Quest> quests() {
        return quests;
    }

    /** What is missing, in player words, such as "the quest Royal Trouble". */
    @Override
    public String toString() {
        return requirement.toString();
    }
}
