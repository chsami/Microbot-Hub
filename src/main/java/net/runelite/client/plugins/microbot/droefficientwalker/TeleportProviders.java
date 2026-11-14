package net.runelite.client.plugins.microbot.droefficientwalker;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.*;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

/**
 * Builds spell, item, and equipment teleport providers and evaluates their requirements.
 */
final class TeleportProviders
{
    private static final Spell APE_ATOLL_TELEPORT = new Spell()
    {
        @Override
        public MagicAction getMagicAction()
        {
            return MagicAction.TELEPORT_APE_ATOLL;
        }

        @Override
        public HashMap<Runes, Integer> getRequiredRunes()
        {
            HashMap<Runes, Integer> runes = new HashMap<>();
            runes.put(Runes.FIRE, 2);
            runes.put(Runes.WATER, 2);
            runes.put(Runes.LAW, 2);
            return runes;
        }

        @Override
        public Rs2Spellbook getSpellbook()
        {
            return Rs2Spellbook.MODERN;
        }

        @Override
        public int getRequiredLevel()
        {
            return getMagicAction().getLevel();
        }
    };

    private static final int[] GAMES_NECKLACE_IDS = {3867, 3865, 3863, 3861, 3859, 3857, 3855, 3853};
    private static final int[] RING_OF_DUELING_IDS = {2566, 2564, 2562, 2560, 2558, 2556, 2554, 2552};
    private static final int[] COMBAT_BRACELET_IDS = {11124, 11122, 11120, 11118, 11974, 11972};
    private static final int[] SKILLS_NECKLACE_IDS = {11111, 11109, 11107, 11105, 11970, 11968};
    private static final int[] AMULET_OF_GLORY_IDS = {1706, 1708, 1710, 1712, 11976, 11978, 10360, 10358, 10356, 10354, 11966, 11964, 19707};
    private static final int[] RING_OF_WEALTH_IDS = {11988, 11986, 11984, 11982, 11980, 20790, 20789, 20788, 20787, 20786};
    private static final int[] SLAYER_RING_IDS = {11873, 11872, 11871, 11870, 11869, 11868, 11867, 11866, 21268};
    private static final int[] DIGSITE_PENDANT_IDS = {11190, 11191, 11192, 11193, 11194};
    private static final int[] RING_OF_RETURNING_IDS = {21138, 21136, 21134, 21132, 21129};
    private static final int[] NECKLACE_OF_PASSAGE_IDS = {21155, 21153, 21151, 21149, 21146};

    private TeleportProviders() {}

    static boolean isItemAllowedOnWorld(Client client, int itemId)
    {
        ItemComposition item = client.getItemDefinition(itemId);
        return item != null && (!item.isMembers() || client.getWorldType().contains(WorldType.MEMBERS));
    }

    static boolean isSpellAllowedOnWorld(Client client, Spell spell)
    {
        return spell != null && spell.getMagicAction() != null
            && (!spell.getMagicAction().isMembers() || client.getWorldType().contains(WorldType.MEMBERS));
    }

    static List<TeleportProvider> createProviders()
    {
        ArrayList<TeleportProvider> providers = new ArrayList<>(List.of(
            new TeleportProvider("Varrock Teleport", TeleportProviders::getVarrockDestination, Rs2Spells.VARROCK_TELEPORT, 14286874, "Cast", TeleportProviders::getVarrockWidgetOption, -1, -1, () -> null, null),
            new TeleportProvider("Varrock Teleport", new WorldPoint(3213, 3424, 0), 8007, TeleportProviders::getVarrockTabletAction),
            new TeleportProvider("Lumbridge Teleport", new WorldPoint(3221, 3218, 0), Rs2Spells.LUMBRIDGE_TELEPORT, 14286877, "Cast", 8008, "Break"),
            new TeleportProvider("Falador Teleport", new WorldPoint(2965, 3378, 0), Rs2Spells.FALADOR_TELEPORT, 14286880, "Cast", 8009, "Break"),
            new TeleportProvider("Teleport to House", TeleportProviders::getHousePortalDestination, Rs2Spells.TELEPORT_TO_HOUSE, 14286882, "Outside", TeleportProviders::getHouseWidgetOption, -1, 8013, TeleportProviders::getHouseTabletAction, null),
            new TeleportProvider("Camelot Teleport", TeleportProviders::getCamelotDestination, Rs2Spells.CAMELOT_TELEPORT, 14286885, "Cast", TeleportProviders::getCamelotWidgetOption, -1, -1, () -> null, null),
            new TeleportProvider("Camelot Teleport", new WorldPoint(2757, 3478, 0), 8010, TeleportProviders::getCamelotTabletAction),
            new TeleportProvider("Kourend Castle Teleport", new WorldPoint(1643, 3672, 0), Rs2Spells.KOUREND_CASTLE_TELEPORT, 14286887, "Cast", 28790, "Break", Quest.CLIENT_OF_KOUREND),
            new TeleportProvider("Ardougne Teleport", new WorldPoint(2664, 3306, 0), Rs2Spells.ARDOUGNE_TELEPORT, 14286892, "Cast", 8011, "Break", Quest.PLAGUE_CITY),
            new TeleportProvider("Civitas illa Fortis Teleport", new WorldPoint(1681, 3133, 0), Rs2Spells.CIVITAS_ILLA_FORTIS_TELEPORT, 14286894, "Cast", 28824, "Break", Quest.TWILIGHTS_PROMISE),
            new TeleportProvider("Watchtower Teleport", TeleportProviders::getWatchtowerDestination, Rs2Spells.WATCHTOWER_TELEPORT, 14286898, "Cast", TeleportProviders::getWatchtowerWidgetOption, -1, 8012, TeleportProviders::getWatchtowerTabletAction, Quest.WATCHTOWER),
            new TeleportProvider("Trollheim Teleport", new WorldPoint(2891, 3678, 0), Rs2Spells.TROLLHEIM_TELEPORT, 14286905, "Cast", -1, null, Quest.EADGARS_RUSE),
            new TeleportProvider("Ape Atoll Teleport", TeleportProviders::getApeAtollDestination, APE_ATOLL_TELEPORT, 14286908, "Cast", TeleportProviders::getApeAtollWidgetOption, 1963, -1, TeleportProviders::getApeAtollTabletAction, Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI)
        ));
        providers.addAll(createItemTeleportProviders());
        return List.copyOf(providers);
    }

    private static List<TeleportProvider> createItemTeleportProviders()
    {
        return List.of(
            new TeleportProvider("Games necklace: Burthorpe", new WorldPoint(2898, 3554, 0), GAMES_NECKLACE_IDS, "Burthorpe"),
            new TeleportProvider("Games necklace: Barbarian Outpost", new WorldPoint(2520, 3571, 0), GAMES_NECKLACE_IDS, "Barbarian Outpost"),
            new TeleportProvider("Games necklace: Tears of Guthix", new WorldPoint(3245, 9500, 2), GAMES_NECKLACE_IDS, "Tears of Guthix", Quest.TEARS_OF_GUTHIX),
            new TeleportProvider("Games necklace: Wintertodt Camp", new WorldPoint(1624, 3938, 0), GAMES_NECKLACE_IDS, "Wintertodt Camp", TeleportProviders::isWintertodtTeleportUnlocked),
            new TeleportProvider("Ring of dueling: Emir's Arena", new WorldPoint(3315, 3235, 0), RING_OF_DUELING_IDS, "Emir's Arena"),
            new TeleportProvider("Ring of dueling: Castle Wars", new WorldPoint(2441, 3091, 0), RING_OF_DUELING_IDS, "Castle Wars"),
            new TeleportProvider("Ring of dueling: Fortis Colosseum", new WorldPoint(1793, 3107, 0), RING_OF_DUELING_IDS, "Fortis Colosseum", TeleportProviders::hasFortisColosseumGlory),
            new TeleportProvider("Combat bracelet: Warriors' Guild", new WorldPoint(2883, 3549, 0), COMBAT_BRACELET_IDS, "Warriors' Guild"),
            new TeleportProvider("Combat bracelet: Champions' Guild", new WorldPoint(3189, 3368, 0), COMBAT_BRACELET_IDS, "Champions' Guild"),
            new TeleportProvider("Combat bracelet: Edgeville Monastery", new WorldPoint(3053, 3487, 0), COMBAT_BRACELET_IDS, "Monastery"),
            new TeleportProvider("Combat bracelet: Ranging Guild", new WorldPoint(2654, 3441, 0), COMBAT_BRACELET_IDS, "Ranging Guild"),
            new TeleportProvider("Skills necklace: Fishing Guild", new WorldPoint(2613, 3390, 0), SKILLS_NECKLACE_IDS, "Fishing Guild"),
            new TeleportProvider("Skills necklace: Mining Guild", new WorldPoint(3049, 9762, 0), SKILLS_NECKLACE_IDS, "Mining Guild"),
            new TeleportProvider("Skills necklace: Crafting Guild", new WorldPoint(2934, 3294, 0), SKILLS_NECKLACE_IDS, "Crafting Guild"),
            new TeleportProvider("Skills necklace: Cooking Guild", new WorldPoint(3145, 3438, 0), SKILLS_NECKLACE_IDS, "Cooking Guild"),
            new TeleportProvider("Skills necklace: Woodcutting Guild", new WorldPoint(1662, 3505, 0), SKILLS_NECKLACE_IDS, "Woodcutting Guild"),
            new TeleportProvider("Skills necklace: Farming Guild", new WorldPoint(1248, 3727, 0), SKILLS_NECKLACE_IDS, "Farming Guild", TeleportProviders::hasFarmingGuildLevel),
            new TeleportProvider("Amulet of glory: Edgeville", new WorldPoint(3087, 3496, 0), AMULET_OF_GLORY_IDS, "Edgeville"),
            new TeleportProvider("Amulet of glory: Karamja", new WorldPoint(2918, 3176, 0), AMULET_OF_GLORY_IDS, "Karamja"),
            new TeleportProvider("Amulet of glory: Draynor Village", new WorldPoint(3105, 3251, 0), AMULET_OF_GLORY_IDS, "Draynor Village"),
            new TeleportProvider("Amulet of glory: Al Kharid", new WorldPoint(3293, 3163, 0), AMULET_OF_GLORY_IDS, "Al Kharid"),
            new TeleportProvider("Ring of wealth: Miscellania", new WorldPoint(2535, 3862, 0), RING_OF_WEALTH_IDS, "Miscellania", Quest.THRONE_OF_MISCELLANIA),
            new TeleportProvider("Ring of wealth: Grand Exchange", new WorldPoint(3162, 3480, 0), RING_OF_WEALTH_IDS, "Grand Exchange"),
            new TeleportProvider("Ring of wealth: Falador Park", new WorldPoint(2995, 3375, 0), RING_OF_WEALTH_IDS, "Falador"),
            new TeleportProvider("Ring of wealth: Dondakan", new WorldPoint(2831, 10165, 0), RING_OF_WEALTH_IDS, "Dondakan", Quest.BETWEEN_A_ROCK),
            new TeleportProvider("Slayer ring: Stronghold", new WorldPoint(2433, 3421, 0), SLAYER_RING_IDS, "Stronghold", TeleportProviders::isStrongholdSlayerRingAvailable, false),
            new TeleportProvider("Slayer ring: Slayer Tower", new WorldPoint(3423, 3536, 0), SLAYER_RING_IDS, "Slayer Tower", Quest.PRIEST_IN_PERIL, false),
            new TeleportProvider("Slayer ring: Fremennik Dungeon", new WorldPoint(2800, 9998, 0), SLAYER_RING_IDS, "Fremennik Dungeon", TeleportProviders::isFremennikSlayerRingAvailable, false),
            new TeleportProvider("Slayer ring: Tarn's Lair", new WorldPoint(3187, 4601, 0), SLAYER_RING_IDS, "Tarn's Lair", Quest.HAUNTED_MINE, false),
            new TeleportProvider("Slayer ring: Dark Beasts", new WorldPoint(2028, 4638, 0), SLAYER_RING_IDS, "Dark Beasts", Quest.MOURNINGS_END_PART_II, false),
            new TeleportProvider("Slayer ring: Wyrmscraig Cavern", new WorldPoint(2773, 8633, 0), SLAYER_RING_IDS, "Wyrmscraig Cavern", Quest.FALLEN_FROM_GRACE, false),
            new TeleportProvider("Digsite pendant: Digsite", new WorldPoint(3339, 3445, 0), DIGSITE_PENDANT_IDS, "Digsite"),
            new TeleportProvider("Digsite pendant: Fossil Island", new WorldPoint(3763, 3869, 1), DIGSITE_PENDANT_IDS, "Fossil Island", Quest.BONE_VOYAGE),
            new TeleportProvider("Digsite pendant: Lithkren", new WorldPoint(3547, 10456, 0), DIGSITE_PENDANT_IDS, "Lithkren Dungeon", TeleportProviders::isLithkrenUnlocked),
            new TeleportProvider("Necklace of passage: Wizards' Tower", new WorldPoint(3114, 3181, 0), NECKLACE_OF_PASSAGE_IDS, "Wizards' Tower"),
            new TeleportProvider("Necklace of passage: The Outpost", new WorldPoint(2431, 3348, 0), NECKLACE_OF_PASSAGE_IDS, "The Outpost"),
            new TeleportProvider("Necklace of passage: Eagles' Eyrie", new WorldPoint(3406, 3157, 0), NECKLACE_OF_PASSAGE_IDS, "Eagles' Eyrie"),
            new TeleportProvider("Necklace of passage: Wyrmscraig", new WorldPoint(2591, 2221, 0), NECKLACE_OF_PASSAGE_IDS, "Wyrmscraig", Quest.FALLEN_FROM_GRACE)
        );
    }

    private static String getVarrockTabletAction() { return varrockTabletAction(Microbot.getVarbitValue(4585)); }
    static String varrockTabletAction(int value) { return value == 1 ? "Varrock" : "Break"; }
    private static WorldPoint getVarrockDestination() { return varrockDestination(Microbot.getVarbitValue(4585)); }
    static WorldPoint varrockDestination(int value) { return value == 1 ? new WorldPoint(3164, 3478, 0) : new WorldPoint(3213, 3424, 0); }
    private static int getVarrockWidgetOption() { return redirectedWidgetOption(Microbot.getVarbitValue(4585)); }
    private static String getCamelotTabletAction() { return camelotTabletAction(Microbot.getVarbitValue(4560)); }
    static String camelotTabletAction(int value) { return value == 1 ? "Camelot" : "Break"; }
    private static WorldPoint getCamelotDestination() { return camelotDestination(Microbot.getVarbitValue(4560)); }
    static WorldPoint camelotDestination(int value) { return value == 1 ? new WorldPoint(2726, 3485, 0) : new WorldPoint(2757, 3478, 0); }
    private static int getCamelotWidgetOption() { return redirectedWidgetOption(Microbot.getVarbitValue(4560)); }
    private static WorldPoint getHousePortalDestination()
    {
        HouseLocation location = HouseLocation.getHouseLocation();
        return location == null ? null : location.getPortalLocation();
    }
    private static WorldPoint getWatchtowerDestination() { return watchtowerDestination(Microbot.getVarbitValue(4548)); }
    static WorldPoint watchtowerDestination(int value) { return value == 1 ? new WorldPoint(2584, 3097, 0) : new WorldPoint(2931, 4711, 2); }
    private static int getWatchtowerWidgetOption() { return redirectedWidgetOption(Microbot.getVarbitValue(4548)); }
    static int redirectedWidgetOption(int value) { return value == 1 ? 3 : 1; }

    private static boolean isLithkrenUnlocked()
    {
        return QuestState.FINISHED.equals(Rs2Player.getQuestState(Quest.DRAGON_SLAYER_II))
            && Microbot.getVarbitValue(6142) == 1;
    }
    private static boolean isStrongholdSlayerRingAvailable() { return true; }
    private static boolean isFremennikSlayerRingAvailable() { return true; }
    private static boolean hasFarmingGuildLevel() { return Rs2Player.getRealSkillLevel(Skill.FARMING) >= 45; }
    private static boolean hasFortisColosseumGlory() { return Microbot.getClient().getVarpValue(4130) >= 12000; }
    private static boolean isWintertodtTeleportUnlocked() { return Microbot.getVarbitValue(4897) == 1; }
    private static int getApeAtollWidgetOption() { return 1; }
    private static WorldPoint getApeAtollDestination() { return new WorldPoint(2797, 2798, 1); }
    private static String getWatchtowerTabletAction() { return "Break"; }
    private static String getHouseTabletAction() { return "Outside"; }
    private static int getHouseWidgetOption() { return 2; }
    private static String getApeAtollTabletAction() { return null; }

    static final class TeleportProvider
    {
        final String name;
        private final Supplier<WorldPoint> destinationSupplier;
        private final Spell spell;
        private final int widgetId;
        private final String widgetAction;
        private final IntSupplier widgetOptionSupplier;
        private final int requiredItemId;
        private final int[] itemIds;
        private final Supplier<String> itemActionSupplier;
        private final BooleanSupplier availability;
        private BooleanSupplier executionAvailability;
        private final boolean equipmentAllowed;

        TeleportProvider(String name, WorldPoint point, Spell spell, int widgetId, String widgetAction, int itemId, String itemAction)
        {
            this(name, () -> point, spell, widgetId, widgetAction, () -> 1, -1, itemId, () -> itemAction, null);
        }

        TeleportProvider(String name, WorldPoint point, Spell spell, int widgetId, String widgetAction, int itemId, String itemAction, Quest quest)
        {
            this(name, () -> point, spell, widgetId, widgetAction, () -> 1, -1, itemId, () -> itemAction, quest);
        }

        TeleportProvider(String name, Supplier<WorldPoint> point, Spell spell, int widgetId, String widgetAction,
          IntSupplier widgetOption, int requiredItem, int itemId, Supplier<String> itemAction, Quest quest)
        {
            this(name, point, spell, widgetId, widgetAction, widgetOption, requiredItem,
                itemId < 0 ? new int[0] : new int[]{itemId}, itemAction,
                quest == null ? () -> true : () -> QuestState.FINISHED.equals(Rs2Player.getQuestState(quest)), true);
        }

        TeleportProvider(String name, WorldPoint point, int[] itemIds, String itemAction)
        {
            this(name, point, itemIds, itemAction, () -> true);
        }

        TeleportProvider(String name, WorldPoint point, int itemId, Supplier<String> itemAction)
        {
            this(name, point, new int[]{itemId}, null, itemAction, () -> true, true);
        }

        TeleportProvider(String name, WorldPoint point, int[] itemIds, String itemAction, Quest quest)
        {
            this(name, point, itemIds, itemAction,
                () -> QuestState.FINISHED.equals(Rs2Player.getQuestState(quest)), true);
        }

        TeleportProvider(String name, WorldPoint point, int[] itemIds, String itemAction, Quest quest, boolean equipmentAllowed)
        {
            this(name, point, itemIds, itemAction,
                () -> QuestState.FINISHED.equals(Rs2Player.getQuestState(quest)), equipmentAllowed);
        }

        TeleportProvider(String name, WorldPoint point, int[] itemIds, String itemAction, BooleanSupplier available)
        {
            this(name, point, itemIds, itemAction, available, true);
        }

        TeleportProvider(String name, WorldPoint point, int[] itemIds, String itemAction, BooleanSupplier available, boolean equipmentAllowed)
        {
            this(name, () -> point, null, -1, null, () -> 1, -1, itemIds, () -> itemAction, available, equipmentAllowed);
        }

        TeleportProvider(String name, WorldPoint point, int[] itemIds, String itemAction, BooleanSupplier available,
          boolean equipmentAllowed, BooleanSupplier executionAvailable)
        {
            this(name, point, itemIds, itemAction, available, equipmentAllowed);
            this.executionAvailability = executionAvailable;
        }

        private TeleportProvider(String name, WorldPoint point, int[] itemIds, String ignoredWidgetAction,
                  Supplier<String> itemAction, BooleanSupplier available, boolean equipmentAllowed)
        {
            this(name, () -> point, null, -1, null, () -> 1, -1, itemIds, itemAction, available, equipmentAllowed);
        }

        private TeleportProvider(String name, Supplier<WorldPoint> point, Spell spell, int widgetId, String widgetAction,
                  IntSupplier widgetOption, int requiredItem, int[] itemIds, Supplier<String> itemAction,
                  BooleanSupplier available, boolean equipmentAllowed)
        {
            this.name = name;
            this.destinationSupplier = point;
            this.spell = spell;
            this.widgetId = widgetId;
            this.widgetAction = widgetAction;
            this.widgetOptionSupplier = widgetOption;
            this.requiredItemId = requiredItem;
            this.itemIds = itemIds;
            this.itemActionSupplier = itemAction;
            this.availability = available;
            this.executionAvailability = available;
            this.equipmentAllowed = equipmentAllowed;
        }

        WorldPoint getDestination()
        {
            return this.destinationSupplier.get();
        }

        int findAvailableSupplyItem(Client client, Map<Integer, Integer> supplies)
        {
            if (Rs2Player.isTeleBlocked() || !this.executionAvailability.getAsBoolean())
            {
                return -1;
            }
            for (int itemId : this.itemIds)
            {
                if (supplies.getOrDefault(itemId, 0) > 0 && TeleportProviders.isItemAllowedOnWorld(client, itemId)
                    && client.getItemDefinition(itemId).getNote() == -1)
                {
                    return itemId;
                }
            }
            return -1;
        }

        List<BankSupplyController.BankSupplyRequirement> getBankSupplyCandidates(Client client, Map<Integer, Integer> supplies, WorldPoint target)
        {
            List<BankSupplyController.BankSupplyRequirement> result = new ArrayList<>();
            if (target == null || Rs2Player.isTeleBlocked() || !this.executionAvailability.getAsBoolean())
            {
                return result;
            }
            int item = findAvailableSupplyItem(client, supplies);
            if (item >= 0)
            {
                result.add(new BankSupplyController.BankSupplyRequirement(this, target, item));
            }
            Map<Integer, Integer> requirements = getRequiredBankItems(client, supplies, -1);
            if (requirements != null && !requirements.isEmpty())
            {
                result.add(new BankSupplyController.BankSupplyRequirement(this, target, requirements));
            }
            return result;
        }

        Map<Integer, Integer> getRequiredBankItems(Client client, Map<Integer, Integer> bank, int itemId)
        {
            if (Rs2Player.isTeleBlocked() || !this.executionAvailability.getAsBoolean())
            {
                return null;
            }
            if (itemId >= 0)
            {
                if (!TeleportProviders.isItemAllowedOnWorld(client, itemId) || Arrays.stream(this.itemIds).noneMatch(id -> id == itemId))
                {
                    return null;
                }
                if (hasUsableTeleportItem(itemId))
                {
                    return Collections.emptyMap();
                }
                return bank.getOrDefault(itemId, 0) > 0 ? Map.of(itemId, 1) : null;
            }
            if (!TeleportProviders.isSpellAllowedOnWorld(client, this.spell) || !this.spell.hasRequirements())
            {
                return null;
            }
            Map<Integer, Integer> allocation = RuneRequirementAllocator.allocateRequiredRunes(Rs2Magic.getMissingRunes(this.spell), bank,
                id -> TeleportProviders.isItemAllowedOnWorld(client, id));
            if (allocation == null)
            {
                return null;
            }
            if (this.requiredItemId >= 0 && !Rs2Inventory.hasItem(this.requiredItemId))
            {
                if (!TeleportProviders.isItemAllowedOnWorld(client, this.requiredItemId) || bank.getOrDefault(this.requiredItemId, 0) < 1)
                {
                    return null;
                }
                allocation.put(this.requiredItemId, 1);
            }
            return allocation;
        }

        boolean isAvailableForRoute(WorldPoint from, WorldPoint to)
        {
            return from != null && to != null && !CollisionRoutePlanner.isWildernessBlocked(to)
                && isAvailableWithRequirements(Rs2Player.isTeleBlocked(), CollisionRoutePlanner.isWildernessBlocked(from) ? 1 : 0,
                    this::canCastSpell, this::hasUsableTeleportItem, this.availability.getAsBoolean());
        }

        boolean isAvailableWithRequirements(boolean teleBlocked, int sourceInWilderness, Predicate<Spell> spellPredicate,
                         IntPredicate itemPredicate, boolean available)
        {
            boolean spellOk = this.spell != null && spellPredicate.test(this.spell)
                && (this.requiredItemId < 0 || itemPredicate.test(this.requiredItemId));
            return !teleBlocked && sourceInWilderness == 0 && available
                && (spellOk || Arrays.stream(this.itemIds).anyMatch(itemPredicate));
        }

        boolean activate(WorldPoint from, WorldPoint to)
        {
            if (!isAvailableForRoute(from, to))
            {
                return false;
            }
            if (this.spell != null && canCastSpell(this.spell) && (this.requiredItemId < 0 || Rs2Inventory.hasItem(this.requiredItemId)) && castSpellWidget())
            {
                return true;
            }
            for (int itemId : this.itemIds)
            {
                if (!TeleportProviders.isItemAllowedOnWorld(Microbot.getClient(), itemId))
                {
                    continue;
                }
                if (Rs2Inventory.hasItem(itemId))
                {
                    return Rs2Inventory.interact(itemId, this.itemActionSupplier.get());
                }
                if (this.equipmentAllowed && TravelEquipment.isWearing(itemId))
                {
                    return Rs2Equipment.interact(itemId, this.itemActionSupplier.get());
                }
            }
            return false;
        }

        private boolean castSpellWidget()
        {
            Widget widget = Microbot.getClient().getWidget(this.widgetId);
            int option = this.widgetOptionSupplier.getAsInt();
            if (!matchesWidgetAction(widget, this.widgetAction, option))
            {
                return false;
            }
            NewMenuEntry entry = new NewMenuEntry()
                .option(this.widgetAction)
                .target(this.spell.getMagicAction().getName())
                .identifier(option)
                .type(MenuAction.CC_OP)
                .param0(-1)
                .param1(this.widgetId);
            Microbot.doInvoke(entry, new Rectangle(widget.getBounds()));
            return true;
        }

        static boolean matchesWidgetAction(Widget widget, String action, int option)
        {
            String[] actions = widget == null ? null : widget.getActions();
            return actions != null && option > 0 && option <= actions.length
                && action.equals(actions[option - 1]);
        }

        boolean isWhistleProvider()
        {
            return !this.equipmentAllowed && Arrays.equals(this.itemIds, QuetzalNetwork.WHISTLE_ITEM_IDS);
        }

        boolean requiresEquipmentTab()
        {
            if (this.spell != null && canCastSpell(this.spell) && (this.requiredItemId < 0 || Rs2Inventory.hasItem(this.requiredItemId)))
            {
                return false;
            }
            for (int itemId : this.itemIds)
            {
                if (!TeleportProviders.isItemAllowedOnWorld(Microbot.getClient(), itemId))
                {
                    continue;
                }
                if (Rs2Inventory.hasItem(itemId))
                {
                    return false;
                }
                if (this.equipmentAllowed && TravelEquipment.isWearing(itemId))
                {
                    return true;
                }
            }
            return false;
        }

        private boolean canCastSpell(Spell spell)
        {
            return TeleportProviders.isSpellAllowedOnWorld(Microbot.getClient(), spell) && Rs2Magic.canCast(spell);
        }

        private boolean hasUsableTeleportItem(int itemId)
        {
            return TeleportProviders.isItemAllowedOnWorld(Microbot.getClient(), itemId)
                && (Rs2Inventory.hasItem(itemId) || (this.equipmentAllowed && TravelEquipment.isWearing(itemId)));
        }

    }
}
