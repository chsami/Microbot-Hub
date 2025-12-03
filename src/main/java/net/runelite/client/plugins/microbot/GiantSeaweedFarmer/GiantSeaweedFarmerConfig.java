package net.runelite.client.plugins.microbot.GiantSeaweedFarmer;

import net.runelite.client.config.*;

@ConfigInformation("Farms giant seaweed underwater on the fossil island. Can be started anywhere. Ensure you have the fishbowl helmet and apparatus in your bank.")
@ConfigGroup("GiantSeaweedFarmer")
public interface GiantSeaweedFarmerConfig extends Config {

    @ConfigItem(
            keyName = "Mode",
            name = "Mode",
            description = "Infinite mode will farm indefinitely. Run once will plant seeds and/or harvest, then go to bank if returnbank is on",
            position = 0
    )
    default modeType modeType() {
        return modeType.RUN_ONCE;
    }

    enum modeType {
        RUN_ONCE,
        RUN_ONCE_THEN_BANK,
        INFINITE
    }

    @ConfigItem(
            keyName = "override",
            name = "Overide Start State?",
            description = "Should we use digsite pendant from inventory/bank?",
            position = 0
    )
    default boolean override() {
        return false;
    }

    @ConfigItem(
            keyName = "StartState",
            name = "Start State",
            description = "Lets you change where to start the script",
            position = 0
    )
    default GiantSeaweedFarmerStatus startState() {
        return GiantSeaweedFarmerStatus.TRAVELLING;
    }

    @ConfigItem(
            keyName = "compostType",
            name = "Compost type",
            description = "Which compost type should be used?",
            position = 0,
            section = generalSection
    )
    default CompostType compostType() {
        return CompostType.NONE;
    }

    enum CompostType {
        NONE,
        COMPOST,
        SUPERCOMPOST,
        ULTRACOMPOST,
        BOTTOMLESS_COMPOST_BUCKET,

    }



    @ConfigSection(
            name = "Transport",
            description = "Transport Plugin Settings",
            position = 1
    )
    String transportSection = "transport";

    @ConfigSection(
            name = "Inventory & Bank",
            description = "General Plugin Settings",
            position = 2
    )
    String bankSection = "bank";

    @ConfigSection(
            name = "General",
            description = "General Plugin Settings",
            position = 2
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Debug",
            description = "Debug Settings",
            position = 3
    )
    String debugSection = "debug";



    @ConfigItem(
            keyName = "digsitependant",
            name = "Invent/Bank Digsite Pendant?",
            description = "Should we use digsite pendant from inventory/bank?",
            position = 1,
            section = transportSection
    )
    default boolean DigsitePendant() {
        return false;
    }

    @ConfigItem(
            keyName = "digsitehouse",
            name = "FaGuild->Tree->House->Pendant?",
            description = "Get to house and use Mounted Digsite Pendant there",
            position = 2,
            section = transportSection
    )
    default boolean DigsiteInHouse() {return false; }


    @ConfigItem(
            keyName = "DepositEquipment",
            name = "Bank Equipment",
            description = "Desposit All Items at start of script",
            position = 1,
            section = bankSection
    )
    default boolean DepositEquipment() {return false;}

    @ConfigItem(
            keyName = "DepositStuff",
            name = "Bank Inventory",
            description = "Desposit All Inventory Items at start of script",
            position = 2,
            section = bankSection
    )
    default boolean DepositInventory() {return false;}

    @ConfigItem(
            keyName = "seedDibber",
            name = "Disable Seed Dibber",
            description = "If you've completed barb training then you dont need a seed dibber and you can enable this",
            position = 3,
            section = bankSection
    )
    default boolean NoSeedDibber() {
        return true;
    }

    @ConfigItem(
            keyName = "rake",
            name = "Disable Rake",
            description = "If you have auto rake weed from tithe farm on, then put this to yes",
            position = 4,
            section = bankSection
    )
    default boolean NoRake() {
        return true;
    }

    @ConfigItem(
            keyName = "Farming Cape?",
            name = "Farming Cape",
            description = "If you have a farming cape then check this to use it, for extra yield",
            position = 5,
            section = generalSection
    )
    default boolean FarmingCape() {
        return true;
    }

    @ConfigItem(
            keyName = "GSFAntiban",
            name = "Anti Ban On?",
            description = "When ticked, this script will set/handle antiban settings.",
            position = 0,
            section = generalSection
    )
    default boolean useAntiBan() {
        return false;
    }


    @ConfigItem(
                keyName = "lootSeaweedSpores",
                name = "Loot seaweed spores",
                description = "Pick up seaweed spores when they spawn on the ground",
                position = 1,
                section = generalSection
        )
    default boolean lootSeaweedSpores() {
            return true;
        }

    @ConfigItem(
            keyName = "LOGDEBUG",
            name = "Debug Logs",
            description = "Debug Logs",
            position = 0,
            section = debugSection
    )
    default boolean logDebug() {
        return true;
    }




}
