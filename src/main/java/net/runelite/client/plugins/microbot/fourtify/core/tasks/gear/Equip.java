package net.runelite.client.plugins.microbot.fourtify.core.tasks.gear;

import net.runelite.client.plugins.microbot.fourtify.core.activity.NeededItem;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.List;

/**
 * Puts on gear that is carried but not worn: the best usable item of the first such need. With the bank open
 * the inventory shows deposit options instead of "Wield" or "Wear", so it waits for the bank to close.
 * Equipping takes effect on the next tick, so it worked when the item shows as worn within
 * {@value #WAIT_TICKS} ticks.
 */
public class Equip implements Task {
    static final int WAIT_TICKS = 2;
    /** Equipment slots, as the client numbers them. */
    public static final int WEAPON_SLOT = 3;
    public static final int SHIELD_SLOT = 5;

    private final List<NeededItem> gear;

    public Equip(List<NeededItem> gear) {
        this.gear = gear;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return !state.isBankOpen() && next(state) != null;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        NeededItem need = next(state);
        int id = need.carried(state);
        if (!act.equip(id)) {
            throw new IllegalStateException("could not equip item " + id);
        }
        return new WaitFor("put on " + need, WAIT_TICKS, (before, now) -> now.getEquipment().contains(id));
    }

    /**
     * Room to put it on. A two-handed weapon over a one-handed weapon and a shield
     * takes both off; one goes where the two-handed weapon was carried, the other needs a free slot.
     */
    public static boolean canWield(NeededItem need, GameState state) {
        return !need.isTwoHanded() || !state.isFull() || state.wornIn(WEAPON_SLOT) < 0 || state.wornIn(SHIELD_SLOT) < 0;
    }

    /** The first gear need not worn whose item is carried, with room to put it on. */
    private NeededItem next(GameState state) {
        for (NeededItem need : gear) {
            if (need.mustBeWorn() && !need.isWorn(state) && need.carried(state) != null && canWield(need, state)) {
                return need;
            }
        }
        return null;
    }
}
