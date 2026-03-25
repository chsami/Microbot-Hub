package net.runelite.client.plugins.microbot.lunarplankmake;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.lunarplankmake.enums.Logs;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.QuantityFormatter;

import java.util.concurrent.TimeUnit;

public class LunarPlankMakeScript extends Script {

    public static String combinedMessage = "";
    public static long plankMade = 0;
    private int profitPerPlank = 0;
    private long startTime;
    private boolean useSetDelay;
    private int setDelay;
    private boolean useRandomDelay;
    private int maxRandomDelay;

    private boolean useVouchers;
    private boolean lazyMode;

    private enum State {
        PLANKING,
        BANKING,
        WAITING
    }

    private State currentState = State.PLANKING;

    public boolean run(LunarPlankMakeConfig config) {
        startTime = System.currentTimeMillis();

        refreshProfitPerPlank(config);

        useSetDelay = config.useSetDelay();
        setDelay = config.setDelay();
        useRandomDelay = config.useRandomDelay();
        maxRandomDelay = config.maxRandomDelay();
        useVouchers = config.useSawmillVouchers();
        lazyMode = config.lazyMode();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;

                switch (currentState) {
                    case PLANKING:
                        plankItems(config);
                        break;
                    case BANKING:
                        bank(config);
                        break;
                    case WAITING:
                        waitUntilReady();
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Exception in LunarPlankMakeScript: " + ex.getMessage());
            }
        }, 0, 50, TimeUnit.MILLISECONDS);

        return true;
    }

    private void plankItems(LunarPlankMakeConfig config) {
        int logId = config.ITEM().getLogItemId();
        if (!Rs2Inventory.hasItem(logId)) {
            currentState = State.BANKING;
            return;
        }

        int plankId = config.ITEM().getPlankItemId();
        int initialPlankCount = Rs2Inventory.count(plankId);

        if (lazyMode) {
            int initialLogQuantity = Rs2Inventory.count(logId);
            Rs2Magic.cast(MagicAction.PLANK_MAKE);
            addDelay();
            Rs2Inventory.interact(logId);
            if (waitUntilNoLogsRemaining(config, initialLogQuantity)) {
                int plankMadeThisBatch = Rs2Inventory.count(plankId) - initialPlankCount;
                plankMade += plankMadeThisBatch;
                addDelay();
            } else {
                Microbot.log("Lazy mode: timed out waiting for logs to finish converting.");
                currentState = State.WAITING;
            }
            return;
        }

        Rs2Magic.cast(MagicAction.PLANK_MAKE);
        addDelay();
        Rs2Inventory.interact(logId);

        if (waitForInventoryChange(plankId, initialPlankCount)) {
            int plankMadeThisAction = Rs2Inventory.count(plankId) - initialPlankCount;
            plankMade += plankMadeThisAction;
            addDelay();
        } else {
            Microbot.log("Failed to detect plank creation.");
            currentState = State.WAITING;
        }
    }

    private boolean waitUntilNoLogsRemaining(LunarPlankMakeConfig config, int initialLogQuantity) {
        if (initialLogQuantity <= 0) {
            return true;
        }
        int logId = config.ITEM().getLogItemId();
        long start = System.currentTimeMillis();
        long timeoutMs = initialLogQuantity * 4000L;
        if (timeoutMs < 60000L) {
            timeoutMs = 60000L;
        }
        while (Rs2Inventory.hasItem(logId)) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                return false;
            }
            sleep(50);
        }
        return true;
    }

    private boolean waitForInventoryChange(int plankItemId, int initialCount) {
        long start = System.currentTimeMillis();
        while (Rs2Inventory.count(plankItemId) == initialCount) {
            if (System.currentTimeMillis() - start > 3000) {
                return false;
            }
            sleep(10);
        }
        return true;
    }

    private void bank(LunarPlankMakeConfig config) {
        if (!Rs2Bank.openBank()) return;

        int plankId = config.ITEM().getPlankItemId();
        int logId = config.ITEM().getLogItemId();

        Rs2Bank.depositAll(plankId);
        sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(plankId));

        boolean hasVoucher = false;

        if (useVouchers) {
            if (Rs2Inventory.contains("Sawmill voucher")) {
                hasVoucher = true;
            } else if (Rs2Bank.hasItem("Sawmill voucher")) {
                Rs2Bank.withdrawAll("Sawmill voucher");
                sleepUntilOnClientThread(() -> Rs2Inventory.contains("Sawmill voucher"));
                hasVoucher = true;
            }
        }

        int logsToWithdraw = hasVoucher ? 12 : 28;

        int logsInInventory = Rs2Inventory.count(logId);
        if (logsInInventory >= logsToWithdraw) {
            Rs2Bank.closeBank();
            currentState = State.PLANKING;
            calculateProfitAndDisplay(config);
            return;
        }

        if (!Rs2Bank.hasItem(logId)) {
            Microbot.showMessage("No more " + config.ITEM().getName() + " to plank.");
            shutdown();
            return;
        }

        int need = logsToWithdraw - logsInInventory;
        Rs2Bank.withdrawX(logId, need);
        sleepUntilOnClientThread(() -> Rs2Inventory.count(logId) >= logsToWithdraw);

        Rs2Bank.closeBank();
        currentState = State.PLANKING;
        calculateProfitAndDisplay(config);
    }

    private void waitUntilReady() {
        sleep(500);
        currentState = State.PLANKING;
    }

    private void refreshProfitPerPlank(LunarPlankMakeConfig config) {
        Logs item = config.ITEM();
        int plankPrice = gePrice(item.getFinished());
        int logPrice = gePrice(item.getName());
        int astral = gePrice("Astral rune");
        int nature = gePrice("Nature rune");
        int runeGp = 2 * astral + nature;
        if (config.includeEarthRuneCost()) {
            int earth = gePrice("Earth rune");
            runeGp += 15 * earth;
        }
        int voucherPerPlank = 0;
        if (config.useSawmillVouchers()) {
            int voucherPrice = gePrice("Sawmill voucher");
            voucherPerPlank = voucherPrice / 24;
        }
        int planksPerLog = config.useSawmillVouchers() ? 2 : 1;
        int logCostPerPlank = logPrice / planksPerLog;
        int coinFeePerPlank = item.getPlankMakeCoinFee() / planksPerLog;
        int runeCostPerPlank = runeGp / planksPerLog;
        profitPerPlank = plankPrice - logCostPerPlank - coinFeePerPlank - runeCostPerPlank - voucherPerPlank;
    }

    private static int gePrice(String itemName) {
        try {
            return Microbot.getItemManager().search(itemName).get(0).getPrice();
        } catch (Exception e) {
            return 0;
        }
    }

    private void calculateProfitAndDisplay(LunarPlankMakeConfig config) {
        refreshProfitPerPlank(config);
        double elapsedHours = (System.currentTimeMillis() - startTime) / 3600000.0;
        int plankPerHour = (int) (plankMade / elapsedHours);
        int totalProfit = profitPerPlank * (int) plankMade;
        int profitPerHour = profitPerPlank * plankPerHour;

        combinedMessage = config.ITEM().getFinished() + ": " +
                QuantityFormatter.quantityToRSDecimalStack((int) plankMade) + " (" +
                QuantityFormatter.quantityToRSDecimalStack(plankPerHour) + "/hr) | " +
                "Profit: " + QuantityFormatter.quantityToRSDecimalStack(totalProfit) + " (" +
                QuantityFormatter.quantityToRSDecimalStack(profitPerHour) + "/hr)";
    }

    private void addDelay() {
        if (useSetDelay) {
            sleep(setDelay);
        } else if (useRandomDelay) {
            sleep(Rs2Random.between(0, maxRandomDelay));
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        plankMade = 0;
        combinedMessage = "";
        currentState = State.PLANKING;
    }
}
