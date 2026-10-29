package net.runelite.client.plugins.microbot.aiomagic;

import com.google.inject.Provides;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicConfig;
import net.runelite.client.plugins.microbot.aiomagic.AIOMagicOverlay;
import net.runelite.client.plugins.microbot.aiomagic.enums.StunSpell;
import net.runelite.client.plugins.microbot.aiomagic.enums.SuperHeatItem;
import net.runelite.client.plugins.microbot.aiomagic.enums.TeleportSpell;
import net.runelite.client.plugins.microbot.aiomagic.scripts.*;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@PluginDescriptor(
        name = PluginDescriptor.GMason + "AIO Magic",
        description = "Microbot Magic plugin",
        tags = {"magic", "microbot", "skilling", "training"},
        version = AIOMagicPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "https://chsami.github.io/Microbot-Hub/AIOMagicPlugin/assets/card.png",
        iconUrl = "https://chsami.github.io/Microbot-Hub/AIOMagicPlugin/assets/icon.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class AIOMagicPlugin extends Plugin {
    @Inject
    private AIOMagicConfig config;

    @Provides
    AIOMagicConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AIOMagicConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AIOMagicOverlay aioMagicOverlay;

    @Inject
    private SplashScript splashScript;

    @Inject
    private AlchScript alchScript;

    @Inject
    private SuperHeatScript superHeatScript;

    @Inject
    private TeleportScript teleportScript;

    @Inject
    private TeleAlchScript teleAlchScript;

    @Inject
    private StunAlchScript stunAlchScript;

    @Inject
    private StunScript stunScript;

    @Inject
    private StunTeleAlchScript stunTeleAlchScript; // NEW

    @Inject
    private SpinFlaxScript spinFlaxScript;

    public final static String version = "1.5.0";

    private static final int ALCH_MIN_PAUSE_MS = 80;

    private Instant scriptStartTime;
    private int startMagicExp;
    private boolean stopping;
    private final ClickDelay clickDelay = new ClickDelay();

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = null;
        startMagicExp = 0;
        stopping = false;
        if (overlayManager != null) {
            overlayManager.add(aioMagicOverlay);
        }

        switch (config.magicActivity()) {
            case SPLASHING:
                splashScript.run();
                break;
            case STUN:
                stunScript.run();
                break;
            case ALCHING:
                alchScript.run();
                break;
            case SUPERHEAT:
                superHeatScript.run();
                break;
            case TELEPORT:
                teleportScript.run();
                break;
            case TELEALCH:
                teleAlchScript.run();
                break;
            case STUNALCH:
                stunAlchScript.run();
                break;
            case STUNTELEALCH: // NEW
                stunTeleAlchScript.run();
                break;
            case SPINFLAX:
                spinFlaxScript.run();
                break;
        }
    }

    protected void shutDown() {
        splashScript.shutdown();
        alchScript.shutdown();
        superHeatScript.shutdown();
        teleportScript.shutdown();
        teleAlchScript.shutdown();
        stunScript.shutdown();
        stunAlchScript.shutdown();
        if (stunTeleAlchScript != null) stunTeleAlchScript.shutdown(); // NEW
        if (spinFlaxScript != null) spinFlaxScript.shutdown();
        overlayManager.remove(aioMagicOverlay);
        scriptStartTime = null;
    }

    // One central check for every activity: stops the plugin once the real Magic level reaches the target
    @Subscribe
    public void onGameTick(GameTick event) {
        int stopLevel = config.stopAtMagicLevel();
        if (stopping || stopLevel <= 0 || Microbot.getClient().getRealSkillLevel(Skill.MAGIC) < stopLevel) {
            return;
        }
        stopping = true;
        Microbot.status = "Reached Magic level " + stopLevel + ", stopping";
        // Off the client thread: showMessage(String) is modal and would freeze the game until dismissed
        SwingUtilities.invokeLater(() -> {
            Microbot.stopPlugin(this);
            Microbot.showMessage("AIO Magic stopped at target Magic level " + stopLevel, 10000);
        });
    }

    public int getMinClickDelay() {
        return Math.min(config.minClickDelay(), config.maxClickDelay());
    }

    public int getMaxClickDelay() {
        return Math.max(config.minClickDelay(), config.maxClickDelay());
    }

    // Rs2Magic.alch pauses between the spell click and the item click; keep it long enough for the tab switch
    public int getAlchPauseMin() {
        return Math.max(ALCH_MIN_PAUSE_MS, getMinClickDelay());
    }

    public int getAlchPauseMax() {
        return Math.max(ALCH_MIN_PAUSE_MS, getMaxClickDelay());
    }

    public int nextClickDelay() {
        return clickDelay.next(getMinClickDelay(), getMaxClickDelay());
    }

    public int getMagicXpGained() {
        return hasStartExp() ? Microbot.getClient().getSkillExperience(Skill.MAGIC) - startMagicExp : 0;
    }

    public long getMagicXpPerHour() {
        if (scriptStartTime == null) {
            return 0;
        }
        long secondsElapsed = Duration.between(scriptStartTime, Instant.now()).getSeconds();
        return secondsElapsed <= 0 ? 0 : getMagicXpGained() * 3600L / secondsElapsed;
    }

    // Takes the start snapshot once logged in with real xp values, never from zeros
    private boolean hasStartExp() {
        if (scriptStartTime == null) {
            if (!Microbot.isLoggedIn() || Microbot.getClient().getOverallExperience() <= 0) {
                return false;
            }
            startMagicExp = Microbot.getClient().getSkillExperience(Skill.MAGIC);
            scriptStartTime = Instant.now();
        }
        return true;
    }

    public Rs2CombatSpells getCombatSpell() {
        return config.combatSpell();
    }

    public AIOMagicConfig getConfig() {
        return config;
    }

    public List<String> getAlchItemNames() {
        return updateItemList(config.alchItems());
    }

    public SuperHeatItem getSuperHeatItem() {
        return config.superHeatItem();
    }

    public String getNpcName() {
        return config.npcName();
    }

    public TeleportSpell getTeleportSpell() {
        return config.teleportSpell();
    }

    public StunSpell getStunSpell() {
        return config.stunSpell();
    }

    public String getStunNpcName() {
        return config.stunNpcName();
    }

    private List<String> updateItemList(String items) {
        if (items == null || items.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(items.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    public Rs2Spells getAlchSpell() {
        return Rs2Player.getSkillRequirement(Skill.MAGIC, 55) ? Rs2Spells.HIGH_LEVEL_ALCHEMY : Rs2Spells.LOW_LEVEL_ALCHEMY;
    }
}
