package net.runelite.client.plugins.microbot.uimchartercrafter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.heapoverfl0w + "UIM Charter Crafter",
        description = "UIM-friendly charter crafting plugin",
        tags = {"uim", "crafting"},
        authors = {"heapoverfl0w"},
        version = UIMCharterCrafterPlugin.version,
        minClientVersion = "1.9.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/UIMCharterCrafterPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/UIMCharterCrafterPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class UIMCharterCrafterPlugin extends Plugin {
    static final String version = "1.0.0";
    @Inject
    private UIMCharterCrafterConfig config;

    @Inject
    private UIMCharterCrafterOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    private UIMCharterCrafterScript script;

    @Getter
    private volatile String state = "Idle";

    @Getter
    private volatile boolean prepared = false;

    @Getter
    private volatile boolean setup = false;

    @Getter
    private volatile String status = "";

    @Provides
    UIMCharterCrafterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(UIMCharterCrafterConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        script = new UIMCharterCrafterScript(this, config);
        script.run();
        if (overlayManager != null) overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() {
        if (overlayManager != null) overlayManager.remove(overlay);
        if (script != null) {
            script.requestStop();
            script.shutdown();
            script = null;
        }
    }

    public String getTargetProduct() {
        return config.product().widgetName();
    }

    void updateState(String state, String status, boolean isPrepared, boolean hasSetup) {
        this.state = state;
        this.status = status;
        this.prepared = isPrepared;
        this.setup = hasSetup;
        Microbot.status = status;
    }

}
