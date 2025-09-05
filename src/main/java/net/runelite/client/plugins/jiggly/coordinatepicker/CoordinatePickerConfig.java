import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("coordinate-picker")
@ConfigInformation(
        "<html>" +
                "<div style='margin:0; padding:0;'>If you have any questions, please contact <b>heapoverfl0w</b> in the <b>Microbot</b> Discord!</div>" +
                "<br/>" +
                "<b>Requirements:</b>" +
                "<ul style='margin:0; padding-left: 10px; list-style-position: inside;'>" +
                "<li>Glassblowing pipe in inventory</li>" +
                "<li>Coins ≥ 1,000</li>" +
                "<li>Astral runes ≥ 2</li>" +
                "<li>Either Air runes ≥ 10 or wear Staff of air / Air battlestaff</li>" +
                "<li>Either Fire runes ≥ 6 or wear Staff of fire / Fire battlestaff</li>" +
                "<li>Be near a Trader Crewmember (no walking logic is included)</li>" +
                "</ul>" +
                "</html>"
)
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
