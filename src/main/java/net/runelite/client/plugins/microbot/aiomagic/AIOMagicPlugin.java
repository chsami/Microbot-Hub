package net.runelite.client.plugins.microbot.aiomagic;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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
import java.awt.*;
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
    private StunTeleAlchScript stunTeleAlchScript; // NEW

    @Inject
    private SpinFlaxScript spinFlaxScript;

    public final static String version = "1.2.1"; // bumped

    @Getter
    private Rs2CombatSpells combatSpell;

    @Getter
    private List<String> alchItemNames = Collections.emptyList();

    @Getter
    private SuperHeatItem superHeatItem;

    @Getter
    private String npcName;

    @Getter
    private TeleportSpell teleportSpell;

    @Getter
    private StunSpell stunSpell;

    @Getter
    private String stunNpcName;

    @Override
    protected void startUp() throws AWTException {
        combatSpell = config.combatSpell();
        alchItemNames = updateItemList(config.alchItems());
        superHeatItem = config.superHeatItem();
        npcName = config.npcName();
        teleportSpell = config.teleportSpell();
        stunSpell = config.stunSpell();
        stunNpcName = config.stunNpcName();

        if (overlayManager != null) {
            overlayManager.add(aioMagicOverlay);
        }

        switch (config.magicActivity()) {
            case SPLASHING:
                splashScript.run();
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
        stunAlchScript.shutdown();
        if (stunTeleAlchScript != null) stunTeleAlchScript.shutdown(); // NEW
        if (spinFlaxScript != null) spinFlaxScript.shutdown();
        overlayManager.remove(aioMagicOverlay);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(AIOMagicConfig.configGroup))
            return;

        if (event.getKey().equals(AIOMagicConfig.combatSpell)) {
            combatSpell = config.combatSpell();
        }

        if (event.getKey().equals(AIOMagicConfig.alchItems)) {
            alchItemNames = updateItemList(config.alchItems());
        }

        if (event.getKey().equals(AIOMagicConfig.superHeatItem)) {
            superHeatItem = config.superHeatItem();
        }

        if (event.getKey().equals(AIOMagicConfig.npcName)) {
            npcName = config.npcName();
        }

        if (event.getKey().equals(AIOMagicConfig.teleportSpell)) {
            teleportSpell = config.teleportSpell();
        }

        if (event.getKey().equals(AIOMagicConfig.stunSpell)) {
            stunSpell = config.stunSpell();
        }

        if (event.getKey().equals(AIOMagicConfig.stunNpcName)) {
            stunNpcName = config.stunNpcName();
        }
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
