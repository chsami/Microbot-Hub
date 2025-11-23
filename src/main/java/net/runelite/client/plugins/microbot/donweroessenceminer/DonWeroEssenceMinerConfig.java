package net.runelite.client.plugins.microbot.donweroessenceminer;

import net.runelite.client.config.*;

@ConfigGroup("DonWeroEssenceMiner")
public interface DonWeroEssenceMinerConfig extends Config {
    @ConfigItem(
            keyName = "Guide",
            name = "Usage guide",
            description = "Usage guide",
            position = 1
    )
    default String GUIDE() {
        return  "Start anywhere with a pickaxe in your inventory or equipped.\n\n" +
                "The bot will:\n" +
                "1. Walk to Aubury in Varrock\n" +
                "2. Teleport to Essence Mine\n" +
                "3. Mine Rune Essence\n" +
                "4. Exit via portal when full\n" +
                "5. Bank and repeat\n\n" +
                "Created by Don Wero";
    }
}
