package net.runelite.client.plugins.microbot.tempoross;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NpcID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "Tempoross",
        description = "Tempoross Plugin",
        tags = {"Tempoross", "minigame", "s1d", "see1duck","infuse21", "microbot", "fishing", "skilling"},
        authors = { "See1Duck", "infuse" },
        version = TemporossPlugin.version,
        // Conservative: verified compiling against 2.6.16; the rework uses APIs (tile-object cache
        // queries, Rs2Tile.isWalkable(LocalPoint), hopToWorld) not present in older clients.
        minClientVersion = "2.6.16",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TemporossPlugin extends Plugin {
    public static final String version = "2.24.5";
    @Inject
    private TemporossConfig config;

    @Inject
    private TemporossOverlay temporossOverlay;

    @Inject
    private TemporossProgressionOverlay temporossProgressionOverlay;

    @Inject
    private TemporossScript temporossScript;

    @Inject
    private static ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private Client client;


    public static volatile int waves = 0;
    public static volatile int fireClouds = 0;
    public static volatile boolean incomingWave = false;
    public static volatile boolean isTethered = false;

    private static final int VARB_IS_TETHERED = 11895;

    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");


    @Provides
    TemporossConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TemporossConfig.class);
    }


    protected void startUp() throws Exception {
        if (overlayManager != null) {
            overlayManager.add(temporossOverlay);
            overlayManager.add(temporossProgressionOverlay);
        }
        temporossScript.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        super.shutDown();
        temporossScript.shutdown();
        overlayManager.remove(temporossOverlay);
        overlayManager.remove(temporossProgressionOverlay);
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        // Sub-tick fire response: a strike can land a fire on the player's tile with ~one tick to
        // douse it. The script's loop cadence is too coarse for that; the spawn event is not.
        TemporossScript.onFireSpawned(event.getNpc());
    }

    @Subscribe
    public void onGameTick(GameTick e) {
        // On the client thread here: capture everything the script executor needs this tick.
        TemporossScript.refreshClientSnapshot();
        TemporossScript.cachedInMinigame = TemporossScript.isInMinigame();
        if (!TemporossScript.cachedInMinigame)
            return;
        // Before the wave gate: shadow ages come from birth ticks stamped in updateCloudData, and
        // pausing the tracker during waves made shadows look younger than they are — a late dodge.
        // Fires too, for the opposite reason: the colossal wave EXTINGUISHES every active fire, so
        // a list frozen during the wave was full of ghost fires afterwards — the bot detoured
        // around and walked to douse fires the wave had already put out.
        if (TemporossScript.workArea != null) {
            TemporossScript.updateCloudData();
            TemporossScript.updateFireData();
        }
        if (incomingWave)
            return;
        TemporossScript.cachedRawFish = State.getRawFish();
        TemporossScript.cachedCookedFish = State.getCookedFish();
        TemporossScript.cachedAllFish = State.getAllFish();
        TemporossScript.cachedTotalSlots = State.getTotalAvailableFishSlots();
        if (TemporossScript.workArea == null)
            return;
        TemporossScript.handleWidgetInfo();
        TemporossScript.updateTotemExitAnchor();
        TemporossScript.updateFishSpotData();
        TemporossScript.updateAmmoCrateData();
        TemporossScript.updateLastWalkPath();

        Rs2NpcModel doubleFishingSpot = Microbot.getRs2NpcCache().query().withId(NpcID.FISHING_SPOT_10569).nearest();

        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot != null) {
            TemporossScript.state = TemporossScript.state.next;
        }

        if (TemporossScript.INTENSITY >= TemporossScript.thresholdForfeitIntensity && TemporossScript.state == State.THIRD_COOK) {
            return;
        }

        if (TemporossScript.state == null) {
            TemporossScript.state = State.THIRD_CATCH;
        }

        if (TemporossScript.state != null && TemporossScript.state.isComplete()) {
            TemporossScript.isFilling = false;
            TemporossScript.state = TemporossScript.state.next == null ? State.THIRD_CATCH : TemporossScript.state.next;
        }

        // The scheduled SECOND_FILL never runs — in EVERY cycle. Pre-pool-1 that is the
        // hold-through-pool-1 strategy (pool 1 cannot end the round, essence starts full); after
        // it, the cycle is catch-and-cook batches with the emergency fill / endgame dump as the
        // single cannon trip. Loading happens via the opening INITIAL_FILL and EMERGENCY_FILL only.
        if (TemporossScript.state == State.SECOND_FILL) {
            TemporossScript.state = State.THIRD_CATCH;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() == VARB_IS_TETHERED) {
            log.info("Tethered: {}", event.getValue());
            isTethered = event.getValue() > 0;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();
        String message = event.getMessage();

        if (type == ChatMessageType.GAMEMESSAGE) {
            if (message.contains("A colossal wave closes in")) {
                waves++;
                incomingWave = true;
                log.info("Wave {}", waves);
            }

            if (message.contains("the rope keeps you securely") || message.contains("the wave slams into you")) {
                incomingWave = false;
                log.info("Wave passed");
            }
            if (message.contains("A strong wind blows as clouds roll in")) {
                fireClouds++;
                log.info("Clouds {}", fireClouds);
            }
            {

            }
        }
    }

    // Set rope config
    public static void setRope(boolean rope) {
        Microbot.getConfigManager().setConfiguration("microbot-tempoross", "rope", rope);
    }
}
