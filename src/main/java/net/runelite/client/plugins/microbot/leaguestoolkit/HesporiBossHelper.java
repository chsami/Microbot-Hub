package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

@Slf4j
public class HesporiBossHelper {

    // NPC IDs
    private static final int HESPORI_NORMAL = NpcID.HESPORI;                    // 8583
    private static final int HESPORI_ECHO = NpcID.LEAGUE_6_HESPORI;             // 15615
    private static final int HEALER_ACTIVE = NpcID.HESPORI_HEALER_ACTIVE;       // 8584
    private static final int HEALER_INACTIVE = NpcID.HESPORI_HEALER_INACTIVE;   // 8585

    // Attack animations
    private static final int ANIM_RANGED = AnimationID.HESPORI_ATTACK_RANGED;   // 8224
    private static final int ANIM_SPECIAL = AnimationID.HESPORI_ATTACK_SPECIAL;  // 8223 (magic)

    // Leagues 6 Hespori projectile IDs (discovered from in-game testing)
    private static final int RANGE_PROJ = 3677;   // Ranged attack — pray against, don't dodge
    private static final int MAGIC_PROJ = 3678;   // Magic attack — pray against, don't dodge
    private static final int VINE_PROJ = 3680;     // Vine/quadrant explosion — DODGE THIS

    @Getter
    private String status = "Idle";
    @Getter
    private String currentPrayer = "None";
    @Getter
    private String phase = "Combat";

    private int lastSeenAnimation = -1;
    private int lastPrayedStyle = -1; // 0=missiles, 1=magic
    @Getter @Setter
    private volatile boolean vineDetected = false;
    @Getter @Setter
    private volatile int dodgeX = -1, dodgeY = -1;
    private volatile long lastDodgeTime = 0;
    private static final long DODGE_COOLDOWN_MS = 5000; // Don't dodge again for 5 seconds

    private ScheduledExecutorService fastExecutor;
    private ScheduledFuture<?> fastLoop;
    private LeaguesToolkitConfig config;

    public void start(LeaguesToolkitConfig config) {
        this.config = config;
        reset();
        if (fastExecutor == null) {
            fastExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        fastLoop = fastExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                tick();
            } catch (Exception ex) {
                log.error("[Hespori] Fast loop error", ex);
            }
        }, 0, 150, TimeUnit.MILLISECONDS);
        log.info("[Hespori] Fast prayer loop started (150ms)");
    }

    public void stop() {
        if (fastLoop != null) {
            fastLoop.cancel(false);
            fastLoop = null;
        }
        if (lastPrayedStyle != -1) {
            Rs2Prayer.disableAllPrayers();
            lastPrayedStyle = -1;
        }
        status = "Stopped";
        currentPrayer = "None";
    }

    public void reset() {
        status = "Idle";
        currentPrayer = "None";
        phase = "Combat";
        lastSeenAnimation = -1;
        lastPrayedStyle = -1;
    }

    public void tick() {
        Rs2NpcModel hespori = findHespori();
        if (hespori == null) {
            status = "No Hespori found";
            return;
        }

        // Eat if HP low
        if (Rs2Player.getHealthPercentage() <= 50) {
            Rs2Player.eatAt(50);
        }

        // Drink prayer if low
        Rs2Player.drinkPrayerPotionAt(20);

        phase = "Combat";

        // === FAST LOOP ONLY: prayer switching + eat/drink ===
        // ALL movement/clicking handled by tickCombat() on the main loop

        // Read boss animation for prayer switching
        int anim = Microbot.getClientThread().runOnClientThreadOptional(
                () -> hespori.getNpc().getAnimation()
        ).orElse(-1);

        if (anim != -1 && anim != lastSeenAnimation) {
            lastSeenAnimation = anim;
            int style = animToStyle(anim);
            if (style != -1 && style != lastPrayedStyle) {
                switchPrayer(style);
                lastPrayedStyle = style;
            }
        }

        // Default: keep Protect from Magic on if no recent attack detected
        if (lastPrayedStyle == -1) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
            currentPrayer = "Magic (default)";
        }

        status = "Fighting Hespori (" + currentPrayer + ")";
    }

    /**
     * Flower phase: boss is invulnerable, kill active flowers.
     * Boss doesn't use magic during this phase — only ranged.
     * Flowers show overhead prayers:
     * - Protect from Melee (HeadIcon.MELEE) → use ranged/magic weapon
     * - Protect from Ranged+Magic (HeadIcon.RANGE_MAGE) → use melee weapon
     */
    private void handleFlowerPhase() {
        // Keep Protect from Missiles on during flower phase (boss only uses ranged here)
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
            currentPrayer = "Missiles (flowers)";
        }

        // Kill ONE flower per tickCombat call — avoids blocking client thread with tight loop
        Rs2NpcModel flower = findActiveFlower();
        if (flower == null) return;

        // Check overhead prayer for weapon switch
        HeadIcon flowerPrayer = Microbot.getClientThread().runOnClientThreadOptional(
                () -> flower.getHeadIcon()
        ).orElse(null);

        if (flowerPrayer != null && config != null) {
            boolean needsMelee = (flowerPrayer == HeadIcon.RANGED || flowerPrayer == HeadIcon.MAGIC
                    || flowerPrayer == HeadIcon.RANGE_MAGE);
            if (needsMelee) {
                String meleeWeapon = config.hesporiMeleeWeapon();
                if (!Rs2Equipment.isWearing(meleeWeapon) && Rs2Inventory.hasItem(meleeWeapon)) {
                    Rs2Inventory.wield(meleeWeapon);
                    sleep(150, 300);
                }
            } else {
                String mageWeapon = config.hesporiMageWeapon();
                if (!Rs2Equipment.isWearing(mageWeapon) && Rs2Inventory.hasItem(mageWeapon)) {
                    Rs2Inventory.wield(mageWeapon);
                    sleep(150, 300);
                }
            }
        }

        status = "Killing flower";
        Microbot.getRs2NpcCache().query()
                .withId(flower.getId())
                .interact(flower.getId(), "Attack");
    }

    // Vine dodge is handled by handleVineDodge() called from tickCombat

    /**
     * Called from tickCombat (main 1-second loop) — safe to click/walk here.
     */
    public boolean handleVineDodge() {
        if (!vineDetected) return false;
        vineDetected = false;
        dodgeX = -1;
        dodgeY = -1;

        // Cooldown — don't dodge again within 5 seconds
        if (System.currentTimeMillis() - lastDodgeTime < DODGE_COOLDOWN_MS) {
            return false;
        }

        WorldPoint myPos = Rs2Player.getWorldLocation();
        if (myPos == null) return false;

        // Determine which quadrant we're in relative to boss center
        // Boss LocalPoint is always (7104, 7104) — convert to world offset direction
        // Just move 5 tiles in the opposite direction from boss center
        LocalPoint myLocal = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var player = Microbot.getClient().getLocalPlayer();
            return player != null ? player.getLocalLocation() : null;
        }).orElse(null);

        if (myLocal == null) return false;

        int bossLocalX = 7104, bossLocalY = 7104;
        int dx = (myLocal.getX() > bossLocalX) ? -5 : 5;
        int dy = (myLocal.getY() > bossLocalY) ? -5 : 5;

        WorldPoint dodgeTarget = new WorldPoint(myPos.getX() + dx, myPos.getY() + dy, myPos.getPlane());

        status = "Dodging vine!";
        log.info("[Hespori] Vine dodge — Rs2Walker.walkFastCanvas to {}", dodgeTarget);
        Rs2Walker.walkFastCanvas(dodgeTarget);
        lastDodgeTime = System.currentTimeMillis();
        sleep(600, 900);
        return true;
    }

    private Rs2NpcModel findHespori() {
        // Both normal and Echo Hespori use the same NPC ID (8583)
        return findAliveNpc(HESPORI_NORMAL);
    }

    /**
     * Find an active flower by ID first, falling back to name-based search for Echo variants.
     */
    private Rs2NpcModel findActiveFlower() {
        // Try by known ID first
        Rs2NpcModel byId = findAliveNpc(HEALER_ACTIVE);
        if (byId != null) return byId;

        // Fallback: search by name "Flower" for Echo variants that might use different IDs
        return Microbot.getRs2NpcCache().query()
                .where(npc -> {
                    String name = npc.getName();
                    return name != null && name.equalsIgnoreCase("Flower");
                })
                .where(npc -> !npc.isDead())
                .where(npc -> {
                    // Only active flowers (not inactive/cyan ones)
                    HeadIcon icon = Microbot.getClientThread().runOnClientThreadOptional(
                            () -> npc.getHeadIcon()
                    ).orElse(null);
                    return icon != null; // Active flowers have overhead prayers, inactive don't
                })
                .nearest();
    }

    private Rs2NpcModel findAliveNpc(int id) {
        return Microbot.getRs2NpcCache().query()
                .withId(id)
                .where(npc -> !npc.isDead())
                .nearest();
    }

    /**
     * Called from the main 1-second toolkit loop to handle attacking.
     * Separated from the fast prayer loop to avoid threading issues with doInvoke.
     */
    public void tickCombat() {
        log.info("[Hespori] tickCombat called");
        Rs2NpcModel hespori = findHespori();
        if (hespori == null) {
            log.info("[Hespori] tickCombat: findHespori returned null — NPC not found or dead");
            return;
        }
        log.info("[Hespori] tickCombat: found NPC id={}, dead={}", hespori.getId(), hespori.isDead());

        // Handle vine dodge FIRST — highest priority
        if (handleVineDodge()) {
            return;
        }

        // Handle flower phase attacking
        Rs2NpcModel activeFlower = findActiveFlower();
        if (activeFlower != null) {
            handleFlowerPhase();
            return;
        }

        // Ensure main weapon equipped
        if (config != null) {
            String mainWeapon = config.hesporiMainWeapon();
            if (!Rs2Equipment.isWearing(mainWeapon) && Rs2Inventory.hasItem(mainWeapon)) {
                Rs2Inventory.wield(mainWeapon);
                sleep(150, 300);
            }
        }

        // NOTE: No walk range check — player is already in the circular arena.
        // Instance coordinate mismatch makes distance calculations unreliable.

        // Only click attack if not already interacting with Hespori
        boolean alreadyFighting = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var player = Microbot.getClient().getLocalPlayer();
            if (player == null) return false;
            var target = player.getInteracting();
            if (target == null) return false;
            return target.equals(hespori.getNpc());
        }).orElse(false);

        if (!alreadyFighting) {
            status = "Attacking Hespori";
            // Dump full NPC state for debugging, then attack
            Microbot.getClientThread().invoke(() -> {
                try {
                    NPC rawNpc = hespori.getNpc();
                    if (rawNpc == null) {
                        log.error("[Hespori] rawNpc is null");
                        return;
                    }

                    // Dump all info
                    log.info("[Hespori] === NPC DEBUG ===");
                    log.info("[Hespori] NPC id={}, index={}, name={}", rawNpc.getId(), rawNpc.getIndex(), rawNpc.getName());
                    log.info("[Hespori] worldLocation={}", rawNpc.getWorldLocation());
                    log.info("[Hespori] localLocation={}", rawNpc.getLocalLocation());
                    log.info("[Hespori] animation={}, isDead={}", rawNpc.getAnimation(), rawNpc.isDead());

                    var comp = Microbot.getClient().getNpcDefinition(rawNpc.getId());
                    if (comp != null) {
                        log.info("[Hespori] Composition actions: {}", java.util.Arrays.toString(comp.getActions()));
                    } else {
                        log.error("[Hespori] NPCComposition is null for id={}", rawNpc.getId());
                    }

                    var canvasPoly = rawNpc.getCanvasTilePoly();
                    log.info("[Hespori] canvasTilePoly={}", canvasPoly != null ? canvasPoly.getBounds() : "null");

                    // Verify the action exists and find its index
                    String[] actions = comp != null ? comp.getActions() : null;
                    if (actions == null) {
                        log.error("[Hespori] No actions on NPC");
                        return;
                    }

                    // Find attack action — don't hardcode, check what's available
                    String attackAction = null;
                    int attackIndex = -1;
                    for (int i = 0; i < actions.length; i++) {
                        if (actions[i] != null && actions[i].toLowerCase().contains("attack")) {
                            attackAction = actions[i];
                            attackIndex = i;
                            break;
                        }
                    }

                    if (attackAction == null) {
                        log.error("[Hespori] No attack-like action found in: {}", java.util.Arrays.toString(actions));
                        return;
                    }

                    log.info("[Hespori] Found action '{}' at index {}", attackAction, attackIndex);

                    MenuAction menuAction = MenuAction.of(MenuAction.NPC_FIRST_OPTION.getId() + attackIndex);
                    log.info("[Hespori] MenuAction opcode: {} (id={})", menuAction, menuAction.getId());

                    Microbot.getClient().menuAction(
                            0, 0, menuAction, rawNpc.getIndex(), -1,
                            attackAction, rawNpc.getName() != null ? rawNpc.getName() : ""
                    );
                    log.info("[Hespori] menuAction dispatched successfully");
                } catch (Exception e) {
                    log.error("[Hespori] Attack error", e);
                }
            });
            sleep(1200, 1500);
        } else {
            status = "Fighting Hespori (" + currentPrayer + ")";
        }
    }

    private boolean isMeleeWeaponEquipped(LeaguesToolkitConfig config) {
        if (config == null) return false;
        String mainWeapon = config.hesporiMainWeapon();
        String meleeWeapon = config.hesporiMeleeWeapon();
        // If the main weapon is the same as the melee weapon, we're in melee mode
        return Rs2Equipment.isWearing(mainWeapon) && mainWeapon.equalsIgnoreCase(meleeWeapon);
    }

    private int animToStyle(int animation) {
        if (animation == ANIM_RANGED) return 0;   // Ranged → Protect from Missiles
        if (animation == ANIM_SPECIAL) return 1;   // Magic → Protect from Magic
        return -1;
    }

    private void switchPrayer(int style) {
        switch (style) {
            case 0:
                currentPrayer = "Missiles";
                log.info("[Hespori] → Protect from Missiles (ranged)");
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                break;
            case 1:
                currentPrayer = "Magic";
                log.info("[Hespori] → Protect from Magic");
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                break;
        }
    }

    private WorldPoint findSafeTile(WorldPoint center, int distance) {
        Map<WorldPoint, Integer> dangerousTiles = Rs2Tile.getDangerousGraphicsObjectTiles();
        List<WorldPoint> candidates = new ArrayList<>();

        for (int dx = -distance; dx <= distance; dx++) {
            for (int dy = -distance; dy <= distance; dy++) {
                if (dx == 0 && dy == 0) continue;
                WorldPoint candidate = new WorldPoint(
                        center.getX() + dx, center.getY() + dy, center.getPlane());
                if (!dangerousTiles.containsKey(candidate) && Rs2Tile.isWalkable(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        if (candidates.isEmpty()) return null;

        Rs2NpcModel boss = findHespori();
        if (boss != null) {
            WorldPoint bossLoc = boss.getWorldLocation();
            candidates.sort(Comparator.comparingInt(c -> c.distanceTo(bossLoc)));
        }

        return candidates.get(0);
    }
}
