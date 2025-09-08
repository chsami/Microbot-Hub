package net.runelite.client.plugins.microbot.coordinatepicker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("coordinate-picker")
public interface CoordinatePickerConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General Settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Show the coordinate picker overlay",
            position = 0,
            section = generalSection
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "maxCoordinates",
            name = "Max Coordinates",
            description = "Maximum number of coordinates to store",
            position = 1,
            section = generalSection
    )
    default int maxCoordinates() {
        return 50;
    }

    @ConfigItem(
            keyName = "showTileOverlays",
            name = "Show Tile Overlays",
            description = "Show overlay markers on picked tiles",
            position = 2,
            section = generalSection
    )
    default boolean showTileOverlays() {
        return true;
    }

    @ConfigItem(
            keyName = "overlayColor",
            name = "Overlay Color",
            description = "Color of the tile overlay markers",
            position = 3,
            section = generalSection
    )
    default java.awt.Color overlayColor() {
        return java.awt.Color.CYAN;
    }
}
