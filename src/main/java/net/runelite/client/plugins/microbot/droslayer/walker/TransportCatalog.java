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

    /**
     * Compatibility hook for Dro's additive transport bridge. Ordinary route data remains the
     * exact Efficient Walker catalogue; shared Dro transports are not bulk-injected here.
     */
    static Transport sharedSource(TransportEdge edge)
    {
        return null;
    }

    /** Actions allowed to carry skill requirements in the ordinary transport catalogue. */
    private static final Set<String> SKILL_GATED_ACTIONS = Set.of(
        "Jump", "Open", "Climb-over", "Jump-to", "Climb-down", "Enter", "Mine", "Jump-over"
    );

    /** Actions allowed to carry quest requirements in the ordinary transport catalogue. */
    private static final Set<String> QUEST_GATED_ACTIONS = Set.of(
        "Open", "Pass-through", "Push", "Exit", "Exit-through", "Pass", "Enter", "Climb-down", "Climb-up"
    );

    private static final String TRANSPORTS = "/net/runelite/client/plugins/microbot/droslayer/walker/transports.tsv";
    private static final String SHORTCUTS = "/net/runelite/client/plugins/microbot/droslayer/walker/agility_shortcuts.tsv";
    private static final String FAIRY_RINGS = "/net/runelite/client/plugins/microbot/droslayer/walker/fairy_rings.tsv";
    private static final String QUETZALS = "/net/runelite/client/plugins/microbot/droslayer/walker/quetzals.tsv";

    private TransportCatalog() {}

    static List<TransportEdge> loadAllTransports()
    {
        try (Reader transports = openResource(TRANSPORTS); Reader shortcuts = openResource(SHORTCUTS))
        {
            ArrayList<TransportEdge> result = new ArrayList<>();
            result.addAll(parseTransports(transports));
            result.addAll(parseAgilityShortcuts(shortcuts));
            result.addAll(StrongholdSecurity.createPortalTransports());
            result.addAll(createBuiltInTransports());
            result.addAll(loadFairyRings().getTransports());
            result.addAll(loadQuetzals().getTransports());
            return Collections.unmodifiableList(result);
        }
        catch (IOException ex)
        {
            log.warn("Could not load transport data; transport routes are unavailable", ex);
            return Collections.emptyList();
        }
    }

    static net.runelite.client.plugins.microbot.droslayer.walker.FairyRingNetwork loadFairyRings()
    {
        try (Reader reader = openResource(FAIRY_RINGS))
        {
            return net.runelite.client.plugins.microbot.droslayer.walker.FairyRingNetwork.readNetwork(reader);
        }
        catch (IOException | IllegalArgumentException ex)
        {
            throw new IllegalStateException("Could not load fairy ring data", ex);
        }
    }

    static QuetzalNetwork loadQuetzals()
    {
        try (Reader reader = openResource(QUETZALS))
        {
            return QuetzalNetwork.readNetwork(reader);
        }
        catch (IOException | IllegalArgumentException ex)
        {
            throw new IllegalStateException("Could not load quetzal data", ex);
        }
    }

    static TransportEdge createFairyRingTransport(WorldPoint origin, WorldPoint destination, int objectId,
                       String skills, String quests, String varbits, String varPlayers)
    {
        return new TransportEdge(origin, destination, "Configure", "Fairy ring", objectId,
            parseSkillRequirements(skills), Collections.emptyList(), parseVarRequirements(varbits), parseVarRequirements(varPlayers), parseQuestRequirements(quests),
            false, new int[0]);
    }

    /**
     * Load a bundled resource, with source-tree fallbacks retained for IDE/development launches.
     */
    private static Reader openResource(String resource) throws IOException
    {
        InputStream stream = WalkerEngineResources.open(resource);
        if (stream == null)
        {
            stream = TransportCatalog.class.getResourceAsStream(resource);
        }
        if (stream != null)
        {
            return new InputStreamReader(stream, StandardCharsets.UTF_8);
        }

        String relative = resource.startsWith("/") ? resource.substring(1) : resource;
        Path[] candidates = {
            Path.of("src/main/resources", relative),
            Path.of("platform/client/src/main/resources", relative)
        };
        for (Path candidate : candidates)
        {
            if (Files.isRegularFile(candidate))
            {
                return Files.newBufferedReader(candidate, StandardCharsets.UTF_8);
            }
        }

        throw new IOException("Missing Efficient Walker resource: " + resource);
    }

    static List<TransportEdge> parseTransports(Reader reader) throws IOException
    {
        return parseTransports(reader, false);
    }

    static List<TransportEdge> parseAgilityShortcuts(Reader reader) throws IOException
    {
        return parseTransports(reader, true);
    }

    static TransportEdge createObjectTransport(WorldPoint origin, WorldPoint destination, String action, String target, int objectId)
    {
        return new TransportEdge(origin, destination, action, target, objectId,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), false, new int[0]);
    }

    private static List<TransportEdge> createBuiltInTransports()
    {
        return List.of(
            createNpcTransport(
                new WorldPoint(1824, 3691, 0),
                new WorldPoint(3055, 3242, 1),
                "Port Sarim",
                "Veos / Cabin Boy Herbert",
                10726, 10727, 10932),
            createNpcTransport(
                new WorldPoint(3055, 3245, 0),
                new WorldPoint(1824, 3695, 1),
                "Port Piscarilius",
                "Veos / Cabin Boy Herbert",
                8630, 10724, 10933)
        );
    }

    static TransportEdge createNpcTransport(WorldPoint origin, WorldPoint destination, String action, String target, int... ids)
    {
        return new TransportEdge(origin, destination, action, target, -1,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), false,
            ids == null ? new int[0] : ids.clone());
    }

    private static List<TransportEdge> parseTransports(Reader reader, boolean shortcut) throws IOException
    {
        ArrayList<TransportEdge> result = new ArrayList<>();
        BufferedReader buffered = new BufferedReader(reader);
        String line;
        while ((line = buffered.readLine()) != null)
        {
            TransportEdge transport = parseTransportRow(line, shortcut);
            if (transport != null)
            {
                result.add(transport);
            }
        }
        markRestrictedDoors(result);
        return Collections.unmodifiableList(result);
    }

    /** Mark adjacent Open/Pass edges which duplicate a requirement-gated edge with the same id. */
    private static void markRestrictedDoors(List<TransportEdge> transports)
    {
        Set<Integer> gatedAdjacentIds = new HashSet<>();
        for (TransportEdge transport : transports)
        {
            if (isRequirementGatedDoor(transport))
            {
                gatedAdjacentIds.add(transport.objectId);
            }
        }
        for (TransportEdge transport : transports)
        {
            transport.restrictedDoor = transport.isAdjacentDoor() && gatedAdjacentIds.contains(transport.objectId);
        }
    }

    private static TransportEdge parseTransportRow(String line, boolean shortcut)
    {
        if (line == null || line.isEmpty() || line.charAt(0) == '#')
        {
            return null;
        }

        try
        {
            String[] fields = line.split("\\t", -1);
            if (fields.length < 8)
            {
                return null;
            }

            String objectInfo = fields[2];
            int firstSpace = objectInfo.indexOf(' ');
            int lastSpace = objectInfo.lastIndexOf(' ');
            if (firstSpace <= 0 || lastSpace <= firstSpace || lastSpace >= objectInfo.length() - 1)
            {
                return null;
            }

            String action = objectInfo.substring(0, firstSpace);
            if (!shortcut && !hasSupportedRequirementColumns(fields, action))
            {
                return null;
            }

            WorldPoint origin = parseWorldPoint(fields[0]);
            WorldPoint destination = parseWorldPoint(fields[1]);
            String target = objectInfo.substring(firstSpace + 1, lastSpace);
            int objectId;
            // Four preserved Swan Song rows omitted the object id. Resolve only these exact
            // gate edges using the ids in Dro's maintained transport catalogue.
            if ("Open Colony gate".equals(objectInfo)
                && origin.getPlane() == 0 && destination.getPlane() == 0
                && origin.getX() == destination.getX()
                && (origin.getX() == 2343 || origin.getX() == 2344)
                && Math.min(origin.getY(), destination.getY()) == 3662
                && Math.max(origin.getY(), destination.getY()) == 3663)
            {
                target = "Colony gate";
                objectId = origin.getX() == 2343
                    ? net.runelite.api.gameval.ObjectID.SWAN_DOOR_L
                    : net.runelite.api.gameval.ObjectID.SWAN_DOOR_R;
            }
            else
            {
                objectId = Integer.parseInt(objectInfo.substring(lastSpace + 1));
            }
            if ((objectId == 44050 || objectId == 44051) && origin.getPlane() == 0
                && Math.min(origin.getX(), destination.getX()) == 3267
                && Math.max(origin.getX(), destination.getX()) == 3268
                && origin.getY() >= 3227 && origin.getY() <= 3228)
            {
                return null;
            }

            List<SkillRequirement> skills = parseSkillRequirements(fields[3]);
            List<ItemRequirementAlternatives> items = parseItemRequirements(fields[4]);
            List<VarRequirement> varbits;
            List<VarRequirement> varPlayers;
            List<Quest> quests;

            if (shortcut)
            {
                varbits = parseVarRequirements(fields.length > 5 ? fields[5] : "");
                varPlayers = parseVarRequirements(fields.length > 6 ? fields[6] : "");
                quests = parseQuestRequirements(fields.length > 8 ? fields[8] : "");
            }
            else
            {
                quests = parseQuestRequirements(fields.length > 5 ? fields[5] : "");
                varbits = parseVarRequirements(fields.length > 6 ? fields[6] : "");
                varPlayers = parseVarRequirements(fields.length > 7 ? fields[7] : "");
            }

            boolean agility = shortcut || skills.stream().anyMatch(TransportCatalog::isAgilityRequirement);
            return new TransportEdge(origin, destination, action, target, objectId,
                skills, items, varbits, varPlayers, quests, agility, new int[0]);
        }
        catch (IllegalArgumentException ex)
        {
            log.warn("Skipping invalid transport data row", ex);
            return null;
        }
    }

    private static List<SkillRequirement> parseSkillRequirements(String value)
    {
        if (value == null || value.isEmpty())
        {
            return Collections.emptyList();
        }

        ArrayList<SkillRequirement> result = new ArrayList<>();
        for (String requirement : value.split(";"))
        {
            if (requirement.isEmpty())
            {
                continue;
            }
            int split = requirement.indexOf(' ');
            if (split <= 0 || split >= requirement.length() - 1)
            {
                throw new IllegalArgumentException("Invalid skill requirement");
            }
            int level = Integer.parseInt(requirement.substring(0, split));
            String skillName = requirement.substring(split + 1).toUpperCase().replace(' ', '_');
            result.add(new SkillRequirement(Skill.valueOf(skillName), level));
        }
        return result;
    }

    private static List<ItemRequirementAlternatives> parseItemRequirements(String value)
    {
        if (value == null || value.isEmpty())
        {
            return Collections.emptyList();
        }

        String normalized = value.replace(" ", "");
        ArrayList<ItemRequirementAlternatives> groups = new ArrayList<>();
        try
        {
            for (String andPart : normalized.split("&"))
            {
                if (andPart.isEmpty())
                {
                    continue;
                }
                ArrayList<ItemRequirement> alternatives = new ArrayList<>();
                for (String alternative : andPart.split("\\|"))
                {
                    int split = alternative.lastIndexOf('=');
                    if (split <= 0 || split >= alternative.length() - 1)
                    {
                        throw new IllegalArgumentException("Invalid item requirement");
                    }
                    String name = alternative.substring(0, split).toUpperCase();
                    int quantity = Integer.parseInt(alternative.substring(split + 1));
                    alternatives.add(new ItemRequirement(name, quantity));
                }
                groups.add(new ItemRequirementAlternatives(alternatives));
            }
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("Invalid item requirement", ex);
        }
        return groups;
    }

    /** Ordinary transports deliberately exclude item-gated rows and restrict gated action types. */
    private static boolean hasSupportedRequirementColumns(String[] fields, String action)
    {
        if (!fields[4].isEmpty())
        {
            return false;
        }
        if (!fields[3].isEmpty() && !SKILL_GATED_ACTIONS.contains(action))
        {
            return false;
        }
        return fields.length <= 5 || fields[5].isEmpty() || QUEST_GATED_ACTIONS.contains(action);
    }

    private static List<VarRequirement> parseVarRequirements(String value)
    {
        if (value == null || value.isEmpty())
        {
            return Collections.emptyList();
        }

        ArrayList<VarRequirement> result = new ArrayList<>();
        try
        {
            for (String requirement : value.split(";"))
            {
                if (requirement.isEmpty())
                {
                    continue;
                }
                int equals = requirement.indexOf('=');
                int greater = requirement.indexOf('>');
                int split = Math.max(equals, greater);
                if (split <= 0 || split >= requirement.length() - 1)
                {
                    throw new IllegalArgumentException("Invalid transport requirement");
                }
                result.add(new VarRequirement(
                    Integer.parseInt(requirement.substring(0, split)),
                    Integer.parseInt(requirement.substring(split + 1)),
                    requirement.charAt(split)));
            }
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("Invalid transport requirement", ex);
        }
        return result;
    }

    private static List<Quest> parseQuestRequirements(String value)
    {
        if (value == null || value.isEmpty())
        {
            return Collections.emptyList();
        }

        ArrayList<Quest> result = new ArrayList<>();
        for (String questName : value.split(";"))
        {
            if (questName.isEmpty())
            {
                continue;
            }
            Quest match = null;
            for (Quest quest : Quest.values())
            {
                if (quest.getName().equals(questName))
                {
                    match = quest;
                    break;
                }
            }
            if (match == null)
            {
                throw new IllegalArgumentException("Unknown quest requirement");
            }
            result.add(match);
        }
        return result;
    }

    static WorldPoint parseWorldPoint(String value)
    {
        try
        {
            String[] parts = value.split(" ");
            if (parts.length != 3)
            {
                throw new IllegalArgumentException("Invalid transport coordinate");
            }
            return new WorldPoint(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("Invalid transport coordinate", ex);
        }
    }

    /** Requirement check used while planning: special wieldables may still be in inventory. */
    private static boolean hasPlanningItemRequirement(String name, int quantity)
    {
        if (isEquipmentRequirement(name))
        {
            return quantity <= 1 && (hasEquippedSpecialItemRequirement(name, quantity) || hasInventoryEquipmentRequirement(name));
        }
        String itemName = name.replace('_', ' ');
        int count = Rs2Inventory.count(itemName, true) + (TravelEquipment.isWearing(itemName, true) ? 1 : 0);
        return count >= quantity;
    }

    private static boolean isEquipmentRequirement(String name)
    {
        return "CLIMBING_BOOTS".equals(name)
            || "CROSSBOW".equals(name)
            || "MITH_GRAPPLE".equals(name);
    }

    /** Special transport items only count as ready once worn/equipped. */
    private static boolean hasEquippedSpecialItemRequirement(String name, int quantity)
    {
        if (quantity > 1)
        {
            return false;
        }
        if ("CLIMBING_BOOTS".equals(name))
        {
            return TravelEquipment.isWearing("Climbing boots", true);
        }
        if ("CROSSBOW".equals(name))
        {
            return TravelEquipment.isWearing("crossbow", false);
        }
        if ("MITH_GRAPPLE".equals(name))
        {
            return TravelEquipment.isWearing("Mith grapple", true);
        }
        return false;
    }

    /** Requirement check after arriving at a transport: special wieldables must already be worn. */
    private static boolean hasExecutionItemRequirement(String name, int quantity)
    {
        if (isEquipmentRequirement(name))
        {
            return hasEquippedSpecialItemRequirement(name, quantity);
        }
        String itemName = name.replace('_', ' ');
        int count = Rs2Inventory.count(itemName, true) + (TravelEquipment.isWearing(itemName, true) ? 1 : 0);
        return count >= quantity;
    }

    private static boolean hasInventoryEquipmentRequirement(String name)
    {
        if ("CLIMBING_BOOTS".equals(name))
        {
            return Rs2Inventory.count("Climbing boots", true) > 0;
        }
        if ("CROSSBOW".equals(name))
        {
            return Rs2Inventory.count("crossbow", false) > 0;
        }
        if ("MITH_GRAPPLE".equals(name))
        {
            return Rs2Inventory.count("Mith grapple", true) > 0;
        }
        return false;
    }

    static boolean equipRequirement(String name)
    {
        if ("CLIMBING_BOOTS".equals(name))
        {
            return Rs2Inventory.wield("Climbing boots");
        }
        if ("CROSSBOW".equals(name))
        {
            return Rs2Inventory.wield("crossbow");
        }
        return "MITH_GRAPPLE".equals(name) && Rs2Inventory.wield("Mith grapple");
    }

    private static boolean isAgilityRequirement(SkillRequirement requirement)
    {
        return requirement.skill == Skill.AGILITY;
    }


    private static boolean isRequirementGatedDoor(TransportEdge transport)
    {
        return transport.isAdjacentDoor() && !transport.hasNoRequirements();
    }

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

        boolean hasAlternativeIds()
        {
            return this.alternativeIds.length > 0;
        }

        boolean matchesAlternativeId(int id)
        {
            for (int candidate : this.alternativeIds)
            {
                if (candidate == id)
                {
                    return true;
                }
            }
            return false;
        }

        boolean meetsRequirements()
        {
            return meetsRequirements(
                Rs2Player::getRealSkillLevel,
                TransportCatalog::hasPlanningItemRequirement,
                Microbot::getVarbitValue,
                Microbot::getVarbitPlayerValue,
                quest -> QuestState.FINISHED.equals(Rs2Player.getQuestState(quest)));
        }

        boolean meetsRequirements(ToIntFunction<Skill> skillLevel,
                         BiPredicate<String, Integer> itemPredicate,
                         IntUnaryOperator varbitValue,
                         IntUnaryOperator varPlayerValue,
                         Predicate<Quest> questPredicate)
        {
            for (SkillRequirement requirement : this.skillRequirements)
            {
                if (!requirement.isSatisfiedBy(skillLevel))
                {
                    return false;
                }
            }
            for (ItemRequirementAlternatives requirement : this.itemRequirements)
            {
                if (!requirement.matchesAnyAlternative(itemPredicate))
                {
                    return false;
                }
            }
            for (VarRequirement requirement : this.varbitRequirements)
            {
                if (!requirement.isSatisfiedBy(varbitValue))
                {
                    return false;
                }
            }
            for (VarRequirement requirement : this.varpRequirements)
            {
                if (!requirement.isSatisfiedBy(varPlayerValue))
                {
                    return false;
                }
            }
            for (Quest quest : this.questRequirements)
            {
                if (!questPredicate.test(quest))
                {
                    return false;
                }
            }
            return true;
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

        boolean isRestrictedDoor()
        {
            return this.restrictedDoor;
        }

        boolean hasNoRequirements()
        {
            return !net.runelite.client.plugins.microbot.droslayer.walker.FairyRingNetwork.isFairyRingTransport(this)
                && this.skillRequirements.isEmpty() && this.itemRequirements.isEmpty() && this.varbitRequirements.isEmpty() && this.varpRequirements.isEmpty() && this.questRequirements.isEmpty();
        }

        boolean isAgilityShortcut()
        {
            return this.agilityShortcut;
        }

        boolean requiresGrapple()
        {
            return this.agilityShortcut && ("Grapple".equalsIgnoreCase(this.action) || requiresItem("CROSSBOW") || requiresItem("MITH_GRAPPLE"));
        }

        String getMissingEquipmentRequirement()
        {
            for (ItemRequirementAlternatives requirement : this.itemRequirements)
            {
                if (requirement.matchesAnyAlternative(TransportCatalog::hasExecutionItemRequirement))
                {
                    continue;
                }
                for (ItemRequirement alternative : requirement.alternatives)
                {
                    if (isEquipmentRequirement(alternative.itemName) && hasInventoryEquipmentRequirement(alternative.itemName))
                    {
                        return alternative.itemName;
                    }
                }
                for (ItemRequirement alternative : requirement.alternatives)
                {
                    if (isEquipmentRequirement(alternative.itemName))
                    {
                        return alternative.itemName;
                    }
                }
            }
            return null;
        }

        boolean requiresItem(String itemName)
        {
            for (ItemRequirementAlternatives requirement : this.itemRequirements)
            {
                for (ItemRequirement alternative : requirement.alternatives)
                {
                    if (itemName.equals(alternative.itemName))
                    {
                        return true;
                    }
                }
            }
            return false;
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
