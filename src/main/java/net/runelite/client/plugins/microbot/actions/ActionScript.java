package net.runelite.client.plugins.microbot.actions;

import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base for scripts driven by an {@link Action} pipeline. Handles the generic wiring: auto-discovers
 * the actions ({@link ActionRegistry}), builds the {@link ActionRunner}, and each tick runs it over
 * a fresh state. Subclasses supply only the script-specific pieces via {@link #actionType()},
 * {@link #createState()} and (optionally) {@link #onInitialize()}.
 *
 * @param <S> the state type shared by this script's actions
 */
@Slf4j
public abstract class ActionScript<S extends ScriptState> extends Script {

    @Inject
    protected Injector injector;

    private ActionRunner<S> runner;

    // Guards {@link #gameTick()} so an externally-driven tick never stacks on the previous one.
    private final AtomicBoolean tickInProgress = new AtomicBoolean(false);

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

    /**
     * Runs a single guarded tick, driven externally (e.g. a plugin's onGameTick) instead of the
     * internal fixed-delay scheduler in {@link #run()}. The tick body is dispatched to the script's
     * worker pool rather than run inline: onGameTick fires on the client thread, but Rs2 walking /
     * clicking use blocking sleeps and must not run there. Applies the same login and base-{@link
     * Script} gate ({@code super.run()}) the scheduler loop uses, and skips overlapping ticks so a
     * slow tick never stacks on the next one. Call {@link #initialize()} once before ticking.
     */
    public final void gameTick() {
        if (!tickInProgress.compareAndSet(false, true)) {
            return;
        }
        scheduledExecutorService.submit(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }
                tick();
            } catch (Exception ex) {
                onException(ex);
                log.error("Exception during tick.", ex);
            } finally {
                tickInProgress.set(false);
            }
        });
    }

    public void onException(Exception e) {

    }

    private void tick() {
        if (runner == null) {
            return;
        }
        S state = createState();
        if (state != null) {
            runner.run(state);
        }
    }
}
