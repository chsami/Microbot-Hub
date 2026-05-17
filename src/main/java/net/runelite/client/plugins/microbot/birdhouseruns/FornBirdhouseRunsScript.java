package net.runelite.client.plugins.microbot.birdhouseruns;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;

import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsInfo.states;
import net.runelite.client.plugins.microbot.birdhouseruns.enums.Log;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsInfo.*;

@Slf4j
public class FornBirdhouseRunsScript extends Script {
    private static final WorldPoint birdhouseLocation1 = new WorldPoint(3763, 3755, 0);
    private static final WorldPoint birdhouseLocation2 = new WorldPoint(3768, 3761, 0);
    private static final WorldPoint birdhouseLocation3 = new WorldPoint(3677, 3882, 0);
    private static final WorldPoint birdhouseLocation4 = new WorldPoint(3679, 3815, 0);
    // Each location maps to a BIRDHOUSE_TRANSMIT_* varp. See isEmpty/isBuilt/isSeeded
    // below for the canonical state decoding (matches RuneLite's BirdHouseState).
    private static final int VARP_HOUSE_1 = VarPlayerID.BIRDHOUSE_TRANSMIT_D; // Verdant SW
    private static final int VARP_HOUSE_2 = VarPlayerID.BIRDHOUSE_TRANSMIT_C; // Verdant NE
    private static final int VARP_HOUSE_3 = VarPlayerID.BIRDHOUSE_TRANSMIT_A; // Meadow N
    private static final int VARP_HOUSE_4 = VarPlayerID.BIRDHOUSE_TRANSMIT_B; // Meadow S
    private static final int ARRIVAL_RADIUS = 4;
    private static final int SCENE_INTERACT_RANGE = 25;
    // Canonical Fossil Island region IDs (matches RuneLite's BirdHouseTracker).
    private static final java.util.Set<Integer> FOSSIL_ISLAND_REGIONS = java.util.Set.of(
            14650, 14651, 14652, 14906, 14907, 15162, 15163);
    // Single source of truth: a birdhouse-accepted seed is one whose item name
    // (lowercased) is in this set. Bank lookup, inventory lookup, and seed pick
    // all match the same way — no id-list drift, no placeholder/variant gotchas.
    // Set lists every allotment/hop/flower seed birdhouses accept (Farming
    // level ≤ 35, per OSRS Wiki).
    private static final Set<String> BIRDHOUSE_SEED_NAMES = Set.of(
            "potato seed",
            "onion seed",
            "cabbage seed",
            "tomato seed",
            "sweetcorn seed",
            "strawberry seed",
            "barley seed",
            "hammerstone seed",
            "asgarnian seed",
            "jute seed",
            "yanillian seed",
            "krandorian seed",
            "wildblood seed",
            "marigold seed",
            "rosemary seed",
            "nasturtium seed",
            "woad seed",
            "limpwurt seed"
    );
    private static final long STATE_STALL_TIMEOUT_MS = 120_000L;
    private boolean initialized;
    private String setupErrorMessage = "";
    private states lastObservedStatus;
    private long stateEnteredAtMs;
    @Inject
    private Notifier notifier;
    private final FornBirdhouseRunsPlugin plugin;
    private final FornBirdhouseRunsConfig config;

    @Inject
    FornBirdhouseRunsScript(FornBirdhouseRunsPlugin plugin, FornBirdhouseRunsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = true;
        botStatus = states.GEARING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
             
                if (!initialized) {
                    if (Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED) {
                        log.error("Birdhouse run failed, you need to finish the quest 'BONE VOYAGE'");
                        this.shutdown();
                        return;
                    }
                    initialized = true;
                    
                    if (config.useInventorySetup()) {
                        boolean hasInventorySetup = config.inventorySetup() != null && Rs2InventorySetup.isInventorySetup(config.inventorySetup().getName());
                        if (hasInventorySetup) {
                            var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                            if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                                Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                                if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                                    log.error("Birdhouse run failed to load inventory setup");
                                    this.shutdown();
                                    return;
                                }
                                if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                            }
                        } else {
                            log.error("Failed to load inventory, inventory setup not found: {}", config.inventorySetup());
                            this.shutdown();
                            return;
                        }
                    } else if (!hasRequiredInventory()) {
                        // Auto bank withdrawal — only if inventory isn't already prepared.
                        if (!setupManualInventory()) {
                            log.error("Birdhouse run failed: {}", setupErrorMessage);
                            this.shutdown();
                            return;
                        }
                    } else {
                        log.info("Inventory already prepared — skipping bank trip");
                    }
                    botStatus = states.TELEPORTING;
                }
                if (!super.run()) return;

                boolean advanced = true;
                while (advanced) {
                    advanced = false;
                    if (botStatus != lastObservedStatus) {
                        log.info("State → {} (player at {}, region={}, onFossilIsland={}, inv=[{}])",
                                botStatus,
                                Rs2Player.getWorldLocation(),
                                Rs2Player.getWorldLocation() == null ? "null" : Rs2Player.getWorldLocation().getRegionID(),
                                isOnFossilIsland(),
                                dumpInventory());
                        lastObservedStatus = botStatus;
                        stateEnteredAtMs = System.currentTimeMillis();
                    } else if (botStatus != states.FINISHED
                            && System.currentTimeMillis() - stateEnteredAtMs > STATE_STALL_TIMEOUT_MS) {
                        log.error("Birdhouse run stalled in state {} for >{}ms — player at {}, inv=[{}] — aborting",
                                botStatus, STATE_STALL_TIMEOUT_MS,
                                Rs2Player.getWorldLocation(), dumpInventory());
                        shutdown();
                        return;
                    }
                    switch (botStatus) {
                        case TELEPORTING:
                        case VERDANT_TELEPORT:
                            botStatus = states.DISMANTLE_HOUSE_1;
                            advanced = true;
                            break;
                        case DISMANTLE_HOUSE_1:
                            if (dismantleBirdhouse(birdhouseLocation1, VARP_HOUSE_1)) {
                                botStatus = states.BUILD_HOUSE_1;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_1:
                            if (buildBirdhouse(birdhouseLocation1, VARP_HOUSE_1)) {
                                botStatus = states.SEED_HOUSE_1;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_1:
                            if (seedHouse(birdhouseLocation1, VARP_HOUSE_1)) {
                                botStatus = states.DISMANTLE_HOUSE_2;
                                advanced = true;
                            }
                            break;
                        case DISMANTLE_HOUSE_2:
                            if (dismantleBirdhouse(birdhouseLocation2, VARP_HOUSE_2)) {
                                botStatus = states.BUILD_HOUSE_2;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_2:
                            if (buildBirdhouse(birdhouseLocation2, VARP_HOUSE_2)) {
                                botStatus = states.SEED_HOUSE_2;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_2:
                            if (seedHouse(birdhouseLocation2, VARP_HOUSE_2)) {
                                botStatus = states.MUSHROOM_TELEPORT;
                                advanced = true;
                            }
                            break;
                        case MUSHROOM_TELEPORT:
                            botStatus = states.DISMANTLE_HOUSE_3;
                            advanced = true;
                            break;
                        case DISMANTLE_HOUSE_3:
                            if (dismantleBirdhouse(birdhouseLocation3, VARP_HOUSE_3)) {
                                botStatus = states.BUILD_HOUSE_3;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_3:
                            if (buildBirdhouse(birdhouseLocation3, VARP_HOUSE_3)) {
                                botStatus = states.SEED_HOUSE_3;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_3:
                            if (seedHouse(birdhouseLocation3, VARP_HOUSE_3)) {
                                botStatus = states.DISMANTLE_HOUSE_4;
                                advanced = true;
                            }
                            break;
                        case DISMANTLE_HOUSE_4:
                            if (dismantleBirdhouse(birdhouseLocation4, VARP_HOUSE_4)) {
                                botStatus = states.BUILD_HOUSE_4;
                                advanced = true;
                            }
                            break;
                        case BUILD_HOUSE_4:
                            if (buildBirdhouse(birdhouseLocation4, VARP_HOUSE_4)) {
                                botStatus = states.SEED_HOUSE_4;
                                advanced = true;
                            }
                            break;
                        case SEED_HOUSE_4:
                            if (seedHouse(birdhouseLocation4, VARP_HOUSE_4)) {
                                botStatus = states.FINISHING;
                                advanced = true;
                            }
                            break;
                        case FINISHING:
                            emptyNests();

                            if (config.goToBank()) {
                                Rs2Walker.walkTo(BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint());
                                if (!Rs2Bank.isOpen()) Rs2Bank.openBank();
                                Rs2Bank.depositAll();
                            }

                            botStatus = states.FINISHED;
                            notifier.notify(Notification.ON, "Birdhouse run is finished.");
                            log.info("Birdhouse run finished — disabling plugin.");
                            Microbot.stopPlugin(plugin);
                            break;
                        case FINISHED:
                            break;
                    }
                }

            } catch (Exception ex) {
                log.error("Error in birdhouse run script", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void emptyNests() {
        var ids = List.of(
                ItemID.BIRD_NEST_EGG_RED,
                ItemID.BIRD_NEST_EGG_GREEN,
                ItemID.BIRD_NEST_EGG_BLUE,
                ItemID.BIRD_NEST_SEEDS,
                ItemID.BIRD_NEST_RING,
                ItemID.BIRD_NEST_SEEDS_JAN2019,
                ItemID.BIRD_NEST_DECENTSEEDS_JAN2019
        );

        Rs2Inventory.items().forEachOrdered(item -> {
            if (ids.contains(item.getId())) {
                Rs2Inventory.interact(item, "Search");
            }
        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        initialized = false;
        botStatus = states.TELEPORTING;
        lastObservedStatus = null;
        stateEnteredAtMs = 0L;
    }

    /** Throttle for arrivedAndStill log lines (one per second per target). */
    private long lastArrivedLogMs;
    private WorldPoint lastArrivedLogTarget;

    private boolean arrivedAndStill(WorldPoint loc) {
        WorldPoint pos = Rs2Player.getWorldLocation();
        int dist = Rs2Player.distanceTo(loc);
        if (dist <= ARRIVAL_RADIUS) {
            return true;
        }
        boolean moving = Rs2Player.isMoving();
        long now = System.currentTimeMillis();
        boolean logThisTick = !loc.equals(lastArrivedLogTarget) || now - lastArrivedLogMs >= 1000;
        if (moving) {
            if (logThisTick) {
                log.info("arrivedAndStill[{}]: moving (at {}, dist={})", loc, pos, dist);
                lastArrivedLogMs = now;
                lastArrivedLogTarget = loc;
            }
            return false;
        }
        if (logThisTick) {
            log.info("arrivedAndStill[{}]: not arrived (at {}, dist={}); walking via WebWalker (stop at {})",
                    loc, pos, dist, SCENE_INTERACT_RANGE);
            lastArrivedLogMs = now;
            lastArrivedLogTarget = loc;
        }
        Rs2Walker.walkTo(loc, SCENE_INTERACT_RANGE);
        return false;
    }

    // Canonical state predicates, matching BirdHouseState.fromVarpValue:
    //   varp == 0          → EMPTY space (need to Build)
    //   varp > 0, %3 != 0  → BUILT (covers "just built, no seeds" and "seeded, growing")
    //   varp > 0, %3 == 0  → SEEDED, ready (Empty action available)
    private static boolean isEmpty(int varp)  { return varp == 0; }
    private static boolean isBuilt(int varp)  { return varp > 0 && varp % 3 != 0; }
    private static boolean isSeeded(int varp) { return varp > 0 && varp % 3 == 0; }

    /** Click Empty on the birdhouse at {@code loc}. Wait for varp to hit 0. */
    private boolean dismantleBirdhouse(WorldPoint loc, int varpId) {
        int varp = Microbot.getVarbitPlayerValue(varpId);
        if (!isSeeded(varp)) {
            log.info("Dismantle[{}]: varp={} not seeded (empty={}, built={}) — skipping",
                    varpId, varp, isEmpty(varp), isBuilt(varp));
            return true;
        }
        log.info("Dismantle[{}]: varp={} → Empty at {}", varpId, varp, loc);
        if (!Rs2GameObject.interact(loc, "Empty")) {
            if (!arrivedAndStill(loc)) return false;
            log.warn("Dismantle[{}]: object not found at {} after arriving", varpId, loc);
            return false;
        }
        if (!sleepUntil(() -> isEmpty(Microbot.getVarbitPlayerValue(varpId)), 10000)) {
            log.warn("Dismantle[{}]: timeout waiting for varp→0 after Empty click; varp={} (player at {})",
                    varpId, Microbot.getVarbitPlayerValue(varpId), Rs2Player.getWorldLocation());
            return false;
        }
        log.info("Dismantle[{}]: success (varp=0)", varpId);
        return true;
    }

    /** Click Build at {@code loc}. Game auto-combines hammer+log. Wait for varp != 0. */
    private boolean buildBirdhouse(WorldPoint loc, int varpId) {
        int varp = Microbot.getVarbitPlayerValue(varpId);
        if (!isEmpty(varp)) {
            log.info("Build[{}]: varp={} not empty (built={}, seeded={}) — skipping",
                    varpId, varp, isBuilt(varp), isSeeded(varp));
            return true;
        }
        int logCount = Rs2Inventory.count(config.logType().getItemId());
        if (logCount == 0) {
            log.error("Build[{}]: no {} (id={}) in inventory — aborting. Inventory: [{}]",
                    varpId, config.logType().getItemName(), config.logType().getItemId(), dumpInventory());
            shutdown();
            return false;
        }
        log.info("Build[{}]: varp=0 → Build at {} (logs in inv: {})", varpId, loc, logCount);
        if (!Rs2GameObject.interact(loc, "Build")) {
            if (!arrivedAndStill(loc)) return false;
            log.warn("Build[{}]: object not found at {} after arriving", varpId, loc);
            return false;
        }
        if (!sleepUntil(() -> !isEmpty(Microbot.getVarbitPlayerValue(varpId)), 15000)) {
            log.warn("Build[{}]: timeout waiting for varp!=0 after Build click; varp={} (player at {}, logs={})",
                    varpId, Microbot.getVarbitPlayerValue(varpId), Rs2Player.getWorldLocation(),
                    Rs2Inventory.count(config.logType().getItemId()));
            return false;
        }
        log.info("Build[{}]: success (varp={})", varpId, Microbot.getVarbitPlayerValue(varpId));
        return true;
    }

    /** Use a seed stack on the birdhouse at {@code loc}. Wait for seeds-down OR varp change. */
    private boolean seedHouse(WorldPoint loc, int varpId) {
        int varp = Microbot.getVarbitPlayerValue(varpId);
        if (isEmpty(varp)) {
            log.error("Seed[{}]: varp=0, can't seed empty space — aborting. Inventory: [{}]",
                    varpId, dumpInventory());
            shutdown();
            return false;
        }
        if (isSeeded(varp)) {
            log.info("Seed[{}]: varp={} already seeded — skipping", varpId, varp);
            return true;
        }
        Rs2ItemModel seed = findInventoryBirdhouseSeed(10).orElse(null);
        if (seed == null) {
            log.error("Seed[{}]: no birdhouse-seed stack of ≥10 — aborting. Inventory: [{}]",
                    varpId, dumpInventory());
            shutdown();
            return false;
        }
        int seedId = seed.getId();
        int seedsBefore = seed.getQuantity();
        int varpBefore = varp;
        log.info("Seed[{}]: use {} id={} (x{}) on {} (varp before={})",
                varpId, seed.getName(), seedId, seedsBefore, loc, varpBefore);
        if (!Rs2Inventory.use(seedId)) {
            log.warn("Seed[{}]: Rs2Inventory.use({}) returned false. Inventory: [{}]",
                    varpId, seedId, dumpInventory());
            return false;
        }
        if (!sleepUntil(() -> Rs2Inventory.getSelectedItemId() == seedId, 2000)) {
            log.warn("Seed[{}]: seed not selected within 2s. getSelectedItemId={}, looking for {}",
                    varpId, Rs2Inventory.getSelectedItemId(), seedId);
            return false;
        }
        log.info("Seed[{}]: seed selected (id={}); clicking birdhouse at {}", varpId, seedId, loc);
        if (!Rs2GameObject.interact(loc)) {
            if (!arrivedAndStill(loc)) return false;
            log.warn("Seed[{}]: object not found at {} after arriving", varpId, loc);
            return false;
        }
        if (!sleepUntil(() ->
                findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0) < seedsBefore,
                10000)) {
            int seedsNow = findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0);
            log.warn("Seed[{}]: no completion signal within 10s. seeds={} (before {}), varp={} (before {})",
                    varpId, seedsNow, seedsBefore,
                    Microbot.getVarbitPlayerValue(varpId), varpBefore);
            return false;
        }
        int seedsAfter = findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0);
        log.info("Seed[{}]: success (varp={}, seeds left={})",
                varpId, Microbot.getVarbitPlayerValue(varpId), seedsAfter);
        return true;
    }

    /** True if {@code item}'s lowercased name is in {@link #BIRDHOUSE_SEED_NAMES}. */
    private static boolean isBirdhouseSeed(Rs2ItemModel item) {
        if (item == null) return false;
        String name = item.getName();
        return name != null && BIRDHOUSE_SEED_NAMES.contains(name.toLowerCase());
    }

    /** First inventory stack of a birdhouse-accepted seed with quantity ≥ {@code minQty}. */
    private static Optional<Rs2ItemModel> findInventoryBirdhouseSeed(int minQty) {
        return Rs2Inventory.items()
                .filter(FornBirdhouseRunsScript::isBirdhouseSeed)
                .filter(item -> item.getQuantity() >= minQty)
                .findFirst();
    }

    /** First bank stack of a birdhouse-accepted seed with quantity ≥ {@code minQty}. */
    private static Optional<Rs2ItemModel> findBankBirdhouseSeed(int minQty) {
        return Rs2Bank.bankItems().stream()
                .filter(FornBirdhouseRunsScript::isBirdhouseSeed)
                .filter(item -> item.getQuantity() >= minQty)
                .findFirst();
    }

    /** Compact "name×qty(id=...)" listing of every inventory item, for diagnostics. */
    private static String dumpInventory() {
        return Rs2Inventory.items()
                .map(item -> item.getName() + "×" + item.getQuantity() + "(id=" + item.getId() + ")")
                .collect(Collectors.joining(", "));
    }

    private boolean isOnFossilIsland() {
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc != null && FOSSIL_ISLAND_REGIONS.contains(loc.getRegionID());
    }

    /** True if the inventory already has everything a full run needs. The digsite
     *  pendant is only required when off Fossil Island (its sole purpose is the
     *  teleport onto the island); on-island, we can just walk. */
    private boolean hasRequiredInventory() {
        if (Rs2Inventory.count(ItemID.CHISEL) < 1) {
            log.info("hasRequiredInventory: no chisel");
            return false;
        }
        if (Rs2Inventory.count(ItemID.HAMMER) < 1) {
            log.info("hasRequiredInventory: no hammer");
            return false;
        }
        if (!isOnFossilIsland()) {
            boolean hasPendant =
                    Rs2Inventory.count(ItemID.NECKLACE_OF_DIGSITE_1) >= 1
                    || Rs2Inventory.count(ItemID.NECKLACE_OF_DIGSITE_2) >= 1
                    || Rs2Inventory.count(ItemID.NECKLACE_OF_DIGSITE_3) >= 1
                    || Rs2Inventory.count(ItemID.NECKLACE_OF_DIGSITE_4) >= 1
                    || Rs2Inventory.count(ItemID.NECKLACE_OF_DIGSITE_5) >= 1;
            if (!hasPendant) {
                log.info("hasRequiredInventory: off-island and no digsite pendant");
                return false;
            }
        }
        int logCount = Rs2Inventory.count(config.logType().getItemId());
        if (logCount < 4) {
            log.info("hasRequiredInventory: only {} {} (need 4)", logCount, config.logType().getItemName());
            return false;
        }
        Optional<Rs2ItemModel> seed = findInventoryBirdhouseSeed(40);
        if (seed.isEmpty()) {
            log.info("hasRequiredInventory: no birdhouse-seed stack ≥ 40 in inventory. Inventory: [{}]",
                    dumpInventory());
            return false;
        }
        log.info("hasRequiredInventory: OK ({} x{}, {} logs)",
                seed.get().getName(), seed.get().getQuantity(), logCount);
        return true;
    }

    private boolean setupManualInventory() {
        log.info("setupManualInventory: start (player at {}, onFossilIsland={}, inv=[{}])",
                Rs2Player.getWorldLocation(), isOnFossilIsland(), dumpInventory());
        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);

        if (!Rs2Bank.openBank()) {
            setupErrorMessage = "Could not open bank";
            log.error(setupErrorMessage);
            return false;
        }
        sleepUntil(Rs2Bank::isOpen);
        log.info("setupManualInventory: bank open at {}", Rs2Player.getWorldLocation());

        Rs2Bank.depositAll();
        Rs2Inventory.waitForInventoryChanges(5000);
        log.info("setupManualInventory: after depositAll, inv=[{}]", dumpInventory());

        if (!Rs2Bank.withdrawX(ItemID.CHISEL, 1)) {
            setupErrorMessage = "Missing chisel in bank";
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        log.info("setupManualInventory: chisel withdrawn (inv count={})", Rs2Inventory.count(ItemID.CHISEL));

        if (!Rs2Bank.withdrawX(ItemID.HAMMER, 1)) {
            setupErrorMessage = "Missing hammer in bank";
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        log.info("setupManualInventory: hammer withdrawn (inv count={})", Rs2Inventory.count(ItemID.HAMMER));

        // Withdraw digsite pendant (prefer lower charges) — only when off Fossil Island.
        // If we're already on the island, the pendant is dead weight; don't burn a charge.
        boolean pendantWithdrawn = isOnFossilIsland();
        if (pendantWithdrawn) {
            log.info("setupManualInventory: on Fossil Island, skipping pendant withdrawal");
        }
        List<Integer> pendantIds = Arrays.asList(
            ItemID.NECKLACE_OF_DIGSITE_1,
            ItemID.NECKLACE_OF_DIGSITE_2,
            ItemID.NECKLACE_OF_DIGSITE_3,
            ItemID.NECKLACE_OF_DIGSITE_4,
            ItemID.NECKLACE_OF_DIGSITE_5
        );

        if (!pendantWithdrawn) {
            for (int pendantId : pendantIds) {
                if (!isRunning()) break;
                if (Rs2Bank.withdrawX(pendantId, 1)) {
                    Rs2Inventory.waitForInventoryChanges(2000);
                    pendantWithdrawn = true;
                    log.info("setupManualInventory: pendant withdrawn (id={})", pendantId);
                    break;
                }
            }
        }

        if (!pendantWithdrawn) {
            setupErrorMessage = "Missing digsite pendant in bank";
            log.error(setupErrorMessage);
            return false;
        }

		Log selectedLogType = config.logType();
        int bankLogCount = Rs2Bank.count(selectedLogType.getItemId());
        if (bankLogCount < 4) {
            setupErrorMessage = "Need 4 " + selectedLogType.getItemName().toLowerCase() + " but only have " + bankLogCount + " in bank";
            log.error(setupErrorMessage);
            return false;
        }
        if (!Rs2Bank.withdrawX(selectedLogType.getItemId(), 4)) {
            setupErrorMessage = "Failed to withdraw " + selectedLogType.getItemName().toLowerCase();
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(2000);
        log.info("setupManualInventory: 4× {} withdrawn (inv count={})",
                selectedLogType.getItemName(), Rs2Inventory.count(selectedLogType.getItemId()));

        if (!withdrawSeeds()) {
            // setupErrorMessage is set in withdrawSeeds
            return false;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());

        log.info("setupManualInventory: complete. Final inv=[{}]", dumpInventory());
        return true;
    }

    private boolean withdrawSeeds() {
        // Log every birdhouse-eligible seed stack the bank has, so we can see
        // both what was picked AND what the alternatives were.
        String bankSeedSummary = Rs2Bank.bankItems().stream()
                .filter(FornBirdhouseRunsScript::isBirdhouseSeed)
                .map(item -> item.getName() + "×" + item.getQuantity() + "(id=" + item.getId() + ")")
                .collect(Collectors.joining(", "));
        log.info("withdrawSeeds: bank birdhouse-seed stacks: [{}]", bankSeedSummary);

        Rs2ItemModel bankSeed = findBankBirdhouseSeed(40).orElse(null);
        if (bankSeed == null) {
            setupErrorMessage = "Need 40 seeds but no birdhouse seed type has 40+ in bank";
            log.error(setupErrorMessage);
            return false;
        }
        int invBefore = Rs2Inventory.count(bankSeed.getId());
        log.info("withdrawSeeds: selected {} (id={}, bank qty={}); inv before withdraw: {} of id={}",
                bankSeed.getName(), bankSeed.getId(), bankSeed.getQuantity(), invBefore, bankSeed.getId());
        if (!Rs2Bank.withdrawX(bankSeed.getId(), 40)) {
            setupErrorMessage = "Failed to withdraw 40 " + bankSeed.getName();
            log.error(setupErrorMessage);
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(3000);
        int invAfter = Rs2Inventory.count(bankSeed.getId());
        int invAfterByName = findInventoryBirdhouseSeed(1).map(Rs2ItemModel::getQuantity).orElse(0);
        log.info("withdrawSeeds: withdrew 40 {} (id={}); inv after = {} of id={} (by-name lookup = {})",
                bankSeed.getName(), bankSeed.getId(), invAfter, bankSeed.getId(), invAfterByName);
        if (invAfter < 40) {
            log.warn("withdrawSeeds: inventory count of id={} after withdraw is {} (<40). Full inv: [{}]",
                    bankSeed.getId(), invAfter, dumpInventory());
        }
        return true;
    }
}
