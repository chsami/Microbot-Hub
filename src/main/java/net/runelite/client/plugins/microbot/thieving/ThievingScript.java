package net.runelite.client.plugins.microbot.thieving;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingNpc;
import net.runelite.client.plugins.microbot.thieving.enums.ThievingFood;
import net.runelite.client.plugins.microbot.thieving.npc.ThievingNpcStrategy;
import net.runelite.client.plugins.microbot.thieving.npc.WealthyCitizenStrategy;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ThievingScript extends Script {
    private final ThievingConfig config;
    private final ThievingPlugin plugin;

    private WorldPoint startingLocation = null;
    private String startingNpc = null;
    protected volatile boolean cleanNpc = false;

    protected State currentState = State.IDLE;

    @Getter
    private volatile Rs2NpcModel thievingNpc = null;

    @Getter(AccessLevel.PROTECTED)
    private volatile boolean underAttack;

    protected volatile long forceShadowVeilActive = System.currentTimeMillis()-1_000;
    private long nextShadowVeil = 0;
    private long startupGraceUntil = 0;
    private final Map<ThievingNpc, ThievingNpcStrategy> npcStrategies = Map.of(
            ThievingNpc.WEALTHY_CITIZEN, new WealthyCitizenStrategy()
    );

    private static final int DOOR_CHECK_RADIUS = 10;
    private static final ActionTimer DOOR_TIMER = new ActionTimer();
    private final long[] doorCloseTime = new long[3];
    private int doorCloseIndex = 0;
    private long lastAction = Long.MAX_VALUE;
    private long lastHopAt = 0;

    protected static int getCloseDoorTime() {
        return DOOR_TIMER.getRemainingTime();
    }

    @Inject
    Rs2TileItemCache rs2TileItemCache;

    /**
     * Get the total runtime of the script
     *
     * @return the total runtime of the script
     */
    public Instant startTime;
    public Duration getRunTime() {
        if (startTime == null) return Duration.ofSeconds(0);
        return Duration.between(startTime, Instant.now());
    }

    @Inject
    public ThievingScript(final ThievingConfig config, final ThievingPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

ThievingNpcStrategy getActiveStrategy() {
    return npcStrategies.get(config.THIEVING_NPC());
}

    private String getNpcName(Rs2NpcModel npc) {
        if (npc == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(npc::getName).orElse(null);
    }

    private boolean shouldHop() {
        return config.THIEVING_NPC() != ThievingNpc.WEALTHY_CITIZEN;
    }

    private State applyOverride(State state) {
        final ThievingNpcStrategy strategy = getActiveStrategy();
        if (strategy == null) return state;
        final State overridden = strategy.overrideState(this, state);
        return overridden == null ? state : overridden;
    }

    private Predicate<Rs2NpcModel> validateName(Predicate<String> stringPredicate) {
        return npc -> {
            if (npc == null) return false;
            final String name = getNpcName(npc);
            if (name == null) return false;
            return stringPredicate.test(name);
        };
    }

    protected String getThievingNpcName() {
        final Rs2NpcModel npc = thievingNpc;
        if (npc == null) return "null";
        else return getNpcName(thievingNpc);
    }

    /**
     * Ensure we have a current thieving NPC reference; refreshes the cache if needed.
     */
    public Rs2NpcModel ensureThievingNpc() {
        if (isNpcNull(thievingNpc)) {
            thievingNpc = getThievingNpcCache();
        }
        return thievingNpc;
    }

    private Predicate<Rs2NpcModel> getThievingNpcFilter() {
        Predicate<Rs2NpcModel> filter = npc -> true;
        if (net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs().isEmpty()) {
            final ThievingNpc finalNpc = config.THIEVING_NPC();
            if (finalNpc == null) {
                log.error("Config Thieving NPC is null");
                return filter;
            }
            final ThievingNpcStrategy strategy = getActiveStrategy();
            if (strategy != null) {
                final Predicate<Rs2NpcModel> customFilter = strategy.npcFilter(this);
                if (customFilter != null) return customFilter;
            }
            switch (finalNpc) {
                case VYRES:
                    filter = validateName(ThievingData.VYRES::contains);
                    break;
                case ARDOUGNE_KNIGHT:
                    filter = validateName("knight of ardougne"::equalsIgnoreCase);
                    if (config.ardougneAreaCheck()) filter = filter.and(npc -> ThievingData.ARDOUGNE_AREA.contains(npc.getWorldLocation()));
                    break;
                case ELVES:
                    filter = validateName(ThievingData.ELVES::contains);
                    break;
                default:
                    filter = validateName(name -> name.toLowerCase().contains(finalNpc.getName()));
                    break;
            }
        }
        return filter;
    }

    private Rs2NpcModel getThievingNpcCache() {
        final Comparator<Rs2NpcModel> comparator;

        if (config.THIEVING_NPC() == ThievingNpc.VYRES && startingNpc != null) {
            comparator = Comparator
                    .comparing((Rs2NpcModel npc) -> {
                        final String name = getNpcName(npc);
                        return name == null || !startingNpc.equalsIgnoreCase(name);
                    })
                    .thenComparingInt(Rs2NpcModel::getDistanceFromPlayer);
        } else {
            comparator = Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer);
        }

        final Optional<Rs2NpcModel> npcOptional = Microbot.getRs2NpcCache().query()
                .where(getThievingNpcFilter())
                .toListOnClientThread()
                .stream()
                .filter(n -> !isNpcNull(n))
                .min(comparator);

        if (npcOptional.isEmpty()) return null;
        Rs2NpcModel npc = npcOptional.get();
        if (startingNpc == null && config.THIEVING_NPC() == ThievingNpc.VYRES) {
            startingNpc = getNpcName(npc);
            log.debug("Set starting npc to {}", startingNpc);
        }
        
        log.debug("Found new NPC={} to thieve @ {}", getNpcName(npc), toString(npc.getWorldLocation()));
        return npc;
    }

    private <T> T getAttackingNpcs(Function<Stream<Rs2NpcModel>, T> consumer, T defaultValue) {
        final Player me = Microbot.getClient().getLocalPlayer();
        if (me == null) return defaultValue;

        final Rs2NpcModel[] npcs = Microbot.getRs2NpcCache().query().toList().toArray(Rs2NpcModel[]::new);
        if (npcs.length == 0) return defaultValue;

        final Predicate<Rs2NpcModel> customFilter = config.THIEVING_NPC() == ThievingNpc.VYRES ?
                Rs2NpcModel.matches(true, "vyrewatch sentinel") :
                npc -> true;

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                consumer.apply(Arrays.stream(npcs)
                        .filter(npc -> npc.getCombatLevel() > 0)
                        .filter(getThievingNpcFilter().negate())
                        .filter(customFilter)
                        .filter(npc -> !isNpcNull(npc))
                        .filter(n -> me.equals(n.getInteracting()))
                )
        ).orElse(defaultValue);
    }

    private boolean isBeingAttackByNpc() {
        return getAttackingNpcs(npcs -> npcs.findAny().isPresent(), false);
    }

    private Rs2NpcModel getAttackingNpc() {
        final WorldPoint myLoc = Rs2Player.getWorldLocation();
        if (myLoc == null) return null;
        return getAttackingNpcs(npcs ->
                npcs.min(Comparator.comparingInt(npc -> myLoc.distanceTo(npc.getWorldLocation())))
                        .orElse(null), null
        );
    }

    private int getMostExpensiveGroundItemId() {
        final int minPrice = config.keepItemsAboveValue();
        // takes long of client thread if there are a lot of dropped items
        return Microbot.getClientThread().runOnClientThreadOptional(() -> rs2TileItemCache.query()
                .toList()
                .stream()
                .filter(Rs2TileItemModel::isOwned)
                .map(Rs2TileItemModel::getId)
                .distinct()
                .map(id -> {
                    final int price = Microbot.getItemManager().getItemPrice(id);
                    return Map.entry(id, price);
                }).filter(entry -> entry.getValue() >= minPrice)
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse(-1)).orElse(-1);
    }

    private State getCurrentState() {
        // Grace period right after startup/login to allow inventory/bank to populate.
        if (System.currentTimeMillis() < startupGraceUntil) return State.WALK_TO_START;

        if (getMostExpensiveGroundItemId() != -1) return applyOverride(State.LOOT);

        if (config.escapeAttacking() && (underAttack || isBeingAttackByNpc())) {
            if (!underAttack) underAttack = true;
            return applyOverride(State.ESCAPE);
        }

        if (!hasReqs()) return applyOverride(State.BANK);

        if (config.useFood()) {
            boolean needToEat = Rs2Player.getHealthPercentage() <= config.hitpoints();
            boolean needToDrink = config.food() == ThievingFood.ANCIENT_BREW && !Rs2Player.hasPrayerPoints();

            if (needToEat || needToDrink) {
                return applyOverride(State.EAT);
            }

            if (config.food() == ThievingFood.ANCIENT_BREW && 
                Rs2Player.getHealthPercentage() <= 15 &&
                !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.REDEMPTION)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.REDEMPTION, true);
            }
        }

        if (Rs2Inventory.isFull()) return applyOverride(State.DROP);

        if (isNpcNull(thievingNpc) && (thievingNpc = getThievingNpcCache()) == null && shouldHop()) return applyOverride(State.HOP);

        if (config.THIEVING_NPC() == ThievingNpc.VYRES) {
            final WorldPoint[] housePolygon = ThievingData.getVyreHouse(getNpcName(thievingNpc));

            if (Microbot.getRs2NpcCache().query()
                    .where(Rs2NpcModel.matches(true, "Vyrewatch Sentinel"))
                    .toListOnClientThread()
                    .stream()
                    .anyMatch(npc -> isPointInPolygon(housePolygon, npc.getWorldLocation()))) {
                log.debug("Vyrewatch Sentinel inside house");
                return applyOverride(State.HOP);
            }

            if (!isPointInPolygon(housePolygon, thievingNpc.getWorldLocation())) {
                if (!sleepUntil(() -> isPointInPolygon(housePolygon, thievingNpc.getWorldLocation()), 1_200 + (int)(Math.random() * 1_800))) {
                    log.debug("Vyre='{}' outside house @ {}", getNpcName(thievingNpc), toString(thievingNpc.getWorldLocation()));
                    return applyOverride(State.HOP);
                }
            }

            if (!isPointInPolygon(housePolygon, Rs2Player.getWorldLocation())) {
                return applyOverride(State.WALK_TO_START);
            }

            // delayed door closing logic
            List<Rs2TileObjectModel> doors = getDoors(Rs2Player.getWorldLocation(), DOOR_CHECK_RADIUS);
            if (doors.isEmpty()) {
                DOOR_TIMER.unset();
            } else if (DOOR_TIMER.isSet()) {
                if (DOOR_TIMER.isTime()) {
                    final long current = System.currentTimeMillis();
                    // did we close the door 3 times in the last 2min? (probably someone troll opening door)
                    if (Arrays.stream(doorCloseTime).allMatch(time -> time != 0 && current - time < 120_000)) {
                        Arrays.fill(doorCloseTime, 0);
                        return applyOverride(State.HOP);
                    }
                    doorCloseTime[doorCloseIndex] = current;
                    doorCloseIndex = (doorCloseIndex+1) % doorCloseTime.length;
                    return applyOverride(State.CLOSE_DOOR);
                }
            } else {
                // delayed door closing
                log.debug("Found {} open door(s).", doors.size());
                DOOR_TIMER.set(System.currentTimeMillis()+3_000+(int) (Math.random()*4_000));
            }
        }

        if (shouldOpenCoinPouches()) return applyOverride(State.COIN_POUCHES);

        if (shouldCastShadowVeil()) return applyOverride(State.SHADOW_VEIL);
        return applyOverride(State.PICKPOCKET);
    }

    protected boolean shouldRun() {
        if (!Microbot.isLoggedIn()) return false;
        return true;
    }

    public void markActionNow() {
        lastAction = System.currentTimeMillis();
    }

    public void sleepBriefly(int min, int max) {
        sleep(min, max);
    }

    private boolean sleepUntilWithInterrupt(BooleanSupplier awaitedCondition, BooleanSupplier interruptCondition, int time) {
        final AtomicBoolean interrupted = new AtomicBoolean(false);
        final boolean result = sleepUntil(() -> {
            if (interruptCondition.getAsBoolean()) {
                interrupted.set(true);
                return true;
            }
            return awaitedCondition.getAsBoolean();
        }, time);
        if (interrupted.get()) throw new SelfInterruptException("Should not be running");
        return result;
    }

    private boolean sleepUntilWithInterrupt(BooleanSupplier awaitedCondition, int time) {
        return sleepUntilWithInterrupt(awaitedCondition, () -> !shouldRun(), time);
    }

    private boolean walkTo(String info, WorldPoint dst, int distance) {
        if (dst == null) {
            log.error("{} to null", info);
            return false;
        }
        log.debug("{} to {}", info, toString(dst));
        final WalkerState walkerState = Rs2Walker.walkWithState(dst, distance);
        log.debug("{} @ {} - dst={}", walkerState, Rs2Player.getWorldLocation(), dst);
        return walkerState == WalkerState.ARRIVED;
    }

    private void loop() {
        if (!shouldRun()) return;
        if (cleanNpc) {
            cleanNpc = false;
            thievingNpc = null;
        }
        if (startingLocation == null) {
            final WorldPoint loc = Rs2Player.getWorldLocation();
            if (loc != null) {
                startingLocation = loc;
                log.debug("Set starting location to {}", loc);
            }
        }

        currentState = getCurrentState();
        if (currentState.isAwaitStuns()) { // some actions like eating/dropping can be done while stunned
            // await stun from most recent pickpocket action
            if (lastAction+600 > System.currentTimeMillis() && (currentState != State.PICKPOCKET || !config.ignoreStuns())) {
                currentState = State.STUNNED;
                sleepUntilWithInterrupt(Rs2Player::isStunned, 600);
                sleepUntilWithInterrupt(() -> !Rs2Player.isStunned(), 10_000);
                return;
            }
        }

        if (currentState != State.PICKPOCKET) log.debug("State {}", currentState);
        final WorldPoint myLoc;

        switch(currentState) {
            case LOOT:
                final int id = getMostExpensiveGroundItemId();
                if (id == -1) return;
                if (Rs2Inventory.isFull()) dropAllExceptImportant();
                final Rs2TileItemModel item = Microbot.getRs2TileItemCache().query().withId(id).within(50).nearest();
                if (item == null) {
                    log.warn("Loot Item is null");
                    return;
                }
                walkTo("Walk to loot", item.getWorldLocation(), 1);
                item.click("Take");
                return;
            case ESCAPE:
                WorldPoint escape = null;
                final String escapeLocationString = config.customEscapeLocation();
                if (!escapeLocationString.isBlank()) {
                    final String[] split = Arrays.stream(escapeLocationString.split(",")).map(String::strip).toArray(String[]::new);
                    if (split.length == 3) {
                        try {
                            escape = new WorldPoint(Integer.parseInt(split[0]),Integer.parseInt(split[1]),Integer.parseInt(split[2]));
                        } catch (NumberFormatException e) {
                            log.error("Invalid custom escape location={}", escapeLocationString);
                        }
                    }
                }
                if (escape == null) {
                    if (thievingNpc == null) thievingNpc = getThievingNpcCache();
                final String name = thievingNpc == null ? null : getNpcName(thievingNpc);
                escape = name == null ? ThievingData.NULL_WORLD_POINT : ThievingData.getVyreEscape(name);
                }
                if (escape != ThievingData.NULL_WORLD_POINT) {
                    walkTo("Escaping", escape, 3);
                    myLoc = Rs2Player.getWorldLocation();
                    if (myLoc != null && myLoc.distanceTo(escape) < 10) {
                        if (underAttack) {
                            underAttack = false;
                            if (!isRunning()) return;
                            if (shouldHop()) {
                                hopWorld();
                            } else {
                                log.debug("Skipping world hop (Wealthy Citizen)");
                            }
                        }

                        if (walkTo("Walk to start", startingLocation, 3)) {
                            sleepUntilWithInterrupt(() -> !Rs2Player.isMoving(), 1_200);
                        }
                        DOOR_TIMER.set();
                        return;
                    } else {
                        log.error("Failed to use escape route defaulting to bank escape");
                    }
                }
            case BANK:
                bankAndEquip();
                return;
            case EAT:
                if (config.food() == ThievingFood.ANCIENT_BREW) {
                    drinkAncientBrew();
                    Rs2Inventory.dropAll(true, "vial");
                    sleepUntil(() -> Rs2Player.hasPrayerPoints(), 800);
                } else {
                    final double hp = Rs2Player.getHealthPercentage();
                    Rs2Player.useFood();
                    Rs2Inventory.dropAll(true, "jug");
                    sleepUntil(() -> Rs2Player.getHealthPercentage() > hp, 800);
                }
                return;
            case DROP:
                dropAllExceptImportant();
                if (Rs2Inventory.isFull()) Rs2Player.eatAt(99);
                return;
            case HOP:
                if (shouldHop()) {
                    hopWorld();
                } else {
                    log.debug("Skipping world hop (Wealthy Citizen)");
                }
                return;
            case COIN_POUCHES:
                repeatedAction(() -> Rs2Inventory.interact("coin pouch", "Open-all"), () -> !Rs2Inventory.hasItem("coin pouch"), 3);
                return;
            case WALK_TO_START:
                myLoc = Rs2Player.getWorldLocation();
                if (myLoc == null) {
                    log.warn("Player Location is null");
                    return;
                }
                if (myLoc.distanceTo(startingLocation) <= 5) {
                    if (thievingNpc != null) {
                        walkTo("Walk to npc ", thievingNpc.getWorldLocation(), 1);
                    } else {
                        thievingNpc = getThievingNpcCache();
                        return;
                    }
                } else {
                    walkTo("Walk to start", startingLocation, 3);
                }
                Rs2Player.waitForWalking();
                return;
            case SHADOW_VEIL:
                castShadowVeil();
                return;
            case CLOSE_DOOR:
                if (isNpcNull(thievingNpc)) return;
                final String name = getNpcName(thievingNpc);
                final WorldPoint[] house = ThievingData.getVyreHouse(name);
                final WorldPoint myLoc2 = Rs2Player.getWorldLocation();
                if (isPointInPolygon(house, myLoc2)) {
                    log.debug("Closing door {} in {} house", toString(myLoc2), name);
                    if (closeNearbyDoor(DOOR_CHECK_RADIUS)) DOOR_TIMER.unset();
                } else if (isPointInPolygon(house, thievingNpc.getWorldLocation())) {
                    walkTo("Walk to npc", thievingNpc.getWorldLocation(), 1);
                } else {
                    log.warn("This door close state should never happen");
                    if (shouldHop()) {
                        hopWorld();
                    } else {
                        log.debug("Skipping world hop (Wealthy Citizen) during close-door fallback");
                    }
                }
                return;
            case PICKPOCKET:
                if (Rs2Inventory.hasItem(ThievingData.ROGUE_SET.toArray(String[]::new)) && !isWearing(ThievingData.ROGUE_SET)) {
                    // only equip if we are safely in the house w/ the npc
                    myLoc = Rs2Player.getWorldLocation();
                    final Rs2NpcModel npc = thievingNpc;
                    if (npc != null && npc.getWorldLocation() != null && myLoc != null) {
                        if (config.THIEVING_NPC() != ThievingNpc.VYRES ||
                                (new Rs2WorldPoint(myLoc)).distanceToPath(npc.getWorldLocation()) < Integer.MAX_VALUE) {
                            if (equip(ThievingData.ROGUE_SET)) {
                                log.debug("Equipped rogue set");
                            }
                        } else {
                            log.debug("Cannot reach {} @ {}", getNpcName(thievingNpc), thievingNpc.getWorldLocation());
                        }
                        DOOR_TIMER.set();
                        return;
                    }
                }

                if (!Rs2Equipment.isWearing("dodgy necklace") && Rs2Inventory.hasItem("dodgy necklace")) {
                    log.debug("Equipping dodgy necklace");
                    Rs2Inventory.wield("dodgy necklace");
                    sleepUntilWithInterrupt(() -> Rs2Equipment.isWearing("dodgy necklace"), 1_800);
                    return;
                }

                final ThievingNpcStrategy strategy = getActiveStrategy();
                if (strategy != null && strategy.handlePickpocket(this)) {
                    return;
                }

                // limit is so breaks etc. don't cause a high last action time
                long timeSince = Math.min(System.currentTimeMillis()-lastAction, 1_000);
                if (timeSince < 250) {
                    sleep((int) (250-timeSince) + 50, (int) (250-timeSince) + 250);
                    timeSince = 350;
                }
                double rand = Math.random();
                if ((timeSince / 500_000d) > rand) sleep(5_000, 10_000); // around every 500s
                if ((timeSince / 30_000d) > rand) sleep(300, 700); // around every 30s

                var highlighted = net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs();
                if (highlighted.isEmpty()) {
                    if (isNpcNull(thievingNpc)) return;
                    Rs2Npc.pickpocket(thievingNpc.getNpc());
                } else {
                    Rs2Npc.pickpocket(highlighted);
                }
                lastAction = System.currentTimeMillis();
                return;
            default:
                // idk
                break;
        }
    }

    public boolean run() {
        Microbot.isCantReachTargetDetectionEnabled = true;
        lastAction = System.currentTimeMillis();
        nextShadowVeil = System.currentTimeMillis()+60_000;
        underAttack = false;
        startupGraceUntil = System.currentTimeMillis() + 4_000;
        startTime = Instant.now();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                loop();
            } catch (SelfInterruptException ex) {
                log.debug("Self Interrupt: {}", ex.getMessage());
                thievingNpc = null;
            } catch (Exception ex) {
                log.error("Error in main loop", ex);
                thievingNpc = null;
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasReqs() {
        if (System.currentTimeMillis() < startupGraceUntil) return true;
        boolean hasReqs = true;
        if (config.useFood()) {
            if (config.food() == ThievingFood.ANCIENT_BREW) {
                if (!hasAncientBrew()) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.REDEMPTION, false);
                    log.debug("Missing ancient brew");
                    hasReqs = false;
                }
            } else {
                if (Rs2Inventory.getInventoryFood().isEmpty()) {
                    log.debug("Missing food");
                    hasReqs = false;
                }
            }
        }

        if (config.dodgyNecklaceAmount() > 0 && !Rs2Inventory.hasItem("Dodgy necklace")) {
            log.debug("Missing dodgy necklaces");
            hasReqs = false;
        }

        if (config.shadowVeil()) {
            if (Rs2Inventory.itemQuantity("Cosmic rune") < 5) {
                log.debug("Missing cosmic runes");
                hasReqs = false;
            }
            boolean hasRunes = Rs2Equipment.isWearing("Lava battlestaff") || Rs2Inventory.hasItem("Earth rune", "Fire rune");
            if (!hasRunes) {
                log.debug("Missing lava battle staff or earth & fire runes");
                hasReqs = false;
            }
        }
        return hasReqs;
    }

    protected static boolean isPointInPolygon(WorldPoint[] polygon, WorldPoint point) {
        if (polygon == null || point == null) return false;
        int n = polygon.length;
        if (n < 3) return false;

        int plane = polygon[0].getPlane();
        if (point.getPlane() != plane) return false;

        int px = point.getX();
        int py = point.getY();
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = polygon[i].getX(), yi = polygon[i].getY();
            int xj = polygon[j].getX(), yj = polygon[j].getY();

            // we check if the point is on the border
            int dx = xj - xi;
            int dy = yj - yi;
            int dxp = px - xi;
            int dyp = py - yi;

            int cross = dx * dyp - dy * dxp;
            if (cross == 0 && // the coords area collinear
                    Math.min(xi, xj) <= px && px <= Math.max(xi, xj) &&
                    Math.min(yi, yj) <= py && py <= Math.max(yi, yj)) {
                return true; // so it is on an edge of the polygon
            }

            // apply the ray-casting algorithm
            boolean intersect = ((yi > py) != (yj > py)) &&
                    (px < (double)(xj - xi) * (py - yi) / (double)(yj - yi) + xi);
            if (intersect) inside = !inside;
        }

        return inside;
    }

    private boolean shouldCastShadowVeil() {
        if (!config.shadowVeil()) return false;
        final long current = System.currentTimeMillis();
        if (current <= forceShadowVeilActive) return false;
        return current > nextShadowVeil || !Rs2Magic.isShadowVeilActive();
    }

    private void castShadowVeil() {
        if (!shouldCastShadowVeil()) return;
        if (!Rs2Magic.canCast(MagicAction.SHADOW_VEIL)) {
            log.error("Cannot cast shadow veil");
            return;
        }
        if (!Rs2Magic.cast(MagicAction.SHADOW_VEIL)) {
            log.error("Failed to cast shadow veil");
            return;
        }
        if (!sleepUntilWithInterrupt(() -> forceShadowVeilActive > System.currentTimeMillis() || Rs2Magic.isShadowVeilActive(), 2_400)) {
            log.error("Failed to await shadow veil active");
            return;
        }
        nextShadowVeil = System.currentTimeMillis()+60_000;
    }

    private int maxCoinPouches() {
        if (config.coinPouchThreshold() >= 0) return config.coinPouchThreshold();
        return plugin.getMaxCoinPouch();
    }

    private boolean shouldOpenCoinPouches() {
        int threshold = Math.max(1, Math.min(plugin.getMaxCoinPouch(), maxCoinPouches() + (int)(Math.random() * (-7))));
        return Rs2Inventory.hasItemAmount("coin pouch", threshold, true);
    }

    private boolean isNpcNull(Rs2NpcModel npc) {
        if (npc == null) return true;
        final String name = getNpcName(npc);
        if (name == null) return true;
        if (name.isBlank() || name.equalsIgnoreCase("null")) return true;
        if (npc.getId() == -1) return true;
        final WorldPoint worldPoint = npc.getWorldLocation();
        if (worldPoint == null) return true;
        final WorldPoint myLoc = Rs2Player.getWorldLocation();
        if (myLoc == null || myLoc.distanceTo(worldPoint) >= 20) return true;
        return npc.getLocalLocation() == null;
    }

    private String toString(WorldPoint point) {
        if (point == null) return "(-1,-1,-1)";
        return "(" + point.getX() + "," + point.getY() + "," + point.getPlane() + ")";
    }

    private List<Rs2TileObjectModel> getDoors(WorldPoint wp, int radius) {
        if (wp == null) return Collections.emptyList();
        final Rs2WorldPoint rs2Wp = new Rs2WorldPoint(wp);
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getRs2TileObjectCache().query()
                .within(wp, radius)
                .where(o -> {
                    var comp = o.getObjectComposition();
                    if (comp == null || !Arrays.asList(comp.getActions()).contains("Close")) return false;
                    return rs2Wp.distanceToPath(o.getWorldLocation()) < Integer.MAX_VALUE;
                })
                .toList()
        ).orElse(Collections.emptyList());
    };

    private boolean closeNearbyDoor(int radius) {
        List<Rs2TileObjectModel> doors;
        int doorCount = 0;
        while (!(doors = getDoors(Rs2Player.getWorldLocation(), radius)).isEmpty()) {
            if (doorCount >= 3) {
                log.error("Closing third door maybe we should hop?");
                return false;
            }
            final WorldPoint myLoc = Rs2Player.getWorldLocation();
            if (myLoc == null) return false;
            final Rs2TileObjectModel door = doors.stream()
                    .min(Comparator.comparingInt(d -> d.getWorldLocation().distanceTo(myLoc)))
                    .orElseThrow();

            final WorldPoint doorWp = door.getWorldLocation();
            if (!door.click("Close")) return false;
            if (door.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) > 1) {
                if (!sleepUntilWithInterrupt(() -> Rs2Player.isMoving() || Rs2Player.isStunned(), 1_200)) return false;
                if (Rs2Player.isStunned()) return false;
                sleepUntilWithInterrupt(() -> !Rs2Player.isMoving(), 5_000);
            }
            if (!sleepUntilWithInterrupt(() -> getDoors(doorWp, 1).isEmpty() || Rs2Player.isStunned(), 1_200)) {
                log.warn("Failed to wait closing door @ {}", toString(doorWp));
                return false;
            }
            if (Rs2Player.isStunned()) return false;
            log.debug("Closed door @ {}", toString(doorWp));
            doorCount++;
        }
        return true;
    }

    private boolean repeatedAction(Runnable action, BooleanSupplier awaitedCondition, BooleanSupplier interruptCondition, int maxTries) {
        for (int i = 0; i < maxTries; i++) {
            if (awaitedCondition.getAsBoolean()) return true;
            action.run();
            sleepUntilWithInterrupt(awaitedCondition, interruptCondition, 2_000);
        }
        return false;
    }

    private boolean repeatedAction(Runnable action, BooleanSupplier check, int maxTries) {
        return repeatedAction(action, check, () -> !shouldRun(), maxTries);
    }

    private boolean equip(String item, boolean shouldLog) {
        if (Rs2Equipment.isWearing(item)) return true;

        final boolean success;
        if (Rs2Inventory.contains(item)) {
            if (config.THIEVING_NPC() == ThievingNpc.VYRES && Rs2Bank.isOpen() && isWearing(ThievingData.VYRE_SET)) return true;
            success = repeatedAction(() -> Rs2Inventory.wear(item), () -> Rs2Equipment.isWearing(item), 3);
            if (shouldLog && !success) log.error("Failed to equip {}", item);
        } else if (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(item)) {
            success = repeatedAction(() -> Rs2Bank.withdrawItem(item), () -> Rs2Inventory.contains(item), 3);
            if (shouldLog && !success) log.error("Could not withdraw item to equip {}", item);
            else return equip(item);
        } else {
            success = false;
            if (shouldLog) log.error("Could not find item to equip {}", item);
        }
        return success;
    }

    private boolean equip(String item) {
        return equip(item, true);
    }

    private boolean isWearing(Set<String> set) {
        for (String item : set) {
            if (!Rs2Equipment.isWearing(item)) return false;
        }
        return true;
    }

    private boolean equip(Set<String> set, boolean shouldLog) {
        boolean success = repeatedAction(
            () -> {
                for (String item : set) {
                    if (!equip(item, shouldLog)) return;
                }
            },
            () -> isWearing(set),
            3
        );
        return success;
    }

    private boolean equip(Set<String> set) {
        return equip(set, true);
    }

    private Set<String> getExclusions() {
        final Set<String> exclusions = new HashSet<>(ThievingData.ROGUE_SET);
        if (config.THIEVING_NPC() == ThievingNpc.VYRES) exclusions.addAll(ThievingData.VYRE_SET);
        exclusions.add("coin pouch");
        if (config.shadowVeil()) {
            exclusions.add("cosmic rune");
            if (!Rs2Equipment.isWearing("lava battlestaff")) {
                exclusions.add("earth rune");
                exclusions.add("fire rune");
            }
        }
        if (config.dodgyNecklaceAmount() > 0) exclusions.add("dodgy necklace");
        if (config.useFood()) {
            if (config.food() == ThievingFood.ANCIENT_BREW) {
                exclusions.addAll(ThievingData.ANCIENT_BREW_DOSES);
            } else {
                exclusions.add(config.food().getName());
            }
        }
        return exclusions;
    }

    private boolean getInventoryAmount(String name, int amount, boolean exact) {
        return repeatedAction(
                () -> {
                    final int deficit = amount-Rs2Inventory.itemQuantity(name, exact);
                    if (deficit == 0) return;
                    if (deficit > 0) {
                        if (Rs2Bank.hasBankItem(name, deficit, exact)) Rs2Bank.withdrawX(name, deficit, exact);
                    }
                    else Rs2Bank.depositX(name, Math.abs(deficit));
                },
                () -> Rs2Inventory.itemQuantity(name, exact) == amount,
                () -> {
                    if (!Rs2Bank.isOpen()) {
                        log.warn("Bank is closed while attempting to withdraw");
                        return true;
                    }
                    return !shouldRun();
                },
                3
        );
    }

    private void showMessage(String message) {
        log.warn(message);
        Microbot.showMessage(message);
    }

    private void bankAndEquip() {
        if (!Rs2Bank.isOpen()) {
            BankLocation bank;
            if (config.THIEVING_NPC() == ThievingNpc.VYRES && ThievingData.OUTSIDE_HALLOWED_BANK.distanceTo(Rs2Player.getWorldLocation()) < 20) {
                log.debug("Near Hallowed");
                bank = BankLocation.HALLOWED_SEPULCHRE;
                // it's not needed for banking, but if we have it in inv we should equip it
                equip(ThievingData.VYRE_SET, false);
            } else {
                log.debug("Not Near Hallowed");
                bank = Rs2Bank.getNearestBank();
                if (bank == BankLocation.DARKMEYER) {
                    if (!equip(ThievingData.VYRE_SET)) {
                        log.error("Did not equip vyre set cannot go to darkmeyer bank");
                        return;
                    }
                }
            }

            if (!isRunning()) return;
            boolean opened = Rs2Bank.isNearBank(bank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(bank);
            if (!opened || !Rs2Bank.isOpen()) return;
        }

        if (!Rs2Bank.isOpen() || !isRunning()) return;
        Rs2Bank.depositAllExcept(getExclusions());

        if (config.useFood()) {
            String itemName = config.food().getName();
            // Para Ancient Brew, siempre retiramos la dosis (4)
            if (config.food() == ThievingFood.ANCIENT_BREW) {
                itemName = "Ancient brew(4)";
            }
            
            if (!getInventoryAmount(itemName, config.foodAmount(), true)) {
                if (!Rs2Bank.isOpen()) return;
                showMessage("No " + config.food().getName() + " found in bank.");
                shutdown();
                return;
            }
        }

        if (config.useFood()) {
            if (config.eatFullHpBank() && config.food() != ThievingFood.ANCIENT_BREW) {
                boolean ateFood = false;
                while (!Rs2Player.isFullHealth() && Rs2Player.useFood()) {
                    Rs2Player.waitForAnimation();
                    ateFood = true;
                }

                if (ateFood) {
                    bankAndEquip();
                    return;
                }
            }
        }

        if (!getInventoryAmount("Dodgy necklace", config.dodgyNecklaceAmount(), true)) {
            if (!Rs2Bank.isOpen()) return;
            showMessage("No Dodgy necklace found in bank.");
            shutdown();
            return;
        }

        if (config.shadowVeil()) {
            if (!Rs2Equipment.isWearing("Lava battlestaff")) {
                if (!Rs2Inventory.contains("Lava battlestaff") &&
                        !(Rs2Inventory.contains("Earth rune") && Rs2Inventory.contains("Fire rune"))) {
                    if (Rs2Bank.hasItem("Lava battlestaff")) {
                        Rs2Bank.withdrawItem("Lava battlestaff");
                        Rs2Inventory.waitForInventoryChanges(1_500);
                    } else if (Rs2Bank.hasItem("Earth rune") && Rs2Bank.hasItem("Fire rune")) {
                        Rs2Bank.withdrawAll(true, "Fire rune", true);
                        Rs2Inventory.waitForInventoryChanges(1_500);
                        Rs2Bank.withdrawAll(true, "Earth rune", true);
                        Rs2Inventory.waitForInventoryChanges(1_500);
                    } else {
                        if (!shouldRun() || !Rs2Bank.isOpen()) return;
                        showMessage("No Lava battlestaff and runes (Earth, Fire) found in bank.");
                        shutdown();
                        return;
                    }
                }
                if (Rs2Inventory.contains("Lava battlestaff")) {
                    Rs2Inventory.wear("Lava battlestaff");
                    Rs2Inventory.waitForInventoryChanges(1_500);
                }
            }

            Rs2Bank.withdrawAll(true, "Cosmic rune", true);
            Rs2Inventory.waitForInventoryChanges(1_500);
            if (!Rs2Inventory.hasItem("Cosmic rune")) {
                if (!shouldRun() || !Rs2Bank.isOpen()) return;
                showMessage("No Cosmic runes found.");
                shutdown();
                return;
            }
        }

        equip(ThievingData.ROGUE_SET);
        Rs2Bank.closeBank();

        if (underAttack) {
            underAttack = false;
            if (shouldHop()) {
                hopWorld();
            } else {
                log.debug("Skipping world hop (Wealthy Citizen)");
            }
        }

        if (walkTo("Return to npc", startingLocation, 3)) {
            sleepUntilWithInterrupt(() -> !Rs2Player.isMoving(), 1_200);
        }
        DOOR_TIMER.set();
    }

    private void dropAllExceptImportant() {
        final Set<String> keep = getExclusions();
        if (config.DoNotDropItemList() != null && !config.DoNotDropItemList().isEmpty())
            keep.addAll(
                    Arrays.stream(config.DoNotDropItemList().split(","))
                            .map(String::strip)
                            .map(String::toLowerCase)
                            .collect(Collectors.toSet())
            );
        Collections.addAll(keep, "coins", "book of the dead");
        if (config.THIEVING_NPC() == ThievingNpc.VYRES) Collections.addAll(keep,"drakan's medallion", "blood shard");
        Rs2Inventory.dropAllExcept(config.keepItemsAboveValue(), keep.toArray(String[]::new));
    }


    private void hopWorld() {
        final long now = System.currentTimeMillis();
        if (now - lastHopAt < 15000) {
            log.debug("Skipping hop; last hop was {}ms ago", now - lastHopAt);
            return;
        }
        lastHopAt = now;
        DOOR_TIMER.unset();
        final int maxAttempts = 5;
        log.debug("Hopping world, please wait");

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            final int currentWorld = Microbot.getClient().getWorld();
            Microbot.hopToWorld(Login.getRandomWorld(true, null));

            BooleanSupplier attackedInterrupt = () -> {
                final Rs2NpcModel attacking = getAttackingNpc();
                if (attacking == null) return false;
                final WorldPoint me = Rs2Player.getWorldLocation();
                final WorldPoint npc = attacking.getWorldLocation();
                if (me != null && npc != null && me.distanceTo(npc) <= 2) {
                    log.warn("Getting attacked while hopping");
                    return true;
                }
                return false;
            };
            
            sleepUntilWithInterrupt(() -> Microbot.getClient().getGameState() == GameState.HOPPING, attackedInterrupt, 15_000);
            sleepUntilWithInterrupt(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, attackedInterrupt, 15_000);
            boolean changed = sleepUntilWithInterrupt(() -> Microbot.getClient().getWorld() != currentWorld, attackedInterrupt, 15_000);

            if (changed) {
                log.debug("Successful hop world");
                thievingNpc = null; // force fresh lookup after hop
                return;
            }
            sleep(250, 350);
            log.warn("Hop attempt {}/{} failed, retrying...", attempt + 1, maxAttempts);
        }
        log.error("Failed to hop world");
    }

    private void drinkAncientBrew() {
        for (String dose : ThievingData.ANCIENT_BREW_DOSES) {
            if (Rs2Inventory.contains(dose) && Rs2Inventory.interact(dose, "Drink")) {
                log.debug("Drinking: {}", dose);
                sleepUntil(() -> Rs2Player.hasPrayerPoints(), 800);
                break;
            }
        }
    }

    private boolean hasAncientBrew() {
        for (String dose : ThievingData.ANCIENT_BREW_DOSES) {
            if (Rs2Inventory.contains(dose)) return true;
        }
        return false;
    }

    public void onChatMessage(ChatMessage event) {
        final ThievingNpcStrategy strategy = getActiveStrategy();
        if (strategy != null) {
            strategy.onChatMessage(event);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Walker.setTarget(null);
        Microbot.isCantReachTargetDetectionEnabled = false;
        startingLocation = null;
        startingNpc = null;
    }
}
