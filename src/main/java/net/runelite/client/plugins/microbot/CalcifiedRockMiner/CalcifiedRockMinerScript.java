package net.runelite.client.plugins.microbot.CalcifiedRockMiner;

import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.CalcifiedRockMiner.CalcifiedRockMinerConfig;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

public class CalcifiedRockMinerScript extends Script {
    private final int WEEPING_ROCK = 51493;
    public static CalcifiedRockMinerState BOT_STATUS = CalcifiedRockMinerState.BANKING;
    private final WorldPoint CALCIFIED_ROCK_LOCATION = new WorldPoint(1516, 9545, 1);
    private final WorldPoint ANVIL = new WorldPoint(1447, 9584, 1);
    public boolean shouldTryMiningAgain = true;

    {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(VERY_LOW);
    }

    public boolean run(net.runelite.client.plugins.microbot.CalcifiedRockMiner.CalcifiedRockMinerConfig config) {
        BOT_STATUS = CalcifiedRockMinerState.MINING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                switch (BOT_STATUS) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case CRUSHING:
                        handleCrushing(config);
                        break;
                    case MINING:
                        handleMining(config);
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Exception message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1500, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleMining(net.runelite.client.plugins.microbot.CalcifiedRockMiner.CalcifiedRockMinerConfig config) {
        if (Rs2Inventory.isFull()) {
            if (config.dropDeposits()) {
                Rs2Inventory.dropAll(false, "calcified deposit", "uncut");
                return;
            } else if (config.crushDeposits()) {
                BOT_STATUS = CalcifiedRockMinerState.CRUSHING;
                return;
            } else {
                BOT_STATUS = CalcifiedRockMinerState.BANKING;
                return;
            }
        }
        if (Rs2Player.distanceTo(CALCIFIED_ROCK_LOCATION) > 15) {
            Rs2Walker.walkTo(CALCIFIED_ROCK_LOCATION);
            return;
        }

        if (hopIfTooManyPlayersNearby(config)) return; // Exit current cycle after hop

        if ((Rs2Equipment.isWearing("Dragon pickaxe") || Rs2Equipment.isWearing("Crystal pickaxe")) && Rs2Combat.getSpecEnergy() == 1000) {
            Rs2Combat.setSpecState(true, 1000);
            return;
        }

        if (config.focusCrackedWaterDeposits() && !Rs2Player.isMoving()) {
            Rs2TileObjectModel weepingRock = Microbot.getRs2TileObjectCache().query()
                    .withId(WEEPING_ROCK)
                    .within(Rs2Player.getWorldLocation(), 15)
                    .first();
            if (weepingRock != null) {
                Rs2TileObjectModel tearRock = Microbot.getClientThread().invoke(() ->
                        Microbot.getRs2TileObjectCache().query()
                                .within(weepingRock.getWorldLocation(), 2)
                                .withName("Calcified rocks")
                                .first());
                if (tearRock != null) {
                    MoveCameraToRock(tearRock.getWorldLocation());
                    var distance = tearRock.getLocalLocation().distanceTo(Rs2Player.getLocalLocation());
                    // 128 == 1 tile, so we must be mining the tear, if above, we should move to the tear
                    if (distance > 128 || !Rs2Player.isAnimating()) {
                        Rs2Camera.turnTo(tearRock.getLocalLocation(), 45);
                        if (tearRock.click()) {
                            Rs2Player.waitForXpDrop(Skill.MINING, true);
                            Rs2Antiban.actionCooldown();
                            Rs2Antiban.takeMicroBreakByChance();
                            sleepUntil(Rs2Player::isAnimating, 5000);
                        }
                        return;
                    }
                }
            }
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating(1500)) {
            return;
        }

        Rs2TileObjectModel rock = Microbot.getClientThread().invoke(() ->
                Microbot.getRs2TileObjectCache().query()
                        .within(CALCIFIED_ROCK_LOCATION, 12)
                        .withName("Calcified rocks")
                        .nearestReachable());
        if (rock != null && shouldTryMiningAgain) {
            MoveCameraToRock(rock.getWorldLocation());
            if (rock.click()) {
                Rs2Player.waitForXpDrop(Skill.MINING, true);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
                sleepUntil(Rs2Player::isAnimating, 5000);
            }
        } else {
            Microbot.log("No calcified rock found. Waiting...");
        }
    }

    private boolean hopIfTooManyPlayersNearby(net.runelite.client.plugins.microbot.CalcifiedRockMiner.CalcifiedRockMinerConfig config) {
        int maxPlayers = config.maxPlayersInArea();
        if (maxPlayers > 0) {
            WorldPoint localLocation = Rs2Player.getWorldLocation();

            long nearbyPlayers = Microbot.getClient().getTopLevelWorldView().players().stream()
                    .filter(p -> p != null && p != Microbot.getClient().getLocalPlayer())
                    .filter(p -> {
                        return p.getWorldLocation().distanceTo(localLocation) <= 15;
                    })
                    //filter if players are using mining animation
                    .filter(p -> p.getAnimation() != -1)
                    .count();

            if (nearbyPlayers >= maxPlayers) {
                Microbot.log("Too many players nearby. Hopping...");
                Rs2Random.waitEx(3200, 800); // Delay to avoid UI locking
                int world = Login.getRandomWorld(Rs2Player.isMember());
                boolean hopped = Microbot.hopToWorld(world);
                if (!hopped) return false;
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
            }
        }
        return false;
    }

    private boolean hasHammer() {
        return Rs2Inventory.hasItem("hammer") || Rs2Equipment.isWearing("hammer");
    }

    private void handleCrushing(net.runelite.client.plugins.microbot.CalcifiedRockMiner.CalcifiedRockMinerConfig config) {
        if (config.crushDeposits() && hasHammer() && Rs2Inventory.hasItem(29088)) {
            if (Rs2Player.getWorldLocation().distanceTo(ANVIL) < 1) {
                Rs2Inventory.interact(29088, "use");
                Rs2TileObjectModel anvil = Microbot.getClientThread().invoke(() ->
                        Microbot.getRs2TileObjectCache().query()
                                .within(ANVIL, 3)
                                .withName("Anvil")
                                .nearestReachable());
                if (anvil == null) {
                    return;
                }
                anvil.click();
                sleep(400,600);
                Rs2Widget.sleepUntilHasWidget("How many would you like to smash?");
                sleep(200,400);
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleep(200,400);
                while (Rs2Inventory.hasItem(29088)) {
                    if (!this.isRunning()) {
                        break;
                    }
                    sleep(1200,1600);
                }
                BOT_STATUS = CalcifiedRockMinerState.BANKING;
            }
            else {
                Rs2Walker.walkTo(ANVIL);
                Rs2Walker.walkFastCanvas(ANVIL);
            }
        } else {
            BOT_STATUS = CalcifiedRockMinerState.BANKING;
        }
    }

    private void MoveCameraToRock(WorldPoint rock) {
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
        Rs2Camera.turnTo(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), rock));
    }

    private void handleBanking(CalcifiedRockMinerConfig config) {
        if (!config.dropDeposits()) {
            Rs2Bank.walkToBank(BankLocation.CAM_TORUM);
            Rs2Bank.openBank();
            sleepUntil(() -> Rs2Bank.isOpen(), 5000);
            Rs2Bank.depositAllExcept("pickaxe", "hammer");
            Rs2Bank.closeBank();

        }
        BOT_STATUS = CalcifiedRockMinerState.MINING;
    }

    public enum CalcifiedRockMinerState {BANKING, CRUSHING, MINING}

}
