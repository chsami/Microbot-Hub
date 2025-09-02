package net.runelite.client.plugins.microbot.chartercrafter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("chartercrafter")
public interface CharterCrafterConfig extends Config {

    enum Product {
        BEER_GLASS("Beer glass", "Beer glass"),
        CANDLE_LANTERN("Candle lantern", "Empty candle lantern"),
        OIL_LAMP("Oil lamp", "Empty oil lamp"),
        VIAL("Vial", "Vial"),
        FISHBOWL("Fishbowl", "Empty fishbowl"),
        UNPOWERED_STAFF_ORB("Unpowered staff orb", "Unpowered orb"),
        LANTERN_LENS("Lantern lens", "Lantern lens"),
        LIGHT_ORB("Light orb", "Empty light orb");

        private final String widgetName;
        private final String sellName;
        Product(String widgetName, String sellName) { this.widgetName = widgetName; this.sellName = sellName; }
        @Override public String toString() { return widgetName; }
        public String widgetName() { return widgetName; }
        public String sellName() { return sellName; }
    }

    @ConfigItem(
            keyName = "product",
            name = "Product",
            description = "Glass item to make"
    )
    default Product product() {
        return Product.BEER_GLASS;
    }

    
}
