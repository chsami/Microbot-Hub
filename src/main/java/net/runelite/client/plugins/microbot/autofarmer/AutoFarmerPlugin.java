package net.runelite.client.plugins.microbot.autofarmer;

import com.google.inject.Provides;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.*;

@PluginDescriptor(
        name = "Scoots Auto-Farmer",
        description = "Quest-aware automatic herb farming",
        tags = {"herb", "farming", "money making", "skilling"},
        authors = {"Mocrosoft"},
        version = AutoFarmerPlugin.version,
        minClientVersion = "2.6.59",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AutoFarmerPlugin/assets/icon.jpg",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AutoFarmerPlugin/assets/card.jpg",
        enabledByDefault = false,
        isExternal = true
)

public class AutoFarmerPlugin extends Plugin {
    public static final String version = "1.10.38";
    @Inject
    private AutoFarmerConfig config;

    @Provides
    AutoFarmerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoFarmerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    private final Timer loginStatusTimer = new Timer(1000, event -> updateLoginStatus());
    private final JTextArea loginStatusBanner = new JTextArea();
    private volatile boolean onLoginScreen;
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


    @net.runelite.client.eventbus.Subscribe
    public void onGameStateChanged(net.runelite.api.events.GameStateChanged event) {
        onLoginScreen = event.getGameState() == GameState.LOGIN_SCREEN;
    }

    private void updateLoginStatus() {
        if (!onLoginScreen) {
            removeLoginStatus();
            return;
        }

        String daily = null;
        String crop = null;
        boolean dailyBreak = config.dailyBreak() && herbrunScript.isTakingDailyBreak();
        if (dailyBreak) {
            long remaining = herbrunScript.dailyBreakMillisRemaining();
            daily = remaining > 0 ? "Daily break: " + countdown(remaining) + " left"
                    : "Daily break complete. Logging in...";
        }
        if (herbrunScript.isWaitingForCrops()) {
            long remaining = herbrunScript.nextCropCheckAt() - System.currentTimeMillis();
            crop = "Next crop check: " + herbrunScript.nextCropName()
                    + (remaining > 0 ? " in " + countdown(remaining) : dailyBreak ? " (due)" : " (logging in...)");
        }
        if (daily == null && crop == null) {
            removeLoginStatus();
            return;
        }

        Component client = ClientUI.getClient();
        if (client == null || !(client.getParent() instanceof JComponent)) return;
        JComponent panel = (JComponent) client.getParent();
        if (!(panel.getLayout() instanceof BorderLayout)) return;
        BorderLayout layout = (BorderLayout) panel.getLayout();
        if (layout.getLayoutComponent(BorderLayout.CENTER) != client) return;
        Component north = layout.getLayoutComponent(BorderLayout.NORTH);
        if (north != null && north != loginStatusBanner) return;

        String message = "Scoots Auto-Farmer  |  " + (daily == null ? crop : daily);
        if (daily != null && crop != null) message += "\n" + crop;
        if (!message.equals(loginStatusBanner.getText())) loginStatusBanner.setText(message);
        if (north == null) {
            panel.add(loginStatusBanner, BorderLayout.NORTH);
            panel.revalidate();
            panel.repaint();
        }
    }

    private void removeLoginStatus() {
        Container parent = loginStatusBanner.getParent();
        if (parent == null) return;
        parent.remove(loginStatusBanner);
        parent.revalidate();
        parent.repaint();
    }

    private static String countdown(long remainingMillis) {
        long seconds = (remainingMillis + 999) / 1000;
        if (seconds >= 3600) return seconds / 3600 + "h " + seconds / 60 % 60 + "m";
        return seconds / 60 + "m " + seconds % 60 + "s";
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(AutoFarmerOverlay);
        }
        net.runelite.client.plugins.microbot.Microbot.log("Auto Farmer version " + version + " starting");
        onLoginScreen = Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN;
        SwingUtilities.invokeLater(() -> {
            loginStatusBanner.setEditable(false);
            loginStatusBanner.setFocusable(false);
            loginStatusBanner.setBackground(new Color(26, 24, 20));
            loginStatusBanner.setForeground(Color.WHITE);
            loginStatusBanner.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            updateLoginStatus();
            loginStatusTimer.start();
        });
        herbrunScript.run();
    }

    protected void shutDown() {
        SwingUtilities.invokeLater(() -> {
            loginStatusTimer.stop();
            removeLoginStatus();
        });
        randomEvents.stop();
        herbrunScript.shutdown();
        overlayManager.remove(AutoFarmerOverlay);
        levelEstimate = null;
        status = null; // Reset status on shutdown
    }
}
