package net.runelite.client.plugins.microbot.gefiremaker;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.gefiremaker.enums.GEWorkLocation;
import net.runelite.client.plugins.microbot.gefiremaker.enums.LogType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

enum State {
    FIREMAKING,
    BANKING,
    BANK_FOR_FIRE_SUPPLIES,
    BUILDING_FIRE
}

public class GEFiremakerScript extends Script {
    public State state = State.BANKING;
    public String debug = "";

    // After using logs on the fire, the player burns the whole inventory on the
    // campfire automatically (no quantity dialog since the game update). While
    // burning is true we only wait; we must NOT re-click the fire.
    private boolean burning = false;
    private int lastLogCount = 0;
    private long lastBurnProgressAt = 0L;

    // Burning a log on a forester's campfire takes ~9 ticks (~5.4s). If no log has
    // been consumed for this long, the fire likely went out and we should re-engage.
    private static final long BURN_STALL_TIMEOUT_MS = 20_000;

    // Fire object IDs (plain fire and forester's campfire)
    private static final int FIRE_ID = 26185;
    private static final int FIRE_ID_ALT = 49927;

    // The "How many would you like to burn?" make-X dialog, if the game shows it
    private static final int BURN_DIALOG_WIDGET = 17694735;

    public boolean run(GEFiremakerConfig config) {
        LogType logType = config.sLogType();
        GEWorkLocation desiredLocation = config.sLocation();

        Microbot.enableAutoRunOn = false;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFiremakingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.microBreakDurationLow = 0;
        Rs2AntibanSettings.microBreakDurationHigh = 3;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                debug("Not running");
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive) {
                debug("Cooldown active");
                Rs2Antiban.actionCooldown();
                return;
            }

            determineState(logType);

            if (Rs2Dialogue.hasContinue()) {
                debug("Click to continue");
                Rs2Dialogue.clickContinue();
                return;
            }

            // While a burn-all is in progress, ignore transient state flips
            // (e.g. the fire lookup briefly missing the fire) and keep waiting.
            if (burning && state != State.FIREMAKING) {
                debug("Still burning, waiting for logs to run out");
                return;
            }

           switch (state) {
            case FIREMAKING:
                int logCount = Rs2Inventory.count(logType.getLogID());
                if (logCount == 0) {
                    burning = false;
                    debug("Out of logs in inventory");
                    return;
                }

                if (burning) {
                    // The player burns the whole inventory on the campfire automatically
                    // (~9 ticks per log), so block here until the logs run out or the
                    // burn visibly stalls.
                    debug("Burning logs on campfire (" + logCount + " left)");
                    while (Rs2Inventory.count(logType.getLogID()) > 0) {
                        if (!super.run() || !Microbot.isLoggedIn()) {
                            return;
                        }

                        if (Rs2Dialogue.hasContinue()) {
                            Rs2Dialogue.clickContinue();
                            continue;
                        }

                        int currentCount = Rs2Inventory.count(logType.getLogID());
                        if (currentCount < lastLogCount) {
                            lastLogCount = currentCount;
                            lastBurnProgressAt = System.currentTimeMillis();
                        } else if (System.currentTimeMillis() - lastBurnProgressAt > BURN_STALL_TIMEOUT_MS) {
                            // No log burned for a while: the fire likely went out (or the
                            // use-on-fire click missed). Re-engage below.
                            debug("Burn stalled, re-engaging fire");
                            break;
                        }
                        sleep(500);
                    }
                    burning = false;
                    return;
                }

                Rs2TileObjectModel fire = findActiveFire();

                if (fire == null) {
                    debug("No fire found");
                    break;
                }

                if (Rs2Player.distanceTo(fire.getWorldLocation()) > 3) {
                    debug("Walking to existing fire");
                    Rs2Walker.walkTo(fire.getWorldLocation());
                    sleep(180, 540);
                    return;
                }

                debug("Using logs on fire");
                Rs2Inventory.use(logType.getLogID());
                if (!sleepUntil(Rs2Inventory::isItemSelected, 2000)) {
                    debug("Log did not select; retrying next tick");
                    break;
                }
                Rs2GameObject.interact(fire);

                // Newer game versions start burning immediately; older ones show the
                // burn make-X dialog, which we confirm with space.
                if (sleepUntil(() -> Rs2Widget.getWidget(BURN_DIALOG_WIDGET) != null, 5000)) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                }

                burning = true;
                lastLogCount = Rs2Inventory.count(logType.getLogID());
                lastBurnProgressAt = System.currentTimeMillis();
                break;
            
            case BANKING:
                debug("Banking");
                bank(logType);
                break;
            
               case BANK_FOR_FIRE_SUPPLIES:
                debug("Banking for fire supplies");
                bankForFireSupplies(logType);
                break;
            
               case BUILDING_FIRE:
                debug("Building fire");

                   if (Rs2Player.isInteracting()) {
                       debug("Interacting");
                       return;
                   }

                   if (Rs2Player.distanceTo(desiredLocation.getWorldPoint()) > 1) {
                       debug("Walking to desired fire location");
                       Rs2Walker.walkTo(desiredLocation.getWorldPoint(), 0);
                       sleep(180, 540);
                       return;
                   }

                   Rs2Inventory.combine("Tinderbox", logType.getLogName());
                   Rs2Player.waitForXpDrop(Skill.FIREMAKING, 20000);
                break;

            default:
                break;
           }

            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(256, 789);
            return;
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    // Determine the state of the script
    private void determineState(LogType logType) {
        debug("Determine state");

        // If any fire spot has an active fire, use it (or bank if out of logs)
        if (findActiveFire() != null) {
            debug("Fire found");
            if (Rs2Inventory.hasItem(logType.getLogID())) {
                debug("Adding logs to fire");
                state = State.FIREMAKING;
            } else {
                debug("Out of logs");
                state = State.BANKING;
            }
            return;
        }

        if (Rs2Inventory.hasItem("Tinderbox") && Rs2Inventory.hasItem(logType.getLogID())) {
            debug("Building fire");
            state = State.BUILDING_FIRE;
        } else {
            debug("Banking for firemaking supplies");
            state = State.BANK_FOR_FIRE_SUPPLIES;
        }
    }

    // Handle all banking actions
    private void bank(LogType logType) {
        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen);
            debug("Bank is open");
            Rs2Bank.depositAll();
            debug("Items deposited");
            sleep(180, 540);

            Rs2Bank.withdrawAll(logType.getLogID());
            sleepUntil(() -> Rs2Inventory.hasItem(logType.getLogID()), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItem(logType.getLogID())) {
                debug("Could not find log type in bank.");
                Microbot.showMessage("Could not find log type in bank.");
                shutdown();
            }
            sleep(180, 540);
            Rs2Bank.closeBank();
        }
    }

    private void bankForFireSupplies(LogType logType) {
        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen);
            debug("Bank is open");
            Rs2Bank.depositAll();
            debug("Items deposited");
            sleep(180, 540);

            Rs2Bank.withdrawOne(590);
            debug("Withdrew a tinderbox");

            sleepUntil(() -> Rs2Inventory.hasItem("Tinderbox"), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItem("Tinderbox")) {
                debug("Could not find Tinderbox in bank.");
                Microbot.showMessage("Could not find Tinderbox in bank.");
                shutdown();
            }

            sleep(180, 540);
            Rs2Bank.withdrawAll(logType.getLogID());
            debug("Withdrew logs");

            sleepUntil(() -> Rs2Inventory.hasItem(logType.getLogID()), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItem(logType.getLogID())) {
                debug("Could not find logs in bank.");
                Microbot.showMessage("Could not find logs in bank.");
                shutdown();
            }
            sleep(180, 540);
            Rs2Bank.closeBank();
        }
    }

    // Finds the active fire / forester's campfire near the player. Mirrors the
    // proven pattern from the firemakingplus plugin: query on the client thread
    // (off-thread reads of the tile-object cache can be stale) and search by
    // name around the player, falling back to known fire object IDs.
    private Rs2TileObjectModel findActiveFire() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Rs2TileObjectModel fire = Microbot.getRs2TileObjectCache().query()
                    .withNameContains("ampfire")
                    .nearest(Rs2Player.getWorldLocation(), 12);
            if (fire == null) {
                fire = Microbot.getRs2TileObjectCache().query()
                        .where(o -> o.getId() == FIRE_ID || o.getId() == FIRE_ID_ALT)
                        .nearest(Rs2Player.getWorldLocation(), 12);
            }
            return fire;
        }).orElse(null);
    }

    private void debug(String msg) {
        debug = msg;
        System.out.println(msg);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
