package net.runelite.client.plugins.microbot.droelf;


import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@PluginDescriptor(
        name = PluginConstants.DRO + "Elf",
        description = "Dedicated Prifddinas elf thieving with a session-locked route",
        authors = {"Dro"},
        version = DroElfPlugin.VERSION,
        minClientVersion = "2.6.55",
        tags = {"dro", "thieving", "elves", "elf", "prifddinas", "microbot"},
        iconUrl = "https://chsami.github.io/Microbot-Hub/DroElfPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/DroElfPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DroElfPlugin extends Plugin {
    static final String VERSION = "1.0.3";

    @Inject
    private Client client;
    @Inject
    private ItemManager itemManager;
    @Inject
    private DroElfScript script;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private DroElfOverlay overlay;

    private volatile long sessionStartedAt;
    private volatile int startingThievingXp = -1;
    private volatile int currentThievingXp = -1;
    private volatile long totalProfit;
    private Map<Integer, Integer> previousInventory = Collections.emptyMap();

    @Provides
    DroElfConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DroElfConfig.class);
    }

    @Override
    protected void startUp() {
        resetOverlayStats();

        // Register and start immediately. Nothing below is allowed to abort startup.
        overlayManager.add(overlay);
        script.run();

        // Client#getItemContainer and getSkillExperience are client-thread-only in
        // this RuneLite/Microbot build. Queue the initial overlay baseline instead
        // of calling them directly from the Swing/AWT plugin-start thread.
        Microbot.getClientThread().invoke(() -> {
            try {
                if (client.getGameState() != GameState.LOGGED_IN) {
                    return;
                }

                onThievingXpChanged(client.getSkillExperience(Skill.THIEVING));
                ItemContainer inventory = client.getItemContainer(InventoryID.INV);
                if (inventory != null) {
                    previousInventory = snapshot(inventory);
                }
            } catch (Exception ex) {
                // Overlay baseline is optional. Script/overlay startup must survive.
                log.debug("[DroElf] Initial overlay baseline skipped", ex);
            }
        });
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        script.onChatMessage(event);
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.THIEVING) {
            onThievingXpChanged(event.getXp());
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INV || event.getItemContainer() == null) {
            return;
        }

        try {
            Map<Integer, Integer> now = snapshot(event.getItemContainer());
            if (previousInventory.isEmpty()) {
                previousInventory = now;
                return;
            }

            if (shouldCountPositiveInventoryGain()) {
                long gainedValue = 0L;
                for (Map.Entry<Integer, Integer> entry : now.entrySet()) {
                    int gained = entry.getValue() - previousInventory.getOrDefault(entry.getKey(), 0);
                    if (gained <= 0) continue;

                    int itemId = entry.getKey();
                    long unitPrice = itemId == 995 ? 1 : Math.max(0, itemManager.getItemPrice(itemId));
                    gainedValue += (long) gained * unitPrice;
                }
                totalProfit += gainedValue;
            }

            previousInventory = now;
        } catch (Exception ex) {
            // Statistics must never interfere with thieving.
            log.debug("[DroElf] Profit event skipped", ex);
        }
    }

    private void resetOverlayStats() {
        sessionStartedAt = System.currentTimeMillis();
        startingThievingXp = -1;
        currentThievingXp = -1;
        totalProfit = 0L;
        previousInventory = Collections.emptyMap();
    }

    private void onThievingXpChanged(int xp) {
        if (xp < 0) return;
        if (startingThievingXp < 0 || (currentThievingXp >= 0 && xp < currentThievingXp)) {
            startingThievingXp = xp;
        }
        currentThievingXp = xp;
    }

    private boolean shouldCountPositiveInventoryGain() {
        String status = script.getStatus();
        if (status == null) return false;
        String lower = status.toLowerCase();

        if (lower.contains("bank")
                || lower.contains("drink")
                || lower.contains("equip")
                || lower.contains("making inventory space")) {
            return false;
        }

        return !"Not locked".equalsIgnoreCase(script.getLockedElfDisplay());
    }

    private static Map<Integer, Integer> snapshot(ItemContainer inventory) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Item item : inventory.getItems()) {
            if (item == null || item.getId() < 0 || item.getQuantity() <= 0) continue;
            result.merge(item.getId(), item.getQuantity(), Integer::sum);
        }
        return result;
    }


    public String getScriptStatus() {
        String value = script.getStatus();
        return value == null || value.isBlank() ? "Starting" : value;
    }

    public String getLockedElfDisplay() {
        return script.getLockedElfDisplay();
    }

    public String getLockedBankDisplay() {
        return script.getLockedBankDisplay();
    }

    public long getSessionElapsedMs() {
        return sessionStartedAt <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - sessionStartedAt);
    }

    public boolean hasXpBaseline() {
        return startingThievingXp >= 0 && currentThievingXp >= 0;
    }

    public int getXpGained() {
        return hasXpBaseline() ? Math.max(0, currentThievingXp - startingThievingXp) : 0;
    }

    public long getXpPerHour() {
        long elapsed = getSessionElapsedMs();
        return elapsed <= 0L ? 0L : getXpGained() * 3_600_000L / elapsed;
    }

    public long getTotalProfit() {
        return Math.max(0L, totalProfit);
    }

    public long getProfitPerHour() {
        long elapsed = getSessionElapsedMs();
        return elapsed <= 0L ? 0L : getTotalProfit() * 3_600_000L / elapsed;
    }
}
