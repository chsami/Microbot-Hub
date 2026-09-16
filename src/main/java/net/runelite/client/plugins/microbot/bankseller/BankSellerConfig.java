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
                "2. The bot banks first, withdraws all tradeable items as notes\n" +
                "and sells each item's full stack in a single offer.\n" +
                "3. Items the GE refuses (e.g. F2P trade-restricted items)\n" +
                "are put back in the bank and skipped.";
    }
}
