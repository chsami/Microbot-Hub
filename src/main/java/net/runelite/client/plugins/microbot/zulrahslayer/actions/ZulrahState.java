package net.runelite.client.plugins.microbot.zulrahslayer.actions;

import net.runelite.client.plugins.microbot.actions.ActionState;
import net.runelite.client.plugins.microbot.actions.ScriptState;

import java.util.ArrayList;
import java.util.List;

/**
 * Zulrah's {@link ScriptState}. Supplies the per-tick {@link ActionState} list (record/query logic
 * is inherited from {@link ScriptState}) plus the durable {@link FightContext} that lives across
 * ticks and is also mutated by the plugin's animation events.
 */
public class ZulrahState implements ScriptState {

    private final FightContext context;
    private final List<ActionState> actionStates = new ArrayList<>();

    public ZulrahState(FightContext context) {
        this.context = context;
    }

    /** Durable fight state (current phase, stand tile, attack timer, gear, melee-dodge, ...). */
    public FightContext context() {
        return context;
    }

    @Override
    public List<ActionState> actionStates() {
        return actionStates;
    }
}
