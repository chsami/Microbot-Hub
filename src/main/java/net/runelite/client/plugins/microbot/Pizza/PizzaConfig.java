package net.runelite.client.plugins.microbot.Pizza;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;

@ConfigGroup("Pizza")
@ConfigInformation(
        "Required ingredients (5 of each per batch):\n" +
                "- Bucket of water\n" +
                "- Pot of flour\n" +
                "- Cheese\n" +
                "- Tomato\n\n" +
                "Recommendation:\n" +
                "Keep at least 1000 of each ingredient in your bank."
)
public interface PizzaConfig extends Config {
}
