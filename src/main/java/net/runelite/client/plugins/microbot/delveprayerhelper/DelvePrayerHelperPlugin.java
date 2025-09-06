package net.runelite.client.plugins.microbot.delveprayerhelper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperProjectile;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginConstants.ENGIN + "Delve Prayer Helper",
	description = "Automatically prays against the Delve boss attacks",
	tags = {"delve", "doom of mokhaiotl", "mokha", "pvm", "boss"},
	authors = { "Engin" },
	version = DelvePrayerHelperPlugin.version,
	minClientVersion = "2.0.0",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class DelvePrayerHelperPlugin extends Plugin {

	static final String version = "1.0.0";

    @Inject
    private DelvePrayerHelperConfig config;
    @Provides
    DelvePrayerHelperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DelvePrayerHelperConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private DelvePrayerHelperOverlay delvePrayerHelperOverlay;

    @Inject
    private DelvePrayerHelperScript delvePrayerHelperScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(delvePrayerHelperOverlay);
        }
        delvePrayerHelperScript.run();
    }

    protected void shutDown() {
        delvePrayerHelperScript.shutdown();
        overlayManager.remove(delvePrayerHelperOverlay);
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved e)
    {
        Projectile projectile = e.getProjectile();

        boolean isMeleeAttack = projectile.getId() == DelvePrayerHelperProjectile.MELEE.getProjectileID();
        boolean isMageAttack = projectile.getId() == DelvePrayerHelperProjectile.MAGE.getProjectileID();
        boolean isRangeAttack = projectile.getId() == DelvePrayerHelperProjectile.RANGE.getProjectileID();

        if (!(isMeleeAttack || isMageAttack || isRangeAttack))
        {
            return;
        }

        int hitCycle = projectile.getEndCycle();
        delvePrayerHelperScript.incomingProjectiles.put(hitCycle, projectile);
    }
}
