package net.runelite.client.plugins.microbot.fourtify.core.tasks.bank;

import net.runelite.client.plugins.microbot.fourtify.core.activity.NeededItem;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Task;
import net.runelite.client.plugins.microbot.fourtify.core.engine.WaitFor;
import net.runelite.client.plugins.microbot.fourtify.core.game.GameState;

import java.util.List;

/**
 * With the bank open, withdraws the best usable item for the first unmet need the bank can meet, one need per
 * step, or else the better item of the first upgradable need that the bank holds ({@link NeededItem#upgrade}).
 * It worked when the inventory holds more of that item.
 */
public class TakeFromBank implements Task {
    static final int WAIT_TICKS = 3;

    private final List<NeededItem> needs;

    public TakeFromBank(List<NeededItem> needs) {
        this.needs = needs;
    }

    @Override
    public boolean shouldRun(GameState state) {
        return state.isBankOpen() && next(state) != null;
    }

    @Override
    public WaitFor run(GameState state, Actions act) {
        NeededItem need = next(state);
        boolean upgrade = need.isMet(state);
        int id = upgrade ? need.upgrade(state) : need.inBank(state);
        if (upgrade) {
            need.tried(id);
        }
        act.withdraw(id, upgrade ? need.amount() : need.missing(state));
        return new WaitFor("withdraw " + need, WAIT_TICKS, (before, now) -> now.count(id) > before.count(id));
    }

    /** The first need that is not met, or has an upgrade, that the bank can meet, and that there is room for. */
    private NeededItem next(GameState state) {
        for (NeededItem need : needs) {
            boolean fetch = need.isMet(state) ? need.upgrade(state) != null : need.inBank(state) != null;
            if (fetch && BankTrip.hasRoomFor(need, state)) {
                return need;
            }
        }
        return null;
    }
}
