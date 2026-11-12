package net.runelite.client.plugins.microbot.karambwans;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.karambwans.enums.KarambwanBankLocation;
import net.runelite.client.plugins.microbot.karambwans.enums.FairyRingAccessMethod;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.karambwans.GabulhasKarambwansInfo.botStatus;
import static net.runelite.client.plugins.microbot.karambwans.GabulhasKarambwansInfo.states;

@Slf4j
public class GabulhasKarambwansScript extends Script {
    // Zanaris fairy ring object ID — the old scene scan did NOT find it; use the tile object cache.
    public static final int FAIRY_RING_ID = 29560;
    private static final String FISHING_RING_CODE = "DKP";
    private static final int MAX_WALK_TO_FISH_ATTEMPTS = 5;
    private static final int FISHING_AREA_RADIUS = 15;
    private static final int HOUSE_TELEPORT_MIN_DISTANCE = 20;
    private static final int[] DIAL_SLOTS = {InterfaceID.Fairyrings.ROOT_MODEL3, InterfaceID.Fairyrings.ROOT_MODEL4, InterfaceID.Fairyrings.ROOT_MODEL5};
    private static final int[] DIAL_CLOCKWISE = {InterfaceID.Fairyrings._1_CLOCKWISE, InterfaceID.Fairyrings._2_CLOCKWISE, InterfaceID.Fairyrings._3_CLOCKWISE};
    private static final int[] DIAL_ANTICLOCKWISE = {InterfaceID.Fairyrings._1_ANTICLOCKWISE, InterfaceID.Fairyrings._2_ANTICLOCKWISE, InterfaceID.Fairyrings._3_ANTICLOCKWISE};
    // Fairy ring is at 4434, not 4435 — off-by-one causes findObjectByLocation to miss it.
    private final WorldPoint zanarisRingPoint = new WorldPoint(2412, 4434, 0);
    private final WorldPoint fishingPoint = new WorldPoint(2899, 3118, 0);
    private final WorldPoint bankPoint = new WorldPoint(2381, 4455, 0);
    private GabulhasKarambwansConfig config;
    private int failedWalksToFish;
    private FishTravelFailure lastTravelFailure = FishTravelFailure.ZANARIS_RING;
    private boolean houseTeleportLanded;

    public boolean run(GabulhasKarambwansConfig config) {
        this.config = config;
        failedWalksToFish = 0;
        houseTeleportLanded = false;
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.setActivity(Activity.CATCHING_RAW_KARAMBWAN);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case FISHING:
                        if (!fishingLoop()) {
                            botStatus = states.WALKING_TO_FISH;
                            break;
                        }
                        Rs2Antiban.takeMicroBreakByChance();
                        botStatus = states.WALKING_TO_RING_TO_BANK;
                        Rs2Player.waitForAnimation();
                        break;
                    case WALKING_TO_RING_TO_BANK:
                        walkToRingToBank();
                        Rs2Random.waitEx(400, 200);
                        botStatus = states.WALKING_TO_BANK;
                        break;
                    case WALKING_TO_BANK:
                        doBank();
                        botStatus = states.BANKING;
                        Rs2Random.waitEx(400, 200);
                        break;
                    case BANKING:
                        useBank();
                        botStatus = states.WALKING_TO_FISH;
                        Rs2Random.waitEx(400, 200);
                        break;
                    case WALKING_TO_FISH:
                        if (walkToFish()) {
                            failedWalksToFish = 0;
                            botStatus = states.FISHING;
                        } else if (FishTravelFailure.shouldStop(lastTravelFailure, houseTeleportLanded, ++failedWalksToFish, MAX_WALK_TO_FISH_ATTEMPTS)) {
                            Microbot.showMessage(lastTravelFailure.getMessage());
                            shutdown();
                            return;
                        } else {
                            log.warn("Did not arrive at the karambwan fishing spot, retrying ({}/{})", failedWalksToFish, MAX_WALK_TO_FISH_ATTEMPTS);
                        }
                        Rs2Random.waitEx(400, 200);
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private boolean fishingLoop() {
        while (!Rs2Inventory.isFull() && super.isRunning()) {
            if (!Rs2Player.isAnimating()) {
                if (!isNearFishingSpot()) {
                    log.warn("Not at the karambwan fishing spot, travelling there first");
                    return false;
                }
                if (Rs2Inventory.contains(ItemID.TBWT_RAW_KARAMBWANJI)) {
                    interactWithFishingSpot();
                    Rs2Player.waitForAnimation();
                    sleep(2000, 4000);
                } else {
                    Microbot.showMessage("Raw karambwanji not detected. Shutting down");
                    shutdown();
                    return true;
                }
            }
        }
        return true;
    }

    private void walkToRingToBank() {
        if (config.bankLocation() != KarambwanBankLocation.NONE) {
            return;
        }
        WorldPoint ringLocation = new WorldPoint(2900, 3111, 0); // Karamja ring
        var fairyRing = Microbot.getRs2TileObjectCache().query().nearest(ringLocation, 3);
        if (fairyRing != null) {
            fairyRing.click("Zanaris");
            Rs2Player.waitForAnimation();
        }
    }

    private void doBank() {
        if (config.bankLocation() == KarambwanBankLocation.SEERS &&
                Microbot.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1
                && Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT))
        {
            Rs2Magic.cast(Rs2Spells.CAMELOT_TELEPORT, "Seers'", 2);
            sleepUntil(() -> Rs2Bank.isNearBank(net.runelite.client.plugins.microbot.util.bank.enums.BankLocation.CAMELOT, 15), 5000);
        } else {
            Rs2Walker.walkTo(bankPoint, 3);
            while (!Rs2Player.isInArea(bankPoint, 4) && super.isRunning()) {
                Rs2Player.waitForWalking();
            }
        }
        if (!Rs2Bank.walkToBankAndUseBank()) {
            Rs2Bank.openBank();
        }
    }

    private void useBank() {
        Rs2Bank.depositAll(ItemID.TBWT_RAW_KARAMBWAN);
        Rs2Inventory.waitForInventoryChanges(2000);
        if (Rs2Inventory.contains("scroll") || Rs2Inventory.contains("Scroll")) {
            Rs2Bank.depositAll("scroll");
            Rs2Bank.depositAll("Scroll");
            Rs2Inventory.waitForInventoryChanges(2000);
        }
        if (Rs2Inventory.contains("scrollbox") || Rs2Inventory.contains("Scrollbox")) {
            Rs2Bank.depositAll("scrollbox");
            Rs2Bank.depositAll("Scrollbox");
            Rs2Inventory.waitForInventoryChanges(2000);
        }
        if (Rs2Inventory.contains(ItemID.FISH_BARREL_OPEN) || Rs2Inventory.contains(ItemID.FISH_BARREL_CLOSED)) {
            Rs2Bank.emptyFishBarrel();
            Rs2Inventory.waitForInventoryChanges(2000);
        }



        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
    }

    private void interactWithFishingSpot() {
        Microbot.getRs2NpcCache().query().withId(NpcID._0_45_48_KARAMBWAN).interact("Fish");
    }

    private boolean walkToFish() {
        if (isNearFishingSpot()) {
            return true;
        }
        if (config.fairyRingAccessMethod() == FairyRingAccessMethod.POH) {
            houseTeleportLanded = false;
            if (findPohFairyRing() == null) {
                WorldPoint before = Rs2Player.getWorldLocation();
                if (hasPohCape()) {
                    Rs2Equipment.interact(net.runelite.api.EquipmentInventorySlot.CAPE, "Tele to POH");
                } else if (Rs2Inventory.hasItem("Teleport to house")) {
                    Rs2Inventory.interact("Teleport to house", "Break");
                } else {
                    Rs2Magic.quickCast(MagicAction.TELEPORT_TO_HOUSE);
                }
                if (!sleepUntil(() -> findPohFairyRing() != null, 10000)) {
                    WorldPoint after = Rs2Player.getWorldLocation();
                    houseTeleportLanded = before != null && after != null && before.distanceTo(after) > HOUSE_TELEPORT_MIN_DISTANCE;
                    lastTravelFailure = FishTravelFailure.HOUSE_RING_NOT_FOUND;
                    log.warn("No fairy ring or spiritual fairy tree found after the house teleport");
                    return false;
                }
            }

            lastTravelFailure = FishTravelFailure.HOUSE_RING_TRAVEL;
            return useFairyRing(findPohFairyRing()) && waitTillPlayerNextToFishingSpot();
        } else {
            lastTravelFailure = FishTravelFailure.ZANARIS_RING;
            Rs2Walker.walkTo(zanarisRingPoint, 3);
            Rs2Player.waitForWalking();

            sleepUntil(() -> findZanarisFairyRing() != null, 5000);

            if (useFairyRing(findZanarisFairyRing())) {
                return waitTillPlayerNextToFishingSpot();
            }
            Rs2Player.waitForWalking();
            return false;
        }
    }

    private Rs2TileObjectModel findPohFairyRing() {
        return Microbot.getRs2TileObjectCache().query()
                .withIds(ObjectID.POH_FAIRY_RING, ObjectID.POH_SPIRIT_RING)
                .nearest();
    }

    private Rs2TileObjectModel findZanarisFairyRing() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(FAIRY_RING_ID)
                .nearestOnClientThread();
    }

    private boolean useFairyRing(Rs2TileObjectModel ring) {
        if (ring == null) {
            log.warn("No fairy ring or spiritual fairy tree found nearby");
            return false;
        }
        ObjectComposition composition = ring.getObjectComposition();
        String[] actions = composition == null ? null : composition.getActions();
        String travel = FairyRingDial.findAction(actions,
                "Last-destination (" + FISHING_RING_CODE + ")",
                "Ring-last-destination (" + FISHING_RING_CODE + ")");
        if (travel != null) {
            return ring.click(travel);
        }
        String configure = FairyRingDial.findAction(actions, "Configure", "Ring-configure");
        if (configure == null || !ring.click(configure)) {
            log.warn("The fairy ring has no Configure or Last-destination ({}) option", FISHING_RING_CODE);
            return false;
        }
        return dialFairyRing(FISHING_RING_CODE);
    }

    private boolean dialFairyRing(String code) {
        if (!sleepUntil(() -> !Rs2Widget.isHidden(InterfaceID.Fairyrings.CONFIRM), 10000)) {
            log.warn("Fairy ring interface did not open");
            return false;
        }
        for (int i = 0; i < DIAL_SLOTS.length; i++) {
            if (!rotateDial(DIAL_SLOTS[i], DIAL_CLOCKWISE[i], DIAL_ANTICLOCKWISE[i], FairyRingDial.rotationFor(code.charAt(i)))) {
                log.warn("Could not set fairy ring dial {} for code {}", i + 1, code);
                return false;
            }
        }
        return Rs2Widget.clickWidget(InterfaceID.Fairyrings.CONFIRM);
    }

    private boolean rotateDial(int slot, int clockwiseButton, int anticlockwiseButton, int desiredRotation) {
        int rotation = dialRotation(slot);
        if (rotation < 0 || desiredRotation < 0) {
            return false;
        }
        int turns = FairyRingDial.clockwiseTurns(rotation, desiredRotation);
        int button = turns < 0 ? anticlockwiseButton : clockwiseButton;
        for (int i = 0; i < Math.abs(turns) && super.isRunning(); i++) {
            int before = dialRotation(slot);
            Rs2Widget.clickWidget(button);
            if (!sleepUntil(() -> dialRotation(slot) != before, 2000)) {
                return false;
            }
        }
        return sleepUntil(() -> dialRotation(slot) == desiredRotation, 3000);
    }

    private int dialRotation(int slot) {
        Widget dial = Rs2Widget.getWidget(slot);
        return dial == null ? -1 : dial.getRotationY();
    }

    private boolean isNearFishingSpot() {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && location.distanceTo(fishingPoint) <= FISHING_AREA_RADIUS;
    }

    private boolean hasPohCape() {
        return Rs2Equipment.isWearing("Construct. cape") || 
               Rs2Equipment.isWearing("Construct. cape(t)") || 
               Rs2Equipment.isWearing("Max cape") || 
               Rs2Equipment.isWearing("Max cape(t)");
    }

    private boolean waitTillPlayerNextToFishingSpot() {
        return sleepUntil(this::isNearFishingSpot, 15000);
    }
}

