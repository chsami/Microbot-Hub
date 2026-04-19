package net.runelite.client.plugins.microbot.HueycoatlPrayer;

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
            keyName = "disableAfterImpact",
            name = "Disable after impact",
            description = "When enabled, turns off protection prayer after the projectile hits (saves prayer). When disabled, prayers stay on until the next attack switches them."
    )
    default boolean disableAfterImpact()
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