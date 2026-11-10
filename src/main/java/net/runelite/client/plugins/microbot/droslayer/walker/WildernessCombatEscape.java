package net.runelite.client.plugins.microbot.droslayer.walker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.death.Rs2Death;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;

import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Walker-owned emergency actions. Client ticks observe; a worker issues at most one action. */
@Slf4j
public final class WildernessCombatEscape implements AutoCloseable
{
    public enum State { IDLE, ESCAPING, ESCAPED, BLOCKED, LOGOUT_WAIT_COMBAT, LOGGED_OUT }
    private static final long TELEPORT_SETTLE_MS = 3600L;
    private static final long FINAL_SETTLE_MS = 4800L;
    private static final int MAX_TELEPORT_ATTEMPTS = 3;
    interface Environment
    {
        GameState gameState();
        WorldPoint location();
        boolean dead();
        long lastDeathAt();
        boolean combatGrace();
        boolean actorInteraction();
        boolean teleBlocked();
        boolean paused();
        int gameTick();
    }

    private final Environment environment;
    private final Runnable cancelWalk;
    private final Executor actionExecutor;
    private final ExecutorService worker;
    private final Runnable teleportAction;
    private final Runnable logoutAction;
    private Runnable southAction = WildernessSouthEscape::clickSouth;
    private final Consumer<String> status;
    private final AtomicLong generation = new AtomicLong();
    private final ThreadLocal<Long> actionGeneration = new ThreadLocal<>();
    private volatile State state = State.IDLE;
    private volatile String blockedReason = "";
    private volatile Future<?> action;
    private volatile boolean actionRunning;
    private long lastActionAt;
    private long lastSouthAt;
    private long encounterStartedAt;
    private final WildernessQuietEncounter quietEncounter = new WildernessQuietEncounter();
    private int attempts;
    private boolean logoutDispatched;
    private WorldPoint origin;
    private boolean playerAttackSeen;

    WildernessCombatEscape(Client client, Runnable cancelWalk)
    {
        this.environment = new Environment()
        {
            public GameState gameState() { return client.getGameState(); }
            public WorldPoint location()
            {
                Player local = client.getLocalPlayer();
                return local == null ? null : local.getWorldLocation();
            }
            public boolean dead()
            {
                Player local = client.getLocalPlayer();
                return local != null && local.isDead();
            }
            public long lastDeathAt()
            {
                return Rs2Death.getLastDeathTime() == null ? 0L : Rs2Death.getLastDeathTime().toEpochMilli();
            }
            public boolean combatGrace() { return Rs2Player.isInCombat(); }
            public boolean actorInteraction()
            {
                Player local = client.getLocalPlayer();
                return local != null && local.getInteracting() != null && !local.getInteracting().isDead();
            }
            public boolean teleBlocked() { return Rs2Player.isTeleBlocked(); }
            public boolean paused() { return Microbot.pauseAllScripts.get(); }
            public int gameTick() { return client.getTickCount(); }
        };
        this.cancelWalk = cancelWalk;
        this.worker = WalkerMouseControl.profiledExecutor(Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "EfficientWalker-wilderness-escape");
            thread.setDaemon(true);
            return thread;
        }));
        this.actionExecutor = worker;
        this.teleportAction = this::teleportOnce;
        this.logoutAction = () -> Rs2Player.logout();
        this.status = reason -> Microbot.status = reason;
    }

    /** State tests use the same controller with observations and clicks supplied separately. */
    WildernessCombatEscape(Environment environment, Runnable cancelWalk, Executor executor,
        Runnable teleportAction, Runnable logoutAction, Consumer<String> status)
    {
        this.environment = environment;
        this.cancelWalk = cancelWalk;
        this.worker = null;
        this.actionExecutor = executor;
        this.teleportAction = teleportAction;
        this.logoutAction = logoutAction;
        this.status = status;
    }

    void setSouthAction(Runnable action) { southAction = action; }

    public State getState() { return state; }
    public String getBlockedReason() { return blockedReason; }

    /** Called only on the client thread. The context covers arrived/fighting states, not travel. */
    void tick(WildernessThreatPolicy.Snapshot threat, long now)
    {
        tick(threat, now, false);
    }

    void tick(WildernessThreatPolicy.Snapshot threat, long now, boolean southEscape)
    {
        tick(threat, now, southEscape, false);
    }

    void tick(WildernessThreatPolicy.Snapshot threat, long now, boolean southEscape, boolean preferLogout)
    {
        if (state == State.ESCAPED || state == State.LOGGED_OUT) return;
        if (environment.gameState() != GameState.LOGGED_IN || environment.location() == null)
        {
            if (logoutDispatched && environment.gameState() == GameState.LOGIN_SCREEN)
                transition(State.LOGGED_OUT, "Threat logout observed");
            return;
        }
        WorldPoint here = environment.location();
        int wilderness = here == null ? 0 : Rs2Pvp.getWildernessLevelFrom(here);
        if (environment.dead()) { reset(); return; }
        if (origin != null && wilderness == 0 && !here.equals(origin))
        {
            // A respawn is not evidence that the escape teleport landed.
            if (environment.lastDeathAt() >= encounterStartedAt)
            {
                reset();
                return;
            }
            transition(State.ESCAPED, "Threat escape observed outside Wilderness");
            return;
        }
        if (wilderness <= 0)
        {
            // A blocked/unfinished escape must never freeze the next activity forever
            // once the client confirms that the player is no longer in Wilderness.
            // ESCAPED is retained as a receipt until the owning script acknowledges it.
            if (state != State.IDLE && state != State.ESCAPED && state != State.LOGGED_OUT)
            {
                if (origin != null) transition(State.ESCAPED, "Threat area exited; confirmed outside Wilderness");
                else resetActions();
            }
            return;
        }
        boolean watching = quietEncounter.observe(threat, environment.gameTick());
        if (threat.hasAttackAction()) playerAttackSeen = true;
        if (state == State.IDLE && !watching) return;
        if (actionRunning && now - lastActionAt > 10000L)
        {
            generation.incrementAndGet();
            Future<?> pending = action;
            if (pending != null) pending.cancel(true);
            actionRunning = false;
            transition(State.BLOCKED, "Escape mouse action timed out; no repeated actions");
            return;
        }
        if ((state == State.BLOCKED || state == State.LOGOUT_WAIT_COMBAT
                || state == State.ESCAPING && playerAttackSeen)
            && !actionRunning && quietEncounter.isQuiet(environment.gameTick()))
        {
            quietEncounter.release(threat);
            resetActions();
            status.accept("Wilderness player activity quiet for 10 game ticks - resuming task");
            log.info("[EfficientWalker] Quiet threat encounter released after 10 game ticks");
            return;
        }
        boolean canPreferLogout = preferLogout
            && mayLogout(environment.combatGrace(), environment.actorInteraction(), threat.isPvpCombat());
        if (state == State.BLOCKED && !(canPreferLogout
                && "Threat teleport blocked by Tele Block".equals(blockedReason)))
        {
            // A live player attack keeps trying once Tele Block ends. Other failed
            // actions (including a stalled mouse worker) remain held until released.
            if (!playerAttackSeen || (!southEscape && environment.teleBlocked())
                || !"Threat teleport blocked by Tele Block".equals(blockedReason)) return;
            transition(State.ESCAPING, "Tele Block ended during player attack - retrying teleport");
        }
        if (origin == null)
        {
            origin = here;
            encounterStartedAt = now;
            cancelWalk.run();
        }
        if (canPreferLogout)
        {
            if (actionRunning) return;
            if (logoutDispatched)
            {
                if (now - lastActionAt > FINAL_SETTLE_MS)
                    transition(State.BLOCKED, "Logout was not observed; no repeated logout clicks");
                return;
            }
            logoutDispatched = true;
            lastActionAt = now;
            transition(State.ESCAPING, "Revenant goblin threat - logging out while combat is clear");
            dispatch(logoutAction);
            return;
        }
        if (southEscape && playerAttackSeen && (wilderness > 30 || environment.teleBlocked()))
        {
            transition(State.ESCAPING, "Player attack - running south on minimap until teleport is available");
            if (!actionRunning && (lastSouthAt == 0L || now - lastSouthAt >= TELEPORT_SETTLE_MS))
            {
                lastSouthAt = now;
                lastActionAt = now;
                dispatch(southAction);
            }
            return;
        }
        if (wilderness > 30)
        {
            if (!mayLogout(environment.combatGrace(), environment.actorInteraction(), threat.isPvpCombat()))
            {
                transition(State.LOGOUT_WAIT_COMBAT, "Threat above level 30 - waiting for combat to clear");
                return;
            }
            if (logoutDispatched)
            {
                if (now - lastActionAt > FINAL_SETTLE_MS)
                    transition(State.BLOCKED, "Logout was not observed; no repeated logout clicks");
                return;
            }
            logoutDispatched = true;
            lastActionAt = now;
            transition(State.ESCAPING, "Threat above level 30 - logging out once combat cleared");
            dispatch(logoutAction);
            return;
        }
        if (environment.teleBlocked())
        {
            transition(State.BLOCKED, "Threat teleport blocked by Tele Block");
            return;
        }
        if (lastSouthAt != 0L && attempts == 0 && !actionRunning) lastActionAt = 0L;
        transition(State.ESCAPING, "Threat at level 30 or below - teleporting");
        if (actionRunning) return;
        long settle = attempts >= MAX_TELEPORT_ATTEMPTS ? FINAL_SETTLE_MS : TELEPORT_SETTLE_MS;
        if (lastActionAt != 0L && now - lastActionAt < settle) return;
        if (attempts >= MAX_TELEPORT_ATTEMPTS && !playerAttackSeen)
        {
            transition(State.BLOCKED, "Three teleport attempts did not land");
            return;
        }
        attempts++;
        lastActionAt = now;
        dispatch(teleportAction);
    }

    static boolean mayLogout(boolean combatGrace, boolean actorInteraction, boolean pvpCombat)
    {
        return !combatGrace && !actorInteraction && !pvpCombat;
    }

    private void teleportOnce()
    {
        if (Rs2Player.isTeleBlocked()) return;
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || Rs2Pvp.getWildernessLevelFrom(here) <= 0
            || Rs2Pvp.getWildernessLevelFrom(here) > 30) return;
        Rs2ItemModel item = Rs2Inventory.items().filter(candidate -> isLevel30Escape(candidate.getName()))
            .sorted(Comparator.comparingInt(candidate -> escapePreference(candidate.getName())))
            .filter(candidate -> directAction(candidate) != null).findFirst().orElse(null);
        if (item != null)
        {
            log.info("[EfficientWalker] Wilderness escape attempt={} item={} destination={}",
                attempts, item.getName(), destinationAction(item.getName()));
            Rs2Inventory.interact(item, directAction(item));
            return;
        }
        // Worn jewellery exposes destinations in its equipment actions. Never equip a travel item.
        Rs2ItemModel equipped = Rs2Equipment.items().stream().filter(candidate -> isLevel30Escape(candidate.getName()))
            .min(Comparator.comparingInt(candidate -> escapePreference(candidate.getName()))).orElse(null);
        if (equipped != null)
        {
            log.info("[EfficientWalker] Wilderness escape attempt={} equipped={} destination={}",
                attempts, equipped.getName(), destinationAction(equipped.getName()));
            Rs2Equipment.interact(equipped.getId(), destinationAction(equipped.getName()));
            return;
        }
        transition(State.BLOCKED, "No usable one-click level-30 escape item carried or worn");
    }

    private String directAction(Rs2ItemModel item)
    {
        String destination = destinationAction(item.getName());
        String action = item.getAction(destination.toLowerCase(Locale.ROOT));
        if (action == null && item.getIndexOfSubAction(destination) != null) action = destination;
        return action;
    }

    /** Shared with bank preparation, so supply choices match the actual emergency executor. */
    public static boolean isLevel30Escape(String name) { return escapePreference(name) != Integer.MAX_VALUE; }

    public static int escapePreference(String name)
    {
        if (name == null) return Integer.MAX_VALUE;
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("royal seed pod")) return 0;
        if (lower.equals("amulet of eternal glory") || lower.matches("amulet of glory(?:\\(t\\))?\\s*\\([1-6]\\)")) return 1;
        if (lower.matches("ring of wealth\\s*\\([1-5]\\)")) return 2;
        if (lower.matches("combat bracelet\\s*\\([1-6]\\)")) return 3;
        if (lower.matches("skills necklace\\s*\\([1-6]\\)")) return 4;
        return Integer.MAX_VALUE;
    }

    public static String destinationAction(String name)
    {
        switch (escapePreference(name))
        {
            case 0: return "Commune";
            case 1: return "Edgeville";
            case 2: return "Grand Exchange";
            case 3: return "Monastery";
            case 4: return "Fishing Guild";
            default: return "";
        }
    }

    private void dispatch(Runnable click)
    {
        long request = generation.get();
        actionRunning = true;
        Runnable command = () ->
        {
            try
            {
                if (request != generation.get() || environment.paused()) return;
                actionGeneration.set(request);
                click.run();
            }
            catch (RuntimeException ex)
            {
                transition(State.BLOCKED, "Escape action failed: " + ex.getClass().getSimpleName());
                log.warn("[EfficientWalker] Wilderness escape action failed", ex);
            }
            finally
            {
                actionGeneration.remove();
                if (request == generation.get()) actionRunning = false;
            }
        };
        if (worker != null) action = worker.submit(command);
        else actionExecutor.execute(command);
    }

    private void transition(State next, String reason)
    {
        Long request = actionGeneration.get();
        if (Thread.currentThread().isInterrupted() || (request != null && request != generation.get())) return;
        if (state != next || !blockedReason.equals(reason))
            log.info("[EfficientWalker] Wilderness safety state={} reason={}", next, reason);
        state = next;
        blockedReason = reason;
        status.accept(reason);
    }

    public void reset()
    {
        quietEncounter.reset();
        resetActions();
    }

    private void resetActions()
    {
        generation.incrementAndGet();
        Future<?> pending = action;
        if (pending != null) pending.cancel(true);
        action = null;
        actionRunning = false;
        state = State.IDLE;
        blockedReason = "";
        attempts = 0;
        lastActionAt = 0L;
        lastSouthAt = 0L;
        encounterStartedAt = 0L;
        logoutDispatched = false;
        origin = null;
        playerAttackSeen = false;
    }

    @Override public void close() { reset(); if (worker != null) worker.shutdownNow(); }
}
