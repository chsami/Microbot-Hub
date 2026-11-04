package net.runelite.client.plugins.microbot.driftnet;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;

/** Single travel sequence, resumed from the observed location after login. */
final class DriftNetTravel
{
    static final WorldPoint ISLAND_BANK = new WorldPoint(3764, 3900, 0);
    static final WorldPoint ENTRANCE = new WorldPoint(3729, 10294, 1);
    private final DriftNetConfig config;
    private final ScheduledFuture<?> future;
    private final DriftNetProfile profile;
    private final Consumer<String> status;
    private final Consumer<String> stop;
    private final Set<String> logged = new HashSet<>();
    private boolean equipped;
    private boolean loaded;
    private String failure;
    private long lastInput;
    private int setupAttempts;

    DriftNetTravel(DriftNetConfig config,
                   ScheduledFuture<?> future, DriftNetProfile profile, Consumer<String> status,
                   Consumer<String> stop)
    {
        this.stop = stop;
        this.config = config;
        this.future = future;
        this.profile = profile;
        this.status = status;
    }

    static boolean atFishingEntrance(WorldPoint here)
    {
        return here != null && here.getRegionID() == 15008 && here.getPlane() == 1
                && (here.getX() >= 3737 && here.getY() >= 10285
                    || here.distanceTo(ENTRANCE) <= 3);
    }

    void tick(WorldPoint here)
    {
        if (failure != null) { status.accept(failure); return; }
        if (config.driftInventorySetup() == null) {
            String msg = "Pick a Drift Net setup for Travel";
            logOnce("Drift Net: pick an inventory setup in the config to use Travel");
            status.accept(msg);
            return;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
        if (System.currentTimeMillis() - lastInput < 1800) return;

        // Already underwater: preserve gear and swim to Ceto/entrance using local movement.
        if (here.getPlane() == 1 && here.getRegionID() == 15008) {
            if (!hasBreathingGear()) { fail("Wear fishbowl helmet and diving gear"); return; }
            if (removeSwimmingWeapon()) return;
            status.accept("Swimming to the Drift Net entrance");
            beforeInput();
            Rs2Walker.walkFastCanvas(ENTRANCE, false);
            return;
        }
        if (here.distanceTo(ISLAND_BANK) <= 10) {
            Rs2Walker.clearWalkingRoute("Arrived at island bank");
            if (!loaded) {
                status.accept("Loading Drift Net setup at the island bank");
                if (!Rs2Bank.isOpen()) {
                    clickObject(30796, "Use", "Bank");
                    return;
                }
                DriftNetInventorySetup setup = new DriftNetInventorySetup(config.driftInventorySetup(), future,
                        item -> {
                            failure = "Stopped: no " + item + " in bank";
                            stop.accept("your bank has no " + item + " for the Drift Net setup");
                        });
                beforeInput();
                boolean success;
                if (!equipped) {
                    success = setup.loadEquipment();
                    if (success) equipped = setup.doesEquipmentMatch();
                } else {
                    success = setup.loadInventory();
                    if (success) loaded = setup.doesInventoryMatch();
                }
                if (failure != null) return;
                if (!success && ++setupAttempts >= 3) fail("Could not load setup; check your bank");
                return;
            }
            if (Rs2Bank.isOpen()) { beforeInput(); Rs2Bank.closeBank(); return; }
            if (!hasBreathingGear()) { fail("Setup needs fishbowl helmet and diving gear"); return; }
            if (removeSwimmingWeapon()) return;
            int weight = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getWeight()).orElse(100);
            if (weight > 27) { fail("Setup too heavy to swim; keep it under 28 kg"); return; }
            status.accept("Diving from the island rowboat");
            if (Rs2Dialogue.hasDialogueOption("Dive into the sea", false)) {
                beforeInput();
                Rs2Dialogue.clickOption("Dive into the sea", false);
            } else if (Rs2Dialogue.hasContinue()) {
                beforeInput();
                Rs2Dialogue.clickContinue();
            } else clickObject(ObjectID.FOSSIL_ROWBOAT_DIVING, "Travel");
            return;
        }
        status.accept("Travelling to the Fossil Island bank");
        Rs2Walker.walkTo(ISLAND_BANK, 4);
    }

    private boolean hasBreathingGear()
    {
        return Rs2Equipment.isWearing(ItemID.HUNDRED_PIRATE_DIVING_HELMET)
                && Rs2Equipment.isWearing(ItemID.HUNDRED_PIRATE_DIVING_BACKPACK);
    }

    private boolean removeSwimmingWeapon()
    {
        for (EquipmentInventorySlot slot : new EquipmentInventorySlot[]{EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.SHIELD}) {
            if (Rs2Equipment.get(slot) == null) continue;
            if (Rs2Inventory.emptySlotCount() == 0) { fail("Leave a free slot for weapon and shield"); return true; }
            beforeInput();
            Rs2Equipment.unEquip(slot);
            return true;
        }
        return false;
    }

    private void clickObject(int id, String... options)
    {
        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query().withId(id).nearestOnClientThread();
        if (object == null) { status.accept("Waiting for the bank or rowboat"); return; }
        String action = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var composition = object.getObjectComposition();
            if (composition == null || composition.getActions() == null) return null;
            return Arrays.stream(composition.getActions()).filter(a -> a != null
                    && Arrays.stream(options).anyMatch(a::equalsIgnoreCase)).findFirst().orElse(null);
        }).orElse(null);
        if (action == null) { status.accept("Can't use the rowboat; retrying"); return; }
        beforeInput();
        object.click(action);
    }

    private void logOnce(String message)
    {
        if (logged.add(message)) Microbot.log(message);
    }

    private void beforeInput()
    {
        lastInput = System.currentTimeMillis();
        profile.beforeAction(DriftNetProfile.ActionPhase.SETUP, false);
    }

    private void fail(String reason)
    {
        failure = reason;
        logOnce("Drift Net travel stopped: " + reason);
        Rs2Walker.clearWalkingRoute("Drift travel failure: " + reason);
        status.accept(reason);
    }
}
