package net.runelite.client.plugins.microbot.droslayer;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.slayer.Task;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Krystilia's Wilderness-only assignments. Coordinates are shipped NPC spawn anchors,
 * not verified walkable/cannon tiles (God Wars uses user-observed combat tiles); Efficient Walker and the cannon placement watchdog
 * must validate the current scene. See docs/DRO_SLAYER_WILDERNESS.md for source evidence.
 */
public final class DroSlayerWildernessTasks
{
    private static final List<WorldPoint> NATIVE_CANNON_SPOTS = loadNativeCannonSpots();
    private static final Map<String, Assignment> BY_NAME = new LinkedHashMap<>();
    private static final List<Assignment> ASSIGNMENTS = new ArrayList<>();

    static
    {
        register(Task.ABYSSAL_DEMONS, true,
            names("Abyssal demon", "Greater abyssal demon"),
            requirements(),
            "85 Slayer; I Wildy More Slayer unlock. Abyssal demons can teleport the player across the level 30 line.",
            names("Abyssal demon"),
            location("Wilderness Slayer Cave - Abyssal demon", new WorldPoint(3343, 10163, 0), true,
                "npc/npcs_locations.json: Abyssal demon / Wilderness Slayer Cave", ""));

        register(Task.ANKOU, true,
            names("Ankou"),
            requirements(),
            "",
            names(),
            location("Wilderness Slayer Cave - Ankou", new WorldPoint(3357, 10076, 0), true,
                "npc/npcs_locations.json: Ankou / Wilderness Slayer Cave", ""),
            location("The Forgotten Cemetery (Wilderness)", new WorldPoint(2977, 3748, 0), true,
                "npc/npcs_locations.json: Ankou / The Forgotten Cemetery (Wilderness)", ""));

        register(Task.AVIANSIES, true,
            names("Aviansie"),
            requirements(Requirement.RANGED_OR_MAGIC),
            "Watch the birdie unlock; ordinary melee cannot attack aviansies.",
            names("Aviansie", "Aviansies"),
            location("Wilderness God Wars Dungeon - Aviansie", new WorldPoint(3030, 10158, 0), false,
                "User-observed God Wars combat tile: 2026-10-07 screenshot", "Observed aviansie cluster; use rangewildy. 60 Agility or Strength to enter; god-aligned equipment controls aggression."));

        register(Task.BANDITS, true,
            names("Bandit", "Black Heather", "Donny the lad", "Speedy Keith"),
            requirements(),
            "",
            names("Bandit"),
            location("Bandit Camp (Wilderness)", new WorldPoint(3032, 3695, 0), true,
                "npc/npcs_locations.json: Bandit / Bandit Camp (Wilderness)", ""));

        register(Task.BEARS, true,
            names("Grizzly bear", "Black bear"),
            requirements(),
            "Ordinary bears only; boss variants are excluded.",
            names("Bear"),
            location("Wilderness bears north-west of Ferox", new WorldPoint(3106, 3651, 0), true,
                "npc/npcs_locations.json: Grizzly bear / Ferox Enclave (Wilderness)", ""),
            location("Wilderness bears south of Western Ruins", new WorldPoint(2988, 3671, 0), true,
                "npc/npcs_locations.json: Grizzly bear / Dark Warriors' Fortress", ""),
            location("Wilderness bears west of Graveyard of Shadows", new WorldPoint(3103, 3677, 0), true,
                "npc/npcs_locations.json: Grizzly bear / Ferox Enclave (Wilderness)", ""));

        register(Task.BLACK_DEMONS, true,
            names("Black demon"),
            requirements(),
            "",
            names("Black demon"),
            location("Wilderness Slayer Cave - Black demon", new WorldPoint(3359, 10114, 0), true,
                "npc/npcs_locations.json: Black demon / Wilderness Slayer Cave", ""),
            location("Wilderness Edgeville Dungeon - Black demons", new WorldPoint(3088, 9961, 0), true,
                "npc/npcs_locations.json: Black demon / Edgeville Dungeon", ""));

        register(Task.BLACK_DRAGONS, true,
            names("Black dragon"),
            requirements(Requirement.DRAGONFIRE_PROTECTION),
            "King Black Dragon and lava dragons do not count for Krystilia black dragon tasks.",
            names("Black dragon"),
            location("Wilderness Slayer Cave - Black dragon", new WorldPoint(3361, 10159, 0), true,
                "npc/npcs_locations.json: Black dragon / Wilderness Slayer Cave", ""),
            location("Lava Maze Dungeon", new WorldPoint(3048, 10266, 0), true,
                "npc/npcs_locations.json: Black dragon / Lava Maze Dungeon", ""));

        register(Task.BLACK_KNIGHTS, true,
            names("Black Knight"),
            requirements(),
            "",
            names("Black knight"),
            location("Black Knights south of Silk Chasm", new WorldPoint(3310, 3769, 0), false,
                "npc/npcs_locations.json: Black Knight / Bone Yard", ""),
            location("Lava Maze", new WorldPoint(3042, 3856, 0), false,
                "npc/npcs_locations.json: Black Knight / Lava Maze", ""));

        register(Task.BLOODVELD, true,
            names("Bloodveld"),
            requirements(),
            "50 Slayer; magical melee is blocked by Protect from Melee.",
            names("Bloodvelds"),
            location("Wilderness God Wars Dungeon - Bloodveld", new WorldPoint(3043, 10139, 0), false,
                "User-observed God Wars combat tile: 2026-10-07 screenshot", "60 Agility or Strength to enter; god-aligned equipment controls aggression. The shipped NPC coordinate is historical."));

        register(Task.CHAOS_DRUIDS, true,
            names("Chaos druid", "Elder Chaos druid"),
            requirements(),
            "Ordinary druids are the default; the Elder Chaos Druids checkbox selects Chaos Temple. Both locations allow cannoning.",
            names("Chaos druid"),
            location("Wilderness Edgeville Dungeon - Chaos druids", new WorldPoint(3111, 9933, 0), true,
                "User-verified cannon tile: 2026-10-05 screenshot", ""),
            location("Chaos Temple - Elder Chaos druids", new WorldPoint(3235, 3615, 0), true,
                "npc/npcs_locations.json: Elder Chaos druid / Chaos Temple (Wilderness)", "Elder Chaos druids cast Tele Block; escape teleport may be unavailable."));

        register(Task.DARK_WARRIORS, true,
            names("Dark warrior"),
            requirements(),
            "",
            names("Dark warrior"),
            location("Dark Warriors' Fortress", new WorldPoint(3030, 3630, 0), true,
                "npc/npcs_locations.json: Dark warrior / Dark Warriors' Fortress", ""));

        register(Task.DUST_DEVILS, true,
            names("Dust devil", "Choke devil"),
            requirements(Requirement.DUST_PROTECTION),
            "65 Slayer; I Wildy More Slayer unlock; facemask or Slayer helmet required.",
            names("Dust devil"),
            location("Wilderness Slayer Cave - Dust devil", new WorldPoint(3439, 10123, 0), true,
                "npc/npcs_locations.json: Dust devil / Wilderness Slayer Cave", ""));

        register(Task.EARTH_WARRIORS, true,
            names("Earth warrior"),
            requirements(),
            "15 Agility to cross the monkey bars.",
            names("Earth warrior"),
            location("Wilderness Edgeville Dungeon - Earth warriors", new WorldPoint(3123, 9988, 0), true,
                "npc/npcs_locations.json: Earth warrior / Edgeville Dungeon", ""));

        register(Task.ENTS, true,
            names("Ent"),
            requirements(),
            "Woodcutting and an axe are needed only to chop the defeated Ent trunk, not for the Slayer kill.",
            names("Ent"),
            location("Ents east of Chaos Temple", new WorldPoint(3301, 3597, 0), false,
                "npc/npcs_locations.json: Ent / Chaos Temple (Wilderness)", ""),
            location("Ents north of Chaos Temple", new WorldPoint(3211, 3690, 0), false,
                "npc/npcs_locations.json: Ent / Chaos Temple (Wilderness)", ""));

        register(Task.FIRE_GIANTS, true,
            names("Fire giant"),
            requirements(),
            "Deep Wilderness destination; no direct teleport out above level 30.",
            names("Fire giant"),
            location("Deep Wilderness Dungeon", new WorldPoint(3046, 10341, 0), true,
                "npc/npcs_locations.json: Fire giant / Deep Wilderness Dungeon", ""));

        register(Task.GREATER_DEMONS, true,
            names("Greater demon"),
            requirements(),
            "",
            names("Greater demon"),
            location("Wilderness Slayer Cave - Greater demon", new WorldPoint(3428, 10151, 0), true,
                "npc/npcs_locations.json: Greater demon / Wilderness Slayer Cave", ""),
            location("Demonic Ruins", new WorldPoint(3282, 3880, 0), true,
                "npc/npcs_locations.json: Greater demon / Wilderness", ""),
            location("Lava Maze Dungeon", new WorldPoint(3028, 10250, 0), true,
                "npc/npcs_locations.json: Greater demon / Lava Maze Dungeon", ""));

        register(Task.GREEN_DRAGONS, true,
            names("Green dragon"),
            requirements(Requirement.DRAGONFIRE_PROTECTION),
            "The cave data contains two separated green dragon clusters; the preferred anchor belongs to the southern one.",
            names("Green dragon"),
            location("Wilderness Slayer Cave - Southern green dragons", new WorldPoint(3415, 10068, 0), true,
                "npc/npcs_locations.json: Green dragon / Wilderness Slayer Cave", ""),
            location("Green dragons west of Dark Warriors Fortress", new WorldPoint(2973, 3620, 0), true,
                "npc/npcs_locations.json: Green dragon / Dark Warriors' Fortress", ""),
            location("Green dragons north of Graveyard of Shadows", new WorldPoint(3144, 3707, 0), true,
                "npc/npcs_locations.json: Green dragon / Graveyard of Shadows", ""),
            location("Green dragons south of Lava Maze", new WorldPoint(3092, 3810, 0), true,
                "npc/npcs_locations.json: Green dragon / Lava Maze", ""),
            location("Green dragons south-west of Bone Yard", new WorldPoint(3339, 3694, 0), true,
                "npc/npcs_locations.json: Green dragon / Boneyard Hunter area", ""),
            location("Wilderness Slayer Cave - Northern green dragons", new WorldPoint(3396, 10119, 0), true,
                "npc/npcs_locations.json: Green dragon / Wilderness Slayer Cave", ""));

        register(Task.HELLHOUNDS, true,
            names("Hellhound"),
            requirements(),
            "Ordinary hellhounds only; boss encounter variants are excluded.",
            names("Hellhound"),
            location("Wilderness Slayer Cave - Hellhound", new WorldPoint(3445, 10085, 0), true,
                "npc/npcs_locations.json: Hellhound / Wilderness Slayer Cave", ""),
            location("Hellhounds north-east of Deserted Keep", new WorldPoint(3180, 3917, 0), true,
                "npc/npcs_locations.json: Hellhound / Wilderness", ""));

        register(Task.HILL_GIANTS, true,
            names("Hill Giant"),
            requirements(),
            "",
            names("Hill Giant"),
            location("Deep Wilderness Dungeon - Hill giants", new WorldPoint(3044, 10318, 0), true,
                "User-verified cannon tile from screenshot, 2026-10-11: 3044,10318,0", ""),
            location("Wilderness Hill giants - Bone Yard Hunter area", new WorldPoint(3307, 3669, 0), true,
                "npc/npcs_locations.json: Hill Giant / Boneyard Hunter area", ""),
            location("Lava Maze", new WorldPoint(3110, 3854, 0), true,
                "npc/npcs_locations.json: Hill Giant / Lava Maze", ""));

        register(Task.ICE_GIANTS, true,
            names("Ice giant"),
            requirements(),
            "",
            names("Ice giant"),
            location("Wilderness Slayer Cave - Ice giant", new WorldPoint(3342, 10056, 0), true,
                "npc/npcs_locations.json: Ice giant / Wilderness Slayer Cave", ""),
            location("Frozen Waste Plateau (Wilderness)", new WorldPoint(2952, 3902, 0), true,
                "npc/npcs_locations.json: Ice giant / Frozen Waste Plateau (Wilderness)", ""));

        register(Task.ICE_WARRIORS, true,
            names("Ice warrior"),
            requirements(),
            "",
            names("Ice warrior"),
            location("Frozen Waste Plateau - Southern ice warriors", new WorldPoint(2954, 3877, 0), true,
                "npc/npcs_locations.json: Ice warrior / Frozen Waste Plateau (Wilderness)", ""),
            location("Frozen Waste Plateau - Northern ice warriors", new WorldPoint(2956, 3930, 0), true,
                "npc/npcs_locations.json: Ice warrior / Frozen Waste Plateau (Wilderness)", ""));

        register(Task.JELLIES, true,
            names("Jelly", "Vitreous Jelly"),
            requirements(),
            "52 Slayer; I Wildy More Slayer unlock; magical melee is blocked by Protect from Melee.",
            names("Jelly"),
            location("Wilderness Slayer Cave - Jelly", new WorldPoint(3430, 10104, 0), true,
                "npc/npcs_locations.json: Jelly / Wilderness Slayer Cave", ""));

        register(Task.LAVA_DRAGONS, true,
            names("Lava dragon"),
            requirements(Requirement.DRAGONFIRE_PROTECTION),
            "Deep Wilderness; ranged or Magic is recommended to fight from across lava. This task does not use a cannon.",
            names("Lava dragon"),
            location("Lava Dragon Isle", new WorldPoint(3213, 3819, 0), false,
                "npc/npcs_locations.json: Lava dragon / Lava Dragon Isle", ""));

        register(Task.LESSER_DEMONS, true,
            names("Lesser demon"),
            requirements(),
            "",
            names("Lesser demon"),
            location("Wilderness Slayer Cave - Lesser demon", new WorldPoint(3337, 10134, 0), true,
                "npc/npcs_locations.json: Lesser demon / Wilderness Slayer Cave", ""),
            location("Lesser demons by Lava Maze Muddy chest", new WorldPoint(3091, 3861, 0), true,
                "npc/npcs_locations.json: Lesser demon / Wilderness", ""),
            location("Lesser demons south of Demonic Ruins", new WorldPoint(3311, 3845, 0), true,
                "npc/npcs_locations.json: Lesser demon / Wilderness", ""),
            location("Lesser demons in King Black Dragon entrance cage", new WorldPoint(3015, 3851, 0), true,
                "npc/npcs_locations.json: Lesser demon / Wilderness", ""));

        register(Task.MAGIC_AXES, true,
            names("Magic axe"),
            requirements(Requirement.LOCKPICK, Requirement.WEB_CUTTING),
            "54-56 Wilderness; lockpick and 23 Thieving to pick-lock the hut; placement space requires verification.",
            names("Magic axe"),
            location("Magic axe hut", new WorldPoint(3191, 3960, 0), true,
                "User-verified Magic axe hut tile 2026-10-05", ""));

        register(Task.MAMMOTHS, true,
            names("Mammoth"),
            requirements(),
            "",
            names("Mammoth"),
            location("Mammoths west of Chaos Temple", new WorldPoint(3171, 3594, 0), true,
                "npc/npcs_locations.json: Mammoth / Ferox Enclave (Wilderness)", ""));

        register(Task.MOSS_GIANTS, true,
            names("Moss giant"),
            requirements(),
            "",
            names("Moss giant"),
            location("Moss giants south-east of Lava Maze", new WorldPoint(3145, 3812, 0), true,
                "npc/npcs_locations.json: Moss giant / Wilderness", ""));

        register(Task.NECHRYAEL, true,
            names("Greater Nechryael", "Nechryarch"),
            requirements(),
            "80 Slayer; I Wildy More Slayer unlock; death spawns are not Slayer targets.",
            names("Nechryaels"),
            location("Wilderness Slayer Cave - Greater Nechryael", new WorldPoint(3334, 10105, 0), true,
                "npc/npcs_locations.json: Greater Nechryael / Wilderness Slayer Cave", ""));

        register(Task.PIRATES, true,
            names("Pirate"),
            requirements(Requirement.LOCKPICK),
            "Preferred Pirates Hideout requires a lockpick and 39 Thieving; Zombie pirates are a valid alternative with Tele Block risk.",
            names("Pirate"),
            location("Pirates' Hideout", new WorldPoint(3041, 3954, 0), true,
                "Adjacent walk tile to npc/npcs_locations.json: Pirate / Pirates' Hideout (3041,3953,0)", ""),
            location("Chaos Temple - Zombie pirates", new WorldPoint(3237, 3607, 0), true,
                "npc/npcs_locations.json: Zombie pirate / Chaos Temple (Wilderness)", "Elder Chaos druids cast Tele Block; appropriate ranged/Magic protection is needed."));

        register(Task.REVENANTS, true,
            names("Revenant goblin"),
            requirements(Requirement.REVENANT_ENTRY_FEE),
            "Preferred southern goblins; charged bracelet of ethereum strongly recommended. Revenant Caves teleport delay may apply.",
            names("Revenant"),
            location("Revenant Caves - Southern goblins", new WorldPoint(3221, 10065, 0), false,
                "npc/npcs_locations.json: Revenant goblin / Revenant Cave", "100,000 coin entry deposit; bracelet of ethereum recommended; no cannon."),
            location("Revenant Caves - Imps", new WorldPoint(3197, 10070, 0), false,
                "npc/npcs_locations.json: Revenant imp / Revenant Caves", ""),
            location("Revenant Caves - Hobgoblins", new WorldPoint(3240, 10096, 0), false,
                "npc/npcs_locations.json: Revenant hobgoblin / Revenant Cave", ""),
            location("Revenant Caves - Southern orks", new WorldPoint(3207, 10093, 0), false,
                "npc/npcs_locations.json: Revenant ork / Revenant Caves", ""));

        register(Task.ROGUES, true,
            names("Rogue"),
            requirements(),
            "",
            names("Rogue"),
            location("Rogues' Castle", new WorldPoint(3285, 3933, 0), true,
                "npc/npcs_locations.json: Rogue / Rogues' Castle", ""),
            location("Rogues east of Mage Arena", new WorldPoint(3076, 3916, 0), true,
                "npc/npcs_locations.json: Rogue / Mage Arena", ""));

        register(Task.SCORPIONS, true,
            names("Scorpion", "King Scorpion"),
            requirements(),
            "Ordinary surface scorpions only; boss encounter variants are excluded.",
            names("Scorpion"),
            location("Scorpions west of Air Obelisk", new WorldPoint(3041, 3572, 0), true,
                "npc/npcs_locations.json: Scorpion / Wilderness", ""),
            location("Scorpion Pit", new WorldPoint(3239, 3942, 0), true,
                "npc/npcs_locations.json: Scorpion / Scorpion Pit", ""),
            location("Lava Maze - King Scorpions", null, true,
                "No matching coordinate in shipped NPC data", "No shipped King Scorpion location anchor; map it live or use a surface alternative."));

        register(Task.SKELETONS, true,
            names("Skeleton"),
            requirements(),
            "Ordinary skeletons only; boss encounter variants are excluded.",
            names("Skeleton"),
            location("South-west Wilderness mine - Skeletons", new WorldPoint(3016, 3596, 0), true,
                "npc/npcs_locations.json: Skeleton / Wilderness", ""),
            location("Southern Wilderness mine - Skeletons", new WorldPoint(3106, 3548, 0), true,
                "npc/npcs_locations.json: Skeleton / Wilderness", ""),
            location("Bone Yard", new WorldPoint(3252, 3737, 0), true,
                "npc/npcs_locations.json: Skeleton / Bone Yard", ""),
            location("Wilderness Edgeville Dungeon - Skeletons", new WorldPoint(3108, 9954, 0), true,
                "npc/npcs_locations.json: Skeleton / Edgeville Dungeon", ""),
            location("Wilderness Agility Course Dungeon", new WorldPoint(3006, 10352, 0), true,
                "npc/npcs_locations.json: Skeleton / Wilderness Agility Course Dungeon", ""));

        register(Task.SPIDERS, true,
            names("Giant spider", "Deadly red spider", "Poison spider", "Spider"),
            requirements(),
            "Ordinary spiders only; boss variants are excluded.",
            names("Spider"),
            location("Giant spiders north-west of Lava Dragon Isle", new WorldPoint(3169, 3886, 0), true,
                "User-verified spider cannon tile 2026-10-05; RuneLite CannonSpots.SPIDER", ""),
            location("Wilderness Bandit Camp - Giant spiders", new WorldPoint(2990, 3688, 0), true,
                "npc/npcs_locations.json: Giant spider / Wilderness Bandit Camp", ""),
            location("Eastern Ruins - Deadly red spiders", new WorldPoint(3164, 3734, 0), true,
                "npc/npcs_locations.json: Deadly red spider / Ruins (east)|Eastern Ruins", ""),
            location("Wilderness Edgeville Dungeon - Deadly red spiders", new WorldPoint(3122, 9955, 0), true,
                "npc/npcs_locations.json: Deadly red spider / Edgeville Dungeon", ""),
            location("Lava Maze", new WorldPoint(3047, 3890, 0), true,
                "npc/npcs_locations.json: Deadly red spider / Lava Maze", ""));

        register(Task.SPIRITUAL_CREATURES, true,
            names("Spiritual warrior", "Spiritual ranger", "Spiritual mage"),
            requirements(),
            "63 Slayer for rangers; warriors and mages require 68 and 83 Slayer respectively; 60 Agility or Strength entrance.",
            names("Spiritual creature"),
            location("Wilderness God Wars Dungeon - Spiritual creatures", new WorldPoint(3030, 10158, 0), false,
                "User-observed God Wars combat tile: 2026-10-07 screenshot", "Observed spiritual mage/aviansie area; filter each target by the player Slayer level. The shipped NPC coordinate is historical. 60 Agility or Strength to enter; god-aligned equipment controls aggression."));

        register(Task.ZOMBIES, true,
            names("Zombie"),
            requirements(),
            "",
            names("Zombie"),
            location("Graveyard of Shadows", new WorldPoint(3165, 3678, 0), true,
                "npc/npcs_locations.json: Zombie / Graveyard of Shadows", ""),
            location("Eastern Ruins - Zombies", new WorldPoint(3156, 3731, 0), true,
                "npc/npcs_locations.json: Zombie / Ruins (east)", ""),
            location("Chaos Temple - Zombie pirates", new WorldPoint(3237, 3607, 0), true,
                "npc/npcs_locations.json: Zombie pirate / Chaos Temple (Wilderness)", "Elder Chaos druids can Tele Block; Zombie pirates also count."));


    }

    private DroSlayerWildernessTasks() {}

    /** Only ordinary Krystilia assignments are supported; boss and unknown names return null. */
    public static Assignment forAssignedTask(String name)
    {
        if (name == null) return null;
        return BY_NAME.get(normalize(name));
    }

    /** The 36 ordinary Wilderness assignment groups. */
    public static List<Assignment> all()
    {
        return Collections.unmodifiableList(ASSIGNMENTS);
    }

    private static void register(Task task, boolean supported, List<String> targets,
                                 List<Requirement> requirements, String notes,
                                 List<String> aliases, Location... locations)
    {
        Assignment entry = new Assignment(task, task.getName(), supported, targets, requirements,
            notes, Arrays.asList(locations));
        ASSIGNMENTS.add(entry);
        BY_NAME.put(normalize(task.getName()), entry);
        BY_NAME.put(normalize(task.name().replace('_', ' ')), entry);
        for (String alias : aliases) BY_NAME.put(normalize(alias), entry);
    }

    private static String normalize(String value)
    {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('’', '\'');
        if (normalized.startsWith("the ")) normalized = normalized.substring(4);
        return normalized.replaceAll("\\s+", " ");
    }

    private static List<String> names(String... names) { return Arrays.asList(names); }
    private static List<Requirement> requirements(Requirement... requirements) { return Arrays.asList(requirements); }
    private static Location location(String name, WorldPoint point, boolean cannon,
                                     String evidence, String notes)
    {
        return new Location(name, point, cannon, evidence, notes);
    }

    /**
     * The native CannonSpots enum is package-private. Read its published list once,
     * without mutating the native plugin or requiring that plugin to be enabled.
     */
    private static List<WorldPoint> loadNativeCannonSpots()
    {
        try
        {
            Class<?> source = Class.forName("net.runelite.client.plugins.cannon.CannonSpots");
            Method getter = source.getDeclaredMethod("getCannonSpots");
            getter.setAccessible(true);
            List<WorldPoint> result = new ArrayList<>();
            Object value = getter.invoke(null);
            if (value instanceof List<?>)
                for (Object point : (List<?>) value)
                    if (point instanceof WorldPoint) result.add((WorldPoint) point);
            return Collections.unmodifiableList(result);
        }
        catch (ReflectiveOperationException | SecurityException unavailable)
        {
            return Collections.emptyList();
        }
    }

    /** Select Elders only when explicitly requested; ordinary druids remain the default. */
    static Assignment forChaosDruidPreference(Assignment assignment, boolean elders) {
        if (assignment == null || assignment.getTask() != Task.CHAOS_DRUIDS || !elders) return assignment;
        List<Location> ordered = new ArrayList<>(assignment.getLocations());
        Collections.swap(ordered, 0, 1);
        return new Assignment(assignment.getTask(), assignment.getTaskName(), assignment.isSupported(),
            assignment.getTargetNames(), assignment.getRequirements(), assignment.getNotes(), ordered);
    }

    private static WorldPoint nativeCannonSpot(WorldPoint anchor)
    {
        if (anchor == null) return null;
        WorldPoint nearest = null;
        int nearestDistance = 16;
        for (WorldPoint point : NATIVE_CANNON_SPOTS)
        {
            if (point.getPlane() != anchor.getPlane()) continue;
            int distance = point.distanceTo(anchor);
            if (distance < nearestDistance)
            {
                nearest = point;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public enum Requirement
    {
        DUST_PROTECTION("Facemask or Slayer helmet"),
        DRAGONFIRE_PROTECTION("Dragonfire protection"),
        LOCKPICK("Lockpick"),
        WEB_CUTTING("Knife or equipped slash weapon"),
        RANGED_OR_MAGIC("Ranged or Magic weapon"),
        REVENANT_ENTRY_FEE("100,000 coin Revenant Caves entry deposit");

        @Getter private final String description;
        Requirement(String description) { this.description = description; }
    }

    @Getter
    public static final class Assignment
    {
        private final Task task;
        private final String taskName;
        private final boolean supported;
        private final List<String> targetNames;
        private final List<Requirement> requirements;
        private final String notes;
        private final List<Location> locations;

        private Assignment(Task task, String taskName, boolean supported, List<String> targetNames,
                           List<Requirement> requirements, String notes, List<Location> locations)
        {
            this.task = task;
            this.taskName = taskName;
            this.supported = supported;
            this.targetNames = Collections.unmodifiableList(new ArrayList<>(targetNames));
            this.requirements = Collections.unmodifiableList(new ArrayList<>(requirements));
            this.notes = notes;
            this.locations = Collections.unmodifiableList(new ArrayList<>(locations));
        }

        public String getLocationName() { return locations.isEmpty() ? taskName : locations.get(0).getName(); }
        public WorldPoint getDestination() { return supported && !locations.isEmpty() ? locations.get(0).getDestination() : null; }
        public boolean isCannonAllowed() { return supported && !locations.isEmpty() && locations.get(0).isCannonAllowed(); }
        /** User-selected cannon tile or exact native RuneLite tile near the preferred NPC cluster. */
        public WorldPoint getCannonSpot()
        {
            return isCannonAllowed() ? locations.get(0).getCannonSpot() : null;
        }
        public String getCannonSpotEvidence()
        {
            if (isCannonAllowed() && "Wilderness Slayer Cave - Black demon".equals(getLocationName()))
                return "User-selected cannon tile from screenshot, 2026-10-06: 3363,10120,0";
            if (isCannonAllowed() && "Wilderness Slayer Cave - Jelly".equals(getLocationName()))
                return "User-selected cannon tile from screenshot, 2026-10-07: 3430,10103,0";
            if (isCannonAllowed() && "Rogues' Castle".equals(getLocationName()))
                return "User-selected cannon tile from photo, 2026-10-06: 3287,3944,0";
            if (isCannonAllowed() && ("Wilderness Edgeville Dungeon - Chaos druids".equals(getLocationName())
                    || "Deep Wilderness Dungeon - Hill giants".equals(getLocationName())))
                return locations.get(0).getEvidence();
            return getCannonSpot() == null ? "No native cannon spot within 15 tiles of the preferred anchor; live placement verification required."
                : "net.runelite.client.plugins.cannon.CannonSpots";
        }
        public boolean isVerified() { return false; }
        public String getUnsupportedReason()
        {
            return supported ? "" : notes + " Block/skip this task or complete it manually until its combat handler is available.";
        }
        public List<String> getRequiredItems()
        {
            List<String> result = new ArrayList<>();
            for (Requirement requirement : requirements) result.add(requirement.getDescription());
            return Collections.unmodifiableList(result);
        }
    }

    @Getter
    public static final class Location
    {
        private final String name;
        private final WorldPoint destination;
        private final boolean cannonAllowed;
        private final String evidence;
        private final String notes;

        private Location(String name, WorldPoint destination, boolean cannonAllowed, String evidence, String notes)
        {
            this.name = name;
            this.destination = destination;
            this.cannonAllowed = cannonAllowed;
            this.evidence = evidence;
            this.notes = notes;
        }
        public WorldPoint getCannonSpot()
        {
            if (cannonAllowed && "Wilderness Slayer Cave - Black demon".equals(name))
                return new WorldPoint(3363, 10120, 0);
            if (cannonAllowed && "Wilderness Slayer Cave - Jelly".equals(name))
                return new WorldPoint(3430, 10103, 0);
            if (cannonAllowed && "Rogues' Castle".equals(name))
                return new WorldPoint(3287, 3944, 0);
            if (cannonAllowed && ("Wilderness Edgeville Dungeon - Chaos druids".equals(name)
                    || "Deep Wilderness Dungeon - Hill giants".equals(name)))
                return destination;
            return cannonAllowed ? nativeCannonSpot(destination) : null;
        }
        public boolean isVerified() { return false; }
    }
}
