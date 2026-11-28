package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

public final class GearPlan {
    public static final int AIR = 556, WATER = 555, EARTH = 557, FIRE = 554, MIND = 558;
    public static final int STAFF_AIR = 1381, STAFF_WATER = 1383, STAFF_EARTH = 1385, STAFF_FIRE = 1387;
    public static final int STAFF = 1379;
    /** Fires only TRAINING_ARROWS. */
    public static final int TRAINING_BOW = 9705, TRAINING_ARROWS = 9706;
    static final int BRONZE_ARROWS = 882, IRON_ARROWS = 884;
    public static final int QUEST_CASTS = 50;
    /** {staff, rune it stands in for (0 none), Attack and Magic to wield}, staffs that save most first. */
    static final int[][] KIT_STAFFS = {
            {1401, FIRE, 40}, {1393, FIRE, 30}, {STAFF_FIRE, FIRE, 1},
            {1407, EARTH, 40}, {1399, EARTH, 30}, {STAFF_EARTH, EARTH, 1},
            {1405, AIR, 40}, {1397, AIR, 30}, {STAFF_AIR, AIR, 1},
            {1403, WATER, 40}, {1395, WATER, 30}, {STAFF_WATER, WATER, 1},
            {STAFF, 0, 1}};
    public static final int MIN_CASTS = 10;
    private static final int[] ELEMENTS = {AIR, WATER, EARTH, FIRE};

    static final int[][] AXES = {{1351, 1}, {1349, 1}, {1353, 6}, {1361, 11}, {1355, 21}, {1357, 31}, {1359, 41}};
    static final int[][] PICKAXES = {{1265, 1}, {1267, 1}, {1269, 6}, {12297, 11}, {1273, 21}};
    static final int[][] LONGSWORDS = {{1291, 1}, {1293, 1}, {1295, 5}, {1297, 10}, {1299, 20}};
    static final int[][] SHORTBOWS = {{841, 1}, {843, 5}, {849, 20}};
    static final int[][] MELEE_HEADS = {{1137, 1}, {1141, 5}};
    static final int[][] RANGED_HEADS = {{1167, 1}, {1169, 20}};
    static final int[][] MAGIC_HEADS = {{579, 1}, {1017, 1}};
    static final int[][] MELEE_BODIES = {{1101, 1}, {1105, 5}, {1109, 20}};
    static final int[][] RANGED_BODIES = {{1129, 1}};
    static final int[][] MAGIC_BODIES = {{577, 1}, {581, 1}};
    static final int[][] MELEE_LEGS = {{1067, 1}};
    static final int[][] RANGED_LEGS = {{1097, 20}};
    static final int[][] MAGIC_LEGS = {{1011, 1}, {1015, 1}};
    static final int[][] BOOTS = {{1061, 0}};
    static final int[][] HANDS = {{1059, 0}, {1063, 0}};
    /** Metal armour lowers ranged and magic accuracy. */
    public static final Set<Integer> METAL_ARMOUR = Set.of(1137, 1141, 1101, 1105, 1109, 1067);
    /** Arrows break on 20% of shots; the rest land under the target. */
    static final double ARROW_BREAK = 0.20;
    public static final double ARROW_MISS_PICKUP = 0.15;
    static final double ARROW_BUFFER = 1.25;
    static final double STRIKE_HIT_CHANCE = 0.7;

    private GearPlan() {}

    public static int[] ids(int[][] table, int level) {
        List<Integer> out = new ArrayList<>();
        for (int[] e : table) if (e[1] <= level) out.add(e[0]);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    public static OptionalInt bestWeapon(Skill training, AccountSnapshot snap) {
        switch (training) {
            case ATTACK:
            case STRENGTH:
                return best(LONGSWORDS, level(snap, Skill.ATTACK), snap);
            case RANGED: {
                OptionalInt real = best(SHORTBOWS, level(snap, Skill.RANGED), snap);
                if (real.isPresent() && snap.owned(BRONZE_ARROWS) + snap.owned(IRON_ARROWS) > 0) return real;
                return snap.owned(TRAINING_BOW) > 0 ? OptionalInt.of(TRAINING_BOW) : real;
            }
            case MAGIC:
                return bestStaff(snap);
            default:
                return OptionalInt.empty();
        }
    }

    static OptionalInt bestStaff(AccountSnapshot snap) {
        int staff = magic(level(snap, Skill.MAGIC), ownedMap(snap), nextMagicXp(snap)).getStaff();
        if (staff != 0) return OptionalInt.of(staff);
        for (int rune : ELEMENTS) {
            if (snap.owned(staffFor(rune)) > 0) return OptionalInt.of(staffFor(rune));
        }
        return snap.owned(STAFF) > 0 ? OptionalInt.of(STAFF) : OptionalInt.empty();
    }

    public static boolean usesTrainingBow(AccountSnapshot snap) {
        OptionalInt bow = bestWeapon(Skill.RANGED, snap);
        return bow.isPresent() && bow.getAsInt() == TRAINING_BOW;
    }

    public static List<Integer> bestArmour(AccountSnapshot snap, Skill training) {
        List<int[][][]> slots = new ArrayList<>();
        switch (training) {
            case RANGED:
                slots.add(new int[][][]{RANGED_HEADS});
                slots.add(new int[][][]{RANGED_BODIES});
                slots.add(new int[][][]{RANGED_LEGS});
                break;
            case MAGIC:
                slots.add(new int[][][]{MAGIC_HEADS});
                slots.add(new int[][][]{MAGIC_BODIES});
                slots.add(new int[][][]{MAGIC_LEGS});
                break;
            default:
                slots.add(new int[][][]{MELEE_HEADS, RANGED_HEADS});
                slots.add(new int[][][]{MELEE_BODIES, RANGED_BODIES});
                slots.add(new int[][][]{MELEE_LEGS, RANGED_LEGS});
        }
        slots.add(new int[][][]{BOOTS});
        slots.add(new int[][][]{HANDS});
        List<Integer> out = new ArrayList<>();
        for (int[][][] choices : slots) {
            for (int[][] table : choices) {
                boolean metal = table == MELEE_HEADS || table == MELEE_BODIES || table == MELEE_LEGS;
                OptionalInt b = best(table, level(snap, metal ? Skill.DEFENCE : Skill.RANGED), snap);
                if (b.isPresent()) {
                    out.add(b.getAsInt());
                    break;
                }
            }
        }
        return out;
    }

    public static Set<Integer> takeOff(Skill training) {
        return training == Skill.RANGED || training == Skill.MAGIC ? METAL_ARMOUR : Set.of();
    }

    public static OptionalInt bestAxe(AccountSnapshot snap) {
        return best(AXES, level(snap, Skill.WOODCUTTING), snap);
    }

    /** 4 Ranged XP a damage; the damage a shot figures are estimates for bronze arrows. */
    public static double rangedShots(int xpHave, int xpNeeded) {
        double shots = 0;
        int xp = xpHave, left = xpNeeded;
        while (left > 0) {
            int lv = Experience.getLevelForXp(xp);
            int seg = lv >= 99 ? left : Math.min(left, Experience.getXpForLevel(lv + 1) - xp);
            double dmg = lv < 3 ? 0.4 : lv < 10 ? 0.8 : 0.95;
            shots += seg / 4.0 / dmg;
            left -= seg;
            xp += seg;
        }
        return shots;
    }

    public static int arrowsNeeded(int xpHave, int xpNeeded, double missShare) {
        double perShot = ARROW_BREAK + (1 - ARROW_BREAK) * missShare;
        return (int) Math.ceil(rangedShots(xpHave, xpNeeded) * perShot * ARROW_BUFFER - 1e-9);
    }

    public static Rs2CombatSpells bestStrike(int magicLevel, Map<Integer, Integer> owned) {
        return magic(magicLevel, owned, 0).getSpell();
    }

    public static Magic magic(int magicLevel, Map<Integer, Integer> owned, int xpNeeded) {
        Rs2CombatSpells[] spells = {Rs2CombatSpells.FIRE_STRIKE, Rs2CombatSpells.EARTH_STRIKE, Rs2CombatSpells.WATER_STRIKE,
                Rs2CombatSpells.WIND_STRIKE};
        int[] levels = {13, 9, 5, 1};
        for (int i = 0; i < spells.length; i++) {
            if (magicLevel < levels[i]) continue;
            Map<Integer, Integer> c = cost(spells[i]);
            int bestStaff = -1, saved = -1;
            for (int rune : ELEMENTS) {
                int staff = staffFor(rune);
                int saves = c.getOrDefault(rune, 0);
                if (saves > saved && owned.getOrDefault(staff, 0) > 0
                        && canCast(spells[i], magicLevel, staff, owned, xpNeeded)) {
                    bestStaff = staff;
                    saved = saves;
                }
            }
            if (saved > 0) return new Magic(bestStaff, spells[i]);
            if (canCast(spells[i], magicLevel, 0, owned, xpNeeded)) return new Magic(0, spells[i]);
        }
        return new Magic(owned.getOrDefault(STAFF_AIR, 0) > 0 ? STAFF_AIR : 0, Rs2CombatSpells.WIND_STRIKE);
    }

    @Value
    public static class Magic {
        int staff;
        Rs2CombatSpells spell;
    }

    public static QuestKit questKit(int magicLevel, int attackLevel, Map<Integer, Integer> owned) {
        Rs2CombatSpells[] spells = {Rs2CombatSpells.FIRE_STRIKE, Rs2CombatSpells.EARTH_STRIKE, Rs2CombatSpells.WATER_STRIKE,
                Rs2CombatSpells.WIND_STRIKE};
        int[] levels = {13, 9, 5, 1};
        for (int i = 0; i < spells.length; i++) {
            if (magicLevel < levels[i]) continue;
            for (int[] row : KIT_STAFFS) {
                if (owned.getOrDefault(row[0], 0) < 1 || Math.min(magicLevel, attackLevel) < row[2]) continue;
                Map<Integer, Integer> perCast = cost(spells[i]);
                perCast.remove(row[1]);
                if (perCast.entrySet().stream().allMatch(e -> owned.getOrDefault(e.getKey(), 0) >= e.getValue() * QUEST_CASTS)) {
                    return new QuestKit(row[0], spells[i], perCast);
                }
            }
        }
        return null;
    }

    @Value
    public static class QuestKit {
        int staff;
        Rs2CombatSpells spell;
        Map<Integer, Integer> perCast;

        public boolean hasCast(AccountSnapshot s) {
            return perCast.entrySet().stream().allMatch(e -> s.carried(e.getKey()) >= e.getValue());
        }

        public Map<Integer, Integer> runes() {
            Map<Integer, Integer> out = new HashMap<>();
            perCast.forEach((rune, n) -> out.put(rune, n * QUEST_CASTS));
            return out;
        }
    }

    public static int casts(Rs2CombatSpells spell, int xpNeeded) {
        return Math.max(MIN_CASTS, (int) Math.ceil(xpNeeded / baseXp(spell) - 1e-9));
    }

    /** Strike damage follows the Magic level, not the spell: max 2/4/6/8 from 1/5/9/13. */
    public static int casts(Rs2CombatSpells spell, int magicLevel, int xpNeeded) {
        double casts = 0;
        int lv = Math.max(1, magicLevel);
        int xp = Experience.getXpForLevel(lv), left = xpNeeded;
        while (left > 0) {
            int seg = lv >= 99 ? left : Math.min(left, Experience.getXpForLevel(lv + 1) - xp);
            casts += seg / (baseXp(spell) + 2 * STRIKE_HIT_CHANCE * strikeMaxHit(lv) / 2.0);
            left -= seg;
            xp += seg;
            lv++;
        }
        return Math.max(MIN_CASTS, (int) Math.ceil(casts - 1e-9));
    }

    static int strikeMaxHit(int magicLevel) {
        return magicLevel >= 13 ? 8 : magicLevel >= 9 ? 6 : magicLevel >= 5 ? 4 : 2;
    }

    private static double baseXp(Rs2CombatSpells spell) {
        switch (spell) {
            case FIRE_STRIKE: return 11.5;
            case EARTH_STRIKE: return 9.5;
            case WATER_STRIKE: return 7.5;
            default: return 5.5;
        }
    }

    public static int xpNeeded(AccountSnapshot snap, int target) {
        return xpNeeded(snap, Skill.MAGIC, target);
    }

    public static int xpNeeded(AccountSnapshot snap, Skill skill, int target) {
        return Math.max(0, Experience.getXpForLevel(target) - xpHave(snap, skill));
    }

    public static int xpHave(AccountSnapshot snap, Skill skill) {
        return Math.max(snap.getXp().getOrDefault(skill, 0), Experience.getXpForLevel(level(snap, skill)));
    }

    static int nextMagicXp(AccountSnapshot snap) {
        int lv = level(snap, Skill.MAGIC);
        for (ApTask t : ApTask.of(Skill.MAGIC)) if (t.target() > lv) return xpNeeded(snap, t.target());
        return 0;
    }

    public static Map<Integer, Integer> cost(Rs2CombatSpells spell) {
        Map<Integer, Integer> c = new HashMap<>();
        c.put(AIR, spell == Rs2CombatSpells.FIRE_STRIKE ? 2 : 1);
        c.put(MIND, 1);
        if (spell == Rs2CombatSpells.WATER_STRIKE) c.put(WATER, 1);
        if (spell == Rs2CombatSpells.EARTH_STRIKE) c.put(EARTH, 2);
        if (spell == Rs2CombatSpells.FIRE_STRIKE) c.put(FIRE, 3);
        return c;
    }

    public static int staffFor(int runeId) {
        switch (runeId) {
            case AIR: return STAFF_AIR;
            case WATER: return STAFF_WATER;
            case EARTH: return STAFF_EARTH;
            case FIRE: return STAFF_FIRE;
            default: return 0;
        }
    }

    public static Map<Integer, Integer> ownedMap(AccountSnapshot snap) {
        Map<Integer, Integer> m = new HashMap<>();
        for (Map<Integer, Integer> src : List.of(snap.getInventory(), snap.getEquipment(), snap.getBank())) {
            src.forEach((k, v) -> m.merge(k, v, Integer::sum));
        }
        return m;
    }

    private static boolean canCast(Rs2CombatSpells spell, int magicLevel, int wornStaff, Map<Integer, Integer> owned,
                                   int xpNeeded) {
        int casts = casts(spell, magicLevel, xpNeeded);
        for (Map.Entry<Integer, Integer> e : cost(spell).entrySet()) {
            if (wornStaff != 0 && staffFor(e.getKey()) == wornStaff) continue;
            if (owned.getOrDefault(e.getKey(), 0) < casts * e.getValue()) return false;
        }
        return true;
    }

    private static OptionalInt best(int[][] table, int level, AccountSnapshot snap) {
        int bestId = 0, bestReq = -1;
        for (int[] e : table) {
            if (e[1] <= level && e[1] >= bestReq && snap.owned(e[0]) > 0) {
                bestId = e[0];
                bestReq = e[1];
            }
        }
        return bestReq < 0 ? OptionalInt.empty() : OptionalInt.of(bestId);
    }

    private static int level(AccountSnapshot snap, Skill s) {
        return snap.getLevels().getOrDefault(s, 1);
    }
}
