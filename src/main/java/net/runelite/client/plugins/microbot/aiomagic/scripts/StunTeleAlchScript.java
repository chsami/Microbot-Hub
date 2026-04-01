package net.runelite.client.plugins.microbot.aiomagic.scripts;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
public class StunTeleAlchScript extends Script {
    private final AIOMagicPlugin plugin;
    private static final List<Integer> TELEPORT_GRAPHICS = List.of(800, 802, 803, 804);
    private static final long NPC_RECOVERY_TELEPORT_COOLDOWN_MS = 8_000L;
    private static final int MAX_CONSECUTIVE_RECOVERY_TELEPORTS = 3;
    private static final long NPC_NOT_FOUND_LOG_THROTTLE_MS = 2_000L;
    private long lastNpcRecoveryTeleportAt = 0L;
    private int consecutiveNpcRecoveryTeleports = 0;
    private long lastNpcNotFoundLogAt = 0L;
    @Inject
    public StunTeleAlchScript(AIOMagicPlugin plugin) {
        this.plugin = plugin;
    }
    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.TELEPORT_TRAINING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();
                String configuredTargetNpc = plugin.getStunNpcName();
                if (configuredTargetNpc == null || configuredTargetNpc.isBlank()) {
                    Microbot.showMessage("Set a stun NPC name in config");
                    shutdown();
                    return;
                }
                if (plugin.getTeleportSpell() == null) {
                    Microbot.showMessage("Set a teleport spell in config");
                    shutdown();
                    return;
                }
                // Ensure we have an alchemy item present
                if (plugin.getAlchItemNames().isEmpty()
                        || plugin.getAlchItemNames().stream().noneMatch(Rs2Inventory::hasItem)) {
                    Microbot.log("Missing alchemy items...");
                    return;
                }
                // Ensure we can cast alchemy
                if (!Rs2Magic.hasRequiredRunes(plugin.getAlchSpell())) {
                    Microbot.showMessage("Out of runes for alchemy");
                    shutdown();
                    return;
                }
                // Ensure we can stun
                if (!Rs2Magic.hasRequiredRunes(plugin.getStunSpell().getRs2Spell())) {
                    Microbot.showMessage("Out of runes for " + plugin.getStunSpell().name());
                    shutdown();
                    return;
                }
                // Resolve the alchemy item
                Rs2ItemModel alchemyItem = plugin.getAlchItemNames().stream()
                        .filter(Rs2Inventory::hasItem)
                        .map(Rs2Inventory::get)
                        .findFirst()
                        .orElse(null);
                if (alchemyItem == null) {
                    Microbot.log("Missing alchemy items...");
                    return;
                }
                // Optional inventory slot normalization for natural mouse
                if (Rs2AntibanSettings.naturalMouse) {
                    int inventorySlot = Rs2Player.getSkillRequirement(Skill.MAGIC, 55) ? 12 : 4;
                    if (alchemyItem.getSlot() != inventorySlot) {
                        Rs2Inventory.moveItemToSlot(alchemyItem, inventorySlot);
                        return;
                    }
                }
                // 1) STUN target from user config using queryable NPC cache
                Rs2NpcModel target = Microbot.getRs2NpcCache().query()
                        .withName(configuredTargetNpc)
                        .nearest();
                if (target == null) {
                    attemptNpcRecoveryTeleport(configuredTargetNpc);
                    return;
                }
                consecutiveNpcRecoveryTeleports = 0;
                if (target.getWorldLocation() == null) {
                    Microbot.log("NPC has no valid world location: " + configuredTargetNpc);
                    return;
                }
                if (!Rs2Magic.cast(plugin.getStunSpell().getRs2Spell().getMagicAction())) {
                    Microbot.log("Failed to select stun spell: " + plugin.getStunSpell().name());
                    return;
                }
                if (!sleepUntil(() -> Microbot.getClient().isWidgetSelected(), 800)) {
                    Microbot.log("Stun spell was not selected in time");
                    return;
                }
                boolean stunned = target.click();
                if (!stunned) {
                    Microbot.log("Failed to cast stun on NPC: " + configuredTargetNpc);
                    return;
                }
                // 2) ALCHEMY
                if (Rs2AntibanSettings.naturalMouse) {
                    Rs2Magic.alch(alchemyItem, 10, 50);
                } else {
                    Rs2Magic.alch(alchemyItem);
                    sleep(200, 300);
                }
                // 3) TELEPORT from user config
                if (!Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell())) {
                    Microbot.showMessage("Out of runes for " + plugin.getTeleportSpell().name());
                    shutdown();
                    return;
                }
                if (!castConfiguredTeleportAndWait("rotation")) {
                    return;
                }
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                Microbot.log("Total time for loop " + totalTime);
            } catch (Exception ex) {
                Microbot.log("Stun TeleAlchemy loop failed: " + ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override
    public void shutdown() {
        lastNpcRecoveryTeleportAt = 0L;
        consecutiveNpcRecoveryTeleports = 0;
        lastNpcNotFoundLogAt = 0L;
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private void attemptNpcRecoveryTeleport(String configuredTargetNpc) {
        long now = System.currentTimeMillis();
        if (consecutiveNpcRecoveryTeleports >= MAX_CONSECUTIVE_RECOVERY_TELEPORTS) {
            Microbot.showMessage(
                    "Unable to find NPC '" + configuredTargetNpc + "' after " + MAX_CONSECUTIVE_RECOVERY_TELEPORTS
                            + " recovery teleports. Check your stun NPC and location setup."
            );
            shutdown();
            return;
        }

        long elapsedSinceRecoveryTeleport = now - lastNpcRecoveryTeleportAt;
        if (elapsedSinceRecoveryTeleport < NPC_RECOVERY_TELEPORT_COOLDOWN_MS) {
            logMissingNpcThrottled(
                    "Unable to find NPC '" + configuredTargetNpc + "'. Waiting "
                            + (NPC_RECOVERY_TELEPORT_COOLDOWN_MS - elapsedSinceRecoveryTeleport)
                            + "ms before recovery teleport."
            );
            return;
        }

        if (!Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell())) {
            Microbot.showMessage(
                    "Unable to find NPC '" + configuredTargetNpc + "' and out of runes for recovery teleport: "
                            + plugin.getTeleportSpell().name()
            );
            shutdown();
            return;
        }

        logMissingNpcThrottled("Unable to find NPC '" + configuredTargetNpc + "'. Attempting recovery teleport...");
        boolean recovered = castConfiguredTeleportAndWait("npc recovery");
        consecutiveNpcRecoveryTeleports++;
        lastNpcRecoveryTeleportAt = System.currentTimeMillis();

        if (!recovered) {
            Microbot.log("Recovery teleport failed for missing NPC: " + configuredTargetNpc);
        }
    }

    private boolean castConfiguredTeleportAndWait(String context) {
        WorldPoint preTeleportLocation = Rs2Player.getWorldLocation();
        if (!Rs2Magic.cast(plugin.getTeleportSpell().getRs2Spell().getMagicAction())) {
            Microbot.log("Failed to cast teleport (" + context + "): " + plugin.getTeleportSpell().name());
            return false;
        }

        boolean teleportStarted = sleepUntil(() ->
                        Rs2Player.isAnimating(1200)
                                || TELEPORT_GRAPHICS.stream().anyMatch(Rs2Player::hasSpotAnimation)
                                || (preTeleportLocation != null && !preTeleportLocation.equals(Rs2Player.getWorldLocation())),
                2400);
        if (!teleportStarted) {
            Microbot.log("Teleport did not start in time (" + context + "): " + plugin.getTeleportSpell().name());
            return false;
        }

        boolean teleportFinished = sleepUntil(() -> {
            WorldPoint currentLocation = Rs2Player.getWorldLocation();
            boolean locationChanged = preTeleportLocation != null && !preTeleportLocation.equals(currentLocation);
            boolean noTeleportSpotAnimation = TELEPORT_GRAPHICS.stream().noneMatch(Rs2Player::hasSpotAnimation);
            boolean settled = !Rs2Player.isAnimating(1200) && noTeleportSpotAnimation;
            return preTeleportLocation == null ? settled : (locationChanged && settled);
        }, 5000);
        if (!teleportFinished) {
            Microbot.log("Teleport did not finish in time (" + context + "): " + plugin.getTeleportSpell().name());
            return false;
        }

        return true;
    }

    private void logMissingNpcThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastNpcNotFoundLogAt >= NPC_NOT_FOUND_LOG_THROTTLE_MS) {
            Microbot.log(message);
            lastNpcNotFoundLogAt = now;
        }
    }
}
