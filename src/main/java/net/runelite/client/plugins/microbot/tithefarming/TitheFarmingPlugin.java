package net.runelite.client.plugins.microbot.tithefarming;


import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmMaterial;
import net.runelite.client.plugins.microbot.tithefarming.enums.TitheFarmState;
import net.runelite.client.plugins.microbot.tithefarming.models.TitheFarmPlant;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Tithe Farm",
        description = "Plays the Tithe farm minigame for you!",
        tags = {"tithe farming", "microbot", "skills", "minigame"},
        authors = { "Mocrosoft" },
        version = TitheFarmingPlugin.version,
        minClientVersion = "1.9.8.8",
        iconUrl = "https://chsami.github.io/Microbot-Hub/TitheFarmingPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/TitheFarmingPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TitheFarmingPlugin extends Plugin {

    final static String version = "1.0.1";

    @Inject
    public TitheFarmingConfig config;

    @Provides
    TitheFarmingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TitheFarmingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TitheFarmingOverlay titheFarmOverlay;

    private final net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript titheFarmScript = new net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript();

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);

        if (config.enableAntiban()){
            Rs2AntibanSettings.naturalMouse = true;
            Rs2Antiban.antibanSetupTemplates.applyFarmingSetup();
            Rs2Antiban.setActivity(Activity.GENERAL_FARMING);
            Rs2Antiban.activateAntiban();
        }

        if (overlayManager != null) {
            overlayManager.add(titheFarmOverlay);
        }
        titheFarmScript.run(config);
    }

    protected void shutDown() {
        titheFarmScript.shutdown();
        overlayManager.remove(titheFarmOverlay);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        for (TitheFarmPlant plant : net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript.plants) {
            if (event.getGameObject().getWorldLocation().equals(plant.getGameObject().getWorldLocation())) {
                plant.setGameObject(event.getGameObject());
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (TitheFarmMaterial.getSeedForLevel() != null) {
            Item fruit = Arrays.stream(event.getItemContainer().getItems()).filter(x -> x.getId() == TitheFarmMaterial.getSeedForLevel().getFruitId()).findFirst().orElse(null);
            if (fruit != null) {
                net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript.fruits = fruit.getQuantity() - net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript.initialFruit;
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        String message = chatMessage.getMessage();
        if (message.contains("%")) {
            Pattern pattern = Pattern.compile("(\\d+(\\.\\d+)?)%");
            Matcher matcher = pattern.matcher(message);

            if (matcher.find()) {
                String percentage = matcher.group(1);
                net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript.gricollerCanCharges = (int) (Float.parseFloat(percentage));
            }
        } else if (message.equalsIgnoreCase("Gricoller's can is already full.")) {
            net.runelite.client.plugins.microbot.tithefarming.TitheFarmingScript.gricollerCanCharges = 100;
        } else if (message.equalsIgnoreCase("You don't have a suitable vessel of water for watering the plant.")) {
            TitheFarmingScript.state = TitheFarmState.REFILL_WATERCANS;
        }
    }
}
