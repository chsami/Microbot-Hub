package net.runelite.client.plugins.microbot.accountstarter.run;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.accountstarter.AccountStarterConfig;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.Spots;
import net.runelite.client.plugins.microbot.accountstarter.logic.StuckTracker;
import net.runelite.client.plugins.microbot.accountstarter.logic.WildernessGuard;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.death.Rs2Death;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Walks block the loop thread, so the time-critical checks also run from {@link #onGameTick}. */
@Slf4j
public final class Watchdogs {
    static final int STUCK_STRIKES = 3;
    static final int DEATH_TRIES = 2;
    static final int MAX_DEATHS = 3;
    static final int TRADE_ESCAPES = 3;
    private static final long TICK_WARN_GAP_MS = 60_000L;

    private final AccountStarterConfig config;
    private final Runnable abortActivity;
    private final Consumer<String> stop;
    private final StuckTracker stuck = new StuckTracker();
    private final AtomicBoolean cancelling = new AtomicBoolean();

    private volatile Map<Skill, Integer> baseline;
    private volatile Set<Skill> protectedSkills = Collections.emptySet();
    private volatile Skill xpLeak;
    private volatile boolean wildSeen;
    private volatile boolean routeCut;
    private volatile boolean escaping;
    private long stuckAfterMs;
    private long lastTickWarnMs;
    private int deathFailures;
    private int deaths;
    private boolean stopped;

    public Watchdogs(AccountStarterConfig config, Runnable abortActivity, Consumer<String> stop) {
        this.config = config;
        this.abortActivity = abortActivity;
        this.stop = stop;
    }

    public void reset(long stuckAfterMs) {
        this.stuckAfterMs = stuckAfterMs;
        stuck.reset();
        disarm();
        xpLeak = null;
        wildSeen = false;
        routeCut = false;
        escaping = false;
        deathFailures = 0;
        deaths = 0;
        stopped = false;
    }

    /** Called while the loop is held by a break, a pause or a login. */
    public void paused() {
        stuck.pause();
    }

    public void disarm() {
        baseline = null;
    }

    public boolean armedFor(Set<Skill> skills) {
        return baseline != null && protectedSkills.equals(skills);
    }

    public void arm(AccountSnapshot s, Set<Skill> protectedSkills) {
        // EnumMap's copy constructor throws on an empty non-EnumMap source
        Map<Skill, Integer> b = new EnumMap<>(Skill.class);
        b.putAll(s.getXp());
        // baseline first: the client-thread tick must never pair a new skill with the old baseline
        baseline = Collections.unmodifiableMap(b);
        this.protectedSkills = protectedSkills.isEmpty() ? Collections.emptySet() : EnumSet.copyOf(protectedSkills);
    }

    /** Client thread. Never sleeps or walks. */
    public void onGameTick(GameTick e) {
        try {
            if (escaping) return;
            if (Rs2Pvp.isInWilderness()) {
                wildSeen = true;
                // every tick: a walk started after the first cancel would go deeper in
                if (Rs2Walker.getCurrentTarget() != null) {
                    cancelWalk("Account Starter: entered the Wilderness");
                }
                return;
            }
            if (Rs2Walker.getCurrentTarget() != null) {
                Pathfinder pf = Rs2PathApi.getPathfinder();
                List<WorldPoint> path = pf == null ? null : pf.getPath();
                // a null path is still being computed; the next tick checks it
                if (path != null && !WildernessGuard.routeSafe(path, Rs2PathApi::isInWilderness)) {
                    routeCut = true;
                    cancelWalk("Account Starter: route crossed the Wilderness");
                }
            }
            Map<Skill, Integer> base = baseline;
            if (base != null && xpLeak == null) {
                Optional<Skill> leak = XpGuard.protectedSkillGained(base, xpNow(Microbot.getClient()), protectedSkills);
                if (leak.isPresent()) {
                    xpLeak = leak.get();
                    cancelWalk("Account Starter: XP in a protected skill");
                }
            }
        } catch (Exception ex) {
            long now = System.currentTimeMillis();
            if (now - lastTickWarnMs >= TICK_WARN_GAP_MS) {
                lastTickWarnMs = now;
                log.warn("Account Starter: watchdog tick: {}", ex.toString());
            }
        }
    }

    /** Off the client thread: a walk holds the walker's route lock while it waits on that thread. */
    private void cancelWalk(String why) {
        if (!cancelling.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                Rs2Walker.setTarget(null, why);
            } finally {
                cancelling.set(false);
            }
        });
    }

    public Optional<Outcome> check(AccountSnapshot s, String activity) {
        // the plugin stops asynchronously; never act twice on one stop
        if (stopped) return Optional.of(Outcome.cont());
        if (wildSeen || Rs2Pvp.isInWilderness()) return Optional.of(leaveWilderness());
        if (routeCut) {
            routeCut = false;
            log.info("Account Starter: route crossed the Wilderness; walk cancelled");
        }
        Map<Skill, Integer> base = baseline;
        Skill leak = xpLeak;
        if (leak == null && base != null) leak = XpGuard.protectedSkillGained(base, s.getXp(), protectedSkills).orElse(null);
        if (leak != null) return Optional.of(stopWith("XP landed in protected skill " + leak.getName()));
        if (tradeVisible()) return Optional.of(closeTrade());
        if (Rs2Death.hasDeathToHandle()) return Optional.of(recoverDeath());
        Optional<Outcome> st = checkStuck(s, activity);
        if (st.isPresent()) return st;
        if (!config.questOnly() && membersWorld()) return Optional.of(stopWith("F2P worlds only (world " + Microbot.getClient().getWorld() + ", " + Microbot.getClient().getWorldType() + ")"));
        return Optional.empty();
    }

    private static boolean tradeVisible() {
        return Rs2Widget.isWidgetVisible(InterfaceID.Trademain.UNIVERSE) || Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.UNIVERSE);
    }

    /** Escape only closes interfaces with "Esc closes the current interface" on, so it is capped. */
    private Outcome closeTrade() {
        log.info("Account Starter: a trade window opened; closing it");
        for (int i = 0; i < TRADE_ESCAPES; i++) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            if (Global.sleepUntil(() -> !tradeVisible(), 1200)) return Outcome.cont();
        }
        return stopWith("a trade window would not close");
    }

    private Outcome leaveWilderness() {
        escaping = true;
        abortActivity.run();
        log.warn("Account Starter: entered the Wilderness; walking out to Lumbridge");
        boolean out = Rs2Walker.walkTo(Spots.LUMBRIDGE_BANK, 4);
        return stopWith(out ? "entered the Wilderness" : "entered the Wilderness and could not walk out");
    }

    private Outcome recoverDeath() {
        abortActivity.run();
        log.info("Account Starter: died; recovering items");
        boolean ok;
        boolean wasLocked = BreakHandlerScript.isLockState();
        BreakHandlerScript.setLockState(true);
        try {
            ok = Rs2Death.recoverItems(config.deathBudget(), config.useDeathsOffice());
        } finally {
            BreakHandlerScript.setLockState(wasLocked);
        }
        if (ok) {
            deathFailures = 0;
            if (++deaths >= MAX_DEATHS) return stopWith("died " + MAX_DEATHS + " times this session");
            return Outcome.natural("recovered after death");
        }
        if (++deathFailures >= DEATH_TRIES) return stopWith("could not recover items after death");
        return Outcome.natural("item recovery failed; trying again");
    }

    private Optional<Outcome> checkStuck(AccountSnapshot s, String activity) {
        long now = System.currentTimeMillis();
        long xp = s.getXp().values().stream().mapToLong(Integer::longValue).sum();
        stuck.observe(now, s.getLocation(), xp, activity);
        if (!stuck.stuck(now, stuckAfterMs)) return Optional.empty();
        int strikes = stuck.strikes(activity);
        if (!config.recoverWhenStuck()) {
            log.info("Account Starter: {} looks stuck (strike {}); recovery is off", activity, strikes);
            return Optional.empty();
        }
        log.info("Account Starter: {} looks stuck (strike {})", activity, strikes);
        abortActivity.run();
        return Optional.of(strikes >= STUCK_STRIKES
                ? Outcome.parked("stuck " + STUCK_STRIKES + " times")
                : Outcome.natural("stuck; choosing again"));
    }

    private boolean membersWorld() {
        // the world list, not getWorldType(): that flag can read MEMBERS on a world a free account is logged in to
        return LoginManager.isMemberWorld(Microbot.getClient().getWorld());
    }

    private Outcome stopWith(String reason) {
        stopped = true;
        stop.accept(reason);
        return Outcome.cont();
    }

    private static Map<Skill, Integer> xpNow(Client client) {
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
        for (Skill s : Skill.values()) xp.put(s, client.getSkillExperience(s));
        return xp;
    }
}
