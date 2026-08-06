package net.runelite.client.plugins.microbot.butterflycatcher;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigInformation(
        "<b>Butterfly Catcher</b> — by StonksCode<br/><br/>" +
        "<b>Species (net / barehanded):</b><br/>" +
        "Ruby Harvest 5/15 &nbsp;|&nbsp; Sapphire Glacialis 25/35 &nbsp;|&nbsp; Snowy Knight 35/45<br/>" +
        "Black Warlock 45/55 &nbsp;|&nbsp; Sunlight Moth 65/75 &nbsp;|&nbsp; Moonlight Moth 75/85<br/><br/>" +
        "<b>Barehanded</b> — XP only, nothing in inventory<br/>" +
        "<b>Butterfly Net</b> — equip net first, 10 levels lower requirement"
)
@ConfigGroup("ButterflyCatcher")
public interface ButterflyCatcherConfig extends Config {

    @ConfigItem(
            keyName = "butterflyType",
            name = "Butterfly / Moth",
            description = "<html>Which creature to hunt.<br>"
                    + "<b>Classic:</b> Ruby Harvest, Sapphire Glacialis, Snowy Knight, Black Warlock.<br>"
                    + "<b>Varlamore:</b> Sunlight Moth, Moonlight Moth.<br>"
                    + "Net level shown in parentheses; barehanded = net + 10.</html>",
            position = 0
    )
    default ButterflyType butterflyType() {
        return ButterflyType.BLACK_WARLOCK;
    }

    @ConfigItem(
            keyName = "catchMode",
            name = "Catch Mode",
            description = "<html><b>BAREHANDED:</b> catch and release for XP — nothing enters inventory.<br>"
                    + "<b>BUTTERFLY_NET:</b> use an equipped butterfly net (lower level requirement).</html>",
            position = 1
    )
    default CatchMode catchMode() {
        return CatchMode.BAREHANDED;
    }
}
