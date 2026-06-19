package net.runelite.client.plugins.microbot.attackrangesplus;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = "<html>[<font color=#BB86FC>P</font>] " + "Attack Ranges Plus",
        description = "Draws your attack range (and optionally your target's), auto-detected from your weapon and clipped to line of sight.",
        tags = {"range", "pvp", "combat", "overlay"},
        authors = {"pjmarz"},
        version = AttackRangesPlusPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AttackRangesPlusPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AttackRangesPlusPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class AttackRangesPlusPlugin extends Plugin
{
    public static final String version = "0.2.3";

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AttackRangesPlusOverlay overlay;

    @Provides
    AttackRangesPlusConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AttackRangesPlusConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
    }
}
