package net.runelite.client.plugins.microbot.scurrius;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Decision;
import net.runelite.client.plugins.microbot.scurrius.PrayerFlickTiming.Style;
import net.runelite.client.plugins.microbot.scurrius.enums.PrayerMode;
import net.runelite.client.plugins.microbot.scurrius.enums.State;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Game side of the tick work: prayer flicking and falling-rock dodging. The plugin forwards GameTick, HitsplatApplied
 * and GraphicsObjectCreated events here (all on the client thread); the decisions are made by the pure
 * {@link PrayerFlickTiming}, {@link FallingRocks} and {@link SafeTileFinder}. Clicks are made on one worker so the client
 * thread never waits on them and prayer and walk clicks keep their order.
 */
@Slf4j
public class ScurriusTickDriver {
    static final int ROCKS_GRAPHICS_ID = 2644;
    private static final int MELEE_ATTACK_ANIMATION = 10693;
    private static final int RANGE_ATTACK_ANIMATION = 10695;
    private static final int MAGIC_ATTACK_ANIMATION = 10697;
    /** Scurrius attack speed in ticks: an animation id that stays the same this long is a new attack. */
    private static final int ATTACK_SPEED_TICKS = 5;
    /** A dodge walk is given this many ticks to arrive before the attack hold is released. */
    private static final int DODGE_TIMEOUT_TICKS = 6;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "scurrius-tick");
        thread.setDaemon(true);
        return thread;
    });
    private final PrayerFlickTiming timing = new PrayerFlickTiming();
    private final FallingRocks rocks = new FallingRocks();

    private volatile PrayerMode mode = PrayerMode.LAZY_FLICK;
    private volatile boolean attackHeld;
    private volatile WorldPoint dodgeTarget;
    private int dodgeTick;
    private int previousAnimation = -1;
    private int lastAttackTick = Integer.MIN_VALUE;
    private boolean ratsAttacking;

    public void setMode(PrayerMode mode) {
        this.mode = mode;
    }

    /** True while the player stands on, or is walking off, a falling-rock tile: the script must not click Attack. */
    public boolean isAttackHeld() {
        return attackHeld;
    }

    public void onRocksCreated(Client client, GraphicsObject graphicsObject, int tick) {
        rocks.add(toWorld(client, graphicsObject.getLocation()), tick);
    }

    public void onGameTick(Client client) {
        final int tick = client.getTickCount();
        rocks.refresh(tick, liveRockTiles(client));
        if (!inFight() || client.getLocalPlayer() == null) {
            leaveFight();
            return;
        }
        ratsAttacking = countRatsAttacking(client) > 0;
        dodge(client, tick);
        flick(client, tick);
    }

    /** A teleport out is still part of the fight: the prayer keeps protecting until the room is left. */
    private static boolean inFight() {
        return ScurriusScript.state == State.FIGHTING || ScurriusScript.state == State.TELEPORT_AWAY;
    }

    /** The prayer and dodge state is dropped when the fight stops; learned hit delays are kept. */
    private void leaveFight() {
        timing.clearPending();
        previousAnimation = -1;
        lastAttackTick = Integer.MIN_VALUE;
        dodgeTarget = null;
        attackHeld = false;
        ratsAttacking = false;
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
        final Set<WorldPoint> rockTiles = rocks.tiles();
        final WorldArea bossArea = bossFootprint(client);
        dodgeTick = tick;
        dodgeTarget = player; // placeholder until the worker picks a tile; keeps the attack held meanwhile
        attackHeld = true;
        worker.submit(() -> {
            try {
                moveToSafeTile(player, rockTiles, bossArea);
            } catch (Exception ex) {
                log.warn("Scurrius dodge failed", ex);
            }
        });
    }

    private void moveToSafeTile(WorldPoint player, Set<WorldPoint> rockTiles, WorldArea bossArea) {
        int range = Math.max(1, Rs2Combat.getAttackRange());
        WorldPoint safe = SafeTileFinder.find(player, rockTiles, Rs2Tile::isWalkable, ScurriusScript.FIGHT_ROOM,
                bossArea, range);
        if (safe == null) {
            log.warn("Scurrius dodge: no safe tile near {}", player);
            dodgeTarget = null;
            return;
        }
        dodgeTarget = safe;
        log.info("Scurrius dodge: rocks on {} -> {}", player, safe);
        Rs2Walker.walkFastCanvas(safe);
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

    /** Instance-aware, like Rs2Tile: inside an instance the template coordinates match Rs2Player.getWorldLocation(). */
    private static WorldPoint toWorld(Client client, LocalPoint local) {
        if (local == null) {
            return null;
        }
        return client.getTopLevelWorldView().getScene().isInstance()
                ? WorldPoint.fromLocalInstance(client, local)
                : WorldPoint.fromLocal(client, local);
    }

    private WorldArea bossFootprint(Client client) {
        for (NPC npc : client.getNpcs()) {
            if ("Scurrius".equals(npc.getName()) && npc.getComposition() != null) {
                int size = npc.getComposition().getSize();
                LocalPoint centre = npc.getLocalLocation();
                int offset = (size - 1) * Perspective.LOCAL_TILE_SIZE / 2;
                WorldPoint sw = toWorld(client, new LocalPoint(centre.getX() - offset, centre.getY() - offset));
                return sw == null ? null : new WorldArea(sw, size, size);
            }
        }
        return null;
    }

    // ---- prayer ----

    private void flick(Client client, int tick) {
        NPC scurrius = null;
        for (NPC npc : client.getNpcs()) {
            if ("Scurrius".equals(npc.getName())) {
                scurrius = npc;
                break;
            }
        }
        if (scurrius != null) {
            noteAnimation(tick, scurrius.getAnimation());
        }
        Decision decision = timing.decide(tick, mode, ratsAttacking);
        Rs2PrayerEnum want = decision.style == null ? null : prayerFor(decision.style);
        Rs2PrayerEnum active = Rs2Prayer.getActiveProtectionPrayer();
        final boolean pulse = decision.pulse;
        if (want == null) {
            if (active != null) {
                click(active);
            }
        } else if (active != want) {
            click(want);
        } else if (pulse) {
            click(want);
            click(want);
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
            timing.onAttackStarted(tick, style);
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
        timing.onHit(client.getTickCount(), event.getHitsplat().getAmount(), active == null ? null : styleFor(active),
                ambiguous);
    }

    private void click(Rs2PrayerEnum prayer) {
        worker.submit(() -> {
            try {
                Rs2Prayer.toggle(prayer); // a single click, no waiting for the server
            } catch (Exception ex) {
                log.warn("Scurrius prayer click failed", ex);
            }
        });
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
            case MELEE_ATTACK_ANIMATION:
                return Style.MELEE;
            case RANGE_ATTACK_ANIMATION:
                return Style.RANGE;
            case MAGIC_ATTACK_ANIMATION:
                return Style.MAGIC;
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

    /** Stops the worker. Called on plugin shutdown. */
    public void shutdown() {
        worker.shutdownNow();
        timing.reset();
        rocks.clear();
        leaveFight();
    }
}
