package net.runelite.client.plugins.microbot.jewelleryenchant;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.jewelleryenchant.util.Jewellery;

@ConfigGroup("jewelleryenchant")
@ConfigInformation(
        "▪ This plugin enchants all types of jewellery using the standard spellbook.<br /><br />" +
                "▪ Select the jewellery to enchant from the dropdown menu.<br /><br />" +
                "▪ Ensure your bank contains:<br />" +
                "  - The unenchanted jewellery you wish to make.<br />" +
                "  - Cosmic runes.<br />" +
                "  - Elemental runes (Water, Air, etc.) OR an elemental staff.<br /><br />" +
                "▪ The script will automatically swap staves if available in your bank."
)
public interface JewelleryEnchantConfig extends Config {
    @ConfigItem(
            keyName = "jewellery",
            name = "Jewellery",
            description = "The jewellery to enchant",
            position = 1
    )
    default Jewellery jewellery() {
        return Jewellery.SAPPHIRE_RING;
    }
}