package net.runelite.client.plugins.microbot.firemakingplus;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Firemaking trainer with two selectable methods (Forester's Campfire and Line firemaking) wrapped
 * in the Plus layer (stop conditions, target level + clean shutdown, overlay/pause, speed/league
 * modes).
 *
 * <p>The LINE method does tinderbox-on-log, auto-steps west, and uses TileScanner line finding with
 * a blocked-line guard. The CAMPFIRE method stands at a bank, finds or lights a fire, then uses logs
 * on it until the inventory is empty.</p>
 */
@Slf4j
public class AutoFiremakingPlusScript extends Script {

    private static final int TINDERBOX_ID = ItemID.TINDERBOX;
    private static final String TINDERBOX_NAME = "Tinderbox";
    private static final int FIRE_ID = ObjectID.FIRE;
    private static final int FIRE_ID_ALT = 49927;
    // Using a log on a Forester's Campfire opens this "Burn" make-X dialog; SPACE burns the whole
    // inventory.
    private static final int BURN_INTERFACE_WIDGET = 17694735;

    private State state = State.SCANNING;
    private WorldPoint startPosition;
    private Logs activeLog;
    private FireLine currentLine;

    // Line blocked-line guard: the Y of the last line that blocked, excluded from the next scan.
    private int blockedLineY = Integer.MIN_VALUE;
    private int emptyScans = 0;

    // Stats (read by AutoFiremakingPlusOverlay).
    private long startTimeMillis = 0;
    private int startSkillXp = 0;
    private int startSkillLevel = 0;
    private int actionsCompleted = 0;

    private boolean shutdownAfterCleanup = false;

    // Campfire burn tracking (tick-driven).
    // lastLogCount = active-log count observed last tick (-1 = reset/unknown);
    // lastBurnProgressMs = last time the count dropped or we (re)initiated a burn.
    private int lastLogCount = -1;
    private long lastBurnProgressMs = 0;

    public long getStartTimeMillis() { return startTimeMillis; }
    public int getStartSkillXp() { return startSkillXp; }
    public int getStartSkillLevel() { return startSkillLevel; }
    public int getActionsCompleted() { return actionsCompleted; }

    public boolean run(AutoFiremakingPlusConfig config) {
        startPosition = null;
        state = State.SCANNING;
        activeLog = null;
        currentLine = null;
        blockedLineY = Integer.MIN_VALUE;
        emptyScans = 0;
        lastLogCount = -1;
        lastBurnProgressMs = 0;

        startTimeMillis = System.currentTimeMillis();
        startSkillXp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getSkillExperience(Skill.FIREMAKING)).orElse(0);
        startSkillLevel = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING)).orElse(1);
        actionsCompleted = 0;
        shutdownAfterCleanup = false;

        Microbot.enableAutoRunOn = true;
        Rs2Walker.disableTeleports = true; // keep banking on foot
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFiremakingSetup();
        if (config.speedMode()) {
            Rs2AntibanSettings.antibanEnabled = false;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (Microbot.pauseAllScripts.get()) {
                    Microbot.status = "[PAUSED]";
                    return;
                }

                if (config.stopAfterMinutes() > 0
                        && (System.currentTimeMillis() - startTimeMillis) / 60000 >= config.stopAfterMinutes()) {
                    Microbot.log("AutoFiremakingPlus: reached stopAfterMinutes. Shutting down.");
                    super.shutdown();
                    return;
                }
                if (config.stopAfterXp() > 0) {
                    int currentXp = Microbot.getClientThread().runOnClientThreadOptional(() ->
                            Microbot.getClient().getSkillExperience(Skill.FIREMAKING)).orElse(startSkillXp);
                    if (currentXp - startSkillXp >= config.stopAfterXp()) {
                        Microbot.log("AutoFiremakingPlus: reached stopAfterXp. Shutting down.");
                        super.shutdown();
                        return;
                    }
                }
                if (config.targetLevel() > 0 && !shutdownAfterCleanup) {
                    int currentLevel = Microbot.getClientThread().runOnClientThreadOptional(() ->
                            Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING)).orElse(startSkillLevel);
                    if (currentLevel >= config.targetLevel()) {
                        Microbot.log("AutoFiremakingPlus: reached targetLevel (" + currentLevel
                                + "). Banking then shutting down.");
                        shutdownAfterCleanup = true;
                        state = State.BANKING;
                    }
                }

                if (config.leagueMode() && Rs2Player.checkIdleLogout(Rs2Random.between(500, 1500))) {
                    int[] arrowKeys = { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN };
                    Rs2Keyboard.keyPress(arrowKeys[Rs2Random.between(0, arrowKeys.length - 1)]);
                }

                // Skip the tick while the global action cooldown is active. Speed mode bypasses the
                // gate entirely so it actually runs without antiban pacing.
                if (!config.speedMode() && Rs2AntibanSettings.actionCooldownActive) return;

                if (startPosition == null) startPosition = Rs2Player.getWorldLocation();

                activeLog = config.progressiveMode() ? Logs.getBestForLevel() : config.logType();
                if (activeLog == null || !activeLog.hasRequiredLevel()) {
                    Microbot.status = "Firemaking level too low for "
                            + (activeLog != null ? activeLog.getLogName() : "any logs");
                    return;
                }

                if (config.method() == FiremakingMethod.CAMPFIRE) {
                    runCampfire(config);
                } else {
                    runLine(config);
                }
                // Arm the cooldown / micro-break by chance after each tick's work, unless speed mode
                // has disabled antiban.
                if (!config.speedMode()) {
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                }
            } catch (Exception ex) {
                Microbot.logStackTrace("AutoFiremakingPlusScript", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    // --- Forester's Campfire: stand at a bank, find/light a fire, use logs on it until empty. ---

    private void runCampfire(AutoFiremakingPlusConfig config) {
        if (shutdownAfterCleanup || !Rs2Inventory.hasItem(activeLog.getItemId())) {
            lastLogCount = -1; // reset burn tracking before banking
            handleBanking(config);
            return;
        }
        // Only block on movement (walking back from the bank). The burn is tick-driven below.
        if (Rs2Player.isMoving()) {
            Microbot.status = "Returning to campfire";
            return;
        }

        WorldPoint anchor = Rs2Player.getWorldLocation();
        Rs2TileObjectModel target = findCampfire(anchor, 12);

        if (target == null) {
            // No fire or campfire nearby: light our own fire with a tinderbox, then burn logs on it.
            // Using logs on a self-lit fire opens the same Burn dialog as a Forester's Campfire, so
            // the burn logic below handles either one.
            if (!Rs2Inventory.hasItem(TINDERBOX_NAME)) {
                Microbot.status = "No tinderbox - banking for one";
                lastLogCount = -1;
                handleBanking(config);
                return;
            }
            Microbot.status = "Lighting own fire";
            WorldPoint here = Rs2Player.getWorldLocation();
            Rs2Inventory.combine(TINDERBOX_NAME, activeLog.getLogName());
            if (Rs2Player.waitForXpDrop(Skill.FIREMAKING, 5000)) {
                // Fire lit (one log burned); the player auto-steps back. Next tick findCampfire
                // finds the new fire and the burn logic adds the rest of the inventory to it.
                actionsCompleted++;
                lastLogCount = -1;
            } else {
                // "You can't light a fire here" -> step to a nearby lightable tile and retry.
                Microbot.status = "Can't light here - repositioning";
                WorldPoint spot = findLightableTile(here);
                if (spot != null) {
                    Rs2Walker.walkTo(spot, 0);
                    sleepUntil(() -> !Rs2Player.isMoving(), 3000);
                }
            }
            return;
        }

        int count = Rs2Inventory.count(activeLog.getItemId());
        long now = System.currentTimeMillis();

        // Burn in progress: the count dropped since last tick. Credit each log burned and let it
        // keep going -- do NOT re-initiate, which would re-open the dialog mid-burn.
        if (lastLogCount >= 0 && count < lastLogCount) {
            actionsCompleted += (lastLogCount - count);
            lastLogCount = count;
            lastBurnProgressMs = now;
            Microbot.status = "Burning logs (" + count + " left)";
            return;
        }
        // Recently (re)initiated and still inside the grace window: give the burn time to tick
        // before re-kicking. The loop stays responsive (pause/stop honoured every tick).
        if (lastLogCount >= 0 && now - lastBurnProgressMs < 5000) {
            lastLogCount = count;
            Microbot.status = "Burning logs (" + count + " left)";
            return;
        }

        // Fresh start, or the burn stalled with logs remaining -> (re)initiate it. Use a log on the
        // campfire (menu-based) -> "Burn" make-X dialog (widget 17694735) -> SPACE. Using a log is
        // what yields XP. Select the log and wait for it to actually enter "use" mode before
        // interacting, since useItemOnObject's internal 100ms check is too tight under antiban.
        Microbot.status = "Adding logs to campfire";
        Rs2Inventory.use(activeLog.getItemId());
        if (!sleepUntil(Rs2Inventory::isItemSelected, 2000)) {
            Microbot.log("[Firemaking] log did not select; retrying next tick");
            return;
        }
        Rs2GameObject.interact(target);
        if (sleepUntil(() -> Rs2Widget.getWidget(BURN_INTERFACE_WIDGET) != null, 5000)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            lastLogCount = count;
            lastBurnProgressMs = System.currentTimeMillis();
            sleep(600, 900);
        } else {
            Microbot.log("[Firemaking] burn dialog did NOT open; retrying next tick");
        }
    }

    /**
     * Find a nearby Forester's Campfire (by name) or plain fire (by id) within radius of anchor.
     * Queried on the client thread: the script loop runs on a background thread, and off-thread reads
     * of the tile-object cache can return a stale campfire that has already burned out. On the client
     * thread the cache reflects the despawn, so this correctly returns null when no fire exists.
     */
    private Rs2TileObjectModel findCampfire(WorldPoint anchor, int radius) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Rs2TileObjectModel t = Microbot.getRs2TileObjectCache().query()
                    .withNameContains("ampfire")
                    .nearest(anchor, radius);
            if (t == null) {
                t = Microbot.getRs2TileObjectCache().query()
                        .where(o -> o.getId() == FIRE_ID || o.getId() == FIRE_ID_ALT)
                        .nearest(anchor, radius);
            }
            return t;
        }).orElse(null);
    }

    /** Find a nearby walkable tile with no fire on it, so we can light our own there. */
    private WorldPoint findLightableTile(WorldPoint from) {
        int[][] offsets = { {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1} };
        for (int[] o : offsets) {
            WorldPoint p = new WorldPoint(from.getX() + o[0], from.getY() + o[1], from.getPlane());
            if (Rs2Tile.isWalkable(p) && !TileScanner.hasFire(p)) {
                return p;
            }
        }
        return null;
    }

    // --- Line firemaking: light logs in a line stepping west, with a blocked-line guard. ---

    private void runLine(AutoFiremakingPlusConfig config) {
        switch (state) {
            case SCANNING:
                lineScan(config);
                break;
            case WALKING_TO_LINE:
                lineWalkTo();
                break;
            case BURNING:
                lineBurn();
                break;
            case BANKING:
                handleBanking(config);
                break;
            case WALKING_BACK:
                lineWalkBack(config);
                break;
        }
    }

    private void lineScan(AutoFiremakingPlusConfig config) {
        Microbot.status = "Scanning for open space";
        if (!Rs2Inventory.hasItem(activeLog.getItemId())) {
            state = State.BANKING;
            return;
        }
        // Scan once and derive both the best line and the blocked-row fallback from the same list
        // (findFireLines returns lines already sorted best-first).
        List<FireLine> lines = TileScanner.findFireLines(startPosition, config.scanRadius());
        FireLine line = lines.isEmpty() ? null : lines.get(0);
        // Guard: if the best line is the same row we just got blocked on, pick a different row.
        if (line != null && line.getY() == blockedLineY) {
            line = lines.stream()
                    .filter(l -> l.getY() != blockedLineY)
                    .findFirst().orElse(null);
        }
        if (line == null) {
            emptyScans++;
            if (emptyScans >= 5) {
                Microbot.status = "No open line found - move me to open ground";
                emptyScans = 0;
                blockedLineY = Integer.MIN_VALUE; // reset so banking->return can retry fresh
                state = State.BANKING;
            } else {
                Microbot.status = "No open space found - retrying";
            }
            return;
        }
        emptyScans = 0;
        currentLine = line;
        Microbot.status = "Found line: " + line.getLength() + " tiles";
        state = State.WALKING_TO_LINE;
    }

    private void lineWalkTo() {
        if (currentLine == null) {
            state = State.SCANNING;
            return;
        }
        WorldPoint eastEnd = currentLine.getEastEnd();
        Microbot.status = "Walking to line";
        if (Rs2Player.getWorldLocation().distanceTo(eastEnd) <= 1) {
            state = State.BURNING;
            return;
        }
        if (!Rs2Player.isMoving()) {
            Rs2Walker.walkTo(eastEnd, 0);
        }
    }

    private void lineBurn() {
        if (!Rs2Inventory.hasItem(activeLog.getItemId())) {
            state = State.BANKING;
            return;
        }
        if (Rs2Player.isMoving()) {
            Microbot.status = "Walking after lighting";
            return;
        }
        if (Rs2Player.isAnimating()) {
            Microbot.status = "Lighting animation";
            return;
        }
        if (!Rs2Inventory.hasItem(TINDERBOX_NAME)) {
            state = State.BANKING;
            return;
        }

        WorldPoint playerPos = Rs2Player.getWorldLocation();
        if (TileScanner.hasFire(playerPos)) {
            WorldPoint westTile = new WorldPoint(playerPos.getX() - 1, playerPos.getY(), playerPos.getPlane());
            if (!Rs2Tile.isWalkable(westTile)) {
                blockedLineY = playerPos.getY(); // remember this row so the next scan avoids it
                Microbot.status = "Blocked west - rescanning";
                state = State.SCANNING;
                return;
            }
            Rs2Walker.walkTo(westTile, 0);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(westTile) <= 0, 3000);
            return;
        }
        if (!Rs2Tile.isWalkable(playerPos)) {
            blockedLineY = playerPos.getY();
            Microbot.status = "Blocked tile - rescanning";
            state = State.SCANNING;
            return;
        }

        Microbot.status = "Lighting " + activeLog.getLogName();
        WorldPoint before = Rs2Player.getWorldLocation();
        Rs2Inventory.combine(TINDERBOX_NAME, activeLog.getLogName());
        if (Rs2Player.waitForXpDrop(Skill.FIREMAKING, 10000)) {
            actionsCompleted++;
            sleepUntil(() -> !Rs2Player.getWorldLocation().equals(before), 3000);
            sleep(200, 400);
        }
    }

    private void lineWalkBack(AutoFiremakingPlusConfig config) {
        Microbot.status = "Walking back";
        if (Rs2Player.getWorldLocation().distanceTo(startPosition) <= config.scanRadius()) {
            state = State.SCANNING;
            return;
        }
        if (!Rs2Player.isMoving()) {
            Rs2Walker.walkTo(startPosition, 3);
        }
    }

    // --- Shared banking (both methods). ---

    private void handleBanking(AutoFiremakingPlusConfig config) {
        if (Rs2Player.isMoving()) return;
        Microbot.status = "Banking";
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank()) return;
        }
        if (!Rs2Bank.isOpen()) return;

        // Decide whether to carry a tinderbox this trip. The line method always lights fires, so it
        // always needs one. The campfire method needs one only to light its OWN fire: if a Forester's
        // Campfire is already within burn range and maximizeLogSpace is on, bank the tinderbox and
        // carry one extra log instead. findCampfire reads on the client thread (live scene) and uses
        // the same radius runCampfire burns at, so it reliably predicts whether we will need to light.
        boolean needTinderbox = config.method() == FiremakingMethod.LINE
                || !config.maximizeLogSpace()
                || findCampfire(Rs2Player.getWorldLocation(), 12) == null;

        if (needTinderbox) {
            Rs2Bank.depositAllExcept(TINDERBOX_NAME, activeLog.getLogName());
        } else {
            // A campfire is up: bank the tinderbox too, freeing a slot for one more log.
            Rs2Bank.depositAllExcept(activeLog.getLogName());
        }
        sleep(300);

        if (shutdownAfterCleanup) {
            Rs2Bank.closeBank();
            Microbot.log("AutoFiremakingPlus: target reached, banked, shutting down.");
            super.shutdown();
            return;
        }

        if (needTinderbox && !Rs2Inventory.hasItem(TINDERBOX_NAME)) {
            if (!Rs2Bank.hasItem(TINDERBOX_ID)) {
                Microbot.showMessage("No tinderbox in the bank!");
                super.shutdown();
                return;
            }
            Rs2Bank.withdrawOne(TINDERBOX_ID);
            sleepUntil(() -> Rs2Inventory.hasItem(TINDERBOX_NAME), 3000);
        }

        if (!Rs2Bank.hasItem(activeLog.getItemId())) {
            Microbot.showMessage("No " + activeLog.getLogName() + " in the bank!");
            super.shutdown();
            return;
        }
        Microbot.status = "Withdrawing logs";
        Rs2Bank.withdrawAll(activeLog.getItemId());
        sleepUntil(() -> Rs2Inventory.hasItem(activeLog.getItemId()), 3000);
        Rs2Random.wait(300, 700);
        Rs2Bank.closeBank();

        // Resume: campfire burns where it stands; line walks back to its start area then rescans.
        state = (config.method() == FiremakingMethod.CAMPFIRE) ? State.BURNING : State.WALKING_BACK;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Walker.disableTeleports = false;
        Rs2Antiban.resetAntibanSettings();
        startPosition = null;
        currentLine = null;
    }
}
