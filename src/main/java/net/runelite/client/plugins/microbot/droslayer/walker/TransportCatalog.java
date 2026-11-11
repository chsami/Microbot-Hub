package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Loads ordinary transports and agility shortcuts with their skill, item,
 * quest, and variable requirements.
 */
final class TransportCatalog
{
    private static final Logger log = LoggerFactory.getLogger(TransportCatalog.class);

    /** Actions allowed to carry skill requirements in the ordinary transport catalogue. */
    private static final Set<String> SKILL_GATED_ACTIONS = Set.of(
        "Jump", "Open", "Climb-over", "Jump-to", "Climb-down", "Enter", "Mine", "Jump-over"
    );

    /** Actions allowed to carry quest requirements in the ordinary transport catalogue. */
    private static final Set<String> QUEST_GATED_ACTIONS = Set.of(
        "Open", "Pass-through", "Push", "Exit", "Exit-through", "Pass", "Enter", "Climb-down", "Climb-up"
    );

    private TransportCatalog() {}


    static final class ItemRequirement
    {
        private final String itemName;
        private final int quantity;

        private ItemRequirement(String name, int quantity)
        {
            this.itemName = name;
            this.quantity = quantity;
        }
    }

    static final class ItemRequirementAlternatives
    {
        private final List<ItemRequirement> alternatives;

        private ItemRequirementAlternatives(List<ItemRequirement> alternatives)
        {
            this.alternatives = alternatives;
        }

        private boolean matchesAnyAlternative(BiPredicate<String, Integer> predicate)
        {
            for (ItemRequirement alternative : this.alternatives)
            {
                if (predicate.test(alternative.itemName, alternative.quantity))
                {
                    return true;
                }
            }
            return false;
        }
    }

    static final class SkillRequirement
    {
        private final Skill skill;
        private final int minimumLevel;

        private SkillRequirement(Skill skill, int level)
        {
            this.skill = skill;
            this.minimumLevel = level;
        }

        private boolean isSatisfiedBy(ToIntFunction<Skill> levelFunction)
        {
            return levelFunction.applyAsInt(this.skill) >= this.minimumLevel;
        }
    }

    static final class TransportEdge
    {
        final WorldPoint origin;
        final WorldPoint destination;
        final String action;
        final String target;
        final int objectId;
        private final List<VarRequirement> varbitRequirements;
        private final List<VarRequirement> varpRequirements;
        private final List<SkillRequirement> skillRequirements;
        private final List<ItemRequirementAlternatives> itemRequirements;
        private final List<Quest> questRequirements;
        private final boolean agilityShortcut;
        private final int[] alternativeIds;
        private boolean restrictedDoor;

        private TransportEdge(WorldPoint origin, WorldPoint destination, String action, String target, int objectId,
                  List<SkillRequirement> skills, List<ItemRequirementAlternatives> items, List<VarRequirement> varbits, List<VarRequirement> varPlayers,
                  List<Quest> quests, boolean agility, int[] alternativeIds)
        {
            this.origin = origin;
            this.destination = destination;
            this.action = action;
            this.target = target;
            this.objectId = objectId;
            this.varbitRequirements = varbits;
            this.varpRequirements = varPlayers;
            this.skillRequirements = skills;
            this.itemRequirements = items;
            this.questRequirements = quests;
            this.agilityShortcut = agility;
            this.alternativeIds = alternativeIds;
        }

        boolean isUnrestrictedDoor()
        {
            return isAdjacentDoor() && !this.restrictedDoor;
        }

        private boolean isAdjacentDoor()
        {
            return this.origin != null && this.destination != null
                && this.origin.getPlane() == this.destination.getPlane()
                && this.origin.distanceTo2D(this.destination) == 1
                && ("Open".equalsIgnoreCase(this.action) || "Pass".equalsIgnoreCase(this.action));
        }

    }

    static final class VarRequirement
    {
        private final int id;
        private final int value;
        private final char comparator;

        private VarRequirement(int id, int value, char comparator)
        {
            this.id = id;
            this.value = value;
            this.comparator = comparator;
        }

        private boolean isSatisfiedBy(IntUnaryOperator valueFunction)
        {
            int current = valueFunction.applyAsInt(this.id);
            return this.comparator == '=' ? current == this.value : current > this.value;
        }
    }
}
