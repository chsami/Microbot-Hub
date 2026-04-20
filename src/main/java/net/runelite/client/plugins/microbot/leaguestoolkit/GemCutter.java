package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class GemCutter {

    private static final WorldPoint TOCI_LOCATION = new WorldPoint(1428, 2975, 0);
    private static final String TOCI_NPC_NAME = "Toci";
    private static final String CHISEL_NAME = "Chisel";
    private static final int COINS_ID = 995;
    private static final String BRIEFCASE_NAME = "Banker's briefcase";

    @Getter
    private GemCutterState state = GemCutterState.WALKING_TO_SHOP;
    @Getter
    private String status = "Idle";

    public void reset() {
        state = GemCutterState.WALKING_TO_SHOP;
        status = "Idle";
    }

    public boolean tick(LeaguesToolkitConfig config) {
        GemType gem = config.gemType();
        if (gem == null) {
            status = "No gem selected";
            log.info("[GemCutter] tick: no gem selected");
            return false;
        }

        log.info("[GemCutter] tick: state={}, cutGems={}, mode={}", state, shouldCut(config), config.gemCutterMode());

        // Need to bank for coins if idle with low funds
        if (state == GemCutterState.WALKING_TO_SHOP
                && Rs2Inventory.itemQuantity(COINS_ID) < config.gemCutterMinCoins()
                && !Rs2Inventory.hasItem(gem.getUncutName())
                && !Rs2Inventory.hasItem(gem.getCutName())) {
            state = GemCutterState.BANKING;
        }

        switch (state) {
            case BANKING:
                return handleBanking(config);
            case WALKING_TO_SHOP:
                return handleWalkingToShop();
            case BUYING:
                return handleBuying(gem, config);
            case CUTTING:
                return handleCutting(gem, config);
            case SELLING:
                return handleSelling(gem, config);
            case BRIEFCASE_BANKING:
                return handleBriefcaseBanking(gem, config);
            case TELEPORTING_BACK:
                return handleTeleportingBack();
        }
        return true;
    }

    private boolean handleBanking(LeaguesToolkitConfig config) {
        if (Rs2Shop.isOpen()) {
            Rs2Shop.closeShop();
            return true;
        }

        if (Rs2Equipment.isWearing(BRIEFCASE_NAME)) {
            if (!Rs2Bank.isOpen()) {
                status = "Using equipped briefcase to bank";
                Rs2Equipment.interact(BRIEFCASE_NAME, "Last-destination");
                sleepUntil(Rs2Bank::isOpen, 5000);
                return true;
            }
        } else if (Rs2Inventory.hasItem(BRIEFCASE_NAME)) {
            if (!Rs2Bank.isOpen()) {
                status = "Using briefcase to bank";
                Rs2Inventory.interact(BRIEFCASE_NAME, "Bank");
                sleepUntil(Rs2Bank::isOpen, 5000);
                return true;
            }
        } else {
            if (!Rs2Bank.isOpen()) {
                status = "Walking to bank";
                if (!Rs2Bank.walkToBankAndUseBank()) return true;
            }
        }

        if (!Rs2Bank.isOpen()) return true;

        status = "Withdrawing coins";
        if (!Rs2Bank.hasItem(COINS_ID)) {
            status = "No coins in bank — stopping";
            Rs2Bank.closeBank();
            return false;
        }

        Rs2Bank.withdrawAll(COINS_ID);
        Rs2Bank.closeBank();
        state = GemCutterState.WALKING_TO_SHOP;
        return true;
    }

    private boolean handleWalkingToShop() {
        log.info("[GemCutter] handleWalkingToShop entered");

        if (Rs2Shop.isOpen()) {
            log.info("[GemCutter] Shop already open — transitioning to BUYING");
            status = "Shop already open";
            state = GemCutterState.BUYING;
            return true;
        }

        WorldPoint playerPos = Rs2Player.getWorldLocation();
        if (playerPos == null) {
            log.info("[GemCutter] Player position is null");
            status = "Waiting for player position...";
            return true;
        }

        int distance = playerPos.distanceTo(TOCI_LOCATION);
        log.info("[GemCutter] Player at {}, Toci at {}, distance={}", playerPos, TOCI_LOCATION, distance);

        // Far away — walk first, don't try to open shop
        if (distance > 15) {
            status = "Walking to Toci (" + distance + " tiles away)";
            log.info("[GemCutter] Walking to Toci...");
            Rs2Walker.walkTo(TOCI_LOCATION, 4);
            sleep(3000, 5000);
            return true;
        }

        // Close enough — try to open shop
        status = "Near Toci — opening shop";
        log.info("[GemCutter] Close enough, opening shop");
        boolean opened = Rs2Shop.openShop(TOCI_NPC_NAME);
        log.info("[GemCutter] openShop returned {}", opened);
        if (opened) {
            sleepUntil(Rs2Shop::isOpen, 5000);
            if (Rs2Shop.isOpen()) {
                state = GemCutterState.BUYING;
                return true;
            }
        }
        return true;
    }

    private boolean shouldCut(LeaguesToolkitConfig config) {
        GemCutterMode mode = config.gemCutterMode();
        return mode == GemCutterMode.BUY_CUT_SELL || mode == GemCutterMode.BUY_CUT_BANK;
    }

    private boolean shouldUseBriefcase(LeaguesToolkitConfig config) {
        GemCutterMode mode = config.gemCutterMode();
        return mode == GemCutterMode.BUY_AND_BANK || mode == GemCutterMode.BUY_CUT_BANK;
    }

    private GemCutterState nextStateAfterCutting(LeaguesToolkitConfig config) {
        return shouldUseBriefcase(config)
                ? GemCutterState.BRIEFCASE_BANKING
                : GemCutterState.SELLING;
    }

    private boolean handleBuying(GemType gem, LeaguesToolkitConfig config) {
        if (!Rs2Shop.isOpen()) {
            status = "Opening Toci's shop";
            if (!Rs2Shop.openShop(TOCI_NPC_NAME)) {
                status = "Could not open shop";
                return true;
            }
            sleepUntil(Rs2Shop::isOpen, 3000);
            return true;
        }

        int uncutCount = Rs2Inventory.count(gem.getUncutName());

        if (Rs2Inventory.isFull()) {
            status = "Inventory full";
            Rs2Shop.closeShop();
            state = shouldCut(config) ? GemCutterState.CUTTING : nextStateAfterCutting(config);
            return true;
        }

        if (!Rs2Shop.hasStock(gem.getUncutName())) {
            if (uncutCount > 0) {
                status = "Shop out of stock";
                Rs2Shop.closeShop();
                state = shouldCut(config) ? GemCutterState.CUTTING : nextStateAfterCutting(config);
                return true;
            }
            status = "Shop out of " + gem.getUncutName() + " — waiting";
            sleep(1500, 2500);
            return true;
        }

        // Mass-click buy at 100-250ms intervals
        status = "Rapid-buying " + gem.getUncutName();
        int safetyMax = 32;
        int missedInRow = 0;
        for (int i = 0; i < safetyMax; i++) {
            if (!Rs2Shop.isOpen()) break;
            int before = Rs2Inventory.count(gem.getUncutName());
            Rs2Shop.buyItem(gem.getUncutName(), "1");
            sleep(Rs2Random.between(100, 250));
            if (Rs2Inventory.count(gem.getUncutName()) > before) {
                missedInRow = 0;
            } else {
                missedInRow++;
                if (missedInRow >= 2) break;
            }
        }
        return true;
    }

    private boolean handleCutting(GemType gem, LeaguesToolkitConfig config) {
        // Skip cutting if disabled OR no chisel — go straight to sell/bank
        if (!shouldCut(config) || !Rs2Inventory.hasItem(CHISEL_NAME)) {
            log.info("[GemCutter] Skipping CUTTING (cutGems={}, hasChisel={}), going to next state",
                    shouldCut(config), Rs2Inventory.hasItem(CHISEL_NAME));
            state = nextStateAfterCutting(config);
            return true;
        }

        if (!Rs2Inventory.hasItem(gem.getUncutName())) {
            status = "All gems cut";
            if (shouldUseBriefcase(config)) {
                state = GemCutterState.BRIEFCASE_BANKING;
            } else {
                state = GemCutterState.SELLING;
            }
            return true;
        }

        status = "Starting to cut " + gem.getCutName();
        Rs2Inventory.use(CHISEL_NAME);
        sleep(300, 500);
        Rs2Inventory.use(gem.getUncutName());

        sleep(600, 900);
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

        sleep(2000, 3000);
        status = "Cutting " + gem.getCutName() + "...";
        sleepUntil(() -> !Microbot.isGainingExp || !Rs2Inventory.hasItem(gem.getUncutName()), 60000);

        return true;
    }

    // === SELL MODE ===

    private boolean handleSelling(GemType gem, LeaguesToolkitConfig config) {
        if (!Rs2Inventory.hasItem(gem.getCutName())) {
            status = "All cut gems sold — looping";
            if (Rs2Inventory.itemQuantity(COINS_ID) < config.gemCutterMinCoins()) {
                if (Rs2Shop.isOpen()) Rs2Shop.closeShop();
                state = GemCutterState.BANKING;
            } else {
                // Keep shop open for next buy cycle
                state = GemCutterState.BUYING;
            }
            return true;
        }

        if (!Rs2Shop.isOpen()) {
            status = "Reopening shop to sell";
            if (!Rs2Shop.openShop(TOCI_NPC_NAME)) {
                status = "Could not reopen shop";
                return true;
            }
            sleepUntil(Rs2Shop::isOpen, 3000);
            return true;
        }

        // Mass-click sell from bottom of inventory
        int initialCount = Rs2Inventory.count(gem.getCutName());
        status = "Rapid-selling " + gem.getCutName() + " x" + initialCount;

        int safetyMax = initialCount + 5;
        int missedInRow = 0;
        for (int i = 0; i < safetyMax; i++) {
            if (!Rs2Shop.isOpen()) break;
            if (!Rs2Inventory.hasItem(gem.getCutName())) break;

            Rs2ItemModel last = Rs2Inventory.items(item ->
                    gem.getCutName().equalsIgnoreCase(item.getName()))
                    .reduce((a, b) -> b)
                    .orElse(null);
            if (last == null) break;
            int before = Rs2Inventory.count(gem.getCutName());

            Rs2Inventory.slotInteract(last.getSlot(), "Sell 1");
            sleep(Rs2Random.between(100, 250));

            if (Rs2Inventory.count(gem.getCutName()) < before) {
                missedInRow = 0;
            } else {
                missedInRow++;
                if (missedInRow >= 2) break;
            }
        }

        return true;
    }

    // === BRIEFCASE BANKING MODE ===

    private boolean handleBriefcaseBanking(GemType gem, LeaguesToolkitConfig config) {
        log.info("[GemCutter] handleBriefcaseBanking: bankOpen={}, wearing={}, hasInInv={}",
                Rs2Bank.isOpen(),
                Rs2Equipment.isWearing(BRIEFCASE_NAME),
                Rs2Inventory.hasItem(BRIEFCASE_NAME));

        // Step 1: Teleport to bank via briefcase, then open bank
        if (!Rs2Bank.isOpen()) {
            // First teleport to the bank
            status = "Teleporting to bank via briefcase";
            if (Rs2Equipment.isWearing(BRIEFCASE_NAME)) {
                log.info("[GemCutter] Clicking equipped briefcase 'Last-destination'");
                Rs2Equipment.interact(BRIEFCASE_NAME, "Last-destination");
            } else {
                status = "No briefcase equipped — walking to bank";
                if (!Rs2Bank.walkToBankAndUseBank()) return true;
                sleepUntil(Rs2Bank::isOpen, 10000);
                return true;
            }

            // Wait for teleport to finish
            sleep(2000, 3000);
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 8000);
            sleep(500, 1000);

            // Now open the bank normally
            status = "Opening bank";
            log.info("[GemCutter] Teleported, now opening bank");
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5000);
            log.info("[GemCutter] Bank open: {}", Rs2Bank.isOpen());
            return true;
        }

        // Step 2: Deposit gems (cut or uncut depending on config)
        status = "Depositing gems";
        if (shouldCut(config) && Rs2Inventory.hasItem(gem.getCutName())) {
            Rs2Bank.depositAll(gem.getCutName());
            sleep(300, 500);
        }
        if (!shouldCut(config) && Rs2Inventory.hasItem(gem.getUncutName())) {
            Rs2Bank.depositAll(gem.getUncutName());
            sleep(300, 500);
        }

        // Step 3: Withdraw coins if low
        if (Rs2Inventory.itemQuantity(COINS_ID) < config.gemCutterMinCoins() && Rs2Bank.hasItem(COINS_ID)) {
            Rs2Bank.withdrawAll(COINS_ID);
            sleep(300, 500);
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        state = GemCutterState.TELEPORTING_BACK;
        return true;
    }

    private boolean handleTeleportingBack() {
        // Walk back to Toci using the web walker
        status = "Walking back to Toci";
        log.info("[GemCutter] TELEPORTING_BACK → transitioning to WALKING_TO_SHOP");
        state = GemCutterState.WALKING_TO_SHOP;
        return true;
    }
}
