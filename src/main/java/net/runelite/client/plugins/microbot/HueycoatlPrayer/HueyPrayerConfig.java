package net.runelite.client.plugins.microbot.huey;

import net.runelite.client.config.*;

@ConfigGroup("hueyprayer")
public interface HueyPrayerConfig extends Config
{
    @ConfigItem(
            keyName = "enabled",
            name = "Enable",
            description = "Enable auto prayer"
    )
    default boolean enabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug Projectiles",
            description = "Print projectile IDs"
    )
    default boolean debug()
    {
        return false;
    }
}