package net.runelite.client.plugins.custom.actions;

import com.google.inject.Injector;
import net.runelite.client.plugins.microbot.Script;

import javax.inject.Inject;

/**
 * Base for scripts driven by an {@link Action} pipeline. Handles the generic wiring: auto-discovers
 * the actions ({@link ActionRegistry}), builds the {@link ActionRunner}, and each tick runs it over
 * a fresh state. Subclasses supply only the script-specific pieces via {@link #actionType()},
 * {@link #createState()} and (optionally) {@link #onInitialize()}.
 *
 * @param <S> the state type shared by this script's actions
 */
public abstract class ActionScript<S extends ScriptState> extends Script {

    @Inject
    protected Injector injector;

    private ActionRunner<S> runner;

    /** The action interface to scan for; its package is scanned for concrete implementations. */
    protected abstract Class<? extends Action<S>> actionType();

    /** Builds the per-tick state, or returns null to skip this tick (e.g. not initialised yet). */
    protected abstract S createState();

    /** Script-specific setup, run before the action pipeline is discovered/built. */
    protected void onInitialize() {
    }

    public void initialize() {
        onInitialize();
        runner = new ActionRunner<>(ActionRegistry.discover(actionType(), injector));
    }

    public void tick() {
        if (runner == null) {
            return;
        }
        S state = createState();
        if (state != null) {
            runner.run(state);
        }
    }
}
