package net.runelite.client.plugins.microbot.drodriftnet;

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

/** Single travel sequence, resumed from the observed location after login. */
final class DroDriftTravel
{
    static final WorldPoint ISLAND_BANK = new WorldPoint(3764, 3900, 0);
    static final WorldPoint ENTRANCE = new WorldPoint(3729, 10294, 1);
    private final DroDriftNetConfig config;
    private final ScheduledFuture<?> future;
    private final BaseProfileDro profile;
    private final Consumer<String> status;
    private boolean equipped;
    private boolean loaded;
    private String failure;
    private long lastInput;
    private int setupAttempts;

    DroDriftTravel(DroDriftNetConfig config,
                   ScheduledFuture<?> future, BaseProfileDro profile, Consumer<String> status)
    {
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
            status.accept("Select a Drift Net inventory setup for automatic travel");
            return;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
        if (System.currentTimeMillis() - lastInput < 1800) return;

        // Already underwater: preserve gear and swim to Ceto/entrance using local movement.
        if (here.getPlane() == 1 && here.getRegionID() == 15008) {
            if (!hasBreathingGear()) { fail("Equip fishbowl helmet and diving apparatus before swimming"); return; }
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
                DroInventorySetup setup = new DroInventorySetup(config.driftInventorySetup(), future);
                beforeInput();
                boolean success;
                if (!equipped) {
                    success = setup.loadEquipment();
                    if (success) equipped = setup.doesEquipmentMatch();
                } else {
                    success = setup.loadInventory();
                    if (success) loaded = setup.doesInventoryMatch();
                }
                if (!success && ++setupAttempts >= 3) fail("Could not load Drift Net setup; check bank supplies and restart");
                return;
            }
            if (Rs2Bank.isOpen()) { beforeInput(); Rs2Bank.closeBank(); return; }
            if (!hasBreathingGear()) { fail("Drift Net setup needs fishbowl helmet and diving apparatus equipped"); return; }
            if (removeSwimmingWeapon()) return;
            int weight = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getWeight()).orElse(100);
            if (weight > 27) { fail("Drift Net setup is too heavy to swim; reduce it below 28 kg"); return; }
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
        status.accept("Travelling to the island bank: Rs2Walker");
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
            if (Rs2Inventory.emptySlotCount() == 0) { fail("Leave space in the setup to remove weapon/shield before swimming"); return true; }
            beforeInput();
            Rs2Equipment.unEquip(slot);
            return true;
        }
        return false;
    }

    private void clickObject(int id, String... options)
    {
        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query().withId(id).nearestOnClientThread();
        if (object == null) { status.accept("Waiting for travel object " + id); return; }
        String action = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var composition = object.getObjectComposition();
            if (composition == null || composition.getActions() == null) return null;
            return Arrays.stream(composition.getActions()).filter(a -> a != null
                    && Arrays.stream(options).anyMatch(a::equalsIgnoreCase)).findFirst().orElse(null);
        }).orElse(null);
        if (action == null) { status.accept("Travel action unavailable; check the rowboat/shortcut"); return; }
        beforeInput();
        object.click(action);
    }

    private void beforeInput()
    {
        lastInput = System.currentTimeMillis();
        profile.beforeAction(BaseProfileDro.ActionPhase.SETUP, false);
    }

    private void fail(String reason)
    {
        failure = reason;
        Rs2Walker.clearWalkingRoute("Drift travel failure: " + reason);
        status.accept(reason);
    }
}
