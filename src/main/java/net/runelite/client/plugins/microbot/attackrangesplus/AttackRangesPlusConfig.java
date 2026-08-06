package net.runelite.client.plugins.microbot.attackrangesplus;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

import java.awt.Color;

@ConfigGroup("attackrangesplus")
@ConfigInformation("<h2>Attack Ranges Plus</h2>" +
        "<h3>Version: " + AttackRangesPlusPlugin.version + "</h3>" +
        "<p>This overlay draws your attack range on the ground, clipped to line of sight. It runs no script and never moves or banks for you.</p>" +
        "<p></p>" +
        "<p>1. <strong>Attack style:</strong> how the range is sized. Auto reads your weapon and falls back to melee. Pick Melee, Ranged, or Magic to force a size. Use Magic when casting at 10 tiles.</p>" +
        "<p></p>" +
        "<p>2. <strong>Line color:</strong> the color of the range outline.</p>" +
        "<p></p>" +
        "<p>3. <strong>Fill area:</strong> shades the tiles inside your range. The shading is redrawn every frame and can cost FPS at large ranges like magic. Leave it off for the cheapest outline only overlay.</p>" +
        "<p></p>" +
        "<p>4. <strong>Fill color:</strong> the color and opacity of the shaded area. Used only when Fill area is on.</p>" +
        "<p></p>" +
        "<p>5. <strong>Show overlay:</strong> when to draw it. Always shows it everywhere. In PvP areas limits it to the Wilderness, PvP and Deadman worlds, and PvP flagged zones.</p>" +
        "<p></p>" +
        "<p>6. <strong>Show target's range:</strong> also outline the range of the player you are fighting. Their exact style is not knowable, so this is their weapon's base reach.</p>" +
        "<p></p>" +
        "<p>7. <strong>Target line color:</strong> the outline color used for your target's range.</p>")
public interface AttackRangesPlusConfig extends Config
{
    @ConfigItem(
            keyName = "style",
            name = "Attack style",
            description = "How to size the overlay. Auto detects ranged weapons and falls back to melee. Pick Magic when casting (10 tiles).",
            position = 0
    )
    default RangeMode style()
    {
        return RangeMode.AUTO;
    }

    @Alpha
    @ConfigItem(
            keyName = "borderColor",
            name = "Line color",
            description = "Color of the attack-range outline.",
            position = 1
    )
    default Color borderColor()
    {
        return Color.WHITE;
    }

    @ConfigItem(
            keyName = "showFill",
            name = "Fill area",
            description = "Shade the tiles inside your attack range. Note: the fill is repainted every frame and costs FPS at large ranges (e.g. magic). Leave off for the cheapest outline-only overlay.",
            position = 2
    )
    default boolean showFill()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
            keyName = "fillColor",
            name = "Fill color",
            description = "Color and opacity of the shaded area (used only when Fill area is on).",
            position = 3
    )
    default Color fillColor()
    {
        return new Color(0, 0, 0, 35);
    }

    @ConfigItem(
            keyName = "displayMode",
            name = "Show overlay",
            description = "When to show the overlay. 'In PvP areas' covers the Wilderness, PvP/Deadman worlds, and PvP-flagged zones.",
            position = 4
    )
    default DisplayMode displayMode()
    {
        return DisplayMode.ALWAYS;
    }

    @ConfigItem(
            keyName = "showOpponent",
            name = "Show target's range",
            description = "Also outline the attack range of the player you are fighting. Their exact style is not knowable, so this is their weapon's base reach.",
            position = 5
    )
    default boolean showOpponent()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
            keyName = "opponentColor",
            name = "Target line color",
            description = "Outline color for your target's range.",
            position = 6
    )
    default Color opponentColor()
    {
        return new Color(255, 64, 64);
    }
}
