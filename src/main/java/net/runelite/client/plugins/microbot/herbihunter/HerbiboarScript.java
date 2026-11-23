package net.runelite.client.plugins.microbot.herbihunter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.NPCComposition;
import net.runelite.api.GameObject;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@Slf4j
public class HerbiboarScript extends Script {

    public static int herbiCaught;
    @Setter
    @Getter
    // Volatile: the panel reads it on the client thread every frame.
    public static volatile HerbiboarState state = HerbiboarState.INITIALIZING;
    @Getter
    private static volatile String activity;
    private boolean attackedTunnel;
    /** Latched in TUNNEL: finishId drops to 0 the moment the boar comes out. */
    private WorldPoint huntTunnel;
    private static final WorldPoint CAMP_BANK = new WorldPoint(3739, 3804, 0);
    /** Where the pendant's Digsite option lands, about 20 tiles from the barge. */
    private static final WorldPoint DIGSITE = new WorldPoint(3341, 3445, 0);
    /** Closer to the barge than this, walking beats spending a charge. */
    private static final int PENDANT_MIN_TILES = 30;
    private static final int MAX_TRAVEL_FAILS = 5;
    private static final String WALKER_CONFIG = "shortestpath", MUSHTREES = "useMagicMushtrees";
    private static final int[] DIGSITE_PENDANTS = {11190, 11191, 11192, 11193, 11194};
    private static final WorldPoint RETURN_LOCATION = new WorldPoint(3703, 3878, 0);

    /** The walker abandons the climb, so we click it. */
    private static final int[] HOLE_SHORTCUT_IDS = {31481, 31482};
    private static final int HOLE_SHORTCUT_AGILITY = 70;
    private static final int HOLE_SEARCH_DISTANCE = 40;
    /** The hole's ends straddle this latitude, separating the southern spots from the northern. */
    private static final int HOLE_DIVIDE_Y = 3823;
    /** A herbiboar further than this from our tunnel belongs to someone else. */
    private static final int HERBIBOAR_TUNNEL_RADIUS = 5;
    private static final WorldPoint HOLE_MOUTH_SOUTH = new WorldPoint(3716, 3815, 0);
    private static final WorldPoint HOLE_MOUTH_NORTH = new WorldPoint(3713, 3830, 0);
    private static final int HOLE_CLIMB_TIMEOUT_MS = 15000;
    /** Beyond this from the nearest mouth, walking round the cliff beats doubling back to it. */
    private static final int HOLE_PREFER_DISTANCE = 30;
    /** The left-click option on every start rock and trail object (not "Search"). */
    static final String TRAIL_ACTION = "Inspect";

    private static final int STALLS_BEFORE_SHORTCUT = 1;
    private static final int STALLS_BEFORE_RESET = 5;

    /** Searches take up to ~11s. */
    private static final int DISPATCH_DEADLINE_TICKS = 20;
    private static final String HARVEST_ACTION = "Harvest";
    private static final int[] HERB_SACKS = {ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN,
            ItemID.SLAYER_HERB_SACK_SILK, ItemID.SLAYER_HERB_SACK_SILK_OPEN};
    private static final int[] HERB_SACKS_CLOSED = {ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_SILK};
    private static final int HARVEST_READY_TIMEOUT_MS = 2400;
    private static final int TUNNEL_GIVE_UP_MS = 45000;
    private static final int CAMERA_STOP_TOLERANCE_MIN = 18;
    private static final int CAMERA_STOP_TOLERANCE_MAX = 42;
    private static final int CAMERA_TURN_TIMEOUT_MS = 2500;

    private int stalledWalks;
    private Instant tunnelSince;
    private Instant harvestSince;
    private static final int HARVEST_GIVE_UP_MS = 8000;
    private static final int MINIMAP_REACH_MIN = 7;
    private static final double MINIMAP_OUTER_BAND = 0.85;
    private static final int MINIMAP_SHORT_HOP_PERCENT = 15;
    private static final int MINIMAP_HOVER_PX = 40;
    private int confusedTrails;
    private Instant lastDrinkAt = Instant.EPOCH;
    private int drinkCooldownMs = nextDrinkCooldownMs();
    private boolean cameraFramed;
    private long frameDueAt;
    private boolean cameUpFromWelcome;
    private boolean warningToggleTried;
    private boolean wasLoggedOut;
    private Boolean savedAutoRun;
    private boolean savedAutoStamina;
    private int runBackOnAt = 5;
    private boolean wasRunOn = true;
    private static final int LOW_ENERGY_DRINK = 10;
    private Instant lastMove = Instant.now();
    private WorldPoint lastLocation;

    public void resetHerbisCaught() {
        herbiCaught = 0;
    }

    /** Rs2Camera.getAngleTo is current minus target, so positive needs LEFT. Pure. */
    static int cameraKeyFor(int signedDeltaDegrees, int toleranceDegrees) {
        if (Math.abs(signedDeltaDegrees) <= toleranceDegrees) return 0;
        return signedDeltaDegrees > 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
    }

    private int framedZoom;

    /** Stepped like scroll-wheel notches: setZoom on its own is one instant jump. */
    private void humanZoom(int target) {
        int from = Rs2Camera.getZoom();
        int notches = Rs2Random.betweenInclusive(3, 6);
        for (int i = 1; i <= notches; i++) {
            Rs2Camera.setZoom(from + Math.round((target - from) * (i / (float) notches)));
            sleep(Rs2Random.betweenInclusive(90, 210));
        }
    }

    /** Higher is closer. A relog, the game or a stray scroll can leave it zoomed in. */
    private void holdZoom() {
        if (framedZoom <= 0 || inFlight != null || Rs2Player.isMoving()) return;
        int now = Rs2Camera.getZoom();
        if (now <= framedZoom + 10) return;
        hbLog.detail("CAM", "Zoomed in to " + now + " (framed at " + framedZoom + "); zooming back out");
        humanZoom(framedZoom);
        sleep(Rs2Random.betweenInclusive(200, 400));
    }

    /** Below this the view is too flat: trail spots hide behind fungi and rocks. */
    static final int MIN_HUNTING_PITCH = 330;   // Rs2Camera pitch runs 128 (flat) to 383 (top-down)

    /** The client walker pitches the camera down on long walks and never restores it. */
    private void holdPitch() {
        if (framedZoom <= 0 || inFlight != null || Rs2Player.isMoving()) return;
        int now = Rs2Camera.getPitch();
        if (now >= MIN_HUNTING_PITCH) {
            pitchFixAt = 0;
            return;
        }
        long t = System.currentTimeMillis();
        if (pitchFixAt == 0) {
            pitchFixAt = t + habits.pitchFixDelayMs(paceRng);
            hbLog.detail("CAM", "Camera pitched down to " + now + "; raising it in " + (pitchFixAt - t) / 1000 + "s");
            return;
        }
        if (t < pitchFixAt) return;
        pitchFixAt = 0;
        hbLog.detail("CAM", "Camera pitched down to " + now + "; raising it back");
        Rs2Camera.setPitch(Rs2Random.betweenInclusive(345, 383));
        sleep(Rs2Random.betweenInclusive(200, 400));
    }

    private long pitchFixAt;

    /** Holds an arrow key: setYaw is ten blocking steps and judders, Rs2Camera.turnTo forces 3x speed. */
    private void turnCameraTo(int targetDegrees, BooleanSupplier doneEarly) {
        turnCameraTo(targetDegrees, Rs2Random.betweenInclusive(CAMERA_STOP_TOLERANCE_MIN, CAMERA_STOP_TOLERANCE_MAX), doneEarly);
    }

    private void turnCameraTo(int targetDegrees, int tolerance, BooleanSupplier doneEarly) {
        int delta = Rs2Camera.getAngleTo(targetDegrees);
        int key = cameraKeyFor(delta, tolerance);
        if (key == 0) return;
        long t0 = System.currentTimeMillis();
        int from = Rs2Camera.getAngle();
        Rs2Keyboard.keyHold(key);
        try {
            // Also stop once the gap changes sign: a narrow window can be jumped between polls, leaving the key down.
            sleepUntil(() -> doneEarly.getAsBoolean()
                    || Math.abs(Rs2Camera.getAngleTo(targetDegrees)) <= tolerance
                    || Integer.signum(Rs2Camera.getAngleTo(targetDegrees)) != Integer.signum(delta), CAMERA_TURN_TIMEOUT_MS);
        } finally {
            Rs2Keyboard.keyRelease(key);
        }
        hbLog.detail("CAM", "Turned the camera " + from + " -> " + Rs2Camera.getAngle() + " deg (wanted "
                + targetDegrees + " +-" + tolerance + ", gap was " + delta + ") in " + (System.currentTimeMillis() - t0)
                + "ms with " + (key == KeyEvent.VK_LEFT ? "LEFT" : "RIGHT") + " for " + state);
    }

    /** Not in startUp: that is the Swing thread, where camera moves freeze the UI. */
    private void frameCamera() {
        List<Runnable> steps = new ArrayList<>();
        if (Rs2Camera.getZoom() > 140) steps.add(() -> humanZoom(Rs2Random.betweenInclusive(100, 125)));
        Integer heading = headingToNearestStart();
        steps.add(() -> turnCameraTo(heading != null
                ? Math.floorMod(heading + (int) Rs2Random.randomGaussian(0, 12), 360)
                : Rs2Random.dicePercentage(50) ? Rs2Random.randomGaussian(132, 9) : Rs2Random.randomGaussian(299, 9),
                () -> false));
        int pitch = Rs2Camera.getPitch();
        if (pitch < MIN_HUNTING_PITCH) steps.add(() -> Rs2Camera.setPitch(Rs2Random.betweenInclusive(345, 383)));
        Collections.shuffle(steps);
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) sleep(Rs2Random.betweenInclusive(180, 560));
            steps.get(i).run();
        }
        // What the client clamped it to: the zoom guard holds us here.
        framedZoom = Rs2Camera.getZoom();
    }

    private static Integer headingToNearestStart() {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null) return null;
        WorldPoint rock = HerbiHunterPlugin.START_LOCATIONS.stream()
                .min(Comparator.comparingInt(me::distanceTo)).orElse(null);
        if (rock == null || rock.distanceTo(me) < 3) return null;
        return Math.floorMod(Rs2Camera.angleToTile(rock) - 90, 360);
    }

    private static boolean canHarvest(Rs2NpcModel npc) {
        if (npc == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPCComposition def = Microbot.getClient().getNpcDefinition(npc.getId());
            if (def == null || def.getActions() == null) return false;
            for (String a : def.getActions()) {
                if (HARVEST_ACTION.equals(a)) return true;
            }
            return false;
        }).orElse(false);
    }

    /** Never the whole-canvas stand-in clickbox; 60% of the box and 8px each way is enough to click. Pure. */
    static boolean insideViewport(Rectangle r, int vx, int vy, int vw, int vh) {
        if (r == null || r.width <= 0 || r.height <= 0 || r.width >= vw || r.height >= vh) return false;
        Rectangle in = r.intersection(new Rectangle(vx, vy, vw, vh));
        if (in.isEmpty()) return false;
        if (in.width == r.width && in.height == r.height) return true;
        return in.width >= 8 && in.height >= 8 && (double) in.width * in.height >= 0.6 * r.width * r.height;
    }

    private static boolean insideViewport(Rectangle r) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            return insideViewport(r, c.getViewportXOffset(), c.getViewportYOffset(),
                    c.getViewportWidth(), c.getViewportHeight());
        }).orElse(false);
    }

    private static boolean hasAction(Rs2TileObjectModel object, String action) {
        ObjectComposition comp = object.getObjectComposition();
        return comp != null && comp.getActions() != null
                && Arrays.stream(comp.getActions()).anyMatch(action::equalsIgnoreCase);
    }

    private static boolean partlyInView(Rectangle r) {
        return !insideViewport(r) && Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            return r.intersects(new Rectangle(c.getViewportXOffset(), c.getViewportYOffset(), c.getViewportWidth(), c.getViewportHeight()));
        }).orElse(false);
    }

    private static boolean onScreen(TileObject object) {
        if (object == null) return false;
        Shape box = hullOf(object);
        return box != null && insideViewport(box.getBounds());
    }

    /** The generic constructor clicks the centre tile, wrong for 2x1 driftwood. */
    private static Rs2TileObjectModel model(TileObject object) {
        return object instanceof GameObject ? new Rs2TileObjectModel((GameObject) object) : new Rs2TileObjectModel(object);
    }

    private boolean moveOnNextHover;
    private int cursorMisses;
    /** Tries past something standing in front of the target: 0 re-aim, 1 walk closer, 2-3 camera turns (then 2 again). */
    private int coverTries;
    private String cursorMissTarget;

    private boolean hoverOnce(Supplier<? extends Shape> hull, IntSupplier cameraAngle) {
        return hoverOnce(hull, cameraAngle, Collections.emptyList());
    }

    private boolean hoverOnce(Supplier<? extends Shape> hull, IntSupplier cameraAngle, List<Shape> neighbours) {
        Shape s = hull.get();
        if (s == null) return false;
        if (!insideViewport(s.getBounds())) return false;
        List<Rectangle> panels = panels();
        if (pointInside(s, panels) == null) {
            if (!nudgeCameraToward(cameraAngle.getAsInt(), () -> {
                Shape t = hull.get();
                return t != null && pointInside(t, panels) != null;
            })) return false;
            s = hull.get();
            if (s == null || pointInside(s, panels) == null) return false;
            moveOnNextHover = true;
        }
        List<Shape> avoid = new ArrayList<>(panels);
        avoid.addAll(neighbours);
        boolean clearOfNeighbours = pointInside(s, avoid) != null;
        java.awt.Point mouse = Microbot.getMouse().getMousePosition();
        if (moveOnNextHover || !s.contains(mouse) || (clearOfNeighbours && covered(mouse.x, mouse.y, neighbours))) {
            moveOnNextHover = false;
            return moveLeading(hull, clearOfNeighbours ? avoid : panels);
        }
        slideVx = slideVy = 0; // no move this time: an earlier target's slide must not hold the press
        sampledAt = 0;
        return true;
    }

    private boolean moveLeading(Supplier<? extends Shape> hull, List<? extends Shape> panels) {
        waitForCameraToSettle();
        Shape s = hull.get();
        if (s == null || !insideViewport(s.getBounds())) return false;
        Rectangle before = s.getBounds();
        long t0 = System.currentTimeMillis();
        // Over 3-4 frames: two frames quantise to a pixel.
        if (Rs2Player.isMoving()) sleep(Rs2Random.betweenInclusive(60, 90));
        Shape now = hull.get();
        if (now == null || !insideViewport(now.getBounds())) return false;
        long dt = System.currentTimeMillis() - t0;
        java.awt.Point in = pointInside(now, panels);
        if (in == null) return false;
        // Lead the centre: a growing box's corner moves unlike its middle. No lead standing still: an animating boar reads as a slide.
        // By the longest recent move (NaturalMouse durations vary), so the cursor lands just ahead and the target slides on.
        long leadMs = Math.min(leadHorizonMs(recentMoves, recentMoveCount, travelMs) + PRESS_LEAD_MS, msUntilStop());
        boolean led = Rs2Player.isMoving() && dt >= 40;
        java.awt.Point aim = led ? lead(in, centre(before), centre(now.getBounds()), dt, leadMs) : in;
        slideVx = led ? (now.getBounds().getCenterX() - before.getCenterX()) / dt : 0;
        slideVy = led ? (now.getBounds().getCenterY() - before.getCenterY()) / dt : 0;
        if (covered(aim.x, aim.y, panels) || !insideViewport(new Rectangle(aim.x, aim.y, 1, 1))) aim = in;
        long moveStart = System.currentTimeMillis();
        Microbot.getNaturalMouse().moveTo(aim.x, aim.y);
        movesThisClick++;
        lastMoveEnd = System.currentTimeMillis();
        lastLeadMs = led ? leadMs : 0;
        lastMoveMs = lastMoveEnd - moveStart;
        sampledAt = led ? moveStart : 0;
        sampledCx = now.getBounds().getCenterX();
        sampledCy = now.getBounds().getCenterY();
        // A move past this is a stall (scene load, the user's own mouse), not mouse speed; real moves take 150-340ms.
        if (lastMoveMs <= MOVE_SAMPLE_MAX_MS) {
            travelMs = (travelMs * 3 + lastMoveMs) / 4;
            recentMoves[recentMoveCount++ % recentMoves.length] = lastMoveMs;
        }
        return true;
    }

    static final long MOVE_SAMPLE_MAX_MS = 600;

    private int movesThisClick;
    private long lastLeadMs, lastMoveMs;
    private final long[] recentMoves = new long[8];
    private int recentMoveCount;
    private Boolean savedMoveMouseRandomly;
    private boolean savedMoveMouseOffScreen, savedSimulateMistakes, savedTakeMicroBreaks;
    private volatile boolean stopping;
    private double slideVx, slideVy;
    private long sampledAt;
    private double sampledCx, sampledCy;

    private double[] seenSlide(Rectangle nowBox) {
        long dt = System.currentTimeMillis() - sampledAt;
        if (sampledAt == 0 || nowBox == null || dt <= 0) return new double[]{0, 0};
        return new double[]{(nowBox.getCenterX() - sampledCx) / dt, (nowBox.getCenterY() - sampledCy) / dt};
    }

    static long leadHorizonMs(long[] recent, int count, long fallback) {
        int n = Math.min(count, recent.length);
        long max = 0;
        for (int i = 0; i < n; i++) max = Math.max(max, recent[i]);
        return n == 0 ? fallback : Math.min(max, 2 * fallback); // one outlier must not over-lead the next 8 moves
    }

    static long followWaitMs(long leadMs, long moveMs) {
        return Math.max(0, leadMs - moveMs) + 120;
    }

    static boolean slidPast(Rectangle target, java.awt.Point cursor, double vx, double vy) {
        if (target == null || cursor == null || vx * vx + vy * vy < 1e-4) return false;
        return (cursor.x - target.getCenterX()) * vx + (cursor.y - target.getCenterY()) * vy < 0;
    }

    static long msUntilUnderCursor(Rectangle target, java.awt.Point cursor, double vx, double vy, long maxWaitMs) {
        double v2 = vx * vx + vy * vy;
        if (target == null || cursor == null || v2 < 1e-4) return -1;
        double rx = cursor.x - target.getCenterX(), ry = cursor.y - target.getCenterY();
        double t = (rx * vx + ry * vy) / v2;
        if (t <= 0 || t > maxWaitMs) return -1;
        double off = Math.hypot(rx - vx * t, ry - vy * t);
        return off <= halfWidthAcross(target, vx, vy) ? Math.round(t) : -1;
    }

    /** Half the width across the slide, not half the shorter side, which refuses a diagonal slide too early. Pure. */
    static double halfWidthAcross(Rectangle target, double vx, double vy) {
        double v = Math.hypot(vx, vy);
        return (target.width * Math.abs(vy) + target.height * Math.abs(vx)) / v / 2.0;
    }

    /** Running covers 2 tiles a tick (256 local units / 600ms), walking 1. */
    private static final double RUN_UNITS_PER_MS = 256 / 600.0;

    private static long msUntilStop() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            LocalPoint me = c.getLocalPlayer() == null ? null : c.getLocalPlayer().getLocalLocation();
            LocalPoint dest = c.getLocalDestinationLocation();
            // The flag clears before the glide onto the last tile ends: the scene still slides, so end on the server tile.
            if (dest == null && me != null) {
                dest = LocalPoint.fromWorld(c.getTopLevelWorldView(), c.getLocalPlayer().getWorldLocation());
            }
            return dest == null || me == null ? 0L : msToCover(me.distanceTo(dest), Rs2Player.isRunEnabled());
        }).orElse(0L);
    }

    static long msToCover(int localUnits, boolean running) {
        return Math.round(localUnits / (running ? RUN_UNITS_PER_MS : RUN_UNITS_PER_MS / 2));
    }

    private long lastMoveEnd;
    private long nextLoginAt;
    private long seenLoginScreenAt;
    private boolean everLoggedIn;

    private void waitForCameraToSettle() {
        sleepUntil(() -> !cameraEasing(), 600);
    }

    /** Whole-degree yaw reads equal mid-glide, so presses hit Walk here. */
    private boolean cameraEasing() {
        // Raw yaw (16384 a turn) moves every frame of a glide; getCameraYawTarget equals getCameraYaw, so it never shows a turn.
        // Yaw only: a slow pitch drift blocks presses with the target still on screen.
        long now = System.currentTimeMillis();
        int before;
        if (lastYawRaw != Integer.MIN_VALUE && now - lastYawAt >= 15 && now - lastYawAt <= 80) {
            before = lastYawRaw;
        } else {
            before = rawYaw();
            sleep(20);
        }
        int y = rawYaw();
        lastYawRaw = y;
        lastYawAt = System.currentTimeMillis();
        return angleUnitsApart(y, before) > CAMERA_STILL_UNITS;
    }

    private int lastYawRaw = Integer.MIN_VALUE;
    private long lastYawAt;

    private static int rawYaw() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getCameraYaw()).orElse(0);
    }

    static int angleUnitsApart(int a, int b) {
        int d = Math.floorMod(a - b, 16384);
        return Math.min(d, 16384 - d);
    }

    /** Raw yaw change per frame below which the camera is still (~0.2 deg). */
    private static final int CAMERA_STILL_UNITS = 8;

    private long travelMs = 250;

    /** A jump faster than any scroll (camera snap, re-render) is not led. Pure. */
    static java.awt.Point lead(java.awt.Point aim, java.awt.Point before, java.awt.Point after, long dtMs, long travelMs) {
        if (dtMs <= 0) return aim;
        double vx = (after.x - before.x) / (double) dtMs, vy = (after.y - before.y) / (double) dtMs;
        if (Math.hypot(vx, vy) > MAX_SCROLL_PX_PER_MS) return aim;
        return new java.awt.Point(aim.x + (int) Math.round(vx * travelMs), aim.y + (int) Math.round(vy * travelMs));
    }

    /** Running scrolls the scene at roughly 0.1-0.2 px/ms; above this it is not a scroll. */
    private static final double MAX_SCROLL_PX_PER_MS = 1.0;
    private static final int PRESS_LEAD_MS = 60;

    private static java.awt.Point centre(Rectangle r) {
        return new java.awt.Point((int) Math.round(r.getCenterX()), (int) Math.round(r.getCenterY()));
    }


    /** The box's empty corners are "Walk here". */
    static java.awt.Point pointInside(Shape s, List<? extends Shape> panels) {
        Rectangle r = s.getBounds();
        // The middle half first: a margin for the slide, and the outline's thin edges pick Walk here.
        {
            Rectangle mid = new Rectangle(r.x + r.width / 4, r.y + r.height / 4, Math.max(1, r.width / 2), Math.max(1, r.height / 2));
            for (int i = 0; i < 16; i++) {
                Point p = Rs2UiHelper.getClickingPoint(mid, true);
                if (s.contains(p.getX(), p.getY()) && !covered(p.getX(), p.getY(), panels)) {
                    return new java.awt.Point(p.getX(), p.getY());
                }
            }
        }
        for (int i = 0; i < 24; i++) {
            Point p = Rs2UiHelper.getClickingPoint(r, true);
            if (s.contains(p.getX(), p.getY()) && !covered(p.getX(), p.getY(), panels)) {
                return new java.awt.Point(p.getX(), p.getY());
            }
        }
        List<java.awt.Point> clear = new ArrayList<>();
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                if (s.contains(x, y) && !covered(x, y, panels)) clear.add(new java.awt.Point(x, y));
            }
        }
        return clear.isEmpty() ? null : clear.get(Rs2Random.betweenInclusive(0, clear.size() - 1));
    }

    /** The game picks its model right up to the outline. */
    static final int BLOCKER_MARGIN_PX = 3;

    static Shape blockerZone(Shape blocker) {
        java.awt.geom.Area zone = new java.awt.geom.Area(blocker);
        zone.add(new java.awt.geom.Area(new java.awt.BasicStroke(2f * BLOCKER_MARGIN_PX).createStrokedShape(blocker)));
        return zone;
    }

    private boolean clickable(Shape s) {
        return s != null && insideViewport(s.getBounds()) && pointInside(s, panels()) != null;
    }

    static boolean covered(int x, int y, List<? extends Shape> panels) {
        return panels.stream().anyMatch(p -> p.contains(x, y));
    }

    private List<Rectangle> panels() {
        List<Rectangle> found = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            List<Rectangle> out = new ArrayList<>();
            Widget[] roots = c.getWidgetRoots();
            if (roots != null) {
                for (Widget w : roots) collectPanels(w, c.getViewportWidth(), c.getViewportHeight(), out);
            }
            out.addAll(outsideView(c.getViewportXOffset(), c.getViewportYOffset(), c.getViewportWidth(), c.getViewportHeight()));
            return out;
        }).orElse(Collections.<Rectangle>emptyList());
        return found;
    }

    static List<Rectangle> outsideView(int vx, int vy, int vw, int vh) {
        int far = 10_000;
        return List.of(new Rectangle(vx - far, vy - far, vw + 2 * far, far), new Rectangle(vx - far, vy + vh, vw + 2 * far, far),
                new Rectangle(vx - far, vy, far, vh), new Rectangle(vx + vw, vy, far, vh));
    }

    private static void collectPanels(Widget w, int vw, int vh, List<Rectangle> out) {
        if (w == null || w.isHidden()) return;
        Rectangle b = w.getBounds();
        // Anything about the size of the game view is a container: look inside it.
        boolean container = b == null || (b.width >= vw * 0.9 && b.height >= vh * 0.9);
        boolean takesClick = w.getNoClickThrough()
                || (w.getActions() != null && Arrays.stream(w.getActions()).anyMatch(a -> a != null && !a.isEmpty()));
        if (!container && takesClick && b.width > 0 && b.height > 0) {
            // An outline's last 1-2px beside a panel reads Walk here.
            Rectangle grown = new Rectangle(b);
            grown.grow(BLOCKER_MARGIN_PX, BLOCKER_MARGIN_PX);
            out.add(grown);
            return;
        }
        for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()}) {
            if (kids != null) for (Widget k : kids) collectPanels(k, vw, vh, out);
        }
    }

    private List<Shape> overlappingTrailHulls(TileObject target) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Shape t = target.getClickbox();
            List<Shape> out = new ArrayList<>();
            if (t == null) return out;
            for (TileObject o : plugin.getTrailObjects().values()) {
                if (o == null || o == target) continue;
                Shape h = o.getClickbox();
                if (h != null && h.getBounds().intersects(t.getBounds())) out.add(h);
            }
            return out;
        }).orElse(Collections.emptyList());
    }

    private static Shape hullOf(TileObject object) {
        return Microbot.getClientThread().runOnClientThreadOptional(object::getClickbox).orElse(null);
    }

    /** Rs2UiHelper.getActorClickbox only gives its bounding box. */
    private static Shape hullOf(Rs2NpcModel npc) {
        return npc == null ? null : Microbot.getClientThread().runOnClientThreadOptional(npc::getConvexHull).orElse(null);
    }

    /** Held dead still, the press lands on the edge of a sliding target, where the game reads Walk here. */
    private void driftToward(Rectangle box, java.awt.Point cursor) {
        double dx = box.getCenterX() - cursor.x, dy = box.getCenterY() - cursor.y;
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        // Uneven steps with a little sideways wobble: a hand never creeps in a perfectly straight line.
        double step = Math.min(len, 1 + Rs2Random.betweenInclusive(0, 20) / 10.0);
        double side = Rs2Random.randomGaussian(0, 0.6);
        int x = (int) Math.round(cursor.x + (dx * step - dy * side) / len);
        int y = (int) Math.round(cursor.y + (dy * step + dx * side) / len);
        if (covered(x, y, panels())) return;
        Microbot.getMouse().move(new Point(x, y));
    }

    /** Under a panel: turn the camera. Under something in the world: re-aim, turn, walk closer, never a right-click. */
    private boolean clickUnderCursor(String action, int id, Supplier<? extends Shape> hull, Runnable forcedClick,
                                     Object what, IntSupplier cameraAngle) {
        String target = String.valueOf(what);
        if (!target.equals(cursorMissTarget)) {
            // Per target, not per object id: two spots of the same kind share an id.
            cursorMissTarget = target;
            cursorMisses = 0;
            coverTries = 0;
            movesThisClick = Math.min(movesThisClick, 1);
        }
        // Running: no settle. The move aimed at where the target will be, so any wait lets it slide off.
        if (!Rs2Player.isMoving()) {
            long settleLeft = Rs2Random.betweenInclusive(70, 110) - (System.currentTimeMillis() - lastMoveEnd);
            if (settleLeft > 0) sleep((int) settleLeft);
        }
        // The game's top entry is built from what is under the cursor that frame.
        // Pinned (Microbot.targetMenu) so a slide cannot turn it into Walk here.
        int[] tile = what instanceof WorldPoint ? Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            LocalPoint lp = LocalPoint.fromWorld(c.getTopLevelWorldView(), (WorldPoint) what);
            return lp == null ? new int[0] : new int[]{lp.getSceneX(), lp.getSceneY()};
        }).orElse(new int[0]) : new int[0];
        java.util.function.Predicate<MenuEntry> ours = e -> e != null
                && leftClickDoes(e.getOption(), e.getIdentifier(), action, id)
                && onOurTile(e.getType(), e.getParam0(), e.getParam1(), tile);
        MenuEntry top = topMenuEntry();
        boolean followed = false;
        long watchStart = System.currentTimeMillis();
        long slideCap = watchStart + 1200;
        long watchUntil = watchStart + Rs2Random.betweenInclusive(380, 520);
        while (!ours.test(top) && System.currentTimeMillis() < watchUntil) {
            sleep(Rs2Random.betweenInclusive(10, 16)); // under a client frame: catch the first frame it is ours
            Shape s = hull.get();
            java.awt.Point cursorAt = Microbot.getMouse().getMousePosition();
            if (!followed && s != null && !s.contains(cursorAt) && insideViewport(s.getBounds())) {
                long eta = Rs2Player.isMoving() ? msUntilUnderCursor(s.getBounds(), cursorAt, slideVx, slideVy, 600) : -1;
                if (eta >= 0) {
                    driftToward(s.getBounds(), cursorAt);
                    watchUntil = Math.max(watchUntil, Math.min(System.currentTimeMillis() + eta + 120, slideCap));
                    top = topMenuEntry();
                    continue;
                }
                followed = true;
                Rectangle fb = s.getBounds();
                double fdx = cursorAt.x - fb.getCenterX(), fdy = cursorAt.y - fb.getCenterY();
                double fv = Math.hypot(slideVx, slideVy);
                double[] seen = seenSlide(fb);
                double seenVx = seen[0], seenVy = seen[1];
                hbLog.detail("FOLLOW", String.format(java.util.Locale.ENGLISH, "%s: cursor %+.0f,%+.0f from its centre, box %dx%d, slide %.2f,%.2f (seen over the move %.2f,%.2f), %s",
                        action, fdx, fdy, fb.width, fb.height, slideVx, slideVy, seenVx, seenVy,
                        !Rs2Player.isMoving() || fv < 1e-2 ? "not sliding"
                                : (fdx * slideVx + fdy * slideVy) <= 0 ? "behind it"
                                : String.format(java.util.Locale.ENGLISH, "ahead, %.0fpx off its line (tolerance %.0f)",
                                        Math.abs(fdx * slideVy - fdy * slideVx) / fv, Math.min(fb.width, fb.height) / 2.0)));
                moveLeading(hull, panels());
                long followWait = habits.impatientPress(paceRng) ? 120 : followWaitMs(lastLeadMs, lastMoveMs);
                watchUntil = Math.max(watchUntil, Math.min(System.currentTimeMillis() + followWait, slideCap));
            } else if (followed && s != null && !s.contains(cursorAt) && Rs2Player.isMoving()
                    && slidPast(s.getBounds(), cursorAt, slideVx, slideVy)) {
                break;
            }
            top = topMenuEntry();
        }
        String miss;
        boolean clicked = false;
        if (top instanceof NewMenuEntry && ours.test(top)) {
            clicked = true;
            int clicks = plugin.getClickCount();
            // The canvas position is (-1,-1) once the cursor leaves the window.
            java.awt.Point at = Microbot.getMouse().getMousePosition();
            Microbot.getMouse().click(new Point(at.x, at.y), (NewMenuEntry) top);
            // Running, the target can slide off between the read and the press.
            if (sleepUntil(() -> plugin.getClickCount() != clicks, 600)
                    && leftClickDoes(plugin.getLastClickedOption(), plugin.getLastClickedId(), action, id)) {
                if (movesThisClick >= 2) {
                    hbLog.detail("AIM", String.format("%s on %s took %d moves (cursor %d,%d, last lead %dms, move took %dms)%s",
                            action, what, movesThisClick, at.x, at.y, lastLeadMs, lastMoveMs,
                            Rs2Player.isMoving() ? ", running" : ""));
                }
                movesThisClick = 0;
                cursorMisses = 0;
                coverTries = 0;
                return true;
            }
            miss = "the click did " + plugin.getLastClickedOption();
        } else {
            miss = "under the cursor is " + (top == null ? "nothing" : Rs2UiHelper.stripTags(top.getOption() + " " + top.getTarget()));
        }
        Shape s = hull.get();
        miss += s == null ? " (target has no outline)"
                : s.contains(Microbot.getMouse().getMousePosition()) ? " (cursor inside its outline)" : " (cursor off its outline)";
        miss += Rs2Player.isMoving() ? ", running" : "";
        java.awt.Point cur = Microbot.getMouse().getMousePosition();
        Rectangle b = s == null ? null : s.getBounds();
        double[] seen = seenSlide(b);
        double seenVx = seen[0], seenVy = seen[1];
        miss += String.format(java.util.Locale.ENGLISH,
                " [zoom %d, box %s, cursor %d,%d, slide %.2f,%.2f px/ms (seen %.2f,%.2f), moves %d, lead %dms, move took %dms]",
                Rs2Camera.getZoom(), b == null ? "-" : b.width + "x" + b.height + "@" + b.x + "," + b.y,
                cur.x, cur.y, slideVx, slideVy, seenVx, seenVy, movesThisClick, lastLeadMs, lastMoveMs);
        if (!clicked && ours.test(top)) {
            hbLog.detail("MISS", miss + ": " + action + " is under the cursor; clicking once it settles");
            return false;
        }
        if (top != null && uiCovers(top.getType()) && s != null && s.contains(Microbot.getMouse().getMousePosition())
                && nudgeCameraToward(cameraAngle.getAsInt())) {
            hbLog.detail("MISS", miss + ": " + what + " is behind a panel; turning the camera slightly");
            moveOnNextHover = true;
            return false;
        }
        // Only another object/NPC on top is "in front"; Walk here means the cursor simply missed.
        // NPC indexes are unique; object ids are not.
        if (top != null && top.getType() == MenuAction.EXAMINE_NPC && top.getIdentifier() == id) {
            hbLog.detail("MISS", miss + ": " + what + " is not offering " + action + " yet");
            return false;
        }
        if (top != null && !uiCovers(top.getType()) && top.getType() != MenuAction.WALK
                && !"Walk here".equalsIgnoreCase(top.getOption()) && s != null && s.contains(Microbot.getMouse().getMousePosition())) {
            String blocked = miss + ": " + Rs2UiHelper.stripTags(top.getTarget()) + " is in front of " + what;
            knownBlockers.put(target, top);
            Shape blocker = blockerShape(top);
            if (coverTries == 0) {
                coverTries++;
                List<Shape> avoid = new ArrayList<>(panels());
                if (blocker != null) avoid.add(blockerZone(blocker));
                if (s != null && pointInside(s, avoid) != null) {
                    hbLog.detail("COVER", blocked + "; aiming at the part it leaves showing");
                    moveLeading(hull, avoid);
                    return false;
                }
            }
            WorldPoint spot = what instanceof WorldPoint ? (WorldPoint) what : null;
            WorldPoint me = Rs2Player.getWorldLocation();
            if (coverTries == 1) {
                coverTries++;
                if (!target.equals(walkedCloserFor) && spot != null && me != null && me.distanceTo(spot) > 2) {
                    walkedCloserFor = target;
                    hbLog.detail("COVER", blocked + "; walking closer for another angle");
                    dropWalkerRoute();
                    if (Rs2Walker.walkMiniMap(spot)) sleepUntil(() -> spot.distanceTo(Rs2Player.getWorldLocation()) <= 2, 4000);
                    moveOnNextHover = true;
                    return false;
                }
            }
            hbLog.detail("COVER", blocked + (coverTries == 2 ? "; turning the camera so it stands clear" : "; turning the camera the other way"));
            peekAround(coverTries == 2 ? 1 : -2);
            coverTries = coverTries == 2 ? 3 : 2;
            // Falls through: counted as a miss, so a blocker that never clears ends in the forced click, not endless turns.
        }
        // Never stop to wait for the character: a stop-and-retry is a visible jerk.
        ++cursorMisses;
        WorldPoint missSpot = what instanceof WorldPoint ? (WorldPoint) what : null;
        WorldPoint meNow = Rs2Player.getWorldLocation();
        if (cursorMisses >= 4 && top != null && "Walk here".equalsIgnoreCase(top.getOption()) && s != null
                && s.contains(Microbot.getMouse().getMousePosition()) && missSpot != null && meNow != null
                && meNow.distanceTo(missSpot) > 3) {
            // On its outline yet the game offers Walk here: not clickable from here (not drawn, too far).
            hbLog.detail("MISS", miss + ", not " + action + " on " + what + ", 4 times; walking closer instead");
            cursorMisses = 0;
            dropWalkerRoute();
            if (Rs2Walker.walkMiniMap(missSpot)) sleepUntil(() -> missSpot.distanceTo(Rs2Player.getWorldLocation()) <= 3, 6000);
            moveOnNextHover = true;
            return false;
        }
        if (cursorMisses >= 4) {
            hbLog.event("RECOVER", miss + ", not " + action + " on " + what + ", 4 times; sending " + action + " anyway");
            cursorMisses = 0;
            coverTries = 0;
            forcedClick.run();
            return true;
        }
        hbLog.detail("MISS", miss + ", not " + action + " on " + what + " (miss " + cursorMisses + ")");
        hbLog.miss();
        moveOnNextHover = s != null && s.contains(Microbot.getMouse().getMousePosition());
        return false;
    }

    private MenuEntry topMenuEntry() {
        return plugin.getTopEntry();
    }

    private boolean nudgeCameraToward(int targetDegrees) {
        return nudgeCameraToward(targetDegrees, () -> false);
    }

    private void peekAround(int steps) {
        if (peekSide == 0 || steps > 0) peekSide = Rs2Random.dicePercentage(50) ? 1 : -1;
        int by = Rs2Random.betweenInclusive(35, 60) * steps * peekSide;
        turnCameraTo(Math.floorMod(Rs2Camera.getAngle() + by, 360), 6, () -> false);
    }

    private int peekSide;
    private String walkedCloserFor;
    private final Map<String, MenuEntry> knownBlockers = new ConcurrentHashMap<>();

    private List<Shape> withKnownBlocker(Object what, List<Shape> neighbours) {
        // Remembered most visits, not every one: perfect memory is its own pattern.
        if (!habits.recallsBlocker(paceRng)) return neighbours;
        Shape b = blockerShape(knownBlockers.get(String.valueOf(what)));
        if (b == null) return neighbours;
        List<Shape> out = new ArrayList<>(neighbours);
        out.add(blockerZone(b));
        return out;
    }

    /** Objects count too: a Smelly mushroom stands in front of a tunnel. */
    private static Shape blockerShape(MenuEntry top) {
        if (top == null || top.getType() == null) return null;
        MenuAction t = top.getType();
        boolean npc = t == MenuAction.NPC_FIRST_OPTION || t == MenuAction.NPC_SECOND_OPTION || t == MenuAction.NPC_THIRD_OPTION
                || t == MenuAction.NPC_FOURTH_OPTION || t == MenuAction.NPC_FIFTH_OPTION || t == MenuAction.EXAMINE_NPC;
        boolean object = t == MenuAction.GAME_OBJECT_FIRST_OPTION || t == MenuAction.GAME_OBJECT_SECOND_OPTION
                || t == MenuAction.GAME_OBJECT_THIRD_OPTION || t == MenuAction.GAME_OBJECT_FOURTH_OPTION
                || t == MenuAction.GAME_OBJECT_FIFTH_OPTION || t == MenuAction.EXAMINE_OBJECT;
        if (!npc && !object) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var wv = Microbot.getClient().getTopLevelWorldView();
            if (npc) {
                NPC n = wv.npcs().byIndex(top.getIdentifier());
                return n == null ? null : n.getConvexHull();
            }
            int x = top.getParam0(), y = top.getParam1();
            Tile[][][] tiles = wv.getScene().getTiles();
            if (x < 0 || y < 0 || x >= tiles[wv.getPlane()].length || y >= tiles[wv.getPlane()][x].length) return null;
            Tile tile = tiles[wv.getPlane()][x][y];
            if (tile == null) return null;
            List<TileObject> on = new ArrayList<>();
            for (GameObject g : tile.getGameObjects()) if (g != null) on.add(g);
            on.add(tile.getGroundObject());
            on.add(tile.getDecorativeObject());
            on.add(tile.getWallObject());
            for (TileObject o : on) {
                if (o != null && o.getId() == top.getIdentifier() && o.getClickbox() != null) return o.getClickbox();
            }
            return null;
        }).orElse(null);
    }

    private boolean nudgeCameraToward(int targetDegrees, BooleanSupplier clear) {
        int delta = Math.abs(Rs2Camera.getAngleTo(targetDegrees));
        if (delta < 8) return false;
        turnCameraTo(targetDegrees, delta * Rs2Random.betweenInclusive(15, 35) / 100, clear);
        return true;
    }

    static boolean uiCovers(MenuAction type) {
        return type == MenuAction.CANCEL || type == MenuAction.CC_OP || type == MenuAction.CC_OP_LOW_PRIORITY
                || type.name().startsWith("WIDGET_");
    }

    static boolean leftClickDoes(String topOption, int topId, String action, int id) {
        return topOption != null && topOption.equalsIgnoreCase(action) && topId == id;
    }

    /** Give or take 2 for wide objects; every tunnel shares one id. Pure. */
    static boolean onOurTile(MenuAction type, int sceneX, int sceneY, int[] tile) {
        if (tile.length < 2 || type == null || !type.name().startsWith("GAME_OBJECT_")) return true;
        return Math.abs(sceneX - tile[0]) <= 2 && Math.abs(sceneY - tile[1]) <= 2;
    }

    /** Time, not passes: the poll is fast. */
    private long hiddenSince;
    private boolean pitchedForHidden;

    private boolean clickWhenVisible(int cameraAngle, Supplier<? extends Shape> hull, WorldPoint what) {
        if (clickable(hull.get())) {
            hiddenSince = 0;
            return true;
        }
        WorldPoint me = Rs2Player.getWorldLocation();
        if (what != null && me != null && me.distanceTo(what) > CAMERA_TURN_MAX_TILES
                && minimapStepToward(what, () -> clickable(hull.get()))) {
            hiddenSince = 0;
            idle("walking toward " + what + " until it is on screen");
            return clickable(hull.get());
        }
        if (bringIntoView(cameraAngle, hull)) {
            hiddenSince = 0;
            return true;
        }
        long now = System.currentTimeMillis();
        if (hiddenSince == 0) {
            hiddenSince = now;
            pitchedForHidden = false;
        }
        if (!pitchedForHidden && now - hiddenSince > 1000) {
            pitchedForHidden = true;
            Rs2Camera.setPitch(Rs2Random.betweenInclusive(360, 383));
        }
        if (now - hiddenSince > 2000) {
            hbLog.detail("MISS", what + " never came into view; clicking it anyway");
            hiddenSince = 0;
            return true;
        }
        idle("bringing " + what + " into view");
        return false;
    }

    private boolean bringIntoView(int cameraAngle, Supplier<? extends Shape> hull) {
        if (clickable(hull.get())) return true;
        // At the edge of the view it slides fastest.
        turnCameraTo(cameraAngle, () -> comfortablyInView(hull.get()));
        return clickable(hull.get());
    }

    private static final int CAMERA_TURN_MAX_TILES = 2;

    /** Further out an object projects an outline but is not drawn, so clicks read "Walk here". */
    private static final int CLICK_MAX_TILES = 28;
    /** Beyond this a faced spot is not reliably drawn: a game limit, not a habit. */
    static final int TURN_SHOWS_MAX_TILES = 24;

    static boolean smallTurnShows(int distance, boolean drawn, int degreesOff, int maxDeg, int clickRange) {
        // Already faced (under 5 deg), a turn shows nothing more: it is off the top or bottom.
        return drawn && distance > CAMERA_TURN_MAX_TILES && distance <= clickRange
                && Math.abs(degreesOff) >= 5 && Math.abs(degreesOff) < maxDeg;
    }

    /**
     * A spot in click range but off screen is behind or beside the camera. Walking to it first costs about 3.5 s
     * more than clicking it; a watching player turns to look. Pure.
     */
    static boolean turnsToFollow(boolean onScreen, int distance, int clickRange, double chance, double roll) {
        return !onScreen && distance > CAMERA_TURN_MAX_TILES && distance <= clickRange && roll < chance;
    }

    static boolean walkFirst(boolean onScreen, int distance) {
        return walkFirst(onScreen, distance, CLICK_MAX_TILES);
    }

    static boolean walkFirst(boolean onScreen, int distance, int clickRange) {
        return (!onScreen || distance > clickRange) && distance > CAMERA_TURN_MAX_TILES;
    }

    private boolean comfortablyInView(Shape s) {
        if (!clickable(s)) return false;
        Rectangle b = s.getBounds();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            int mx = c.getViewportWidth() * 15 / 100, my = c.getViewportHeight() * 15 / 100;
            return new Rectangle(c.getViewportXOffset() + mx, c.getViewportYOffset() + my,
                    c.getViewportWidth() - 2 * mx, c.getViewportHeight() - 2 * my).contains(b.getCenterX(), b.getCenterY());
        }).orElse(false);
    }

    static boolean shouldUseHole(WorldPoint from, WorldPoint to) {
        return crossesHole(from, to)
                && from.distanceTo(holeMouthNearest(from)) <= HOLE_PREFER_DISTANCE;
    }

    /** The hole is the only multi-tile hop across the divide. Pure. */
    static boolean pathJumpsHole(List<WorldPoint> path) {
        if (path == null) return false;
        for (int i = 0; i + 1 < path.size(); i++) {
            WorldPoint a = path.get(i), b = path.get(i + 1);
            if (crossesHole(a, b) && a.distanceTo(b) > 2) return true;
        }
        return false;
    }

    private WorldPoint holeDecisionTarget;
    private boolean holeDecision;
    private int holeFails;

    private boolean useHole(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || !crossesHole(from, to)) return false;
        if (Rs2Player.getRealSkillLevel(Skill.AGILITY) < HOLE_SHORTCUT_AGILITY) return false;
        if (!to.equals(holeDecisionTarget)) {
            List<WorldPoint> path = Rs2Walker.getWalkPath(from, to);
            holeDecision = path.isEmpty() ? shouldUseHole(from, to) : pathJumpsHole(path);
            holeDecisionTarget = to;
            holeFails = 0;
            hbLog.detail("START", (holeDecision ? "Using the hole" : "Walking round, not the hole")
                    + " to " + to + " (route " + path.size() + " tiles)");
        }
        return holeDecision;
    }

    static WorldPoint holeMouthNearest(WorldPoint from) {
        return from != null && from.getY() < HOLE_DIVIDE_Y ? HOLE_MOUTH_SOUTH : HOLE_MOUTH_NORTH;
    }

    private static String trailProgress(HerbiHunterPlugin plugin) {
        return plugin.getCurrentPath().size() + ":" + plugin.getFinishId();
    }

    /** The boar always comes out on the same tile per tunnel; unlisted tunnels are learned at the first harvest. */
    static final Map<WorldPoint, WorldPoint> SEEDED_BOAR_TILES = Map.of(
            new WorldPoint(3693, 3798, 0), new WorldPoint(3696, 3796, 0),
            new WorldPoint(3751, 3849, 0), new WorldPoint(3753, 3851, 0),
            new WorldPoint(3710, 3881, 0), new WorldPoint(3710, 3883, 0),
            new WorldPoint(3705, 3814, 0), new WorldPoint(3707, 3801, 0),
            new WorldPoint(3681, 3863, 0), new WorldPoint(3681, 3865, 0),
            new WorldPoint(3685, 3869, 0), new WorldPoint(3683, 3870, 0),
            new WorldPoint(3700, 3777, 0), new WorldPoint(3665, 3784, 0),
            new WorldPoint(3702, 3808, 0), new WorldPoint(3704, 3808, 0),
            new WorldPoint(3703, 3826, 0), new WorldPoint(3705, 3827, 0));
    private final Map<WorldPoint, WorldPoint> boarTiles = new ConcurrentHashMap<>(SEEDED_BOAR_TILES);

    /** Height above its tile to aim at the boar's body, in local units. */
    private static final int BOAR_BODY_HEIGHT = 50;
    private static final int PRE_HOVER_NEAR_PX = 8;
    private java.awt.Point preHoverAim;

    private boolean preHoverBoarSpot(WorldPoint tunnel) {
        WorldPoint tile = tunnel == null ? null : boarTiles.get(tunnel);
        WorldPoint me = Rs2Player.getWorldLocation();
        // Aimed earlier (3-6 tiles out, while running), the camera follows the player and the cursor ends up outside
        // the boar, so the Harvest click comes later. Only aim once standing at the tunnel.
        String why = tile == null ? "tile unknown" : me == null ? "no player" : me.distanceTo(tunnel) > 2 ? "not at the tunnel yet"
                : Rs2Player.isMoving() ? "moving" : herbiboarAt(tunnel) != null ? "boar already out" : null;
        if (why != null) {
            preHoverNote(tunnel, why);
            return false;
        }
        java.awt.Point at = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            LocalPoint lp = LocalPoint.fromWorld(c.getTopLevelWorldView(), tile);
            Point p = lp == null ? null : Perspective.localToCanvas(c, lp, tile.getPlane(), BOAR_BODY_HEIGHT);
            return p == null ? null : new java.awt.Point(p.getX(), p.getY());
        }).orElse(null);
        if (at == null || !insideViewport(new Rectangle(at.x, at.y, 1, 1)) || covered(at.x, at.y, panels())) {
            preHoverNote(tunnel, "spot " + (at == null ? "not projected" : at.x + "," + at.y + " not clickable") + " (tile " + tile + ")");
            return false;
        }
        if (Microbot.getMouse().getMousePosition().distance(at) <= PRE_HOVER_NEAR_PX) return true;
        int x = at.x + Rs2Random.betweenInclusive(-3, 3), y = at.y + Rs2Random.betweenInclusive(-3, 3);
        Microbot.getNaturalMouse().moveTo(x, y);
        lastMoveEnd = System.currentTimeMillis();
        preHoverAim = new java.awt.Point(x, y);
        return true;
    }

    enum BoarWait { PRE_HOVER, MOUSE_AWAY, NOTHING }

    static BoarWait boarWaitChoice(boolean relaxed, double roll, double preHoverChance) {
        if (!relaxed || roll < preHoverChance) return BoarWait.PRE_HOVER;
        return roll < preHoverChance + 0.15 ? BoarWait.MOUSE_AWAY : BoarWait.NOTHING;
    }

    private BoarWait boarWait;
    private boolean glanced;

    private void glanceAway() {
        if (glanced) return;
        glanced = true;
        idle("glancing away");
        int[] v = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var c = Microbot.getClient();
            return new int[]{c.getViewportXOffset() + c.getViewportWidth(), c.getViewportYOffset(), c.getViewportHeight()};
        }).orElse(null);
        if (v == null) return;
        Microbot.getNaturalMouse().moveTo(v[0] + Rs2Random.betweenInclusive(3, 8), v[1] + v[2] / 4 + Rs2Random.betweenInclusive(0, v[2] / 2));
        lastMoveEnd = System.currentTimeMillis();
    }

    private String lastPreHoverNote;

    private void preHoverNote(WorldPoint tunnel, String why) {
        String note = tunnel + ": " + why;
        if (note.equals(lastPreHoverNote)) return;
        lastPreHoverNote = note;
        hbLog.detail("HOVER", "Boar spot not hovered at " + note);
    }

    /** The emerging boar never offers Harvest. */
    private Rs2NpcModel harvestableBoarAt(WorldPoint tunnel) {
        if (tunnel == null) return null;
        WorldPoint at = boarSearchCentre(tunnel);
        return Microbot.getRs2NpcCache().query().withName("Herbiboar").where(HerbiboarScript::canHarvest)
                .nearestOnClientThread(at, boarSearchRadius(tunnel, at));
    }

    private Rs2NpcModel herbiboarAt(WorldPoint tunnel) {
        if (tunnel == null) return null;
        WorldPoint at = boarSearchCentre(tunnel);
        return Microbot.getRs2NpcCache().query().withName("Herbiboar")
                .nearestOnClientThread(at, boarSearchRadius(tunnel, at));
    }

    /**
     * Where to look for our boar: its known tile when the tunnel has one, which keeps another player's boar at the
     * next tunnel (4 tiles off at 3681,3863) out; otherwise around the tunnel until the first harvest teaches the tile.
     */
    private WorldPoint boarSearchCentre(WorldPoint tunnel) {
        WorldPoint known = boarTiles.get(tunnel);
        return known != null && known.distanceTo(tunnel) <= HERBIBOAR_TUNNEL_RADIUS ? known : tunnel;
    }

    private static int boarSearchRadius(WorldPoint tunnel, WorldPoint centre) {
        return centre == tunnel ? HERBIBOAR_TUNNEL_RADIUS : 1;
    }

    /** Interaction timing is tick-based, not wall-clock. */
    private static int tickCount() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getTickCount()).orElse(0);
    }

    
    static int lowestDoseHeld(IntPredicate held, int... idsLowestFirst) {
        for (int id : idsLowestFirst) {
            if (held.test(id)) {
                return id;
            }
        }
        return -1;
    }

    private void consumeLowestDose(String action, int... idsLowestFirst) {
        int id = lowestDoseHeld(held -> Rs2Inventory.contains(held), idsLowestFirst);
        if (id != -1) {
            useItem(id, action);
            lastDrinkAt = Instant.now();
            drinkCooldownMs = nextDrinkCooldownMs();
        }
    }

    private void useItem(int id, String action) {
        backToInventory();
        Rs2ItemModel item = Rs2Inventory.get(id);
        if (item != null && Rs2Inventory.hover(item)) {
            sleep(Rs2Random.betweenInclusive(60, 140));
            if (sleepUntil(() -> {
                MenuEntry top = topMenuEntry();
                return top != null && action.equalsIgnoreCase(top.getOption()) && top.getItemId() == id;
            }, 300)) {
                java.awt.Point at = Microbot.getMouse().getMousePosition();
                Microbot.getMouse().click(new Point(at.x, at.y));
                return;
            }
        }
        Rs2Inventory.interact(id, action);
    }

    private static int nextDrinkCooldownMs() {
        return Math.max(4000, Rs2Random.randomGaussian(6000, 1200));
    }

    /** Never with a click queued (an inventory click cancels it) or mid-action (the server drops it). */
    private void upkeep() {
        backToInventory();
        dropConfiguredItems(config);
        openHerbSack(config);
        manageRunEnergy(config);
    }

    private void backToInventory() {
        // Asked of the widget: Rs2Tab's cached tab can be stale (still INVENTORY with the world switcher up).
        if (Rs2Bank.isOpen() || inventoryShown()) return;
        Widget tab = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget[] roots = Microbot.getClient().getWidgetRoots();
            if (roots == null) return null;
            for (Widget w : roots) {
                Widget found = withAction(w, "Inventory");
                if (found != null) return found;
            }
            return null;
        }).orElse(null);
        hbLog.detail("UPKEEP", "Inventory is not showing; back to it");
        if (tab != null) {
            Rs2Widget.clickWidget(tab);
        } else {
            int key = InterfaceTab.INVENTORY.getHotkey();
            if (key != -1) Rs2Keyboard.keyPress(key);
        }
        sleepUntil(this::inventoryShown, 1200);
    }

    private boolean inventoryShown() {
        return Rs2Widget.isWidgetVisible(InterfaceID.Inventory.ITEMS);
    }

    private static Widget withAction(Widget w, String action) {
        if (w == null || w.isHidden()) return null;
        if (w.getActions() != null && Arrays.asList(w.getActions()).contains(action)) return w;
        for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()}) {
            if (kids == null) continue;
            for (Widget k : kids) {
                Widget found = withAction(k, action);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean inDeadTime() {
        if (Rs2Player.isAnimating()) return false;
        if (state == HerbiboarState.TUNNEL && attackedTunnel && plugin.getFinishId() == 0) return true;
        boolean trailWork = state == HerbiboarState.START || state == HerbiboarState.TRAIL
                || state == HerbiboarState.TUNNEL || state == HerbiboarState.HARVEST;
        return !trailWork && inFlight == null && !Rs2Player.isMoving();
    }

    /** Whether the step must wait for upkeep before the next click. Pure. */
    static boolean upkeepDue(boolean inventoryShown, boolean somethingToDrop, boolean closedSackToOpen, boolean drinkDue) {
        return !inventoryShown || somethingToDrop || closedSackToOpen || drinkDue;
    }

    private boolean drinkDue(HerbiboarConfig config) {
        // A drink takes a moment to register; without this the same potion goes down on consecutive passes.
        if (!Instant.now().isAfter(lastDrinkAt.plusMillis(drinkCooldownMs))) return false;
        // hasStaminaActive reads the live varbit; hasStaminaBuffActive is a stale static.
        return hasEnergyItems(config) && shouldUseEnergyItem(config.runEnergyOption(), config.stamBuffAlwaysActive(),
            Rs2Player.hasStaminaActive(), Rs2Player.getRunEnergy(), config.thresholdEnergy());
    }

    private void manageRunEnergy(HerbiboarConfig config) {
        if (!drinkDue(config)) return;
        switch (config.runEnergyOption()) {
            case STRANGE_FRUIT:
                if (Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT)) {
                    useItem(ItemID.MACRO_TRIFFIDFRUIT, "Eat");
                }
                break;
            case NONE:
                break;
            default:
                consumeLowestDose("Drink", doseIds(config.runEnergyOption()));
                break;
        }
    }

    /** One stamina dose at a time: a second while it runs is wasted. Pure. */
    static boolean shouldUseEnergyItem(HerbiboarConfig.RunEnergyOption option, boolean keepStaminaUp, boolean staminaActive,
                                       int energy, int threshold) {
        switch (option) {
            case NONE:
                return false;
            case STAMINA_POTION:
            case EXTENDED_STAMINA_POTION:
                if (staminaActive && energy > 15) return false;
                return keepStaminaUp || energy <= threshold;
            default:
                return energy <= threshold;
        }
    }
    
    /** The bank's side-panel lock options: "show lock option" and "ignore locks". Each button flips its setting. */
    private static void setBankSideLockOptions(int showOp, int ignoreLocks) {
        if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) == showOp
                && Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) == ignoreLocks) return;
        Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
        sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.Bankmain.MENU_CONTAINER), 2000);
        Rs2Widget.clickWidget(InterfaceID.Bankmain.LOCKS);
        sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000);
        if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) != ignoreLocks) {
            Rs2Widget.clickWidget(InterfaceID.BankSideLocks.IGNORELOCKS);
            sleepUntil(() -> Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) == ignoreLocks, 2000);
        }
        if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) != showOp) {
            Rs2Widget.clickWidget(InterfaceID.BankSideLocks.EXTRAOPTIONS);
            sleepUntil(() -> Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) == showOp, 2000);
        }
        Rs2Widget.clickWidget(InterfaceID.BankSideLocks.DONE);
        sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000);
        Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
    }

    private static void unlockSlots(List<Integer> slots) {
        for (int slot : slots) {
            Rs2ItemModel locked = Rs2Inventory.getItemInSlot(slot);
            // toggleItemLock(name) acts on the FIRST item of that name: only when that is this slot.
            Rs2ItemModel first = locked == null ? null : Rs2Inventory.get(locked.getName(), true);
            if (first != null && first.getSlot() == slot && Rs2Bank.isLockedSlot(slot)) {
                Rs2Bank.toggleItemLock(locked.getName(), true);
            }
        }
    }

    /** Only an open herb sack catches herbs as they are harvested. */
    private void openHerbSack(HerbiboarConfig config) {
        if (!config.useHerbSack()) return;
        for (int id : HERB_SACKS_CLOSED) {
            if (Rs2Inventory.contains(id)) {
                useItem(id, "Open");
                Rs2Inventory.waitForInventoryChanges(1200);
                return;
            }
        }
    }

    private void dropConfiguredItems(HerbiboarConfig config) {
        dropIfPresent(itemsToDrop(config).stream().mapToInt(Integer::intValue).toArray());
    }

    static List<Integer> itemsToDrop(HerbiboarConfig config) {
        List<Integer> ids = new ArrayList<>();
        if (config.dropEmptyVials()) ids.add(ItemID.VIAL_EMPTY);
        if (config.dropSmallFossil()) ids.add(ItemID.FOSSIL_SMALL_UNID);
        if (config.dropMediumFossil()) ids.add(ItemID.FOSSIL_MEDIUM_UNID);
        if (config.dropLargeFossil()) ids.add(ItemID.FOSSIL_LARGE_UNID);
        if (config.dropRareFossil()) ids.add(ItemID.FOSSIL_RARE_UNID);
        if (config.dropGuam()) ids.add(ItemID.UNIDENTIFIED_GUAM);
        if (config.dropMarrentill()) ids.add(ItemID.UNIDENTIFIED_MARENTILL);
        if (config.dropTarromin()) ids.add(ItemID.UNIDENTIFIED_TARROMIN);
        if (config.dropHarralander()) ids.add(ItemID.UNIDENTIFIED_HARRALANDER);
        if (config.dropRanarr()) ids.add(ItemID.UNIDENTIFIED_RANARR);
        if (config.dropToadflax()) ids.add(ItemID.UNIDENTIFIED_TOADFLAX);
        if (config.dropIrit()) ids.add(ItemID.UNIDENTIFIED_IRIT);
        if (config.dropAvantoe()) ids.add(ItemID.UNIDENTIFIED_AVANTOE);
        if (config.dropKwuarm()) ids.add(ItemID.UNIDENTIFIED_KWUARM);
        if (config.dropSnapdragon()) ids.add(ItemID.UNIDENTIFIED_SNAPDRAGON);
        if (config.dropCadantine()) ids.add(ItemID.UNIDENTIFIED_CADANTINE);
        if (config.dropLantadyme()) ids.add(ItemID.UNIDENTIFIED_LANTADYME);
        if (config.dropDwarfWeed()) ids.add(ItemID.UNIDENTIFIED_DWARF_WEED);
        if (config.dropTorstol()) ids.add(ItemID.UNIDENTIFIED_TORSTOL);
        return ids;
    }

    private void dropIfPresent(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) {
                Rs2Inventory.drop(itemId);
            }
        }
    }

    private boolean needsToBank(HerbiboarConfig config) {
        int empty = Rs2Inventory.emptySlotCount();
        boolean setupOk = hasRequiredInventorySetup(config);
        boolean bank = shouldBank(empty, setupOk);
        if (bank && state != HerbiboarState.BANK) {
            hbLog.event("BANK", "Banking: " + empty + " free slots" + (setupOk ? "" : ", setup incomplete (" + config.runEnergyOption()
                    + (config.useHerbSack() && !Rs2Inventory.contains(HERB_SACKS) ? ", no herb sack" : "") + ")"));
        }
        return bank;
    }

    static boolean shouldBank(int emptySlots, boolean setupOk) {
        return emptySlots == 0 || !setupOk;
    }

    private boolean hasRequiredInventorySetup(HerbiboarConfig config) {
        return setupOk(hasEnergyItems(config), config.useHerbSack(), Rs2Inventory.contains(HERB_SACKS),
                config.useMagicSecateurs(), Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS));
    }

    /** Takes the herb sack from the open bank when it is turned on and not carried, and puts the secateurs on. */
    private void withdrawMissingSetup(HerbiboarConfig config) {
        if (config.useHerbSack() && !Rs2Inventory.contains(HERB_SACKS)) {
            for (int sack : HERB_SACKS) {
                if (Rs2Bank.hasItem(sack)) {
                    Rs2Bank.withdrawOne(sack);
                    break;
                }
            }
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        if (config.useMagicSecateurs() && !Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
            if (!Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS) && Rs2Bank.hasItem(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                Rs2Bank.withdrawOne(ItemID.FAIRY_ENCHANTED_SECATEURS);
                Rs2Inventory.waitForInventoryChanges(1000);
            }
            // Worn, not carried: they work from the inventory too, but worn they free a slot for herbs.
            if (Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                Rs2Bank.wearItem(ItemID.FAIRY_ENCHANTED_SECATEURS);
                sleepUntil(() -> Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS), 1800);
            }
        }
    }

    static boolean setupOk(boolean energyOk, boolean useSack, boolean hasSack, boolean useSecateurs, boolean hasSecateurs) {
        return energyOk && (!useSack || hasSack) && (!useSecateurs || hasSecateurs);
    }

    static boolean shouldRestock(boolean restockOn, boolean energyOk, HerbiboarConfig.RunEnergyOption option) {
        return restockOn && !energyOk && fullDoseItemId(option) != -1;
    }

    private static boolean hasEnergyItems(HerbiboarConfig config) {
        HerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();
        boolean hasEnergyItems;
        switch (energyOption) {
            case STRANGE_FRUIT:
                hasEnergyItems = Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT);
                break;
            case NONE:
                hasEnergyItems = true;
                break;
            default:
                hasEnergyItems = Rs2Inventory.contains(doseIds(energyOption));
                break;
        }
        return hasEnergyItems;
    }
    private void ensureEnergyDoses(HerbiboarConfig.RunEnergyOption option, int targetDoses) {
        int fullDoseId = fullDoseItemId(option);
        if (fullDoseId == -1 || !Rs2Bank.isOpen()) return;
        int wanted = Math.max(1, targetDoses / 4);
        if (Rs2Inventory.count(fullDoseId) >= wanted) return;
        if (Rs2Bank.count(fullDoseId) > 0) {
            Rs2Bank.withdrawX(fullDoseId, wanted - Rs2Inventory.count(fullDoseId));
            Rs2Inventory.waitForInventoryChanges(2000);
            return;
        }
        // bankedDoses counts partial potions, so they must be usable or the bank reads stocked but gives nothing
        int left = wanted;
        int[] ids = doseIds(option);
        for (int i = ids.length - 2; i >= 0 && left > 0; i--) {
            int take = Math.min(left, Rs2Bank.count(ids[i]));
            if (take == 0) continue;
            Rs2Bank.withdrawX(ids[i], take);
            Rs2Inventory.waitForInventoryChanges(2000);
            left -= take;
        }
    }

    static int fullDoseItemId(HerbiboarConfig.RunEnergyOption option) {
        int[] ids = doseIds(option);
        return ids.length == 0 ? -1 : ids[ids.length - 1];
    }

    /** Its tick deadline cannot be pushed back by moving. */
    private static final class InFlight {
        final WorldPoint target;
        final Object snapshot;
        final int deadlineTick;
        final String what;
        final int dispatchTick = tickCount();

        InFlight(WorldPoint target, Object snapshot, int deadlineTick, String what) {
            this.target = target;
            this.snapshot = snapshot;
            this.deadlineTick = deadlineTick;
            this.what = what;
        }
    }

    private InFlight inFlight;
    private final java.util.concurrent.atomic.AtomicBoolean passRunning = new java.util.concurrent.atomic.AtomicBoolean();
    private HerbiHunterPlugin plugin;
    private HerbiboarConfig config;
    private WorldPoint skipStart;
    private String lastIdleReason;
    private HerbiboarState lastPolledState;

    private void idle(String reason) {
        activity = reason;
        if (!reason.equals(lastIdleReason)) {
            lastIdleReason = reason;
            hbLog.detail("WAIT", "Waiting: " + reason);
        }
    }

    private void settled() {
        activity = null;
        lastIdleReason = null;
    }

    private void clearInFlight() {
        inFlight = null;
        lastIdleReason = null;
        activity = null;
    }

    private boolean waitingOn(WorldPoint loc, Supplier<Object> progress) {
        if (inFlight == null) return false;
        boolean progressChanged = !Objects.equals(progress.get(), inFlight.snapshot);
        boolean deadlinePassed = tickCount() >= inFlight.deadlineTick;
        boolean differentTarget = loc != null && !loc.equals(inFlight.target);
        if (!progressChanged && hitInterrupted(plugin.getLastHitTick(), inFlight.dispatchTick,
                tickCount(), Rs2Player.isMoving() || Rs2Player.isAnimating())) {
            Rs2NpcModel attacker = Microbot.getRs2NpcCache().query()
                .where(n -> !"Herbiboar".equals(n.getName()) && n.isInteractingWithPlayer()).first();
            hbLog.event("RECOVER", "Hit by " + (attacker == null ? "an NPC" : attacker.getName()) + " during "
                + inFlight.what + (state == HerbiboarState.START ? "; trying another start" : "; clicking again"));
            if (state == HerbiboarState.START) { skipStart = inFlight.target; startTarget = null; startChosen = false; }
            clearInFlight();
            if (!Rs2Combat.setAutoRetaliate(false)) hbLog.problem("PROBLEM", "Could not turn auto retaliate off");
            return false;
        }
        if (progressChanged) {
            stepTimer.mark("seen", System.currentTimeMillis());
            unansweredCount = 0;
            // End this pass so the next decides from the new state: carrying on re-attacks an emptied tunnel.
            clearInFlight();
            sleep(reactionMs());
            if (lapseAllowed(state, herbiboarAt(huntTunnel) != null, inFlight != null)) {
                long lapse = pace().lapseMs(paceRng);
                if (lapse > 0) {
                    idle("taking a moment");
                    sleep((int) lapse);
                    settled();
                }
            }
            stepTimer.mark("react", System.currentTimeMillis());
            // Waiting out the search animation is only for inventory work, which the game drops mid-animation.
            boolean due = upkeepDue(inventoryShown(),
                Rs2Inventory.contains(itemsToDrop(config).stream().mapToInt(Integer::intValue).toArray()),
                config.useHerbSack() && Rs2Inventory.contains(HERB_SACKS_CLOSED), drinkDue(config));
            boolean still = due && !Rs2Player.isMoving() && sleepUntil(() -> !Rs2Player.isAnimating(), 1200);
            stepTimer.mark("anim", System.currentTimeMillis());
            if (still) upkeep();
            stepTimer.mark("upkeep", System.currentTimeMillis());
            return true;
        }
        if (dispatchResolved(false, deadlinePassed, differentTarget)) {
            if (deadlinePassed && !progressChanged) {
                hbLog.event("RECOVER", "No answer to " + inFlight.what + " within "
                        + DISPATCH_DEADLINE_TICKS + " ticks; retrying");
                hbLog.unanswered();
                if (state == HerbiboarState.START) { skipStart = inFlight.target; startTarget = null; startChosen = false; }
                noteUnanswered(inFlight.target);
            }
            clearInFlight();
            return false;
        }
        if (state == HerbiboarState.TRAIL && Rs2Player.isAnimating()) driftCameraWhileSearching();
        idle(inFlight.what);
        return true;
    }

    static boolean hitInterrupted(int hitTick, int dispatchTick, int now, boolean busy) {
        return hitTick > dispatchTick && now >= hitTick + 2 && !busy;
    }

    private WorldPoint unansweredAt;
    private int unansweredCount;
    private WorldPoint relogTriedFor;
    private volatile boolean relogPending;

    /** The game can ignore a correct Inspect for minutes; a relog starts a fresh trail. */
    private void noteUnanswered(WorldPoint target) {
        if (target == null || state != HerbiboarState.TRAIL) return;
        if (target.equals(unansweredAt)) unansweredCount++;
        else {
            unansweredAt = target;
            unansweredCount = 1;
        }
        if (unansweredCount >= 2) {
            String dump = Microbot.getClientThread().runOnClientThreadOptional(() -> plugin.trailDump()).orElse("?");
            hbLog.event("STUCK", String.format("%s unanswered %dx; trail: %s", target, unansweredCount, dump));
        }
        if (trailIsStuck(unansweredCount) && !target.equals(relogTriedFor)) {
            relogTriedFor = target;
            unansweredCount = 0;
            hbLog.problem("PROBLEM", "The trail is stuck at " + target + " (no answer 3 times); logging out and back in for a fresh trail");
            relogPending = true;
        }
    }

    static boolean trailIsStuck(int unansweredSearches) {
        return unansweredSearches >= 3;
    }

    private final java.util.Random paceRng = new java.util.Random();
    private HerbiboarPace sessionRelaxed;

    /** The same instance every time, so switching keeps the personality. */
    static HerbiboarPace choose(HerbiboarConfig.PlayStyle style, HerbiboarPace sessionRelaxed) {
        return style == HerbiboarConfig.PlayStyle.FOCUSED || sessionRelaxed == null ? HerbiboarPace.focused() : sessionRelaxed;
    }

    private HerbiboarPace sessionFocused;
    private HerbiboarHabits habits = HerbiboarHabits.draw(0);
    private double annoyance, breakingPoint = 1;
    private WorldPoint annoyanceJudged;
    private WorldPoint followJudged;

    /**
     * The client picks the natural-mouse profile from the activity intensity. Herbiboar hunting is rated Low, the
     * slow "normal gamer" profile; Focused is the speed mode, so it takes High, the "fast gamer" one.
     */
    static ActivityIntensity mouseIntensity(boolean relaxed) {
        return relaxed ? Activity.HUNTING_HERBIBOARS.getActivityIntensity() : ActivityIntensity.HIGH;
    }

    private HerbiboarPace pace() {
        HerbiboarPace p = choose(config.playStyle(), sessionRelaxed);
        return p.isRelaxed() || sessionFocused == null ? p : sessionFocused;
    }

    private int reactionMs() {
        return pace().reactionMs(System.currentTimeMillis(), paceRng);
    }

    static String antibanOverride(boolean universal, boolean overwrite) {
        if (universal && overwrite) return "Universal antiban, Overwrite script settings";
        return universal ? "Universal antiban" : overwrite ? "Overwrite script settings" : null;
    }

    static boolean lapseAllowed(HerbiboarState state, boolean boarOut, boolean clickPending) {
        return (state == HerbiboarState.TRAIL || state == HerbiboarState.START) && !boarOut && !clickPending;
    }

    static boolean distractionAllowed(HerbiboarState s, boolean boarOut) {
        return s != HerbiboarState.TUNNEL && s != HerbiboarState.HARVEST && !boarOut;
    }

    private InFlight driftChecked;

    /** The default tolerance would swallow a 10-25 degree turn. */
    static final int DRIFT_TOLERANCE_MIN = 2, DRIFT_TOLERANCE_MAX = 5;

    private void driftCameraWhileSearching() {
        if (driftChecked == inFlight) return;
        driftChecked = inFlight;
        if (!distractionAllowed(state, herbiboarAt(huntTunnel) != null)
                || !pace().driftCameraDuringSearch(paceRng)) return;
        idle("looking around");
        int sign = paceRng.nextBoolean() ? 1 : -1;
        turnCameraTo(Math.floorMod(Rs2Camera.getAngle() + sign * Rs2Random.betweenInclusive(10, 25), 360),
            Rs2Random.betweenInclusive(DRIFT_TOLERANCE_MIN, DRIFT_TOLERANCE_MAX), () -> false);
    }

    private void glanceAtXp() {
        int[] v = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client c = Microbot.getClient();
            return new int[]{c.getViewportXOffset() + c.getViewportWidth(), c.getViewportYOffset()};
        }).orElse(null);
        if (v == null) return;
        idle("checking xp");
        Microbot.getNaturalMouse().moveTo(v[0] - Rs2Random.betweenInclusive(90, 140), v[1] + Rs2Random.betweenInclusive(20, 70));
        sleep(Rs2Random.betweenInclusive(400, 1200));
    }

    private void lookAtSkills() {
        idle("looking at skills");
        Widget tab = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget[] roots = Microbot.getClient().getWidgetRoots();
            if (roots == null) return null;
            for (Widget w : roots) {
                Widget found = withAction(w, "Skills");
                if (found != null) return found;
            }
            return null;
        }).orElse(null);
        if (tab != null) {
            Rs2Widget.clickWidget(tab);
        } else {
            int key = InterfaceTab.SKILLS.getHotkey();
            if (key == -1) return;
            Rs2Keyboard.keyPress(key);
        }
        sleepUntil(() -> Rs2Widget.findWidget("Hunter", false) != null, 1500);
        Widget hunter = Rs2Widget.findWidget("Hunter", false);
        if (hunter != null && hunter.getBounds() != null) {
            Rectangle b = hunter.getBounds();
            Microbot.getNaturalMouse().moveTo(b.x + b.width / 2 + Rs2Random.betweenInclusive(-b.width / 4, b.width / 4),
                b.y + b.height / 2 + Rs2Random.betweenInclusive(-b.height / 4, b.height / 4));
        }
        sleep(Rs2Random.betweenInclusive(800, 2000));
        sleep(Rs2Random.betweenInclusive(70, 110));
        backToInventory();
    }

    private void afterCatchDistraction() {
        switch (pace().afterCatch(paceRng)) {
            case XP_GLANCE: glanceAtXp(); break;
            case SKILL_TAB: lookAtSkills(); break;
            default: break;
        }
        settled();
    }

    static boolean dispatchResolved(boolean progressChanged, boolean deadlinePassed,
                                    boolean differentTarget) {
        return progressChanged || deadlinePassed || differentTarget;
    }

    private boolean approachAndInteract(TileObject object, WorldPoint loc, String action,
                                        Supplier<Object> progress, HerbiboarConfig config) {
        if (loc == null) return false;
        showRoute(loc);
        if (waitingOn(loc, progress)) return false;
        stepTimer.mark("pass", System.currentTimeMillis());
        // Cross the hole first: clicking an object on its far side paths the long way round.
        WorldPoint from = Rs2Player.getWorldLocation();
        if (useHole(from, loc)) {
            tryHoleShortcut();
            return false;
        }
        if (object == null) {
            // Not in the loaded scene yet; walking there loads it, so arrival = it loaded (not a distance check).
            if (!minimapStepToward(loc, () -> plugin.getTrailObjects().get(loc) != null
                    || plugin.getTunnels().get(loc) != null || plugin.getStarts().get(loc) != null)) {
                travelTo(loc, () -> false, config);
            }
            idle("walking to load " + loc);
            return false;
        }
        if (from != null && !onScreen(object)) {
            int objectAngle = Rs2Camera.getObjectAngle(object);
            int off = Rs2Camera.getAngleTo(objectAngle);
            Shape seen = hullOf(object);
            if (seen != null && !partlyInView(seen.getBounds())
                    && smallTurnShows(from.distanceTo(loc), true, off, habits.turnMaxDeg,
                            Math.min(habits.clickRangeTiles, TURN_SHOWS_MAX_TILES))
                    && habits.turnsFor(off, paceRng)) {
                hbLog.detail("CAM", "Turning the camera " + Math.abs(off) + " deg to " + loc + " rather than walking to it");
                // A tight stop tolerance, or the turn ends still out of view.
                turnCameraTo(objectAngle, Rs2Random.betweenInclusive(DRIFT_TOLERANCE_MIN, DRIFT_TOLERANCE_MAX),
                        () -> comfortablyInView(hullOf(object)));
            }
        }
        if (from != null && !loc.equals(annoyanceJudged)
                && from.distanceTo(loc) <= Math.min(habits.clickRangeTiles, TURN_SHOWS_MAX_TILES)) {
            annoyanceJudged = loc;
            int off = Rs2Camera.getAngleTo(Rs2Camera.getObjectAngle(object));
            annoyance = habits.annoyance(annoyance, off, paceRng);
            if (annoyance >= breakingPoint) {
                int to = HerbiboarHabits.turnAroundTarget(Rs2Camera.getAngle(), off, habits.turnMaxDeg, paceRng);
                hbLog.detail("TURNROUND", String.format(java.util.Locale.ENGLISH, "%d deg off %s, annoyance %.2f >= %.2f: turning to %d", off, loc, annoyance, breakingPoint, to));
                turnCameraTo(to, Rs2Random.betweenInclusive(3, 12), () -> false);
                annoyance = 0;
                breakingPoint = habits.breakingPoint(paceRng);
            }
        }
        // Judged once per spot: the walk that follows a "no" must not be second-guessed mid-stride.
        if (from != null && !loc.equals(followJudged)) {
            followJudged = loc;
            double odds = pace().followTurnChance;
            if (odds > 0 && turnsToFollow(onScreen(object), from.distanceTo(loc), habits.clickRangeTiles, odds, paceRng.nextDouble())) {
                int objectAngle = Rs2Camera.getObjectAngle(object);
                hbLog.detail("CAM", "Following the trail: turning the camera " + Math.abs(Rs2Camera.getAngleTo(objectAngle))
                        + " deg to " + loc);
                turnCameraTo(objectAngle, Rs2Random.betweenInclusive(DRIFT_TOLERANCE_MIN, DRIFT_TOLERANCE_MAX),
                        () -> comfortablyInView(hullOf(object)));
            }
        }
        if (from != null && walkFirst(onScreen(object), from.distanceTo(loc), habits.clickRangeTiles)) {
            Shape wh = hullOf(object);
            hbLog.detail("WALK", String.format(java.util.Locale.ENGLISH, "%s: %d tiles, %s, camera %d deg off it",
                    loc, from.distanceTo(loc),
                    from.distanceTo(loc) > habits.clickRangeTiles ? "out of click range"
                            : wh == null ? "not drawn" : partlyInView(wh.getBounds()) ? "partly in view" : "out of view",
                    Rs2Camera.getAngleTo(Rs2Camera.getObjectAngle(object))));
            BooleanSupplier inReach = () -> onScreen(object) && Rs2Player.getWorldLocation().distanceTo(loc) <= habits.clickRangeTiles;
            if (!minimapStepToward(loc, inReach)) {
                travelTo(loc, inReach, config);
            }
            idle("closing on " + loc);
            return false;
        }
        if (!clickWhenVisible(Rs2Camera.getObjectAngle(object), () -> hullOf(object), loc)) return false;
        if (!hoverOnce(() -> hullOf(object), () -> Rs2Camera.getObjectAngle(object), withKnownBlocker(loc, overlappingTrailHulls(object)))) {
            if (!minimapStepToward(loc, () -> onScreen(object))) travelTo(loc, () -> onScreen(object), config);
            idle("closing on " + loc + " to aim");
            return false;
        }
        stepTimer.mark("aim", System.currentTimeMillis());
        if (!clickUnderCursor(action, object.getId(), () -> hullOf(object), () -> model(object).click(action), loc,
                () -> Rs2Camera.getObjectAngle(object))) return false;
        logStep("click");
        int walkTicks = from == null ? 0 : from.distanceTo(loc);
        inFlight = new InFlight(loc, progress.get(), tickCount() + DISPATCH_DEADLINE_TICKS + walkTicks,
                action + " " + loc);
        lastIdleReason = null;
        return true;
    }

    /** One client-thread hop for the whole scan: a worldToMinimap per tile off it waits a frame each. */
    private static WorldPoint furthestOnMinimap(List<WorldPoint> path, WorldPoint from) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> furthestOnMinimapNow(path, from)).orElse(null);
    }

    private static WorldPoint furthestOnMinimapNow(List<WorldPoint> path, WorldPoint from) {
        int end = path.size();
        for (int i = 0; i + 1 < path.size(); i++) {
            if (path.get(i).distanceTo(path.get(i + 1)) > 2) {
                end = i + 1;
                break;
            }
        }
        int visible = 0;
        for (int i = 0; i < end; i++) {
            Point p = Rs2MiniMap.worldToMinimap(path.get(i));
            if (p != null && Rs2MiniMap.isPointInsideMinimap(p)) visible = Math.max(visible, path.get(i).distanceTo(from));
        }
        int reach = minimapReach(visible, Rs2Random.dicePercentage(MINIMAP_SHORT_HOP_PERCENT), Math.random());
        for (int i = end - 1; i >= 0; i--) {
            if (path.get(i).distanceTo(from) > reach) continue;
            Point p = Rs2MiniMap.worldToMinimap(path.get(i));
            if (p != null && Rs2MiniMap.isPointInsideMinimap(p)) return path.get(i);
        }
        return null;
    }

    static int minimapReach(int visible, boolean shortHop, double u) {
        int floor = Math.min(MINIMAP_REACH_MIN, visible);
        if (visible <= floor) return visible;
        int lo = shortHop ? floor : Math.max(floor, (int) Math.ceil(visible * MINIMAP_OUTER_BAND));
        return Math.min(visible, lo + (int) (u * (visible - lo + 1)));
    }

    private WorldPoint routeFor;
    private long routeAskedAt;
    private static final long ROUTE_RETRY_MS = 3000;

    private void showRoute(WorldPoint to) {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (to == null || me == null) return;
        List<WorldPoint> shown = plugin.getWalkRoute();
        int at = HerbiHunterPlugin.routeAheadFrom(shown, me);
        long now = System.currentTimeMillis();
        if (reusesRoute(to.equals(routeFor), shown == null ? 0 : shown.size(),
                at < 0 ? -1 : shown.get(at).distanceTo(me), now - routeAskedAt)) return;
        routeFor = to;
        routeAskedAt = now;
        plugin.setWalkRoute(Rs2Walker.getWalkPath(me, to));
    }

    /** Kept while we stay within 3 tiles of it (or have arrived); a route that came back empty is asked for again now and then. Pure. */
    static boolean reusesRoute(boolean sameTarget, int routeSize, int tilesOffRoute, long sinceAskedMs) {
        if (!sameTarget) return false;
        if (routeSize < 2) return sinceAskedMs < ROUTE_RETRY_MS;
        return tilesOffRoute <= 3;
    }

    private void clearRoute() {
        routeFor = null;
        plugin.setWalkRoute(Collections.emptyList());
    }

    /** The walker keeps turning the camera toward its target for up to 5s otherwise. */
    private static void dropWalkerRoute() {
        Rs2Walker.setTarget(null, "herbiboar: leg finished, our own clicks take over");
    }

    /**
     * Over the bare minimap the menu holds only Cancel and a left click walks; an orb or the compass puts its own
     * option on top ("Floating World Map", "Look North"), and that is what a click there would do. Pure.
     */
    static boolean minimapClickIsAWalk(String topOption) {
        return topOption == null || "Walk here".equals(topOption) || "Cancel".equals(topOption);
    }

    private boolean minimapStepToward(WorldPoint loc, BooleanSupplier arrived) {
        WorldPoint from = Rs2Player.getWorldLocation();
        if (from == null) return false;
        List<WorldPoint> path = Rs2Walker.getWalkPath(from, loc);
        WorldPoint step = furthestOnMinimap(path.isEmpty() ? Collections.singletonList(loc) : path, from);
        if (step == null || step.distanceTo(from) <= 1) return false;
        dropWalkerRoute();
        // Glide over first: clicking cold presses at full speed, a one-frame jump that reads as a teleport.
        Point dot = Rs2MiniMap.worldToMinimap(step);
        java.awt.Point mouse = Microbot.getMouse().getMousePosition();
        if (dot != null && (mouse == null || mouse.distance(dot.getX(), dot.getY()) > MINIMAP_HOVER_PX)) {
            Microbot.getNaturalMouse().moveTo(dot.getX(), dot.getY());
            sleep(Rs2Random.betweenInclusive(70, 180));
        }
        // The orbs sit on the minimap's rim, right where a far step lands.
        MenuEntry over = topMenuEntry();
        if (!minimapClickIsAWalk(over == null ? null : over.getOption())) {
            hbLog.detail("WALK", "Minimap step skipped: the cursor is over "
                    + net.runelite.client.util.Text.removeTags(over.getOption()));
            return false;
        }
        if (!Rs2Walker.walkMiniMap(step)) return false;
        logStep("walk");
        int early = Rs2Random.betweenInclusive(2, 5);
        long stillAfter = System.currentTimeMillis() + Rs2Random.betweenInclusive(1100, 1700);
        sleepUntil(() -> arrived.getAsBoolean()
                || Rs2Player.getWorldLocation().distanceTo(step) <= early
                || (System.currentTimeMillis() > stillAfter && !Rs2Player.isMoving()), 10000);
        return true;
    }

    private void travelTo(WorldPoint loc, BooleanSupplier arrived, HerbiboarConfig config) {
        travelTo(loc, 3, arrived);
    }

    /** @param within the walker's arrival radius; must match the caller's, or ARRIVED-without-a-step repeats forever. */
    private void travelTo(WorldPoint loc, int within, BooleanSupplier arrived) {
        WorldPoint from = Rs2Player.getWorldLocation();
        // The walker routes to the hole and then walks round; we climb it ourselves.
        if (useHole(from, loc)) {
            tryHoleShortcut();
            return;
        }
        logStep("walk");
        WalkerState result = Rs2Walker.walkWithStateUntil(loc, within, arrived);
        dropWalkerRoute();
        WorldPoint now = Rs2Player.getWorldLocation();
        boolean moved = from != null && !from.equals(now);
        // ARRIVED without a step and without the caller's arrival is a stall, not an arrival.
        if (moved || (result == WalkerState.ARRIVED && arrived.getAsBoolean())) {
            stalledWalks = 0;
            return;
        }
        stalledWalks++;
        hbLog.event("RECOVER", "Walk to " + loc + " ended as " + result + " without moving (stall " + stalledWalks + ")");
        switch (stallAction(stalledWalks)) {
            case RESET:
                hbLog.event("RECOVER", "Walking stalled " + stalledWalks + " times, resetting script state");
                stalledWalks = 0;
                setState(HerbiboarState.RESET);
                break;
            case SHORTCUT:
                // useHole's cached "walk round" is why we stalled; climb anyway (tryHoleShortcut checks Agility).
                if (crossesHole(from, loc)) tryHoleShortcut();
                break;
            case CONTINUE:
            default:
                break;
        }
    }

    private int stuckMinutes;
    // The bank and start-up pause the stuck clock, so their retries are counted here instead.
    private int bankPasses, lockFails, retaliateTries;

    /** Three tries in a row at a step the stuck clock does not watch, then give up. Pure. */
    static boolean outOfTries(int tries) {
        return tries >= 3;
    }

    /** States where standing still is expected. Pure. */
    static boolean stuckClockPaused(HerbiboarState state, boolean bankOpen) {
        return state == HerbiboarState.RESET || state == HerbiboarState.INITIALIZING
                || state == HerbiboarState.CHECK_AUTO_RETALIATE || state == HerbiboarState.RESTOCK
                || (state == HerbiboarState.BANK && bankOpen);
    }

    /** Only a 99 gained this session logs out: a character that started at 99 ticked the box by habit. Pure. */
    static boolean logOutAt99(boolean enabled, int levelAtStart, int levelNow) {
        return enabled && levelAtStart > 0 && levelAtStart < 99 && levelNow >= 99;
    }

    static boolean giveUpWhenStuck(int consecutiveStuckMinutes) {
        return consecutiveStuckMinutes >= 3;
    }

    enum StallAction { CONTINUE, SHORTCUT, RESET }

    static StallAction stallAction(int stalls) {
        if (stalls >= STALLS_BEFORE_RESET) return StallAction.RESET;
        if (stalls >= STALLS_BEFORE_SHORTCUT) return StallAction.SHORTCUT;
        return StallAction.CONTINUE;
    }

    static boolean crossesHole(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) return false;
        return (from.getY() < HOLE_DIVIDE_Y) != (to.getY() < HOLE_DIVIDE_Y);
    }

    /** A just-started trail has a group but no spots. Pure. */
    static boolean trailIsLost(boolean pathEmpty, boolean noGroup) {
        return pathEmpty && noGroup;
    }

    static WorldPoint nextTargetOf(int finishId, List<HerbiboarSearchSpot> path, List<WorldPoint> endLocations) {
        if (validFinishId(finishId, endLocations.size())) return endLocations.get(finishId - 1);
        return path.isEmpty() ? null : path.get(path.size() - 1).getLocation();
    }

    /** Tiles the hole climb is worth when comparing routes. */
    static final int HOLE_ROUTE_PENALTY = 12;

    static int routeCost(List<WorldPoint> path) {
        if (path == null || path.isEmpty()) return -1;
        return path.size() + (pathJumpsHole(path) ? HOLE_ROUTE_PENALTY : 0);
    }

    static WorldPoint nearestByRoute(List<WorldPoint> candidates, java.util.function.ToIntFunction<WorldPoint> cost,
                                     WorldPoint skip) {
        WorldPoint best = null;
        int bestCost = Integer.MAX_VALUE;
        for (WorldPoint c : candidates) {
            if (c.equals(skip)) continue;
            int n = cost.applyAsInt(c);
            if (n >= 0 && n < bestCost) {
                best = c;
                bestCost = n;
            }
        }
        return best;
    }

    private int startRouteCost(WorldPoint me, WorldPoint rock) {
        try {
            List<WorldPoint> path = Rs2Walker.getWalkPath(me, rock);
            // A rock is not walkable: a good path ends beside it (MIDDLE's nearest standable tile is 3-4 away).
            if (path == null || path.isEmpty() || path.get(path.size() - 1).distanceTo(rock) > 5) {
                hbLog.detail("START", String.format("Start route to %s: no path (%s)", rock,
                        path == null || path.isEmpty() ? "empty" : "ends " + path.get(path.size() - 1).distanceTo(rock) + " away"));
                return -1;
            }
            return routeCost(path);
        } catch (Exception e) {
            hbLog.detail("START", String.format("Start route to %s: no path (%s)", rock, e.toString()));
            return -1;
        }
    }

    private WorldPoint notedNext;
    private WorldPoint startTarget;
    private boolean startChosen;
    private static final List<String> START_NAMES = List.of("MIDDLE", "LEPRECHAUN", "CAMP_ENTRANCE", "GHOST_MUSHROOM", "DRIFTWOOD");

    /** A break of any kind that is asked for, starting or running. */
    private static boolean breakDue() {
        return BreakHandlerScript.isMicroBreakActive() || breakActive();
    }

    /** The original handler's timer has run out, or Break Handler V2 has asked. Pure. */
    static boolean breakLooming(boolean handlerOn, int breakInSeconds, boolean v2Asked) {
        return (handlerOn && breakInSeconds <= 0) || v2Asked;
    }

    /** A looming break sends the next catch to the bank; not again within ten minutes if it never came. Pure. */
    static boolean bankForBreak(boolean looming, long msSinceLastTrip) {
        return looming && msSinceLastTrip >= 600_000;
    }

    /** The bank is the only place a break begins: nothing attacks there, and no trail is left hanging. Pure. */
    static boolean breakStartsAtBank(boolean trailLive, boolean breakDue) {
        return !trailLive && breakDue;
    }

    private boolean breakLooming() {
        boolean handlerOn = Microbot.isPluginEnabled(BreakHandlerPlugin.class)
                && !"true".equals(Microbot.getConfigManager().getConfiguration("break-handler", "OnlyMicroBreaks"));
        return breakLooming(handlerOn, BreakHandlerScript.breakIn, BreakHandlerV2State.isBreakActive());
    }

    private boolean trailLive() {
        return keepBreaksLockedAtBankExit(resumeState(plugin.getFinishId(), plugin.getCurrentGroup() != null));
    }

    /** Either break handler: the original one or Break Handler V2. */
    private static boolean breakActive() {
        return BreakHandlerScript.isBreakActive() || BreakHandlerV2State.isBreakActive();
    }

    /** A logout during a break is the handler's doing; only a disconnect is ours to undo. Pure. */
    static boolean mayLogBackIn(boolean atLoginScreen, boolean breakActive, boolean breakActiveV2, boolean cooldownPassed) {
        return atLoginScreen && !breakActive && !breakActiveV2 && cooldownPassed;
    }

    /** A requested break is answered once no trail is live. Pure. */
    static boolean readyForBreak(boolean requested, boolean trailLocked, boolean originalHandlerBreaking) {
        // The original handler stops checking the lock once it has asked, so it only hears "ready" between trails.
        // Break Handler V2 asks mid-trail and waits on the lock itself: an answer held back there times out at 120 s.
        return requested && (!trailLocked || !originalHandlerBreaking);
    }

    /** A live trail is always finished first; a handler that has committed pauses every script itself. Pure. */
    static boolean pauseForBreak(boolean breakActive, boolean trailLocked) {
        return breakActive && !trailLocked;
    }

    /** Logged out is always a safe place for a break to begin. */
    private void answerBreakRequest(boolean loggedOut) {
        BreakPreparation.Handle handle = breakPrep;
        if (handle == null || !handle.isRequested()) return;
        if (loggedOut || readyForBreak(true, BreakHandlerScript.isLockState(), BreakHandlerScript.isBreakActive())) {
            handle.ready();
        }
    }

    /** Out on the island an idle character is attacked, and the game refuses a logout in combat. Pure. */
    static boolean walksToBankBeforeLogout(boolean onIsland, int tilesFromBank) {
        return onIsland && tilesFromBank > 8;
    }

    /** Five to forty seconds, mostly short. Pure. */
    static long reloginWaitMs(double roll) {
        return 5000 + (long) (roll * roll * 35000);
    }

    /** Why this account can't hunt herbiboars, or null when it can. Boosted levels: the game accepts boosts. Pure. */
    static String levelShortfall(int hunter, int herblore) {
        if (hunter < 80) return "Herbi Hunter needs 80 Hunter to track herbiboars (you have " + hunter + "). Boost it or train it, then start again.";
        if (herblore < 31) return "Herbi Hunter needs 31 Herblore to harvest herbiboars (you have " + herblore + "). Boost it or train it, then start again.";
        return null;
    }

    /** Stops the run when a level is short; true when it stopped. */
    private boolean stopIfLevelsTooLow(HerbiHunterPlugin herbiboarPlugin) {
        int hunter = Rs2Player.getBoostedSkillLevel(Skill.HUNTER);
        int herblore = Rs2Player.getBoostedSkillLevel(Skill.HERBLORE);
        String why = levelShortfall(hunter, herblore);
        if (why == null) return false;
        hbLog.problem("PROBLEM", why);
        herbiboarPlugin.setStopReason(hunter < 80 ? "Hunter below 80" : "Herblore below 31");
        logOutAndStop();
        return true;
    }

    /** Ends the run for good: the client's auto-login is told this logout is meant, and no further pass acts. */
    private void logOutAndStop() {
        stopping = true;
        BreakHandlerScript.setLockState(false);
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me != null && walksToBankBeforeLogout(onFossilIsland(me), me.distanceTo(CAMP_BANK))) {
            hbLog.event("RUN", "Walking to the camp bank to log out: nothing attacks there");
            // Bounded: whatever stopped us may stop the walk too, and then we log out where we stand.
            long until = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < until && Microbot.isLoggedIn()) {
                WorldPoint now = Rs2Player.getWorldLocation();
                if (now == null || now.distanceTo(CAMP_BANK) <= 8) break;
                Rs2Walker.walkTo(CAMP_BANK, 4);
                sleep(Rs2Random.betweenInclusive(600, 1200));
            }
            dropWalkerRoute();
        }
        // The game refuses a logout within ten seconds of a hit, so the first click can do nothing.
        for (int tries = 0; tries < 4 && Microbot.isLoggedIn(); tries++) {
            Rs2Player.logoutWithoutAutoLogin();
            if (Microbot.isLoggedIn()) sleep(Rs2Random.betweenInclusive(3500, 5000));
        }
        Microbot.stopPlugin(plugin);
    }

    /** Hunting is never interrupted: a micro break only starts standing at the bank or the G.E. with no trail live. Pure. */
    static boolean microBreakNow(boolean mayRoll, boolean trailLive, double chance, double roll) {
        return mayRoll && !trailLive && roll < chance;
    }

    /** The odds are the play style's own, drawn per session; the Antiban panel only switches micro breaks on or off. */
    private boolean microBreakHere(boolean trailLive, String where) {
        if (!mayRollMicroBreak(Rs2AntibanSettings.takeMicroBreaks, Microbot.isPluginEnabled(BreakHandlerPlugin.class))) return false;
        if (!microBreakNow(true, trailLive, pace().microBreakChance, paceRng.nextDouble())) return false;
        Rs2AntibanSettings.microBreakActive = true;
        BreakHandlerScript.setLockState(false);
        hbLog.event("BREAK", "Micro break " + where);
        return true;
    }

    /** Only the Break Handler ends a micro break: without it a rolled one would pause the script for good. Pure. */
    static boolean mayRollMicroBreak(boolean microBreaksOn, boolean breakHandlerOn) {
        return microBreaksOn && breakHandlerOn;
    }

    private void notePaused() {
        HerbiboarSession session = plugin == null ? null : plugin.getSession();
        if (session != null) session.pause(System.currentTimeMillis());
    }

    /** Back from a break or pause: it is not hunting time, and standing still through it was not being stuck. */
    private void noteRunning() {
        HerbiboarSession session = plugin == null ? null : plugin.getSession();
        long ms = session == null ? 0 : session.resume(System.currentTimeMillis());
        if (ms <= 0) return;
        hbLog.paused(ms);
        lastMove = Instant.now();
        // A step that was open across the pause would be timed with the pause in it.
        if (ms >= 5000) stepTimer.reset();
        BreakHandlerScript.setLockState(breaksLocked(state));
        if (ms >= 5000) hbLog.event("BREAK", "Back after " + ms / 1000 + "s paused");
    }

    /**
     * Locked everywhere but the bank: a character that stands still on the shore is attacked by crabs, the handler
     * will not begin a break in combat, and the wait ends in the stuck check logging out.
     */
    static boolean breaksLocked(HerbiboarState s) {
        return s != HerbiboarState.BANK;
    }

    static boolean keepBreaksLockedAtBankExit(HerbiboarState resume) {
        return resume != HerbiboarState.START;
    }

    /** A null location reads as on the island: nothing moves on a bad read. Pure. */
    static boolean onFossilIsland(WorldPoint p) {
        return p == null || p.getX() >= 3600 && p.getX() <= 3860 && p.getY() >= 3690 && p.getY() <= 3910;
    }

    static boolean needsTravel(HerbiboarState s, WorldPoint me) {
        return s == HerbiboarState.START && !onFossilIsland(me);
    }

    static boolean pendantHelps(WorldPoint me) {
        return me != null && me.distanceTo(DIGSITE) > PENDANT_MIN_TILES;
    }

    static boolean bankAllowedIn(HerbiboarState s) {
        return s != HerbiboarState.INITIALIZING && s != HerbiboarState.CHECK_AUTO_RETALIATE && s != HerbiboarState.BANK
                && s != HerbiboarState.RESTOCK && s != HerbiboarState.TUNNEL && s != HerbiboarState.HARVEST;
    }

    /** The next trail may fill the inventory: about 1 fossil with the sack, up to 5 slots without. Pure. */
    static boolean bankAtCatch(int freeSlots, boolean useSack) {
        return freeSlots < (useSack ? 2 : 6);
    }

    static HerbiboarState resumeState(int finishId, boolean hasTrail) {
        if (finishId > 0) return HerbiboarState.TUNNEL;
        return hasTrail ? HerbiboarState.TRAIL : HerbiboarState.START;
    }

    static boolean validFinishId(int finishId, int endLocationCount) {
        return finishId > 0 && finishId <= endLocationCount;
    }

    private boolean tryHoleShortcut() {
        if (Rs2Player.getRealSkillLevel(Skill.AGILITY) < HOLE_SHORTCUT_AGILITY) {
            idle("hole needs " + HOLE_SHORTCUT_AGILITY + " Agility");
            return false;
        }
        Rs2TileObjectModel hole = Microbot.getRs2TileObjectCache().query()
                .withIds(HOLE_SHORTCUT_IDS).nearestOnClientThread(HOLE_SEARCH_DISTANCE);
        if (hole == null) {
            WorldPoint mouth = holeMouthNearest(Rs2Player.getWorldLocation());
            idle("walking toward the hole at " + mouth);
            Rs2Walker.walkWithStateUntil(mouth, 5, () -> Microbot.getRs2TileObjectCache().query()
                    .withIds(HOLE_SHORTCUT_IDS).nearestOnClientThread(HOLE_SEARCH_DISTANCE) != null);
            dropWalkerRoute();
            return false;
        }
        WorldPoint holeAt = hole.getWorldLocation();
        if (!onScreen(hole) && holeAt.distanceTo(Rs2Player.getWorldLocation()) > 6) {
            idle(minimapStepToward(holeAt, () -> onScreen(hole)) ? "closing on the hole" : "hole is off the minimap");
            return false;
        }
        if (!clickWhenVisible(Rs2Camera.getObjectAngle(hole), () -> hullOf(hole), holeAt)) return false;
        if (!hoverOnce(() -> hullOf(hole), () -> Rs2Camera.getObjectAngle(hole))) {
            idle("aiming at the hole");
            return false;
        }
        WorldPoint before = Rs2Player.getWorldLocation();
        if (!clickUnderCursor("Climb through", hole.getId(), () -> hullOf(hole), () -> hole.click("Climb through"), holeAt,
                () -> Rs2Camera.getObjectAngle(hole))) return false;
        logStep("click");
        hbLog.detail("START", "Clicking the hole to climb through it");
        // Crossing the divide is the proof the climb happened.
        if (!sleepUntil(() -> crossesHole(before, Rs2Player.getWorldLocation()), HOLE_CLIMB_TIMEOUT_MS)) {
            holeFails++;
            if (walksRoundAfterHoleFails(holeFails) && holeDecisionTarget != null) {
                holeDecision = false;
                hbLog.event("RECOVER", "The hole did not take us across " + holeFails + " times; walking round to " + holeDecisionTarget);
            }
            return false;
        }
        holeFails = 0;
        stalledWalks = 0;
        return true;
    }

    /** Climbs that never crossed: past this many for one target, the walk round is used instead. Pure. */
    static boolean walksRoundAfterHoleFails(int fails) {
        return fails >= 3;
    }

    /** [HB-T] timing lines: where the delay between a step landing and our next action goes. */
    final HerbiboarStepTimer stepTimer = new HerbiboarStepTimer();
    /** The plugin hands over its own in startUp; this one only keeps early calls from being null. */
    volatile HerbiboarLog hbLog = new HerbiboarLog();
    private long arrivedAtTunnelMs;
    private long harvestClickMs;
    private BreakPreparation.Handle breakPrep;
    private long lastBreakBankAt;

    private void logStep(String kind) {
        String line = stepTimer.finish(kind, System.currentTimeMillis());
        if (line == null) return;
        hbLog.detail("T", line.substring("[HB-T] ".length()));
        hbLog.step(stepTimer.lastWhat(), kind, stepTimer.lastTotalMs());
    }

    /** When we first stand still at the tunnel: how long before the boar shows is the pre-hover's whole window. */
    private void noteArrival(WorldPoint tunnel) {
        if (arrivedAtTunnelMs != 0 || tunnel == null) return;
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me != null && me.distanceTo(tunnel) <= 2 && !Rs2Player.isMoving()) arrivedAtTunnelMs = System.currentTimeMillis();
    }

    public boolean run(HerbiboarConfig config, HerbiHunterPlugin herbiboarPlugin) {
        plugin = herbiboarPlugin;
        stepTimer.reset();
        arrivedAtTunnelMs = 0;
        breakPrep = BreakPreparation.register("herbi-hunter");
        stopping = false;
        everLoggedIn = false;
        seenLoginScreenAt = 0;
        bankPasses = 0;
        lockFails = 0;
        retaliateTries = 0;
        // The script object outlives a stop: nothing from the last run may count against this one.
        stuckMinutes = 0;
        lastLocation = null;
        lastMove = Instant.now();
        stalledWalks = 0;
        holeFails = 0;
        skipStart = null;
        unansweredAt = null;
        unansweredCount = 0;
        relogTriedFor = null;
        relogPending = false;
        travelFails = 0;
        restockStep = RestockStep.COINS;
        restockWalkFails = 0;
        restockLegSince = 0;
        lastBreakBankAt = 0;
        pauseGenSeen = -1;
        lastPassAt = 0;
        this.config = config;
        // Script.run() drinks a stamina whenever energy is low and we are moving, which cancels a clicked object.
        if (savedAutoRun == null) {
            savedAutoRun = Microbot.enableAutoRunOn;
            savedAutoStamina = Microbot.useStaminaPotsIfNeeded;
        }
        Microbot.enableAutoRunOn = false;
        Microbot.useStaminaPotsIfNeeded = false;
        Rs2Walker.disableTeleports = true;
        // The walker scores mushtree, zip line and rowboat under walking, but they take about twice as long: off for the session.
        if (savedMushtrees == null) {
            Boolean set = Microbot.getConfigManager().getConfiguration(WALKER_CONFIG, MUSHTREES, Boolean.class);
            // unset means the walker's default, on
            savedMushtrees = set == null || set;
        }
        Microbot.getConfigManager().setConfiguration(WALKER_CONFIG, MUSHTREES, false);
        setState(HerbiboarState.INITIALIZING);

        sessionRelaxed = HerbiboarPace.relaxed(System.nanoTime(), System.currentTimeMillis());
        sessionFocused = HerbiboarPace.focused(System.nanoTime());
        habits = HerbiboarHabits.draw(System.nanoTime());
        annoyance = 0;
        annoyanceJudged = null;
        followJudged = null;
        breakingPoint = habits.breakingPoint(paceRng);
        hbLog.detail("PACE", String.format("%s", pace().describe()));
        hbLog.detail("HABITS", String.format("%s", habits.describe()));
        cameraFramed = false;
        // Saved before the Hunter template writes any of them, so shutdown gives back the user's own values.
        savedTakeMicroBreaks = Rs2AntibanSettings.takeMicroBreaks;
        savedMoveMouseRandomly = Rs2AntibanSettings.moveMouseRandomly;
        savedMoveMouseOffScreen = Rs2AntibanSettings.moveMouseOffScreen;
        savedSimulateMistakes = Rs2AntibanSettings.simulateMistakes;
        // The Hunter template switches micro breaks off; whether to take them is the user's choice.
        Rs2Antiban.antibanSetupTemplates.applyHunterSetup();
        Rs2AntibanSettings.takeMicroBreaks = savedTakeMicroBreaks;
        Rs2Antiban.setActivity(Activity.HUNTING_HERBIBOARS);
        // Our pace owns the mouse; the client's own random moves, off-screen trips and slips would fight it.
        Rs2AntibanSettings.moveMouseRandomly = false;
        Rs2AntibanSettings.moveMouseOffScreen = false;
        Rs2AntibanSettings.simulateMistakes = false;
        String override = antibanOverride(Rs2AntibanSettings.universalAntiban, Rs2AntibanSettings.overwriteScriptSettings);
        if (override != null) hbLog.problem("PROBLEM", String.format("%s is on in the client settings and may override this script's pace", override));

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (stopping) return;
            if (!passRunning.compareAndSet(false, true)) {
                hbLog.problem("PROBLEM", "A second script pass started while one was running; skipped");
                return;
            }
            try {
                // An interrupted thread makes every natural-mouse sleep return at once: moves teleport.
                if (Thread.interrupted()) hbLog.problem("PROBLEM", "Script thread was interrupted; cleared so the mouse moves naturally");
                // The client skips every pass while paused, so the pause shows only as a new generation.
                long gen = ScriptPauseController.getGeneration();
                long passAt = System.currentTimeMillis();
                if (pauseGenSeen >= 0 && gen != pauseGenSeen && lastPassAt > 0) {
                    HerbiboarSession paused = herbiboarPlugin.getSession();
                    if (paused != null) paused.pause(lastPassAt);   // noteRunning below files the gap as paused time
                }
                pauseGenSeen = gen;
                lastPassAt = passAt;
                // super.run() gates the pause only further down: the login and the welcome screen would still get input.
                if (ScriptPauseController.isPaused()) {
                    super.run();
                    answerBreakRequest(Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN);
                    notePaused();
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    // A region load (LOADING) is not a logout: it happens every ~40s out here.
                    if (Microbot.getClient().getGameState() != GameState.LOADING) wasLoggedOut = true;
                    // A person takes a moment to notice a disconnect. Not at the very start: there the user is waiting.
                    if (everLoggedIn && seenLoginScreenAt == 0 && !breakActive()
                            && Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN) {
                        seenLoginScreenAt = System.currentTimeMillis();
                        long wait = reloginWaitMs(paceRng.nextDouble());
                        nextLoginAt = Math.max(nextLoginAt, seenLoginScreenAt + wait);
                        hbLog.event("RECOVER", "Logged out; coming back in about " + wait / 1000 + "s");
                    }
                    // Never during a break: the break handler logs back in.
                    if (mayLogBackIn(Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN,
                            BreakHandlerScript.isBreakActive(), BreakHandlerV2State.isBreakActive(),
                            System.currentTimeMillis() >= nextLoginAt)) {
                        nextLoginAt = System.currentTimeMillis() + Rs2Random.betweenInclusive(8000, 15000);
                        hbLog.event("RECOVER", "At the login screen; logging back in");
                        LoginManager.login();
                    }
                    // A region load (every ~40s here) is not a safe place for a break; the login screen is.
                    answerBreakRequest(Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN);
                    if (breakActive()) notePaused();
                    return;
                }
                everLoggedIn = true;
                seenLoginScreenAt = 0;
                // An open world map covers the game view: nothing behind it can be clicked or seen.
                if (Rs2Widget.isWidgetVisible(InterfaceID.Worldmap.CLOSE)) {
                    hbLog.event("RECOVER", "The world map is open; closing it");
                    Rs2Walker.closeWorldMap();
                    return;
                }
                // The welcome screen is LOGGED_IN but swallows camera keys and zoom.
                // Play is clicked here: the client's own handler is slow.
                if (Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY)) {
                    cameraFramed = false;
                    frameDueAt = 0;
                    cameUpFromWelcome = true;
                    idle("the welcome screen");
                    sleep(Rs2Random.betweenInclusive(600, 1500));
                    if (Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY)) {
                        Rs2Widget.clickWidget(InterfaceID.WelcomeScreen.PLAY);
                        sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.WelcomeScreen.PLAY), 3000);
                    }
                    return;
                }
                if (wasLoggedOut) {
                    // Let the trail varbits land and the welcome screen clear, then resume from the game's state.
                    wasLoggedOut = false;
                    sleep(Rs2Random.betweenInclusive(1200, 2600));
                    // The G.E. offer and the walk survive a relog.
                    if (state != HerbiboarState.INITIALIZING && state != HerbiboarState.CHECK_AUTO_RETALIATE
                            && state != HerbiboarState.RESTOCK) {
                        hbLog.event("RECOVER", "Back in game (was " + state + "); resuming from the game's trail state");
                        backToInventory();
                        setState(HerbiboarState.RESET);
                    }
                }
                if (relogPending) {
                    relogPending = false;
                    Rs2Player.logout();
                    // The game refuses a logout in combat: let the same spot ask for a relog again.
                    if (!sleepUntil(() -> !Microbot.isLoggedIn(), 3000)) {
                        relogTriedFor = null;
                        hbLog.event("RECOVER", "The logout for a fresh trail was refused; carrying on");
                    }
                    return;
                }
                if (state != lastPolledState) {
                    lastPolledState = state;
                    clearInFlight();
                    // A break asked for at the bank keeps its opening until it has begun.
                    if (breaksLocked(state) && !breakDue()) BreakHandlerScript.setLockState(true);
                    HerbiboarSession session = herbiboarPlugin.getSession();
                    if (session != null && state == HerbiboarState.BANK) session.bankTrips++;
                    if (state == HerbiboarState.BANK) {
                        notedNext = nextTargetOf(herbiboarPlugin.getFinishId(), herbiboarPlugin.getCurrentPath(),
                                herbiboarPlugin.getEndLocations());
                        hbLog.event("BANK", notedNext != null ? "Banking mid-trail: next was " + notedNext
                                : "Banking after a catch");
                    }
                    startTarget = null; startChosen = false;
                    if (session != null && state == HerbiboarState.RESTOCK) session.restocks++;
                    clearRoute();
                    tunnelSince = null;
                    harvestSince = null;
                    cursorMissTarget = null;
                    cursorMisses = 0;
                    coverTries = 0;
                    walkedCloserFor = null;
                }
                // Any other script's shutdown() resets this global, so re-assert it: teleports only for the G.E. trip.
                Rs2Walker.disableTeleports = state != HerbiboarState.RESTOCK;
                if (!super.run()) {
                    answerBreakRequest(false);
                    notePaused();
                    return;
                }
                // Every pass: the play style can be switched mid-session, and the Antiban panel's slider can move it.
                ActivityIntensity mouse = mouseIntensity(pace().isRelaxed());
                if (Rs2Antiban.getActivityIntensity() != mouse) Rs2Antiban.setActivityIntensity(mouse);
                // Answered before the pause below: "break requested" already counts as an active break, and a
                // handler left without an answer skips the break after 120 seconds.
                answerBreakRequest(false);
                if (pauseForBreak(breakDue(), BreakHandlerScript.isLockState())) {
                    notePaused();
                    return;
                }
                noteRunning();
                if (!cameraFramed) {
                    // Clicks on the login camera miss. Camera moves never cancel the character's action.
                    long now = System.currentTimeMillis();
                    if (frameDueAt == 0) {
                        frameDueAt = now + (cameUpFromWelcome
                                ? Rs2Random.betweenInclusive(1000, 3000) : Rs2Random.betweenInclusive(600, 1500));
                        cameUpFromWelcome = false;
                    }
                    if (now < frameDueAt) {
                        idle("settling in");
                        return;
                    }
                    cameraFramed = true;
                    frameDueAt = 0;
                    frameCamera();
                }
                holdZoom();
                holdPitch();

                if (lastLocation != null && !lastLocation.equals(Rs2Player.getWorldLocation())) {
                    lastMove = Instant.now();
                    lastLocation = Rs2Player.getWorldLocation();
                    stuckMinutes = 0;
                } else if (stuckClockPaused(state, Rs2Bank.isOpen())) {
                    // Time spent here is not standing still: the clock starts again when we leave.
                    lastMove = Instant.now();
                } else if (config.resetIfStuck() && lastMove != null
                        && Instant.now().isAfter(lastMove.plusSeconds(60))) {
                    stuckMinutes++;
                    if (giveUpWhenStuck(stuckMinutes)) {
                        hbLog.problem("PROBLEM", "Stuck in " + state + " at " + Rs2Player.getWorldLocation()
                                + " for " + stuckMinutes + " minutes; logging out");
                        herbiboarPlugin.setStopReason("stuck in " + state + " for " + stuckMinutes + " minutes");
                        logOutAndStop();
                        return;
                    }
                    hbLog.event("RECOVER", "Player has not moved for over 1 minute, resetting script state");
                    lastMove = Instant.now();
                    lastLocation = null;
                    setState(HerbiboarState.RESET);
                } else if (lastMove == null || lastLocation == null) {
                    lastMove = Instant.now();
                    lastLocation = Rs2Player.getWorldLocation();
                }

                // Not with the bank open: that drinks from the bank's inventory view.
                if (inDeadTime() && !Rs2Bank.isOpen() && state != HerbiboarState.RESTOCK) upkeep();
                // An inventory click cancels a queued object click but not a plain walk.
                int energy = Rs2Player.getRunEnergy();
                if (energy <= LOW_ENERGY_DRINK && inFlight == null && !Rs2Player.isAnimating() && !Rs2Bank.isOpen()
                        && state != HerbiboarState.RESTOCK) {
                    manageRunEnergy(config);
                }
                boolean runOn = Rs2Player.isRunEnabled();
                if (wasRunOn && !runOn) hbLog.event("RECOVER", String.format("Run went off at %d%% energy", energy));
                wasRunOn = runOn;
                // The game turns run off at 0 energy, and Rs2Player.toggleRunEnergy refuses below the client's threshold.
                // Clicking the orb never cancels a walk or a queued click.
                if (!runOn && energy >= runBackOnAt) {
                    // String.format: %% is a literal percent.
                    hbLog.event("RECOVER", String.format("Run is off at %d%% energy; turning it back on", Rs2Player.getRunEnergy()));
                    Rs2Widget.clickWidget(InterfaceID.Orbs.RUNBUTTON);
                    sleepUntil(Rs2Player::isRunEnabled, 1200);
                    runBackOnAt = Rs2Random.betweenInclusive(3, 10);
                }

                // Not in TUNNEL/HARVEST: leaving an attacked boar loses the catch.
                if (bankAllowedIn(state) && needsToBank(config)) {
                    hbLog.event("BANK", "Need to bank, switching to BANK state");
                    setState(HerbiboarState.BANK);
                }

                if (needsTravel(state, Rs2Player.getWorldLocation())) {
                    hbLog.event("TRAVEL", "Off Fossil Island at " + Rs2Player.getWorldLocation() + "; travelling there");
                    pendantTried = false;
                    travelFails = 0;
                    setState(HerbiboarState.TRAVEL);
                }

                switch (state) {
                    case RESET:
                        // Inspecting a start rock does not abandon a trail.
                        Microbot.status = "Resuming...";
                        attackedTunnel = false;
                        boarWait = null;
                        huntTunnel = null;
                        preHoverAim = null;
                        lastMove = Instant.now();
                        lastLocation = null;
                        HerbiboarState resume = resumeState(herbiboarPlugin.getFinishId(),
                                herbiboarPlugin.getCurrentGroup() != null);
                        hbLog.detail("START", "Resuming in " + resume + " (finishId="
                                + herbiboarPlugin.getFinishId() + ", path=" + herbiboarPlugin.getCurrentPath().size() + ")");
                        // Set before the hand-off, or a mid-trail break can slip in before the next pass relocks.
                        BreakHandlerScript.setLockState(breaksLocked(resume));
                        setState(resume);
                        break;
                    case INITIALIZING:
                        Microbot.status = "Starting...";
                        hbLog.detail("START", "Initializing...");
                        if (stopIfLevelsTooLow(herbiboarPlugin)) return;
                        setState(HerbiboarState.CHECK_AUTO_RETALIATE);
                        break;
                    case CHECK_AUTO_RETALIATE:
                        Microbot.status = "Checking auto retaliate...";
                        hbLog.detail("START", "Checking auto retaliate...");
                        // Retaliating on a crab drags the player off the trail.
                        if (!Rs2Combat.setAutoRetaliate(false)) {
                            if (outOfTries(++retaliateTries)) {
                                hbLog.problem("PROBLEM", "Could not turn auto retaliate off 3 times; turn it off yourself, then start again");
                                herbiboarPlugin.setStopReason("could not turn auto retaliate off");
                                logOutAndStop();
                                return;
                            }
                            idle("auto retaliate is still on; trying again");
                            break;
                        }
                        retaliateTries = 0;
                        // RESET, not START: after a crash or restart mid-trail, pick the trail or tunnel back up.
                        setState(HerbiboarState.RESET);
                        break;
                    case START:
                        Microbot.status = "Finding start location";
                        if (herbiboarPlugin.getCurrentGroup() == null) {
                            if (!startChosen) {
                                // Re-checked on every pick: a refused rock re-picks here, and a boost can wear off mid-run.
                                if (stopIfLevelsTooLow(herbiboarPlugin)) return;
                                startChosen = true;
                                WorldPoint me = Rs2Player.getWorldLocation();
                                List<WorldPoint> rocks = HerbiHunterPlugin.START_LOCATIONS;
                                int[] costs = new int[rocks.size()];
                                for (int i = 0; i < costs.length; i++) costs[i] = startRouteCost(me, rocks.get(i));
                                startTarget = nearestByRoute(rocks, c -> costs[rocks.indexOf(c)], skipStart);
                                StringBuilder others = new StringBuilder();
                                for (int i = 0; i < costs.length; i++) {
                                    if (!rocks.get(i).equals(startTarget)) {
                                        others.append(others.length() == 0 ? "" : ", ").append(START_NAMES.get(i)).append(' ').append(costs[i]);
                                    }
                                }
                                if (startTarget != null) {
                                    hbLog.detail("START", "Start: " + START_NAMES.get(rocks.indexOf(startTarget)) + " " + startTarget.getX() + "," + startTarget.getY()
                                            + " (route " + costs[rocks.indexOf(startTarget)] + " tiles; " + others + ")");
                                } else {
                                    hbLog.detail("START", "Start: no route to any start rock; nearest loaded by distance");
                                }
                            }
                            TileObject start;
                            if (startTarget != null) {
                                start = herbiboarPlugin.getStarts().get(startTarget);
                            } else {
                                start = herbiboarPlugin.getStarts().values().stream()
                                    .filter(s -> !s.getWorldLocation().equals(skipStart))
                                    .min(Comparator.comparing(s -> Rs2Player.getWorldLocation().distanceTo(s.getWorldLocation())))
                                    .orElse(null);
                                if (start == null) {
                                    showRoute(RETURN_LOCATION);
                                    travelTo(RETURN_LOCATION, 4, () -> Rs2Player.getWorldLocation().distanceTo(RETURN_LOCATION) < 5);
                                    break;
                                }
                            }
                            if (start != null && !warningToggleTried
                                    && Microbot.getVarbitValue(VarbitID.FOSSIL_HERBIBOAR_ALREADY_CAUGHT_IGNORE_WARNING) != 1
                                    && start.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 10) {
                                // Once per session: otherwise a new trail can stop on a confirmation dialog.
                                warningToggleTried = true;
                                model(start).click("Toggle warning");
                                sleepUntil(() -> Microbot.getVarbitValue(VarbitID.FOSSIL_HERBIBOAR_ALREADY_CAUGHT_IGNORE_WARNING) == 1, 3000);
                            }
                            {
                                // A start rock plays no animation; the trail appearing is the proof.
                                WorldPoint rockLoc = startTarget != null ? startTarget : start.getWorldLocation();
                                if (approachAndInteract(start, rockLoc, TRAIL_ACTION,
                                        herbiboarPlugin::getCurrentGroup, config)) {
                                    BreakHandlerScript.setLockState(true);
                                    sleepUntil(() -> herbiboarPlugin.getCurrentGroup() != null, 3000);
                                }
                            }
                        } else {
                            skipStart = null;
                            startTarget = null; startChosen = false;
                            setState(HerbiboarState.TRAIL);
                        }
                        break;
                    case TRAIL:
                        Microbot.status = "Following trail";
                        // First: confusion empties the trail, which every later branch needs.
                        if (checkForConfusionMessage(herbiboarPlugin)) return;
                        if (herbiboarPlugin.getFinishId() > 0) {
                            setState(HerbiboarState.TUNNEL);
                            break; 
                        }
                        List<HerbiboarSearchSpot> path = herbiboarPlugin.getCurrentPath();
                        if (trailIsLost(path.isEmpty(), herbiboarPlugin.getCurrentGroup() == null)) {
                            hbLog.event("RECOVER", "Trail data is gone, searching for a new start");
                            setState(HerbiboarState.START);
                            break;
                        }
                        if (!path.isEmpty()) {
                            int searched = path.size();
                            WorldPoint loc = path.get(searched - 1).getLocation();
                            TileObject object = herbiboarPlugin.getTrailObjects().get(loc);
                            approachAndInteract(object, loc, TRAIL_ACTION,
                                    () -> trailProgress(herbiboarPlugin), config);
                        }
                        break;
                    case TUNNEL:
                        Microbot.status = "Attacking tunnel";
                        int finishId = herbiboarPlugin.getFinishId();
                        List<WorldPoint> endLocations = herbiboarPlugin.getEndLocations();
                        if (validFinishId(finishId, endLocations.size())) {
                            huntTunnel = endLocations.get(finishId - 1);
                        }
                        WorldPoint ourTunnel = huntTunnel;
                        if (herbiboarAt(ourTunnel) != null) {
                            tunnelSince = null;
                            harvestSince = null;
                            setState(HerbiboarState.HARVEST);
                            harvest(config);
                            break;
                        }
                        // Attacked and the trail has ended: the boar is on its way out, so re-clicking only interrupts it.
                        if (attackedTunnel && boarWait != BoarWait.NOTHING) {
                            if (boarWait == BoarWait.MOUSE_AWAY) {
                                if (!glanced) {
                                    glanceAway();
                                    settled();
                                }
                            }
                            else preHoverBoarSpot(ourTunnel);
                        }
                        boolean boarComing = attackedTunnel && finishId == 0;
                        if (boarComing) {
                            if (tunnelSince == null) tunnelSince = Instant.now();
                            if (Instant.now().isAfter(tunnelSince.plusMillis(HARVEST_GIVE_UP_MS))) {
                                hbLog.event("RECOVER", "No herbiboar came out of " + ourTunnel
                                        + ", starting the next trail");
                                tunnelSince = null;
                                attackedTunnel = false;
                                boarWait = null;
                                huntTunnel = null;
                                preHoverAim = null;
                                setState(HerbiboarState.START);
                                break;
                            }
                            idle("waiting for the herbiboar at " + ourTunnel);
                            TileObject emerging = herbiboarPlugin.getTunnels().get(ourTunnel);
                            if (boarWait != BoarWait.MOUSE_AWAY && boarWait != BoarWait.NOTHING
                                    && !preHoverBoarSpot(ourTunnel) && emerging != null && onScreen(emerging)) {
                                hoverOnce(() -> hullOf(emerging), () -> Rs2Camera.getObjectAngle(emerging));
                            }
                            if (sleepUntil(() -> {
                                noteArrival(ourTunnel);
                                return herbiboarAt(ourTunnel) != null;
                            }, 1500)) {
                                tunnelSince = null;
                                setState(HerbiboarState.HARVEST);
                                harvest(config);
                                break;
                            }
                        }
                        if (!boarComing) {
                            if (ourTunnel != null) {
                                final WorldPoint finishLoc = ourTunnel;
                                TileObject tunnel = herbiboarPlugin.getTunnels().get(finishLoc);
                                String action = tunnel != null && hasAction(model(tunnel), "Attack")
                                        ? "Attack" : "Search";
                                if (tunnelSince == null) tunnelSince = Instant.now();
                                if (Instant.now().isAfter(tunnelSince.plusMillis(TUNNEL_GIVE_UP_MS))) {
                                    hbLog.event("RECOVER", "Tunnel at " + finishLoc + " yielded nothing in "
                                            + (TUNNEL_GIVE_UP_MS / 1000) + "s (object "
                                            + (tunnel == null ? "not loaded" : "loaded") + "), attacking it again");
                                    tunnelSince = Instant.now();
                                    attackedTunnel = false;
                                    boarWait = null;
                                    clearInFlight();
                                }
                                // Latch: an in-flight poll returns false and must not un-attack.
                                if (approachAndInteract(tunnel, finishLoc, action,
                                        () -> herbiboarPlugin.getFinishId() == 0 || herbiboarAt(finishLoc) != null, config)) {
                                    attackedTunnel = true;
                                    boarWait = boarWaitChoice(pace().isRelaxed(), paceRng.nextDouble(), pace().preHoverChance());
                                    glanced = false;
                                    tunnelSince = Instant.now();
                                }
                            }
                        }
                        break;
                    case HARVEST:
                        harvest(config);
                        break;
                    case BANK:
                        Microbot.status = "Banking items";

                        if (!Rs2Bank.isOpen() && shouldRestock(config.restock(), hasEnergyItems(config), config.runEnergyOption())
                                && bankedDoses(config.runEnergyOption()) == 0) {
                            hbLog.event("RESTOCK", "Out of " + config.runEnergyOption() + " and the bank has none either; "
                                    + "straight to the G.E.");
                            restockWalkFails = 0;
                            restockStep = RestockStep.COINS;
                            setState(HerbiboarState.RESTOCK);
                            break;
                        }
                        if (!Rs2Bank.isOpen()) {
                            // Via the walker, which plans the hole itself: our own hole/minimap legs freeze here.
                            if (onFossilIsland(Rs2Player.getWorldLocation())) {
                                hbLog.event("BANK", "Walking to the camp bank");
                                showRoute(CAMP_BANK);
                                Rs2Bank.walkToBankAndUseBank(BankLocation.FOSSIL_ISLAND);
                            } else {
                                hbLog.event("BANK", "Off Fossil Island; using the nearest bank");
                                Rs2Bank.walkToBankAndUseBank();
                            }
                            dropWalkerRoute();
                            sleepUntil(Rs2Bank::isOpen, 3000);
                        } else {
                            // A deposit or withdrawal that fails every pass would otherwise repeat for good.
                            if (outOfTries(bankPasses)) {
                                hbLog.problem("PROBLEM", "Banking did not finish in " + bankPasses + " tries ("
                                        + Rs2Inventory.emptySlotCount() + " free slots); logging out");
                                herbiboarPlugin.setStopReason("could not finish banking");
                                Rs2Bank.closeBank();
                                logOutAndStop();
                                return;
                            }
                            bankPasses++;
                            // The one opening for a break: bank open, nothing left hanging.
                            if (!trailLive()) BreakHandlerScript.setLockState(false);
                            withdrawMissingSetup(config);
                            ArrayList<Integer> slotsToLock = new ArrayList<>();
                            if (config.useHerbSack() && Rs2Inventory.contains(HERB_SACKS)) {
                                slotsToLock.add(Rs2Inventory.get(HERB_SACKS).getSlot());
                            }
                            if (config.useMagicSecateurs() && !Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS)
                                    && Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS) ) {
                                slotsToLock.add(Rs2Inventory.get(ItemID.FAIRY_ENCHANTED_SECATEURS).getSlot());
                            }
                            // Lock the sack and secateurs so deposit-all keeps them; the lock options are the player's, put back after.
                            int showOpBefore = Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP);
                            int ignoreLocksBefore = Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS);
                            if (!slotsToLock.isEmpty()) setBankSideLockOptions(1, 0);

                            // Only unlock the slots we lock: toggleAllLocks undoes the player's own locks too.
                            List<Integer> lockedBefore = Rs2Bank.findLockedSlots();
                            slotsToLock.removeAll(lockedBefore);
                            if (!slotsToLock.isEmpty()) {
                                int[] slots = slotsToLock.stream().mapToInt(Integer::intValue).toArray();
                                // lockAllBySlot is true once any slot locks; deposit-all would bank whichever did not.
                                for (int tries = 0; tries < 2 && !slotsToLock.stream().allMatch(Rs2Bank::isLockedSlot); tries++) {
                                    Rs2Bank.lockAllBySlot(slots);
                                    sleepUntil(() -> slotsToLock.stream().allMatch(Rs2Bank::isLockedSlot), 2000);
                                }
                                if (!slotsToLock.stream().allMatch(Rs2Bank::isLockedSlot)) {
                                    hbLog.problem("PROBLEM", "Could not lock the herb sack or secateurs slot; not depositing this pass");
                                    unlockSlots(slotsToLock);
                                    setBankSideLockOptions(showOpBefore, ignoreLocksBefore);
                                    if (outOfTries(++lockFails)) {
                                        hbLog.problem("PROBLEM", "Could not lock the herb sack or magic secateurs in the bank 3 times. "
                                                + "Lock the slot yourself, or turn off Herb sack / Magic secateurs, then start again.");
                                        herbiboarPlugin.setStopReason("could not lock the herb sack or secateurs in the bank");
                                        Rs2Bank.closeBank();
                                        logOutAndStop();
                                        return;
                                    }
                                    break;
                                }
                            }
                            lockFails = 0;

                            boolean sackFirst = config.useHerbSack() && Rs2Inventory.contains(HERB_SACKS) && habits.emptiesSackFirst(paceRng);
                            if (sackFirst) {
                                Rs2Inventory.interact(HERB_SACKS, "Empty");
                                Rs2Inventory.waitForInventoryChanges(1000);
                            }
                            Rs2Widget.clickWidget(InterfaceID.Bankmain.DEPOSITINV);
                            if (!sackFirst && config.useHerbSack() && Rs2Inventory.contains(HERB_SACKS)) {
                                // Empty with the bank open banks the herbs; Rs2Bank.emptyHerbSack misses the silk sack.
                                Rs2Inventory.interact(HERB_SACKS, "Empty");
                                Rs2Inventory.waitForInventoryChanges(1000);
                                Rs2Widget.clickWidget(InterfaceID.Bankmain.DEPOSITINV);
                            }
                            Rs2Inventory.waitForInventoryChanges(1000);
                            sleep(reactionMs());
                            unlockSlots(slotsToLock);
                            setBankSideLockOptions(showOpBefore, ignoreLocksBefore);

                            // Again now there is room: a full inventory refused the first try.
                            withdrawMissingSetup(config);

                            HerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();
                            switch (energyOption) {
                                case STAMINA_POTION:
                                case EXTENDED_STAMINA_POTION:
                                case SUPER_ENERGY_POTION:
                                case EXTREME_ENERGY_POTION:
                                case ENERGY_POTION:
                                    ensureEnergyDoses(energyOption, 24);
                                    break;
                                case STRANGE_FRUIT:
                                    if (Rs2Inventory.count(ItemID.MACRO_TRIFFIDFRUIT) < 6) {
                                        Rs2Bank.withdrawX(ItemID.MACRO_TRIFFIDFRUIT, 6 - Rs2Inventory.count(ItemID.MACRO_TRIFFIDFRUIT));
                                        sleep(reactionMs());
                                    }
                                    break;
                                default:
                                    break;
                            }
                            if (shouldRestock(config.restock(), hasEnergyItems(config), energyOption)) {
                                hbLog.event("RESTOCK", "Bank has no " + energyOption + " left; restocking at the G.E.");
                                restockWalkFails = 0;
                                restockStep = RestockStep.COINS;
                                setState(HerbiboarState.RESTOCK);
                                break;
                            }
                            if (!hasRequiredInventorySetup(config)) {
                                hbLog.event("BANK", "Bank cannot supply the configured setup ("
                                        + energyOption + (config.useHerbSack() ? ", herb sack" : "")
                                        + "); logging out");
                                herbiboarPlugin.setStopReason("bank ran out of supplies");
                                logOutAndStop();
                                return;
                            }
                            if (!needsToBank(config)) {
                                // deposit-all took any pendant carried: the trip still wants one
                                if (!onFossilIsland(Rs2Player.getWorldLocation()) && !Rs2Inventory.contains(DIGSITE_PENDANTS)) {
                                    for (int pendant : DIGSITE_PENDANTS) {
                                        if (Rs2Bank.hasItem(pendant)) {
                                            Rs2Bank.withdrawOne(pendant);
                                            Rs2Inventory.waitForInventoryChanges(1000);
                                            break;
                                        }
                                    }
                                }
                                Rs2Bank.closeBank();
                                sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                                boolean trailLive = trailLive();
                                boolean micro = microBreakHere(trailLive, "at the bank");
                                // The handler looks once a second: a break that is due gets the moment to be asked for.
                                if (!trailLive && breakLooming()) sleepUntil(HerbiboarScript::breakDue, 3000);
                                boolean breakHere = breakStartsAtBank(trailLive, breakDue());
                                BreakHandlerScript.setLockState(!breakHere);
                                if (breakHere && !micro) hbLog.event("BREAK", "A break is starting at the bank");
                                bankPasses = 0;
                                setState(HerbiboarState.RETURN_FROM_ISLAND);
                                // One or the other: a break at the bank already is the pause.
                                long afk = breakHere ? 0 : pace().afkAfterBankMs(paceRng);
                                if (afk > 0) {
                                    idle("taking a moment");
                                    sleep((int) afk);
                                    settled();
                                }
                            }
                        }
                        break;
                    case RESTOCK:
                        Microbot.status = "Restocking at the G.E.";
                        restock(config, herbiboarPlugin);
                        break;
                    case TRAVEL:
                        Microbot.status = "Walking to Fossil Island";
                        travel(herbiboarPlugin);
                        break;
                    case RETURN_FROM_ISLAND:
                        Microbot.status = "Returning to island";
                        HerbiboarState next = resumeState(herbiboarPlugin.getFinishId(),
                                herbiboarPlugin.getCurrentGroup() != null);
                        WorldPoint nowNext = nextTargetOf(herbiboarPlugin.getFinishId(), herbiboarPlugin.getCurrentPath(),
                                herbiboarPlugin.getEndLocations());
                        if (next == HerbiboarState.START) {
                            hbLog.event("BANK", notedNext != null
                                    ? "Back from the bank: trail gone while banking (noted " + notedNext + ")"
                                    : "Back from the bank: START");
                        } else {
                            hbLog.event("BANK", "Back from the bank: " + next + " at " + nowNext
                                    + (notedNext != null ? " (noted " + notedNext + " at the chest)" : ""));
                        }
                        notedNext = null;
                        setState(next);
                        break;
                }
            } catch (Exception ex) {
                // Stopping the plugin interrupts this thread mid client-thread call; not an error.
                if (!isRunning() || ex.getCause() instanceof InterruptedException) return;
                reportError(ex);
            } catch (LinkageError | StackOverflowError err) {
                // Escaping, these end the scheduled task silently: the character stands still with no log line.
                log.error("Herbi Hunter stopped by an error (a client update can cause this)", err);
                hbLog.problem("PROBLEM", "Stopped by an error: " + err);
                herbiboarPlugin.setStopReason("error: " + err);
                stopping = true;
                Microbot.stopPlugin(plugin);
            } finally {
                passRunning.set(false);
            }
        }, 0, 80, TimeUnit.MILLISECONDS); // fast poll; human pacing lives in the reaction pause and mouse moves
        return true;
    }

    enum RestockStep { COINS, TO_GE, BUY, WAIT, TO_CAMP }

    private RestockStep restockStep = RestockStep.COINS;
    // The offer as checked at COINS: kept until it is resolved, whatever the settings do meanwhile.
    private int restockItemId;
    private int restockPrice;
    private int restockAmount;
    private String restockItem;
    private GrandExchangeSlots restockSlot;
    private boolean restockCollecting;
    private long restockOfferAt;
    private int restockWalkFails;
    private static final int RESTOCK_OFFER_TIMEOUT_MS = 180_000;

    enum RestockWait { WAIT, COLLECT, DONE, GONE, TIMED_OUT }

    /** What the WAIT step does with the slot our offer went into. Pure. */
    static RestockWait restockWait(boolean slotHoldsOurOffer, boolean bought, boolean collecting, long sincePlacedMs) {
        // The slot reads empty once collected; just after placing, the offer may not have shown yet.
        if (!slotHoldsOurOffer) return collecting ? RestockWait.DONE : sincePlacedMs > 5000 ? RestockWait.GONE : RestockWait.WAIT;
        if (bought) return RestockWait.COLLECT;
        return sincePlacedMs > RESTOCK_OFFER_TIMEOUT_MS ? RestockWait.TIMED_OUT : RestockWait.WAIT;
    }

    /** The slot still holds the buy this restock placed: same item, amount and price. Pure. */
    static boolean isRestockOffer(int slotItemId, int slotTotal, long slotPrice, boolean selling, int itemId, int amount, int price) {
        return !selling && slotItemId == itemId && slotTotal == amount && slotPrice == price;
    }

    static boolean canAfford(long coins, long cost) {
        return cost > 0 && cost <= Integer.MAX_VALUE && coins >= cost;
    }

    private static int bankedDoses(HerbiboarConfig.RunEnergyOption option) {
        // A snapshot restored from an earlier session can be out of date; only a bank seen this session counts.
        if (Rs2Bank.getBankLiveEpoch() == 0) return -1;
        int[] ids = doseIds(option);
        if (ids.length == 0) return -1;
        int doses = 0;
        for (int i = 0; i < ids.length; i++) doses += (i + 1) * Rs2Bank.count(ids[i]);
        return doses;
    }

    /** Ids by dose, 1 to 4; empty for an item without doses. The one place a potion's ids are listed. */
    static int[] doseIds(HerbiboarConfig.RunEnergyOption option) {
        switch (option) {
            case STAMINA_POTION: return new int[]{ItemID._1DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._4DOSESTAMINA};
            case EXTENDED_STAMINA_POTION: return new int[]{ItemID._1DOSE2STAMINA, ItemID._2DOSE2STAMINA, ItemID._3DOSE2STAMINA, ItemID._4DOSE2STAMINA};
            case EXTREME_ENERGY_POTION: return new int[]{ItemID._1DOSE3ENERGY, ItemID._2DOSE3ENERGY, ItemID._3DOSE3ENERGY, ItemID._4DOSE3ENERGY};
            case SUPER_ENERGY_POTION: return new int[]{ItemID._1DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._4DOSE2ENERGY};
            case ENERGY_POTION: return new int[]{ItemID._1DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._4DOSE1ENERGY};
            default: return new int[0];
        }
    }

    static int restockPrice(long guidePrice) {
        return guidePrice <= 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(guidePrice * 1.10));
    }

    private void restock(HerbiboarConfig config, HerbiHunterPlugin herbiboarPlugin) {
        int potion = fullDoseItemId(config.runEnergyOption());
        // a value saved before the cap could still be above the buy limit
        int amount = Math.min(config.restockAmount(), 2000);
        WorldPoint ge = BankLocation.GRAND_EXCHANGE.getWorldPoint();
        WorldPoint me = Rs2Player.getWorldLocation();
        switch (restockStep) {
            case COINS: {
                // Count from a bank open now: its cache only updates while open, so a closed bank still misses the last restock.
                if (!Rs2Bank.isOpen()) {
                    boolean opened = onFossilIsland(me) ? Rs2Bank.walkToBankAndUseBank(BankLocation.FOSSIL_ISLAND)
                            : Rs2Bank.walkToBankAndUseBank();
                    if (!opened && ++restockWalkFails >= 5) {
                        stopRestock("could not open a bank to count coins and potions (5 tries failed)", herbiboarPlugin);
                    }
                    return;
                }
                if (bankedDoses(config.runEnergyOption()) > 0) {
                    hbLog.event("RESTOCK", "The bank has " + config.runEnergyOption() + " after all; banking instead of buying");
                    setState(HerbiboarState.BANK);
                    return;
                }
                restockItem = Microbot.getClientThread().runOnClientThreadOptional(
                        () -> Microbot.getClient().getItemDefinition(potion).getName()).orElse(null);
                // ItemManager reads the item definition: client thread only.
                restockPrice = restockPrice(Microbot.getClientThread().runOnClientThreadOptional(
                        () -> Microbot.getItemManager().getItemPriceWithSource(potion, false)).orElse(0L));
                if (restockItem == null || restockPrice <= 0) {
                    stopRestock("no G.E. price for " + restockItem + " (" + potion + ")", herbiboarPlugin);
                    return;
                }
                long cost = (long) restockPrice * amount;
                long coins = Rs2Inventory.itemQuantity(ItemID.COINS) + (long) Rs2Bank.count(ItemID.COINS);
                if (!canAfford(coins, cost)) {
                    stopRestock("need " + cost + " coins for " + amount + " " + restockItem + " at " + restockPrice
                            + " each, have " + coins + " (inventory + bank)", herbiboarPlugin);
                    return;
                }
                restockItemId = potion;
                restockAmount = amount;
                // No withdrawal: a G.E. buy offer takes the coins from the bank itself (inventory coins first).
                hbLog.event("RESTOCK", "Restock: " + amount + " " + restockItem + " at " + restockPrice + " ("
                        + cost + " coins of " + coins + "); walking to the G.E.");
                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
                }
                restockWalkFails = 0;
                bankPasses = 0;
                restockStep = RestockStep.TO_GE;
                return;
            }
            case TO_GE:
                if (me != null && me.distanceTo(ge) <= 8) {
                    restockWalkFails = 0;
                    restockLegSince = 0;
                    restockStep = RestockStep.BUY;
                    return;
                }
                if (restockLegTimedOut()) {
                    stopRestock("did not reach the G.E. in " + RESTOCK_LEG_MS / 60_000 + " minutes (at " + me + ")", herbiboarPlugin);
                    return;
                }
                if (!Rs2Walker.walkTo(ge, 5) && ++restockWalkFails >= 5) {
                    stopRestock("could not reach the G.E. (5 walks failed, at " + me + ")", herbiboarPlugin);
                }
                return;
            case BUY:
                if (Rs2GrandExchange.isOpen() || Rs2GrandExchange.openExchange()) {
                    // buyItem fills the first free slot
                    restockSlot = Rs2GrandExchange.getAvailableSlot();
                    // The other offers are the player's: collecting or cancelling them is not ours to do.
                    if (restockSlot == null) {
                        stopRestock("every G.E. slot is in use; free one and start again", herbiboarPlugin);
                        return;
                    }
                    if (Rs2GrandExchange.buyItem(restockItem, restockPrice, restockAmount)) {
                        restockWalkFails = 0;
                        restockCollecting = false;
                        restockOfferAt = System.currentTimeMillis();
                        restockStep = RestockStep.WAIT;
                        return;
                    }
                }
                if (++restockWalkFails >= 5) stopRestock("could not place the G.E. offer (5 tries failed)", herbiboarPlugin);
                return;
            case WAIT: {
                GrandExchangeOfferDetails offer = Rs2GrandExchange.getOfferDetails(restockSlot);
                boolean ours = offer != null && isRestockOffer(offer.getItemId(), offer.getTotalQuantity(), offer.getPrice(),
                        offer.isSelling(), restockItemId, restockAmount, restockPrice);
                switch (restockWait(ours, ours && offer.getState() == GrandExchangeOfferState.BOUGHT, restockCollecting,
                        System.currentTimeMillis() - restockOfferAt)) {
                    case COLLECT:
                        if ((Rs2GrandExchange.isOpen() || Rs2GrandExchange.openExchange())
                                && Rs2GrandExchange.collectOffer(restockSlot, true)) {
                            restockCollecting = true;
                            sleepUntil(() -> Rs2GrandExchange.getOfferDetails(restockSlot) == null, 5000);
                        }
                        if (Rs2GrandExchange.getOfferDetails(restockSlot) != null && ++restockWalkFails >= 5) {
                            stopRestock("could not collect the " + restockAmount + " " + restockItem + " (5 tries failed)", herbiboarPlugin);
                        }
                        return;
                    case DONE:
                        Rs2GrandExchange.closeExchange();
                        hbLog.event("RESTOCK", "Restock: bought " + restockAmount + " " + restockItem + "; back to Fossil Island");
                        microBreakHere(trailLive(), "at the G.E.");
                        restockWalkFails = 0;
                        restockStep = RestockStep.TO_CAMP;
                        return;
                    case GONE:
                        stopRestock("the offer for " + restockAmount + " " + restockItem + " left " + restockSlot
                                + " without being collected here", herbiboarPlugin);
                        return;
                    case TIMED_OUT:
                        Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(restockSlot), true);
                        stopRestock("the offer for " + restockAmount + " " + restockItem + " at " + restockPrice
                                + " did not fill in " + (RESTOCK_OFFER_TIMEOUT_MS / 60000) + " minutes", herbiboarPlugin);
                        return;
                    default:
                        return;
                }
            }
            case TO_CAMP:
                if (me != null && me.distanceTo(CAMP_BANK) <= 5) {
                    restockLegSince = 0;
                    setState(HerbiboarState.BANK);
                    return;
                }
                if (restockLegTimedOut()) {
                    stopRestock("did not get back to the camp bank in " + RESTOCK_LEG_MS / 60_000 + " minutes (at " + me + ")", herbiboarPlugin);
                    return;
                }
                if (!Rs2Walker.walkTo(CAMP_BANK, 3) && ++restockWalkFails >= 5) {
                    stopRestock("could not get back to the Fossil Island camp bank (at " + me + ")", herbiboarPlugin);
                }
                return;
        }
    }

    private Boolean savedMushtrees;
    private boolean pendantTried;
    private int travelFails;
    private long pauseGenSeen = -1;
    private long lastPassAt;
    /** When the current walk to or from the G.E. began; a walk that never arrives gives up after RESTOCK_LEG_MS. */
    private long restockLegSince;
    private static final long RESTOCK_LEG_MS = 8 * 60_000L;

    private void travel(HerbiHunterPlugin herbiboarPlugin) {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (onFossilIsland(me)) {
            hbLog.event("TRAVEL", "On Fossil Island at " + me);
            setState(HerbiboarState.START);
            return;
        }
        if (!pendantTried && Rs2Inventory.contains(DIGSITE_PENDANTS) && pendantHelps(me)) {
            pendantTried = true;
            rubPendant(me);
            return;
        }
        if (!Rs2Walker.walkTo(CAMP_BANK, 3) && ++travelFails >= MAX_TRAVEL_FAILS) {
            hbLog.problem("PROBLEM", "Could not reach Fossil Island from " + me + "; logging out");
            herbiboarPlugin.setStopReason("could not reach Fossil Island");
            logOutAndStop();
        }
    }

    /** The Fossil Island option needs the House on the Hill machine. */
    private void rubPendant(WorldPoint from) {
        sleep(reactionMs());
        if (!Rs2Inventory.interact(DIGSITE_PENDANTS, "Rub") || !sleepUntil(Rs2Dialogue::hasSelectAnOption, 2400)) return;
        String where = Rs2Dialogue.hasDialogueOption("Fossil Island") ? "Fossil Island" : "Digsite";
        sleep(reactionMs());
        if (Rs2Dialogue.clickOption(where)) {
            hbLog.event("TRAVEL", "Digsite pendant: " + where);
            sleepUntil(() -> {
                WorldPoint now = Rs2Player.getWorldLocation();
                return now != null && now.distanceTo(from) > PENDANT_MIN_TILES;
            }, 6000);
        }
    }

    /** RESTOCK is outside the stuck clock, so each walk leg carries its own deadline. */
    private boolean restockLegTimedOut() {
        long now = System.currentTimeMillis();
        if (restockLegSince == 0) restockLegSince = now;
        return now - restockLegSince > RESTOCK_LEG_MS;
    }

    private void stopRestock(String why, HerbiHunterPlugin herbiboarPlugin) {
        hbLog.problem("PROBLEM", "Restock failed: " + why + "; logging out");
        herbiboarPlugin.setStopReason("restock failed: " + why);
        if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
        logOutAndStop();
    }

    private String lastErrorKey;
    private long lastErrorAt;
    private int repeatedErrors;

    private void reportError(Exception ex) {
        StackTraceElement at = ex.getStackTrace().length > 0 ? ex.getStackTrace()[0] : null;
        String key = ex.getClass().getName() + ": " + ex.getMessage() + " @ " + at;
        long now = System.currentTimeMillis();
        if (!key.equals(lastErrorKey)) {
            lastErrorKey = key;
            lastErrorAt = now;
            repeatedErrors = 0;
            log.error("Herbiboar script error", ex);
            return;
        }
        repeatedErrors++;
        if (now - lastErrorAt >= 60_000) {
            log.error("Herbiboar script error repeated {} more times in the last minute: {}", repeatedErrors, key);
            lastErrorAt = now;
            repeatedErrors = 0;
        }
    }

    private long boarSeenAt;
    private long harvestableAt;

    private void harvest(HerbiboarConfig config) {
        Microbot.status = "Harvesting herbiboar";
        Rs2NpcModel out = herbiboarAt(huntTunnel);
        if (out == null) {
            if (harvestSince == null) harvestSince = Instant.now();
            if (Instant.now().isAfter(harvestSince.plusMillis(HARVEST_GIVE_UP_MS))) {
                hbLog.event("RECOVER", "No herbiboar at " + huntTunnel + " for "
                        + (HARVEST_GIVE_UP_MS / 1000) + "s, starting the next trail");
                harvestSince = null;
                attackedTunnel = false;
                boarWait = null;
                huntTunnel = null;
                preHoverAim = null;
                tunnelSince = null;
                boarSeenAt = 0;
                harvestableAt = 0;
                setState(HerbiboarState.START);
            } else {
                idle("no herbiboar within " + HERBIBOAR_TUNNEL_RADIUS + " of tunnel " + huntTunnel);
            }
            return;
        }
        harvestSince = null;
        if (boarSeenAt == 0) {
            boarSeenAt = System.currentTimeMillis();
            stepTimer.landed(boarSeenAt, "BOAR");
            hbLog.detail("T", "BOAR gap=" + (arrivedAtTunnelMs == 0 ? "none" : (boarSeenAt - arrivedAtTunnelMs) + "ms"));
            arrivedAtTunnelMs = 0;
        }
        if (!clickWhenVisible(Rs2Camera.getCharacterAngle(out), () -> hullOf(out), huntTunnel)) return;
        if (!canHarvest(out)) {
            // The emerging boar slides ~1.2s, then the game swaps in a new NPC on that spot which offers Harvest.
            boolean parked = preHoverAim != null && Microbot.getMouse().getMousePosition().distance(preHoverAim) <= PRE_HOVER_NEAR_PX;
            if (!parked) hoverOnce(() -> hullOf(out), () -> Rs2Camera.getCharacterAngle(out));
            WorldPoint tunnel = huntTunnel;
            sleepUntil(() -> harvestableBoarAt(tunnel) != null, HARVEST_READY_TIMEOUT_MS);
        }
        Rs2NpcModel herb = canHarvest(out) ? out : harvestableBoarAt(huntTunnel);
        if (herb != null) {
            if (huntTunnel != null) boarTiles.putIfAbsent(huntTunnel, herb.getWorldLocation());
            if (preHoverAim != null) {
                Shape h = hullOf(herb);
                if (h != null) {
                    Rectangle b = h.getBounds();
                    hbLog.detail("HOVER", String.format("Boar spot: aimed %d,%d; boar outline %d,%d %dx%d (aim %s it)", preHoverAim.x,
                            preHoverAim.y, b.x, b.y, b.width, b.height, h.contains(preHoverAim) ? "inside" : "outside"));
                }
                preHoverAim = null;
            }
            if (harvestableAt == 0) {
                harvestableAt = System.currentTimeMillis();
                // See Harvest first: a parked cursor would click a fixed ~185 ms after it shows.
                long seen = reactionMs() - (System.currentTimeMillis() - harvestableAt);
                if (seen > 0) sleep((int) seen);
            }
            if (!hoverOnce(() -> hullOf(herb), () -> Rs2Camera.getCharacterAngle(herb))) {
                idle("aiming at the herbiboar");
                return;
            }
            if (!clickUnderCursor("Harvest", herb.getIndex(), () -> hullOf(herb), () -> herb.click("Harvest"), herb.getWorldLocation(),
                    () -> Rs2Camera.getCharacterAngle(herb))) return;
            logStep("click");
            harvestClickMs = System.currentTimeMillis() - boarSeenAt;
            hbLog.detail("HARVEST", "Harvest clicked " + harvestClickMs + "ms after the herbiboar appeared ("
                    + (System.currentTimeMillis() - harvestableAt) + "ms after Harvest was offered)");
            boarSeenAt = 0;
            harvestableAt = 0;
            // The boar despawning is the sooner, surer signal than the animation.
            int harvested = herb.getIndex();
            boolean gone = sleepUntil(() -> Microbot.getRs2NpcCache().query().withName("Herbiboar")
                    .toListOnClientThread().stream().noneMatch(n -> n.getIndex() == harvested), 5000);
            if (!gone) return;
            herbiCaught++;
            stepTimer.landed(System.currentTimeMillis(), "CAUGHT");
            HerbiboarSession session = plugin.getSession();
            if (session != null) session.recordCatch(System.currentTimeMillis());
            hbLog.caught(System.currentTimeMillis(), harvestClickMs);
            harvestClickMs = 0;
            // Checked here, after the harvest: that is where the XP lands and no boar is left waiting.
            if (logOutAt99(config.logoutAt99(), session == null ? 99 : session.startLevel,
                    Microbot.getClient().getRealSkillLevel(Skill.HUNTER))) {
                hbLog.event("RUN", "Reached 99 Hunter; logging out");
                plugin.setStopReason("reached 99 Hunter");
                logOutAndStop();
                return;
            }
            tunnelSince = null;
            attackedTunnel = false;
            boarWait = null;
            huntTunnel = null;
            preHoverAim = null;
            boolean forBreak = bankForBreak(breakLooming(), System.currentTimeMillis() - lastBreakBankAt);
            if (forBreak) {
                lastBreakBankAt = System.currentTimeMillis();
                hbLog.event("BREAK", "A break is due: banking first, so it starts at the bank");
            }
            if (needsToBank(config) || bankAtCatch(Rs2Inventory.emptySlotCount(), config.useHerbSack()) || forBreak) {
                setState(HerbiboarState.BANK);
            } else {
                setState(HerbiboarState.START);
            }
            if (distractionAllowed(state, false)) afterCatchDistraction();
        }
    }

    private boolean checkForConfusionMessage(HerbiHunterPlugin plugin) {
        if (!plugin.consumeTrailConfused()) return false;
        confusedTrails++;
        HerbiboarSession session = plugin.getSession();
        if (session != null) session.confusions++;
        hbLog.event("RECOVER", "The herbiboar confused us; trail lost (" + confusedTrails
                + " this session, " + herbiCaught + " caught)");
        setState(HerbiboarState.START);
        attackedTunnel = false;
        boarWait = null;
        huntTunnel = null;
        preHoverAim = null;
        tunnelSince = null;
        return true;
    }

    @Override
    public void shutdown() {
        stopping = true;
        if (savedMushtrees != null) {
            Microbot.getConfigManager().setConfiguration(WALKER_CONFIG, MUSHTREES, savedMushtrees);
            savedMushtrees = null;
        }
        if (savedAutoRun != null) {
            Microbot.enableAutoRunOn = savedAutoRun;
            Microbot.useStaminaPotsIfNeeded = savedAutoStamina;
            Microbot.getConfigManager().setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyEnableAutoRunOn, savedAutoRun);
            Microbot.getConfigManager().setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyUseStaminaPotsIfNeeded, savedAutoStamina);
            savedAutoRun = null;
        }
        Microbot.status = "IDLE";
        // Closing an unready handle cancels a pending request instead of claiming we are ready.
        if (breakPrep != null) {
            breakPrep.close();
            breakPrep = null;
        }
        resetHerbisCaught();
        BreakHandlerScript.setLockState(false);
        // Antiban settings are process-wide statics: the user's own values go back after the reset, which clears them.
        Rs2Antiban.resetAntibanSettings();
        if (savedMoveMouseRandomly != null) {
            Rs2AntibanSettings.moveMouseRandomly = savedMoveMouseRandomly;
            Rs2AntibanSettings.moveMouseOffScreen = savedMoveMouseOffScreen;
            Rs2AntibanSettings.simulateMistakes = savedSimulateMistakes;
            Rs2AntibanSettings.takeMicroBreaks = savedTakeMicroBreaks;
            savedMoveMouseRandomly = null;
        }
        super.shutdown();
    }

    /** Waits up to maxMs for a pass already running to return. Not on the client thread: that pass may be waiting on it. */
    void awaitPassEnd(long maxMs) {
        if (Microbot.getClient().isClientThread()) return;
        long until = System.currentTimeMillis() + maxMs;
        while (passRunning.get() && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
