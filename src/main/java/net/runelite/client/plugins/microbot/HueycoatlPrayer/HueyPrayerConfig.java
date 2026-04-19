package net.runelite.client.plugins.microbot.HueycoatlPrayer;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

@ConfigGroup("hueyprayer")
public interface HueyPrayerConfig extends Config
{
    @ConfigSection(
            name = "General",
            description = "Core Huey prayer behaviour",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Trio mode",
            description = "After a Huey projectile hits, set protection for pillar charging (fixed or autobalance)",
            position = 1
    )
    String trioSection = "trio";

    @ConfigItem(
            keyName = "enabled",
            name = "Enable",
            description = "Enable auto prayer",
            position = 0,
            section = generalSection
    )
    default boolean enabled()
    {
        return true;
    }

    @ConfigItem(
            keyName = "disableAfterImpact",
            name = "Disable after impact",
            description = "When enabled, turns off protection prayer after the projectile hits (saves prayer). When disabled, prayers stay on until the next attack switches them.",
            position = 1,
            section = generalSection
    )
    default boolean disableAfterImpact()
    {
        return true;
    }

    @ConfigItem(
            keyName = "debug",
            name = "Debug Projectiles",
            description = "Print projectile IDs",
            position = 2,
            section = generalSection
    )
    default boolean debug()
    {
        return false;
    }

    @ConfigItem(
            keyName = "trioMode",
            name = "Trio mode",
            description = "While incoming Huey projectiles still use the correct protect vs type, after impact (requires Disable after impact) switches to your pillar role: Fixed or Autobalance protection from teammates' overheads.",
            position = 0,
            section = trioSection
    )
    default boolean trioMode()
    {
        return false;
    }

    @ConfigItem(
            keyName = "trioRoleStyle",
            name = "Role style",
            description = "Fixed: always use the protection prayer below. Autobalance: among other players in radius, count melee / missiles / magic overheads — you pray the least-covered protection.",
            position = 1,
            section = trioSection
    )
    default TrioRoleStyle trioRoleStyle()
    {
        return TrioRoleStyle.FIXED;
    }

    @ConfigItem(
            keyName = "trioFixedProtection",
            name = "Fixed protection",
            description = "Used when Role style is Fixed.",
            position = 2,
            section = trioSection
    )
    default TrioFixedProtection trioFixedProtection()
    {
        return TrioFixedProtection.PROTECT_RANGE;
    }

    @ConfigItem(
            keyName = "trioRadius",
            name = "Autobalance radius",
            description = "Tiles from your tile to include other players when autobalancing (they must still be loaded).",
            position = 3,
            section = trioSection
    )
    default int trioRadius()
    {
        return 15;
    }

    enum TrioRoleStyle
    {
        FIXED,
        AUTOBALANCE
    }

    enum TrioFixedProtection
    {
        PROTECT_MELEE(Rs2PrayerEnum.PROTECT_MELEE),
        PROTECT_RANGE(Rs2PrayerEnum.PROTECT_RANGE),
        PROTECT_MAGIC(Rs2PrayerEnum.PROTECT_MAGIC);

        private final Rs2PrayerEnum prayer;

        TrioFixedProtection(Rs2PrayerEnum prayer)
        {
            this.prayer = prayer;
        }

        public Rs2PrayerEnum getPrayer()
        {
            return prayer;
        }
    }
}
