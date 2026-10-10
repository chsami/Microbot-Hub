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
                "2. Starting inventory/gear types and matching bank copies\n" +
                "are always protected.\n" +
                "3. Existing GE offers are left untouched and their item\n" +
                "types are not sold.\n" +
                "4. The bot banks first, withdraws other tradeable items as notes\n" +
                "and sells each item's full stack in a single offer.\n" +
                "5. Items the GE refuses (e.g. F2P trade-restricted items)\n" +
                "are put back in the bank and skipped.";
    }
}
