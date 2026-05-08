package net.runelite.client.plugins.microbot.banksshopper;

import net.runelite.client.config.*;

@ConfigGroup(BanksShopperConfig.configGroup)
@ConfigInformation(
        "The Banks Shopper plugin automates buying and selling items from NPC shops.<br>" +
                "Features include:<br>" +
                "<ul>" +
                "<li>Set buy/sell mode and quantity.</li>" +
                "<li>Use the bank when inventory is full.</li>" +
                "<li>Logout when out of supply.</li>" +
                "<li>Hop to the next world for better stock.</li>" +
                "<li>Specify items to trade and maintain minimum stock.</li>" +
                "<li>Trade with specific NPCs using exact naming if needed.</li>" +
                "<li>Unlimited Stock mode: loops buying until full, then banks (no hopping).</li>" +
                "<li>Fast Mode: skips closing bank/shop interfaces for faster cycles.</li>" +
                "<li>Set shop coordinates for precise walk-back after banking.</li>" +
                "</ul>"
)
public interface BanksShopperConfig extends Config {

    String configGroup = "banks-shopper";
    String npcName = "npcName";
    String itemNames = "itemNames";
    String minStock = "minStock";
    String action = "action";
    String quantity = "quantity";
    String useBank = "useBank";
    String logout = "logout";
    String useNextWorld = "useNextWorld";
        String blastFurnaceOptimization = "blastFurnaceOptimization";
        String useKeyboardWorldHop = "useKeyboardWorldHop";
        String unlimitedStock = "unlimitedStock";
        String fastMode = "fastMode";
        String bankName = "bankName";
        String shopX = "shopX";
        String shopY = "shopY";
        String shopZ = "shopZ";
        String useGameObject = "useGameObject";
        String shopAction = "shopAction";

    @ConfigSection(
            name = "Action Settings",
            description = "Action Settings",
            position = 0,
            closedByDefault = false
    )
    String actionSection = "actionSection";

    @ConfigSection(
            name = "Shop Settings",
            description = "Shop Settings",
            position = 1,
            closedByDefault = false
    )
    String shopSection = "shopSection";

    @ConfigSection(
            name = "Item Settings",
            description = "Item Settings",
            position = 2,
            closedByDefault = false
    )
    String itemSection = "itemSection";

    @ConfigItem(
            position = 0,
            keyName = action,
            name = "Action",
            description = "Set Buy/Sell Mode",
            section = actionSection
    )
    default Actions action() {
        return Actions.BUY;
    }

    @ConfigItem(
            position = 1,
            keyName = quantity,
            name = "Quantity",
            description = "Set Buy/Sell Quantity",
            section = actionSection
    )
    default Quantities quantity() {
        return Quantities.FIFTY;
    }

    @ConfigItem(
            position = 2,
            keyName = useBank,
            name = "Use Bank",
            description = "Use bank if your inventory is full",
            section = actionSection
    )
    default boolean useBank() {
        return true;
    }

    @ConfigItem(
            position = 3,
            keyName = logout,
            name = "Logout when out of supply",
            description = "Logout",
            section = actionSection
    )
    default boolean logout() {
        return true;
    }

    @ConfigItem(
            position = 4,
            keyName = useNextWorld,
            name = "Hop to next world",
            description = "Hop to next world instead of random world",
            section = actionSection
    )
    default boolean useNextWorld() {
        return false;
    }

        @ConfigItem(
                        position = 5,
                        keyName = blastFurnaceOptimization,
                        name = "Blast Furnace optimization",
                        description = "Optimize route at Blast Furnace by banking/interacting directly",
                        section = actionSection
        )
        default boolean blastFurnaceOptimization() {
                return false;
        }

        @ConfigItem(
                        position = 6,
                        keyName = useKeyboardWorldHop,
                        name = "Use Ctrl+Shift+Right to hop",
                        description = "Use keyboard shortcut to hop to the next world",
                        section = actionSection
        )
        default boolean useKeyboardWorldHop() {
                return false;
        }

        @ConfigItem(
                        position = 7,
                        keyName = unlimitedStock,
                        name = "Unlimited Stock",
                        description = "Shop has unlimited stock - keep buying until inventory is full, then bank (no world hopping)",
                        section = actionSection
        )
        default boolean unlimitedStock() {
                return false;
        }

        @ConfigItem(
                        position = 8,
                        keyName = fastMode,
                        name = "Fast Mode",
                        description = "Don't close bank/shop interfaces - interact directly with bank object and shop NPC by name",
                        section = actionSection
        )
        default boolean fastMode() {
                return false;
        }

    @ConfigItem(
            keyName = itemNames,
            name = "Item Name(s)/ID(s)",
            description = "Items to buy or sell, supports comma separated values e.g., Blood Rune,564,Soda Ash",
            position = 0,
            section = itemSection
    )

    default String itemNames() {
        return "item1,item2,item3";
    }

    @ConfigItem(
            keyName = minStock,
            name = "Minimum Stock",
            description = "Minimum stock to maintain. Won't buy below or sell above this value.",
            position = 1,
            section = itemSection
    )

    default int minimumStock() {
        return 0;
    }

    @ConfigItem(
            keyName = npcName,
            name = "NPC/Object Name",
            description = "Name of the NPC or Game Object to trade with (e.g., Shop keeper, Culinaromancer's chest)",
            position = 0,
            section = shopSection
    )

    default String npcName() {
        return "";
    }

    @ConfigItem(
            position = 1,
            keyName = "useExactNaming",
            name = "Use exact NPC naming",
            description = "Use exact NPC naming - Useful for places like Shantay pass",
            section = shopSection
    )
    default boolean useExactNaming() {
        return true;
    }

    @ConfigItem(
            position = 2,
            keyName = useGameObject,
            name = "Shop is a Game Object",
            description = "Enable if the shop is a Game Object instead of an NPC (e.g., Culinaromancer's chest)",
            section = shopSection
    )
    default boolean useGameObject() {
        return false;
    }

    @ConfigItem(
            keyName = shopAction,
            name = "Shop Action",
            description = "Action to open the shop (e.g., Trade, Buy-food, Buy-items)",
            position = 3,
            section = shopSection
    )
    default String shopAction() {
        return "Trade";
    }

    @ConfigItem(
            keyName = bankName,
            name = "Bank Object/NPC Name",
            description = "Name of the bank booth, chest, or NPC to interact with in Fast Mode (e.g., Bank chest, Bank booth)",
            position = 2,
            section = shopSection
    )
    default String bankName() {
        return "Bank chest";
    }

    @ConfigItem(
            keyName = shopX,
            name = "Shop X",
            description = "X coordinate of the shop location (0 to use current position)",
            position = 3,
            section = shopSection
    )
    default int shopX() {
        return 0;
    }

    @ConfigItem(
            keyName = shopY,
            name = "Shop Y",
            description = "Y coordinate of the shop location (0 to use current position)",
            position = 4,
            section = shopSection
    )
    default int shopY() {
        return 0;
    }

    @ConfigItem(
            keyName = shopZ,
            name = "Shop Plane",
            description = "Plane/floor of the shop location (usually 0)",
            position = 5,
            section = shopSection
    )
    default int shopZ() {
        return 0;
    }
}
