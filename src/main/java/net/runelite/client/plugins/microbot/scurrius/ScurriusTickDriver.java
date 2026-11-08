package net.runelite.client.plugins.microbot.scurrius;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Decision;
import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Style;
import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;
import net.runelite.client.plugins.microbot.scurrius.enums.State;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Game side of the tick work: prayer flicking and falling-rock dodging. The plugin forwards GameTick, AnimationChanged,
 * HitsplatApplied and GraphicsObjectCreated events here (all on the client thread); the decisions are made by the pure
 * {@link PrayerFlickTiming}, {@link FallingRocks} and {@link SafeTileFinder}.
 *
 * <p>Prayer clicks are made right on the client thread: off it every Microbot click sleeps 50-100 ms afterwards, which
 * is what made the switch slow. Walk clicks wait for the game to confirm the destination, so they run on their own
 * worker and can never delay a prayer click.
 *
 * <p>Coordinates: everything is in the same space as {@code Rs2Player.getWorldLocation()}, which inside an instance
 * (the private room) is template coordinates. Tiles from the scene are converted with {@link #toWorld}, and a
 * template tile is turned back into a scene tile with {@link #toLocal} before asking the collision map or clicking.
 *
 * <p>Tick clock: events that come from the game update (animation, hitsplat, graphics object) fire before that tick's
 * GameTick, and the client's tick count is increased only when GameTick fires. They are therefore labelled
 * {@code lastGameTick + 1}, so an animation, a hitsplat and a GameTick of the same server tick share one number.
 */
@Slf4j
public class ScurriusTickDriver {
    static final int ROCKS_GRAPHICS_ID = 2644;
    // Animation ids from net.runelite.api.gameval.AnimationID (NPC_RAT_BOSS_*): the normal and the "feeding" (low health) variants.
    private static final int MELEE_ATTACK_ANIMATION_1 = 10692;
    private static final int MELEE_ATTACK_ANIMATION_2 = 10693;
    private static final int RANGE_ATTACK_ANIMATION = 10694;
    private static final int RANGE_ATTACK_FEEDING_ANIMATION = 10695;
    private static final int MAGIC_ATTACK_ANIMATION = 10696;
    private static final int MAGIC_ATTACK_FEEDING_ANIMATION = 10697;
    /** First and last NPC_RAT_BOSS_* animation ids (spawn ... enrage 2): ids in here that are not attacks are not logged. */
    private static final int FIRST_BOSS_ANIMATION = 10686;
    private static final int LAST_BOSS_ANIMATION = 10706;
    // Projectile ids from net.runelite.api.gameval.SpotanimID: VFX_RAT_BOSS_PROJ_MAGIC_01 / VFX_RAT_BOSS_PROJ_RANGED_01.
    private static final int MAGIC_PROJECTILE = 2640;
    private static final int RANGED_PROJECTILE = 2642;
    private static final int PROJECTILE_MEMORY_TICKS = 12;
    /** Scurrius attack speed in ticks: an animation id that stays the same this long is a new attack. */
    private static final int ATTACK_SPEED_TICKS = 5;
    /** A dodge walk is given this many ticks to arrive before the attack hold is released. */
    private static final int DODGE_TIMEOUT_TICKS = 6;
    private static final int NO_TICK = Integer.MIN_VALUE;

    /** Walk clicks only: they block until the game confirms the destination, so they must not share a thread with prayers. */
    private final ExecutorService walkWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "scurrius-walk");
        thread.setDaemon(true);
        return thread;
    });
    /** Lazy flick only: the off-click (and an on-click from nothing) is placed a little way into the tick, not on it. */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "scurrius-prayer-delay");
        thread.setDaemon(true);
        return thread;
    });
    private final Random random = new Random();
    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> scheduledClick;
    private Rs2PrayerEnum scheduledPrayer;
    private boolean scheduledOn;
    private final PrayerFlickTiming timing = new PrayerFlickTiming();
    private final FallingRocks rocks = new FallingRocks();

    private volatile PrayerMode mode = PrayerMode.LAZY_FLICK;
    private volatile boolean variation = true;
    private volatile boolean mousePrayers = false;
    private volatile PrayerMode killMode = PrayerMode.LAZY_FLICK;
    private volatile int mixedAlwaysWeight = 50;
    private volatile int mixedLazyWeight = 50;
    private volatile int tickLabel = NO_TICK;
    private volatile long tickStartMs;
    /** Mouse clicks on the prayer icon run here: they move the mouse and wait, so never on the client thread. */
    private final ExecutorService mouseWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "scurrius-mouse");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean attackHeld;
    private volatile WorldPoint dodgeTarget;
    private int dodgeTick;
    private int lastNoSafeTileLog = NO_TICK;
    private volatile int lastGameTick = NO_TICK;
    private int previousAnimation = -1;
    private int lastAttackTick = NO_TICK;
    private boolean ratsAttacking;
    /** The protection prayer we last asked for and the tick we asked in: the game shows it only a tick later. */
    private volatile Rs2PrayerEnum expectedPrayer;
    /** Projectiles already handled (ProjectileMoved fires every frame) and the tick they were first seen; logged ids. */
    private final Map<Projectile, Integer> seenProjectiles = new IdentityHashMap<>();
    private final Set<Integer> loggedAnimations = new HashSet<>();
    private final Set<Integer> loggedProjectiles = new HashSet<>();
    private volatile int expectedTick = NO_TICK;

    public void setMode(PrayerMode mode) {
        this.mode = mode;
    }

    /** Anti-ban: switch prayers by clicking the icon with the mouse (falling back to the instant click when time is short). */
    public void setMousePrayers(boolean mousePrayers) {
        this.mousePrayers = mousePrayers;
    }

    /** Mixed prayer: the weights for always on and lazy flick when a kill picks its mode. */
    public void setMixedWeights(int alwaysOnWeight, int lazyWeight) {
        mixedAlwaysWeight = alwaysOnWeight;
        mixedLazyWeight = lazyWeight;
    }

    /** Called at the start and after every kill: in Mixed mode the next kill gets its own mode. */
    public void rollKillMode() {
        killMode = HumanHabits.pickKillMode(mixedAlwaysWeight, mixedLazyWeight, random);
        log.debug("Scurrius prayer mode for the next kill: {}", killMode);
    }

    private PrayerMode effectiveMode() {
        PrayerMode current = mode;
        return current == PrayerMode.MIXED ? killMode : current;
    }

    /** The first moment of a tick seen from an event or the GameTick; the mouse path measures its time budget from it. */
    private void markTick(int label) {
        if (label != tickLabel) {
            tickLabel = label;
            tickStartMs = System.currentTimeMillis();
        }
    }

    /** After eating or drinking has left the prayer tab: go back to it a moment later, like a player flicking would. */
    public void returnToPrayerTab() {
        if (!mousePrayers) {
            return;
        }
        scheduler.schedule(() -> mouseWorker.submit(() -> {
            try {
                if (inFight() && Rs2Tab.getCurrentTab() != InterfaceTab.PRAYER) {
                    Rs2Tab.switchTo(InterfaceTab.PRAYER);
                }
            } catch (Exception ex) {
                log.debug("Returning to the prayer tab failed", ex);
            }
        }), 300 + random.nextInt(900), TimeUnit.MILLISECONDS);
    }

    /** Lazy flick settings: ticks the prayer stays on after a hit (random between min and max) and the variation switch. */
    public void setLazySettings(int holdMin, int holdMax, boolean variation) {
        timing.setLazyHold(holdMin, holdMax);
        timing.setLazyVariation(variation);
        this.variation = variation;
    }

    /** True while the player stands on, or is walking off, a falling-rock tile: the script must not click Attack. */
    public boolean isAttackHeld() {
        return attackHeld;
    }

    /** Label for an event of the game update that is processed before this tick's GameTick. */
    private int updateTick(Client client) {
        int last = lastGameTick;
        return (last == NO_TICK ? client.getTickCount() : last) + 1;
    }

    public void onRocksCreated(Client client, GraphicsObject graphicsObject) {
        rocks.add(toWorld(client, graphicsObject.getLocation()), updateTick(client));
    }

    public void onGameTick(Client client) {
        final int tick = client.getTickCount();
        lastGameTick = tick;
        markTick(tick);
        rocks.refresh(tick, liveRockTiles(client));
        for (Iterator<Integer> it = seenProjectiles.values().iterator(); it.hasNext(); ) {
            if (tick - it.next() > PROJECTILE_MEMORY_TICKS) {
                it.remove();
            }
        }
        if (!inFight() || client.getLocalPlayer() == null) {
            leaveFight();
            return;
        }
        ratsAttacking = countRatsAttacking(client) > 0;
        dodge(client, tick);
        NPC scurrius = findScurrius(client);
        if (scurrius != null) {
            noteAnimation(tick, scurrius.getAnimation());
        }
        actOnPrayer(tick, true, false);
    }

    /**
     * Scurrius's animation changed: switch the prayer at once instead of waiting for the GameTick. The same decision is
     * repeated at the GameTick, which sees the click already made and does nothing more.
     */
    public void onAnimationChanged(Client client, AnimationChanged event) {
        if (!inFight() || !(event.getActor() instanceof NPC)) {
            return;
        }
        NPC npc = (NPC) event.getActor();
        if (!"Scurrius".equals(npc.getName())) {
            return;
        }
        logUnknownAnimation(npc.getAnimation());
        if (styleFor(npc.getAnimation()) == null) {
            return;
        }
        int tick = updateTick(client);
        markTick(tick);
        noteAnimation(tick, npc.getAnimation());
        actOnPrayer(tick, false, true);
    }

    /**
     * A projectile aimed at the player. Scurrius's magic and ranged projectiles name the style directly, so the prayer
     * switches the moment one appears, even when the attack animation was missed or is one we do not know. A projectile
     * from Scurrius with an unknown id is logged and changes nothing.
     */
    public void onProjectileMoved(Client client, ProjectileMoved event) {
        Projectile projectile = event.getProjectile();
        if (!inFight() || client.getLocalPlayer() == null || projectile.getTargetActor() != client.getLocalPlayer()) {
            return;
        }
        int tick = updateTick(client);
        if (seenProjectiles.putIfAbsent(projectile, tick) != null) {
            return; // not the first frame of this projectile
        }
        Style style = projectileStyle(projectile.getId());
        if (style == null) {
            net.runelite.api.Actor source = projectile.getSourceActor();
            if (source instanceof NPC && "Scurrius".equals(((NPC) source).getName())
                    && loggedProjectiles.add(projectile.getId())) {
                log.info("scurrius projectile {} -> unknown style, prayer kept (tick {})", projectile.getId(), tick);
            }
            return;
        }
        if (loggedProjectiles.add(projectile.getId())) {
            log.info("scurrius projectile {} -> {}", projectile.getId(), style);
        }
        markTick(tick);
        timing.onStyleSignal(tick, style);
        actOnPrayer(tick, false, true);
    }

    private void logUnknownAnimation(int animation) {
        boolean known = styleFor(animation) != null;
        boolean bossAnimation = animation >= FIRST_BOSS_ANIMATION && animation <= LAST_BOSS_ANIMATION;
        if (animation != -1 && !known && !bossAnimation && loggedAnimations.add(animation)) {
            log.info("scurrius anim {} (unknown, not treated as an attack)", animation);
        }
    }

    private static boolean inFight() {
        // A teleport out is still part of the fight: the prayer keeps protecting until the room is left.
        return ScurriusScript.state == State.FIGHTING || ScurriusScript.state == State.TELEPORT_AWAY;
    }

    /** The prayer and dodge state is dropped when the fight stops; learned hit delays are kept. */
    private void leaveFight() {
        timing.clearPending();
        previousAnimation = -1;
        lastAttackTick = NO_TICK;
        dodgeTarget = null;
        attackHeld = false;
        ratsAttacking = false;
        cancelScheduled();
        expectedPrayer = null;
        expectedTick = NO_TICK;
        seenProjectiles.clear();
    }

    // ---- dodging ----

    private void dodge(Client client, int tick) {
        final WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return;
        }
        if (dodgeTarget != null && (player.equals(dodgeTarget) || tick - dodgeTick > DODGE_TIMEOUT_TICKS)) {
            dodgeTarget = null;
        }
        final LocalPoint destination = client.getLocalDestinationLocation();
        final WorldPoint heading = destination == null ? player : toWorld(client, destination);
        final boolean underRock = rocks.contains(player);
        attackHeld = underRock || dodgeTarget != null;

        // Standing on a rock tile, or about to step onto one. A walk already under way to a safe tile is left alone.
        boolean needMove = rocks.contains(heading == null ? player : heading);
        boolean clickInFlight = dodgeTarget != null && tick - dodgeTick <= 1;
        if (!needMove || clickInFlight) {
            return;
        }
        WorldPoint safe = pickSafeTile(client, player);
        if (safe == null) {
            if (lastNoSafeTileLog == NO_TICK || tick - lastNoSafeTileLog > 5) {
                log.warn("Scurrius dodge: no safe tile near {}", player);
                lastNoSafeTileLog = tick;
            }
            return;
        }
        dodgeTick = tick;
        dodgeTarget = safe;
        attackHeld = true;
        log.info("Scurrius dodge: rocks on {} -> {}", player, safe);
        walkWorker.submit(() -> {
            try {
                Rs2Walker.walkFastCanvas(safe);
            } catch (Exception ex) {
                log.warn("Scurrius dodge walk failed", ex);
            }
        });
    }

    /** Runs on the client thread, where the collision lookups and the world-to-scene conversion are direct calls. */
    private WorldPoint pickSafeTile(Client client, WorldPoint player) {
        int range = 1;
        try {
            range = Math.max(1, Rs2Combat.getAttackRange());
        } catch (Exception ex) {
            log.debug("Attack range unavailable, assuming melee", ex);
        }
        return SafeTileFinder.find(player, rocks.tiles(),
                SafeTileFinder.walkable(point -> toLocal(client, point), Rs2Tile::isWalkable),
                ScurriusScript.FIGHT_ROOM, bossFootprint(client), range);
    }

    private Set<WorldPoint> liveRockTiles(Client client) {
        Set<WorldPoint> live = new HashSet<>();
        for (GraphicsObject object : client.getTopLevelWorldView().getGraphicsObjects()) {
            if (object.getId() == ROCKS_GRAPHICS_ID) {
                WorldPoint tile = toWorld(client, object.getLocation());
                if (tile != null) {
                    live.add(tile);
                }
            }
        }
        return live;
    }

    /** Scene tile to the space of Rs2Player.getWorldLocation(): template coordinates inside an instance. */
    private static WorldPoint toWorld(Client client, LocalPoint local) {
        if (local == null) {
            return null;
        }
        return client.getTopLevelWorldView().getScene().isInstance()
                ? WorldPoint.fromLocalInstance(client, local)
                : WorldPoint.fromLocal(client, local);
    }

    /** The reverse of {@link #toWorld}: a template tile inside an instance is looked up in the instance chunk table. */
    private static LocalPoint toLocal(Client client, WorldPoint world) {
        boolean instance = client.getTopLevelWorldView().isInstance();
        LocalPoint local = instance ? Rs2LocalPoint.fromWorldInstance(world)
                : LocalPoint.fromWorld(client.getTopLevelWorldView(), world);
        if (local == null) {
            local = instance ? LocalPoint.fromWorld(client.getTopLevelWorldView(), world) : null;
        }
        return local;
    }

    /** Scurrius's footprint in template/world space, as the box around its converted corner tiles (instance chunks may be rotated). */
    private WorldArea bossFootprint(Client client) {
        NPC scurrius = findScurrius(client);
        if (scurrius == null || scurrius.getComposition() == null) {
            return null;
        }
        int size = scurrius.getComposition().getSize();
        LocalPoint centre = scurrius.getLocalLocation();
        int offset = (size - 1) * Perspective.LOCAL_TILE_SIZE / 2;
        WorldPoint a = toWorld(client, new LocalPoint(centre.getX() - offset, centre.getY() - offset));
        WorldPoint b = toWorld(client, new LocalPoint(centre.getX() + offset, centre.getY() + offset));
        if (a == null || b == null) {
            return null;
        }
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        return new WorldArea(minX, minY, Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, a.getPlane());
    }

    private static NPC findScurrius(Client client) {
        for (NPC npc : client.getNpcs()) {
            if ("Scurrius".equals(npc.getName())) {
                return npc;
            }
        }
        return null;
    }

    // ---- prayer ----

    /**
     * Decides and clicks. {@code eventPhase} is true when called from the game update (before the GameTick): the game
     * has not applied clicks of the previous tick yet there, so our own record of them is trusted over the game state.
     */
    private void actOnPrayer(int tick, boolean allowPulse, boolean eventPhase) {
        PrayerMode currentMode = effectiveMode();
        Decision decision = timing.decide(tick, currentMode, ratsAttacking);
        Rs2PrayerEnum want = decision.style == null ? null : prayerFor(decision.style);
        boolean trustExpected = expectedTick == tick || (eventPhase && expectedTick == tick - 1);
        Rs2PrayerEnum active = trustExpected ? expectedPrayer : Rs2Prayer.getActiveProtectionPrayer();
        boolean pulse = allowPulse && decision.pulse && expectedTick != tick;
        boolean lazy = currentMode == PrayerMode.LAZY_FLICK;
        if (want == null) {
            if (active != null && !isScheduled(active, false)) {
                cancelScheduled();
                if (lazy) {
                    schedule(active, false, PrayerFlickTiming.offDelayMs(random), tick);
                } else {
                    click(active, tick);
                    expectedPrayer = null;
                    expectedTick = tick;
                }
            }
            return;
        }
        if (active != want) {
            // A different protection prayer replaces the active one: one click, no separate switch-off.
            if (lazy && variation && active == null) {
                // Turning on from nothing: a little way into the tick. A style switch below stays immediate.
                if (!isScheduled(want, true)) {
                    cancelScheduled();
                    schedule(want, true, PrayerFlickTiming.onDelayMs(random), tick);
                    logOn(want, tick, decision.tag);
                }
                return;
            }
            cancelScheduled();
            click(want, tick);
            expectedPrayer = want;
            expectedTick = tick;
            logOn(want, tick, decision.tag);
            return;
        }
        cancelScheduled(); // already on and wanted on: a pending off-click is dropped (hold, or the next attack)
        if (allowPulse && decision.pulse && expectedTick != tick) {
            click(want, tick);
            click(want, tick);
            expectedPrayer = want;
            expectedTick = tick;
        }
    }

    private void logOn(Rs2PrayerEnum want, int tick, String tag) {
        log.info("Scurrius prayer -> {} (anim {}, tick {}){}", want, previousAnimation, tick,
                tag.isEmpty() ? "" : " [" + tag + "]");
    }

    private boolean isScheduled(Rs2PrayerEnum prayer, boolean on) {
        synchronized (scheduleLock) {
            return scheduledClick != null && scheduledPrayer == prayer && scheduledOn == on;
        }
    }

    private void cancelScheduled() {
        synchronized (scheduleLock) {
            if (scheduledClick != null) {
                scheduledClick.cancel(false);
                scheduledClick = null;
                scheduledPrayer = null;
            }
        }
    }

    /** Runs {@link #fireScheduled} after the delay, off the client thread; a new decision cancels it first. */
    private void schedule(Rs2PrayerEnum prayer, boolean on, int delayMs, int tick) {
        synchronized (scheduleLock) {
            scheduledPrayer = prayer;
            scheduledOn = on;
            scheduledClick = scheduler.schedule(() -> fireScheduled(prayer, on, tick), delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void fireScheduled(Rs2PrayerEnum prayer, boolean on, int tick) {
        synchronized (scheduleLock) {
            if (scheduledClick == null || scheduledPrayer != prayer || scheduledOn != on) {
                return; // cancelled meanwhile
            }
            scheduledClick = null;
            scheduledPrayer = null;
        }
        if (!inFight()) {
            return;
        }
        try {
            boolean active = Rs2Prayer.isPrayerActive(prayer);
            if (active == on) {
                return;
            }
            performClick(prayer, tick);
            expectedPrayer = on ? prayer : null;
            expectedTick = tick;
            if (!on) {
                log.info("Scurrius prayer off (held {} ticks)", timing.lastHoldTicks());
            }
        } catch (Exception ex) {
            log.warn("Scurrius delayed prayer click failed (tick {})", tick, ex);
        }
    }

    private void noteAnimation(int tick, int animation) {
        Style style = styleFor(animation);
        boolean sameAnimationAsBefore = animation == previousAnimation;
        previousAnimation = animation;
        if (style == null) {
            return;
        }
        if (!sameAnimationAsBefore || tick - lastAttackTick >= ATTACK_SPEED_TICKS) {
            lastAttackTick = tick;
            timing.onStyleSignal(tick, style);
        }
    }

    public void onHitsplat(Client client, HitsplatApplied event) {
        Player player = client.getLocalPlayer();
        int type = event.getHitsplat().getHitsplatType();
        if (!inFight() || player == null || event.getActor() != player
                || type == HitsplatID.POISON || type == HitsplatID.VENOM || type == HitsplatID.HEAL
                || type == HitsplatID.DISEASE) {
            return;
        }
        WorldPoint here = Rs2Player.getWorldLocation();
        boolean ambiguous = countRatsAttacking(client) > 0 || (here != null && rocks.contains(here));
        Rs2PrayerEnum active = Rs2Prayer.getActiveProtectionPrayer();
        timing.onHit(updateTick(client), event.getHitsplat().getAmount(), active == null ? null : styleFor(active),
                ambiguous);
    }

    /**
     * One click, without waiting for the server. By default it is made right here on the client thread (no post-click
     * sleep, no queue). With mouse prayers it goes to the mouse worker and clicks the icon.
     */
    private void click(Rs2PrayerEnum prayer, int tick) {
        if (mousePrayers) {
            long start = tickStartMs;
            mouseWorker.submit(() -> mouseClick(prayer, tick, start));
            return;
        }
        try {
            Rs2Prayer.toggle(prayer);
        } catch (Exception ex) {
            log.warn("Scurrius prayer click failed (tick {})", tick, ex);
        }
    }

    /** A click from a worker thread: the mouse path when there is time, else (and always when mouse prayers are off) the menu click. */
    private void performClick(Rs2PrayerEnum prayer, int tick) {
        if (mousePrayers) {
            mouseClick(prayer, tick, tickStartMs);
        } else {
            Rs2Prayer.toggle(prayer);
        }
    }

    private void mouseClick(Rs2PrayerEnum prayer, int tick, long tickStart) {
        try {
            boolean tabOpen = Rs2Tab.getCurrentTab() == InterfaceTab.PRAYER;
            PrayerClickPlan.Plan plan = PrayerClickPlan.decide(true, tabOpen, System.currentTimeMillis() - tickStart);
            if (plan == PrayerClickPlan.Plan.INSTANT) {
                instantClick(prayer);
                return;
            }
            if (plan == PrayerClickPlan.Plan.TAB_THEN_MOUSE) {
                Rs2Tab.switchTo(InterfaceTab.PRAYER);
                if (Rs2Tab.getCurrentTab() != InterfaceTab.PRAYER
                        || !PrayerClickPlan.mouseStillInTime(System.currentTimeMillis() - tickStart)) {
                    instantClick(prayer);
                    return;
                }
            }
            Rs2Prayer.toggle(prayer); // with the prayer tab open this clicks the icon itself
        } catch (Exception ex) {
            log.warn("Scurrius prayer mouse click failed (tick {}), clicking instantly", tick, ex);
            instantClick(prayer);
        }
    }

    /** The menu click that works with the prayer tab closed: what Rs2Prayer uses when the icon is not visible. */
    private static void instantClick(Rs2PrayerEnum prayer) {
        NewMenuEntry entry = new NewMenuEntry()
                .param0(-1)
                .param1(prayer.getIndex())
                .opcode(MenuAction.CC_OP.getId())
                .identifier(1)
                .itemId(-1)
                .option("Activate");
        Microbot.doInvokeQuick(entry, Rs2UiHelper.getDefaultRectangle());
    }

    private static int countRatsAttacking(Client client) {
        int count = 0;
        for (NPC npc : client.getNpcs()) {
            String name = npc.getName();
            if (name != null && name.toLowerCase().contains("giant rat") && !npc.isDead()
                    && npc.getInteracting() == client.getLocalPlayer()) {
                count++;
            }
        }
        return count;
    }

    static Style styleFor(int animation) {
        switch (animation) {
            case MELEE_ATTACK_ANIMATION_1:
            case MELEE_ATTACK_ANIMATION_2:
                return Style.MELEE;
            case RANGE_ATTACK_ANIMATION:
            case RANGE_ATTACK_FEEDING_ANIMATION:
                return Style.RANGE;
            case MAGIC_ATTACK_ANIMATION:
            case MAGIC_ATTACK_FEEDING_ANIMATION:
                return Style.MAGIC;
            default:
                return null;
        }
    }

    static Style projectileStyle(int projectileId) {
        switch (projectileId) {
            case MAGIC_PROJECTILE:
                return Style.MAGIC;
            case RANGED_PROJECTILE:
                return Style.RANGE;
            default:
                return null;
        }
    }

    static Style styleFor(Rs2PrayerEnum prayer) {
        switch (prayer) {
            case PROTECT_MELEE:
                return Style.MELEE;
            case PROTECT_RANGE:
                return Style.RANGE;
            case PROTECT_MAGIC:
                return Style.MAGIC;
            default:
                return null;
        }
    }

    static Rs2PrayerEnum prayerFor(Style style) {
        switch (style) {
            case RANGE:
                return Rs2PrayerEnum.PROTECT_RANGE;
            case MAGIC:
                return Rs2PrayerEnum.PROTECT_MAGIC;
            default:
                return Rs2PrayerEnum.PROTECT_MELEE;
        }
    }

    /** Stops the walk worker. Called on plugin shutdown. */
    public void shutdown() {
        cancelScheduled();
        scheduler.shutdownNow();
        walkWorker.shutdownNow();
        mouseWorker.shutdownNow();
        timing.reset();
        rocks.clear();
        leaveFight();
    }
}
