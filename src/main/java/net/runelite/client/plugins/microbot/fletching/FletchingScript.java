package net.runelite.client.plugins.microbot.fletching;


import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingItem;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingMaterial;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Getter
class ProgressiveFletchingModel {
    @Setter
    private FletchingItem fletchingItem;
    @Setter
    private FletchingMaterial fletchingMaterial;
}

public class FletchingScript extends Script {

    // The fletching interface widget group ID
    private static final int FLETCHING_WIDGET_GROUP_ID = 17694736;

    private static final String FLETCHING_KNIFE = "fletching knife";
    private static final String KNIFE = "knife";
    private static final String BOW_STRING_SPOOL = "bow string spool";
    private static final String BOW_STRING = "bow string";
    // Total attempts to open the stringing menu with the spool before switching to bow strings.
    private static final int BOW_STRING_SPOOL_MENU_ATTEMPTS = 3;

    ProgressiveFletchingModel model = new ProgressiveFletchingModel();

    String primaryItemToFletch = "";
    String secondaryItemToFletch = "";

    FletchingMode fletchingMode;

    private boolean bowStringSpoolDisabled;
    private boolean bowStringSpoolRefillFailed;
    private int bowStringSpoolMenuFailures;
    private String lastStringingItem = "";

    public void run(FletchingConfig config) {
        fletchingMode = config.fletchingMode();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFletchingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn())
                    return;
                if (!super.run()) return;

                if ((fletchingMode == FletchingMode.PROGRESSIVE || fletchingMode == FletchingMode.PROGRESSIVE_STRUNG)
                        && model.getFletchingItem() == null) {
                    calculateItemToFletch();
                }


                if (!configChecks(config)) return;

                if (Rs2AntibanSettings.actionCooldownActive)
                    return;

//                if (config.Afk() && Random.random(1, 100) == 2)
//                    sleep(1000, 60000);

                boolean hasRequirementsToFletch;
                boolean hasRequirementsToBank;
                primaryItemToFletch = usesKnife() ? getPreferredKnife() : getStringingItem(config);

                if (fletchingMode == FletchingMode.PROGRESSIVE) {
                    secondaryItemToFletch = model.getFletchingMaterial().getLogItemName();
                    hasRequirementsToFletch = hasPrimaryItemInInventory()
                            && Rs2Inventory.hasItemAmount(secondaryItemToFletch, model.getFletchingItem().getAmountRequired());
                    hasRequirementsToBank = !hasPrimaryItemInInventory()
                            || !Rs2Inventory.hasItemAmount(secondaryItemToFletch, model.getFletchingItem().getAmountRequired());
                } else if (fletchingMode == FletchingMode.PROGRESSIVE_STRUNG) {
                    secondaryItemToFletch = model.getFletchingMaterial().getName() + " "
                            + model.getFletchingItem().getContainsInventoryName() + " (u)";
                    hasRequirementsToFletch = hasPrimaryItemInInventory() && Rs2Inventory.hasItem(secondaryItemToFletch);
                    hasRequirementsToBank = !hasPrimaryItemInInventory() || !Rs2Inventory.hasItem(secondaryItemToFletch);
                } else {
                    secondaryItemToFletch = fletchingMode == FletchingMode.STRUNG
                            ? config.fletchingMaterial().getName() + " " + config.fletchingItem().getContainsInventoryName() + " (u)"
                            : config.fletchingMaterial().getLogItemName();
                    hasRequirementsToFletch = hasPrimaryItemInInventory()
                            && Rs2Inventory.hasItemAmount(secondaryItemToFletch, config.fletchingItem().getAmountRequired());
                    hasRequirementsToBank = !hasPrimaryItemInInventory()
                            || !Rs2Inventory.hasItemAmount(secondaryItemToFletch, config.fletchingItem().getAmountRequired());
                }

                if (hasRequirementsToFletch) {
                    fletch(config);
                }
                if (hasRequirementsToBank) {
                    bankItems(config);
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }

    private void bankItems(FletchingConfig config) {
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) {
                return; // Bank didn't open, retry next iteration
            }
        }

        // Refill the spool from banked bow strings when it has run dry.
        if (shouldRefillBowStringSpool(config)) {
            if (refillBowStringSpool()) {
                Microbot.status = "Stringing with bow string spool";
            } else {
                bowStringSpoolRefillFailed = true;
                Microbot.status = "Could not refill the bow string spool, using bow strings for the rest of this run";
                Microbot.log(Microbot.status);
            }
        }

        // Re-evaluate the stringing item now that the bank is open (the bank snapshot is not available before).
        primaryItemToFletch = usesKnife() ? getPreferredKnife() : getStringingItem(config);

        // A spool trip leaves the spool in the inventory. Deposit only the strung bows with one
        // Deposit-All click, then withdraw the unstrung bows in one click below.
        boolean spoolInInventory = isBowStringSpool(primaryItemToFletch) && Rs2Inventory.hasItem(BOW_STRING_SPOOL);
        String strungBowName = getStrungBowName();
        if (spoolInInventory) {
            if (Rs2Bank.depositAll(strungBowName, true)) {
                sleepUntil(() -> !Rs2Inventory.hasItem(strungBowName, true), 5000);
            }
        }

        // Deposit items based on the fletching mode
        switch (fletchingMode) {
            case STRUNG:
                if (!spoolInInventory) {
                    Rs2Bank.depositAll();
                }
                break;
            case PROGRESSIVE:
                Rs2Bank.depositAll(model.getFletchingItem().getContainsInventoryName());
                calculateItemToFletch();
                secondaryItemToFletch = model.getFletchingMaterial().getLogItemName();
                break;
            case PROGRESSIVE_STRUNG:
                if (!spoolInInventory) {
                    Rs2Bank.depositAll();
                }
                calculateItemToFletch();
                secondaryItemToFletch = model.getFletchingMaterial().getName() + " "
                        + model.getFletchingItem().getContainsInventoryName() + " (u)";
                break;
            default:
                Rs2Bank.depositAll(config.fletchingItem().getContainsInventoryName());
                Rs2Inventory.waitForInventoryChanges(5000);
                break;
        }

        // Check if the primary item is available
        boolean hasPrimaryInBank = hasPrimaryItemInBank();
        boolean hasPrimaryInInventory = hasPrimaryItemInInventory();
        if (!hasPrimaryInBank && !hasPrimaryInInventory) {
            Rs2Bank.closeBank();
            boolean spoolUnusable = bowStringSpoolDisabled && config.useBowStringSpool() && !usesKnife();
            boolean spoolEmpty = config.useBowStringSpool()
                    && !usesKnife()
                    && getBowStringSpoolCharges() <= 0
                    && (Rs2Bank.hasItem(BOW_STRING_SPOOL) || Rs2Inventory.hasItem(BOW_STRING_SPOOL));
            if (spoolUnusable) {
                Microbot.status = "[Shutting down] - Reason: Your bow string spool could not be used (the stringing menu did not open) and there are no bow strings in the bank. Bank some bow strings, or start the script again to retry the spool.";
            } else if (spoolEmpty) {
                Microbot.status = "[Shutting down] - Reason: Your bow string spool is empty and there are no bow strings in the bank to refill it. Bank more bow strings (the script refills the spool for you), then start again.";
            } else {
                Microbot.status = "[Shutting down] - Reason: " + primaryItemToFletch + " not found in the bank.";
            }
            Microbot.showMessage(Microbot.status);
            shutdown();
            return;
        }

        // Ensure the inventory isn't full without the primary item
        if (!hasPrimaryInInventory) {
            Rs2Bank.depositAll();
        }

        // Withdraw the primary item if not already in the inventory
        if (!hasPrimaryInInventory) {
            if (isBowStringSpool(primaryItemToFletch)) {
                Rs2Bank.withdrawItem(primaryItemToFletch);
            } else {
                boolean withdrawn = Rs2Bank.withdrawX(primaryItemToFletch, fletchingMode.getAmount(), true);
                if (!withdrawn && !usesKnife()
                        && !Rs2Bank.hasBankItem(primaryItemToFletch, fletchingMode.getAmount(), true)) {
                    // Fewer bow strings than a batch: without this the script banks forever.
                    Rs2Bank.closeBank();
                    int bowStringsLeft = Rs2Bank.count(primaryItemToFletch);
                    Microbot.status = "[Shutting down] - Reason: Only " + bowStringsLeft
                            + (bowStringsLeft == 1 ? " bow string" : " bow strings")
                            + " left in the bank (" + fletchingMode.getAmount()
                            + " needed per trip). Bank more bow strings, then start again.";
                    Microbot.showMessage(Microbot.status);
                    shutdown();
                    return;
                }
            }
        }

        // Check if the secondary item is available
        if (!Rs2Bank.hasItem(secondaryItemToFletch)) {
            if (fletchingMode == FletchingMode.UNSTRUNG_STRUNG && Rs2Bank.hasBankItem("bow string")) {
                Rs2Bank.depositAll();
                fletchingMode = FletchingMode.STRUNG;
                return;
            }
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + secondaryItemToFletch + " not found in the bank.";
            Microbot.showMessage(Microbot.status);
            shutdown();
            return;
        }

        // Withdraw the secondary item if not already in the inventory
        if (!Rs2Inventory.hasItem(secondaryItemToFletch)) {
            if (isBowStringSpool(primaryItemToFletch)) {
                // Withdraw-All fills the free slots in one click (27 with the spool still in the inventory).
                Rs2Bank.withdrawAll(secondaryItemToFletch);
            } else if (fletchingMode == FletchingMode.STRUNG) {
                Rs2Bank.withdrawDeficit(secondaryItemToFletch, fletchingMode.getAmount());
            } else {
                Rs2Bank.withdrawAll(secondaryItemToFletch);
            }
        }
        if (Rs2AntibanSettings.naturalMouse) {
            // Testing if completing the mouse movement before the final item check improves the overall flow.
            // This should allow time for the inventory to update while the mouse is moving.
            // Enhances the bot's behavior to appear more natural and less automated.
            Widget closeButton = Rs2Widget.getWidget(786434).getChild(11);
            Point closePoint = Rs2UiHelper.getClickingPoint(closeButton != null ? closeButton.getBounds() : null, true);
            Rs2Random.waitEx(200, 100);
            Microbot.naturalMouse.moveTo(closePoint.getX(), closePoint.getY());
        }

        // Final check to ensure both items are in the inventory
        boolean hasPrimaryFinal = hasPrimaryItemInInventory();
        if (!hasPrimaryFinal || !Rs2Inventory.hasItem(secondaryItemToFletch)) {
            Microbot.log("waiting for inventory changes.");
            Rs2Inventory.waitForInventoryChanges(5000);
        }

        Rs2Random.waitEx(200, 100);
        Rs2Bank.closeBank();
    }


    private void fletch(FletchingConfig config) {
        String itemToUse = usesKnife() ? getKnifeInInventory() : primaryItemToFletch;
        boolean usingBowStringSpool = isBowStringSpool(itemToUse);
        Rs2Inventory.combineClosest(itemToUse, secondaryItemToFletch);
        boolean stringingMenuOpen = sleepUntil(() -> Rs2Widget.getWidget(FLETCHING_WIDGET_GROUP_ID) != null, 5000);
        if (!stringingMenuOpen && usingBowStringSpool) {
            if (++bowStringSpoolMenuFailures < BOW_STRING_SPOOL_MENU_ATTEMPTS) {
                Microbot.log("Bow string spool did not open the stringing menu, retrying");
                return;
            }
            bowStringSpoolDisabled = true;
            lastStringingItem = fletchingMode.getItemName();
            Microbot.status = "Bow string spool could not be used, switching to bow strings";
            Microbot.log("Bow string spool did not open the stringing menu, switching to bow strings");
            return;
        }
        bowStringSpoolMenuFailures = 0;
        char option;
        if (fletchingMode == FletchingMode.PROGRESSIVE || fletchingMode == FletchingMode.PROGRESSIVE_STRUNG) {

            option = model.getFletchingItem().getOption(model.getFletchingMaterial(), fletchingMode);
            Rs2Keyboard.keyPress(option);
        } else {
            option = config.fletchingItem().getOption(config.fletchingMaterial(), fletchingMode);
            Rs2Keyboard.keyPress(option);
        }

        Rs2Bank.preHover();

        // A spool trip strings 27 bows instead of the usual 14, so give it more time to finish.
        // Stop waiting early if the spool runs dry before the batch is finished.
        sleepUntil(() -> !Rs2Inventory.hasItem(secondaryItemToFletch)
                        || (usingBowStringSpool && getBowStringSpoolCharges() == 0),
                usingBowStringSpool ? 90000 : 60000);
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
    }

    private boolean configChecks(FletchingConfig config) {
        if (config.fletchingMaterial() == FletchingMaterial.REDWOOD && config.fletchingItem() != FletchingItem.SHIELD) {
            Microbot.getNotifier().notify("[Wrong Configuration] You can only make shields with redwood logs.");
            shutdown();
            return false;
        }
        return true;
    }

    private String getPreferredKnife() {
        if (Rs2Inventory.hasItem(FLETCHING_KNIFE) || Rs2Bank.hasItem(FLETCHING_KNIFE)) {
            return FLETCHING_KNIFE;
        }
        return KNIFE;
    }

    private boolean hasAnyKnife() {
        return Rs2Inventory.hasItem(FLETCHING_KNIFE) || Rs2Inventory.hasItem(KNIFE);
    }

    private String getKnifeInInventory() {
        if (Rs2Inventory.hasItem(FLETCHING_KNIFE)) {
            return FLETCHING_KNIFE;
        }
        return KNIFE;
    }

    private boolean bankHasAnyKnife() {
        return Rs2Bank.hasItem(FLETCHING_KNIFE) || Rs2Bank.hasItem(KNIFE);
    }

    private boolean usesKnife() {
        return fletchingMode == FletchingMode.UNSTRUNG
                || fletchingMode == FletchingMode.UNSTRUNG_STRUNG
                || fletchingMode == FletchingMode.PROGRESSIVE;
    }

    private boolean hasPrimaryItemInBank() {
        // Exact match keeps "bow string" from matching the "bow string spool".
        return usesKnife() ? bankHasAnyKnife() : Rs2Bank.hasBankItem(primaryItemToFletch, true);
    }

    private boolean hasPrimaryItemInInventory() {
        return usesKnife() ? hasAnyKnife() : Rs2Inventory.hasItem(primaryItemToFletch, true);
    }

    private String getStringingItem(FletchingConfig config) {
        String stringingItem = isUsingBowStringSpool(config) ? BOW_STRING_SPOOL : fletchingMode.getItemName();
        if (!stringingItem.equals(lastStringingItem)) {
            if (BOW_STRING_SPOOL.equals(stringingItem)) {
                Microbot.log("Using bow string spool (" + getBowStringSpoolCharges() + " strings left)");
            } else if (!lastStringingItem.isEmpty()) {
                Microbot.log(shouldRefillBowStringSpool(config)
                        ? "Bow string spool is empty, refilling from the bank"
                        : "Bow string spool is empty or missing, using bow strings");
            }
            lastStringingItem = stringingItem;
        }
        return stringingItem;
    }

    private boolean isUsingBowStringSpool(FletchingConfig config) {
        if (bowStringSpoolDisabled || !config.useBowStringSpool()) {
            return false;
        }
        if (fletchingMode != FletchingMode.STRUNG && fletchingMode != FletchingMode.PROGRESSIVE_STRUNG) {
            return false;
        }
        return getBowStringSpoolCharges() > 0
                && (Rs2Inventory.hasItem(BOW_STRING_SPOOL) || Rs2Bank.hasItem(BOW_STRING_SPOOL));
    }

    private boolean isBowStringSpool(String itemName) {
        return BOW_STRING_SPOOL.equals(itemName);
    }

    private String getStrungBowName() {
        // The strung bow is the unstrung name without the " (u)" suffix.
        return secondaryItemToFletch.endsWith(" (u)")
                ? secondaryItemToFletch.substring(0, secondaryItemToFletch.length() - 4)
                : secondaryItemToFletch;
    }

    private int getBowStringSpoolCharges() {
        return Microbot.getVarbitValue(VarbitID.BOWSTRING_SPOOL_CHARGES);
    }

    private boolean shouldRefillBowStringSpool(FletchingConfig config) {
        if (!config.useBowStringSpool() || bowStringSpoolDisabled || bowStringSpoolRefillFailed) {
            return false;
        }
        if (fletchingMode != FletchingMode.STRUNG && fletchingMode != FletchingMode.PROGRESSIVE_STRUNG) {
            return false;
        }
        if (getBowStringSpoolCharges() > 0) {
            return false;
        }
        if (!Rs2Inventory.hasItem(BOW_STRING_SPOOL) && !Rs2Bank.hasItem(BOW_STRING_SPOOL)) {
            return false;
        }
        return Rs2Bank.hasBankItem(BOW_STRING, 1, true);
    }

    private boolean refillBowStringSpool() {
        Microbot.status = "Refilling bow string spool";
        if (!Rs2Inventory.hasItem(BOW_STRING_SPOOL)) {
            // The spool is banked: clear the inventory, then pull it out.
            Rs2Bank.depositAll();
            Rs2Bank.withdrawItem(BOW_STRING_SPOOL);
            if (!sleepUntil(() -> Rs2Inventory.hasItem(BOW_STRING_SPOOL), 5000)) {
                return false;
            }
        } else {
            // Keep the spool; clear the strung bows and leftover unstrung bows to make room
            // for the noted bow strings.
            Rs2Bank.depositAll(getStrungBowName(), true);
            Rs2Bank.depositAll(secondaryItemToFletch, true);
            if (!sleepUntil(() -> !Rs2Inventory.isFull(), 5000)) {
                return false;
            }
        }
        boolean withdrawNoted = Rs2Bank.hasWithdrawAsNote();
        boolean refilled;
        try {
            if (!Rs2Bank.setWithdrawAsNote()) {
                return false;
            }
            // Withdraw the whole stack as notes, then Fill takes exactly the spool's remaining
            // capacity and any leftovers are deposited back.
            Rs2Bank.withdrawAll(BOW_STRING, true);
            if (!sleepUntil(() -> Rs2Inventory.hasItem(BOW_STRING, true), 5000)) {
                return false;
            }
            Rs2Inventory.interact(BOW_STRING_SPOOL, "Fill");
            refilled = sleepUntil(() -> getBowStringSpoolCharges() > 0, 5000);
            if (!refilled) {
                // Fallback for interfaces where Fill does not accept the noted stack.
                Rs2Inventory.combineClosest(BOW_STRING, BOW_STRING_SPOOL);
                refilled = sleepUntil(() -> getBowStringSpoolCharges() > 0, 5000);
            }
        } finally {
            // Always return any leftover strings: a failed fill must not leave noted strings behind.
            Rs2Bank.depositAll(BOW_STRING, true);
            sleepUntil(() -> !Rs2Inventory.hasItem(BOW_STRING, true), 5000);
            Rs2Bank.setWithdrawAs(withdrawNoted);
        }
        return refilled;
    }

    public void calculateItemToFletch() {
        int level = Microbot.getClient().getRealSkillLevel(Skill.FLETCHING);
        FletchingItem item = null;
        FletchingMaterial material = null;



        if (fletchingMode == FletchingMode.PROGRESSIVE_STRUNG && level < 5) {
            Microbot.showMessage("Can't String Bows Below Level 5");
            shutdown();
            return;
        }
        if (level < 5) {
            item = FletchingItem.ARROW_SHAFT;
            material = FletchingMaterial.LOG;
        } else if (level < 10) {
            item = FletchingItem.SHORT;
            material = (fletchingMode == FletchingMode.PROGRESSIVE) ? FletchingMaterial.LOG : FletchingMaterial.WOOD;
        } else if (level < 20) {
            item = FletchingItem.LONG;
            material = (fletchingMode == FletchingMode.PROGRESSIVE) ? FletchingMaterial.LOG : FletchingMaterial.WOOD;
        } else if (level < 25) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.OAK;
        } else if (level < 35) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.OAK;
        } else if (level < 40) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.WILLOW;
        } else if (level < 50) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.WILLOW;
        } else if (level < 55) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.MAPLE;
        } else if (level < 65) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.MAPLE;
        } else if (level < 70) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.YEW;
        } else if (level < 80) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.YEW;
        } else if (level < 85) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.MAGIC;
        } else {
            item = FletchingItem.LONG;
            material = FletchingMaterial.MAGIC;
        }

        model.setFletchingItem(item);
        model.setFletchingMaterial(material);
    }


    @Override
    public void shutdown() {

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
