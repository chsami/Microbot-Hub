package net.runelite.client.plugins.microbot.droslayer.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerConfig;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerPrayer;
import net.runelite.client.plugins.microbot.droslayer.DroSlayerRuntime;
import net.runelite.client.plugins.microbot.droslayer.PrayerFlickStyle;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Handles prayer flicking for slayer combat.
 * Based on AIOFighter's FlickerScript.
 */
@Slf4j
public class DroSlayerFlickerScript {

    // Default attack speed (4 ticks = 2.4 seconds) used when NPC stats are unavailable
    private static final int DEFAULT_ATTACK_SPEED = 4;

    public static final AtomicReference<List<DroSlayerMonster>> currentMonstersAttackingUsRef = new AtomicReference<>(new ArrayList<>());
    private final AtomicReference<List<Rs2NpcModel>> npcsRef = new AtomicReference<>(new ArrayList<>());

    private DroSlayerConfig config;
    private DroSlayerPrayer activePrayer = null;
    private volatile DroSlayerPrayer departurePrayer;
    private int tickToFlick = 0;
    private boolean isRunning = false;

    public void start(DroSlayerConfig config) {
        this.config = config;
        departurePrayer = null;
        this.isRunning = true;
        log.info("DroSlayerFlickerScript started");
    }

    public void stop() {
        this.isRunning = false;
        currentMonstersAttackingUsRef.set(new ArrayList<>());
        activePrayer = null;
        departurePrayer = null;
        log.info("DroSlayerFlickerScript stopped");
    }

    /**
     * Sets the active prayer for flicking
     */
    public void setActivePrayer(DroSlayerPrayer prayer) {
        if (this.activePrayer != prayer) {
            this.activePrayer = prayer;
            log.info("FlickerScript: Active prayer set to {}", prayer != null ? prayer.getDisplayName() : "null");
        }
    }

    /**
     * Clears active prayer (for task reset)
     */
    public void clearActiveProfile() {
        this.activePrayer = null;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /** Keep protection steady through loot, cannon pickup and retreat, including gaps in aggression. */
    public void holdDeparturePrayer(DroSlayerPrayer prayer) {
        departurePrayer = prayer;
    }

    /**
     * Called every game tick from the plugin
     */
    public void onGameTick() {
        if (net.runelite.client.plugins.microbot.droslayer.support.DroLoginReadiness.shouldWaitForLogin()) return;
        if (!isRunning || config == null || DroSlayerRuntime.wildernessSafetyOwnsActions()) {
            return;
        }

        DroSlayerPrayer held = departurePrayer;
        if (held != null && held.getPrayer() != null) {
            if (!Rs2Prayer.isPrayerActive(held.getPrayer())) {
                DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true,
                        () -> Rs2Prayer.toggle(held.getPrayer(), true));
            }
            return;
        }

        PrayerFlickStyle style = config.prayerFlickStyle();
        if (style != PrayerFlickStyle.LAZY_FLICK &&
            style != PrayerFlickStyle.PERFECT_LAZY_FLICK &&
            style != PrayerFlickStyle.MIXED_LAZY_FLICK) {
            return;
        }

        // Determine flick timing based on style
        switch (style) {
            case LAZY_FLICK:
                tickToFlick = 1;
                break;
            case PERFECT_LAZY_FLICK:
                tickToFlick = 0;
                break;
            case MIXED_LAZY_FLICK:
                tickToFlick = Rs2Random.betweenInclusive(0, 1);
                break;
        }

        // Update NPC snapshot
        npcsRef.set(Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getInteracting() == Microbot.getClient().getLocalPlayer())
                .toListOnClientThread());

        // Remove monsters that no longer exist
        currentMonstersAttackingUsRef.updateAndGet(monsters -> {
            List<DroSlayerMonster> updated = new ArrayList<>(monsters);
            updated.removeIf(monster ->
                    npcsRef.get().stream().noneMatch(npc -> npc.getIndex() == monster.npc.getIndex())
            );
            return updated;
        });

        // Seed tracking on restart without waiting for a hitsplat to initialize it.
        resetLastAttack(false);

        // Process monsters: decrement timers, flick prayer on exact tick, reset attacks.
        // The prayer varbit lags one tick, so a second same-style monster due this tick
        // would otherwise click the same prayer again and switch it back off.
        java.util.Set<Rs2PrayerEnum> toggledThisTick = java.util.EnumSet.noneOf(Rs2PrayerEnum.class);
        for (DroSlayerMonster monster : currentMonstersAttackingUsRef.get()) {
            monster.lastAttack--;
            if (monster.lastAttack == tickToFlick && !monster.npc.isDead()) {
                Rs2PrayerEnum prayer = getPrayerToActivate(monster);
                if (prayer != null && !Rs2Prayer.isPrayerActive(prayer) && toggledThisTick.add(prayer)) {
                    log.debug("Flicking {} on (monster: {}, tick: {})", prayer, monster.npc.getName(), monster.lastAttack);
                    DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, () -> {
                        Rs2Prayer.toggle(prayer, true);
                    });
                }
            }
            resetLastAttack(false);
        }

        // Disable prayers if no monsters attacking and not interacting
        if (currentMonstersAttackingUsRef.get().isEmpty() &&
                !Rs2Player.isInteracting() &&
                (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE) ||
                        Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC) ||
                        Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE) ||
                        Rs2Prayer.isQuickPrayerEnabled())) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, () -> {
                if (config.slayerMaster() == net.runelite.client.plugins.microbot.droslayer.DroSlayerMaster.KRYSTILIA) {
                    // Preserve Protect Item when the player-attack or recovery policy enabled it.
                    boolean keepProtectItem = Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_ITEM);
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
                    if (Rs2Prayer.isQuickPrayerEnabled()) Rs2Prayer.toggleQuickPrayer(false);
                    if (keepProtectItem) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, true);
                } else {
                    Rs2Prayer.disableAllPrayers();
                }
            });
        }
    }

    /**
     * Gets the appropriate prayer to activate for a specific monster.
     * Uses the profile prayer if set, otherwise determines from the monster's attack style.
     */
    private Rs2PrayerEnum getPrayerToActivate(DroSlayerMonster monster) {
        if (activePrayer != null && activePrayer != DroSlayerPrayer.NONE) {
            return activePrayer.getPrayer();
        }

        if (monster.attackStyle != null) {
            switch (monster.attackStyle) {
                case MAGE: return Rs2PrayerEnum.PROTECT_MAGIC;
                case RANGED: return Rs2PrayerEnum.PROTECT_RANGE;
                default: return Rs2PrayerEnum.PROTECT_MELEE;
            }
        }
        return Rs2PrayerEnum.PROTECT_MELEE;
    }

    /**
     * Called when an NPC despawns
     */
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        if (!isRunning) return;

        int idx = npcDespawned.getNpc().getIndex();
        currentMonstersAttackingUsRef.updateAndGet(monsters ->
                monsters.stream()
                        .filter(m -> m.npc.getIndex() != idx)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Called when player is hit - resets attack timers
     */
    public void onPlayerHit() {
        if (!isRunning) return;

        // Reset attack timers when we get hit (helps re-sync timing)
        resetLastAttack(true);
        // Note: Don't disable prayers here - if we got hit, we want protection ON
    }

    public void resetLastAttack(boolean forceReset) {
        List<Rs2NpcModel> npcs = npcsRef.get();
        currentMonstersAttackingUsRef.updateAndGet(monsters -> {
            List<DroSlayerMonster> updated = new ArrayList<>(monsters);
            for (Rs2NpcModel npc : npcs) {
                DroSlayerMonster m = updated.stream()
                        .filter(x -> x.npc.getIndex() == npc.getIndex())
                        .findFirst()
                        .orElse(null);

                String style = Rs2NpcManager.getAttackStyle(npc.getId());
                // Default to MELEE if no attack style data available
                AttackStyle attackStyle = (style != null) ? mapToAttackStyle(style) : AttackStyle.MELEE;

                if (m != null) {
                    int attackSpeed = m.getAttackSpeed();
                    if (forceReset && (m.lastAttack <= 0 || npc.getAnimation() != -1)) {
                        m.lastAttack = attackSpeed;
                    }
                    if ((!npc.isDead() && m.lastAttack <= 0) ||
                            (!npc.isDead() && npc.getGraphic() != -1)) {
                        m.lastAttack = (npc.getGraphic() != -1 && attackStyle != AttackStyle.MELEE)
                                ? attackSpeed - 2 + tickToFlick
                                : attackSpeed;
                    }
                    if (m.lastAttack <= -attackSpeed / 2) {
                        updated.remove(m);
                    }
                } else if (!npc.isDead()) {
                    var stats = Rs2NpcManager.getStats(npc.getId());
                    // Create monster even without stats, using defaults
                    DroSlayerMonster toAdd = new DroSlayerMonster(npc, stats);
                    toAdd.attackStyle = attackStyle;
                    updated.add(toAdd);
                    log.debug("Added monster to tracking: {} (stats: {}, style: {})",
                            npc.getName(), stats != null ? "available" : "using defaults", attackStyle);
                }
            }
            return updated;
        });
    }

    /**
     * Maps a string attack style to AttackStyle enum
     */
    private AttackStyle mapToAttackStyle(String style) {
        if (style == null || style.isEmpty()) {
            return AttackStyle.MELEE;
        }

        String lowerCaseStyle = style.toLowerCase();

        if (lowerCaseStyle.contains(",")) {
            String[] styles = lowerCaseStyle.split(",");
            lowerCaseStyle = styles[0].trim();
        }

        if (lowerCaseStyle.contains("melee") || lowerCaseStyle.contains("crush") ||
                lowerCaseStyle.contains("slash") || lowerCaseStyle.contains("stab")) {
            return AttackStyle.MELEE;
        } else if (lowerCaseStyle.contains("magic")) {
            return AttackStyle.MAGE;
        } else if (lowerCaseStyle.contains("ranged")) {
            return AttackStyle.RANGED;
        } else {
            return AttackStyle.MELEE;
        }
    }
}
