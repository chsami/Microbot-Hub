package net.runelite.client.plugins.microbot.FourtifyFishing.data;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.fourtify.core.activity.NeededItem;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Harpoons that fish, by item ID: the Echo harpoon, the Gauntlet's harpoons, noted and placeholder items do not
 * count. A harpoon fishes carried or worn once the Fishing level allows it; every harpoon but the plain one only on a
 * members world. The wield requirements (60 Attack for a dragon harpoon) do not matter: carrying one is enough.
 * <p>
 * The catch rate is a percentage of the plain harpoon's: the dragon harpoon catches 20% more often, the crystal
 * harpoon 35% more (12.5% more than the dragon one), the barb-tail harpoon as the plain one. Charged or uncharged,
 * the infernal harpoon and its Trailblazer versions fish as a dragon harpoon (the charged ones also cook a third of the
 * catch, which destroys that fish: nothing arrives). The inactive crystal harpoon fishes as a dragon harpoon.
 */
@Getter
public enum Harpoon {
    /** The plain harpoon has no level and no members rule. */
    HARPOON(0, false, 100, ItemID.HARPOON),
    BARB_TAIL(0, true, 100, ItemID.HUNTING_BARBED_HARPOON),
    DRAGON(61, true, 120, ItemID.DRAGON_HARPOON, ItemID.TRAILBLAZER_HARPOON_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_HARPOON_NO_INFERNAL),
    INFERNAL(61, true, 120, ItemID.INFERNAL_HARPOON, ItemID.INFERNAL_HARPOON_EMPTY, ItemID.TRAILBLAZER_HARPOON,
            ItemID.TRAILBLAZER_HARPOON_EMPTY, ItemID.TRAILBLAZER_RELOADED_HARPOON, ItemID.TRAILBLAZER_RELOADED_HARPOON_EMPTY),
    /** Needs Fishing 71 like the active one, then fishes as a dragon harpoon. */
    CRYSTAL_INACTIVE(71, true, 120, ItemID.CRYSTAL_HARPOON_INACTIVE),
    CRYSTAL(71, true, 135, ItemID.CRYSTAL_HARPOON);

    /** The Fishing level that allows the harpoon (0: any). */
    private final int level;
    private final boolean members;
    /** The catch rate in percent of the plain harpoon's. */
    private final int catchRate;
    private final int[] itemIds;

    Harpoon(int level, boolean members, int catchRate, int... itemIds) {
        this.level = level;
        this.members = members;
        this.catchRate = catchRate;
        this.itemIds = itemIds;
    }

    /** The level and the world allow this harpoon. */
    public boolean allowedIn(GameState state) {
        return level <= state.level(Skill.FISHING) && (!members || state.isMembersWorld());
    }

    /** True when the player has a harpoon that the level and the world allow, carried, worn or in the bank. */
    public static boolean owned(GameState state) {
        return bestCatchRate(state) > 0;
    }

    /**
     * The catch rate in percent of the best harpoon that the level and the world allow and that the player carries,
     * wears or has in the bank (a missing one is fetched before fishing), or 0 when there is none.
     */
    public static int bestCatchRate(GameState state) {
        int best = 0;
        for (Harpoon harpoon : values()) {
            if (harpoon.catchRate > best && harpoon.allowedIn(state)) {
                for (int id : harpoon.itemIds) {
                    if (state.hasAny(id) || state.wearsAny(id) || state.bankCount(id) > 0) {
                        best = harpoon.catchRate;
                    }
                }
            }
        }
        return best;
    }

    /**
     * A harpoon the level and the world allow, carried or worn; a missing one is fetched from the bank, the
     * best the bank holds first. A better harpoon in the bank than the best one carried or worn is fetched too,
     * and the worse ones carried go back to the bank. Harpoons of the same catch rate count as equal.
     */
    public static NeededItem need() {
        List<Integer> ids = new ArrayList<>();
        Map<Integer, Harpoon> byId = new HashMap<>();
        for (Harpoon harpoon : values()) {
            for (int id : harpoon.itemIds) {
                ids.add(id);
                byId.put(id, harpoon);
            }
        }
        ToIntFunction<Integer> rate = id -> byId.get(id).catchRate;
        // A stable sort: ties stay in the order above.
        ids.sort(Comparator.comparingInt(rate).reversed());
        return new NeededItem("a harpoon for your Fishing level", 1, ids, (state, id) -> byId.get(id).allowedIn(state)).upgradable(rate);
    }
}
