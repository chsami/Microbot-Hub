package net.runelite.client.plugins.microbot.thieving;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;
import java.time.Duration;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Thieving",
        description = "Microbot thieving plugin",
        authors = { "Mocrosoft", "Kryox", "Jesusfh" },
        version = ThievingPlugin.version,
        minClientVersion = "1.9.8.2",
        tags = {"thieving", "skilling"},
		iconUrl = "https://chsami.github.io/Microbot-Hub/ThievingPlugin/assets/icon.png",
		cardUrl = "https://chsami.github.io/Microbot-Hub/ThievingPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class ThievingPlugin extends Plugin {
	public static final String version = "2.0.5";

    @Inject
    @Getter
    private ThievingConfig config;
    @Provides
    ThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ThievingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ThievingOverlay thievingOverlay;
    @Inject
    @Getter
    private ThievingScript thievingScript;

    private int startXp = 0;
	@Getter
	private int maxCoinPouch;
    private String name = null;

    @Inject
    private EventBus eventBus;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(thievingOverlay);
        }
        startXp = 0;
		maxCoinPouch = determineMaxCoinPouch();
        thievingScript.run();

        eventBus.register(NpcDespawned.class, event -> {
            // clear the npc reference
            if (getThievingScript().thievingNpc != null && event.getNpc().getIndex() == getThievingScript().thievingNpc.getIndex()) {
                log.info("NPC (index={}) properties changed, updating reference", getThievingScript().thievingNpc.getIndex());
                getThievingScript().thievingNpc = null;
            }
        }, 0);

        eventBus.register(ChatMessage.class, event -> {
            if (!event.getMessage().toLowerCase().contains("you can only cast shadow veil every 30 seconds.")) {
                return;
            }
            log.warn("Attempted to cast shadow veil while it was active");
            getThievingScript().forceShadowVeilActive = System.currentTimeMillis()+30_000;
        }, 0);
    }

    protected void shutDown() {
        thievingScript.shutdown();
        overlayManager.remove(thievingOverlay);
		maxCoinPouch = 0;
        startXp = 0;
        eventBus.unregister(this);
    }

    private void setStartXp() {
        // this should handle changing accounts
        final String name = Rs2Player.getLocalPlayer().getName();
        if (startXp == 0 || (name != null && !name.equals(this.name))) {
            this.name = name;
            startXp = Microbot.getClient().getSkillExperience(Skill.THIEVING);
        }
    }

    public int xpGained() {
        setStartXp();
        final int currentXp = Microbot.getClient().getSkillExperience(Skill.THIEVING);
        return currentXp - startXp;
    }

	public int determineMaxCoinPouch() {
		if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE) == 1) {
			return 140;
		} else if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE) == 1) {
			return 84;
		} else if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1) {
			return 56;
		} else {
			return 28;
		}
	}

	public Duration getRunTime() {
		return thievingScript.getRunTime();
	}

    public State getState() {
        return thievingScript.currentState;
    }
}
