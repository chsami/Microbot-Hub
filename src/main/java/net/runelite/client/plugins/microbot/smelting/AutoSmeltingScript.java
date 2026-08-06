package net.runelite.client.plugins.microbot.smelting;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.smelting.enums.Ores;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.Map;
import java.util.concurrent.TimeUnit;


public class AutoSmeltingScript extends Script {

    static boolean coalBagEmpty;
    static final int coalBag = 12019;
    boolean hasBeenFilled = false;

    public boolean run(AutoSmeltingConfig config) {
        initialPlayerLocation = null;
        Rs2Walker.disableTeleports = true;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applySmithingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                int currentSmithing = Rs2Player.getRealSkillLevel(Skill.SMITHING);
                // Skill reads return 0 transiently during login / region load.
                if (currentSmithing <= 0) return;
                int requiredSmithing = config.SELECTED_BAR_TYPE().getRequiredSmithingLevel();
                if (currentSmithing < requiredSmithing) {
                    Microbot.log("Smithing " + currentSmithing + " is below the " + requiredSmithing
                            + " required for " + config.SELECTED_BAR_TYPE() + ". Shutting down.");
                    super.shutdown();
                    return;
                }

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating(6500)) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                // walk to bank until it's open then deposit everything and withdraw materials
                if (!inventoryHasMaterialsForOneBar(config)) {
                    if (!Rs2Bank.openBank()) {
                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.walkToBankAndUseBank();
                            return;
                        }
                    }
                    Rs2Player.waitForWalking();
                    sleep(600,1200);
                    if (!Rs2Player.isInMemberWorld()) {
                        Rs2Bank.depositAll();
                    } else if (Rs2Player.isMember()) Rs2Bank.depositAllExcept(coalBag);
                    // depositAll fires a menu click and returns before the next game tick
                    // processes it. Wait for slots to free so the upcoming withdrawX isn't
                    // blocked by Rs2Bank's "inventory full of the wrong item" guard.
                    sleepUntil(() -> !Rs2Inventory.isFull(), 3000);
                    if (config.SELECTED_BAR_TYPE().getId() == ItemID.IRON_BAR && Rs2Bank.hasItem(ItemID.RING_OF_FORGING) && !Rs2Equipment.isWearing(ItemID.RING_OF_FORGING)) {
                        Rs2Bank.withdrawAndEquip(ItemID.RING_OF_FORGING);
                        return;
                    }
                    for (int i : new int[]{ItemID.GOLD_BAR}) {
                        int selectedBar = config.SELECTED_BAR_TYPE().getId();
                        if (selectedBar == i && Rs2Bank.hasItem(ItemID.GAUNTLETS_OF_GOLDSMITHING) && !Rs2Equipment.isWearing(ItemID.GAUNTLETS_OF_GOLDSMITHING)) {
                            Rs2Bank.withdrawAndEquip(ItemID.GAUNTLETS_OF_GOLDSMITHING);
                            return;
                        }
                        if (selectedBar != i && (Rs2Bank.hasItem(ItemID.SMITHING_UNIFORM_GLOVES) || Rs2Bank.hasItem(ItemID.SMITHING_UNIFORM_GLOVES_ICE)) && !Rs2Equipment.isWearing(ItemID.SMITHING_UNIFORM_GLOVES_ICE) && !Rs2Equipment.isWearing(ItemID.SMITHING_UNIFORM_GLOVES)) {
                            if (Rs2Bank.hasItem(ItemID.SMITHING_UNIFORM_GLOVES_ICE)) {
                                Rs2Bank.withdrawAndEquip(ItemID.SMITHING_UNIFORM_GLOVES_ICE);
                                return;
                            }
                            if (Rs2Bank.hasItem(ItemID.SMITHING_UNIFORM_GLOVES)) {
                                Rs2Bank.withdrawAndEquip(ItemID.SMITHING_UNIFORM_GLOVES);
                                return;
                            }
                        }
                    }
                    int selectedBar = config.SELECTED_BAR_TYPE().getId();
                    boolean needsCoalBag = false;

                    for (int i : new int[]{ItemID.STEEL_BAR, ItemID.MITHRIL_BAR, ItemID.ADAMANTITE_BAR, ItemID.RUNITE_BAR}) {
                        if (selectedBar == i && Rs2Player.isInMemberWorld()) {
                            needsCoalBag = true;
                            break;
                        }
                    }

                    if (needsCoalBag && Rs2Bank.hasItem(coalBag) && !Rs2Inventory.hasItem(coalBag) && Rs2Player.isInMemberWorld()) {
                        Rs2Bank.withdrawItem(coalBag);
                        return;
                    }

                    if (!needsCoalBag && Rs2Inventory.hasItem(coalBag)) {
                        Rs2Bank.depositItems(coalBag);
                        return;
                    }
                    if ((coalBagEmpty || !hasBeenFilled) && Rs2Inventory.hasItem(coalBag) && Rs2Player.isInMemberWorld()) {
                        handleCoalBag();
                    }
                    withdrawRightAmountOfMaterials(config);
                    return;
                }
                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen(), 1500);
                    return;
                }

                // Run the lookup on the client thread so each getName() resolves in-place
                // instead of round-tripping through ClientThread.invoke per scene object.
                Rs2TileObjectModel furnace = Microbot.getRs2TileObjectCache().query()
                        .withNameContains("furnace")
                        .nearestOnClientThread(initialPlayerLocation, 20);

                if (furnace == null) {
                    if (initialPlayerLocation.distanceTo(Rs2Player.getWorldLocation()) > 4) {
                        Rs2Walker.walkTo(initialPlayerLocation, 4);
                    } else {
                        Microbot.status = "AutoSmelting: no furnace within 20 tiles of start — stand near a furnace and restart";
                    }
                    return;
                }

                furnace.click("smelt");
                sleepUntil(Rs2Player::isMoving, 1000);
                sleepUntil(() -> !Rs2Player.isMoving(), 6000);
                Rs2Widget.sleepUntilHasWidgetText("What would you like to smelt?", 270, 5, false, 4000);
                Rs2Widget.clickWidget(config.SELECTED_BAR_TYPE().getName());
                Rs2Widget.sleepUntilHasNotWidgetText("What would you like to smelt?", 270, 5, false, 4000);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();

            } catch (Exception ex) {
                Microbot.logStackTrace("AutoSmeltingScript", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private boolean inventoryHasMaterialsForOneBar(AutoSmeltingConfig config) {
        if (!coalBagEmpty && hasBeenFilled) {
            Rs2Inventory.interact(coalBag, "Empty");
            Rs2Inventory.waitForInventoryChanges(3000);
            return true;
        }
        for (Map.Entry<Ores, Integer> requiredMaterials : config.SELECTED_BAR_TYPE().getRequiredMaterials().entrySet()) {
            Integer amount = requiredMaterials.getValue();
            String name = requiredMaterials.getKey().toString();
            if (!Rs2Inventory.hasItemAmount(name, amount, false, true)) {
                return false;
            }
        }
        return true;
    }
    private void withdrawRightAmountOfMaterials(AutoSmeltingConfig config) {
        for (Map.Entry<Ores, Integer> requiredMaterials : config.SELECTED_BAR_TYPE().getRequiredMaterials().entrySet()) {
            Integer amountForOne = requiredMaterials.getValue();
            String name = requiredMaterials.getKey().toString();
            int totalAmount = Rs2Inventory.hasItem(coalBag)
                    ? config.SELECTED_BAR_TYPE().getWithdrawalsWithCoalBag(Rs2Inventory.capacity()).get(requiredMaterials.getKey())
                    : config.SELECTED_BAR_TYPE().maxBarsForFullInventory() * amountForOne;
            if (!Rs2Bank.hasBankItem(name, totalAmount, true)) {
                Microbot.log("Bank lacks " + totalAmount + " " + name + ". Shutting down.");
                shutdown();
                return;
            }
            Rs2Bank.withdrawX(name, totalAmount, true);
            sleepUntil(() -> Rs2Inventory.hasItemAmount(name, totalAmount, false, true), 3500);
            // Withdraw missed the verify window — let the next tick retry instead of bailing.
            if (!Rs2Inventory.hasItemAmount(name, totalAmount, false, true)) {
                Microbot.log("Withdraw of " + totalAmount + " " + name + " didn't settle in 3.5s; retrying next tick.");
                return;
            }
        }
    }

    private void handleCoalBag() {
        boolean fullCoalBag = Rs2Inventory.interact(coalBag, "Fill");
        if (!fullCoalBag)
            return;
        sleep(300, 1200);
    }

}
