package net.runelite.client.plugins.microbot.accountstarter.logic;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Spots {
    private Spots() {
    }

    private static final class Entry {
        final Set<Skill> skills;
        final Spot spot;

        Entry(Set<Skill> skills, Spot spot) {
            this.skills = skills;
            this.spot = spot;
        }
    }

    private static final Set<Skill> MELEE = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE);
    private static final Set<Skill> ALL_COMBAT = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);

    public static final Map<Skill, WorldPoint> TUTORS = new LinkedHashMap<>();
    public static final Map<String, WorldPoint> VARROCK = new LinkedHashMap<>();
    public static final WorldPoint LUMBRIDGE_BANK = new WorldPoint(3209, 3220, 2);
    public static final Spot LUMBRIDGE_RIVER = new Spot("Lumbridge river", new WorldPoint(3238, 3241, 0), 12, Set.of("Rod Fishing spot"), 20, 99, false, 0, 0);
    static final int LEVEL_MARGIN = 2;
    static final int HITS_NO_FOOD = 4;
    static final int HITS_WITH_FOOD = 3;
    static final int CROWD_PLAYERS = 2;
    static final int DARK_WIZARDS_MIN_Y = 3363;
    /** Above double the level 20 wizards' level neither kind attacks. */
    static final int DARK_WIZARDS_IGNORE_COMBAT = 41;
    static final List<WorldPoint> DETOUR = List.of(new WorldPoint(3199, 3372, 0), new WorldPoint(3172, 3420, 0));

    static {
        TUTORS.put(Skill.FISHING, new WorldPoint(3245, 3157, 0));
        TUTORS.put(Skill.WOODCUTTING, new WorldPoint(3227, 3245, 0));
        TUTORS.put(Skill.MINING, new WorldPoint(3226, 3146, 0));
        VARROCK.put("Lowe", new WorldPoint(3233, 3423, 0));
        VARROCK.put("Aubury", new WorldPoint(3253, 3401, 0));
        VARROCK.put("Apothecary", new WorldPoint(3196, 3404, 0));
        VARROCK.put("Grand Exchange Clerk", new WorldPoint(3165, 3490, 0));
    }

    // only these three serve combat tasks: nothing there can kill a fresh account
    private static final List<Entry> TABLE = Arrays.asList(
        fight("Lumbridge chickens", 3230, 3298, 6, 1, 0, "Chicken"),
        fight("Lumbridge goblins", 3251, 3242, 10, 5, 1, "Goblin"),
        fight("Lumbridge cows", 3254, 3278, 12, 2, 1, "Cow", "Cow calf"),
        e(EnumSet.of(Skill.FISHING), "Lumbridge swamp fishing", 3244, 3153, 4, 1, 4, "Fishing spot"),
        // not Draynor: two aggressive Dark wizards spawn on that spot, deadly at level 3
        e(EnumSet.of(Skill.FISHING), "Lumbridge swamp fishing south", 3239, 3147, 4, 1, 4, "Fishing spot"),
        // a Mugger (lvl 6, aggressive to combat 12 or less) spawns at 3181,3216
        e(EnumSet.of(Skill.WOODCUTTING), "Lumbridge castle trees", 3192, 3223, 7, 1, 4, "Tree"),
        e(EnumSet.of(Skill.WOODCUTTING), "Draynor road trees", 3150, 3231, 7, 1, 4, "Tree"),
        e(EnumSet.of(Skill.MINING), "East swamp mine copper", 3229, 3147, 4, 1, 4, "Copper rocks", "Tin rocks"),
        // not the west swamp mine: coal, mithril and adamantite only, nothing a level-1 miner can mine
        e(EnumSet.of(Skill.MINING), "East swamp mine tin", 3224, 3147, 4, 1, 4, "Tin rocks", "Copper rocks"));

    public static final List<int[]> SURVEYED = Arrays.asList(
        new int[]{3222, 3218, 3230, 3298},
        new int[]{3222, 3218, 3251, 3242},
        new int[]{3222, 3218, 3254, 3278},
        new int[]{3205, 3214, 3207, 3201},
        new int[]{3200, 3177, 3165, 3184},
        new int[]{3222, 3218, 3244, 3153},
        new int[]{3222, 3218, 3239, 3147},
        new int[]{3222, 3218, 3192, 3223},
        new int[]{3222, 3218, 3150, 3231},
        new int[]{3222, 3218, 3229, 3147},
        new int[]{3222, 3218, 3224, 3147});

    private static Entry e(Set<Skill> skills, String name, int x, int y, int radius, int min, int max, String... targets) {
        return new Entry(skills, new Spot(name, new WorldPoint(x, y, 0), radius, Set.of(targets), min, max, false, 0, 0));
    }

    private static Entry fight(String name, int x, int y, int radius, int npcLevel, int npcMaxHit, String... targets) {
        return new Entry(ALL_COMBAT, new Spot(name, new WorldPoint(x, y, 0), radius, Set.of(targets), 1, 99, false, npcLevel, npcMaxHit));
    }

    public static List<Spot> forTask(ApTask task) {
        int level = task.target() - 1;
        return TABLE.stream()
            .filter(en -> en.skills.contains(task.skill()) && en.spot.getMinLevel() <= level && level <= en.spot.getMaxLevel())
            .map(en -> en.spot)
            .collect(Collectors.toList());
    }

    public static List<Spot> usable(ApTask task, int level, boolean skiller) {
        return usable(task, Map.of(task.skill(), level), skiller, 0);
    }

    public static List<Spot> usable(ApTask task, Map<Skill, Integer> levels, boolean skiller, int food) {
        int level = levels.getOrDefault(task.skill(), 1);
        List<Spot> all = forTask(task);
        List<Spot> inBand = all.stream().filter(s -> s.getMinLevel() <= level && level <= s.getMaxLevel()).collect(Collectors.toList());
        int combat = combatLevel(levels);
        int hp = levels.getOrDefault(Skill.HITPOINTS, 10);
        int attack = levels.getOrDefault(Skill.ATTACK, 1);
        List<Spot> ok = (inBand.isEmpty() ? all : inBand).stream()
            .filter(s -> !(skiller && s.isAggressive()))
            .filter(s -> safe(s, task.skill(), combat, hp, attack, food))
            .collect(Collectors.toList());
        if (!ok.isEmpty()) return ok;
        return TABLE.stream().filter(en -> en.skills.contains(task.skill()) && !(skiller && en.spot.isAggressive()))
            .map(en -> en.spot)
            .min(Comparator.comparingInt(Spot::getNpcMaxHit).thenComparingInt(Spot::getNpcLevel))
            .map(List::of).orElse(List.of());
    }

    public static boolean safe(Spot spot, Skill trained, int combatLevel, int hitpoints, int attack, int food) {
        if (spot.getNpcLevel() <= 0) return true;
        if (spot.getNpcLevel() > combatLevel + LEVEL_MARGIN) return false;
        if (spot.getNpcMaxHit() * (food > 0 ? HITS_WITH_FOOD : HITS_NO_FOOD) > hitpoints) return false;
        return !MELEE.contains(trained) || spot.getNpcLevel() <= 2 * attack + 3;
    }

    public static int combatLevel(Map<Skill, Integer> levels) {
        int def = levels.getOrDefault(Skill.DEFENCE, 1);
        int hp = levels.getOrDefault(Skill.HITPOINTS, 10);
        int prayer = levels.getOrDefault(Skill.PRAYER, 1);
        int melee = levels.getOrDefault(Skill.ATTACK, 1) + levels.getOrDefault(Skill.STRENGTH, 1);
        int ranged = levels.getOrDefault(Skill.RANGED, 1) * 3 / 2;
        int magic = levels.getOrDefault(Skill.MAGIC, 1) * 3 / 2;
        // in 1/40ths, so the floor is exact
        return (10 * (def + hp + prayer / 2) + 13 * Math.max(melee, Math.max(ranged, magic))) / 40;
    }

    public static boolean crowded(int otherPlayers, int freeTargets) {
        return otherPlayers >= CROWD_PLAYERS && otherPlayers > freeTargets;
    }

    /** Waypoints that skirt the stone circle's aggressive dark wizards on the way into Varrock. */
    public static List<WorldPoint> waypoints(WorldPoint from, WorldPoint to, int combatLevel) {
        if (from == null || to == null) return List.of();
        boolean southOfCircle = from.getY() < DARK_WIZARDS_MIN_Y;
        boolean inVarrock = to.getX() >= 3180 && to.getX() <= 3300 && to.getY() >= 3380 && to.getY() < 3520;
        return southOfCircle && inVarrock && combatLevel < DARK_WIZARDS_IGNORE_COMBAT ? DETOUR : List.of();
    }
}
