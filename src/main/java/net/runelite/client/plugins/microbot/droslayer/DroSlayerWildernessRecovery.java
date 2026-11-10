package net.runelite.client.plugins.microbot.droslayer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.walker.WildernessCombatEscape;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.death.Rs2Death;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/** Caller-driven recovery with minimal supplies and a bounded attempt to retrieve an optional cannon. */
@Slf4j
public final class DroSlayerWildernessRecovery {
    public enum Result { PROGRESS, COMPLETE, BLOCKED }
    enum Phase { IDLE, RESPAWN, BANK, REFRESH, TRAVEL, CANNON, GRAVE, NULODION, RETURN_BANK, COMPLETE, BLOCKED }
    private static final long PHASE_TIMEOUT_MS = 8 * 60_000L;
    private static final long ACTION_GAP_MS = 1800L;
    private static final WorldPoint FEROX_POOL = new WorldPoint(3129, 3636, 0);
    private static final WorldPoint NULODION = new WorldPoint(3012, 3453, 0);
    private static final Set<Integer> CANNON_IDS = new HashSet<>(Arrays.asList(
            5, 6, 7, 8, 9, 14916, 43027, 43028, 43029, 43030, 43031));
    private static final String[] CANNON_PARTS = { "Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace" };
    private final DroSlayerWildernessSettings safetySettings;
    private Phase phase = Phase.IDLE;
    private WorldPoint deathPoint;
    private WorldPoint cannonTile;
    private boolean cannonResolved;
    private boolean cannonUnavailable;
    private int cannonWorld;
    private boolean needsLockpick;
    private int returnWorldAttempts;
    private long returnWorldAttemptAt;
    private boolean inventoryDeposited;
    private boolean equipmentDeposited;
    private int deathAttempts;
    private long phaseStarted;
    private long lastAction;
    private long nulodionInteractionStarted;
    private String failureReason;

    public DroSlayerWildernessRecovery() { this(new DroSlayerWildernessSettings()); }
    DroSlayerWildernessRecovery(DroSlayerWildernessSettings safetySettings) { this.safetySettings = safetySettings; }

    /** Call from the script loop after an event queues the death; a second death preserves the first cannon site. */
    public void begin(WorldPoint newDeathPoint, WorldPoint deployedCannonTile) {
        begin(newDeathPoint, deployedCannonTile, currentWorld(), false);
    }

    public void begin(WorldPoint newDeathPoint, WorldPoint deployedCannonTile, int deployedWorld) {
        begin(newDeathPoint, deployedCannonTile, deployedWorld, false);
    }

    public void begin(WorldPoint newDeathPoint, WorldPoint deployedCannonTile, int deployedWorld, boolean requiresLockpick) {
        if (!isActive()) {
            deathAttempts = 0;
            cannonTile = deployedCannonTile;
            cannonWorld = deployedCannonTile == null ? 0 : deployedWorld;
            needsLockpick = requiresLockpick;
        } else if (cannonTile == null && deployedCannonTile != null) {
            cannonTile = deployedCannonTile;
            cannonWorld = deployedWorld;
            needsLockpick = requiresLockpick;
        }
        safetySettings.reset();
        deathPoint = newDeathPoint;
        cannonResolved = cannonTile == null;
        cannonUnavailable = false;
        inventoryDeposited = false;
        equipmentDeposited = false;
        failureReason = null;
        returnWorldAttempts = 0;
        returnWorldAttemptAt = 0L;
        deathAttempts++;
        DroSlayerRuntime.resetRecoveryRoute();
        boolean aliveInWilderness = Rs2Pvp.isInWilderness() && !Rs2Death.isDead()
                && Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > 0
                && Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getLocalPlayer() != null && !Microbot.getClient().getLocalPlayer().isDead()).orElse(false);
        changePhase(aliveInWilderness ? Phase.BANK : Phase.RESPAWN);
        log.info("Wilderness recovery queued: death={} cannon={} world={} attempt={} aliveReturn={}",
                deathPoint, cannonTile, cannonWorld, deathAttempts, aliveInWilderness);
    }

    public void cancel() {
        DroSlayerRuntime.cancelWalk();
        phase = Phase.IDLE;
        suspendedAt = 0L;
        cannonTile = null;
        cannonWorld = 0;
        needsLockpick = false;
        cannonUnavailable = false;
        deathPoint = null;
        failureReason = null;
        deathAttempts = 0;
    }

    public boolean isActive() { return phase != Phase.IDLE && phase != Phase.COMPLETE && phase != Phase.BLOCKED; }
    public String getFailureReason() { return failureReason; }
    public WorldPoint getCannonTile() { return cannonTile; }
    public int getCannonWorld() { return cannonWorld; }
    public boolean wasCannonUnavailable() { return cannonUnavailable; }

    /** Run on a background script tick, with profile ownership already established by the caller. */
    private long nextRespawnWaitLogAt;

    private long suspendedAt;

    /** Human input, login and profile holds are not failed recovery progress. */
    public void suspend() {
        if (isActive() && suspendedAt == 0L) suspendedAt = System.nanoTime();
    }

    private void resumeClock() {
        if (suspendedAt != 0L) {
            phaseStarted += System.nanoTime() - suspendedAt;
            suspendedAt = 0L;
        }
    }

    private void logRespawnHold(String reason) {
        if (System.currentTimeMillis() < nextRespawnWaitLogAt) return;
        nextRespawnWaitLogAt = System.currentTimeMillis() + 15_000L;
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            var player = client.getLocalPlayer();
            log.warn("Wilderness recovery held: phase={} reason={} gameState={} location={} dead={} hp={} wilderness={}",
                    phase, reason, client.getGameState(), player == null ? null : player.getWorldLocation(),
                    player == null || player.isDead(), client.getBoostedSkillLevel(Skill.HITPOINTS),
                    client.getVarbitValue(Varbits.IN_WILDERNESS));
            return true;
        });
    }

    public Result tick(DroSlayerConfig config) {
        if (phase == Phase.BLOCKED) return Result.BLOCKED;
        if (phase == Phase.COMPLETE || phase == Phase.IDLE) return Result.COMPLETE;
        if (deathAttempts > Math.max(1, config.maxRecoveryAttempts())) return block("Repeated Wilderness deaths exhausted recovery attempts");
        if (Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getGameState() != GameState.LOGGED_IN).orElse(true)) {
            suspend();
            logRespawnHold("not logged in");
            return Result.PROGRESS;
        }
        resumeClock();
        WorldPoint here = Rs2Player.getWorldLocation();
        boolean livingAtSafeRespawn = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            var player = client.getLocalPlayer();
            return player != null && here != null
                    && client.getBoostedSkillLevel(Skill.HITPOINTS) > 0
                    && client.getVarbitValue(Varbits.IN_WILDERNESS) == 0
                    && (!player.isDead() || deathPoint != null && here.distanceTo(deathPoint) > 32);
        }).orElse(false);
        // A retained actor death flag must not mask an observed, living safe respawn.
        // Check before timeout too: resuming after a hold can already be at the respawn.
        if (phase == Phase.RESPAWN && livingAtSafeRespawn) {
            if (Rs2Player.getRealSkillLevel(Skill.PRAYER) < 25) return block("Protect Item requires level 25 Prayer for Wilderness recovery");
            log.info("Wilderness respawn confirmed at {}; proceeding to recovery bank", here);
            changePhase(Phase.BANK);
            return Result.PROGRESS;
        }
        if (elapsedMillis() > PHASE_TIMEOUT_MS) {
            if (!cannonResolved && (phase == Phase.TRAVEL || phase == Phase.CANNON || phase == Phase.NULODION)) {
                skipCannon("Optional cannon recovery timed out during " + phase);
            } else return block("Wilderness recovery timed out during " + phase);
        }
        Microbot.status = "Wilderness recovery: " + phase;
        if (here == null || (Rs2Death.isDead() && !livingAtSafeRespawn)) {
            logRespawnHold(here == null ? "player location unavailable" : "death flag active");
            return Result.PROGRESS;
        }
        if (phase != Phase.RESPAWN && phase != Phase.REFRESH && (phase != Phase.BANK || Rs2Pvp.isInWilderness())) {
            if (maintainSurvival(config)) return Result.PROGRESS;
        }
        // Walker's threat response owns movement and interactions in every recovery phase.
        if (phase != Phase.RESPAWN && emergencyEscapeActive()) return Result.PROGRESS;
        switch (phase) {
            case RESPAWN:
                logRespawnHold("waiting for positive HP outside Wilderness");
                return Result.PROGRESS;
            case BANK:
                prepareRecoverySupplies(config);
                break;
            case REFRESH:
                refreshPrayer(config);
                break;
            case TRAVEL:
                if (!hasRecoveryTarget()) { changePhase(Phase.RETURN_BANK); break; }
                if (emergencyEscapeActive()) return Result.PROGRESS;
                if (!cannonResolved && !ensureOriginalCannonWorld(config)) return Result.PROGRESS;
                if (!hasRecoveryTarget()) { changePhase(Phase.RETURN_BANK); break; }
                if (!ensureRecoverySafeSettings()) return Result.PROGRESS;
                if (Rs2Bank.isOpen()) { act(() -> { Rs2Bank.closeBank(); return true; }); return Result.PROGRESS; }
                WorldPoint target = !cannonResolved ? cannonTile : deathPoint;
                if (target == null) return block("No recorded Wilderness death or cannon location");
                if (DroSlayerRuntime.walkFull(config, target, 4)) {
                    DroSlayerRuntime.cancelWalk();
                    changePhase(cannonResolved ? Phase.GRAVE : Phase.CANNON);
                }
                break;
            case CANNON:
                if (emergencyEscapeActive()) return Result.PROGRESS;
                if (!cannonResolved && !ensureOriginalCannonWorld(config)) return Result.PROGRESS;
                if (!ensureRecoverySafeSettings()) return Result.PROGRESS;
                collectCannon(config);
                break;
            case GRAVE:
                collectGrave(config);
                break;
            case NULODION:
                recoverFromNulodion(config);
                break;
            case RETURN_BANK:
                returnToBank(config);
                break;
            default:
                break;
        }
        return phase == Phase.BLOCKED ? Result.BLOCKED : phase == Phase.COMPLETE ? Result.COMPLETE : Result.PROGRESS;
    }

    private void prepareRecoverySupplies(DroSlayerConfig config) {
        if (!ensureRecoverySafeSettings()) return;
        if (hasAllCannonParts()) cannonResolved = true;
        if (!DroSlayerRuntime.walkToBankAndOpen(config, "Wilderness recovery supplies")) return;
        // PvP deaths often have neither a grave nor a deployed cannon. Do not prepare or visit an empty death site.
        if (!hasRecoveryTarget()) { changePhase(Phase.RETURN_BANK); return; }
        if (!equipmentDeposited) {
            if (!Rs2Equipment.items().isEmpty()) {
                act(Rs2Bank::depositEquipment);
                return;
            }
            equipmentDeposited = true;
        }
        if (!inventoryDeposited) {
            if (!Rs2Inventory.items().findAny().isEmpty()) {
                act(Rs2Bank::depositAll);
                return;
            }
            inventoryDeposited = true;
        }
        int wantedFood = recoveryFoodTarget(config.wildernessRecoveryFood());
        int carriedFood = (int) Rs2Inventory.items().filter(i -> DroSlayerWildernessSupplies.isFood(i, true)).count();
        if (carriedFood < wantedFood) {
            Rs2ItemModel food = Rs2Bank.bankItems().stream()
                    .filter(i -> DroSlayerWildernessSupplies.isFood(i, true) && !i.isStackable())
                    .min(Comparator.comparingInt(i -> DroSlayerWildernessSupplies.foodPreference(i.getName(), true))).orElse(null);
            if (food == null) {
                if (!hasMinimumRecoveryFood(carriedFood)) { block("At least five recovery food are required in the bank"); return; }
            } else {
                int quantity = Math.min(wantedFood - carriedFood, food.getQuantity());
                act(() -> Rs2Bank.withdrawX(food.getId(), quantity));
                return;
            }
        }
        if (!Rs2Inventory.items().anyMatch(i -> DroSlayerWildernessSupplies.isPrayerPotion(i, true))) {
            // Ordinary restores can also top up Prayer on a safe travel leg.
            Rs2ItemModel restore = Rs2Bank.bankItems().stream()
                    .filter(i -> DroSlayerWildernessSupplies.isPrayerPotion(i, true))
                    .min(Comparator.comparingInt(i -> DroSlayerWildernessSupplies.isBlighted(i.getName()) ? 1 : 0)).orElse(null);
            if (restore == null) { block("One restore/prayer potion is required for Wilderness recovery"); return; }
            act(() -> Rs2Bank.withdrawOne(restore.getId()));
            return;
        }
        if (findEscapeItem() == null) {
            Rs2ItemModel escape = Rs2Bank.bankItems().stream().filter(i -> i.getQuantity() > 0 && !i.isNoted() && escapeDestination(i.getName()) != null)
                    .min(Comparator.comparingInt(i -> WildernessCombatEscape.escapePreference(i.getName()))).orElse(null);
            if (escape == null) { block("A charged level-30 escape teleport is required for Wilderness recovery"); return; }
            act(() -> Rs2Bank.withdrawOne(escape.getId()));
            return;
        }
        if (!cannonResolved && needsLockpick && !Rs2Inventory.hasItem("Lockpick")) {
            if (Rs2Bank.count("Lockpick", true) == 0) {
                skipCannon("Lockpick unavailable for optional cannon recovery access");
                return;
            }
            act(() -> Rs2Bank.withdrawOne("Lockpick"));
            return;
        }
        if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) < recoveryPrayerTarget()) {
            changePhase(Phase.REFRESH);
            return;
        }
        changePhase(Phase.TRAVEL);
    }

    private int recoveryPrayerTarget() { return Math.max(1, (int) Math.ceil(Rs2Player.getRealSkillLevel(Skill.PRAYER) * 0.70)); }

    private void refreshPrayer(DroSlayerConfig config) {
        if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= recoveryPrayerTarget()) { changePhase(Phase.TRAVEL); return; }
        if (Rs2Bank.isOpen()) { act(() -> { Rs2Bank.closeBank(); return true; }); return; }
        if (Rs2Player.getWorldLocation().distanceTo(FEROX_POOL) > 5) {
            DroSlayerRuntime.walkFull(config, FEROX_POOL, 1);
            return;
        }
        Rs2TileObjectModel pool = Microbot.getRs2TileObjectCache().query().withName("Pool of Refreshment").nearestOnClientThread();
        if (pool == null || !pool.isReachable()) {
            DroSlayerRuntime.walkFull(config, FEROX_POOL, 1);
            return;
        }
        DroSlayerRuntime.cancelWalk();
        act(() -> pool.click("Drink"));
    }

    /** Each successful maintenance action owns this tick; never interrupt a cannon/grave click with a second action. */
    private boolean maintainSurvival(DroSlayerConfig config) {
        boolean wilderness = Rs2Pvp.isInWilderness();
        if (Rs2Player.getHealthPercentage() < Math.max(60, config.eatAtHealthPercent())) {
            Rs2ItemModel food = Rs2Inventory.items().filter(i -> DroSlayerWildernessSupplies.isFood(i, wilderness)).findFirst().orElse(null);
            if (food != null && act(() -> Rs2Inventory.interact(food, "Eat"))) return true;
            if (food == null && wilderness && phase != Phase.RETURN_BANK) {
                endOptionalRetrieval("Recovery exhausted usable food; returning to bank for normal regear");
                return true;
            }
        }
        int prayerRestoreAt = Math.max(config.drinkPrayerAt(), (int) Math.ceil(Rs2Player.getRealSkillLevel(Skill.PRAYER) * 0.70));
        if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= prayerRestoreAt) {
            Rs2ItemModel potion = Rs2Inventory.items().filter(i -> DroSlayerWildernessSupplies.isPrayerPotion(i, wilderness)).findFirst().orElse(null);
            if (potion != null && act(() -> Rs2Inventory.interact(potion, "Drink"))) return true;
        }
        if (wilderness && Rs2Player.getBoostedSkillLevel(Skill.PRAYER) == 0 && phase != Phase.RETURN_BANK) {
            endOptionalRetrieval("Recovery ran out of Prayer for Protect Item; returning to bank for normal regear");
            return true;
        }
        if (wilderness && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_ITEM)) {
            return act(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, true));
        }
        return false;
    }

    private void collectCannon(DroSlayerConfig config) {
        if (hasAllCannonParts()) {
            cannonResolved = true;
            log.info("Wilderness recovery verified all four cannon parts in inventory");
            changePhase(Phase.GRAVE);
            return;
        }
        WorldPoint owner = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int coord = Microbot.getClient().getVarpValue(VarPlayer.CANNON_COORD);
            return coord > 0 ? WorldPoint.fromCoord(coord) : null;
        }).orElse(null);
        Rs2TileObjectModel cannon = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().query()
                        .where(o -> CANNON_IDS.contains(o.getId()) && matchesCannonSite(o.getWorldLocation(), cannonTile, owner))
                        .nearestOnClientThread()).orElse(null);
        if (cannon == null) {
            // A missing object is not proof of collection. Try Nulodion before falling back to normal combat.
            if (elapsedMillis() > 10_000L) {
                log.info("Saved Wilderness cannon absent; attempting Nulodion reclaim");
                changePhase(Phase.NULODION);
            }
            return;
        }
        if (Rs2Inventory.emptySlotCount() < 4) { skipCannon("No room for optional recovered cannon parts"); return; }
        if (Rs2Player.getWorldLocation().distanceTo(cannon.getWorldLocation()) > 4) {
            DroSlayerRuntime.walkFull(config, cannon.getWorldLocation(), 3);
            return;
        }
        DroSlayerRuntime.cancelWalk();
        act(() -> cannon.click("Pick-up"));
    }

    private void collectGrave(DroSlayerConfig config) {
        if (!Rs2Death.hasGrave()) { changePhase(Phase.RETURN_BANK); return; }
        var grave = Rs2Death.getGrave();
        if (grave == null) {
            if (deathPoint != null) DroSlayerRuntime.walkFull(config, deathPoint, 4);
            return;
        }
        if (Rs2Player.getWorldLocation().distanceTo(grave.getWorldLocation()) > 5) {
            DroSlayerRuntime.walkFull(config, grave.getWorldLocation(), 4);
            return;
        }
        DroSlayerRuntime.cancelWalk();
        if (!Rs2Death.isGraveOpen()) { act(Rs2Death::openGrave); return; }
        if (!Rs2Death.getGraveFreeItems().isEmpty()) {
            if (Rs2Inventory.emptySlotCount() == 0) { finishGraveWhenFull("free"); return; }
            act(Rs2Death::lootGraveFreeItems);
            return;
        }
        // Zero fee still means a real take action is needed; no paid fees are authorised by this coordinator.
        if (!Rs2Death.getGravePaidItems().isEmpty() && Rs2Death.getGraveFee() == 0) {
            if (Rs2Inventory.emptySlotCount() == 0) { finishGraveWhenFull("no-fee"); return; }
            act(() -> Rs2Death.lootGravePaidItems(0));
            return;
        }
        if (!Rs2Death.getGravePaidItems().isEmpty()) log.info("Leaving paid grave items; recovery reclaim budget is zero");
        if (act(() -> { Rs2Death.closeInterfaces(); return true; })) changePhase(Phase.RETURN_BANK);
    }

    private void finishGraveWhenFull(String category) {
        log.warn("Recovery pack is full after cannon collection; returning to bank with {} grave items remaining", category);
        if (act(() -> { Rs2Death.closeInterfaces(); return true; })) changePhase(Phase.RETURN_BANK);
    }

    private void recoverFromNulodion(DroSlayerConfig config) {
        if (hasAllCannonParts()) { cannonResolved = true; changePhase(Phase.GRAVE); return; }
        if (Rs2Pvp.isInWilderness()) {
            if (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 30 && !Rs2Player.isTeleBlocked()) {
                attemptEscape();
            } else {
                DroSlayerRuntime.walkFull(config, new WorldPoint(3087, 3520, 0), 3);
            }
            return;
        }
        if (!DroSlayerRuntime.walkFull(config, NULODION, 2)) return;
        DroSlayerRuntime.cancelWalk();
        if (nulodionInteractionStarted == 0L) nulodionInteractionStarted = System.nanoTime();
        // Start the short reclaim watchdog only after arrival; travel owns the wider phase budget.
        if ((System.nanoTime() - nulodionInteractionStarted) / 1_000_000L > 60_000L) {
            skipCannon("Nulodion did not return the optional cannon; normal Slayer combat will resume");
            return;
        }
        if (Rs2Dialogue.hasSelectAnOption()) { act(() -> Rs2Dialogue.clickOption("I've lost my cannon", "I have lost my cannon")); return; }
        if (Rs2Dialogue.hasContinue()) { act(() -> { Rs2Dialogue.clickContinue(); return true; }); return; }
        var npc = Microbot.getRs2NpcCache().query().withName("Nulodion").nearestOnClientThread();
        if (npc != null) act(() -> npc.click("Talk-to"));
        // Still-deployed cannons cannot be claimed here; the arrival watchdog retains the original site.
    }

    private void returnToBank(DroSlayerConfig config) {
        if (Rs2Death.isGraveOpen()) { act(() -> { Rs2Death.closeInterfaces(); return true; }); return; }
        if (Rs2Pvp.isInWilderness()) {
            if (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 30 && !Rs2Player.isTeleBlocked()) {
                attemptEscape();
            } else {
                DroSlayerRuntime.walkFull(config, new WorldPoint(3087, 3520, 0), 3);
            }
            return;
        }
        if (!DroSlayerRuntime.walkToBankAndOpen(config, "Wilderness recovery return")) return;
        if (!cannonResolved) { skipCannon("Optional cannon unresolved when returning to bank"); return; }
        if (failureReason != null) { block(failureReason); return; }
        Rs2Death.clearDeathState();
        changePhase(Phase.COMPLETE);
        log.info("Wilderness recovery trip complete at bank: cannonRecovered={} cannonUnavailable={}; caller must regear and choose a new safe world",
                cannonTile != null && !cannonUnavailable, cannonUnavailable);
    }

    private boolean ensureOriginalCannonWorld(DroSlayerConfig config) {
        if (hasAllCannonParts()) { cannonResolved = true; return true; }
        if (cannonWorld <= 0) {
            // A tile alone cannot establish which world owns the deployed cannon.
            changePhase(Phase.NULODION);
            return false;
        }
        if (currentWorld() == cannonWorld) return true;
        // Never open a hopper while exposed to aggressive NPCs or an active player attack.
        if (Rs2Pvp.isInWilderness()) {
            if (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 30 && !Rs2Player.isTeleBlocked()) attemptEscape();
            else DroSlayerRuntime.walkFull(config, new WorldPoint(3087, 3520, 0), 3);
            return false;
        }
        if (Rs2Player.isInCombat()) return false;
        boolean interacting = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getLocalPlayer() == null
                        || (Microbot.getClient().getLocalPlayer().getInteracting() != null
                            && Microbot.getClient().getLocalPlayer().getInteracting().getCombatLevel() > 0)).orElse(true);
        if (interacting) return false;
        if (Rs2Bank.isOpen()) { act(() -> { Rs2Bank.closeBank(); return true; }); return false; }
        long now = System.currentTimeMillis();
        if (now - returnWorldAttemptAt < DroSlayerHopPolicy.RETRY_GAP_MS) return false;
        if (returnWorldAttempts >= Math.max(1, config.maxRecoveryAttempts())) {
            skipCannon("Could not rejoin the original cannon world " + cannonWorld);
            return false;
        }
        var worlds = Microbot.getWorldService().getWorlds();
        boolean members = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.MEMBERS)).orElse(true);
        boolean eligible = worlds != null
                && DroSlayerHopPolicy.eligible(worlds.findWorld(cannonWorld), members, currentWorld());
        if (eligible) {
            act(() -> {
                returnWorldAttemptAt = System.currentTimeMillis();
                returnWorldAttempts++;
                return Microbot.hopToWorld(cannonWorld);
            });
        } else {
            returnWorldAttemptAt = now;
            returnWorldAttempts++;
            log.warn("Original cannon world {} unavailable or has a warning; recovery hop attempt {}/{}", cannonWorld, returnWorldAttempts, config.maxRecoveryAttempts());
        }
        return false;
    }

    private boolean hasRecoveryTarget() { return !cannonResolved || Rs2Death.hasGrave(); }

    private static boolean emergencyEscapeActive() {
        String state = DroSlayerRuntime.wildernessEscapeState();
        return "ESCAPING".equals(state) || "LOGOUT_WAIT_COMBAT".equals(state)
                || "LOGGED_OUT".equals(state) || "BLOCKED".equals(state);
    }

    private boolean ensureRecoverySafeSettings() {
        // Settings UI must not interrupt the Walker's threat response, including its wait for a safe logout.
        if (emergencyEscapeActive()) return false;
        if (safetySettings.ensureSafeSettings()) return true;
        DroSlayerRuntime.cancelWalk();
        return false;
    }

    private void endOptionalRetrieval(String reason) {
        if (!cannonResolved) { skipCannon(reason); return; }
        DroSlayerRuntime.cancelWalk();
        log.warn("{}; remaining grave retrieval is optional", reason);
        changePhase(Phase.RETURN_BANK);
    }

    private void skipCannon(String reason) {
        cannonUnavailable = true;
        cannonResolved = true;
        DroSlayerRuntime.cancelWalk();
        log.warn("{}; saved cannon site={} world={}, cannon is optional", reason, cannonTile, cannonWorld);
        changePhase(Phase.RETURN_BANK);
    }

    private static int currentWorld() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWorld()).orElse(0);
    }

    private void attemptEscape() {
        DroSlayerRuntime.cancelWalk();
        Rs2ItemModel item = findEscapeItem();
        if (item == null) { block("Recovery escape teleport is missing"); return; }
        String destination = escapeDestination(item.getName());
        if (Rs2Dialogue.hasSelectAnOption()) { act(() -> Rs2Dialogue.clickOption(destination)); return; }
        String action = item.getAction(destination.toLowerCase(java.util.Locale.ROOT));
        if (action == null && item.getIndexOfSubAction(destination) != null) action = destination;
        if (action == null) action = item.getActionFromList(Arrays.asList("Commune", "Rub", "Teleport", "Invoke"));
        if (action == null) { block("No usable recovery teleport action on " + item.getName()); return; }
        final String selectedAction = action;
        act(() -> Rs2Inventory.interact(item, selectedAction));
    }

    private Rs2ItemModel findEscapeItem() {
        return Rs2Inventory.get(i -> i != null && !i.isNoted() && escapeDestination(i.getName()) != null);
    }

    static String escapeDestination(String name) {
        return WildernessCombatEscape.isLevel30Escape(name)
                ? WildernessCombatEscape.destinationAction(name) : null;
    }

    static boolean matchesCannonSite(WorldPoint object, WorldPoint saved, WorldPoint owner) {
        if (object == null || saved == null || object.getPlane() != saved.getPlane() || object.distanceTo(saved) > 2) return false;
        return owner == null || (object.getPlane() == owner.getPlane() && object.distanceTo(owner) <= 2);
    }

    static int recoveryFoodTarget(int configured) { return Math.max(5, Math.min(10, configured)); }
    static boolean hasMinimumRecoveryFood(int carried) { return carried >= 5; }

    private boolean hasAllCannonParts() {
        for (String part : CANNON_PARTS) {
            if (!Rs2Inventory.items().anyMatch(i -> i.getName() != null && i.getName().startsWith(part) && !i.isNoted())) return false;
        }
        return true;
    }

    private boolean act(BooleanSupplier action) {
        long now = System.nanoTime();
        if ((now - lastAction) / 1_000_000L < ACTION_GAP_MS) return false;
        lastAction = now;
        return DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true, action);
    }

    private void changePhase(Phase next) {
        phase = next;
        phaseStarted = System.nanoTime();
        suspendedAt = 0L;
        lastAction = 0L;
        nulodionInteractionStarted = 0L;
        log.info("Wilderness recovery phase={}", next);
    }
    private long elapsedMillis() { return (System.nanoTime() - phaseStarted) / 1_000_000L; }
    private Result block(String reason) {
        failureReason = reason;
        DroSlayerRuntime.cancelWalk();
        changePhase(Phase.BLOCKED);
        if (Rs2Death.isGraveOpen()) {
            DroSlayerRuntime.performAction(BaseProfileDro.ActionPhase.MAINTENANCE, true,
                    (Runnable) Rs2Death::closeInterfaces);
        }
        Microbot.status = reason;
        log.warn("Wilderness recovery blocked: {} cannon={} death={}", reason, cannonTile, deathPoint);
        return Result.BLOCKED;
    }
}
