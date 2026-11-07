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
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final PrayerFlickTiming timing = new PrayerFlickTiming();
    private final FallingRocks rocks = new FallingRocks();

    private volatile PrayerMode mode = PrayerMode.LAZY_FLICK;
    private volatile boolean attackHeld;
    private volatile WorldPoint dodgeTarget;
    private int dodgeTick;
    private int lastNoSafeTileLog = NO_TICK;
    private volatile int lastGameTick = NO_TICK;
    private int previousAnimation = -1;
    private int lastAttackTick = NO_TICK;
    private boolean ratsAttacking;
    /** The protection prayer we last asked for and the tick we asked in: the game shows it only a tick later. */
    private Rs2PrayerEnum expectedPrayer;
    /** Projectiles already handled (ProjectileMoved fires every frame) and the tick they were first seen; logged ids. */
    private final Map<Projectile, Integer> seenProjectiles = new IdentityHashMap<>();
    private final Set<Integer> loggedAnimations = new HashSet<>();
    private final Set<Integer> loggedProjectiles = new HashSet<>();
    private int expectedTick = NO_TICK;

    public void setMode(PrayerMode mode) {
        this.mode = mode;
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
        Decision decision = timing.decide(tick, mode, ratsAttacking);
        Rs2PrayerEnum want = decision.style == null ? null : prayerFor(decision.style);
        boolean trustExpected = expectedTick == tick || (eventPhase && expectedTick == tick - 1);
        Rs2PrayerEnum active = trustExpected ? expectedPrayer : Rs2Prayer.getActiveProtectionPrayer();
        boolean pulse = allowPulse && decision.pulse && expectedTick != tick;
        if (want == null) {
            if (active != null) {
                click(active, tick);
                expectedPrayer = null;
            }
        } else if (active != want) {
            // A different protection prayer replaces the active one: one click, no separate switch-off.
            click(want, tick);
            expectedPrayer = want;
            log.info("Scurrius prayer -> {} (anim {}, tick {})", want, previousAnimation, tick);
        } else if (pulse) {
            click(want, tick);
            click(want, tick);
            expectedPrayer = want;
        }
        if (want != null || active != null) {
            expectedTick = tick;
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

    /** One click, made on the client thread (no post-click sleep, no queue) and without waiting for the server. */
    private void click(Rs2PrayerEnum prayer, int tick) {
        try {
            Rs2Prayer.toggle(prayer);
        } catch (Exception ex) {
            log.warn("Scurrius prayer click failed (tick {})", tick, ex);
        }
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
        walkWorker.shutdownNow();
        timing.reset();
        rocks.clear();
        leaveFight();
    }
}
