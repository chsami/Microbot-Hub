package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bankseller")
public interface BankSellerConfig extends Config {
    @ConfigItem(
            keyName = "instructions",
            name = "Instructions",
            description = "",
            position = 0
    )
    default String instructions() {
        return "1. Start near a bank at the Grand Exchange.\n" +
                "2. The bot will withdraw tradeable items as notes and sell them.";
    }
}