package net.runelite.client.plugins.microbot.leaguestoolkit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;

import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class Transmuter {

    @Getter
    private String status = "Idle";
    @Getter
    private boolean running = false;

    // Track state across ticks instead of blocking
    private boolean casting = false;
    private long lastCastTime = 0;
    private int lastItemCount = 0;
    private String currentItemName = null;

    // If item count hasn't changed for this long AND shop is closed, re-cast.
    // Auto-recast processes 10 items per batch with gaps between, so be patient.
    private static final long RECAST_TIMEOUT_MS = 45_000;
    // When shop is open, auto-recast pauses entirely — use a much longer timeout
    private static final long RECAST_TIMEOUT_SHOP_OPEN_MS = 180_000;

    public void reset() {
        status = "Idle";
        running = false;
        casting = false;
        lastCastTime = 0;
        lastItemCount = 0;
        currentItemName = null;
    }

    /**
     * Run one tick of the transmute loop.
     * Returns false if the feature should be disabled (done or error).
     */
    public boolean tick(LeaguesToolkitConfig config) {
        running = true;
        TransmuteItem startEnum = config.transmuteStartItem();
        TransmuteItem targetEnum = config.transmuteTargetItem();
        TransmuteDirection direction = config.transmuteDirection();

        if (startEnum == null || targetEnum == null) {
            status = "Config incomplete";
            return false;
        }

        if (startEnum.getCategory() != targetEnum.getCategory()) {
            status = "Start and target must be in the same category ("
                    + startEnum.getCategory().getDisplayName() + " vs "
                    + targetEnum.getCategory().getDisplayName() + ")";
            return false;
        }

        TransmuteCategory category = startEnum.getCategory();
        String startItem = startEnum.getItemName();
        String targetItem = targetEnum.getItemName();
        List<String> chain = category.getChain();
        int startIdx = category.indexOf(startItem);
        int targetIdx = category.indexOf(targetItem);

        if (startIdx == -1 || targetIdx == -1) {
            status = "Item not found in " + category.getDisplayName() + " chain";
            return false;
        }

        // Determine direction based on chain positions — the user picks upgrade/downgrade
        // to select the spell, but we walk the chain in whichever direction goes from start to target
        int step = (targetIdx > startIdx) ? 1 : -1;
        int currentIdx = findCurrentTier(chain, startIdx, targetIdx, step);

        // Check if we're done: target exists AND no intermediate items remain
        if (Rs2Inventory.hasItem(targetItem)) {
            boolean intermediatesRemain = false;
            for (int i = startIdx; i != targetIdx; i += step) {
                if (i < 0 || i >= chain.size()) break;
                if (Rs2Inventory.hasItem(chain.get(i))) {
                    intermediatesRemain = true;
                    break;
                }
            }
            if (!intermediatesRemain) {
                status = "Done — all items are now " + targetItem;
                running = false;
                casting = false;
                return false;
            }
            // Target exists but intermediates remain — keep going
        }

        if (currentIdx == -1) {
            // Log what we searched for to debug
            log.info("[Transmuter] No items found. Searched chain indices {} to {} (step {}):", startIdx, targetIdx, step);
            for (int i = startIdx; i != targetIdx + step; i += step) {
                if (i < 0 || i >= chain.size()) break;
                String name = chain.get(i);
                boolean has = Rs2Inventory.hasItem(name);
                log.info("[Transmuter]   {} = {}", name, has);
            }
            status = "No transmutable items found in inventory";
            running = false;
            casting = false;
            return false;
        }

        String currentItem = chain.get(currentIdx);
        String nextItem = chain.get(currentIdx + step);

        // Are we mid-cast and the item changed? Advance.
        if (casting && currentItemName != null && !currentItemName.equals(currentItem)) {
            log.info("Tier advanced: {} → {}", currentItemName, currentItem);
            casting = false;
            currentItemName = null;
        }

        // Check if auto-recast is still running
        if (casting) {
            int currentCount = Rs2Inventory.count(currentItem);

            if (currentCount == 0) {
                // All items transmuted — next tick will pick up new tier
                casting = false;
                currentItemName = null;
                status = "Tier complete → " + nextItem;
                return true;
            }

            // Check if count is still decreasing (auto-recast active)
            if (currentCount < lastItemCount) {
                lastItemCount = currentCount;
                lastCastTime = System.currentTimeMillis();
                status = "Auto-recasting " + currentItem + " → " + nextItem + " (" + currentCount + " left)";
                return true;
            }

            // Count hasn't changed — check if we've timed out (recast interrupted)
            // Shop windows pause auto-recast, so use a longer timeout when shop is open
            long elapsed = System.currentTimeMillis() - lastCastTime;
            long timeout = Rs2Shop.isOpen() ? RECAST_TIMEOUT_SHOP_OPEN_MS : RECAST_TIMEOUT_MS;

            if (elapsed < timeout) {
                String reason = Rs2Shop.isOpen() ? " (shop open — recast paused)" : "";
                status = "Waiting for recast... " + currentItem + " (" + currentCount + " left)" + reason;
                return true;
            }

            // Timed out — recast got interrupted, try again
            log.info("Auto-recast interrupted for {} (shop open: {}), re-casting", currentItem, Rs2Shop.isOpen());
            casting = false;
        }

        // Cast the spell on the current tier item
        // In Leagues, High Alch = "Alchemic Divergence", Low Alch = "Alchemic Convergence"
        String spellName = direction == TransmuteDirection.UPGRADE
                ? "Alchemic Divergence"
                : "Alchemic Convergence";
        status = "Casting " + spellName + " on " + currentItem + " → " + nextItem;

        Rs2ItemModel item = Rs2Inventory.get(currentItem);
        if (item == null) {
            status = "Lost " + currentItem + " from inventory";
            return true;
        }

        // Switch to magic tab and click the spell by name
        Rs2Tab.switchToMagicTab();
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC).orElse(false));
        sleep(200, 400);

        if (!Rs2Widget.clickWidget(spellName)) {
            status = "Could not find " + spellName + " spell — do you have the Transmutation relic?";
            return true;
        }

        // Wait for inventory tab to appear, then click the item
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false));
        sleep(300, 500);
        Rs2Inventory.interact(item, "Cast");
        sleep(600, 900);

        // Switch back to inventory tab so we can monitor item count changes
        Rs2Tab.switchToInventoryTab();
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(
                () -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false));

        // Mark as casting — subsequent ticks will monitor progress
        casting = true;
        currentItemName = currentItem;
        lastItemCount = Rs2Inventory.count(currentItem);
        lastCastTime = System.currentTimeMillis();
        status = "Auto-recasting " + currentItem + " → " + nextItem + " (" + lastItemCount + " remaining)";

        return true;
    }

    /**
     * Finds which tier item is currently in the inventory.
     * Searches the entire chain (not just start→target) in case a previous
     * transmutation went in an unexpected direction.
     */
    private int findCurrentTier(List<String> chain, int startIdx, int targetIdx, int step) {
        // First: search from start toward target (expected path)
        for (int i = startIdx; i != targetIdx + step; i += step) {
            if (i < 0 || i >= chain.size()) break;
            if (Rs2Inventory.hasItem(chain.get(i))) return i;
        }
        // Fallback: search the entire chain for any matching item
        for (int i = 0; i < chain.size(); i++) {
            if (Rs2Inventory.hasItem(chain.get(i))) return i;
        }
        return -1;
    }
}
