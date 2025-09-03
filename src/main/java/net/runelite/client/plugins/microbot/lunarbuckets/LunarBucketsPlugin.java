package net.runelite.client.plugins.microbot.lunarbuckets;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.gameval.ItemID;

import java.time.Duration;
import java.time.Instant;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = "[Ne] Lunar Buckets",
        description = "Casts Humidify to fill buckets of water",
        tags = {"lunar", "humidify", "magic", "bucket", "MoneyMaking"},
        authors = {"Neoxic"},
        version = LunarBucketsPlugin.version,
        minClientVersion = "1.9.9.2",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class LunarBucketsPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private LunarBucketsConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private LunarBucketsOverlay overlay;

    @Inject
    private LunarBucketsScript script;

    private static final int XP_PER_CAST = 65;
    private Instant startTime;
    private int casts;
    private int totalProfit;
    private int profitPerCast;

    @Provides
    LunarBucketsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LunarBucketsConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.profileSwitching = true;

        startTime = Instant.now();
        casts = 0;
        totalProfit = 0;
        profitPerCast = calculateProfitPerCast();

        script.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    private int calculateProfitPerCast() {
        int filled = Rs2GrandExchange.getPrice(ItemID.BUCKET_WATER);
        int empty = Rs2GrandExchange.getPrice(ItemID.BUCKET_EMPTY);
        int astral = Rs2GrandExchange.getPrice(ItemID.ASTRALRUNE);
        return filled * 27 - (empty * 27 + astral);
    }

    public void recordCast() {
        casts++;
        totalProfit += profitPerCast;
    }

    public int getTotalProfit() {
        return totalProfit;
    }

    private double getRuntimeHours() {
        return (double) Duration.between(startTime, Instant.now()).toMillis() / 3600000d;
    }

    public int getProfitPerHour() {
        double hours = getRuntimeHours();
        if (hours <= 0) return 0;
        double castsPerHour = casts / hours;
        return (int) (castsPerHour * profitPerCast);
    }

    public Duration getRunTime() {
        return Duration.between(startTime, Instant.now());
    }
}
