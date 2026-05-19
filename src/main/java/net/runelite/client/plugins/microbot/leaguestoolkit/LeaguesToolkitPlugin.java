package net.runelite.client.plugins.microbot.leaguestoolkit;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.DV + "Leagues Toolkit",
        description = "Quality-of-life utilities for Leagues (anti-AFK, and more to come)",
        tags = {"leagues", "microbot", "utility", "afk"},
        version = LeaguesToolkitPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class LeaguesToolkitPlugin extends Plugin {
    public static final String version = "1.3.0";

    @Inject
    private LeaguesToolkitConfig config;

    @Inject
    private LeaguesToolkitScript leaguesToolkitScript;

    @Provides
    LeaguesToolkitConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LeaguesToolkitConfig.class);
    }

    @Override
    protected void startUp() {
        leaguesToolkitScript.run(config);
    }

    @Override
    protected void shutDown() {
        leaguesToolkitScript.shutdown();
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (config.enableGorillaPrayer()) {
            leaguesToolkitScript.getGorillaPrayerHelper().onAnimationChanged(event);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (config.enableGorillaPrayer()) {
            leaguesToolkitScript.getGorillaPrayerHelper().onHitsplatApplied(event);
        }
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event) {
        if (config.enableGorillaPrayer()) {
            leaguesToolkitScript.getGorillaPrayerHelper().onOverheadTextChanged(event);
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        if (!config.enableHespori() || config.hesporiPrayerOnly()) return;
        int id = event.getProjectile().getId();
        // Vine/quadrant explosion projectile — calculate dodge position on client thread
        if (id == 3680) {
            var helper = leaguesToolkitScript.getHesporiBossHelper();
            if (!helper.isVineDetected()) {
                log.info("[LeaguesToolkit] Vine 3680 — calculating dodge position");
                // Calculate dodge canvas position (we're on client thread, safe to access)
                var player = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
                if (player != null) {
                    var myLocal = player.getLocalLocation();
                    // Boss is always at center of arena: LocalPoint(7104, 7104)
                    // Determine which quadrant player is in relative to boss
                    // and move to the OPPOSITE quadrant
                    int bossX = 7104, bossY = 7104;
                    int dx = (myLocal.getX() > bossX) ? -768 : 768;  // If east of boss → go west
                    int dy = (myLocal.getY() > bossY) ? -768 : 768;  // If north of boss → go south
                    var dodgeLocal = new net.runelite.api.coords.LocalPoint(
                            myLocal.getX() + dx, myLocal.getY() + dy, myLocal.getWorldView());
                    var poly = net.runelite.api.Perspective.getCanvasTilePoly(
                            net.runelite.client.plugins.microbot.Microbot.getClient(), dodgeLocal);
                    if (poly != null) {
                        var bounds = poly.getBounds();
                        helper.setDodgeX((int) bounds.getCenterX());
                        helper.setDodgeY((int) bounds.getCenterY());
                        helper.setVineDetected(true); // Set AFTER coordinates are stored
                        log.info("[LeaguesToolkit] Dodge position: ({}, {})", bounds.getCenterX(), bounds.getCenterY());
                    }
                }
            }
        }
    }
}
