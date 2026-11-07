package net.runelite.client.plugins.microbot.scurrius;

import com.google.inject.Inject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.scurrius.enums.State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ScurriusScript extends Script {

    @Inject
    private ScurriusConfig config;

    private long lastEatTime = -1;
    private long lastPrayerTime = -1;
    private static final int EAT_COOLDOWN_MS = 2000;
    private static final int PRAYER_COOLDOWN_MS = 2000;

    final WorldPoint bossLocation = new WorldPoint(3279, 9869, 0);
    static final WorldArea FIGHT_ROOM = new WorldArea(new WorldPoint(3290, 9860, 0), 17, 17);
    final WorldArea fightRoom = FIGHT_ROOM;
    public static State state = State.BANKING;
    Rs2NpcModel scurrius = null;
    private State previousState = null;
    private boolean hasLoggedRespawnWait = false;
    private Boolean previousInFightRoom = null;
    private static final int TELEPORT_TAB_ID = ItemID.POH_TABLET_VARROCKTELEPORT;
    private static final long TELEPORT_RETRY_COOLDOWN_MS = 5000;
    /** Set when the script decided to teleport out; stops the fight rules pulling the state back until the room is left. */
    private boolean emergencyActive = false;
    private long emergencyRetryAt = 0;
    private boolean loggedNoTeleport = false;
    /** Percent thresholds for the next eat and the next prayer potion; -1 until rolled. */
    /** Kills since the last bank visit and the kill count that starts the next restock trip (0 when trips are off); shown in the overlay. */
    public static volatile int killsSinceBank = 0;
    public static volatile int nextTripAt = 0;
    /** Kills this session and the experience tracker, both shown in the overlay. */
    public static volatile int totalKills = 0;
    public static volatile int specsUsed = 0;
    public static final XpTracker XP = new XpTracker();
    private int restockTarget = 0;
    private boolean scurriusWasPresent = false;
    private boolean fightedThisKill = false;
    /** Set when we fought since the last bank visit; a bank visit without it is not a real restock trip. */
    private boolean fightedSinceBank = false;
    private boolean lootPending = false;
    private int emptyLootLoops = 0;
    /** Breaks: bank visits since the last break, the visit count that starts the next one, and the running break. */
    public static volatile int breakTripsDone = 0;
    public static volatile int breakTripsTarget = 0;
    /** When the running break ends (epoch ms), 0 when not on a break; shown in the overlay. */
    public static volatile long breakEndsAtMs = 0;
    private int tripsSinceBreak = 0;
    private int breakTripTarget = 0;
    private boolean onBreak = false;
    private long breakEndAt = 0;
    private long lastLoginAttempt = 0;
    private int loginAttempts = 0;
    private static final long LOGIN_RETRY_MS = 15_000;
    private boolean restockTrip = false;
    private long restockStartedAt = 0;
    private static final int LOOT_DONE_AFTER_EMPTY_LOOPS = 5;
    private static final long RESTOCK_WALK_TIMEOUT_MS = 120_000;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ScurriusScript.class);
    private final Random habitRandom = new Random();
    private boolean habitsDoneThisKill = false;
    private boolean pausedBeforeEntry = false;
    private long lateReclickAt = 0;
    /** Special attack: when the energy became available (0 = not), its random delay, the attempt being verified, and a weapon swap. */
    private long specAvailableSince = 0;
    private int specDelayMs = 0;
    private long specPendingSince = 0;
    private int specEnergyBefore = 0;
    private long specRetryAt = 0;
    private boolean swappedForSpec = false;
    private int mainWeaponId = -1;
    private int mainShieldId = -1;
    private int eatThreshold = -1;
    private int prayerThreshold = -1;
    /** Prayer flicking and rock dodging run on game ticks in this driver, owned by the plugin. */
    private volatile ScurriusTickDriver tickDriver;

    public void setTickDriver(ScurriusTickDriver tickDriver) {
        this.tickDriver = tickDriver;
    }

    private boolean isAttackHeld() {
        ScurriusTickDriver driver = tickDriver;
        return driver != null && driver.isAttackHeld();
    }

    public boolean run(ScurriusConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = true;
        setUpAntiban();
        habitsDoneThisKill = false;
        pausedBeforeEntry = false;
        lateReclickAt = 0;
        killsSinceBank = 0;
        totalKills = 0;
        specsUsed = 0;
        specPendingSince = 0;
        specAvailableSince = 0;
        specRetryAt = 0;
        swappedForSpec = false;
        restockTarget = rollRestockTarget();
        restockTrip = false;
        lootPending = false;
        scurriusWasPresent = false;
        fightedThisKill = false;
        fightedSinceBank = false;
        onBreak = false;
        breakEndsAtMs = 0;
        tripsSinceBreak = 0;
        breakTripTarget = rollBreakTarget();
        List<Integer> importantItems = List.of(config.foodSelection().getId(), config.potionSelection().getItemId(), ItemID.POH_TABLET_VARROCKTELEPORT);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (onBreak) {
                    handleBreak();
                    return; // on a break the script does nothing else: no clicks
                }
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2Player.getWorldLocation() == null) return;
                long startTime = System.currentTimeMillis();
                long currentTime = System.currentTimeMillis();

                if (state != previousState) {
                    Microbot.log("State changed to: " + getStateDescription(state));
                    previousState = state;
                }
                if (config.antibanFramework()) {
                    // The antiban framework must never pause, cool down or break inside the fight.
                    safetyHold(state == State.FIGHTING || state == State.TELEPORT_AWAY);
                }

                scurrius = Microbot.getRs2NpcCache().query().withName("Scurrius").nearestOnClientThread();

                boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
                boolean hasPrayerPotions = Rs2Inventory.hasItem("prayer potion") || Rs2Inventory.hasItem("super restore");
                boolean isScurriusPresent = scurrius != null;
                boolean isInFightRoom = isInFightRoom();
                boolean hasLineOfSightWithScurrius = scurrius != null && scurrius.hasLineOfSight();

                if (previousInFightRoom == null || isInFightRoom != previousInFightRoom) {
                    Microbot.log(isInFightRoom ? "Player has entered the boss room." : "Player has exited the boss room.");
                    previousInFightRoom = isInFightRoom;
                }

                // A kill: Scurrius is gone while we are still in the room after fighting him (a teleport-out or our own
                // death leaves the room, or has emergencyActive set, and does not count).
                if (isScurriusPresent && state == State.FIGHTING) {
                    fightedThisKill = true;
                    fightedSinceBank = true;
                }
                if (scurriusWasPresent && !isScurriusPresent && isInFightRoom && fightedThisKill && !emergencyActive
                        && state != State.TELEPORT_AWAY) {
                    killsSinceBank++;
                    totalKills++;
                    habitsDoneThisKill = false;
                    ScurriusTickDriver killDriver = tickDriver;
                    if (killDriver != null) killDriver.rollKillMode();
                    fightedThisKill = false;
                    lootPending = true;
                    emptyLootLoops = 0;
                    Microbot.log("Kills: " + killsSinceBank + (config.restockEnabled() ? " / next trip at " + restockTarget : ""));
                }
                scurriusWasPresent = isScurriusPresent;
                if (!isInFightRoom) {
                    fightedThisKill = false;
                } else {
                    pausedBeforeEntry = false;
                }
                nextTripAt = config.restockEnabled() ? restockTarget : 0;
                breakTripsDone = tripsSinceBreak;
                breakTripsTarget = config.breakEnabled() ? breakTripTarget : 0;

                if (emergencyActive && !isInFightRoom) {
                    emergencyActive = false; // the teleport worked: the normal rules apply again (bank, walk back)
                }

                // The one table for what to do with the supplies and the boss: leave when out of food or potions, top up
                // outside the room, walk in with full supplies. A decided teleport or walk-out (emergencyActive) is not undone.
                if (!emergencyActive && state != State.TELEPORT_AWAY) {
                    int foodLeft = Rs2Inventory.getInventoryFood().size();
                    boolean hasTab = Rs2Inventory.hasItem(TELEPORT_TAB_ID);
                    State next = SupplyRules.nextState(isInFightRoom, isScurriusPresent, hasLineOfSightWithScurrius, lootPending,
                            foodLeft, hasPrayerPotions, hasTab, Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER),
                            Microbot.getClient().getRealSkillLevel(Skill.PRAYER), hasRequiredSupplies());
                    boolean leaving = isInFightRoom && (next == State.TELEPORT_AWAY || next == State.BANKING);
                    if (leaving && currentTime < emergencyRetryAt) {
                        next = state; // a teleport just failed: wait out the cooldown instead of retrying every loop
                    } else if (leaving) {
                        emergencyActive = true; // keeps the fight rules from pulling the state back
                        if (!hasTab && !loggedNoTeleport) {
                            Microbot.log("Out of supplies in the boss room and no teleport tab: walking out to the bank.");
                            loggedNoTeleport = true;
                        }
                        if (state != next) {
                            Microbot.log("Leaving to restock (food " + foodLeft + ", prayer potions " + (hasPrayerPotions ? "yes" : "no") + ").");
                        }
                    }
                    if (next == State.WAITING_FOR_BOSS && state != State.WAITING_FOR_BOSS) {
                        hasLoggedRespawnWait = false;
                    }
                    if (next == State.FIGHTING && state == State.WAITING_FOR_BOSS) {
                        next = state; // the WAITING case switches to FIGHTING itself, after its looting step
                    }
                    state = next;
                }

                switch (state) {
                    case BANKING:
                        if (restockTrip && isInFightRoom && System.currentTimeMillis() - restockStartedAt > RESTOCK_WALK_TIMEOUT_MS) {
                            Microbot.log("Restock trip: could not leave the boss room, skipping this trip.");
                            skipRestockTrip();
                            state = State.WAITING_FOR_BOSS;
                            break;
                        }
                        boolean isCloseToBank = Rs2Bank.walkToBank();
                        if (isCloseToBank) {
                            Rs2Bank.openBank();
                        }
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.depositAllExcept(importantItems.toArray(new Integer[0]));

                            int requiredFoodAmount = config.foodAmount();
                            int requiredPotionAmount = config.prayerPotionAmount();
                            int requiredTeleports = config.teleportAmount();

                            // Each item is topped up to the full amount before the bank is closed: withdrawDeficit returns
                            // before the inventory has updated, so one call (and the next item's) can leave it short.
                            if (!topUp(config.foodSelection().getId(), requiredFoodAmount)) {
                                Microbot.showMessage("Missing Food in Bank");
                                shutdown();
                                return;
                            }
                            if (!topUp(config.potionSelection().getItemId(), requiredPotionAmount)) {
                                Microbot.showMessage("Missing Potion in Bank");
                                shutdown();
                                return;
                            }
                            if (!topUp(ItemID.POH_TABLET_VARROCKTELEPORT, requiredTeleports)) {
                                Microbot.showMessage("Missing Teleports in Bank");
                                shutdown();
                                return;
                            }

                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                            onBankVisitDone();
                        }
                        break;

                    case FIGHTING:
                        if (tryEmergencyTeleport(currentTime)) break;
                        // Eating and drinking are not gated on animating: with a blowpipe the player animates all the time.
                        // The threshold is a percent of the real level, rolled again after each eat or drink.
                        if (currentTime - lastEatTime > EAT_COOLDOWN_MS) {
                            if (eatThreshold < 0) {
                                eatThreshold = HumanHabits.eatThreshold(randomThreshold(config.minEatPercent(), config.maxEatPercent()),
                                        habitRandom, config.habitEatEarly());
                            }
                            int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
                            int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);

                            if (shouldEat(hp, maxHp, eatThreshold) && Rs2Player.useFood()) {
                                Microbot.log("Eating at " + hp + "/" + maxHp + " hp (" + (hp * 100 / maxHp) + "%), threshold "
                                        + eatThreshold + "%.");
                                lastEatTime = currentTime;
                                returnToPrayerTab();
                                eatThreshold = -1;
                            }
                        }

                        if (currentTime - lastPrayerTime > PRAYER_COOLDOWN_MS) {
                            if (prayerThreshold < 0) prayerThreshold = randomThreshold(config.minPrayerPercent(), config.maxPrayerPercent());
                            int prayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
                            int maxPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);

                            // drinkPrayerPotionAt takes prayer points and drinks when the level is not above them
                            if (shouldEat(prayer, maxPrayer, prayerThreshold) && Rs2Player.drinkPrayerPotionAt(prayer)) {
                                Microbot.log("Drinking a prayer potion at " + prayer + "/" + maxPrayer + " prayer ("
                                        + (prayer * 100 / maxPrayer) + "%), threshold " + prayerThreshold + "%.");
                                lastPrayerTime = currentTime;
                                returnToPrayerTab();
                                prayerThreshold = -1;
                            }
                        }

                        // Rocks falling on the player: the driver walks off them; no Attack click until clear.
                        if (isAttackHeld()) break;

                        handleSpecialAttack(currentTime);

                        Rs2NpcModel giantRatModel = Microbot.getRs2NpcCache().query()
                                .where(ScurriusScript::isLiveGiantRat)
                                .nearest();
                        if (giantRatModel != null) {
                            boolean didWeAttackAGiantRat = scurrius != null && config.prioritizeRats() && giantRatModel.click("Attack");
                            if (didWeAttackAGiantRat) return;
                        }

                        if (scurrius != null && !Microbot.getClient().getLocalPlayer().isInteracting()) {
                            // Now and then a little late (under a tick), without blocking the loop: eating etc. go on meanwhile.
                            if (lateReclickAt == 0) {
                                int delay = HumanHabits.reclickDelayMs(habitRandom, config.habitLateReclick());
                                lateReclickAt = delay == 0 ? currentTime : currentTime + delay;
                            }
                            if (currentTime >= lateReclickAt) {
                                scurrius.click("Attack");
                                lateReclickAt = 0;
                            }
                        } else {
                            lateReclickAt = 0;
                        }
                        break;

                    case TELEPORT_AWAY:
                        int tabsBeforeTeleport = Rs2Inventory.count(TELEPORT_TAB_ID);
                        if (tabsBeforeTeleport == 0) {
                            Microbot.log("No Varrock teleport tab to break, staying in the fight.");
                            emergencyActive = false;
                            restockTrip = false;
                            state = isInFightRoom ? State.FIGHTING : State.BANKING;
                            break;
                        }
                        Rs2Inventory.interact("Varrock teleport", "break");
                        boolean leftFightRoom = sleepUntil(() -> !isInFightRoom(), 10_000);
                        if (!leftFightRoom) {
                            Microbot.log("Teleport did not take us out of the boss room (tabs " + tabsBeforeTeleport
                                    + " -> " + Rs2Inventory.count(TELEPORT_TAB_ID) + "), back to fighting.");
                            emergencyActive = false;
                            emergencyRetryAt = System.currentTimeMillis() + TELEPORT_RETRY_COOLDOWN_MS;
                            if (restockTrip) {
                                skipRestockTrip(); // a restock trip is optional: do not retry it in a loop
                                state = State.WAITING_FOR_BOSS;
                                break;
                            }
                            state = State.FIGHTING;
                            break;
                        }
                        sleep(600 * 2);
                        disableAllPrayers();
                        state = State.BANKING;
                        break;

                    case WALK_TO_BOSS:
                        if (!hasRequiredSupplies()) {
                            Microbot.log("Missing supplies, restarting pathfinding and returning to Bank.");
                            Rs2Walker.setTarget(null);
                            state = State.BANKING;
                            return;
                        }

                        Rs2Walker.walkTo(bossLocation);
                        if (!pausedBeforeEntry && Rs2Player.getWorldLocation().distanceTo(bossLocation) < 12) {
                            pausedBeforeEntry = true;
                            humanPause("before entering the room");
                        }
                        String interactionType = config.bossRoomEntryType().getInteractionText();
                        Microbot.getRs2TileObjectCache().query().withId(ObjectID.RAT_BOSS_ENTRANCE).interact(interactionType);
                        sleepUntil(this::isInFightRoom);
                        break;

                    case WAITING_FOR_BOSS:
                        if (attemptLooting(config)) {
                            lootPending = true;
                            emptyLootLoops = 0;
                        } else if (lootPending && ++emptyLootLoops >= LOOT_DONE_AFTER_EMPTY_LOOPS) {
                            lootPending = false;
                        }
                        if (!hasLoggedRespawnWait) {
                            Microbot.log("Waiting for Scurris to respawn...");
                            hasLoggedRespawnWait = true;
                            disableAllPrayers();
                        }
                        if (isScurriusPresent) {
                            state = State.FIGHTING;
                            Microbot.log("Scurris has respawned, switching to FIGHTING.");
                            break;
                        }
                        if (!lootPending && !habitsDoneThisKill) {
                            habitsDoneThisKill = true;
                            runBetweenKillHabits();
                        }
                        if (config.restockEnabled() && RestockTrips.shouldStartRestockTrip(killsSinceBank, restockTarget,
                                isScurriusPresent, lootPending, Rs2Inventory.getInventoryFood().size())) {
                            startRestockTrip();
                        }
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 400, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Microbot's antiban the way other plugins use it, with a combat activity. The action cooldown, micro breaks and
     * universal antiban stay off (the cooldown never ends in nonstop combat), and the safety hold in the loop keeps
     * anything else out of the fight.
     */
    private void setUpAntiban() {
        if (!config.antibanFramework()) {
            return;
        }
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.actionCooldownChance = 0.0;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.universalAntiban = false;
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
    }

    /**
     * Rs2Antiban.setSafetyHold exists only in newer clients (it keeps cooldowns and micro breaks out of a fight), so it is
     * called by reflection and is simply skipped where it is missing; the cooldown chance is 0 and micro breaks are off anyway.
     */
    private static void safetyHold(boolean hold) {
        try {
            Rs2Antiban.class.getMethod("setSafetyHold", boolean.class).invoke(null, hold);
        } catch (NoSuchMethodException ex) {
            // older client: nothing to hold
        } catch (Exception ex) {
            LOG.debug("setSafetyHold failed", ex);
        }
    }

    /**
     * Special attack on Scurrius: after eating and praying have had their turn and never while attacks are held. The spec
     * is turned on through the orb (Rs2Combat.setSpecState clicks it), and the attempt counts only when the energy drops.
     * With a spec weapon set it is wielded first and the main weapon (and shield) put back afterwards.
     */
    private void handleSpecialAttack(long now) {
        try {
            if (specPendingSince != 0) {
                int energy = Rs2Combat.getSpecEnergy();
                if (SpecialAttacks.fired(specEnergyBefore, energy)) {
                    specsUsed++;
                    specPendingSince = 0;
                    LOG.debug("Special attack fired (energy {} -> {})", specEnergyBefore, energy);
                    restoreMainWeapon();
                } else if (now - specPendingSince > SpecialAttacks.VERIFY_TIMEOUT_MS) {
                    LOG.debug("Special attack did not fire, trying again in a while");
                    specPendingSince = 0;
                    specRetryAt = now + SpecialAttacks.RETRY_COOLDOWN_MS;
                    Rs2Combat.setSpecState(false);
                    restoreMainWeapon();
                }
                return;
            }
            if (!config.specEnabled() || scurrius == null) {
                return;
            }
            String specWeapon = config.specWeapon() == null ? "" : config.specWeapon().trim();
            int needed = SpecialAttacks.neededPercent(config.specEnergyNeeded(), weaponSpecCost(specWeapon));
            int energyRaw = Rs2Combat.getSpecEnergy();
            if (energyRaw / 10 >= needed) {
                if (specAvailableSince == 0) {
                    specAvailableSince = now;
                    specDelayMs = SpecialAttacks.delayMs(habitRandom, config.antibanFramework());
                }
            } else {
                specAvailableSince = 0;
                return;
            }
            net.runelite.api.Actor target = Microbot.getClient().getLocalPlayer().getInteracting();
            boolean attackingBoss = target != null && target == scurrius.getNpc();
            int hpPercent = 100;
            net.runelite.api.NPC npc = scurrius.getNpc();
            if (npc.getHealthScale() > 0 && npc.getHealthRatio() >= 0) {
                hpPercent = npc.getHealthRatio() * 100 / npc.getHealthScale();
            }
            if (!SpecialAttacks.shouldSpec(true, energyRaw / 10, needed, !npc.isDead(), attackingBoss, isAttackHeld(), false,
                    now >= specRetryAt, now - specAvailableSince >= specDelayMs, hpPercent, config.specSkipBossHp())) {
                return;
            }
            if (!specWeapon.isEmpty() && !swapToSpecWeapon(specWeapon)) {
                specRetryAt = now + SpecialAttacks.RETRY_COOLDOWN_MS;
                return;
            }
            specEnergyBefore = energyRaw;
            if (Rs2Combat.setSpecState(true, needed * 10)) {
                specPendingSince = now;
                specAvailableSince = 0;
                lateReclickAt = 0; // the attack branch re-clicks Scurrius if the weapon swap dropped the target
            } else {
                restoreMainWeapon();
                specRetryAt = now + SpecialAttacks.RETRY_COOLDOWN_MS;
            }
        } catch (Exception ex) {
            LOG.debug("Special attack step failed", ex);
        }
    }

    /** The spec cost in 0-1000 of the spec weapon (or the equipped weapon when blank); 0 when unknown. */
    private int weaponSpecCost(String specWeaponName) {
        String name = specWeaponName;
        if (name.isEmpty()) {
            net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            name = weapon == null || weapon.getName() == null ? "" : weapon.getName();
        }
        String lower = name.toLowerCase();
        for (SpecialAttackWeaponEnum candidate : SpecialAttackWeaponEnum.values()) {
            if (lower.contains(candidate.getName())) {
                return candidate.getEnergyRequired();
            }
        }
        return 0;
    }

    /** Wields the spec weapon from the inventory, remembering the main weapon and shield. False if it cannot. */
    private boolean swapToSpecWeapon(String name) {
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel current = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (current != null && current.getName() != null && current.getName().toLowerCase().contains(name.toLowerCase())) {
            return true; // already wielded
        }
        if (!Rs2Inventory.hasItem(name)) {
            LOG.debug("Spec weapon {} is not in the inventory", name);
            return false;
        }
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel shield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        mainWeaponId = current == null ? -1 : current.getId();
        mainShieldId = shield == null ? -1 : shield.getId();
        Rs2Inventory.wield(name);
        boolean worn = sleepUntil(() -> {
            net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel now = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            return now != null && now.getName() != null && now.getName().toLowerCase().contains(name.toLowerCase());
        }, 2000);
        swappedForSpec = worn;
        return worn;
    }

    /** After a spec (or a failed one): the main weapon back, and the shield if a two-handed spec weapon pushed it off. */
    private void restoreMainWeapon() {
        if (!swappedForSpec) {
            return;
        }
        swappedForSpec = false;
        if (mainWeaponId != -1) {
            Rs2Inventory.wield(mainWeaponId);
            sleepUntil(() -> Rs2Equipment.isWearing(mainWeaponId), 2000);
        }
        if (mainShieldId != -1 && !Rs2Equipment.isWearing(EquipmentInventorySlot.SHIELD)) {
            Rs2Inventory.wield(mainShieldId);
            sleepUntil(() -> Rs2Equipment.isWearing(mainShieldId), 2000);
        }
        lateReclickAt = 0;
    }

    private void returnToPrayerTab() {
        ScurriusTickDriver driver = tickDriver;
        if (driver != null) {
            driver.returnToPrayerTab();
        }
    }

    private void humanPause(String where) {
        int ms = HumanHabits.pauseMs(habitRandom, config.habitPauses());
        if (ms > 0) {
            LOG.debug("Habit: pausing {} ms {}", ms, where);
            sleep(ms);
        }
    }

    /** Between kills, with Scurrius gone and the loot picked up: now and then one or two small human things. Short, never in the fight. */
    private void runBetweenKillHabits() {
        try {
            if (config.habitOffScreen() && HumanHabits.roll(habitRandom, HumanHabits.OFF_SCREEN_PERCENT)) {
                LOG.debug("Habit: mouse off-screen");
                Rs2Antiban.moveMouseOffScreen();
            }
            if (config.habitCheckSkills() && HumanHabits.roll(habitRandom, HumanHabits.SKILL_CHECK_PERCENT)) {
                LOG.debug("Habit: checking the skills tab");
                Rs2Tab.switchTo(InterfaceTab.SKILLS);
                sleep(1000 + habitRandom.nextInt(2000));
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            }
            if (config.habitCameraNudges() && HumanHabits.roll(habitRandom, HumanHabits.CAMERA_NUDGE_PERCENT)) {
                int target = ((Rs2Camera.getAngle() + HumanHabits.cameraNudgeDegrees(habitRandom)) % 360 + 360) % 360;
                LOG.debug("Habit: camera to {}", target);
                Rs2Camera.setAngle(target, 10);
            }
        } catch (Exception ex) {
            LOG.debug("Habit failed", ex);
        }
    }

    private int rollRestockTarget() {
        return RestockTrips.rollTarget(config.restockMinKills(), config.restockMaxKills(), ThreadLocalRandom.current());
    }

    /** Leaves after a kill and the loot, with food left: the teleport tab, else the walker. Never mid-fight. */
    private void startRestockTrip() {
        Microbot.log("Restock trip after " + killsSinceBank + " kills (planned " + restockTarget + ").");
        restockTrip = true;
        restockStartedAt = System.currentTimeMillis();
        emergencyActive = true; // keeps the fight rules from pulling the state back while we are still in the room
        state = Rs2Inventory.hasItem(TELEPORT_TAB_ID) ? State.TELEPORT_AWAY : State.BANKING;
    }

    /** A trip that did not work out: forget it and plan the next one. */
    private void skipRestockTrip() {
        restockTrip = false;
        emergencyActive = false;
        killsSinceBank = 0;
        restockTarget = rollRestockTarget();
    }

    private int rollBreakTarget() {
        return Breaks.rollTripTarget(config.breakMinTrips(), config.breakMaxTrips(), ThreadLocalRandom.current());
    }

    /** True while Microbot's own Break Handler (either version) is on a break: it owns logging out and in then. */
    private static boolean isGlobalBreakActive() {
        return BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive()
                || BreakHandlerV2State.isBreakActive();
    }

    /** Right after restocking, at the bank: prayers off, then a random-length break, logged out if configured. */
    private void startBreak() {
        long length = Breaks.rollLengthMs(config.breakMinMinutes(), config.breakMaxMinutes(), ThreadLocalRandom.current());
        onBreak = true;
        breakEndAt = System.currentTimeMillis() + length;
        breakEndsAtMs = breakEndAt;
        loginAttempts = 0;
        lastLoginAttempt = 0;
        disableAllPrayers();
        Microbot.log("Taking a break of " + Breaks.formatRemaining(length) + " after " + tripsSinceBreak + " restock trips"
                + (config.breakLogout() ? ", logging out." : "."));
    }

    private void endBreak() {
        onBreak = false;
        breakEndsAtMs = 0;
        tripsSinceBreak = 0;
        breakTripTarget = rollBreakTarget();
        Microbot.log("Break over. Next break after " + breakTripTarget + " restock trips.");
    }

    /**
     * Runs instead of the whole loop while on a break. Logging out uses the client's terminal logout, which also stops Auto
     * Login from logging straight back in; logging in again uses the client's own LoginManager with the active profile,
     * so no credentials are handled here. If Microbot's Break Handler starts a break meanwhile, ours is dropped and it
     * owns the login.
     */
    private void handleBreak() {
        if (isGlobalBreakActive()) {
            Microbot.log("Microbot's Break Handler started a break: dropping ours.");
            endBreak();
            return;
        }
        boolean timeUp = System.currentTimeMillis() >= breakEndAt;
        if (!timeUp) {
            if (config.breakLogout() && Microbot.isLoggedIn()) {
                Rs2Player.logoutWithoutAutoLogin();
            }
            return;
        }
        if (Microbot.isLoggedIn()) {
            endBreak();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLoginAttempt >= LOGIN_RETRY_MS) {
            lastLoginAttempt = now;
            loginAttempts++;
            boolean started = LoginManager.login();
            if (!started && loginAttempts % 4 == 1) {
                Microbot.log("Break over but login did not start (attempt " + loginAttempts
                        + "). It needs an active login profile (Auto Login plugin / account selector).");
            }
        }
    }

    /**
     * Withdraws until the inventory holds the full amount, staying in the bank. Returns false when the bank cannot supply it.
     * Each round waits for the inventory count to follow before judging the result.
     */
    private boolean topUp(int itemId, int required) {
        for (int attempt = 0; attempt < 6; attempt++) {
            if (Rs2Inventory.itemQuantity(itemId) >= required) {
                return true;
            }
            boolean asked = Rs2Bank.withdrawDeficit(itemId, required);
            sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= required, 3000);
            if (!asked && Rs2Inventory.itemQuantity(itemId) < required) {
                sleep(600); // the count may still be catching up; one more look before calling the bank empty
                if (Rs2Inventory.itemQuantity(itemId) >= required) {
                    return true;
                }
                if (!Rs2Bank.hasBankItem(itemId, required - Rs2Inventory.itemQuantity(itemId))) {
                    return false;
                }
            }
        }
        return Rs2Inventory.itemQuantity(itemId) >= required;
    }

    /**
     * Every bank visit empties the loot and restocks. Only a visit that follows fighting (a kill, or at least time in the
     * fight) is a restock trip: it restarts the kill count and the target and counts toward the next break. Visits at the
     * start or repeated visits with no fighting between them change nothing.
     */
    private void onBankVisitDone() {
        restockTrip = false;
        lootPending = false;
        boolean real = RestockTrips.isRealTrip(killsSinceBank, fightedSinceBank);
        fightedSinceBank = false;
        if (!real) {
            Microbot.log("Banked. No fighting since the last bank visit: trip and break counts unchanged.");
            return;
        }
        killsSinceBank = 0;
        restockTarget = rollRestockTarget();
        Microbot.log("Banked. Next restock trip after " + restockTarget + " kills.");
        tripsSinceBreak++;
        if (Breaks.shouldBreakNow(config.breakEnabled(), tripsSinceBreak, breakTripTarget, onBreak, isInFightRoom(),
                isGlobalBreakActive())) {
            startBreak();
        } else if (config.breakEnabled() && tripsSinceBreak >= breakTripTarget && isGlobalBreakActive()) {
            Microbot.log("Break due, but Microbot's own Break Handler is on a break: skipping ours for now.");
        } else {
            humanPause("after banking");
        }
    }

    /** Teleports out when the food is nearly gone and health is low. Returns true when it switched to TELEPORT_AWAY. */
    private boolean tryEmergencyTeleport(long now) {
        if (now < emergencyRetryAt) return false;
        int foodLeft = Rs2Inventory.getInventoryFood().size();
        int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        if (!shouldEmergencyTeleport(foodLeft, hp, maxHp, config)) return false;
        if (!Rs2Inventory.hasItem(TELEPORT_TAB_ID)) {
            if (!loggedNoTeleport) {
                Microbot.log("Emergency teleport wanted (food " + foodLeft + ", hp " + hp + "/" + maxHp
                        + ") but there is no Varrock teleport tab. Eating what is left.");
                loggedNoTeleport = true;
            }
            return false;
        }
        loggedNoTeleport = false;
        Microbot.log("Emergency teleport: food " + foodLeft + ", hp " + hp + "/" + maxHp + ".");
        emergencyActive = true;
        state = State.TELEPORT_AWAY;
        return true;
    }

    /**
     * Whether to teleport out: food left at or under the threshold and hitpoints under the percent of real max hitpoints.
     * Pure; hp is the boosted level, maxHp the real level.
     */
    static boolean shouldEmergencyTeleport(int foodLeft, int hp, int maxHp, ScurriusConfig cfg) {
        if (maxHp <= 0) return false;
        return foodLeft <= cfg.emergencyFoodLeft() && hp * 100 < maxHp * cfg.emergencyHpPercent();
    }

    private String getStateDescription(State state) {
        switch (state) {
            case BANKING:
                return "Out of food or prayer potions. Banking.";
            case TELEPORT_AWAY:
                return "Out of food or low on food and health. Teleporting away.";
            case WALK_TO_BOSS:
                return "Scurris is not present, walking to boss.";
            case FIGHTING:
                return "Engaging with Scurris.";
            case WAITING_FOR_BOSS:
                return "Waiting for Scurris to respawn.";
            default:
                return "Unknown state.";
        }
    }

    private boolean isInFightRoom() {
        return isInArea(fightRoom, Rs2Player.getWorldLocation());
    }

    static boolean isInArea(WorldArea area, WorldPoint location) {
        return location != null && area.contains(location);
    }

    static boolean isLiveGiantRat(Rs2NpcModel npc) {
        String name = npc.getName();
        return name != null && name.toLowerCase().contains("giant rat") && !npc.getNpc().isDead();
    }

    /** Whether a level (hitpoints or prayer) is under the percent of its real maximum. Pure. */
    static boolean shouldEat(int level, int maxLevel, int thresholdPercent) {
        return maxLevel > 0 && level * 100 < maxLevel * thresholdPercent;
    }

    static int randomThreshold(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ThreadLocalRandom.current().nextInt(low, high + 1);
    }

    private long lastSupplyShortageLog = 0;

    /** hasRequiredSupplies runs every loop now, so the shortage is logged at most every 10 seconds. */
    private void logSupplyShortage(String message) {
        long now = System.currentTimeMillis();
        if (now - lastSupplyShortageLog > 10_000) {
            lastSupplyShortageLog = now;
            Microbot.log(message);
        }
    }

    private boolean hasRequiredSupplies() {
        if (Rs2Inventory.count(TELEPORT_TAB_ID) < config.teleportAmount()) {
            logSupplyShortage("Not enough teleport tabs in inventory. Expected: " + config.teleportAmount());
            return false;
        }
        int foodAmount = config.foodAmount();
        int foodItemId = config.foodSelection().getId();
        int prayerPotionAmount = config.prayerPotionAmount();
        int potionItemId = config.potionSelection().getItemId();

        int currentFoodCount = Rs2Inventory.count(foodItemId);
        if (currentFoodCount < foodAmount) {
            logSupplyShortage("Not enough food in inventory. Expected: " + foodAmount + ", Found: " + currentFoodCount);
            return false;
        }

        int currentPrayerPotionCount = Rs2Inventory.count(potionItemId);
        if (currentPrayerPotionCount < prayerPotionAmount) {
            logSupplyShortage("Not enough prayer potions in inventory. Expected: " + prayerPotionAmount + ", Found: " + currentPrayerPotionCount);
            return false;
        }

        return true;
    }

    /** Loots; returns true when something was picked up (more may follow). */
    private boolean attemptLooting(ScurriusConfig config) {
        List<String> lootItems = parseLootItems(config.lootItems());
        LootingParameters nameParams = new LootingParameters(10, 1, 1, 0, false, true, lootItems.toArray(new String[0]));
        boolean byName = Rs2GroundItem.lootItemsBasedOnNames(nameParams);
        LootingParameters valueParams = new LootingParameters(10, 1, config.lootValueThreshold(), 0, false, true);
        boolean byValue = Rs2GroundItem.lootItemBasedOnValue(valueParams);
        return byName | byValue;
    }

    private List<String> parseLootItems(String lootFilter) {
        return Arrays.asList(lootFilter.toLowerCase().split(","));
    }

    public void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        Microbot.log("All prayers disabled to preserve prayer points.");
    }

    @Override
    public void shutdown() {
        super.shutdown();
        disableAllPrayers();
        safetyHold(false);
        if (config != null && config.antibanFramework()) {
            Rs2Antiban.resetAntibanSettings();
        }
    }
}
