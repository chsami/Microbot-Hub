package net.runelite.client.plugins.microbot.slayer.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.slayer.SlayerConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.item.Rs2ExplorersRing;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Handles high alching for slayer combat.
 * Only alchs items that are explicitly on the whitelist.
 */
@Slf4j
public class SlayerHighAlchScript extends Script {

    // Randomized interval between alchs (in ticks)
    private static final int MIN_TICKS = (int) Math.ceil(30.0 / 0.6);
    private static final int MAX_TICKS = (int) Math.floor(45.0 / 0.6);

    private int lastAlchCheckTick = -1;
    private int nextAlchIntervalTicks = 0;
    private Set<String> alchWhitelist = Collections.emptySet();
    private Set<String> alchExcludeList = Collections.emptySet();

    public boolean run(SlayerConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !config.enableHighAlch()) {
                    return;
                }

                // Parse whitelist and exclusion list from config
                updateWhitelist(config.highAlchItemList());
                updateExcludeList(config.highAlchExcludeList());

                if (alchWhitelist.isEmpty()) {
                    return;
                }

                // Find items in inventory that match the whitelist and are not excluded
                List<Rs2ItemModel> itemsToAlch = Rs2Inventory.getList(item ->
                        isOnWhitelist(item.getName()) && !isExcluded(item.getName()));

                if (itemsToAlch.isEmpty()) {
                    return;
                }

                // Check tick-based cooldown
                int currentTick = Microbot.getClient().getTickCount();
                if (lastAlchCheckTick != -1 && currentTick - lastAlchCheckTick < nextAlchIntervalTicks) {
                    return;
                }

                lastAlchCheckTick = currentTick;
                nextAlchIntervalTicks = Rs2Random.nextInt(MIN_TICKS, MAX_TICKS, 1.5, true);

                // Try Explorer's Ring first (free alchs)
                if (Rs2ExplorersRing.hasRing() && Rs2ExplorersRing.hasCharges()) {
                    for (Rs2ItemModel item : itemsToAlch) {
                        if (!isRunning()) {
                            break;
                        }

                        log.debug("High alching {} with Explorer's Ring", item.getName());
                        Rs2ExplorersRing.highAlch(item);
                    }
                    Rs2ExplorersRing.closeInterface();
                } else if (Rs2Magic.canCast(Rs2Spells.HIGH_LEVEL_ALCHEMY)) {
                    // Use normal high alchemy spell
                    for (Rs2ItemModel item : itemsToAlch) {
                        if (!isRunning()) {
                            break;
                        }

                        log.debug("High alching {} with spell", item.getName());
                        Rs2Magic.alch(item);

                        // Handle high value item warning
                        if (item.getHaPrice() > Rs2Settings.getMinimumItemValueAlchemyWarning()) {
                            sleepUntil(() -> Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"));
                            if (Rs2Widget.hasWidget("Proceed to cast High Alchemy on it")) {
                                Rs2Keyboard.keyPress('1');
                            }
                        }
                        Rs2Player.waitForAnimation();
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Updates the whitelist from the config string.
     */
    private void updateWhitelist(String csvNames) {
        if (csvNames == null || csvNames.trim().isEmpty()) {
            alchWhitelist = Collections.emptySet();
            return;
        }

        alchWhitelist = Arrays.stream(csvNames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Text::standardize)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Updates the exclusion list from the config string.
     */
    private void updateExcludeList(String csvNames) {
        if (csvNames == null || csvNames.trim().isEmpty()) {
            alchExcludeList = Collections.emptySet();
            return;
        }

        alchExcludeList = Arrays.stream(csvNames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Text::standardize)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Checks if an item name matches any entry in the exclusion list.
     * Supports wildcards (*) for pattern matching.
     */
    private boolean isExcluded(String itemName) {
        if (itemName == null || alchExcludeList.isEmpty()) {
            return false;
        }

        String normalizedName = Text.standardize(itemName);
        if (normalizedName.isEmpty()) {
            return false;
        }

        for (String pattern : alchExcludeList) {
            if (matchesPattern(normalizedName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an item name matches any entry in the whitelist.
     * Supports wildcards (*) for pattern matching.
     */
    private boolean isOnWhitelist(String itemName) {
        if (itemName == null || alchWhitelist.isEmpty()) {
            return false;
        }

        String normalizedName = Text.standardize(itemName);
        if (normalizedName.isEmpty()) {
            return false;
        }

        for (String pattern : alchWhitelist) {
            if (matchesPattern(normalizedName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches an item name against a pattern that may contain wildcards (*).
     */
    private boolean matchesPattern(String itemName, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }

        // Exact match
        if (!pattern.contains("*")) {
            return itemName.equals(pattern);
        }

        // Convert wildcard pattern to regex
        // Escape regex special chars except *, then convert * to .*
        String regex = pattern
                .replace(".", "\\.")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("*", ".*");

        return itemName.matches(regex);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        alchWhitelist = Collections.emptySet();
        alchExcludeList = Collections.emptySet();
    }
}
