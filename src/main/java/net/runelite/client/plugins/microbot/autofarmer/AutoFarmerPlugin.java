package net.runelite.client.plugins.microbot.autofarmer;

import com.google.inject.Provides;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "Scoots Auto Herb, Tree, Fruit Farmer",
        description = "Schedules and tends herbs, regular trees, and fruit trees.",
        tags = {"herb", "farming", "money making", "skilling"},
        authors = {"Mocrosoft", "Scoots"},
        version = AutoFarmerPlugin.version,
        minClientVersion = "2.6.59",
        enabledByDefault = false,
        isExternal = true
)

public class AutoFarmerPlugin extends Plugin {
    public static final String version = "1.10.17";
    @Inject
    private AutoFarmerConfig config;

    @Provides
    AutoFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoFarmerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoFarmerOverlay AutoFarmerOverlay;

    @Inject
    AutoFarmerScript herbrunScript;
    @Inject
    net.runelite.client.plugins.microbot.autofarmer.randomevents.RandomEventSupport randomEvents;

    @net.runelite.client.eventbus.Subscribe
    public void onConfigChanged(net.runelite.client.events.ConfigChanged event) {
        if (!"AutoFarmer".equals(event.getGroup()) || !"builtInRandomEvents".equals(event.getKey())) return;
        if (config.builtInRandomEvents()) randomEvents.start(); else randomEvents.stop();
    }


    @net.runelite.client.eventbus.Subscribe
    public void onChatMessage(net.runelite.api.events.ChatMessage event) {
        if (event.getType() == net.runelite.api.ChatMessageType.GAMEMESSAGE || event.getType() == net.runelite.api.ChatMessageType.SPAM) herbrunScript.onGameMessage(event.getMessage());
    }

    static String status;
    static volatile int farmingLevel;
    private volatile LevelEstimate levelEstimate;

    LevelEstimate getLevelEstimate() {
        return levelEstimate;
    }

    static final class LevelEstimate {
        private final int nextLevel;
        private final String herbName;
        private final int herbs;

        LevelEstimate(int nextLevel, String herbName, int herbs) {
            this.nextLevel = nextLevel;
            this.herbName = herbName;
            this.herbs = herbs;
        }

        int nextLevel() { return nextLevel; }
        String herbName() { return herbName; }
        int herbs() { return herbs; }
    }

    @net.runelite.client.eventbus.Subscribe
    public void onGameTick(net.runelite.api.events.GameTick event) {
        herbrunScript.checkNavigationOwnership();
        var client = Microbot.getClient();
        farmingLevel = client.getRealSkillLevel(Skill.FARMING);
        levelEstimate = estimateNextLevel(client.getSkillExperience(Skill.FARMING));
    }



    private LevelEstimate estimateNextLevel(int farmingXp) {
        int level = Experience.getLevelForXp(farmingXp);
        if (level >= Experience.MAX_REAL_LEVEL) return null;

        HerbSeedType herb = herbrunScript.getFirstHerbSeedInInventory();
        if (herb == null) herb = config.herbSeedType();
        if (!herb.canPlant(level)) return null;

        int nextLevel = level + 1;
        int remainingXp = Experience.getXpForLevel(nextLevel) - farmingXp;
        int herbs = (int) Math.ceil(remainingXp / herb.getHarvestXp());
        return new LevelEstimate(nextLevel, herb.getHerbName(), herbs);
    }


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(AutoFarmerOverlay);
        }
        net.runelite.client.plugins.microbot.Microbot.log("Auto Farmer version " + version + " starting");
        herbrunScript.run();
    }

    protected void shutDown() {
        randomEvents.stop();
        herbrunScript.shutdown();
        overlayManager.remove(AutoFarmerOverlay);
        levelEstimate = null;
        status = null; // Reset status on shutdown
    }
}
