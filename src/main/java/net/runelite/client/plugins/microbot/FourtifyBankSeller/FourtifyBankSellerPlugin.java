package net.runelite.client.plugins.microbot.FourtifyBankSeller;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.fourtify.core.client.PluginName;
import javax.inject.Inject;

@PluginDescriptor(
        name = PluginName.PREFIX + "Fourtify Bank Seller",
        description = "At the Grand Exchange, sells all eligible bank notes and stackables, highest stack value first, at 1 gp each",
        tags = {"fourtify", "bank", "grand exchange", "sell"},
        authors = {"Fourtifys"},
        version = FourtifyBankSellerPlugin.version,
        minClientVersion = "2.6.42",
        iconUrl = "https://chsami.github.io/Microbot-Hub/FourtifyBankSellerPlugin/assets/icon.png",
        cardUrl = "https://chsami.github.io/Microbot-Hub/FourtifyBankSellerPlugin/assets/card.png",
        enabledByDefault = false,
        isExternal = true
)
public class FourtifyBankSellerPlugin extends Plugin {
    /** The Hub build reads this constant for the jar's file name and the plugin list. */
    static final String version = "1.0.0";
    @Inject private FourtifyBankSellerScript script;
    @Inject private FourtifyBankSellerConfig config;

    @Provides
    FourtifyBankSellerConfig provideConfig(ConfigManager manager) { return manager.getConfig(FourtifyBankSellerConfig.class); }

    @Override protected void startUp() { script.run(config); }
    @Override protected void shutDown() { script.shutdown(); }
}
