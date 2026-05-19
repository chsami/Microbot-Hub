package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class KrakenBossHelper {

    // NPC IDs
    private static final int BOSS_WHIRLPOOL = NpcID.SLAYER_KRAKEN_BOSS_WHIRLPOOL;       // 496
    private static final int BOSS_AWAKE = NpcID.SLAYER_KRAKEN_BOSS;                       // 494
    private static final int TENTACLE_WHIRLPOOL = NpcID.SLAYER_KRAKEN_BOSS_TENTACLE_WHIRLPOOL; // 5534
    private static final int TENTACLE_AWAKE = NpcID.SLAYER_KRAKEN_BOSS_TENTACLE;          // 5535

    @Getter
    private String status = "Idle";
    private boolean antibanInitialized = false;

    public void reset() {
        status = "Idle";
        antibanInitialized = false;
    }

    public boolean tick(LeaguesToolkitConfig config) {
        // Initialize antiban on first tick
        if (!antibanInitialized) {
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
            Rs2AntibanSettings.simulateMistakes = true;
            Rs2AntibanSettings.takeMicroBreaks = true;
            Rs2AntibanSettings.microBreakChance = 0.02;
            Rs2AntibanSettings.actionCooldownChance = 0.15;
            Rs2AntibanSettings.moveMouseOffScreen = true;
            antibanInitialized = true;
            log.info("[Kraken] Antiban initialized");
        }

        // Keep Protect from Magic on at all times
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        }

        // Eat food if HP low
        if (Rs2Player.getHealthPercentage() <= config.krakenEatAt()) {
            status = "Eating";
            Rs2Player.eatAt(config.krakenEatAt());
            sleep(300, 500);
        }

        // Drink prayer pot if low
        if (config.krakenDrinkPrayer()) {
            Rs2Player.drinkPrayerPotionAt(config.krakenPrayerThreshold());
        }

        // Wait if already in combat
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            // Check what we're fighting
            Rs2NpcModel awakeTentacle = findAliveNpc(TENTACLE_AWAKE);
            Rs2NpcModel boss = findAliveNpc(BOSS_AWAKE);
            if (awakeTentacle != null) {
                status = "Killing tentacle";
                return true;
            }
            if (boss != null) {
                status = "Fighting Kraken";
                return true;
            }
        }

        // Step 1: Kill any awake tentacles first
        Rs2NpcModel awakeTentacle = findAliveNpc(TENTACLE_AWAKE);
        if (awakeTentacle != null) {
            if (!Rs2Combat.inCombat()) {
                status = "Attacking tentacle";
                awakeTentacle.click("Attack");
                sleep(600, 900);
            } else {
                status = "Killing tentacle";
            }
            return true;
        }

        // Step 2: Disturb next tentacle whirlpool
        Rs2NpcModel tentaclePool = findNpc(TENTACLE_WHIRLPOOL);
        if (tentaclePool != null) {
            status = "Disturbing tentacle";
            tentaclePool.click("Disturb");
            sleep(600, 900);
            return true;
        }

        // Step 3: All tentacles dead — disturb the boss whirlpool
        Rs2NpcModel bossPool = findNpc(BOSS_WHIRLPOOL);
        if (bossPool != null) {
            status = "Disturbing Kraken boss";
            bossPool.click("Disturb");
            sleep(600, 900);
            return true;
        }

        // Step 4: Boss is awake — attack it
        Rs2NpcModel boss = findAliveNpc(BOSS_AWAKE);
        if (boss != null) {
            if (!Rs2Combat.inCombat()) {
                status = "Attacking Kraken";
                boss.click("Attack");
                sleep(600, 900);
            } else {
                status = "Fighting Kraken";
            }
            return true;
        }

        // Step 4: Boss is dead — loot valuable drops before respawn
        if (lootValuableDrops()) {
            status = "Looting";
            return true;
        }

        // Between kills — safe to do antiban here while waiting
        if (findNpc(BOSS_WHIRLPOOL) == null && findNpc(BOSS_AWAKE) == null) {
            status = "Waiting for respawn...";
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            return true;
        }

        // Also safe to antiban before starting a new kill cycle
        if (findNpc(BOSS_WHIRLPOOL) != null && findNpc(TENTACLE_WHIRLPOOL) != null
                && findAliveNpc(TENTACLE_AWAKE) == null && findAliveNpc(BOSS_AWAKE) == null) {
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
        }

        status = "Idle";
        return true;
    }

    /**
     * Loot high-value drops like Trident of the seas (full).
     * Eats food to make space if inventory is full.
     */
    private boolean lootValuableDrops() {
        // Check for valuable drops nearby
        String[] valuableItems = {"Trident of the seas (full)", "Kraken tentacle", "Jar of dirt", "Pet kraken"};
        for (String item : valuableItems) {
            if (Rs2GroundItem.exists(item, 10)) {
                if (Rs2Inventory.isFull()) {
                    // Eat food to make space
                    if (!Rs2Inventory.getInventoryFood().isEmpty()) {
                        Rs2Player.eatAt(100);
                        sleep(300, 500);
                    }
                }
                Rs2GroundItem.loot(item, 10);
                sleep(600, 900);
                return true;
            }
        }
        return false;
    }

    private Rs2NpcModel findNpc(int id) {
        return Microbot.getRs2NpcCache().query()
                .withId(id)
                .nearest();
    }

    private Rs2NpcModel findAliveNpc(int id) {
        return Microbot.getRs2NpcCache().query()
                .withId(id)
                .where(npc -> !npc.isDead())
                .nearest();
    }
}
