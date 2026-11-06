package net.runelite.client.plugins.microbot.GemCrabKiller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Gem Crab Killer",
        description = "Automatically kills the Gem Crab boss",
        authors = {"TaF"},
        tags = {"TaF", "crab", "combat", "training", "gem"},
        version = GemCrabKillerPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "https://chsami.github.io/Microbot-Hub/GemCrabKillerPlugin/assets/GemStoneCrab.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/GemCrabKillerPlugin/assets/GemStoneCrab.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class GemCrabKillerPlugin extends Plugin {

    public final static String version = "1.0.7";
    private final Map<Skill, Integer> startSkillExp = new EnumMap<>(Skill.class);
    @Inject
    GemCrabKillerScript gemCrabKillerScript;
    @Inject
    private GemCrabKillerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GemCrabKillerOverlay gemCrabKillerOverlay;
    private Instant scriptStartTime;
    private long startTotalExp;

    @Provides
    GemCrabKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GemCrabKillerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(gemCrabKillerOverlay);
        }
        gemCrabKillerScript.run(config);
    }

    protected void shutDown() {
        gemCrabKillerScript.shutdown();
        overlayManager.remove(gemCrabKillerOverlay);
        scriptStartTime = null;
        startTotalExp = 0;
        startSkillExp.clear();
        gemCrabKillerScript.totalCrabKills = 0;
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    public long getXpGained() {
        return hasStartExp() ? Microbot.getClient().getOverallExperience() - startTotalExp : 0;
    }

    public long getXpPerHour() {
        return perHour(getXpGained());
    }

    public long getSkillXpPerHour(Skill skill) {
        return perHour(getSkillXpGained(skill));
    }

    /**
     * @return the skill that gained the most xp this session, or null if none gained any yet
     */
    public Skill getTrainedSkill() {
        if (!hasStartExp()) {
            return null;
        }
        return startSkillExp.keySet().stream()
                .filter(skill -> getSkillXpGained(skill) > 0)
                .max(Comparator.comparingInt(this::getSkillXpGained))
                .orElse(null);
    }

    private int getSkillXpGained(Skill skill) {
        return Microbot.getClient().getSkillExperience(skill) - startSkillExp.getOrDefault(skill, 0);
    }

    // Takes the start snapshot once logged in with real xp values, never from zeros
    private boolean hasStartExp() {
        if (startTotalExp == 0) {
            long overallExp = Microbot.getClient().getOverallExperience();
            if (!Microbot.isLoggedIn() || overallExp <= 0) {
                return false;
            }
            startTotalExp = overallExp;
            snapshotSkillExp();
        }
        return true;
    }

    private void snapshotSkillExp() {
        for (Skill skill : Skill.values()) {
            if (skill != Skill.OVERALL) {
                startSkillExp.put(skill, Microbot.getClient().getSkillExperience(skill));
            }
        }
    }

    private long perHour(long xp) {
        if (scriptStartTime == null) {
            return 0;
        }

        long secondsElapsed = java.time.Duration.between(scriptStartTime, Instant.now()).getSeconds();
        if (secondsElapsed <= 0) {
            return 0;
        }

        return (xp * 3600L) / secondsElapsed;
    }
}
