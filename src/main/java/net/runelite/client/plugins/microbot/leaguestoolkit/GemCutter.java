package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class GemCutter {

    // Toci's actual tile in Aldarin, Varlamore
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
            return false;
        }

        if (!gem.hasRequiredLevel()) {
            status = "Crafting level too low for " + gem.getCutName() + " (need " + gem.getCraftingLevel() + ")";
            return false;
        }

        if (!Rs2Inventory.hasItem(CHISEL_NAME)) {
            status = "No chisel in inventory";
            return false;
        }

        // Need to bank for coins if we're idle (no gems in hand) and low on coins
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
                return handleBuying(gem);
            case CUTTING:
                return handleCutting(gem);
            case SELLING:
                return handleSelling(gem, config);
        }
        return true;
    }

    private boolean handleBanking(LeaguesToolkitConfig config) {
        if (Rs2Shop.isOpen()) {
            Rs2Shop.closeShop();
            return true;
        }

        if (config.gemCutterUseBriefcase() && Rs2Inventory.hasItem(BRIEFCASE_NAME)) {
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
        if (Rs2Npc.getNpc(TOCI_NPC_NAME) != null) {
            status = "At Toci";
            state = GemCutterState.BUYING;
            return true;
        }
        status = "Walking to Toci";
        if (!Rs2Player.isMoving()) {
            Rs2Walker.walkTo(TOCI_LOCATION, 6);
        }
        return true;
    }

    private boolean handleBuying(GemType gem) {
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
            status = "Inventory full — moving to cut";
            Rs2Shop.closeShop();
            state = GemCutterState.CUTTING;
            return true;
        }

        if (!Rs2Shop.hasStock(gem.getUncutName())) {
            if (uncutCount > 0) {
                status = "Shop out of stock — cutting what we have";
                Rs2Shop.closeShop();
                state = GemCutterState.CUTTING;
                return true;
            }
            status = "Shop out of " + gem.getUncutName() + " — waiting";
            sleep(1500, 2500);
            return true;
        }

        // Mass-click buy at 100-250ms intervals. Stop when 2 consecutive clicks
        // fail to add a ruby to inventory (inventory full OR shop out of stock).
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

    private boolean handleCutting(GemType gem) {
        if (!Rs2Inventory.hasItem(gem.getUncutName())) {
            status = "All gems cut — moving to sell";
            state = GemCutterState.SELLING;
            return true;
        }

        // Start the cut: chisel on uncut gem
        status = "Starting to cut " + gem.getCutName();
        Rs2Inventory.use(CHISEL_NAME);
        sleep(300, 500);
        Rs2Inventory.use(gem.getUncutName());

        // Wait for "How many do you wish to make?" dialog, then press space for All
        sleep(600, 900);
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);

        // Wait for cutting to finish (XP stops flowing OR all uncut gems gone)
        sleep(2000, 3000);
        status = "Cutting " + gem.getCutName() + "...";
        sleepUntil(() -> !Microbot.isGainingExp || !Rs2Inventory.hasItem(gem.getUncutName()), 60000);

        return true;
    }

    private boolean handleSelling(GemType gem, LeaguesToolkitConfig config) {
        if (!Rs2Inventory.hasItem(gem.getCutName())) {
            status = "All cut gems sold — looping";
            if (Rs2Inventory.itemQuantity(COINS_ID) < config.gemCutterMinCoins()) {
                // Only close when we need to walk somewhere (banking)
                if (Rs2Shop.isOpen()) Rs2Shop.closeShop();
                state = GemCutterState.BANKING;
            } else {
                // Leave shop open — handleBuying will use the already-open shop next tick
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

        // Mass-click sell at 100-250ms intervals — click the LAST slot containing
        // a cut gem, repeat until inventory runs out. Two consecutive misses = stop.
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
}
