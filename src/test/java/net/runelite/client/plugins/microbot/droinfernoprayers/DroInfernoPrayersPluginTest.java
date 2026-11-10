package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.nio.file.Files;
import java.nio.file.Paths;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroInfernoPrayersPluginTest
{
    private final PluginDescriptor descriptor = DroInfernoPrayersPlugin.class.getAnnotation(PluginDescriptor.class);

    @Test public void publicPluginLoaderAcceptsThePlugin() {
        // The client loader only takes classes whose direct parent is Plugin.
        assertEquals(Plugin.class, DroInfernoPrayersPlugin.class.getSuperclass());
        assertTrue(descriptor.isExternal());
        assertFalse(descriptor.enabledByDefault());
    }

    @Test public void descriptorNamesThePrayersPluginAndItsVersion() {
        assertEquals("[Dro] Inferno Prayers", descriptor.name());
        assertEquals("1.0.10", descriptor.version());
        assertEquals("2.6.55", descriptor.minClientVersion());
        assertArrayEquals(new String[]{"droplugins"}, descriptor.authors());
    }

    @Test public void catalogImagesHaveMatchingDocumentationAssets() {
        assertEquals("https://chsami.github.io/Microbot-Hub/DroInfernoPrayersPlugin/assets/icon.png", descriptor.iconUrl());
        assertEquals("https://chsami.github.io/Microbot-Hub/DroInfernoPrayersPlugin/assets/card.png", descriptor.cardUrl());
        String docs = "src/main/resources/net/runelite/client/plugins/microbot/droinfernoprayers/docs/";
        assertTrue(Files.isRegularFile(Paths.get(docs + "assets/icon.png")));
        assertTrue(Files.isRegularFile(Paths.get(docs + "assets/card.png")));
        assertTrue(Files.isRegularFile(Paths.get(docs + "README.md")));
    }

    @Test public void prayersOnlyIsTheDefaultAndFullAutomaticCannotBeChosen() {
        DroInfernoPrayersConfig config = new DroInfernoPrayersConfig() { };
        assertEquals(ArenaAssistMode.PRAYERS_ONLY, config.mode());
        for (ArenaAssistMode mode : ArenaAssistMode.values()) {
            assertTrue(mode == ArenaAssistMode.PRAYERS_ONLY || mode == ArenaAssistMode.AUTO_NO_MOVEMENT, mode.name());
        }
        DroInfernoPrayersPlugin plugin = new DroInfernoPrayersPlugin();
        assertEquals(ArenaAssistMode.PRAYERS_ONLY, plugin.mode(), "unset settings fall back to Prayers only");
        assertTrue(plugin.prayersOnly());
        assertFalse(plugin.manualMovement());
        assertEquals(ArenaFightInput.INVOKES, plugin.fightInput());
    }

    @Test public void aioStartupSettingsAreHiddenAndOff() throws Exception {
        DroInfernoPrayersConfig config = new DroInfernoPrayersConfig() { };
        assertFalse(config.automateStartup());
        assertNull(config.setup());
        for (String key : new String[]{"prayersOnly", "manualMovement", "fightInput", "automateStartup", "setup", "purchaseBudget", "coinReserve", "maxUnitPrice", "priceMarkupPercent", "offerWaitSeconds", "buyRunePouchNote", "sacrificeFirecape"}) {
            ConfigItem item = DroInfernoPrayersConfig.class.getMethod(key).getAnnotation(ConfigItem.class);
            assertNotNull(item, key);
            assertTrue(item.hidden(), key + " must not show in the settings panel");
        }
    }
}
