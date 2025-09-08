package net.runelite.client.plugins.microbot.cannonballsmelter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.cannonballsmelter.enums.Furnace;

@ConfigGroup("CannonballSmelter")
@ConfigInformation ("<div style='background-color:black;color:yellow;padding:20px;'>" +
                    "<center><h2>Instructions</h2>" +
                    " <p>Start the script at the bank next to your selected Furnace <br /><br />" +
                    " <b style='color:red;'>MUST</b> have Ammo mould or Double ammo mould <br />" +
                    " and Steel bars in the bank</center></div>")
public interface CannonballSmelterConfig extends Config {
    @ConfigItem(
            keyName = "furnace",
            name = "Furnace",
            description = "Which furnace location to smelt the cannonballs at",
            position = 1
    )
    default Furnace getFurnace() {
        return Furnace.EDGEVILLE;
    }
}
