package net.runelite.client.plugins.microbot.fourtify.core.client;

/**
 * Our plugins' names in the plugin panel, which shows them as HTML: "Fourtify <name>" in our colour, with no
 * Microbot tag in front. Use it in {@code @PluginDescriptor(name = PluginName.PREFIX + "Fourtify Fishing")}. The
 * Hub build reads the text after the {@code +} as the plugin's name, so the name must hold "Fourtify" itself.
 */
public final class PluginName {
    public static final String PREFIX = "<html><font color=#f5dd42>";

    private PluginName() {
    }
}
