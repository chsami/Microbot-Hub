package net.runelite.client.plugins.microbot.FourtifyWoodcutting.data;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.fourtify.core.activity.NeededItem;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Axes that cut trees, by item ID: noted axes, placeholders and same-named items such as the
 * Gauntlet's crystal axe do not count. An axe works from the inventory or worn once the Woodcutting level allows
 * it; a members axe only on a members world. Uncharged infernal and
 * inactive crystal axes still cut, as dragon axes.
 */
@Getter
public enum Axe {
    BRONZE(1, false, 1000, ItemID.BRONZE_AXE),
    IRON(1, false, 1500, ItemID.IRON_AXE),
    STEEL(6, false, 2000, ItemID.STEEL_AXE),
    BLACK(11, false, 2250, ItemID.BLACK_AXE),
    MITHRIL(21, false, 2500, ItemID.MITHRIL_AXE),
    ADAMANT(31, false, 3000, ItemID.ADAMANT_AXE),
    RUNE(41, false, 3500, ItemID.RUNE_AXE),
    GILDED(41, false, 3500, ItemID.TRAIL_GILDED_AXE),
    DRAGON(61, true, 3850, ItemID.DRAGON_AXE, ItemID.TRAILBLAZER_AXE_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_AXE_NO_INFERNAL),
    THIRD_AGE(61, true, 3850, ItemID._3A_AXE),
    INFERNAL(61, true, 3850, ItemID.INFERNAL_AXE, ItemID.INFERNAL_AXE_EMPTY, ItemID.TRAILBLAZER_AXE, ItemID.TRAILBLAZER_AXE_EMPTY,
            ItemID.TRAILBLAZER_RELOADED_AXE, ItemID.TRAILBLAZER_RELOADED_AXE_EMPTY),
    CRYSTAL(71, true, 4025, ItemID.CRYSTAL_AXE, ItemID.CRYSTAL_AXE_INACTIVE),
    BRONZE_FELLING(1, true, 1000, ItemID.BRONZE_AXE_2H),
    IRON_FELLING(1, true, 1500, ItemID.IRON_AXE_2H),
    STEEL_FELLING(6, true, 2000, ItemID.STEEL_AXE_2H),
    BLACK_FELLING(11, true, 2250, ItemID.BLACK_AXE_2H),
    MITHRIL_FELLING(21, true, 2500, ItemID.MITHRIL_AXE_2H),
    ADAMANT_FELLING(31, true, 3000, ItemID.ADAMANT_AXE_2H),
    RUNE_FELLING(41, true, 3500, ItemID.RUNE_AXE_2H),
    DRAGON_FELLING(61, true, 3850, ItemID.DRAGON_AXE_2H),
    THIRD_AGE_FELLING(61, true, 3850, ItemID._3A_AXE_2H),
    CRYSTAL_FELLING(71, true, 4025, ItemID.CRYSTAL_AXE_2H, ItemID.CRYSTAL_AXE_2H_INACTIVE);

    private final int level;
    private final boolean members;
    /** The chance to get a log from a swing, in tenths of a percent of a bronze axe's (bronze 1000). */
    private final int chopChance;
    private final int[] itemIds;

    Axe(int level, boolean members, int chopChance, int... itemIds) {
        this.level = level;
        this.members = members;
        this.chopChance = chopChance;
        this.itemIds = itemIds;
    }

    /** Item IDs that cut as a dragon axe (see {@link #chopChanceOf}), though listed with the crystal axes. */
    private static final Set<Integer> CUT_AS_DRAGON = new HashSet<>(Arrays.asList(ItemID.CRYSTAL_AXE_INACTIVE, ItemID.CRYSTAL_AXE_2H_INACTIVE));

    /** The chop chance of one of this class's item IDs: an inactive crystal axe cuts as a dragon axe. */
    private static int chopChanceOf(Axe axe, int id) {
        return CUT_AS_DRAGON.contains(id) ? DRAGON.chopChance : axe.chopChance;
    }

    private int chopChanceOf(int id) {
        return chopChanceOf(this, id);
    }

    /** Usable now: the level allows it, the world allows it, and the player carries or wears it. */
    public boolean usableIn(GameState state) {
        return allowedIn(state) && (state.hasAny(itemIds) || state.wearsAny(itemIds));
    }

    /** Woodcutting level and world type allow this axe. */
    public boolean allowedIn(GameState state) {
        return level <= state.level(Skill.WOODCUTTING) && (!members || state.isMembersWorld());
    }

    /**
     * An axe the level and world allow, carried or worn; a missing one is fetched from the bank, the best the
     * bank holds first (the highest chop chance, and a one-handed axe before a felling axe of the same chance).
     * A better axe in the bank than the best one carried or worn is fetched too, the worse ones carried going back
     * to the bank. Axes of the same chop chance count as equal, so none is swapped for another of its kind.
     * The relative chop chances are bronze 100%, iron 150%, steel 200%, black
     * 225%, mithril 250%, adamant 300%, rune 350%, dragon 385%, crystal 402.5%. The gilded axe is a rune axe
     * and the 3rd Age and charged or uncharged infernal axes cut as dragon axes; a felling
     * axe cuts as its one-handed kind (it has no separate rate). Inactive crystal axes have no rate of their own
     * but act as dragon axes.
     */
    public static NeededItem need() {
        List<Integer> ids = new ArrayList<>();
        Map<Integer, Axe> byId = new HashMap<>();
        for (Axe axe : values()) {
            for (int id : axe.itemIds) {
                ids.add(id);
                byId.put(id, axe);
            }
        }
        ToIntFunction<Integer> chance = id -> chopChanceOf(byId.get(id), id);
        // A stable sort: ties stay in the order above.
        ids.sort(Comparator.comparingInt(chance).reversed().thenComparing(id -> byId.get(id).name().endsWith("_FELLING")));
        return new NeededItem("an axe for your Woodcutting level", 1, ids, (state, id) -> byId.get(id).allowedIn(state))
                .upgradable(chance);
    }

    /**
     * The chop chance (bronze 1000) of the best axe the player can use: one carried or worn that the level and world
     * allow, or one in the bank that they will fetch before chopping. Bronze when none is known, as the bank trip then
     * finds the player the best axe it can. An inactive crystal axe counts as a dragon axe.
     */
    public static int bestChopChance(GameState state) {
        int best = BRONZE.chopChance;
        for (Axe axe : values()) {
            if (!axe.allowedIn(state)) {
                continue;
            }
            for (int id : axe.itemIds) {
                if (axe.chopChanceOf(id) > best && (state.hasAny(id) || state.wearsAny(id) || state.bankCount(id) > 0)) {
                    best = axe.chopChanceOf(id);
                }
            }
        }
        return best;
    }

    /** True when the player has an axe they can cut with right now. */
    public static boolean any(GameState state) {
        for (Axe axe : values()) {
            if (axe.usableIn(state)) {
                return true;
            }
        }
        return false;
    }
}
