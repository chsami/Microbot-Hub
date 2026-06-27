package net.runelite.client.plugins.microbot.plankrunner;

import com.google.inject.Inject;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.plankrunner.enums.PlankRunnerState;
import net.runelite.client.plugins.microbot.plankrunner.enums.SawmillLocation;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlankRunnerScript extends Script {

    private static final int AUBURNVALE_BANK_BOOTH_ID = 57330;
    private static final long AUBURNVALE_BANK_BOOTH_CLICK_COOLDOWN_MS = 2500;

    public static PlankRunnerState state;
    private final PlankRunnerPlugin plugin;
    private long lastAuburnvaleBankBoothClickAt;

    @Inject
    public PlankRunnerScript(PlankRunnerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
        Rs2Walker.disableTeleports = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (hasStateChanged()) {
                    state = updateState();
                }

                if (state == null) {
                    Microbot.showMessage("Unable to evaluate state");
                    shutdown();
                    return;
                }

                switch (state) {
                    case BANKING:
                        boolean bankOpened = openBank();
                        if (!bankOpened || !Rs2Bank.isOpen()) return;

                        if (Rs2Inventory.contains(plugin.getPlank().getPlankItemId())) {
                            Rs2Bank.depositAll(plugin.getPlank().getPlankItemId());
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }

                        if (Rs2Inventory.emptySlotCount() < 26) {
                            Rs2Bank.depositAll();
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }

                        if (!Rs2Inventory.contains(ItemID.COINS)) {
                            Rs2Bank.withdrawAll(ItemID.COINS);
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }

                        boolean drankStaminaPotion = false;
                        while (plugin.isUseEnergyRestorePotions() && Rs2Player.getRunEnergy() <= plugin.getDrinkAtPercent()) {
                            boolean hasStaminaPotion = Rs2Bank.hasItem(Rs2Potion.getStaminaPotion());
                            boolean hasEnergyRestorePotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());

                            if (!hasStaminaPotion && !hasEnergyRestorePotion) {
                                Microbot.showMessage("Unable to find Stamina Potion OR Energy Restore Potions");
                                shutdown();
                                return;
                            }

                            boolean shouldUseEnergyRestorePotion = hasEnergyRestorePotion &&
                                    (Rs2Player.hasStaminaBuffActive() || !hasStaminaPotion || drankStaminaPotion);

                            if (shouldUseEnergyRestorePotion) {
                                Rs2ItemModel energyRestoreItem = Rs2Bank.bankItems().stream()
                                        .filter(rs2Item -> Rs2Potion.getRestoreEnergyPotionsVariants().stream()
                                                .anyMatch(variant -> rs2Item.getName().toLowerCase().contains(variant.toLowerCase())))
                                        .max(Comparator.comparingInt(rs2Item -> getDoseFromName(rs2Item.getName())))
                                        .orElse(null);

                                if (energyRestoreItem == null) break;

                                withdrawAndDrink(energyRestoreItem.getName(), true);
                            } else if (hasStaminaPotion && !drankStaminaPotion) {
                                Rs2ItemModel staminaPotionItem = Rs2Bank.bankItems().stream()
                                        .filter(rs2Item -> rs2Item.getName().toLowerCase().contains(Rs2Potion.getStaminaPotion().toLowerCase()))
                                        .max(Comparator.comparingInt(rs2Item -> getDoseFromName(rs2Item.getName())))
                                        .orElse(null);

                                if (staminaPotionItem == null) break;

                                withdrawAndDrink(staminaPotionItem.getName(), false);
                                drankStaminaPotion = true;
                            } else {
                                break;
                            }
                        }

                        int logsToWithdraw = Rs2Inventory.emptySlotCount();
                        if (!Rs2Bank.hasBankItem(plugin.getPlank().getLogItemId(), logsToWithdraw)) {
                            Microbot.showMessage("Not enough logs for a full run!");
                            shutdown();
                            return;
                        }
                        Rs2Bank.withdrawX(plugin.getPlank().getLogItemId(), logsToWithdraw);

                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                        break;
                    case RUNNING_TO_SAWMILL:
                        Set<Integer> sawmillNpcs = Set.of(NpcID.POH_SAWMILL_OPP, NpcID.AUBURN_SAWMILL_OPERATOR);
                        var sawmillOperator = Microbot.getRs2NpcCache().query()
                                .where(n -> sawmillNpcs.contains(n.getId()))
                                .nearest();

                        if (sawmillOperator != null && Rs2Camera.isTileOnScreen(sawmillOperator.getLocalLocation())) {
                            Rs2Walker.setTarget(null);
                            sleepUntil(() -> !Rs2Player.isMoving());
                            sawmillOperator.click("Buy-plank");
                        } else {
                            Microbot.status = "Running to Sawmill";
                            if (!Rs2Player.isMoving()) {
                                Rs2Walker.walkFastCanvas(plugin.getSawmillLocation().getWorldPoint());
                            }
                            return;
                        }

                        Microbot.status = "Buying Planks";
                        Rs2Dialogue.sleepUntilHasCombinationDialogue();
                        Rs2Dialogue.clickCombinationOption(plugin.getPlank().getDialogueOption());
                        sleepUntil(() -> Rs2Inventory.hasItem(plugin.getPlank().getPlankItemId()));
                        plugin.calculateProfit();
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private boolean hasStateChanged() {
        if (state == null) return true;
        if (hasRequiredItems()) return true;
        return !hasRequiredItems();
    }

    private PlankRunnerState updateState() {
        if (hasRequiredItems()) return PlankRunnerState.RUNNING_TO_SAWMILL;
        if (!hasRequiredItems()) return PlankRunnerState.BANKING;
        return null;
    }

    private boolean openBank() {
        if (Rs2Bank.isOpen()) {
            lastAuburnvaleBankBoothClickAt = 0;
            return true;
        }

        if (plugin.getSawmillLocation() == SawmillLocation.AUBURNVALE) {
            return openAuburnvaleBank();
        }

        return Rs2Bank.isNearBank(plugin.getSawmillLocation().getBankLocation(), 15)
                ? Rs2Bank.openBank()
                : Rs2Bank.walkToBankAndUseBank(plugin.getSawmillLocation().getBankLocation());
    }

    private boolean openAuburnvaleBank() {
        if (isAuburnvaleBankBoothClickPending()) {
            Microbot.status = "Waiting for Auburnvale bank booth";
            return true;
        }

        if (Rs2Player.isMoving()) {
            Microbot.status = "Running to bank";
            return true;
        }

        TileObject bankBooth = Rs2GameObject.findObjectById(AUBURNVALE_BANK_BOOTH_ID);
        if (bankBooth != null && bankBooth.getLocalLocation() != null && Rs2Camera.isTileOnScreen(bankBooth.getLocalLocation())) {
            return openVisibleAuburnvaleBankBooth(bankBooth);
        }

        if (Rs2Bank.isNearBank(plugin.getSawmillLocation().getBankLocation(), 8)) {
            return Rs2Bank.openBank();
        }

        Microbot.status = "Running to bank";
        Rs2Walker.walkTo(plugin.getSawmillLocation().getBankLocation().getWorldPoint());
        return true;
    }

    private boolean openVisibleAuburnvaleBankBooth(TileObject bankBooth) {
        lastAuburnvaleBankBoothClickAt = System.currentTimeMillis();
        Microbot.status = "Clicking Auburnvale bank booth";
        boolean opened = Rs2Bank.openBank(bankBooth);
        if (!opened && !isAuburnvaleBankBoothClickPending()) {
            lastAuburnvaleBankBoothClickAt = 0;
        }
        return opened || isAuburnvaleBankBoothClickPending();
    }

    private boolean isAuburnvaleBankBoothClickPending() {
        return lastAuburnvaleBankBoothClickAt > 0
                && System.currentTimeMillis() - lastAuburnvaleBankBoothClickAt < AUBURNVALE_BANK_BOOTH_CLICK_COOLDOWN_MS;
    }

    private boolean hasRequiredItems() {
        int logsInInventory = Rs2Inventory.items()
                .filter(rs2Item -> rs2Item.getId() == plugin.getPlank().getLogItemId())
                .mapToInt(rs2Item -> 1)
                .sum();
        return Rs2Inventory.hasItem(plugin.getPlank().getLogItemId()) &&
                Rs2Inventory.hasItemAmount(ItemID.COINS, logsInInventory * plugin.getPlank().getCostPerPlank());
    }

    private void withdrawAndDrink(String potionItemName, boolean drinkUntilThreshold) {
        String simplifiedPotionName = potionItemName.replaceAll("\\s*\\(\\d+\\)", "").trim();
        Rs2Bank.withdrawOne(potionItemName);
        Rs2Inventory.waitForInventoryChanges(1800);
        do {
            Rs2Inventory.interact(simplifiedPotionName, "drink");
            Rs2Inventory.waitForInventoryChanges(1800);
        } while (drinkUntilThreshold && Rs2Player.getRunEnergy() <= plugin.getDrinkAtPercent() && Rs2Inventory.hasItem(simplifiedPotionName));
        if (Rs2Inventory.hasItem(simplifiedPotionName)) {
            Rs2Bank.depositOne(simplifiedPotionName);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
            Rs2Bank.depositOne(ItemID.VIAL_EMPTY);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
    }

    private int getDoseFromName(String potionItemName) {
        Pattern pattern = Pattern.compile("\\((\\d+)\\)$");
        Matcher matcher = pattern.matcher(potionItemName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }
}
