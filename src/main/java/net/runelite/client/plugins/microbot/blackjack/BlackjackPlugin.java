package net.runelite.client.plugins.microbot.blackjack;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>JOJ</font>] Blackjack",
        description = "Lures and blackjacks a supported Pollnivneach target",
        authors = {"jogonjohn"},
        version = BlackjackPlugin.VERSION,
        minClientVersion = "2.6.38",
        tags = {"thieving", "blackjack", "pollnivneach"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BlackjackPlugin extends Plugin
{
    public static final String VERSION = "1.1.29";

    @Inject
    @Getter
    private BlackjackScript script;

    @Inject
    private BlackjackConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Getter
    private volatile BlackjackSupplies supplies;

    @Inject
    private BlackjackOverlay overlay;

    @Inject
    private BlackjackHouseOverlay houseOverlay;

    @Provides
    BlackjackConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BlackjackConfig.class);
    }

    @Override
    protected void startUp()
    {
        log.info("Starting BlackjackPlugin version={} implementationVersion={} buildCommit={} buildBranch={} buildDirty={} jarSha256={} source={}",
                VERSION,
                buildAttribute(Attributes.Name.IMPLEMENTATION_VERSION.toString()),
                buildAttribute("Build-Commit"),
                buildAttribute("Build-Branch"),
                buildAttribute("Build-Dirty"),
                jarSha256(),
                codeSource());
        Microbot.pauseAllScripts.compareAndSet(true, false);
        overlayManager.add(overlay);
        overlayManager.add(houseOverlay);
        Microbot.getClientThread().invoke(() -> {
            ItemContainer inventory = Microbot.getClient().getItemContainer(InventoryID.INV);
            supplies = inventory == null ? null : BlackjackSupplies.from(inventory.getItems());
        });
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
        overlayManager.remove(houseOverlay);
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        script.onCameraClientTick();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.INV)
        {
            supplies = BlackjackSupplies.from(event.getItemContainer().getItems());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        script.onChatMessage(event.getMessage());
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event)
    {
        if (event.getActor() instanceof NPC)
        {
            script.onOverheadTextChanged((NPC) event.getActor(), event.getOverheadText());
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        script.onStatChanged(event.getSkill(), event.getXp());
    }

    private static String buildAttribute(String name)
    {
        try
        {
            URL source = BlackjackPlugin.class.getProtectionDomain().getCodeSource().getLocation();
            File file = new File(source.toURI());
            if (!file.isFile())
            {
                return "development";
            }
            try (JarFile jar = new JarFile(file))
            {
                if (jar.getManifest() == null)
                {
                    return "unknown";
                }
                String value = jar.getManifest().getMainAttributes().getValue(name);
                return value == null || value.isEmpty() ? "unknown" : value;
            }
        }
        catch (Exception ignored)
        {
            return "unknown";
        }
    }

    private static String codeSource()
    {
        try
        {
            return BlackjackPlugin.class.getProtectionDomain().getCodeSource().getLocation().toString();
        }
        catch (Exception ignored)
        {
            return "unknown";
        }
    }

    private static String jarSha256()
    {
        try
        {
            URL source = BlackjackPlugin.class.getProtectionDomain().getCodeSource().getLocation();
            File file = new File(source.toURI());
            if (!file.isFile())
            {
                return "development";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file.toPath()))
            {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1)
                {
                    digest.update(buffer, 0, read);
                }
            }

            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest())
            {
                hash.append(String.format("%02X", value));
            }
            return hash.toString();
        }
        catch (Exception ignored)
        {
            return "unknown";
        }
    }
}
