package net.runelite.client.plugins.microbot.drozulrah;

import java.nio.file.Files;
import java.nio.file.Paths;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroZulrahDescriptorTest
{
    private final PluginDescriptor descriptor = DroZulrahPlugin.class.getAnnotation(PluginDescriptor.class);

    @Test public void hubRefreshAndUnloadCanRecognizeThisExternalPlugin() {
        assertTrue(descriptor.isExternal(), "Hub sync and unload filter on the external descriptor flag");
        assertFalse(descriptor.enabledByDefault());
        assertEquals("2.6.26", descriptor.minClientVersion());
    }

    @Test public void runtimeLogReportsThePublishedPluginVersion() {
        assertEquals("1.10.17", descriptor.version());
        assertEquals(descriptor.version(), DroZulrahScript.BUILD);
    }

    @Test public void authorAndPrefixAreExplicitWithoutRenamingThePlugin() {
        assertEquals(PluginConstants.DRO + "Zulrah", descriptor.name());
        assertEquals("[Dro] Zulrah", descriptor.name());
        assertArrayEquals(new String[]{"droplugins"}, descriptor.authors());
    }

    @Test public void catalogImagesHaveMatchingDocumentationAssets() {
        String url = "https://chsami.github.io/Microbot-Hub/DroZulrahPlugin/assets/";
        assertEquals(url + "icon.png", descriptor.iconUrl());
        assertEquals(url + "card.png", descriptor.cardUrl());
        String folder = "src/main/resources/net/runelite/client/plugins/microbot/drozulrah/docs/assets/";
        assertTrue(Files.isRegularFile(Paths.get(folder + "icon.png")));
        assertTrue(Files.isRegularFile(Paths.get(folder + "card.png")));
    }
}
