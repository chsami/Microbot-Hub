package net.runelite.client.plugins.microbot.vorkathhelper;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("vorkathhelper")
public interface VorkathHelperConfig extends Config
{

    @ConfigSection(
            name = "Special Overlay",
            description = "",
            position = 0
    )
    String specialSection = "Special Settings";

    @ConfigSection(
            name = "Zombie Overlay",
            description = "",
            position = 1
    )
    String zombieSection = "Zombie Settings";

    @ConfigSection(
            name = "Acid Overlay",
            description = "",
            position = 2
    )
    String acidSection = "Acid Settings";

    @ConfigSection(
            name = "Fireball Overlay",
            description = "",
            position = 3
    )
    String fireballSection = "Fireball Settings";

    @ConfigItem(
            position = 0,
            section = specialSection,
            keyName = "Enable Vorkath Special Count Overlay",
            name = "Enable Vorkath Special Count Overlay",
            description = "Enable Vorkath Special Count Overlay"
    )
    default boolean vorkathSpecialOverlay()
    {
        return true;
    }

    // Zombie Config
    @ConfigItem(
            position = 0,
            section = zombieSection,
            keyName = "Enable Zombie Spawn Overlay",
            name = "Enable Zombie Spawn Overlay",
            description = "Enable Zombie Spawn Overlay"
    )
    default boolean zombieSpawnOverlay()
    {
        return true;
    }

    @ConfigItem(
            position = 1,
            section = zombieSection,
            keyName = "Auto Crumble Undead",
            name = "Auto Crumble Undead",
            description = "Cast crumble undead (no slayer staff required)"
    )
    default boolean autoCastCrumbleUndead()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
            position = 2,
            section = zombieSection,
            keyName = "zombieTileColor",
            name = "Zombie Tile Color",
            description = "Color of the zombie spawn tile"
    )
    default Color zombieTileColor()
    {
        return new Color(255, 0, 0, 80);
    }
    @Alpha
    @ConfigItem(
            position = 3,
            section = zombieSection,
            keyName = "zombieLineColor",
            name = "Zombie Line Color",
            description = "Color of the zombie spawn line"
    )
    default Color zombieLineColor()
    {
        return new Color(255, 0, 0, 100);
    }

    @ConfigItem(
            position = 4,
            section = zombieSection,
            keyName = "zombieOutlineColor",
            name = "Zombie Outline Color",
            description = "Color of zombie outlines and text"
    )
    default Color zombieOutlineColor()
    {
        return Color.RED;
    }

    @ConfigItem(
            position = 0,
            section = fireballSection,
            keyName = "Enable Fire Ball Overlay",
            name = "Enable Fire Ball Overlay",
            description = "Enable Fire Ball Overlay"
    )
    default boolean fireBallOverlay()
    {
        return true;
    }
    @Alpha
    @ConfigItem(
            position = 1,
            section = fireballSection,
            keyName = "fireballDangerColor",
            name = "Fireball Danger Color",
            description = "Fireball explosion area color"
    )
    default Color fireballDangerColor()
    {
        return new Color(255, 0, 0, 60);
    }

    @ConfigItem(
            position = 0,
            section = acidSection,
            keyName = "Enable Acid Overlay",
            name = "Enable Acid Overlay",
            description = "Enable Acid Overlay"
    )
    default boolean acidOverlay()
    {
        return true;
    }
    @Alpha
    @ConfigItem(
            position = 1,
            section = acidSection,
            keyName = "acidFillColor",
            name = "Acid Fill Color",
            description = "Acid tile fill color"
    )
    default Color acidFillColor()
    {
        return new Color(255, 0, 0, 110);
    }

    @ConfigItem(
            position = 2,
            section = acidSection,
            keyName = "acidBorderColor",
            name = "Acid Border Color",
            description = "Acid tile border color"
    )
    default Color acidBorderColor()
    {
        return Color.RED;
    }

}