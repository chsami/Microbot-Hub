package net.runelite.client.plugins.microbot.royaltitans;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.shared.FeroxService;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.royaltitans.RoyalTitansShared.FIRE_TITAN_DEAD_ID;
import static net.runelite.client.plugins.microbot.royaltitans.RoyalTitansShared.FIRE_TITAN_ID;
import static net.runelite.client.plugins.microbot.royaltitans.RoyalTitansShared.ICE_TITAN_DEAD_ID;
import static net.runelite.client.plugins.microbot.royaltitans.RoyalTitansShared.ICE_TITAN_ID;
import static net.runelite.client.plugins.microbot.royaltitans.RoyalTitansShared.ITEMS_TO_LOOT;
import static net.runelite.client.plugins.microbot.royaltitans.RoyalTitansShared.evaluateAndConsumePotions;
import static net.runelite.client.plugins.microbot.royaltitans.RoyalTitansShared.isInBossRegion;
import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.EXTREME;
import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.disableAllPrayers;

@Slf4j
public class RoyalTitansScript extends Script {

    private static final Integer MELEE_TITAN_ICE_REGION_X = 35;
    private static final Integer MELEE_TITAN_FIRE_REGION_X = 26;
    private static final Integer FIRE_MINION_ID = 14150;
    private static final Integer ICE_MINION_ID = 14151;
    private static final Integer FIRE_WALL = 14152;
    private static final Integer ICE_WALL = 14153;
    private static final Integer TUNNEL_ID = 55986;
    private static final Integer WIDGET_START_A_FIGHT = 14352385;
    private static final WorldPoint BOSS_LOCATION = new WorldPoint(2951, 9574, 0);
    private static final WorldArea FIGHT_AREA = new WorldArea(new WorldPoint(2909, 9561, 0), 12, 4);

    @Getter
    @Setter
    private RoyalTitansBotStatus state = RoyalTitansBotStatus.TRAVELLING;
    @Getter
    @Setter
    private String subState = "";
    @Getter
    private int kills = 0;

    @Inject
    private FeroxService feroxService;

    @Inject
    private Rs2NpcCache rs2NpcCache;

    @Inject
    private Rs2TileObjectCache rs2TileObjectCache;

    @Inject
    private Rs2PlayerCache rs2PlayerCache;

    @Inject
    private Client client;

    private Rs2InventorySetup inventorySetup = null;
    private Rs2InventorySetup magicInventorySetup = null;
    private Rs2InventorySetup meleeInventorySetup = null;
    private Rs2InventorySetup specialAttackInventorySetup = null;
    private Rs2InventorySetup rangedInventorySetup = null;
    private RoyalTitansTravelStatus travelStatus = RoyalTitansTravelStatus.TO_BANK;
    private Instant waitingTimeStart = null;
    private boolean waitedLastIteration = false;
    private boolean isRunning;
    private double fireTitanHealthPercentage = 100;
    private double iceTitanHealthPercentage = 100;
    private RoyalTitansConfig config;
    private final AtomicReference<Tile> enrageTile = new AtomicReference<>(null);
    private final List<WorldPoint> dangerousTiles = new ArrayList<>();
    private int minFreeSlots = 0;
    private RoyalTitansConfig.RoyalTitan lootedTitan = null;

    static {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = false;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.profileSwitching = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(EXTREME);
    }

    public boolean run(RoyalTitansConfig config) {
        this.config = config;
        isRunning = true;
        resetEnragedTile();
        waitingTimeStart = null;
        travelStatus = RoyalTitansTravelStatus.TO_BANK;
        state = RoyalTitansBotStatus.TRAVELLING;
        Microbot.enableAutoRunOn = false;

        if (config.overrideState()) {
            state = config.startState();
        }

        meleeInventorySetup = new Rs2InventorySetup(config.meleeEquipment(), mainScheduledFuture);
        magicInventorySetup = new Rs2InventorySetup(config.magicEquipment(), mainScheduledFuture);
        rangedInventorySetup = new Rs2InventorySetup(config.rangedEquipment(), mainScheduledFuture);
        specialAttackInventorySetup = new Rs2InventorySetup(config.specialAttackWeapon(), mainScheduledFuture);
        inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isRunning || !this.isRunning()) {
                    return;
                }

                switch (state) {
                    case BANKING:
                        handleBanking();
                        break;
                    case TRAVELLING:
                        handleTravelling();
                        break;
                    case WAITING:
                        handleWaiting();
                        break;
                    case FIGHTING:
                        handleFighting();
                        break;
                    case LOOTING:
                        handleLooting();
                        break;
                }
            } catch (Exception e) {
                log.error("Exception", e);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !isRunning || !this.isRunning()) {
                    return;
                }

                detectState();
            } catch (Exception e) {
                log.error("Error detecting state", e);
            }
        }, 10000, 10000, TimeUnit.MILLISECONDS);

        return true;
    }

    public void resetEnragedTile() {
        this.enrageTile.set(null);
    }

    public void setEnragedTile(Tile tile) {
        this.enrageTile.set(tile);
    }

    public void increaseKillCount() {
        this.kills++;
    }

    public Tile getEnrageTile() {
        return enrageTile.get();
    }

    public void addDangerousTile(LocalPoint localPoint) {
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
        dangerousTiles.add(worldPoint);
    }

    /**
     * Handles edgecases where the bot get stuck due to the other players actions
     *
     */
    private void detectState() {
        if (RoyalTitansShared.isInBossRegion() && state != RoyalTitansBotStatus.FIGHTING) {
            state = RoyalTitansBotStatus.FIGHTING;
        }
        if (state == RoyalTitansBotStatus.FIGHTING && !RoyalTitansShared.isInBossRegion()) {
            state = RoyalTitansBotStatus.TRAVELLING;
            travelStatus = RoyalTitansTravelStatus.TO_INSTANCE;
        }
    }

    private void equipArmor(Rs2InventorySetup inventorySetup) {
        inventorySetup.wearEquipment();
    }

    private void handleWaiting() {
        resetEnragedTile();

        if (config.soloMode()) {
            state = RoyalTitansBotStatus.TRAVELLING;
            travelStatus = RoyalTitansTravelStatus.TO_INSTANCE;
            evaluateAndConsumePotions(config);
            sleep(1200, 2400);
            return;
        }

        var teammate = rs2PlayerCache.query().withName(config.teammateName()).nearestOnClientThread();

        if (waitingTimeStart == null && teammate == null && !waitedLastIteration) {
            waitingTimeStart = Instant.now();
            waitedLastIteration = true;
            return;
        }

        if (teammate != null) {
            if (teammate.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) < 5) {
                waitedLastIteration = false;
                waitingTimeStart = null;
                state = RoyalTitansBotStatus.TRAVELLING;
                travelStatus = RoyalTitansTravelStatus.TO_INSTANCE;
                evaluateAndConsumePotions(config);
                sleep(1200, 2400);
                return;
            }
        }

        if (waitingTimeStart != null && teammate == null && Instant.now().isAfter(waitingTimeStart.plusSeconds(config.waitingTimeForTeammate()))) {
            log.info("Teammate did not show after {} seconds, shutting down", config.waitingTimeForTeammate());
            shutdown();
        }
    }

    private void handleFighting() {
        handleEscaping();
        findSafeTile();
        handleEating();
        handlePrayers();
        handleMinions();
        handleWalls();
        attackBoss();
    }

    private void handleEating() {
        Rs2Player.eatAt(config.minEatPercent());
        Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
    }

    private void handleEscaping() {
        int currentHealth = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);

        boolean noFood = Rs2Inventory.getInventoryFood().isEmpty();
        boolean noPrayerPotions = Rs2Inventory.items()
                .noneMatch(item -> item != null && item.getName() != null && Rs2Potion.getPrayerPotionsVariants().contains(item.getName()));

        if ((noFood && currentHealth <= config.healthThreshold()) || (noPrayerPotions && currentPrayer <= 60)) {
            resetEnragedTile();
            feroxService.restoreAtFerox();
            state = RoyalTitansBotStatus.TRAVELLING;
            travelStatus = RoyalTitansTravelStatus.TO_BANK;
            Rs2Prayer.disableAllPrayers();
        }
    }

    private void handlePrayers() {
        if (config.enableOffensivePrayer()) {
            handleOffensivePrayers();
        }
        if (Rs2Combat.inCombat()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            return;
        }
        if (rs2NpcCache.query().where(x -> x.getId() == ICE_TITAN_ID || x.getId() == FIRE_TITAN_ID).nearestOnClientThread() != null) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            return;
        }
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
    }

    private void handleOffensivePrayers() {
        var bestMeleePrayer = Rs2Prayer.getBestMeleePrayer();
        var bestRangedPrayer = Rs2Prayer.getBestRangePrayer();

        if (getEnrageTile() != null) {
            Rs2Prayer.toggle(bestMeleePrayer, false);
            Rs2Prayer.toggle(bestRangedPrayer, true);
        } else if (Rs2Player.isInCombat()) {
            var titanToAttack = getTitanToAttack();
            if (titanIsWithinMeleeDistance(titanToAttack)) {
                if (bestMeleePrayer != null && !Rs2Prayer.isPrayerActive(bestMeleePrayer)) {
                    Rs2Prayer.toggle(bestMeleePrayer, true);
                }
            } else if (bestRangedPrayer != null && !Rs2Prayer.isPrayerActive(bestRangedPrayer)) {
                Rs2Prayer.toggle(bestRangedPrayer, true);
            }
        }
    }

    private Rs2NpcModel getTitanToAttack() {
        var iceTitan = rs2NpcCache.query().withId(ICE_TITAN_ID).nearestOnClientThread(20);
        var fireTitan = rs2NpcCache.query().withId(FIRE_TITAN_ID).nearestOnClientThread(20);

        if (config.soloMode()) {
            return selectTitanForSoloMode(iceTitan, fireTitan);
        } else if (fireTitanShouldBeAttacked(fireTitan, iceTitan)) {
            return fireTitan;
        } else {
            return iceTitan;
        }
    }

    private void attackBoss() {
        var iceTitan = rs2NpcCache.query().withId(ICE_TITAN_ID).nearestOnClientThread(20);
        var fireTitan = rs2NpcCache.query().withId(FIRE_TITAN_ID).nearestOnClientThread(20);

        if (iceTitan == null && fireTitan == null) {
            iceTitanHealthPercentage = 100;
            fireTitanHealthPercentage = 100;
            return;
        }

        // Handle enrage tile first
        if (getEnrageTile() != null) {
            subState = "Handling enrage tile";

            if (Rs2Player.getLocalLocation().equals(getEnrageTile().getLocalLocation()) && isTitanAlive(fireTitan) && isTitanAlive(iceTitan)) {
                handleBossAttack();
            }
            return;
        } else if (config.soloMode()) {
            updateHealthPercentages(iceTitan, fireTitan);
        }

        subState = "Attacking titan";
        handleBossAttack();
    }

    private boolean isDangerousTile(WorldPoint worldPoint) {
        return dangerousTiles.contains(worldPoint);
    }

    private void findSafeTile() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (isDangerousTile(playerLocation)) {
            log.info("Finding safe tile");
            List<WorldPoint> nearbyTiles = new ArrayList<>();

            int x = playerLocation.getX();
            int y = playerLocation.getY();
            int plane = playerLocation.getPlane();

            // Offset Y
            for (int dy : List.of(-2, -1, 1, 2)) {
                nearbyTiles.add(new WorldPoint(x, y + dy, plane));
            }

            var safeTile = nearbyTiles.stream().filter(tile -> !isDangerousTile(tile)).findFirst();
            if (safeTile.isPresent()) {
                Rs2Walker.walkFastCanvas(safeTile.get(), true);
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(safeTile.get()));
                dangerousTiles.clear();
            }
        }
    }

    private void handleBossAttack() {
        var titan = getTitanToAttack();
        if (!titan.isReachable()) {
            destroyWalls();
        }

        var specEnergy = Rs2Combat.getSpecEnergy() / 10;
        if (isTitanAlive(titan)) {
            if (titanIsWithinMeleeDistance(titan)) {
                if (specialAttackPossible(specEnergy, titan, RoyalTitansConfig.SpecialAttackWeaponStyle.MELEE)) {
                    specialAttackInventorySetup.wearEquipment();
                    Rs2Combat.setSpecState(true, config.specEnergyConsumed() * 10);
                    sleepUntil(Rs2Combat::getSpecState);
                    titan.click("attack");
                } else {
                    equipArmor(meleeInventorySetup);
                }
            } else {
                if (specialAttackPossible(specEnergy, titan, RoyalTitansConfig.SpecialAttackWeaponStyle.RANGED)) {
                    specialAttackInventorySetup.wearEquipment();
                    Rs2Combat.setSpecState(true, config.specEnergyConsumed() * 10);
                    sleepUntil(Rs2Combat::getSpecState);
                    titan.click("attack");
                } else {
                    equipArmor(rangedInventorySetup);
                }
            }

            NPC attackingNpc = (NPC) Microbot.getClientThread().invoke(() -> Microbot.getClient().getLocalPlayer().getInteracting());
            if (attackingNpc == null || titan.getIndex() != attackingNpc.getIndex()) {
                log.info("Attacking titan: {}", titan.getId());
                titan.click("attack");
            }
        }
    }

    private boolean specialAttackPossible(int specEnergy, Rs2NpcModel titan, RoyalTitansConfig.SpecialAttackWeaponStyle specialAttackWeaponStyle) {
        return config.useSpecialAttacks()
                && config.specialAttackWeaponStyle() == specialAttackWeaponStyle
                && specEnergy >= config.specEnergyConsumed()
                && isTitanAlive(titan)
                && !Rs2Inventory.isFull()
                && getEnrageTile() == null;
    }

    public static boolean isTitanAlive(Rs2NpcModel titan) {
        return titan != null && !titan.isDead();
    }

    private boolean fireTitanShouldBeAttacked(Rs2NpcModel fireTitan, Rs2NpcModel iceTitan) {
        return (config.royalTitanToFocus() == RoyalTitansConfig.RoyalTitan.FIRE_TITAN && isTitanAlive(fireTitan)) || !isTitanAlive(iceTitan);
    }

    private boolean titanIsWithinMeleeDistance(Rs2NpcModel titan) {
        if (enrageTile.get() != null) {
            return false;
        }
        int titanX = titan.getWorldLocation().getRegionX();
        log.info("TitanX: {}", titanX);
        return (titan.getId() == FIRE_TITAN_ID && titanX >= MELEE_TITAN_FIRE_REGION_X) ||
                (titan.getId() == ICE_TITAN_ID && titanX <= MELEE_TITAN_ICE_REGION_X);
    }

    private void handleWalls() {
        if (config.soloMode() || config.minionResponsibility() == RoyalTitansConfig.Minions.NONE) {
            return;
        }
        subState = "Handling walls";

        destroyWalls();
    }

    private void destroyWalls() {
        // For solo mode, handle both types of walls
        List<Rs2NpcModel> walls = new ArrayList<>();
        if (config.soloMode() || config.minionResponsibility() == RoyalTitansConfig.Minions.ALL) {
            List<Rs2NpcModel> fireWalls = rs2NpcCache.query().withId(FIRE_WALL).where(npcInCenterOfArena()).within(6).toListOnClientThread();
            List<Rs2NpcModel> iceWalls = rs2NpcCache.query().withId(ICE_WALL).where(npcInCenterOfArena()).within(6).toListOnClientThread();
            walls.addAll(fireWalls);
            walls.addAll(iceWalls);
        } else {
            walls.addAll(rs2NpcCache.query().withId(config.minionResponsibility() == RoyalTitansConfig.Minions.FIRE_MINIONS ? FIRE_WALL : ICE_WALL).where(npcInCenterOfArena()).toListOnClientThread());
        }

        if (walls.isEmpty() || walls.size() < 2) {
            log.info("No/not enough walls to get rid of.");
            return;
        }

        log.info("Equipping magic armour to get rid of walls.");
        equipArmor(magicInventorySetup);
        sleep(600);
        boolean isWearingTwinflame = Rs2Equipment.isWearing(ItemID.TWINFLAME_STAFF);

        for (var wall : walls) {
            if (wall != null && wall.getId() != -1 && !wall.isDead()) {
                log.info("Getting rid of wall.");
                if (isWearingTwinflame) {
                    wall.click();
                    sleep(600);
                } else {
                    if (wall.getId() == FIRE_WALL) {
                        Rs2Magic.cast(MagicAction.WATER_WAVE);
                        wall.click();
                    } else {
                        Rs2Magic.cast(MagicAction.FIRE_WAVE);
                        wall.click();
                    }
                    sleep(1800);
                }
            }
        }


    }

    @Nonnull
    private static Predicate<Rs2NpcModel> npcInCenterOfArena() {
        return npc -> {
            log.info("RegionX: {}", npc.getWorldLocation().getRegionX());
            return npc.getWorldLocation().getRegionX() == 31;
        };
    }

    private void handleMinions() {
        if (config.minionResponsibility() == RoyalTitansConfig.Minions.NONE) {
            return;
        }
        subState = "Handling minions";

        // For solo mode, handle both types of minions
        List<Rs2NpcModel> minions = new ArrayList<>();
        if (config.soloMode()) {
            var fireMinion = rs2NpcCache.query()
                    .withId(FIRE_MINION_ID)
                    .where(npc -> !npc.isDead())
                    .nearestOnClientThread(6);
            var iceMinion = rs2NpcCache.query()
                    .withId(ICE_MINION_ID)
                    .where(npc -> !npc.isDead())
                    .nearestOnClientThread(6);

            if (fireMinion != null) {
                minions.add(fireMinion);
            }

            if (iceMinion != null) {
                minions.add(iceMinion);
            }
        } else {
            var minion = rs2NpcCache
                    .query()
                    .withId(config.minionResponsibility() == RoyalTitansConfig.Minions.FIRE_MINIONS ? FIRE_MINION_ID : ICE_MINION_ID)
                    .where(npc -> !npc.isDead())
                    .nearestOnClientThread(6);
            if (minion != null) {
                minions.add(minion);
            }
        }

        if (minions.isEmpty()) {
            log.info("No minions to get rid of.");
            return;
        }

        equipArmor(magicInventorySetup);
        sleep(600);

        boolean isWearingTwinflame = Rs2Equipment.isWearing(ItemID.TWINFLAME_STAFF);

        for (var minion : minions) {
            log.info("Attacking minion");
            if (isWearingTwinflame) {
                minion.click();
            } else {
                if (minion.getId() == FIRE_MINION_ID) {
                    Rs2Magic.cast(MagicAction.WATER_WAVE);
                    minion.click();
                } else {
                    Rs2Magic.cast(MagicAction.FIRE_WAVE);
                    minion.click();
                }
            }
            sleep(600);
        }
    }

    private void updateHealthPercentages(Rs2NpcModel iceTitan, Rs2NpcModel fireTitan) {
        if (!isTitanAlive(iceTitan) && !isTitanAlive(fireTitan)) {
            iceTitanHealthPercentage = 100;
            fireTitanHealthPercentage = 100;
            return;
        }

        if (iceTitanHealthPercentage != 100 && !isTitanAlive(iceTitan)) {
            log.info("Ice titan is dead. Set health percentage to 0.");
            iceTitanHealthPercentage = 0;
        }

        if (fireTitanHealthPercentage != 100 && !isTitanAlive(fireTitan)) {
            log.info("Fire titan is dead. Set health percentage to 0.");
            fireTitanHealthPercentage = 0;
        }

        if (isTitanAlive(fireTitan)) {
            double currentFireTitanHealth = fireTitan.getHealthPercentage();
            log.info("Detected health of fire titan: {}", currentFireTitanHealth);

            if (currentFireTitanHealth < 100) { //If they are out of combat for too long, it shows as 100%
                fireTitanHealthPercentage = currentFireTitanHealth;
            }
        }

        if (isTitanAlive(iceTitan)) {
            double currentIceTitanHealth = iceTitan.getHealthPercentage();
            log.info("Detected health of ice titan: {}", currentIceTitanHealth);

            if (currentIceTitanHealth < 100) { //If they are out of combat for too long, it shows as 100%
                iceTitanHealthPercentage = currentIceTitanHealth;
            }
        }

        log.info("Last registered health of ice titan: {}", iceTitanHealthPercentage);
        log.info("Last registered health of fire titan: {}", fireTitanHealthPercentage);
    }

    private Rs2NpcModel selectTitanForSoloMode(Rs2NpcModel iceTitan, Rs2NpcModel fireTitan) {
        if (!isTitanAlive(iceTitan)) {
            log.info("Ice titan is dead, take fire titan");
            return fireTitan;
        }
        if (!isTitanAlive(fireTitan)) {
            log.info("Fire titan is dead, take ice titan");
            return iceTitan;
        }

        if (fireTitanHealthPercentage > 15) {
            return fireTitan;
        }

        return iceTitan;
    }

    private void handleTravelling() {
        Rs2Prayer.disableAllPrayers();
        switch (travelStatus) {
            case TO_BANK:
                if (inventorySetup.doesInventoryMatch() && inventorySetup.doesEquipmentMatch()) {
                    state = RoyalTitansBotStatus.TRAVELLING;
                    travelStatus = RoyalTitansTravelStatus.TO_TITANS;
                    return;
                }
                subState = "Walking to bank";
                Rs2Bank.walkToBank();
                var isAtBank = Rs2Bank.isNearBank(5);
                if (isAtBank) {
                    state = RoyalTitansBotStatus.BANKING;
                }
                break;
            case TO_TITANS:
                subState = "Walking to titans";
                Rs2Walker.walkTo(BOSS_LOCATION, 3);
                var gotToTitans = Rs2Player.distanceTo(BOSS_LOCATION) < 5;
                if (gotToTitans) {
                    state = RoyalTitansBotStatus.WAITING;
                    travelStatus = RoyalTitansTravelStatus.TO_BANK;
                } else {
                    Rs2Walker.walkTo(BOSS_LOCATION, 3);
                }
                break;
            case TO_INSTANCE:
                subState = "Walking to instance";
                var isVisible = Rs2Widget.isWidgetVisible(WIDGET_START_A_FIGHT);
                if (isVisible) {
                    iceTitanHealthPercentage = 100;
                    fireTitanHealthPercentage = 100;
                    if (config.currentBotInstanceOwner()) {
                        sleep(600, 1200);
                        Rs2Widget.clickWidget("Start a fight (Your friends will be able to join you).");
                        sleep(600, 1200);
                        state = RoyalTitansBotStatus.FIGHTING;
                    } else {
                        var teammate = rs2PlayerCache.query().withName(config.teammateName()).nearestOnClientThread();
                            if (teammate != null) {
                            log.info("Waiting for teammate to enter the instance");
                            return;
                        }
                        Rs2Widget.clickWidget("Join a fight.");
                        sleep(1200, 1600);
                        Rs2Keyboard.typeString(config.teammateName());
                        sleep(600, 1200);
                        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                        sleep(600, 1200);
                        state = RoyalTitansBotStatus.FIGHTING;
                        sleep(1200, 1600);
                    }
                } else {
                    var tunnel = rs2TileObjectCache.query().withId(TUNNEL_ID).nearestOnClientThread(20);
                    if (tunnel != null) {
                        tunnel.click("Enter");
                        //Microbot.getClientThread().invoke(() -> tunnel.click("Enter"));
                    }
                }
                break;
        }
    }

    private void handleBanking() {
        subState = "Equipping gear";
        equipArmor(inventorySetup);
        if (!Rs2Bank.isOpen()) {
            log.info("Opening bank.");
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen);
        }

        Rs2Bank.depositAll();
        var items = inventorySetup.getEquipmentItems();
        var inventory = inventorySetup.getInventoryItems();

        for (var item : items) {
            if (item != null && item.getId() != -1) {
                log.info("Wearing {}? {}", item.getName(), Rs2Equipment.isWearing(item.getName(), false));
                if (!Rs2Equipment.isWearing(item.getName(), false)) {
                    log.info("Item ID: {}", item.getId());
                    if (item.getName().equals("Ring of dueling")) {
                        Rs2Bank.withdrawAndEquip(ItemID.RING_OF_DUELING_8);
                    } else {
                        if (item.getQuantity() == 1) {
                            log.info("Withdrawing one");
                            Rs2Bank.withdrawAndEquip(item.getId());
                        } else {
                            log.info("Withdrawing many");
                            Rs2Bank.withdrawXAndEquip(item.getId(), item.getQuantity());
                        }
                    }
                }
            }
        }

        Map<InventorySetupsItem, Integer> result =
                inventory.stream()
                        .collect(Collectors.groupingBy(
                                InventorySetupsItem::getId,
                                LinkedHashMap::new,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        objects -> Map.entry(objects.get(0), objects.size())
                                )
                        ))
                        .values()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a,
                                LinkedHashMap::new
                        ));

        result.forEach((item, quantity) -> {
            if (item != null && item.getId() != -1) {
                if (item.getStackCompare().getType() != 0) {
                    Rs2Bank.withdrawAll(item.getId());
                    sleep(600);
                } else {
                    if (quantity == 1) {
                        Rs2Bank.withdrawOne(item.getId());
                    } else {
                        Rs2Bank.withdrawX(item.getId(), quantity);
                    }
                    sleep(600);
                }
            }
        });

        Rs2Bank.closeBank();
        travelStatus = RoyalTitansTravelStatus.TO_TITANS;
        state = RoyalTitansBotStatus.TRAVELLING;
    }

    private static void lootTitan(Rs2NpcModel iceTitanDead) {
        iceTitanDead.click("Loot");
        Rs2Inventory.waitForInventoryChanges(5000);
    }

    private void lootUntradeableItems() {
        LootingParameters untradeableItemsParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                "untradeable"
        );
        if (Rs2GroundItem.lootUntradables(untradeableItemsParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    private void lootRunes() {
        LootingParameters runesParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                " rune"
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(runesParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    private void lootCoins() {
        LootingParameters coinsParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                "coins"
        );
        if (Rs2GroundItem.lootCoins(coinsParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    private void lootItemsOnName() {
        LootingParameters valueParams = new LootingParameters(
                15,
                1,
                1,
                minFreeSlots,
                false,
                false,
                ITEMS_TO_LOOT
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(valueParams)) {
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
    }

    private void handleLooting() {
        log.info("Handling looting...");
        if (!isInBossRegion()) {
            return;
        }

        var iceTitanDead = rs2NpcCache.query().withId(ICE_TITAN_DEAD_ID).nearestOnClientThread(20);
        var fireTitanDead = rs2NpcCache.query().withId(FIRE_TITAN_DEAD_ID).nearestOnClientThread(20);

        Rs2Prayer.disableAllPrayers();
        if (fireTitanDead != null && iceTitanDead != null) {
            setSubState("Looting ground items");
            lootItemsOnName();
            lootRunes();
            lootCoins();
            lootUntradeableItems();

            setSubState("Handling looting from Titans");
            switch (config.loot()) {
                case ICE_TITAN:
                    lootTitan(iceTitanDead);
                    break;
                case FIRE_TITAN:
                    lootTitan(fireTitanDead);
                    break;
                case ALTERNATE:
                    if (lootedTitan == null || lootedTitan == RoyalTitansConfig.RoyalTitan.FIRE_TITAN) {
                        lootedTitan = RoyalTitansConfig.RoyalTitan.ICE_TITAN;
                        lootTitan(iceTitanDead);
                    } else {
                        lootedTitan = RoyalTitansConfig.RoyalTitan.FIRE_TITAN;
                        lootTitan(fireTitanDead);
                    }
                    break;
                case RANDOM:
                    if (Math.random() < 0.5) {
                        lootTitan(iceTitanDead);
                    } else {
                        lootTitan(fireTitanDead);
                    }
                    break;
            }


            increaseKillCount();
            RoyalTitansShared.evaluateAndConsumePotions(config);
            state = RoyalTitansBotStatus.FIGHTING;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        isRunning = false;
        state = RoyalTitansBotStatus.BANKING;
        travelStatus = RoyalTitansTravelStatus.TO_BANK;
        resetEnragedTile();
        kills = 0;
        disableAllPrayers();
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        log.info("Shutting down Royal Titans script");
    }
}
