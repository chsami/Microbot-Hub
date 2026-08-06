package net.runelite.client.plugins.microbot.karambwans;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.karambwans.enums.KarambwanBankLocation;
import net.runelite.client.plugins.microbot.karambwans.enums.FairyRingAccessMethod;

@ConfigGroup("GabulhasKarambwans")
@ConfigInformation(
        "<h3>Setup Instructions</h3>" +
        "<ol>" +
                "<li>Configure the fairy rings to DKP (last destination must be DKP).</li>" +
                "<li>Make sure to have a karambwan vessel and raw karambwanji in your inventory.</li>" +
                "<li>Start the script next to the karambwan fishing spot.</li>" +
        "</ol>" +
        "<h3>Teleports & Banking</h3>" +
        "<ul>" +
                "<li><b>POH Fairy Ring:</b> Supports Construct./Max cape, House tabs, or House teleport spells.</li>" +
                "<li><b>Seers Bank:</b> Requires Kandarin Hard Diary completed and Camelot teleport runes. The script will cast the Seers' teleport variant.</li>" +
        "</ul>" +
        "<br><i>Note: The script does <b>not</b> automatically restock teleports. Make sure to bring enough runes, a rune pouch, or teleport tabs for your session.</i>"
)
public interface GabulhasKarambwansConfig extends Config {

    String startingStateSection = "startingStateSection";

    @ConfigSection(
            name = "Teleports",
            description = "Teleportation methods to bank and return to DKP",
            position = 1,
            closedByDefault = false
    )
    String teleportsSection = "teleportsSection";

    @ConfigItem(
            keyName = "bankLocation",
            name = "Bank Location",
            description = "Bank location to teleport to.",
            position = 1,
            section = teleportsSection
    )
    default KarambwanBankLocation bankLocation() {
        return KarambwanBankLocation.NONE;
    }

    @ConfigItem(
            keyName = "fairyRingAccessMethod",
            name = "Fairy Ring Access",
            description = "How to access the fairy ring to return to DKP.",
            position = 2,
            section = teleportsSection
    )
    default FairyRingAccessMethod fairyRingAccessMethod() {
        return FairyRingAccessMethod.ZANARIS_WALK;
    }
    @ConfigItem(
            keyName = "startingState",
            name = "(Debug) Starting State",
            description = "Starting State. Only used for development.",
            position = 0,
            section = startingStateSection
    )
    default GabulhasKarambwansInfo.states STARTING_STATE() {
        return GabulhasKarambwansInfo.states.FISHING;
    }
}


