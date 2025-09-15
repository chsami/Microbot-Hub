package net.runelite.client.plugins.microbot.LT.Bones;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.agility.MicroAgilityPlugin;

import javax.inject.Inject;

@PluginDescriptor(

        name = PluginConstants.LT + "Bones Burier",
        description = "Entierra Big Bones automáticamente cerca de un banco",
        authors = { "LT" },
        version = MicroAgilityPlugin.version,
        minClientVersion = "2.0.0",
        tags = {"bones", "bury", "prayer"},
        iconUrl = "",
        cardUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class BonesPlugin extends Plugin {

    @Inject
    private BonesScript script;

    @Override
    protected void startUp() {
        script.run();
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }
}
