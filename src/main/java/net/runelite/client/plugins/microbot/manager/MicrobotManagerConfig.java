package net.runelite.client.plugins.microbot.manager;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("microbot-manager")
public interface MicrobotManagerConfig extends Config {
    @ConfigItem(
        keyName = "signalRUrl",
        name = "SignalR Service URL",
        description = "URL of the .NET SignalR service"
    )
    default String signalRUrl() {
        return "http://localhost:5000/signalr";
    }
}
