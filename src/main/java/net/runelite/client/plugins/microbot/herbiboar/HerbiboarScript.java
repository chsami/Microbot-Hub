package net.runelite.client.plugins.microbot.herbiboar;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HerbiboarScript extends Script {

    public static int herbiCaught;
    @Setter
    @Getter
    public static HerbiboarState state = HerbiboarState.INITIALIZING;
    private boolean attackedTunnel;
    private static final WorldPoint BANK_LOCATION = new WorldPoint(3766, 3899, 0);
    private static final WorldPoint RETURN_LOCATION = new WorldPoint(3703, 3878, 0);

    @Getter
    @Setter
    private Instant lastMove = Instant.now();

    @Getter
    @Setter
    private WorldPoint lastLocation = null;

    public static String version = HerbiboarPlugin.version;

    public void incrementHerbisCaught() {
        herbiCaught++;
    }


    public void resetHerbisCaught() {
        herbiCaught = 0;
    }

    public HerbiboarState getCurrentState() {
        return state;
    }
    
    public void handleConfusionMessage() {
        setState(HerbiboarState.START);
        attackedTunnel = false;
    }
    
    
    private boolean isNearBank() {
        return Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION) <= 5;
    }

    /** Check if the player's run energy is below the configured threshold.
     *
     * @param config The HerbiboarConfig containing the threshold setting.
     * @return true if run energy is at or below the threshold, false otherwise.
     */
    private boolean energyUnderThreshold(HerbiboarConfig config) {
        return Rs2Player.getRunEnergy() <= config.thresholdEnergy();
    }
    
    private void manageRunEnergy(HerbiboarConfig config) {
        HerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();

        switch (energyOption) {
            case STAMINA_POTION:
                if ((config.stamBuffAlwaysActive() && Rs2Player.hasStaminaBuffActive()) ||
                        (!config.stamBuffAlwaysActive() && !energyUnderThreshold(config))) {
                    return;
                }
                if (Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA)) {
                    Rs2Inventory.interact(ItemID._4DOSESTAMINA, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._3DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._2DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._1DOSESTAMINA, "Drink");
                    }
                }
                break;
            case SUPER_ENERGY_POTION:
                if (!energyUnderThreshold(config)) return;
                if (Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE2ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE2ENERGY, "Drink");
                    }
                }
                break;
            case ENERGY_POTION:
                if (!energyUnderThreshold(config)) return;
                if (Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE1ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE1ENERGY, "Drink");
                    }
                }
                break;
            case STRANGE_FRUIT:
                if (!energyUnderThreshold(config)) return;
                if (Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT)) {
                    Rs2Inventory.interact(ItemID.MACRO_TRIFFIDFRUIT, "Eat");
                }
                break;
            case NONE:
            default:
                break;
        }
    }
    
    private void dropConfiguredItems(HerbiboarConfig config) {
        if (config.dropEmptyVials()) {
            dropIfPresent(ItemID.VIAL_EMPTY);
        }
        if (config.dropSmallFossil()) {
            dropIfPresent(ItemID.FOSSIL_SMALL_UNID, ItemID.FOSSIL_SMALL_1, ItemID.FOSSIL_SMALL_2, 
                         ItemID.FOSSIL_SMALL_3, ItemID.FOSSIL_SMALL_4, ItemID.FOSSIL_SMALL_5);
        }
        if (config.dropMediumFossil()) {
            dropIfPresent(ItemID.FOSSIL_MEDIUM_UNID, ItemID.FOSSIL_MEDIUM_1, ItemID.FOSSIL_MEDIUM_2, 
                         ItemID.FOSSIL_MEDIUM_3, ItemID.FOSSIL_MEDIUM_4, ItemID.FOSSIL_MEDIUM_5);
        }
        if (config.dropLargeFossil()) {
            dropIfPresent(ItemID.FOSSIL_LARGE_UNID, ItemID.FOSSIL_LARGE_1, ItemID.FOSSIL_LARGE_2, 
                         ItemID.FOSSIL_LARGE_3, ItemID.FOSSIL_LARGE_4, ItemID.FOSSIL_LARGE_5);
        }
        if (config.dropRareFossil()) {
            dropIfPresent(ItemID.FOSSIL_RARE_UNID, ItemID.FOSSIL_RARE_1, ItemID.FOSSIL_RARE_2, 
                         ItemID.FOSSIL_RARE_3, ItemID.FOSSIL_RARE_4, ItemID.FOSSIL_RARE_5, ItemID.FOSSIL_RARE_6);
        }
        if (config.dropGuam()) {
            dropIfPresent(ItemID.UNIDENTIFIED_GUAM);
        }
        if (config.dropMarrentill()) {
            dropIfPresent(ItemID.UNIDENTIFIED_MARENTILL);
        }
        if (config.dropTarromin()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TARROMIN);
        }
        if (config.dropHarralander()) {
            dropIfPresent(ItemID.UNIDENTIFIED_HARRALANDER);
        }
        if (config.dropRanarr()) {
            dropIfPresent(ItemID.UNIDENTIFIED_RANARR);
        }
        if (config.dropToadflax()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TOADFLAX);
        }
        if (config.dropIrit()) {
            dropIfPresent(ItemID.UNIDENTIFIED_IRIT);
        }
        if (config.dropAvantoe()) {
            dropIfPresent(ItemID.UNIDENTIFIED_AVANTOE);
        }
        if (config.dropKwuarm()) {
            dropIfPresent(ItemID.UNIDENTIFIED_KWUARM);
        }
        if (config.dropSnapdragon()) {
            dropIfPresent(ItemID.UNIDENTIFIED_SNAPDRAGON);
        }
        if (config.dropCadantine()) {
            dropIfPresent(ItemID.UNIDENTIFIED_CADANTINE);
        }
        if (config.dropLantadyme()) {
            dropIfPresent(ItemID.UNIDENTIFIED_LANTADYME);
        }
        if (config.dropDwarfWeed()) {
            dropIfPresent(ItemID.UNIDENTIFIED_DWARF_WEED);
        }
        if (config.dropTorstol()) {
            dropIfPresent(ItemID.UNIDENTIFIED_TORSTOL);
        }
    }
    
    private void dropIfPresent(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) {
                Rs2Inventory.drop(itemId);
            }
        }
    }

    private boolean needsToBank(HerbiboarConfig config) {
        return Rs2Inventory.emptySlotCount() < 4 || !hasRequiredInventorySetup(config);
    }

    private boolean hasRequiredInventorySetup(HerbiboarConfig config) {
        HerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();
        boolean hasEnergyItems;
        switch (energyOption) {
            case STAMINA_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA);
                break;
            case SUPER_ENERGY_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY);
                break;
            case ENERGY_POTION:
                hasEnergyItems = Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY);
                break;
            case STRANGE_FRUIT:
                hasEnergyItems = Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT);
                break;
            case NONE:
            default:
                hasEnergyItems = true;
                break;
        }
        boolean hasHerbSack = !config.useHerbSack() || Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN);
        boolean hasMagicSecateurs = !config.useMagicSecateurs() || (Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS) ||
                Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS));

        return hasEnergyItems && hasHerbSack && hasMagicSecateurs;
    }

    /* Withdraw potions using greedy exact-dose logic: try to reach target doses starting with the highest dose potions */
    private void withdrawDosesDescending(int[] ids, int[] doses, int targetDoses) {
        Microbot.log(Level.INFO, "Withdrawing potions to reach "+targetDoses+" total doses");

        // Calculate current doses we already have
        int current = 0;
        for (int i = 0; i < ids.length; i++) {
            int count = Rs2Inventory.count(ids[i]);
            current += count * doses[i];
            Microbot.log(Level.INFO,"Already have "+count+"x "+doses[i]+"-dose potions = "+(count * doses[i])+" doses");
        }

        int remaining = targetDoses - current;
        Microbot.log(Level.INFO,"Current total: "+current+" doses, Need: "+remaining+" more doses to reach target of "+targetDoses);

        if (remaining <= 0) {
            Microbot.log(Level.INFO,"Already have enough doses, no need to withdraw more");
            return;
        }

        // Try withdrawing potions starting with highest dose first
        for (int i = 0; i < ids.length && remaining > 0; i++) {
            int id = ids[i];
            int dose = doses[i];
            int potionsToWithdraw = (int) Math.ceil(remaining / (double) dose); // How many of this potion we need

            if (potionsToWithdraw <= 0) {
                Microbot.log(Level.INFO,"Don't need any {}-dose potions (remaining doses: {})", dose, remaining);
                continue;
            }

            Microbot.log(Level.INFO,"Attempting to withdraw {}x {}-dose potions (id: {})", potionsToWithdraw, dose, id);

            int before = Rs2Inventory.count(id);
            if (potionsToWithdraw == 1) {
                Rs2Bank.withdrawOne(id);
            } else {
                Rs2Bank.withdrawX(id, potionsToWithdraw);
            }

            Rs2Inventory.waitForInventoryChanges(5000);

            int after = Rs2Inventory.count(id);
            int withdrawn = after - before;

            if (withdrawn > 0) {
                int dosesWithdrawn = withdrawn * dose;
                remaining -= dosesWithdrawn;
                Microbot.log(Level.INFO,"Successfully withdrawn {}x {}-dose potions = {} doses", withdrawn, dose, dosesWithdrawn);
                Microbot.log(Level.INFO,"Remaining doses needed: {}", remaining);

                // Recalculate current total doses after this withdrawal
                current = 0;
                for (int j = 0; j < ids.length; j++) {
                    int count = Rs2Inventory.count(ids[j]);
                    current += count * doses[j];
                }
                Microbot.log(Level.INFO,"Current total: {} doses", current);

                // If we've reached or exceeded our target, stop withdrawing
                if (current >= targetDoses) {
                    Microbot.log(Level.INFO,"Reached or exceeded target doses ({}), stopping withdrawal", targetDoses);
                    break;
                }
            } else {
                Microbot.log(Level.INFO,"Failed to withdraw any {}-dose potions", dose);
            }
        }

        // Log final counts
        current = 0;
        Microbot.log(Level.INFO,"--- Final inventory after withdrawal ---");
        for (int i = 0; i < ids.length; i++) {
            int count = Rs2Inventory.count(ids[i]);
            current += count * doses[i];
            Microbot.log(Level.INFO,"{}x {}-dose potions = {} doses", count, doses[i], count * doses[i]);
        }
        Microbot.log(Level.INFO,"Final total: {} doses (target was {})", current, targetDoses);
    }

    private int getEnergyDoseCount(HerbiboarConfig.RunEnergyOption option) {
        // Check inventory for all potions regardless of type to get a more accurate count
        Microbot.log(Level.INFO,"Checking inventory for all potion types");
        int totalDoses = 0;

        // Count all potion types in inventory
        int staminaCount = Rs2Inventory.count(ItemID._4DOSESTAMINA) * 4 +
                          Rs2Inventory.count(ItemID._3DOSESTAMINA) * 3 +
                          Rs2Inventory.count(ItemID._2DOSESTAMINA) * 2 +
                          Rs2Inventory.count(ItemID._1DOSESTAMINA);

        int superEnergyCount = Rs2Inventory.count(ItemID._4DOSE2ENERGY) * 4 +
                            Rs2Inventory.count(ItemID._3DOSE2ENERGY) * 3 +
                            Rs2Inventory.count(ItemID._2DOSE2ENERGY) * 2 +
                            Rs2Inventory.count(ItemID._1DOSE2ENERGY);

        int energyCount = Rs2Inventory.count(ItemID._4DOSE1ENERGY) * 4 +
                         Rs2Inventory.count(ItemID._3DOSE1ENERGY) * 3 +
                         Rs2Inventory.count(ItemID._2DOSE1ENERGY) * 2 +
                         Rs2Inventory.count(ItemID._1DOSE1ENERGY);

        int fruitCount = Rs2Inventory.count(ItemID.MACRO_TRIFFIDFRUIT);

        Microbot.log(Level.INFO,"Found in inventory: stamina={}, superEnergy={}, energy={}, fruit={}",
                staminaCount, superEnergyCount, energyCount, fruitCount);

        // Return the correct count based on the option
        switch (option) {
            case STAMINA_POTION:
                return staminaCount;
            case SUPER_ENERGY_POTION:
                return superEnergyCount;
            case ENERGY_POTION:
                return energyCount;
            case STRANGE_FRUIT:
                return fruitCount;
            case NONE:
            default:
                return 0;
        }
    }

    private void ensureEnergyDoses(HerbiboarConfig.RunEnergyOption option, int targetDoses) {
        if (option == HerbiboarConfig.RunEnergyOption.NONE || option == HerbiboarConfig.RunEnergyOption.STRANGE_FRUIT) return;

        // Get current doses before doing any bank operations
        int currentDoses = getEnergyDoseCount(option);
        Microbot.log(Level.INFO,"Ensuring energy doses for {}: current={}, target={}", option, currentDoses, targetDoses);

        if (currentDoses >= targetDoses) {
            Microbot.log(Level.INFO,"Already have enough doses ({}), no need to withdraw more", currentDoses);
            return;
        }

        // Only proceed with withdrawal if bank is open
        if (!Rs2Bank.isOpen()) {
            Microbot.log(Level.INFO,"Bank not open, skipping dose withdrawal");
            return;
        }

        switch (option) {
            case STAMINA_POTION:
                withdrawDosesDescending(
                        new int[]{ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA},
                        new int[]{4, 3, 2, 1},
                        targetDoses);
                break;
            case SUPER_ENERGY_POTION:
                withdrawDosesDescending(
                        new int[]{ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY},
                        new int[]{4, 3, 2, 1},
                        targetDoses);
                break;
            case ENERGY_POTION:
                withdrawDosesDescending(
                        new int[]{ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY},
                        new int[]{4, 3, 2, 1},
                        targetDoses);
                break;
            default:
                break;
        }

        // One final check after withdrawal to avoid multiple calls
        currentDoses = getEnergyDoseCount(option);
        Microbot.log(Level.INFO,"After withdrawal check: current={}, target={}", currentDoses, targetDoses);
    }

    public boolean run(HerbiboarConfig config, HerbiboarPlugin herbiboarPlugin) {
        Microbot.enableAutoRunOn = false;
       setState(HerbiboarState.INITIALIZING);

        /*
         * Set camera settings for the script.
         */
        Rs2Camera.setZoom(Rs2Random.randomGaussian(170, 20));
        Rs2Camera.setYaw((Rs2Random.dicePercentage(50)? Rs2Random.randomGaussian(750, 50) : Rs2Random.randomGaussian(1700, 50)));
        Rs2Camera.setPitch(Rs2Random.betweenInclusive(418, 512));
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (BreakHandlerScript.isMicroBreakActive()) return;
                if (BreakHandlerScript.isBreakActive()) return;


                if (config.resetIfStuck() && state != HerbiboarState.RESET && state != HerbiboarState.INITIALIZING
                        && state != HerbiboarState.CHECK_AUTO_RETALIATE && state != HerbiboarState.BANK) {
                    // Keep checking for time of last movement, if more than 1 minute, set state to RESET
                    if (getLastLocation() != null && !getLastLocation().equals(Rs2Player.getWorldLocation())) {
                        setLastMove(Instant.now());
                        setLastLocation(Rs2Player.getWorldLocation());
                    } else if (getLastMove() != null && Instant.now().isAfter(getLastMove().plusSeconds(60))) {
                        Microbot.log(Level.INFO,"Player has not moved for over 1 minute, resetting script state");
                        setState(HerbiboarState.RESET);
                        setLastMove(Instant.now());
                    } else if (getLastMove() == null) {
                        setLastMove(Instant.now());
                        setLastLocation(Rs2Player.getWorldLocation());
                    }
                }

                if (!Rs2Player.isMoving() && !Rs2Player.isInteracting()) {
                    Microbot.log(Level.INFO,"Checking inventory and run energy");
                    dropConfiguredItems(config);
                    manageRunEnergy(config);
                }
                
                if (state != HerbiboarState.INITIALIZING && state != HerbiboarState.CHECK_AUTO_RETALIATE) {
                    if (needsToBank(config)) {
                        Microbot.log(Level.INFO,"Need to bank, switching to BANK state");
                        setState(HerbiboarState.BANK);
                    } else if (hasRequiredInventorySetup(config) && isNearBank() && 
                              (getState() == HerbiboarState.START)) {
                        Microbot.log(Level.INFO,"Returning to island, switching to RETURN_FROM_ISLAND state");
                        setState(HerbiboarState.RETURN_FROM_ISLAND);
                    }
                }
                if ((Rs2Player.isMoving() || Rs2Player.isInteracting()) && 
                    (getState() == HerbiboarState.START || getState() == HerbiboarState.TRAIL ||
                     getState() == HerbiboarState.TUNNEL || getState() == HerbiboarState.HARVEST)) {
                    return;
                }
                
                switch (state) {
                    case RESET:
                        /**
                         * Here we reset the script state in case we get stuck somewhere for 1 minute.
                         * This will walk us back to the starting rock and re-initialize the script.
                         * If currently the warning for reset is not disabled, it will disable it.
                         * After resetting, we check if there is a trail and then decide to go to START or TRAIL state.
                         */
                        Microbot.status = "Resetting...";
                        Microbot.log(Level.INFO,"Resetting...");
                        attackedTunnel = false;
                        setLastMove(Instant.now());
                        setLastLocation(null);
                        WorldPoint resetRock = new WorldPoint(3704, 3810, 0);
                        boolean reached = Rs2Walker.walkTo(resetRock);
                        if (!reached) {
                            Microbot.log(Level.INFO, "Failed to reach reset rock, stopping script");
                            Rs2Player.logout();
                            Microbot.showMessage("Failed to reach reset rock, stopping script");
                            Microbot.stopPlugin(herbiboarPlugin.getClass());
                            return;
                        }
                        if (Microbot.getVarbitValue(VarbitID.FOSSIL_HERBIBOAR_ALREADY_CAUGHT_IGNORE_WARNING) != 1) {
                            Rs2GameObject.interact(resetRock, "Toggle warning");
                            sleepUntil(() -> Microbot.getVarbitValue(VarbitID.FOSSIL_HERBIBOAR_ALREADY_CAUGHT_IGNORE_WARNING) == 1, 5000);
                        }
                        Rs2GameObject.interact(resetRock, "Inspect");
                        Rs2Player.waitForAnimation();
                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting() && !Rs2Player.isMoving(), 5000);
                        if (herbiboarPlugin.getCurrentGroup() == null) {
                            setState(HerbiboarState.START);
                        } else {
                            setState(HerbiboarState.TRAIL);
                        }
                        break;
                    case INITIALIZING:
                        Microbot.status = "Starting...";
                        Microbot.log(Level.INFO,"Initializing...");
                        setState(HerbiboarState.CHECK_AUTO_RETALIATE);
                        break;
                    case CHECK_AUTO_RETALIATE:
                        Microbot.status = "Checking auto retaliate...";
                        Microbot.log(Level.INFO,"Checking auto retaliate...");
                        if (Microbot.getVarbitPlayerValue(172) == 0) {
                            Microbot.status = "Disabling auto retaliate...";
                            Rs2Tab.switchTo(InterfaceTab.COMBAT);
                            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000);
                            Rs2Widget.clickWidget(38862879);
                            sleepUntil(() -> Microbot.getVarbitPlayerValue(172) == 1, 3000);
                        }
                        setState(HerbiboarState.START);
                        break;
                    case START:
                        BreakHandlerScript.setLockState(true);
                        Microbot.status = "Finding start location";
                        Microbot.log(Level.INFO,"Finding start location");
                        if (herbiboarPlugin.getCurrentGroup() == null) {
                            TileObject start = herbiboarPlugin.getStarts().values().stream()
                                .min(Comparator.comparing(s -> Rs2Player.getWorldLocation().distanceTo(s.getWorldLocation())))
                                .orElse(null);
                            if (start != null) {
                                WorldPoint loc = start.getWorldLocation();
                                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), loc);
                                if (localPoint == null || Rs2Player.getWorldLocation().distanceTo(loc) >= config.interactionDistance()) {
                                    Rs2Walker.walkTo(loc);
                                } else {
                                    Rs2Camera.turnTo(localPoint);
                                    Rs2GameObject.interact(start, "Search");
                                    Rs2Player.waitForAnimation();
                                    sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting() && !Rs2Player.isMoving(), 1000);
                                }
                            }
                        } else {
                            setState(HerbiboarState.TRAIL);
                        }
                        break;
                    case TRAIL:
                        Microbot.status = "Following trail";
                        Microbot.log(Level.INFO,"Following trail");
                        if (herbiboarPlugin.getFinishId() > 0) {
                            if (checkForConfusionMessage(herbiboarPlugin)) return;
                            setState(HerbiboarState.TUNNEL);
                            break; 
                        }
                        List<HerbiboarSearchSpot> path = herbiboarPlugin.getCurrentPath();
                        if (!path.isEmpty()) {
                            WorldPoint loc = path.get(path.size() - 1).getLocation();
                            LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), loc);
                            TileObject object = herbiboarPlugin.getTrailObjects().get(loc);
                            if (localPoint == null || Rs2Player.getWorldLocation().distanceTo(loc) >= config.interactionDistance()){
                                Rs2Walker.walkTo(loc);
                            } else {
                                Rs2Camera.turnTo(localPoint);
                                Rs2GameObject.interact(object, "Search");
                                Rs2Player.waitForAnimation();
                                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting() && !Rs2Player.isMoving(), 5000);
                            }
                            if (checkForConfusionMessage(herbiboarPlugin)) return;
                        }
                        break;
                    case TUNNEL:
                        Microbot.status = "Attacking tunnel";
                        Microbot.log(Level.INFO,"Attacking tunnel");
                        if (!attackedTunnel || (Rs2Npc.getNpc("Herbiboar") == null && attackedTunnel)) {
                            int finishId = herbiboarPlugin.getFinishId();
                            if (finishId > 0) {
                                WorldPoint finishLoc = herbiboarPlugin.getEndLocations().get(finishId - 1);
                                LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), finishLoc);
                                TileObject tunnel = herbiboarPlugin.getTunnels().get(finishLoc);
                                if (localPoint == null || Rs2Player.getWorldLocation().distanceTo(finishLoc) >= config.interactionDistance()) {
                                    Rs2Walker.walkTo(finishLoc);
                                } else {
                                    Rs2Camera.turnTo(localPoint);
                                    attackedTunnel = Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(tunnel), "Attack") ? Rs2GameObject.interact(tunnel, "Attack") : Rs2GameObject.interact(tunnel, "Search");
                                    if (attackedTunnel) {
                                        Rs2Player.waitForAnimation();
                                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting() && !Rs2Player.isMoving(), 5000);
                                    }
                                }
                            }
                        } else {
                            Rs2NpcModel herb = Rs2Npc.getNpc("Herbiboar");
                            if (herb != null) setState(HerbiboarState.HARVEST);
                        }
                        break;
                    case HARVEST:
                        Microbot.status = "Harvesting herbiboar";
                        Microbot.log(Level.INFO,"Harvesting herbiboar");
                        Rs2NpcModel herb = Rs2Npc.getNpc("Herbiboar");
                        if (herb != null) {
                            WorldPoint loc = herb.getWorldLocation();
                            if (Rs2Player.getWorldLocation().distanceTo(loc) <= 8) {
                                Rs2Npc.interact(herb, "Harvest");
                                Rs2Player.waitForAnimation();
                                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting() && !Rs2Player.isMoving(), 5000);
                                incrementHerbisCaught();
                                TileObject start = herbiboarPlugin.getStarts().values().stream()
                                    .min(Comparator.comparing(s -> Rs2Player.getWorldLocation().distanceTo(s.getWorldLocation())))
                                    .orElse(null);
                                if (start != null) {
                                    WorldPoint startLoc = start.getWorldLocation();
                                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), startLoc);
                                    if (localPoint == null || Rs2Player.getWorldLocation().distanceTo(startLoc) >= config.interactionDistance()) {
                                        Rs2Walker.walkTo(startLoc);
                                    } else {
                                        Rs2Camera.turnTo(localPoint);
                                        Microbot.log(Level.INFO,"Searching for next herbiboar");
                                        Rs2GameObject.interact(start, "Search");
                                        Rs2Player.waitForAnimation();
                                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting() && !Rs2Player.isMoving(), 3000);
                                    }
                                }
                                attackedTunnel = false;
                                BreakHandlerScript.setLockState(false);
                                dropConfiguredItems(config);
                                if (needsToBank(config)) {
                                    setState(HerbiboarState.BANK);
                                } else {
                                    setState(HerbiboarState.START);
                                }
                            }
                        }
                        break;
                    case BANK:
                        Microbot.status = "Banking items";
                        Microbot.log(Level.INFO,"Banking items");

                        LocalPoint bankLocalPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), BANK_LOCATION);
                        if (bankLocalPoint == null || Rs2Player.getWorldLocation().distanceTo(BANK_LOCATION) >= 5) {
                            Microbot.log(Level.INFO,"Walking to bank");
                            Rs2Walker.walkTo(BANK_LOCATION);
                        } else if (!Rs2Bank.isOpen()) {
                            Microbot.log(Level.INFO,"Opening bank");
                            Rs2Bank.openBank();
                            sleepUntil(Rs2Bank::isOpen, 3000);
                        } else {
                            // check if we have what we need first, usually we don't when first  running
                            if (config.useHerbSack() && !Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN)) {
                                if (Rs2Bank.hasItem(ItemID.SLAYER_HERB_SACK)) {
                                    Rs2Bank.withdrawOne(ItemID.SLAYER_HERB_SACK);
                                } else if (Rs2Bank.hasItem(ItemID.SLAYER_HERB_SACK_OPEN)) {
                                    Rs2Bank.withdrawOne(ItemID.SLAYER_HERB_SACK_OPEN);
                                }
                                Rs2Inventory.waitForInventoryChanges(1000);
                            }
                            if (config.useMagicSecateurs() && !Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS)
                                    && !Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                                if (Rs2Bank.hasItem(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                                    Rs2Bank.withdrawOne(ItemID.FAIRY_ENCHANTED_SECATEURS);
                                    Rs2Inventory.waitForInventoryChanges(1000);
                                    Rs2Bank.wearItem(ItemID.FAIRY_ENCHANTED_SECATEURS);
                                    Rs2Inventory.waitForInventoryChanges(1000);
                                }
                            }
                            /**
                             * Lock items that should not be deposited
                             */
                            Microbot.log(Level.INFO,"Locking configured items");

                            if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) != 1 ||
                            Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) != 0) {
                                Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
                                sleepUntil(()->Rs2Widget.isWidgetVisible(InterfaceID.Bankmain.MENU_CONTAINER), 2000);
                                Rs2Widget.clickWidget(InterfaceID.Bankmain.LOCKS);
                                sleepUntil(()->Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000);
                                if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) != 0) {
                                    Rs2Widget.clickWidget(InterfaceID.BankSideLocks.IGNORELOCKS);
                                    sleepUntil(() -> Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) == 0, 2000);
                                }
                                if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) != 1){
                                    Rs2Widget.clickWidget(InterfaceID.BankSideLocks.EXTRAOPTIONS);
                                    sleepUntil(()->Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) == 1, 2000);
                                }
                                Rs2Widget.clickWidget(InterfaceID.BankSideLocks.DONE);
                                sleepUntil(()->!Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000);
                                Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
                            }

                            ArrayList<Integer> slotsToLock = new ArrayList<>();
                            if (config.useHerbSack() && Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN)) {
                                slotsToLock.add((Rs2Inventory.hasItem(ItemID.SLAYER_HERB_SACK) ? Rs2Inventory.get(ItemID.SLAYER_HERB_SACK).getSlot() :
                                        Rs2Inventory.get(ItemID.SLAYER_HERB_SACK_OPEN).getSlot()));
                            }
                            if (config.useMagicSecateurs() && !Rs2Equipment.isWearing(ItemID.FAIRY_ENCHANTED_SECATEURS)
                                    && Rs2Inventory.contains(ItemID.FAIRY_ENCHANTED_SECATEURS) ) {
                                slotsToLock.add(Rs2Inventory.get(ItemID.FAIRY_ENCHANTED_SECATEURS).getSlot());
                            }

                            sleepUntil(() -> Rs2Bank.lockAllBySlot(slotsToLock.stream().mapToInt(Integer::intValue).toArray()), 2000);

                            /**
                             * Deposit all unlocked items
                             * Empty herb sack if there
                             * Deposit all unlocked items again to get rid of any herbs that were in the sack
                             */
                            Microbot.log(Level.INFO,"Depositing all items except locked slots: "+ slotsToLock);
                            Rs2Widget.clickWidget(InterfaceID.Bankmain.DEPOSITINV);
                            if (config.useHerbSack() && Rs2Inventory.contains(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN)) {
                                Rs2Bank.emptyHerbSack();
                                Rs2Inventory.waitForInventoryChanges(1000);
                                Rs2Widget.clickWidget(InterfaceID.Bankmain.DEPOSITINV);
                            }
                            Rs2Inventory.waitForInventoryChanges(1000);
                            sleep(300);
                            Rs2Bank.toggleAllLocks();

                            /**
                             * Now withdraw energy pots or strange fruit as needed
                             * Targeting 24 doses total of the selected option
                             * Only call this once per bank visit
                             */
                            HerbiboarConfig.RunEnergyOption energyOption = config.runEnergyOption();
                            boolean potionsWithdrawn = false;

                            switch (energyOption) {
                                case STAMINA_POTION:
                                case SUPER_ENERGY_POTION:
                                case ENERGY_POTION:
                                    Microbot.log(Level.INFO,"Withdrawing potions to ensure 24 doses of "+energyOption);
                                    ensureEnergyDoses(energyOption, 24);
                                    potionsWithdrawn = true;
                                    break;
                                case STRANGE_FRUIT:
                                    Microbot.log(Level.INFO,"Withdrawing strange fruit to ensure 6 in inventory");
                                    if (Rs2Inventory.count(ItemID.MACRO_TRIFFIDFRUIT) < 6) {
                                        Rs2Bank.withdrawX(ItemID.MACRO_TRIFFIDFRUIT, 6 - Rs2Inventory.count(ItemID.MACRO_TRIFFIDFRUIT));
                                        sleep(300);
                                        potionsWithdrawn = true;
                                    }
                                    break;
                                case NONE:
                                default:
                                    Microbot.log(Level.INFO,"No energy restoration selected");
                                    break;
                            }

                            Microbot.log(Level.INFO,"Fished banking, status: "+ (potionsWithdrawn ? "Potions withdrawn" : "No potions needed")+" | energy option: "+energyOption);
                            if (potionsWithdrawn || energyOption == HerbiboarConfig.RunEnergyOption.NONE) {
                                sleep(300); // Give time for inventory to update
                                Rs2Bank.closeBank();
                                sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
                                setState(HerbiboarState.RETURN_FROM_ISLAND);
                            }
                        }
                        break;
                    case RETURN_FROM_ISLAND:
                        Microbot.status = "Returning to island";
                        Microbot.log(Level.INFO,"Returning to island");
                        LocalPoint returnLocalPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), RETURN_LOCATION);
                        if (returnLocalPoint == null) {
                            Rs2Walker.walkTo(RETURN_LOCATION);
                        } else if (Rs2Player.getWorldLocation().distanceTo(RETURN_LOCATION) >= 5) {
                            Rs2Walker.walkTo(RETURN_LOCATION);
                        } else {
                            setState(HerbiboarState.START);
                        }
                        break;
                }
            } catch (Exception ex) {
                System.err.println("AutoHerbiboar error: " + ex.getMessage());
                log.error("e: ", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Check for the presence of the confusion or "start again" messages in the chatbox.
     *
     * @return true if the message is found, false otherwise
     */
    private boolean checkForConfusionMessage(HerbiboarPlugin plugin) {
        for (String msg : plugin.getLastMessages()) {
            if (msg.contains("successfully confused you with its tracks") || msg.contains("need to start again")) {
                handleConfusionMessage();
                return true;
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        Microbot.status = "IDLE";
        resetHerbisCaught();
        BreakHandlerScript.setLockState(false);
        super.shutdown();
    }
}

