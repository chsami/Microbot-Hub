package net.runelite.client.plugins.microbot.mess;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.VarClientStr;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class TheMessScript extends Script {

    private static final WorldPoint UTENSIL_CUPBOARD_LOC = new WorldPoint(1644, 3624, 0);
    private static final WorldPoint FOOD_CUPBOARD_LOC    = new WorldPoint(1645, 3623, 0);
    private static final WorldPoint SINK_LOC             = new WorldPoint(1644, 3628, 0);
    private static final WorldPoint MEAT_TABLE_LOC       = new WorldPoint(1645, 3630, 0);
    private static final WorldPoint CLAY_OVEN_LOC        = new WorldPoint(1648, 3627, 0);
    private static final WorldPoint BUFFET_TABLE_LOC     = new WorldPoint(1640, 3629, 0);
    private static final WorldPoint MESS_HUB             = new WorldPoint(1645, 3627, 0);

    private static final int SHOP_WIDGET_ID    = 15859715;
    private static final int SHOP_CLOSE_PARENT = 15859713;

    private static final int APPRECIATION_BAR_PIE   = 15400966;
    private static final int APPRECIATION_BAR_PIZZA = 15400970;
    private static final int APPRECIATION_BAR_STEW  = 15400974;

    private static final int BATCH_SIZE = 14;
    private static final int PIZZA_BATCH_SIZE = 13; // 2 knife slots reserved

    private TheMessConfig config;
    private TheMessOverlay overlay;

    public boolean run(TheMessConfig config, TheMessOverlay overlay) {
        this.config = config;
        this.overlay = overlay;
        Microbot.enableAutoRunOn = false;

        Rs2Antiban.setActivity(Activity.GENERAL_COOKING);
        Rs2AntibanSettings.naturalMouse = true;

        Rs2Camera.setZoom(Rs2Random.randomGaussian(200, 20));
        Rs2Camera.setYaw(Rs2Random.dicePercentage(50)
                ? Rs2Random.randomGaussian(750, 50)
                : Rs2Random.randomGaussian(1700, 50));
        Rs2Camera.setPitch(Rs2Random.betweenInclusive(418, 512));

        setStatus("Starting");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::tick,
                0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private long lastDecisionLogMs = 0;
    private String lastDecision = "";

    private void tick() {
        try {
            if (!Microbot.isLoggedIn() || !super.run() || !isRunning()) return;
            if (BreakHandlerScript.isBreakActive()) { setStatus("Break"); return; }

            if (!inMess()) { logDecision("walkToMess"); walkToMess(); return; }
            if (hasJunk()) { logDecision("cleanInventory"); runCleanInventory(); return; }
            if (violatesBatchInvariant()) { setStatus("FAIL: batch invariant"); shutdown(); return; }
            dropBurnt();

            switch (config.dish()) {
                case STEW:     stewTick();    break;
                case MEAT_PIE: meatPieTick(); break;
                case PIZZA:    pizzaTick();   break;
            }
        } catch (Exception ex) {
            log.warn("tick failed: {}", ex.getMessage(), ex);
        }
    }

    private void logDecision(String decision) {
        long now = System.currentTimeMillis();
        // Only log when the decision changes, or every 10s on the same decision (so a stuck loop is visible).
        if (!decision.equals(lastDecision) || now - lastDecisionLogMs > 10_000) {
            log.info("[mess] step={} pos={} invCount={} dish={}",
                    decision, Rs2Player.getWorldLocation(), Rs2Inventory.count(), config.dish());
            lastDecision = decision;
            lastDecisionLogMs = now;
        }
    }

    // ---------- per-dish flows ----------

    private void stewTick() {
        // Consume-to-zero phases (cooking) take priority over any combine that uses their output.
        // Withdrawal targets size to predecessor counts so leftovers from burns carry into the next batch.
        if (has(ItemID.HOSIDIUS_SERVERY_STEW))                                          { serveAndMaybeHop(APPRECIATION_BAR_STEW); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_UNCOOKED_STEW))                                 { cookOnOven(ItemID.HOSIDIUS_SERVERY_UNCOOKED_STEW); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_RAW_MEAT))                                      { cookOnOven(ItemID.HOSIDIUS_SERVERY_RAW_MEAT); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_MEATWATER) && has(ItemID.HOSIDIUS_SERVERY_POTATO)){ combineAll(ItemID.HOSIDIUS_SERVERY_MEATWATER, ItemID.HOSIDIUS_SERVERY_POTATO, "Combining stew"); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_MEATWATER))                                     { withdrawFromFood(ItemID.HOSIDIUS_SERVERY_POTATO, count(ItemID.HOSIDIUS_SERVERY_MEATWATER)); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_COOKED_MEAT) && has(ItemID.BOWL_WATER))         { combineAll(ItemID.BOWL_WATER, ItemID.HOSIDIUS_SERVERY_COOKED_MEAT, "Combining meat + water"); return; }
        // Finish filling bowls before sizing the raw-meat withdrawal — otherwise a partial bowl_water
        // count (e.g., fillBowls timed out at 12/14) would drive takeRawMeat to under-fetch.
        if (has(ItemID.BOWL_EMPTY))                                                     { fillBowls(); return; }
        // Only advance to the raw-meat phase with a full batch of bowl_water. With fewer (e.g., burns
        // left some unused last round), fall through to the bowl chain to top up to BATCH_SIZE rather
        // than running a tiny 2-stew loop with all its withdraw/cook/combine round-trips.
        if (count(ItemID.BOWL_WATER) >= BATCH_SIZE && !has(ItemID.HOSIDIUS_SERVERY_RAW_MEAT))    { takeRawMeat(BATCH_SIZE); return; }

        // Fresh batch — top up to BATCH_SIZE accounting for any leftover items in the chain.
        int leftover = count(ItemID.BOWL_WATER) + count(ItemID.HOSIDIUS_SERVERY_MEATWATER) + count(ItemID.HOSIDIUS_SERVERY_UNCOOKED_STEW);
        int needed = Math.max(1, BATCH_SIZE - leftover);
        withdrawFromUtensil(ItemID.BOWL_EMPTY, needed);
    }

    private void meatPieTick() {
        // Consume-to-zero phases (cooking) take priority over any combine that uses their output.
        // Withdrawal targets size to predecessor counts so burn-induced leftovers (extra shells)
        // carry into the next loop and the chain tops up rather than over-fetching.
        if (has(ItemID.HOSIDIUS_SERVERY_MEAT_PIE))                                                   { serveAndMaybeHop(APPRECIATION_BAR_PIE); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_UNCOOKED_MEAT_PIE))                                          { cookOnOven(ItemID.HOSIDIUS_SERVERY_UNCOOKED_MEAT_PIE); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_RAW_MEAT))                                                   { cookOnOven(ItemID.HOSIDIUS_SERVERY_RAW_MEAT); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PIE_SHELL) && has(ItemID.HOSIDIUS_SERVERY_COOKED_MEAT))      { combineAll(ItemID.HOSIDIUS_SERVERY_PIE_SHELL, ItemID.HOSIDIUS_SERVERY_COOKED_MEAT, "Filling pies"); return; }
        // Return bowls only when shells are at full batch (post-combine state of piedish+dough).
        // Before that, any BOWL_EMPTY in inventory is feedstock for the dough chain — returning
        // it would infinite-loop (withdraw → return → withdraw...).
        if (count(ItemID.HOSIDIUS_SERVERY_PIE_SHELL) >= BATCH_SIZE && has(ItemID.BOWL_EMPTY))       { returnEmptyBowls(); return; }
        // Only proceed to raw-meat phase when we have a full batch of shells. With fewer shells
        // (e.g., burns left some unused), fall through to the dough chain to top up to BATCH_SIZE.
        if (count(ItemID.HOSIDIUS_SERVERY_PIE_SHELL) >= BATCH_SIZE)                                  { takeRawMeat(BATCH_SIZE); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PIEDISH) && has(ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH))       { combineAll(ItemID.HOSIDIUS_SERVERY_PIEDISH, ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH, "Forming shells"); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH) && has(ItemID.BOWL_EMPTY))                     { returnEmptyBowls(); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH) && !has(ItemID.HOSIDIUS_SERVERY_PIEDISH))      { withdrawFromUtensil(ItemID.HOSIDIUS_SERVERY_PIEDISH, count(ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH)); return; }
        if (has(ItemID.BOWL_WATER) && has(ItemID.HOSIDIUS_SERVERY_POT_FLOUR))                        { combineDoughDialog(ItemID.BOWL_WATER, ItemID.HOSIDIUS_SERVERY_POT_FLOUR, "Pastry dough"); return; }
        if (has(ItemID.BOWL_EMPTY) && !has(ItemID.HOSIDIUS_SERVERY_POT_FLOUR))                       { withdrawFromFood(ItemID.HOSIDIUS_SERVERY_POT_FLOUR, count(ItemID.BOWL_EMPTY)); return; }
        if (has(ItemID.BOWL_EMPTY))                                                                  { fillBowls(); return; }

        // Fresh batch — top up to BATCH_SIZE accounting for any leftover items in the chain.
        int leftover = count(ItemID.HOSIDIUS_SERVERY_PIE_SHELL)
                + count(ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH)
                + count(ItemID.BOWL_WATER);
        int needed = Math.max(1, BATCH_SIZE - leftover);
        withdrawFromUtensil(ItemID.BOWL_EMPTY, needed);
    }

    private void pizzaTick() {
        // Consume-to-zero phases (cooking) take priority over any combine that uses their output.
        // Withdrawal targets size to predecessor counts so leftovers from burns carry into the next batch.
        if (has(ItemID.HOSIDIUS_SERVERY_PINEAPPLE_PIZZA))                                                 { serveAndMaybeHop(APPRECIATION_BAR_PIZZA); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_UNCOOKED_PIZZA))                                                  { cookOnOven(ItemID.HOSIDIUS_SERVERY_UNCOOKED_PIZZA); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA) && has(ItemID.HOSIDIUS_SERVERY_PINEAPPLE_CHUNKS))    { combineAll(ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA, ItemID.HOSIDIUS_SERVERY_PINEAPPLE_CHUNKS, "Adding pineapple"); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PINEAPPLE) && has(ItemID.KNIFE))                                  { combineAll(ItemID.KNIFE, ItemID.HOSIDIUS_SERVERY_PINEAPPLE, "Cutting pineapple"); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA) && has(ItemID.HOSIDIUS_SERVERY_CHEESE))         { combineAll(ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA, ItemID.HOSIDIUS_SERVERY_CHEESE, "Adding cheese"); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA) && !has(ItemID.HOSIDIUS_SERVERY_CHEESE))        { withdrawFromFood(ItemID.HOSIDIUS_SERVERY_CHEESE, count(ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA)); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PIZZA_BASE) && has(ItemID.HOSIDIUS_SERVERY_TOMATO))               { combineAll(ItemID.HOSIDIUS_SERVERY_PIZZA_BASE, ItemID.HOSIDIUS_SERVERY_TOMATO, "Adding tomato"); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PIZZA_BASE) && !has(ItemID.HOSIDIUS_SERVERY_TOMATO))              { withdrawFromFood(ItemID.HOSIDIUS_SERVERY_TOMATO, count(ItemID.HOSIDIUS_SERVERY_PIZZA_BASE)); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA) && !has(ItemID.HOSIDIUS_SERVERY_PINEAPPLE_CHUNKS) && !has(ItemID.HOSIDIUS_SERVERY_PINEAPPLE)) { withdrawFromFood(ItemID.HOSIDIUS_SERVERY_PINEAPPLE, count(ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA)); return; }
        if (has(ItemID.HOSIDIUS_SERVERY_PIZZA_BASE) && has(ItemID.BOWL_EMPTY))                            { returnEmptyBowls(); return; }
        if (has(ItemID.BOWL_WATER) && has(ItemID.HOSIDIUS_SERVERY_POT_FLOUR))                             { combineDoughDialog(ItemID.BOWL_WATER, ItemID.HOSIDIUS_SERVERY_POT_FLOUR, "Pizza base"); return; }
        if (has(ItemID.BOWL_EMPTY) && !has(ItemID.HOSIDIUS_SERVERY_POT_FLOUR))                            { withdrawFromFood(ItemID.HOSIDIUS_SERVERY_POT_FLOUR, count(ItemID.BOWL_EMPTY)); return; }
        if (has(ItemID.BOWL_EMPTY))                                                                       { fillBowls(); return; }
        if (count(ItemID.KNIFE) < 2)                                                                      { withdrawFromUtensil(ItemID.KNIFE, 2); return; }

        // Fresh batch — top up to PIZZA_BATCH_SIZE accounting for any leftovers in the chain.
        int leftover = count(ItemID.BOWL_WATER)
                + count(ItemID.HOSIDIUS_SERVERY_PIZZA_BASE)
                + count(ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA)
                + count(ItemID.HOSIDIUS_SERVERY_UNCOOKED_PIZZA)
                + count(ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA);
        int needed = Math.max(1, PIZZA_BATCH_SIZE - leftover);
        withdrawFromUtensil(ItemID.BOWL_EMPTY, needed);
    }

    // ---------- step handlers ----------

    private void walkToMess() {
        setStatus("Walking to Mess");
        Rs2Walker.walkTo(MESS_HUB, 4);
    }

    private boolean inMess() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Widget.getWidget(InterfaceID.HosidiusServeryHud.CONTENT) != null
        ).orElse(false);
    }

    private boolean withdrawFromUtensil(int itemId, int targetCount) { return withdrawFromCupboard(UTENSIL_CUPBOARD_LOC, itemId, targetCount); }
    private boolean withdrawFromFood(int itemId, int targetCount)    { return withdrawFromCupboard(FOOD_CUPBOARD_LOC,    itemId, targetCount); }

    private boolean withdrawFromCupboard(WorldPoint loc, int itemId, int targetCount) {
        int have = count(itemId);
        if (have >= targetCount) return true;

        int free = 28 - Rs2Inventory.count();
        int need = Math.min(targetCount - have, free);
        if (need <= 0) {
            log.warn("[mess] withdraw blocked: itemId={} target={} have={} free=0", itemId, targetCount, have);
            return false;
        }

        logDecision("withdraw[loc=" + loc + ",itemId=" + itemId + ",need=" + need + "]");
        setStatus("Getting supplies");

        if (!Rs2GameObject.canReach(loc)) { log.info("[mess] walking to cupboard {}", loc); Rs2Walker.walkTo(loc, 4); return false; }

        if (!Rs2Widget.isWidgetVisible(SHOP_WIDGET_ID)) {
            // Exact-tile interact: cupboards at (1644,3624) and (1645,3623) are 1 tile apart, so a within(loc,1)
            // query catches both and may interact with the wrong one. findObjectByLocation matches the exact tile.
            boolean interacted = Rs2GameObject.interact(loc, "Search");
            log.info("[mess] cupboard interact(Search) at {} -> {}", loc, interacted);
            if (!interacted) return false;
            if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(SHOP_WIDGET_ID), 3000)) {
                log.warn("[mess] shop widget never appeared after Search at {}", loc);
                return false;
            }
        }

        Widget shop = Rs2Widget.getWidget(SHOP_WIDGET_ID);
        Widget[] children = shop != null ? shop.getDynamicChildren() : null;
        if (children == null) { log.warn("[mess] shop widget has no children"); closeShop(); return false; }

        int idx = -1;
        for (int i = 0; i < children.length; i++) {
            if (children[i].getItemId() == itemId) { idx = i; break; }
        }
        if (idx < 0) {
            // Help diagnose mis-targeted cupboard / wrong item ID by logging what's actually on offer.
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(children.length, 16); i++) {
                if (children[i].getItemId() > 0) sb.append(children[i].getItemId()).append(',');
            }
            log.warn("[mess] item {} not in cupboard at {}; items found: [{}]", itemId, loc, sb);
            closeShop();
            return false;
        }

        log.info("[mess] clicking shop slot {} for itemId {} (qty {})", idx, itemId, need);
        Rs2Widget.clickWidgetFast(children[idx], idx, 5);
        if (!sleepUntil(() -> Rs2Widget.hasWidget("Enter amount"), 2000)) { log.warn("[mess] Enter amount widget never appeared"); closeShop(); return false; }

        // Mirror Rs2GrandExchange.setQuantity timing: the chatbox input doesn't reliably
        // accept the value if you set it the same tick the widget appeared — small qty
        // (e.g., 2) silently drops the value, large qty happens to race past the issue.
        // typeString is unsafe here too: KEY_TYPED routes to canvas if the chatbox
        // hasn't focused, leaking digits into game chat. Direct VarClientStr write +
        // ~1s of sleep around it matches the pattern that works in GE.
        sleep(600);
        setChatboxAmount(need);
        sleep(400);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);

        boolean got = sleepUntil(() -> count(itemId) >= targetCount, 4000);
        log.info("[mess] withdraw result for itemId {}: have={} target={} ok={}", itemId, count(itemId), targetCount, got);
        closeShop();
        return got;
    }

    private boolean takeRawMeat(int targetCount) {
        int have = count(ItemID.HOSIDIUS_SERVERY_RAW_MEAT);
        if (have >= targetCount) return true;

        int free = 28 - Rs2Inventory.count();
        int need = Math.min(targetCount - have, free);
        if (need <= 0) return false;

        logDecision("takeRawMeat[need=" + need + "]");
        setStatus("Taking raw meat");
        if (!Rs2GameObject.canReach(MEAT_TABLE_LOC)) { Rs2Walker.walkTo(MEAT_TABLE_LOC, 4); return false; }

        boolean ok = Rs2GameObject.interact(MEAT_TABLE_LOC, "Take-X");
        log.info("[mess] meat-table interact(Take-X) -> {}", ok);
        if (!ok) return false;
        if (!sleepUntil(() -> Rs2Widget.hasWidget("Enter amount"), 2000)) { log.warn("[mess] Enter amount widget never appeared for raw meat"); return false; }

        // See withdrawFromCupboard for the timing rationale — small-qty values race past the input.
        sleep(600);
        setChatboxAmount(need);
        sleep(400);
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        return sleepUntil(() -> count(ItemID.HOSIDIUS_SERVERY_RAW_MEAT) >= targetCount, 4000);
    }

    private boolean fillBowls() {
        if (!has(ItemID.BOWL_EMPTY)) return true;
        logDecision("fillBowls");
        setStatus("Filling bowls");
        if (!Rs2GameObject.canReach(SINK_LOC)) { Rs2Walker.walkTo(SINK_LOC, 4); return false; }

        TileObject sink = Rs2GameObject.findObjectByLocation(SINK_LOC);
        if (sink == null) { log.warn("[mess] sink not found at {}", SINK_LOC); return false; }
        boolean used = Rs2Inventory.useItemOnObject(ItemID.BOWL_EMPTY, sink.getId());
        log.info("[mess] use bowl on sink id={} -> {}", sink.getId(), used);
        return sleepUntil(() -> !has(ItemID.BOWL_EMPTY), 15000);
    }

    private boolean returnEmptyBowls() {
        if (!has(ItemID.BOWL_EMPTY)) return true;
        logDecision("returnEmptyBowls");
        setStatus("Returning empty bowls");
        if (!Rs2GameObject.canReach(UTENSIL_CUPBOARD_LOC)) { Rs2Walker.walkTo(UTENSIL_CUPBOARD_LOC, 4); return false; }

        TileObject cupboard = Rs2GameObject.findObjectByLocation(UTENSIL_CUPBOARD_LOC);
        if (cupboard == null) { log.warn("[mess] utensil cupboard not found at {}", UTENSIL_CUPBOARD_LOC); return false; }
        boolean used = Rs2Inventory.useItemOnObject(ItemID.BOWL_EMPTY, cupboard.getId());
        log.info("[mess] return bowl on cupboard id={} -> {}", cupboard.getId(), used);
        return sleepUntil(() -> !has(ItemID.BOWL_EMPTY), 8000);
    }

    /**
     * Cook all of {@code rawId} on the clay oven in one phase: issue the cook
     * (Make-All via SPACE on the production widget), then BLOCK until the raw
     * ingredient is fully consumed. Returning mid-chain lets the dispatcher
     * pick a different guard (e.g., a combine that depends on the partial
     * cooked-meat output), interrupting cooking — this is what the user
     * explicitly forbids.
     */
    private boolean cookOnOven(int rawId) {
        if (!has(rawId)) return true;
        logDecision("cookOnOven[rawId=" + rawId + "]");
        setStatus("Cooking");
        if (!Rs2GameObject.canReach(CLAY_OVEN_LOC)) { Rs2Walker.walkTo(CLAY_OVEN_LOC, 4); return false; }

        int before = count(rawId);

        if (Rs2Widget.isProductionWidgetOpen()) {
            // Dialog already up from a previous tick — confirm Cook-All.
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        } else {
            TileObject oven = Rs2GameObject.findObjectByLocation(CLAY_OVEN_LOC);
            if (oven == null) { log.warn("[mess] clay oven not found at {}", CLAY_OVEN_LOC); return false; }
            boolean used = Rs2Inventory.useItemOnObject(rawId, oven.getId());
            log.info("[mess] use {} on oven id={} -> {}", rawId, oven.getId(), used);
            if (sleepUntil(Rs2Widget::isProductionWidgetOpen, 2500)) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            }
        }

        // Block until raw is fully consumed. Per-cook ~3 ticks (~1.8s), so an
        // 8s stall guard is generous; hard cap 90s covers a 14-batch comfortably.
        boolean done = waitForDepleted(rawId, before, 8000);
        log.info("[mess] cookOnOven id={} done={} ({}->{})", rawId, done, before, count(rawId));
        return true;
    }

    /** Set the chatbox numeric input field directly. Avoids focus-routing race in typeString. */
    private static void setChatboxAmount(int amount) {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget input = Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2);
            if (input != null) {
                input.setText(amount + "*");
            }
            Microbot.getClient().setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(amount));
            return null;
        });
    }

    /**
     * Combine two items with Make-All semantics.
     * <p>
     * Issues one combine, presses SPACE on the Make-X production widget, then
     * waits for one ingredient to deplete. Combine actions in this minigame
     * have no sustained player animation (the bowl/pot/dish recipes are pure
     * inventory transformations), so we cannot use {@code isAnimating} to
     * detect the chain — we wait on {@link #count} change with a stall guard.
     */
    private boolean combineAll(int item1, int item2, String statusText) {
        if (!has(item1) || !has(item2)) return true;
        logDecision("combineAll[" + item1 + "+" + item2 + "]");
        setStatus(statusText);
        int before1 = count(item1);
        int before2 = count(item2);

        Rs2Inventory.combine(item1, item2);
        if (sleepUntil(Rs2Widget::isProductionWidgetOpen, 1500)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }
        boolean done = waitForCombineChain(item1, item2, before1, before2);
        log.info("[mess] combineAll {} + {} -> done={} ({}->{} / {}->{})",
                item1, item2, done, before1, count(item1), before2, count(item2));
        return true;
    }

    /**
     * Bowl-water + pot-flour combine: first pops a "Select an option" chat
     * dialog (Pastry dough vs Pizza base), then a Make-X widget. Picks the
     * option, presses SPACE for All, then blocks for full chain completion.
     */
    private boolean combineDoughDialog(int item1, int item2, String dialogOption) {
        if (!has(item1) || !has(item2)) return true;
        logDecision("combineDough[" + dialogOption + "]");
        setStatus("Making " + dialogOption);
        int before1 = count(item1);
        int before2 = count(item2);

        Rs2Inventory.combine(item1, item2);
        if (Rs2Dialogue.sleepUntilSelectAnOption()) {
            Rs2Dialogue.keyPressForDialogueOption(dialogOption);
        }
        if (sleepUntil(Rs2Widget::isProductionWidgetOpen, 1500)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }
        boolean done = waitForCombineChain(item1, item2, before1, before2);
        log.info("[mess] combineDough {} + {} -> done={} (flour {}->{})",
                item1, item2, done, before2, count(item2));
        return true;
    }

    /**
     * Wait for a combine chain to consume an ingredient. Returns when either
     * {@code item1} or {@code item2} reaches 0. Stall guard: returns false if
     * the combined count hasn't dropped for 5s. Hard cap 60s.
     * <p>
     * Tracks the SUM of counts so any per-action depletion registers as
     * progress. A min-based guard breaks for combines where one ingredient
     * persists (e.g., knife+pineapple — knife stays at 2 while pineapple
     * counts down, so min stays pinned at 2 and the stall trips early).
     */
    private boolean waitForCombineChain(int item1, int item2, int before1, int before2) {
        long start = System.currentTimeMillis();
        int lastSum = before1 + before2;
        long lastChangeMs = start;
        while (System.currentTimeMillis() - start < 60_000) {
            int c1 = count(item1);
            int c2 = count(item2);
            if (c1 == 0 || c2 == 0) return true;
            int currentSum = c1 + c2;
            if (currentSum < lastSum) {
                lastSum = currentSum;
                lastChangeMs = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastChangeMs > 5000) {
                return false;
            }
            sleep(300);
        }
        return false;
    }

    /**
     * Wait for a single inventory item to be fully consumed (count → 0).
     * Stall guard {@code stallMs}: returns false if the count hasn't dropped
     * for that long. Hard cap 90s.
     */
    private boolean waitForDepleted(int itemId, int beforeCount, long stallMs) {
        long start = System.currentTimeMillis();
        int lastCount = beforeCount;
        long lastChangeMs = start;
        while (System.currentTimeMillis() - start < 90_000) {
            int now = count(itemId);
            if (now == 0) return true;
            if (now < lastCount) {
                lastCount = now;
                lastChangeMs = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastChangeMs > stallMs) {
                return false;
            }
            sleep(400);
        }
        return false;
    }

    /**
     * Hard-fail invariant: no chain item should ever exceed {@link #BATCH_SIZE}
     * (or {@link #PIZZA_BATCH_SIZE} for pizza). Exceeding it means our top-up
     * math overshot or a leftover state went unhandled — we'd start over-fetching
     * ingredients we'll never use, so stop loudly rather than waste resources.
     */
    private boolean violatesBatchInvariant() {
        int max = config.dish() == Dish.PIZZA ? PIZZA_BATCH_SIZE : BATCH_SIZE;
        for (int id : chainItemsForDish()) {
            int c = count(id);
            if (c > max) {
                log.error("[mess] BATCH INVARIANT VIOLATED: itemId={} count={} > max={} for dish={}",
                        id, c, max, config.dish());
                return true;
            }
        }
        return false;
    }

    private int[] chainItemsForDish() {
        switch (config.dish()) {
            case STEW: return new int[] {
                    ItemID.BOWL_EMPTY, ItemID.BOWL_WATER,
                    ItemID.HOSIDIUS_SERVERY_RAW_MEAT, ItemID.HOSIDIUS_SERVERY_COOKED_MEAT,
                    ItemID.HOSIDIUS_SERVERY_MEATWATER, ItemID.HOSIDIUS_SERVERY_POTATO,
                    ItemID.HOSIDIUS_SERVERY_UNCOOKED_STEW, ItemID.HOSIDIUS_SERVERY_STEW,
            };
            case MEAT_PIE: return new int[] {
                    ItemID.BOWL_EMPTY, ItemID.BOWL_WATER,
                    ItemID.HOSIDIUS_SERVERY_POT_FLOUR, ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH,
                    ItemID.HOSIDIUS_SERVERY_PIEDISH, ItemID.HOSIDIUS_SERVERY_PIE_SHELL,
                    ItemID.HOSIDIUS_SERVERY_RAW_MEAT, ItemID.HOSIDIUS_SERVERY_COOKED_MEAT,
                    ItemID.HOSIDIUS_SERVERY_UNCOOKED_MEAT_PIE, ItemID.HOSIDIUS_SERVERY_MEAT_PIE,
            };
            case PIZZA: return new int[] {
                    ItemID.BOWL_EMPTY, ItemID.BOWL_WATER,
                    ItemID.HOSIDIUS_SERVERY_POT_FLOUR, ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH,
                    ItemID.HOSIDIUS_SERVERY_PIZZA_BASE, ItemID.HOSIDIUS_SERVERY_TOMATO,
                    ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA, ItemID.HOSIDIUS_SERVERY_CHEESE,
                    ItemID.HOSIDIUS_SERVERY_UNCOOKED_PIZZA, ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA,
                    ItemID.HOSIDIUS_SERVERY_PINEAPPLE, ItemID.HOSIDIUS_SERVERY_PINEAPPLE_CHUNKS,
                    ItemID.HOSIDIUS_SERVERY_PINEAPPLE_PIZZA,
            };
        }
        return new int[0];
    }

    /**
     * Drop any burnt food, but only between phases — never mid-cook.
     * Dropping during a Cook-All chain interrupts the player's cooking animation
     * and breaks the chain, so we gate on "not currently cooking."
     */
    private void dropBurnt() {
        if (!has(ItemID.BURNT_MEAT) && !has(ItemID.BURNT_PIE) && !has(ItemID.BURNT_STEW) && !has(ItemID.BURNT_PIZZA)) return;
        if (Rs2Player.isAnimating(3500) || Rs2Widget.isProductionWidgetOpen()) return;
        log.info("[mess] dropping burnt food");
        Rs2Inventory.dropAll(ItemID.BURNT_MEAT, ItemID.BURNT_PIE, ItemID.BURNT_STEW, ItemID.BURNT_PIZZA);
    }

    private void serveAndMaybeHop(int appreciationWidgetId) {
        if (isUnderAppreciationThreshold(appreciationWidgetId)) {
            hopWorld();
            return;
        }
        if (!Rs2GameObject.canReach(BUFFET_TABLE_LOC)) { Rs2Walker.walkTo(BUFFET_TABLE_LOC, 4); return; }

        logDecision("serve");
        setStatus("Serving");
        boolean ok = Rs2GameObject.interact(BUFFET_TABLE_LOC, "Serve");
        log.info("[mess] buffet interact(Serve) -> {}", ok);
        Rs2Player.waitForXpDrop(Skill.COOKING, 2500, false);
    }

    private boolean isUnderAppreciationThreshold(int widgetId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget bar = Rs2Widget.getWidget(widgetId);
            if (bar == null) return false;
            Widget filled = bar.getChild(0);
            if (filled == null || bar.getWidth() <= 0) return false;
            int pct = filled.getWidth() * 100 / bar.getWidth();
            return pct < config.appreciation_threshold();
        }).orElse(false);
    }

    private void hopWorld() {
        setStatus("Hopping worlds");
        int currentWorld = Microbot.getClient().getWorld();
        Microbot.hopToWorld(Login.getRandomWorld(true));
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING, 5000);
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 15000);
        if (Microbot.getClient().getWorld() == currentWorld) {
            log.warn("World hop failed; will retry next tick.");
        }
    }

    // ---------- inventory cleanup ----------

    private static final Set<Integer> JUNK_ITEMS = Set.of(
            ItemID.BOWL_EMPTY, ItemID.BOWL_WATER, ItemID.KNIFE,
            ItemID.HOSIDIUS_SERVERY_PIEDISH,
            ItemID.BURNT_PIZZA, ItemID.BURNT_PIE, ItemID.BURNT_STEW, ItemID.BURNT_MEAT,
            ItemID.HOSIDIUS_SERVERY_RAW_MEAT, ItemID.HOSIDIUS_SERVERY_COOKED_MEAT,
            ItemID.HOSIDIUS_SERVERY_PINEAPPLE, ItemID.HOSIDIUS_SERVERY_PINEAPPLE_CHUNKS,
            ItemID.HOSIDIUS_SERVERY_TOMATO, ItemID.HOSIDIUS_SERVERY_CHEESE, ItemID.HOSIDIUS_SERVERY_POTATO,
            ItemID.HOSIDIUS_SERVERY_POT_FLOUR, ItemID.HOSIDIUS_SERVERY_PASTRY_DOUGH,
            ItemID.HOSIDIUS_SERVERY_PIZZA_BASE, ItemID.HOSIDIUS_SERVERY_INCOMPLETE_PIZZA,
            ItemID.HOSIDIUS_SERVERY_PLAIN_PIZZA, ItemID.HOSIDIUS_SERVERY_PIE_SHELL,
            ItemID.HOSIDIUS_SERVERY_UNCOOKED_MEAT_PIE, ItemID.HOSIDIUS_SERVERY_UNCOOKED_STEW,
            ItemID.HOSIDIUS_SERVERY_UNCOOKED_PIZZA, ItemID.HOSIDIUS_SERVERY_MEATWATER,
            // Cooked dishes — drop these too rather than walking to the bank with them
            ItemID.HOSIDIUS_SERVERY_MEAT_PIE, ItemID.HOSIDIUS_SERVERY_STEW, ItemID.HOSIDIUS_SERVERY_PINEAPPLE_PIZZA
    );

    /** Inventory has any item that isn't (a) currently mid-batch (handled by tick predicates) or (b) reserved for the active dish. */
    private boolean hasJunk() {
        if (Rs2Inventory.isEmpty()) return false;
        // The per-dish flow handles every Hosidius/bowl/knife item. Junk = anything outside JUNK_ITEMS that we shouldn't process.
        // Conversely, if everything in inventory IS in JUNK_ITEMS we don't need to clean — let the dish flow consume it.
        // We only clean when there's a non-Mess item that the script can't progress with.
        return Rs2Inventory.items().anyMatch(item -> !JUNK_ITEMS.contains(item.getId()));
    }

    private void runCleanInventory() {
        setStatus("Cleaning inventory");
        // Drop everything we recognize first (faster than banking).
        int dropped = 0;
        for (int slot = 0; slot < 28; slot++) {
            if (Rs2Inventory.isSlotEmpty(slot)) continue;
            Rs2ItemModel item = Rs2Inventory.getItemInSlot(slot);
            if (item != null && JUNK_ITEMS.contains(item.getId())) {
                Rs2Inventory.slotInteract(slot, "Drop");
                sleepGaussian(120, 40);
                dropped++;
            }
        }
        if (dropped > 0) return;

        // Anything left is unrecognized. Bank it.
        if (Rs2Inventory.isEmpty()) return;
        log.info("Banking {} unrecognized items at Hosidius Kitchen.", Rs2Inventory.count());
        Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                Rs2Inventory.items().map(Rs2ItemModel::getName).collect(Collectors.toList()),
                false,
                BankLocation.HOSIDIUS_KITCHEN,
                Rs2Player.getWorldLocation(),
                28,
                3
        );
    }

    // ---------- shop close ----------

    private void closeShop() {
        if (!Rs2Widget.isWidgetVisible(SHOP_WIDGET_ID)) return;
        Widget closeParent = Rs2Widget.getWidget(SHOP_CLOSE_PARENT);
        Widget[] kids = closeParent != null ? closeParent.getChildren() : null;
        if (kids != null && kids.length > 0) {
            Rs2Widget.clickWidget(kids[kids.length - 1]);
        } else {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }
        sleepUntil(() -> !Rs2Widget.isWidgetVisible(SHOP_WIDGET_ID), 1500);
    }

    // ---------- helpers ----------

    private boolean has(int id) { return Rs2Inventory.hasItem(id); }
    private int count(int id)   { return Rs2Inventory.count(id); }

    private void setStatus(String s) {
        if (overlay != null) overlay.setStatus(s);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
    }

    public enum Dish {
        MEAT_PIE("Servery Meat Pie"),
        STEW("Servery Stew"),
        PIZZA("Servery Pineapple Pizza");

        @Getter private final String name;
        Dish(String name) { this.name = name; }
    }
}
