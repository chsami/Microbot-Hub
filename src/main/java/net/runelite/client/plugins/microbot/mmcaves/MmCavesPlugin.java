package net.runelite.client.plugins.microbot.mmcaves;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@PluginDescriptor(
        name = "MM Caves",
        description = "Automates chinning or bursting in Monkey Madness II caves",
        tags = {"mm", "mm2", "caves", "chinning", "bursting", "ranged", "magic"},
        authors = { "FreakJoost" },
        minClientVersion = "2.0.6",
        isExternal = true,
        enabledByDefault = false
)
@Slf4j
public class MmCavesPlugin extends Plugin {
    public static String version = "1.0.3";

    @Inject
    private MmCavesConfig config;
    Instant startTime;

    @Provides
    MmCavesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MmCavesConfig.class);
    }

    @Inject
    @Getter
    private MmCavesScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MmCavesOverlay mmCavesOverlay;

    @Getter
    private volatile WorldPoint myWorldPoint;

    private final Set<Integer> checkedWorlds = ConcurrentHashMap.newKeySet();

    public String state = "test";

    @Override
    protected void startUp() {
        this.startTime = Instant.now();
        myWorldPoint = null;
        checkedWorlds.clear();
        
        if (overlayManager != null) {
            overlayManager.add(mmCavesOverlay);
        }
        script.setConfig(config);
        script.run();
    }

    @Override
    protected void shutDown() {
        script.stopHoverTracking();
        script.shutdown();
        script.releaseRangedPrayer();
        overlayManager.remove(mmCavesOverlay);
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (Microbot.isLoggedIn() && Microbot.getClient().getLocalPlayer() != null) {
            myWorldPoint = Microbot.getClient().getLocalPlayer().getWorldLocation();
        } else {
            myWorldPoint = null;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = event.getMessage();
        int currentWorld = Microbot.getClient().getWorld();

        String caveIsEmptyMessage = "You peek down and see no adventurers inside this section of the caverns.";
        if (msg.equalsIgnoreCase(caveIsEmptyMessage)) {
            script.caveIsEmpty = true;
            checkedWorlds.add(currentWorld);
            return;
        }

        if (msg.contains("adventurer inside this section of the caverns") ||
                msg.contains("adventurers inside this section of the caverns")) {
            script.caveIsEmpty = false;
            checkedWorlds.add(currentWorld);
        }
    }

    public boolean isWorldChecked(int world) {
        return checkedWorlds.contains(world);
    }

    @Subscribe
    public void onClientTick(ClientTick clientTick) {
        script.onClientTick();
    }

    public Set<Integer> getCheckedWorlds() {
        return new HashSet<>(checkedWorlds);
    }
}
