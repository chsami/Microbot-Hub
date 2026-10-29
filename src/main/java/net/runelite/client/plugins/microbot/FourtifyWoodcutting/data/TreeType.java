package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.fourtify.core.game.Gate;
import net.runelite.client.plugins.microbot.fourtify.core.game.Spot;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Trees the plugin chops, in order of Woodcutting level. Object names, menu actions and log IDs are the game's: the
 * action is not always "Chop down" (redwoods are "Cut", arctic pines "Cut down"). Levels, members-only trees, XP per
 * log, respawn times, chopping times and cut chances are the game's published values. Their spots are in {@code SpotData}
 * and their cut chances in {@code ChanceData}.
 * <p>
 * A woodcutting roll comes every 4 ticks whatever the axe. A tree falls on the first log after its chopping time
 * ({@code chopTicks}, counted from the first cut), except a regular tree, which falls after its first log; it then
 * grows back after {@code respawnTicks}.
 */
@Getter
@RequiredArgsConstructor
public enum TreeType {
    TREE("Tree", "Chop down", "Logs", ItemID.LOGS, 1, false, 100, 25, 0),
    OAK("Oak tree", "Chop down", "Oak logs", ItemID.OAK_LOGS, 15, false, 14, 37.5, 45),
    WILLOW("Willow tree", "Chop down", "Willow logs", ItemID.WILLOW_LOGS, 30, false, 14, 67.5, 50),
    TEAK("Teak tree", "Chop down", "Teak logs", ItemID.TEAK_LOGS, 35, true, 15, 85, 50),
    MAPLE("Maple tree", "Chop down", "Maple logs", ItemID.MAPLE_LOGS, 45, false, 59, 100, 100),
    MAHOGANY("Mahogany tree", "Chop down", "Mahogany logs", ItemID.MAHOGANY_LOGS, 50, true, 14, 125, 100),
    ARCTIC_PINE("Arctic pine tree", "Cut down", "Arctic pine logs", ItemID.ARCTIC_PINE_LOG, 54, true, 14, 40, 140),
    YEW("Yew tree", "Chop down", "Yew logs", ItemID.YEW_LOGS, 60, false, 99, 175, 190),
    CAMPHOR("Camphor tree", "Chop down", "Camphor logs", ItemID.CAMPHOR_LOGS, 66, true, 99, 143.5, 200),
    MAGIC("Magic tree", "Chop down", "Magic logs", ItemID.MAGIC_LOGS, 75, true, 199, 250, 390),
    IRONWOOD("Ironwood tree", "Chop down", "Ironwood logs", ItemID.IRONWOOD_LOGS, 80, true, 200, 175, 400),
    REDWOOD("Redwood tree", "Cut", "Redwood logs", ItemID.REDWOOD_LOGS, 90, true, 199, 380, 440),
    ROSEWOOD("Rosewood tree", "Chop down", "Rosewood logs", ItemID.ROSEWOOD_LOGS, 92, true, 205, 212.5, 458);

    private final String objectName;
    /** The menu action that cuts it. */
    private final String action;
    private final String logName;
    /** The logs this tree gives. Matched by ID: the untradeable Tutorial Island logs (2511) share the name "Logs". */
    private final int logId;
    private final int level;
    /** Members-only: free-to-play woodcutting is trees, oak, willow, maple and yew. */
    private final boolean members;
    /** Ticks a felled tree takes to grow back; for regular trees the most of their random 36 to 60 seconds. */
    private final int respawnTicks;
    /** Woodcutting XP for each log. */
    private final double xp;
    /**
     * Ticks of chopping a tree lasts, counted from the first cut: it falls on the first log after that. 0 for a
     * regular tree, which falls after one log.
     */
    private final int chopTicks;

    /**
     * Chance in 256ths that a woodcutting roll gives a log at this real Woodcutting level, with an axe of the given
     * chop chance (bronze 1000, see {@link Axe#getChopChance()}), from the published chances in {@code ChanceData}.
     */
    public int chanceIn256(int woodcuttingLevel, int axeChopChance) {
        return Chances.in256(this, woodcuttingLevel, axeChopChance);
    }

    /** The chance in 256ths with a bronze axe at this tree's own level: the slowest it can go. */
    public int getSlowestChance() {
        return chanceIn256(level, 1000);
    }

    public List<Spot> getSpots() {
        return Spots.forTree(this);
    }

    /** What every spot of the tree needs besides the Woodcutting level: tree types found only on the Sailing islands. */
    private static final Map<TreeType, List<Gate>> GATES = new EnumMap<>(TreeType.class);

    static {
        GATES.put(CAMPHOR, Arrays.asList(Gate.skill(Skill.SAILING, 45), Gate.questStarted(Quest.TROUBLED_TORTUGANS)));
        GATES.put(IRONWOOD, Collections.singletonList(Gate.skill(Skill.SAILING, 72)));
        GATES.put(ROSEWOOD, Collections.singletonList(Gate.skill(Skill.SAILING, 79)));
    }

    /**
     * Requirements of the tree type itself, which every spot of it shares, besides its Woodcutting level and members
     * flag. Camphor trees grow only on the Great Conch, whose dock takes 45 Sailing and Troubled Tortugans started;
     * ironwood trees only on Sunbleak Island (72 Sailing), rosewood trees only on Drumstick Isle (79 Sailing). Access to
     * single spots is in {@code GateData} instead.
     */
    public List<Gate> gates() {
        return GATES.getOrDefault(this, Collections.emptyList());
    }

    @Override
    public String toString() {
        return objectName;
    }
}
