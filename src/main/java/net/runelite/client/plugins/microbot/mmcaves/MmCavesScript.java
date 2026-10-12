package net.runelite.client.plugins.microbot.mmcaves;

import com.google.common.collect.Table;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mmcaves.enums.CombatStyle;
import net.runelite.client.plugins.microbot.mmcaves.enums.DungeonRoute;
import net.runelite.client.plugins.microbot.mmcaves.enums.Mode;
import net.runelite.client.plugins.microbot.mmcaves.enums.State;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Skill;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

import javax.inject.Inject;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MmCavesScript extends Script {
    private final MmCavesPlugin plugin;

    @Inject
    public MmCavesScript(MmCavesPlugin plugin) {
        this.plugin = plugin;
    }

    private MmCavesConfig config;
    private DungeonRoute route;
    private Mode mode;
    public static volatile State state = State.WALK_TO_ENTRANCE;
    public Instant startTime;
    public static volatile long lastAggroResetTime = System.currentTimeMillis();
    private long lastAttackTime = System.currentTimeMillis();
    private long lastDivinePotionAttempt = 0;
    private long lastBassEatAttempt = 0;
    private long lastStackStepTime = System.currentTimeMillis();
    private volatile boolean stackReady = false;
    private volatile MmCavesDecisions.StackCounts latestStackCounts;
    private Rs2PrayerEnum ownedRangedPrayer;
    private int readySamples = 0;
    private int unreadySamples = 0;
    private final MmCavesHoverTracker hoverTracker = new MmCavesHoverTracker();
    private volatile int routeWaypointIndex;
    private long lastRouteProgressTime;
    private long lastRouteClickTime;

    void onClientTick() {
        if (state != State.FIGHT) {
            hoverTracker.stop();
            return;
        }
        hoverTracker.onClientTick();
    }

    void stopHoverTracking() {
        hoverTracker.stop();
    }

    int routeWaypointIndex() { return routeWaypointIndex; }
    boolean stackReady() { return stackReady; }
    MmCavesDecisions.StackCounts stackCounts() { return latestStackCounts; }
    boolean hasStartedFight() { return firstFightStarted; }

    void releaseRangedPrayer() {
        if (ownedRangedPrayer != null && Microbot.isLoggedIn()) {
            Rs2Prayer.toggle(ownedRangedPrayer, false);
        }
        ownedRangedPrayer = null;
    }

    private void updateRangedPrayer() {
        if (state != State.FIGHT || mode != Mode.RANGE) {
            releaseRangedPrayer();
            return;
        }
        Rs2PrayerEnum prayer = Rs2Prayer.isRigourUnlocked() ? Rs2PrayerEnum.RIGOUR
                : Rs2Player.getRealSkillLevel(Skill.PRAYER) >= 44 ? Rs2PrayerEnum.EAGLE_EYE : null;
        if (prayer == null) return;
        if (ownedRangedPrayer != null && ownedRangedPrayer != prayer) releaseRangedPrayer();
        if (!Rs2Prayer.isPrayerActive(prayer)) {
            if (Rs2Prayer.toggle(prayer, true)) ownedRangedPrayer = prayer;
        }
    }

    public void setConfig(MmCavesConfig config) {
        this.config = config;
        this.route = config.dungeonRoute();
        this.mode = config.combatStyle() == CombatStyle.RANGING ? Mode.RANGE : Mode.MAGIC;
    }

    private static final WorldPoint DUNGEON_ENTRANCE = new WorldPoint(2715, 2788, 0);
    private static final WorldPoint JUNGLE_GRASS_TILE = new WorldPoint(2714, 2788, 0);
    private static final int JUNGLE_GRASS_ID = 28810;
    private static final int ROUTE_STALL_MS = 15000;
    // The recorder shows clicks into the wall, with the player stopping one tile short.
    private final WorldPoint FIGHTING_TILE_A = new WorldPoint(2449, 9172, 1);
    private final WorldPoint FIGHTING_TILE_B = new WorldPoint(2448, 9173, 1);
    private final WorldPoint WALL_CLICK_TO_A = new WorldPoint(2450, 9173, 1);
    private final WorldPoint WALL_CLICK_TO_B = new WorldPoint(2448, 9174, 1);
    private static final int BASS_ID = 365;
    private final WorldPoint AGGRO_RESET_TILE = new WorldPoint(2414, 9164, 1);
    private final WorldPoint EXIT_TILE = new WorldPoint(2381, 9168, 1);

    private final int cavesUpstairs = 10383;
    private final long AGGRO_RESET_COOLDOWN = 10 * 60 * 1000; // RESET EVERY 10 MINUTES

    private volatile boolean firstFightStarted = false;
    private boolean resettingAggro = false;
    public boolean caveIsEmpty = false;

    @Override
    public boolean run() {
        state = State.WALK_TO_ENTRANCE;
        if (!route.isMapped()) {
            Microbot.log("MM Caves " + route + " has no recorded navigation yet; stopping without moving");
            scheduledExecutorService.schedule(() -> Microbot.stopPlugin(plugin), 0, TimeUnit.MILLISECONDS);
            return false;
        }
        firstFightStarted = false;
        resettingAggro = false;
        caveIsEmpty = false;
        lastAggroResetTime = System.currentTimeMillis();
        lastAttackTime = System.currentTimeMillis();
        lastDivinePotionAttempt = 0;
        lastBassEatAttempt = 0;
        lastStackStepTime = System.currentTimeMillis();
        stackReady = false;
        latestStackCounts = null;
        readySamples = 0;
        unreadySamples = 0;
        routeWaypointIndex = 0;
        lastRouteProgressTime = System.currentTimeMillis();
        lastRouteClickTime = 0;
        hoverTracker.stop();
        Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (plugin.getMyWorldPoint() == null) return;

                // Protect before navigation/state checks, including a fall off the upper route.
                // These region checks intentionally cover both planes of the dungeon.
                if (isUpperDungeon() || isDownstairs()) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                }

                // Check if player has light source
                boolean hasLightSource = MmCavesDecisions.hasLightSource(
                        Rs2Inventory::contains,
                        name -> Rs2Equipment.isWearing(name, true));
                if (!hasLightSource) {
                    Microbot.log("Player does NOT have a light source");
                    stopAndLog();
                    return;
                }
                state = getState();
                updateRangedPrayer();
                switch (state) {
                    case WALK_TO_ENTRANCE:
                        handleWalkToEntrance();
                        break;
                    case ENTER_DUNGEON:
                        handleEnterDungeon();
                        break;
                    case FOLLOW_ROUTE:
                        handleFollowRoute();
                        break;
                    case CHECK_EMPTY_CAVE:
                        handleCheckEmptyCave();
                        break;
                    case WORLD_HOP:
                        handleWorldHop();
                        break;
                    case ENTER_CAVE:
                        handleEnterCave();
                        break;
                    case WALK_TO_FIGHT_SPOT:
                        handleWalkToFightSpot();
                        break;
                    case FIGHT:
                        handleFight();
                        break;
                    case RESET_AGGRO:
                        handleAggroReset();
                        break;
                    case BANK:
                        handleBanking();
                        break;
                    case STOP:
                        stopAndLog();
                        break;
                    case IDLE:
                        Microbot.log("Idle...");
                        sleep(Rs2Random.between(300, 800));
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Error in MM Caves script: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private State getState() {
        if (mode == Mode.RANGE) {
            boolean wearingChin = Rs2Equipment.items().stream()
                    .anyMatch(name -> name.getName().toLowerCase().contains("chinchompa"));
            if (!wearingChin) {
                Microbot.log("Player does not have any chinchompa's equipped");
                return State.STOP;
            }
        }

        if (mode == Mode.MAGIC) {
            boolean hasRequiredRunes = Rs2Magic.hasRequiredRunes(config.magicSpell().getSpell());
            boolean onAncientSpellbook = Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT);
            if (MmCavesDecisions.magicSuppliesMissing(hasRequiredRunes, onAncientSpellbook)) {
                if (!onAncientSpellbook) {
                    Microbot.log("Ancient spellbook is required for " + config.magicSpell().getSpell().getName());
                }
                if (!hasRequiredRunes) {
                    Microbot.log("Player does not have enough runes to cast selected spell: " + config.magicSpell().getSpell().getName());

                    Map<Runes, Integer> missingRunes = Rs2Magic.getMissingRunes(config.magicSpell().getSpell());
                    if (!missingRunes.isEmpty()) {
                        missingRunes.forEach((rune, amount) ->
                                Microbot.log("Missing " + amount + " x " + rune.name()));
                    }
                }

                return State.STOP;
            }
            if (config.shouldAutoCast()) {
                if (Rs2Magic.getCurrentAutoCastSpell() != config.magicSpell().getSpell()) {
                    Rs2Combat.setAutoCastSpell(config.magicSpell().getSpell(), false);
                    sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == config.magicSpell().getSpell(), 5000);
                }
            }
        }

        // We need to stop the script if the inventory has no prayer potions
        if (!Rs2Inventory.all().stream().anyMatch(name -> name.getName().toLowerCase().contains("prayer potion"))) return State.STOP;

        if (!isDownstairs()) {
            WorldPoint location = plugin.getMyWorldPoint();
            if (!isUpperDungeon()) {
                return location.distanceTo(DUNGEON_ENTRANCE) <= 2
                        ? State.ENTER_DUNGEON : State.WALK_TO_ENTRANCE;
            }
            if (location.distanceTo(route.checkTile()) > 2) return State.FOLLOW_ROUTE;
            if (!plugin.isWorldChecked(Microbot.getClient().getWorld())) return State.CHECK_EMPTY_CAVE;
            return caveIsEmpty ? State.ENTER_CAVE : State.WORLD_HOP;
        }

        // If downstairs but not close to fighting spot -> walk to tile A
        if (
                isDownstairs() &&
                plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A ) > 5 &&
                plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_B ) > 5 &&
                !resettingAggro
        ) return State.WALK_TO_FIGHT_SPOT;

        // Lost aggro
        if (
                firstFightStarted &&
                System.currentTimeMillis() - lastAggroResetTime > AGGRO_RESET_COOLDOWN
        ) {
            resettingAggro = true;
            lastAggroResetTime = System.currentTimeMillis();
            stackReady = false;
            readySamples = 0;
            unreadySamples = 0;
            hoverTracker.stop();
            return State.RESET_AGGRO;
        }

        return State.FIGHT;
    }

    private boolean isUpperDungeon() {
        WorldPoint location = plugin.getMyWorldPoint();
        if (location == null || location.getPlane() != 1) return false;
        int region = location.getRegionID();
        return region == 10126 || region == 10127 || region == 10383;
    }

    private void handleWalkToEntrance() {
        Microbot.log("Walking to Kruk's Dungeon entrance");
        Rs2Walker.walkTo(DUNGEON_ENTRANCE, 2);
        sleepUntil(() -> plugin.getMyWorldPoint() != null
                && plugin.getMyWorldPoint().distanceTo(DUNGEON_ENTRANCE) <= 2, 1000);
    }

    private void handleEnterDungeon() {
        // Resume an entry confirmation already open when the plugin starts.
        if (hasDungeonEntryConfirmation()) {
            confirmDungeonEntry();
            return;
        }
        Rs2TileObjectModel grass = Microbot.getRs2TileObjectCache().query()
                .withId(JUNGLE_GRASS_ID).nearest();
        if (grass == null || grass.getWorldLocation().distanceTo(JUNGLE_GRASS_TILE) > 1) {
            grass = Microbot.getRs2TileObjectCache().query().withName("Jungle Grass").nearest();
        }
        if (grass == null || grass.getWorldLocation().distanceTo(JUNGLE_GRASS_TILE) > 1) {
            Microbot.log("Jungle Grass entrance not found at the recorded position");
            return;
        }
        grass.click("Investigate");
        sleepUntil(() -> isUpperDungeon() || hasDungeonEntryConfirmation(), 7000);
        confirmDungeonEntry();
    }

    private boolean hasDungeonEntryConfirmation() {
        return Rs2Dialogue.hasDialogueOptionTitle("Climb into the secret entrance?", true)
                && Rs2Dialogue.hasDialogueOption("Yes", true);
    }

    private void confirmDungeonEntry() {
        if (hasDungeonEntryConfirmation()) {
            Rs2Dialogue.clickOption("Yes", true);
        }
        if (sleepUntil(this::isUpperDungeon, 7000)) {
            routeWaypointIndex = 0;
            lastRouteProgressTime = System.currentTimeMillis();
        } else {
            Microbot.log("Kruk's Dungeon entry was not confirmed");
        }
    }

    private void handleFollowRoute() {
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        WorldPoint current = plugin.getMyWorldPoint();
        List<WorldPoint> waypoints = route.waypoints();

        if (routeWaypointIndex == 0 && current.distanceTo(waypoints.get(0)) > 2) {
            // A restart may occur partway along the route; only resume at an observed waypoint.
            int resume = -1;
            for (int i = 1; i < waypoints.size(); i++) {
                if (current.distanceTo(waypoints.get(i)) <= 1) {
                    resume = i;
                    break;
                }
            }
            if (resume < 0) {
                Microbot.log("Cannot safely join " + route + " from " + current + "; stop and return to its entrance");
                Microbot.stopPlugin(plugin);
                return;
            }
            routeWaypointIndex = resume;
        }

        while (routeWaypointIndex < waypoints.size()
                && (route.isObstacleWaypoint(routeWaypointIndex)
                    ? current.equals(waypoints.get(routeWaypointIndex)) && !Rs2Player.isAnimating()
                    : current.distanceTo(waypoints.get(routeWaypointIndex)) <= 1)) {
            routeWaypointIndex++;
            lastRouteProgressTime = System.currentTimeMillis();
        }
        if (routeWaypointIndex >= waypoints.size()) return;

        long now = System.currentTimeMillis();
        if (now - lastRouteProgressTime > ROUTE_STALL_MS) {
            Microbot.log("Stopped: " + route + " stalled before waypoint " + (routeWaypointIndex + 1)
                    + " at " + waypoints.get(routeWaypointIndex));
            Microbot.stopPlugin(plugin);
            return;
        }
        if (now - lastRouteClickTime >= 1800) {
            WorldPoint padTile = route.pressurePadForWaypoint(routeWaypointIndex);
            if (padTile != null) {
                crossRouteObstacle(28758, "Pass", padTile, waypoints.get(routeWaypointIndex));
                return;
            }
            WorldPoint holeTile = route.squeezeHoleForWaypoint(routeWaypointIndex);
            if (holeTile != null) {
                crossRouteObstacle(28764, "Squeeze-through", holeTile, waypoints.get(routeWaypointIndex));
                return;
            }
            if (Rs2Walker.walkFastCanvas(waypoints.get(routeWaypointIndex), true)) {
                lastRouteClickTime = now;
            }
        }
    }

    private void crossRouteObstacle(int objectId, String action, WorldPoint objectTile, WorldPoint landing) {
        if (Rs2Player.isAnimating()) return;
        Rs2TileObjectModel obstacle = Microbot.getRs2TileObjectCache().query()
                .withId(objectId)
                .where(object -> objectTile.equals(object.getWorldLocation()))
                .nearest();
        if (obstacle == null) {
            Microbot.log("Route obstacle " + objectId + " not loaded at " + objectTile + "; waiting");
            lastRouteClickTime = System.currentTimeMillis();
            return;
        }
        lastRouteClickTime = System.currentTimeMillis();
        if (!obstacle.click(action)) return;
        if (sleepUntil(() -> landing.equals(plugin.getMyWorldPoint()) && !Rs2Player.isAnimating(), 7000)) {
            routeWaypointIndex++;
            lastRouteProgressTime = System.currentTimeMillis();
        } else {
            Microbot.log(action + " crossing not confirmed at " + objectTile);
        }
    }

    private void handleCheckEmptyCave() {
        Microbot.log("Checking if cave is empty");
        Rs2TileObjectModel hole = Microbot.getRs2TileObjectCache().query().withId(route.holeId()).nearest();
        if (hole != null) {
            hole.click("Look-in");
            sleepUntil(() -> caveIsEmpty, 3000);
        }
    }

    private void handleWorldHop() {
        Microbot.log("Need to world hop");
        int nextWorld = MmCavesDecisions.selectUncheckedWorld(
                () -> Login.getRandomWorld(true, null), plugin.getCheckedWorlds(), 64);

        if (nextWorld < 0) {
            Microbot.log("No unchecked world found after 64 selections; stopping MM Caves");
            Microbot.stopPlugin(plugin);
            return;
        }

        Microbot.hopToWorld(nextWorld);
        final int targetWorld = nextWorld;
        boolean arrived = sleepUntil(() -> Microbot.isLoggedIn()
                && Microbot.getClient().getWorld() == targetWorld, 15000);
        if (!arrived) {
            Microbot.log("World hop to " + targetWorld + " was not confirmed");
        }
    }

    private void handleEnterCave() {
        Microbot.log("Entering cave...");
        Rs2TileObjectModel hole = Microbot.getRs2TileObjectCache().query().withId(route.holeId()).nearest();
        if (hole != null) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);

            hole.click("Enter");
            if (!sleepUntil(this::isDownstairs, 5000)) {
                Microbot.log("Cave entry was not confirmed");
            }
        }
    }

    private void handleWalkToFightSpot() {
        Microbot.log("Walking to fight spot...");
        sleep(Rs2Random.between(300, 600));
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        if (plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A) < 8) {
            Microbot.log("Walking Fast Canvas");
            Rs2Walker.walkFastCanvas(FIGHTING_TILE_A, true);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A) == 0, 1000);
        } else {
            Rs2Walker.walkTo(FIGHTING_TILE_A, 0);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A) < 8, 1000);
        }
    }

    private void handleFight() {
        // Initialize timer when fight starts for the first time
        if (!firstFightStarted) {
            Microbot.log("Starting fight: initializing aggro reset timer");
            lastAggroResetTime = System.currentTimeMillis();
            firstFightStarted = true;
        }

        // Manage prayer and inventory
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        Rs2Player.drinkPrayerPotion();
        Rs2Inventory.dropAll("Vial");

        if (healWithBass()) { hoverTracker.stop(); return; }
        if (drinkDivinePotionIfNeeded()) { hoverTracker.stop(); return; }

        if (mode == Mode.RANGE && !config.clickRangedAttackTargets()) {
            updateStackReadiness();
            if (stackReady) {
                hoverTracker.stop();
                return;
            }
        }

        // A dropped bass needs an inventory slot before it can be eaten.
        if (Rs2Inventory.emptySlotCount() > 1) {
            Table<WorldPoint, Integer, GroundItem> groundItems = Rs2GroundItem.getGroundItems();
            for (Table.Cell<WorldPoint, Integer, GroundItem> cell : groundItems.cellSet()) {
                if (Rs2Inventory.emptySlotCount() <= 1) break;
                GroundItem item = cell.getValue();
                if (item != null) {
                    if (item.getId() == 143) {
                        hoverTracker.stop();
                        Microbot.log("Picking up potion from the ground");
                        int previousAmount = Rs2Inventory.count(143);
                        Rs2GroundItem.pickup(143);
                        sleepUntil(() -> Rs2Inventory.hasItemAmount(143, previousAmount+1), 3000);
                    }
                }
            }
        }

        if (mode == Mode.RANGE && !config.clickRangedAttackTargets()) {
            long now = System.currentTimeMillis();
            long stepDelay = config.useCustomDelay() ? config.customAttackDelay() : 1800;
            if (now - lastStackStepTime >= stepDelay && walkBetweenTiles()) {
                lastStackStepTime = now;
            }
            return;
        }

        // This is not efficient and needs improvement but sufficient for first release
        // If not it impacts magic a lot due to longer delay
        Rs2NpcModel target = Microbot.getRs2NpcCache().query()
                .withName("Maniacal monkey")
                .where(npc -> npc.getWorldLocation().distanceTo(FIGHTING_TILE_A) <= 3
                        && !npc.getNpc().isDead())
                .nearestOnClientThread();

        boolean attacked = attemptAttack(target);
        if (attacked) walkBetweenTiles();
    }

    private void handleAggroReset() {
        hoverTracker.stop();
        Microbot.log("Resetting aggro...");
        if (
                plugin.getMyWorldPoint().distanceTo(AGGRO_RESET_TILE) > 2 &&
                resettingAggro
        ) {
            Rs2Walker.walkTo(AGGRO_RESET_TILE, 0);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(AGGRO_RESET_TILE) < 2, 1000);
            if (plugin.getMyWorldPoint().distanceTo(AGGRO_RESET_TILE) < 5) {
                resettingAggro = false;
            }
        }
    }

    private void handleBanking() {
        Microbot.log("BANKING IS NOT SUPPORTED YET, WALKING TO EXIT TILE TO LOG OUT");
        state = State.STOP;
    }

    private void stopAndLog() {
        hoverTracker.stop();
        int exitAttempts = 0;
        while (isDownstairs() && exitAttempts < 3) {
            exitAttempts++;
            Rs2Walker.walkTo(EXIT_TILE, 2);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(EXIT_TILE) < 3, 1000);

            if ( plugin.getMyWorldPoint().distanceTo(EXIT_TILE) < 3) {
                Rs2TileObjectModel rope = Microbot.getRs2TileObjectCache().query().withId(28775).nearest();
                if (rope != null) {
                    rope.click("Climb-up");
                    sleepUntil(() -> plugin.getMyWorldPoint().getRegionID() == cavesUpstairs, 5000);
                }
            }
        }

        if (isDownstairs()) {
            Microbot.log("Could not confirm cave exit after " + exitAttempts + " attempts; attempting logout in place");
        }

        Rs2Player.logout();
        Plugin PlayerAssistPlugin = Microbot.getPlugin(MmCavesPlugin.class.getName());
        Microbot.stopPlugin(PlayerAssistPlugin);
    }

    private boolean isDownstairs() {
        int tunnel = 9615;
        int openArea = 9871;

        WorldPoint location = plugin.getMyWorldPoint();
        if (location == null) return false;
        int region = location.getRegionID();
        return region == tunnel || region == openArea;
    }

    private boolean walkBetweenTiles()
    {
        WorldPoint currentTile = Rs2Player.getWorldLocation();
        WorldPoint targetTile = FIGHTING_TILE_A.equals(currentTile) ? FIGHTING_TILE_B : FIGHTING_TILE_A;
        WorldPoint wallClick = FIGHTING_TILE_B.equals(targetTile) ? WALL_CLICK_TO_B : WALL_CLICK_TO_A;

        int runEnergy = Rs2Player.getRunEnergy();
        if (runEnergy <= 0) {
            return false;
        }

        if (!Objects.equals(Rs2Player.getWorldLocation(), targetTile)) {
            MmCavesWallTarget clickTarget = hoverTracker.targetFor(wallClick);
            hoverTracker.stop();
            if (!clickWallTarget(clickTarget)) return false;
            hoverTracker.start(FIGHTING_TILE_A.equals(targetTile) ? WALL_CLICK_TO_B : WALL_CLICK_TO_A);
            sleepUntil(() -> targetTile.equals(plugin.getMyWorldPoint()), 1200);
            return true;
        }
        return true;
    }

    private boolean clickWallTarget(MmCavesWallTarget target) {
        Point canvas = target.canvasPoint(Microbot.getClient());
        if (canvas == null || canvas.getX() < 0 || canvas.getY() < 0
                || canvas.getX() >= Microbot.getClient().getCanvasWidth()
                || canvas.getY() >= Microbot.getClient().getCanvasHeight()) {
            return Rs2Walker.walkFastCanvas(target.tile, true);
        }
        Rs2Player.toggleRunEnergy(true);
        int x = canvas.getX();
        int y = canvas.getY();
        NewMenuEntry entry = new NewMenuEntry()
                .param0(x).param1(y).type(MenuAction.WALK).identifier(0).itemId(0).option("Walk here");
        Microbot.doInvoke(entry, new Rectangle(x, y,
                Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    private void updateStackReadiness() {
        List<WorldPoint> monkeyTiles = Microbot.getRs2NpcCache().query()
                .withName("Maniacal monkey")
                .where(npc -> npc.getWorldLocation() != null && !npc.getNpc().isDead())
                .toListOnClientThread().stream()
                .map(Rs2NpcModel::getWorldLocation)
                .collect(Collectors.toList());
        MmCavesDecisions.StackCounts counts = MmCavesDecisions.countStack(monkeyTiles, FIGHTING_TILE_A, 8);
        latestStackCounts = counts;
        boolean readyNow = counts.ready(config.minimumStackSize(), config.maximumOutsideStack());
        readySamples = readyNow ? readySamples + 1 : 0;
        unreadySamples = readyNow ? 0 : unreadySamples + 1;

        if (!stackReady && readySamples >= 3) {
            stackReady = true;
            Microbot.log("Monkey stack ready: " + counts.stacked + " in chin area, " + counts.outside + " outside; holding for auto-retaliate");
        } else if (stackReady && unreadySamples >= 3) {
            stackReady = false;
            Microbot.log("Monkey stack dispersed: " + counts.stacked + " in chin area, " + counts.outside + " outside; gathering again");
        }
    }

    private boolean healWithBass() {
        if (!MmCavesDecisions.shouldHeal(Rs2Player.getHealthPercentage())) return false;
        hoverTracker.stop();

        if (Rs2Inventory.contains(BASS_ID)) {
            if (System.currentTimeMillis() - lastBassEatAttempt < 1800) return true;
            boolean ate = Rs2Inventory.interact(BASS_ID, "Eat");
            if (ate) lastBassEatAttempt = System.currentTimeMillis();
            return ate;
        }

        if (Rs2Inventory.emptySlotCount() == 0) return false;
        boolean bassNearby = Rs2GroundItem.getGroundItems().cellSet().stream()
                .anyMatch(cell -> cell.getValue() != null && cell.getValue().getId() == BASS_ID);
        if (!bassNearby) return false;

        int previousAmount = Rs2Inventory.count(BASS_ID);
        Rs2GroundItem.pickup(BASS_ID);
        if (!sleepUntil(() -> Rs2Inventory.count(BASS_ID) > previousAmount, 3000)) return false;
        boolean ate = Rs2Inventory.interact(BASS_ID, "Eat");
        if (ate) lastBassEatAttempt = System.currentTimeMillis();
        return ate;
    }

    private boolean drinkDivinePotionIfNeeded() {
        if (Rs2Player.getHealthPercentage() <= 50) return false;
        if (System.currentTimeMillis() - lastDivinePotionAttempt < 5000) return false;

        Skill skill = mode == Mode.RANGE ? Skill.RANGED : Skill.MAGIC;
        if (Rs2Player.getBoostedSkillLevel(skill) > Rs2Player.getRealSkillLevel(skill)) return false;

        String potionName = mode == Mode.RANGE ? "Divine ranging potion" : "Divine magic potion";
        boolean drank = Rs2Inventory.all().stream()
                .filter(item -> item.getName().startsWith(potionName))
                .findFirst()
                .map(item -> {
                    hoverTracker.stop();
                    return Rs2Inventory.interact(item, "Drink");
                })
                .orElse(false);
        if (drank) lastDivinePotionAttempt = System.currentTimeMillis();
        return drank;
    }

    private boolean attemptAttack(Rs2NpcModel target) {
        if (target == null) return false;

        long now = System.currentTimeMillis();
        long timeSinceLastAttack = now - lastAttackTime;
        double elapsedSeconds = timeSinceLastAttack / 1000.0;

        boolean attacked;
        int requiredDelay;

        if (config.useCustomDelay()) requiredDelay = config.customAttackDelay();
        else if (mode.equals(Mode.MAGIC)) requiredDelay = 3000;
        else if (mode.equals(Mode.RANGE)) requiredDelay = 1800;
        else requiredDelay = 3000;

        long waitTime = requiredDelay - timeSinceLastAttack;
        if (waitTime > 0) {
            Microbot.log(String.format(
                    "Waiting %.2fs before next attack (elapsed=%.2fs, required=%.2fs).",
                    waitTime / 1000.0,
                    timeSinceLastAttack / 1000.0,
                    requiredDelay / 1000.0
            ));
            sleep((int) waitTime);
            now = System.currentTimeMillis(); // refresh timestamp after sleeping
            timeSinceLastAttack = now - lastAttackTime;
            elapsedSeconds = timeSinceLastAttack / 1000.0;
        }

        if (config.useCustomDelay() && (now - lastAttackTime < config.customAttackDelay())) {
            Microbot.log(String.format(
                    "Too soon to attack again. Elapsed=%.2fs, required=%.2fs.",
                    elapsedSeconds, requiredDelay / 1000.0
            ));
            return false;
        }

        // Mode specific delays if not using custom delay config
         if (!config.useCustomDelay() && ((now - lastAttackTime < 1800) && mode.equals(Mode.RANGE))) {
             Microbot.log(String.format(
                     "Too soon to attack again. Elapsed=%.2fs, required=%.2fs.",
                     elapsedSeconds, requiredDelay / 1000.0
             ));
             return false;
         }

         if (!config.useCustomDelay() && ((now - lastAttackTime < 3000) && mode.equals(Mode.MAGIC))) {
             Microbot.log(String.format(
                     "Too soon to attack again. Elapsed=%.2fs, required=%.2fs.",
                     elapsedSeconds, requiredDelay / 1000.0
             ));
             return false;
         }

        if (MmCavesDecisions.useDirectMagicCast(mode, config.shouldAutoCast())) {
            attacked = Rs2Magic.castOn(config.magicSpell().getSpell(), target);
        } else {
            attacked = target.click("Attack");
        }

        if (attacked) {
            Microbot.log(String.format(
                    "Attacked. Elapsed=%.2fs",
                    elapsedSeconds
            ));
            lastAttackTime = now;
            sleepUntil(Rs2Player::isInteracting, 600);
        } else {
            Microbot.log("Attack failed on: " + target.getName());
        }

        return attacked;
    }
}
