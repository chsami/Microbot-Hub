package net.runelite.client.plugins.microbot.CRONOVISOR;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.Bee + "Clan CRONOVISOR",
        description = "Recruits people at GE to your clan",
        tags = {"recruit", "clan", "type", "keyboard", "typer","Auto-typer", "microbot"},
        authors = { "Mocrosoft" },
        version = CRONOVISORPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class CRONOVISORPlugin extends Plugin {
    final static String version = "1.0.3";
    @Inject
    private CRONOVISORConfig config;

    @Inject
    private EventBus eventBus;

    @Provides
    CRONOVISORConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CRONOVISORConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private CRONOVISOROverlay CRONOVISORORverlay;

    @Inject
    private CRONOVISORScript CRONOVISORScript;

    @Override
    protected void startUp() throws Exception {
        if (overlayManager != null) {
            overlayManager.add(CRONOVISORORverlay);
        }
        CRONOVISORScript.run(config);
        eventBus.register(this);  // Register for events
        log.info("CRONOVISOR Plugin started!");
    }

    @Override
    protected void shutDown() {
        CRONOVISORScript.shutdown();
        overlayManager.remove(CRONOVISORORverlay);
        eventBus.unregister(this);  // Unregister events
        log.info("CRONOVISOR Plugin stopped!");
    }

    /**
     * This method disables the "Walk here" option in the menu for all tiles.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        // Disable the "Walk here" option for all clicks
        if (event.getMenuOption().equalsIgnoreCase("Walk here")) {
            log.info("Disabling 'Walk here' option globally.");
            event.consume();  // Consume the event to prevent the player from walking
        }
    }
}