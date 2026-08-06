package net.runelite.client.plugins.microbot.leaguesfiremaking;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

@Slf4j
public class LeaguesFiremakingScript extends Script {

    private static final int TINDERBOX_ID = 590;
    private static final String TINDERBOX_NAME = "Tinderbox";

    @Getter
    private State state = State.SCANNING;
    @Getter
    private String status = "Starting";
    @Getter
    private FireLine currentLine;

    private WorldPoint startPosition;
    private LogType activeLogType;

    public boolean run(LeaguesFiremakingConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFiremakingSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (startPosition == null) {
                    startPosition = Rs2Player.getWorldLocation();
                }

                activeLogType = config.progressiveMode() ? LogType.getBestForLevel() : config.logType();

                if (activeLogType == null || !activeLogType.hasRequiredLevel()) {
                    status = "Level too low for " + (activeLogType != null ? activeLogType.getLogName() : "any logs");
                    return;
                }

                switch (state) {
                    case SCANNING:
                        handleScanning(config);
                        break;
                    case WALKING_TO_LINE:
                        handleWalkingToLine();
                        break;
                    case BURNING:
                        handleBurning();
                        break;
                    case BANKING:
                        handleBanking(config);
                        break;
                    case WALKING_BACK:
                        handleWalkingBack(config);
                        break;
                }
            } catch (Exception ex) {
                log.error("LeaguesFiremaking loop error", ex);
                Microbot.log(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleScanning(LeaguesFiremakingConfig config) {
        status = "Scanning for open space";

        if (!Rs2Inventory.hasItem(activeLogType.getItemId())) {
            state = State.BANKING;
            return;
        }

        currentLine = TileScanner.findBestLine(startPosition, config.scanRadius());

        if (currentLine == null) {
            status = "No open space found — try moving to a more open area";
            return;
        }

        status = "Found line: " + currentLine.getLength() + " tiles";
        state = State.WALKING_TO_LINE;
    }

    private void handleWalkingToLine() {
        if (currentLine == null) {
            state = State.SCANNING;
            return;
        }

        WorldPoint eastEnd = currentLine.getEastEnd();
        status = "Walking to east end of line";

        if (Rs2Player.getWorldLocation().distanceTo(eastEnd) <= 1) {
            state = State.BURNING;
            return;
        }

        if (!Rs2Player.isMoving()) {
            Rs2Walker.walkTo(eastEnd, 0);
        }
    }

    private void handleBurning() {
        if (!Rs2Inventory.hasItem(activeLogType.getItemId())) {
            status = "Out of logs";
            state = State.BANKING;
            return;
        }

        if (Rs2Player.isMoving()) {
            status = "Walking after lighting...";
            return;
        }

        if (Rs2Player.isAnimating()) {
            status = "Lighting animation...";
            return;
        }

        if (!Rs2Inventory.hasItem(TINDERBOX_NAME)) {
            status = "No tinderbox — banking";
            state = State.BANKING;
            return;
        }

        // Check if we're standing on a fire — need to step west first
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        boolean standingOnFire = TileScanner.hasFire(playerPos);

        if (standingOnFire) {
            // Step one tile west to get off the fire
            WorldPoint westTile = new WorldPoint(playerPos.getX() - 1, playerPos.getY(), playerPos.getPlane());
            if (!Rs2Tile.isWalkable(westTile)) {
                // Can't go west — line is done, rescan
                status = "Blocked west — rescanning";
                state = State.SCANNING;
                return;
            }
            Rs2Walker.walkTo(westTile, 0);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(westTile) <= 0, 3000);
            return;
        }

        if (!Rs2Tile.isWalkable(playerPos)) {
            status = "Standing on blocked tile — rescanning";
            state = State.SCANNING;
            return;
        }

        status = "Lighting " + activeLogType.getLogName();
        WorldPoint beforeLight = Rs2Player.getWorldLocation();
        Rs2Inventory.combine(TINDERBOX_NAME, activeLogType.getLogName());

        // Wait for XP drop (fire lit) then wait for auto-walk west
        if (Rs2Player.waitForXpDrop(Skill.FIREMAKING, 10000)) {
            sleepUntil(() -> !Rs2Player.getWorldLocation().equals(beforeLight), 3000);
            sleep(200, 400);
        }

        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    private void handleBanking(LeaguesFiremakingConfig config) {
        if (config.useBriefcase()) {
            status = "Using briefcase to bank";
            if (!Rs2Inventory.hasItem("Banker's briefcase")) {
                status = "No briefcase found — walking to bank";
                if (!Rs2Bank.walkToBankAndUseBank()) return;
            } else {
                if (!Rs2Bank.isOpen()) {
                    Rs2Inventory.interact("Banker's briefcase", "Bank");
                    sleepUntil(Rs2Bank::isOpen, 5000);
                    if (!Rs2Bank.isOpen()) return;
                }
            }
        } else {
            status = "Walking to bank";
            if (!Rs2Bank.isOpen()) {
                if (!Rs2Bank.walkToBankAndUseBank()) return;
            }
        }

        status = "Depositing and withdrawing";

        Rs2Bank.depositAll();
        sleep(150, 300);

        if (!Rs2Bank.hasItem(TINDERBOX_ID)) {
            status = "No tinderbox in bank — stopping";
            Microbot.log("No tinderbox found in bank.");
            shutdown();
            return;
        }

        Rs2Bank.withdrawOne(TINDERBOX_ID);
        sleepUntil(() -> Rs2Inventory.hasItem(TINDERBOX_NAME), 3000);
        sleep(150, 300);

        if (!Rs2Bank.hasItem(activeLogType.getItemId())) {
            status = "No " + activeLogType.getLogName() + " in bank — stopping";
            Microbot.log("No " + activeLogType.getLogName() + " found in bank.");
            shutdown();
            return;
        }

        Rs2Bank.withdrawAll(activeLogType.getItemId());
        sleepUntil(() -> Rs2Inventory.hasItem(activeLogType.getItemId()), 3000);
        sleep(150, 300);

        Rs2Bank.closeBank();
        state = State.WALKING_BACK;
    }

    private void handleWalkingBack(LeaguesFiremakingConfig config) {
        status = "Walking back to fire area";

        if (Rs2Player.getWorldLocation().distanceTo(startPosition) <= config.scanRadius()) {
            state = State.SCANNING;
            return;
        }

        if (!Rs2Player.isMoving()) {
            Rs2Walker.walkTo(startPosition, 3);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        startPosition = null;
        currentLine = null;
        state = State.SCANNING;
    }
}
