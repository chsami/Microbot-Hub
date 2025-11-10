package net.runelite.client.plugins.microbot.gauntlethelper;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
//import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.gauntlethelper.GauntletHelperConfig;
import net.runelite.client.plugins.microbot.gauntlethelper.GauntletHelperOverlay;
import net.runelite.client.plugins.microbot.gauntlethelper.GauntletHelperPlugin;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;

import static java.lang.Thread.sleep;
import static net.runelite.client.plugins.microbot.Microbot.log;

@PluginDescriptor(
        name = "Gauntlet Helper",
        description = "Gauntlet Helper",
        tags = {"Jam", "cg", "Gauntlet", "hunllef", "pvm", "prayer", "money making", "auto", "boss" },
        version = GauntletHelperPlugin.version,
        minClientVersion = "2.0.30",
        cardUrl = "",
        iconUrl = ""
        //enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        //isExternal = PluginConstants.IS_EXTERNAL
)

public class GauntletHelperPlugin extends Plugin {
    public static final String version = "1.0";

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ConfigManager configManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GauntletHelperOverlay overlay;
    @Inject
    private GauntletHelperConfig config;
    @Provides
    GauntletHelperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GauntletHelperConfig.class);
    }
    @Inject
    private GauntletHelperScript GH_script;

    @Getter
    private static int timer1Count = 0;
    @Getter
    private static long timer1Time = 0;


    private final int RANGE_PROJECTILE_MINIBOSS = 1705;
    private final int MAGE_PROJECTILE_MINIBOSS = 1701;
    private final int RANGE_PROJECTILE = 1711;
    private final int MAGE_PROJECTILE = 1707;
    private final int CG_RANGE_PROJECTILE = 1712;
    private final int CG_MAGE_PROJECTILE = 1708;
    private final int DEACTIVATE_MAGE_PROJECTILE = 1713;
    private final int CG_DEACTIVATE_MAGE_PROJECTILE = 1714;
    private final int MAGE_ANIMATION = 8754;
    private final int RANGE_ANIMATION = 8755;

    private int projectileCount = 0;

    private static final Set<Integer> HUNLLEF_IDS = Set.of(
            9035, 9036, 9037, 9038, // Corrupted Hunllef variants
            9021, 9022, 9023, 9024  // Crystalline Hunllef variants
    );



    @Override
    protected void startUp() throws Exception {
        log("Gauntlet Helper plugin started!");
        GH_script.startup2();
        GH_script.run();
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        log("Gauntlet Helper plugin stopped!");
        try {
            if (GH_script != null) GH_script.shutdown();
        } finally {
            overlayManager.remove(overlay);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event){
        GH_script.event_gametick();
    }


    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        int projectileId = event.getProjectile().getId();

        switch (projectileId) {
            case MAGE_PROJECTILE:
            case CG_MAGE_PROJECTILE:
            case MAGE_PROJECTILE_MINIBOSS:
                GH_script.anim_magic_seen();
                break;

            case RANGE_PROJECTILE:
            case CG_RANGE_PROJECTILE:
            case RANGE_PROJECTILE_MINIBOSS:
                GH_script.anim_range_seen();
                break;

            case CG_DEACTIVATE_MAGE_PROJECTILE:
            case DEACTIVATE_MAGE_PROJECTILE:
                projectileCount++;
                if (projectileCount >= 56) {
                    GH_script.proj_mageblast_end();
                    projectileCount = 0; // reset after the last hit
                }
                break;
            default:
                break;
        }

    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        if (!HUNLLEF_IDS.contains(npc.getId())) return;

        int animationID = npc.getAnimation();
        switch (animationID) {
            case MAGE_ANIMATION:
                GH_script.proj_magic_seen();
                break;
            case RANGE_ANIMATION:
                GH_script.proj_range_seen();
                break;
            default:
                break;
        }
    }
}
