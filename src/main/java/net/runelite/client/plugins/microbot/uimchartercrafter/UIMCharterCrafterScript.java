package net.runelite.client.plugins.microbot.uimchartercrafter;

import lombok.RequiredArgsConstructor;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class UIMCharterCrafterScript extends Script {
    private final UIMCharterCrafterPlugin plugin;
    private final UIMCharterCrafterConfig config;

    private enum State {
        BOOTSTRAP,
        OPEN_SHOP,
        BUY_MATERIALS,
        WORLD_HOP,
        CLOSE_SHOP,
        SUPERGLASS_MAKE,
        START_GLASSBLOWING,
        WAIT_CRAFTING,
        OPEN_SELL,
        SELL_PRODUCTS,
        LOOP_OR_STOP,
        STOP
    }

    private volatile boolean isPrepared = false;
    private volatile boolean hasSetup = false;
    private volatile State state = State.BOOTSTRAP;
    private volatile boolean worldHopPending = false;
    private volatile int beforeHopWorld = -1;
    private volatile int worldHopAttempts = 0;
    private volatile boolean stopRequested = false;

    private static final String TRADER_NAME = "Trader Crewmember";
    private static final String SEAWEED = "Seaweed";
    private static final String BUCKET_OF_SAND = "Bucket of sand";
    private static final String MOLTEN_GLASS = "Molten glass";
    private static final String GLASSBLOWING_PIPE = "Glassblowing pipe";
    private static final String STAFF_OF_FIRE = "Staff of fire";
    private static final String FIRE_BATTLESTAFF = "Fire battlestaff";
    private static final String STAFF_OF_AIR = "Staff of air";
    private static final String AIR_BATTLESTAFF = "Air battlestaff";

    public boolean run() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled() && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        stopRequested = false;
        Microbot.pauseAllScripts.compareAndSet(true, false);
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (stopRequested || Thread.currentThread().isInterrupted()) return;
                if (!super.run() || !Microbot.isLoggedIn()) return;

                switch (state) {
                    case BOOTSTRAP:
                        bootstrap();
                        break;
                    case OPEN_SHOP:
                        openShop();
                        break;
                    case BUY_MATERIALS:
                        buyMaterials();
                        break;
                    case WORLD_HOP:
                        worldHop();
                        break;
                    case CLOSE_SHOP:
                        closeShop();
                        break;
                    case SUPERGLASS_MAKE:
                        superglassMake();
                        break;
                    case START_GLASSBLOWING:
                        startGlassblowing();
                        break;
                    case WAIT_CRAFTING:
                        waitCrafting();
                        break;
                    case OPEN_SELL:
                        openSell();
                        break;
                    case SELL_PRODUCTS:
                        sellProducts();
                        break;
                    case LOOP_OR_STOP:
                        loopOrStop();
                        break;
                    case STOP:
                        stopRequested = true;
                        Microbot.stopPlugin(plugin);
                        break;
                }
            } catch (Exception ex) {
                if (stopRequested) return;
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void bootstrap() {
        isPrepared = false;
        hasSetup = false;

        String target = config.product().widgetName();
        if (target == null || target.isBlank()) {
            update("Bootstrap", "Invalid target product", false, false);
            state = State.STOP;
            return;
        }

        boolean hasPipe = Rs2Inventory.contains(GLASSBLOWING_PIPE);
        boolean hasCoins = Rs2Inventory.hasItemAmount("Coins", 1000);
        boolean hasAstral = Rs2Inventory.hasItemAmount("Astral rune", 2);

        boolean hasAirSupport = ensureElementSupport("Air rune", 10, STAFF_OF_AIR, AIR_BATTLESTAFF);
        boolean hasFireSupport = ensureElementSupport("Fire rune", 6, STAFF_OF_FIRE, FIRE_BATTLESTAFF);

        hasSetup = hasPipe && hasCoins && hasAstral && hasAirSupport && hasFireSupport;
        if (!hasSetup) {
            List<String> missing = new ArrayList<>();
            if (!hasPipe) missing.add("Glassblowing pipe");
            if (!hasCoins) missing.add(">= 1,000 coins");
            if (!hasAstral) missing.add(">= 2 Astral runes");
            if (!hasAirSupport) missing.add("Air support (>=10 Air runes or Staff of air/Air battlestaff)");
            if (!hasFireSupport) missing.add("Fire support (>=6 Fire runes or Staff of fire/Fire battlestaff)");
            String msg = missing.isEmpty() ? "Missing setup requirements" : ("Missing: " + String.join(", ", missing));
            Microbot.showMessage(msg);
            update("Bootstrap", msg, false, false);
            state = State.STOP;
            return;
        }

        if (Rs2Inventory.contains(MOLTEN_GLASS)) {
            update("Bootstrap", "Molten glass found → Start Glassblowing", false, hasSetup);
            state = State.START_GLASSBLOWING;
            return;
        }

        if (Rs2Inventory.itemQuantity("Empty light orb") > 0 || Rs2Inventory.itemQuantity("Light orb") > 0) {
            update("Bootstrap", "Dropping Light orbs (unsellable)", false, hasSetup);
            Rs2Inventory.dropAll("Empty light orb", "Light orb");
        }

        Rs2NpcModel trader = Rs2Npc.getNpc(TRADER_NAME, false);
        if (trader == null) {
            update("Bootstrap", "Trader not nearby", false, true);
            state = State.STOP;
            return;
        }

        if (hasAnySellableGlassItems()) {
            update("Bootstrap", "Found glass items → Sell before buying", false, true);
            state = State.OPEN_SELL;
            return;
        }

        update("Bootstrap", "Ready → Open Shop", false, true);
        state = State.OPEN_SHOP;
    }

    private void openShop() {
        update("Open Shop", "Opening shop", isPrepared, hasSetup);
        boolean opened = Rs2Shop.openShop(TRADER_NAME, true);
        if (!opened) opened = Rs2Shop.openShop("Trader", false);
        if (opened && Rs2Shop.isOpen()) state = State.BUY_MATERIALS;
    }

    private void buyMaterials() {
        int haveSeaweed = Rs2Inventory.itemQuantity(SEAWEED);
        int haveSand = Rs2Inventory.itemQuantity(BUCKET_OF_SAND);
        int needSeaweed = Math.max(0, 10 - haveSeaweed);
        int needSand = Math.max(0, 10 - haveSand);
        update("Buy Materials", String.format("Need seaweed %d, sand %d", needSeaweed, needSand), isPrepared, hasSetup);

        if (needSeaweed <= 0 && needSand <= 0) {
            isPrepared = true;
            state = State.CLOSE_SHOP;
            return;
        }

        if (needSeaweed > 0 && Rs2Shop.hasStock(SEAWEED)) {
            Rs2Shop.buyItemOptimally(SEAWEED, needSeaweed);
        }
        if (needSand > 0 && Rs2Shop.hasStock(BUCKET_OF_SAND)) {
            Rs2Shop.buyItemOptimally(BUCKET_OF_SAND, needSand);
        }

        haveSeaweed = Rs2Inventory.itemQuantity(SEAWEED);
        haveSand = Rs2Inventory.itemQuantity(BUCKET_OF_SAND);
        isPrepared = haveSeaweed >= 10 && haveSand >= 10;
        if (isPrepared) {
            state = State.CLOSE_SHOP;
            return;
        }
        int remainingSeaweed = Math.max(0, 10 - haveSeaweed);
        int remainingSand = Math.max(0, 10 - haveSand);
        if (remainingSeaweed > 0 || remainingSand > 0) {
            update("World Hop", String.format("Still need: seaweed %d, sand %d — hopping", remainingSeaweed, remainingSand), isPrepared, hasSetup);
            worldHopPending = true;
            worldHopAttempts = 0;
            beforeHopWorld = Microbot.getClient().getWorld();
            if (Rs2Shop.isOpen()) Rs2Shop.closeShop();
            state = State.WORLD_HOP;
            return;
        }
    }

    private void worldHop() {
        if (worldHopPending) {
            if (Microbot.getClient().getGameState() == GameState.HOPPING
                    || Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN) {
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 25000);
                sleep(600);
            }
            if (Microbot.getClient().getGameState() == GameState.LOGGED_IN) {
                int currentWorld = Microbot.getClient().getWorld();
                if (beforeHopWorld != -1 && currentWorld != beforeHopWorld) {
                    worldHopPending = false;
                    update("World Hop", "Hopped successfully to world " + currentWorld, isPrepared, hasSetup);
                    state = State.OPEN_SHOP;
                    return;
                }
            }
        }
        if (worldHopAttempts >= 3) {
            update("World Hop", "Hop attempts exhausted; retry later", isPrepared, hasSetup);
            state = State.BUY_MATERIALS;
            return;
        }

        if (Rs2Shop.isOpen()) Rs2Shop.closeShop();
        sleepUntil(() -> !Rs2Player.isAnimating(), 2000);

        int world = Login.getRandomWorld(Rs2Player.isMember());
        worldHopAttempts++;
        update("World Hop", "Initiating hop attempt " + worldHopAttempts + " to world " + world, isPrepared, hasSetup);
        boolean hopCall = Microbot.hopToWorld(world);
        if (!hopCall) {
            sleep(800);
        }
    }

    private void closeShop() {
        update("Close Shop", "Closing shop", isPrepared, hasSetup);
        Rs2Shop.closeShop();
        state = State.SUPERGLASS_MAKE;
    }

    private void superglassMake() {
        if (!(isPrepared && hasSetup)) {
            update("Superglass Make", "Not prepared or setup missing", isPrepared, hasSetup);
            state = State.STOP;
            return;
        }
        Rs2Tab.switchTo(InterfaceTab.MAGIC);
        if (!Rs2Magic.canCast(MagicAction.SUPERGLASS_MAKE)) {
            update("Superglass Make", "Cannot cast Superglass Make", isPrepared, hasSetup);
            state = State.STOP;
            return;
        }
        update("Superglass Make", "Casting Superglass Make", isPrepared, hasSetup);
        boolean cast = Rs2Magic.cast(MagicAction.SUPERGLASS_MAKE);
        if (cast) {
            sleepUntil(() -> Rs2Inventory.itemQuantity(MOLTEN_GLASS) > 0, 5000);
            if (Rs2Inventory.itemQuantity(MOLTEN_GLASS) > 0) {
                state = State.START_GLASSBLOWING;
            } else {
                update("Superglass Make", "No molten glass after cast", isPrepared, hasSetup);
                state = State.STOP;
            }
        }
    }

    private void startGlassblowing() {
        update("Start Glassblowing", "Preparing to combine (waiting for animation to end)", isPrepared, hasSetup);
        sleepUntil(() -> Rs2Inventory.itemQuantity(MOLTEN_GLASS) > 0 && !Rs2Player.isAnimating(), 8000);
        sleep(200);

        update("Start Glassblowing", "Combining pipe with molten glass", isPrepared, hasSetup);
        boolean combined = false;
        for (int attempt = 0; attempt < 3 && !combined; attempt++) {
            combined = Rs2Inventory.combine(
                    Rs2Inventory.get(GLASSBLOWING_PIPE),
                    Rs2Inventory.get(MOLTEN_GLASS)
            );
            if (!combined) {
                sleepUntil(() -> !Rs2Player.isAnimating(), 1500);
                sleep(200);
            }
        }
        if (!combined) {
            update("Start Glassblowing", "Failed to use pipe on molten glass", isPrepared, hasSetup);
            state = State.STOP;
            return;
        }
        String target = config.product().widgetName();
        sleepUntil(Rs2Widget::isProductionWidgetOpen, 5000);
        boolean selected = false;
        try {
            selected = Rs2Widget.clickWidget(target, true)
                    || Rs2Widget.clickWidget(target, false);
        } catch (Exception ignored) { }

        if (!selected) {
            update("Start Glassblowing", "Could not find product in crafting menu: " + target, isPrepared, hasSetup);
            state = State.STOP;
            return;
        }

        update("Wait Crafting", "Making: " + target, isPrepared, hasSetup);
        state = State.WAIT_CRAFTING;
    }

    private void waitCrafting() {
        sleep(600);
        if (!Rs2Inventory.contains(MOLTEN_GLASS)) {
            state = State.OPEN_SELL;
        }
    }

    private void openSell() {
        if (Rs2Inventory.itemQuantity("Empty light orb") > 0 || Rs2Inventory.itemQuantity("Light orb") > 0) {
            update("Open Sell", "Dropping Light orbs (unsellable)", isPrepared, hasSetup);
            Rs2Inventory.dropAll("Empty light orb", "Light orb");
        }

        update("Open Sell", "Opening shop to sell", isPrepared, hasSetup);
        boolean opened = Rs2Shop.openShop(TRADER_NAME, true);
        if (opened && Rs2Shop.isOpen()) state = State.SELL_PRODUCTS;
    }

    private void sellProducts() {
        update("Sell Products", "Selling crafted items", isPrepared, hasSetup);
        for (UIMCharterCrafterConfig.Product p : UIMCharterCrafterConfig.Product.values()) {
            if ("Empty light orb".equals(p.sellName())) continue;
            sellAllOf(p.sellName());
        }
        isPrepared = false;
        state = State.BUY_MATERIALS;
    }

    private void loopOrStop() {
        isPrepared = false;
        boolean hasPipe = Rs2Inventory.contains(GLASSBLOWING_PIPE);
        boolean hasCoins = Rs2Inventory.hasItemAmount("Coins", 1000);
        boolean hasAstral = Rs2Inventory.hasItemAmount("Astral rune", 2);
        boolean hasAirSupport = ensureElementSupport("Air rune", 10, STAFF_OF_AIR, AIR_BATTLESTAFF);
        boolean hasFireSupport = ensureElementSupport("Fire rune", 6, STAFF_OF_FIRE, FIRE_BATTLESTAFF);
        hasSetup = hasPipe && hasCoins && hasAstral && hasAirSupport && hasFireSupport;

        if (hasSetup) {
            update("Loop Or Stop", "Looping for next batch", isPrepared, hasSetup);
            state = State.OPEN_SHOP;
        } else {
            update("Loop Or Stop", "Setup missing; stopping", isPrepared, hasSetup);
            state = State.STOP;
        }
    }

    private void sellAllOf(String name) {
        if (Rs2Inventory.itemQuantity(name) <= 0) return;
        while (Rs2Inventory.itemQuantity(name) > 0 && Rs2Shop.isOpen()) {
            Rs2Inventory.sellItem(name, "50");
            sleep(250);
        }
    }

    

    private boolean hasAnySellableGlassItems() {
        for (UIMCharterCrafterConfig.Product p : UIMCharterCrafterConfig.Product.values()) {
            if (Rs2Inventory.itemQuantity(p.sellName()) > 0) {
                return true;
            }
        }
        return false;
    }

    private void update(String s, String msg, boolean prepared, boolean setup) {
        plugin.updateState(s, msg, prepared, setup);
    }

    public void requestStop() {
        stopRequested = true;
    }

    private boolean ensureElementSupport(String runeName, int requiredAmount, String staffName, String battlestaffName) {
        if (Rs2Inventory.hasItemAmount(runeName, requiredAmount)) return true;
        if (Rs2Equipment.isWearing(staffName) || Rs2Equipment.isWearing(battlestaffName)) return true;

        if (Rs2Inventory.hasItem(staffName)) {
            if (Rs2Inventory.interact(staffName, "Wield")) {
                sleepUntil(() -> Rs2Equipment.isWearing(staffName), 5000);
                if (Rs2Equipment.isWearing(staffName)) return true;
            }
        }
        if (Rs2Inventory.hasItem(battlestaffName)) {
            if (Rs2Inventory.interact(battlestaffName, "Wield")) {
                sleepUntil(() -> Rs2Equipment.isWearing(battlestaffName), 5000);
                if (Rs2Equipment.isWearing(battlestaffName)) return true;
            }
        }
        return false;
    }
}
