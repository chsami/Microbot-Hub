package net.runelite.client.plugins.microbot.moonsOfPeril;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Funk + "Moons of Peril",
        description = "A plugin to farm the Moons of Peril",
        tags = {"bossing", "pvm", "moneymaking", "combat", "microbot"},
        authors = "The Funk",
        version = moonsOfPerilPlugin.version,
        minClientVersion = "2.0.0",
        iconUrl = "https://chsami.github.io/Microbot-Hub/moonsOfPerilPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/moonsOfPerilPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class moonsOfPerilPlugin extends Plugin {
    static final String version = "1.0.1";
    @Inject
    private moonsOfPerilConfig config;
    @Provides
    moonsOfPerilConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(moonsOfPerilConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private moonsOfPerilOverlay moonsOfPerilOverlay;

    @Inject
    moonsOfPerilScript moonsOfPerilScript;
    @Inject
    private moonsOfPerilConfig moonsOfPerilConfig;
    public static int bloodPoolTick;
    public static Instant scriptStartTime;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(moonsOfPerilOverlay);
        }
        moonsOfPerilScript.run();
        Rs2Tile.init();
        this.scriptStartTime = Instant.now();
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        final GraphicsObject graphicEvent = event.getGraphicsObject();
        if (graphicEvent.getId() == SpotanimID.VFX_DJINN_ICE_FLOOR_SPAWN_01) {
            Rs2Tile.addDangerousGraphicsObjectTile(graphicEvent, 600 * 3);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        final GameObject bloodPool = event.getGameObject();
        if (bloodPool.getId() == ObjectID.PMOON_BOSS_BLOOD_POOL) {
            bloodPoolTick = 0;
        }
    }

    protected void shutDown() {
        moonsOfPerilScript.shutdown();
        overlayManager.remove(moonsOfPerilOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        bloodPoolTick ++;
        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }
}
