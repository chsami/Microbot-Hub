package net.runelite.client.plugins.microbot.farmingcontract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.*;

@ConfigInformation("Automates Farming Guild contracts end-to-end.<br/><br/>" +
        "<b>Start:</b> anywhere — the plugin walks to the guild.<br/>" +
        "Have seeds, compost, rake, spade, and seed dibber banked.<br/><br/>" +
        "<b>Cycle:</b> get contract → bank → compost & plant → harvest → turn in → repeat.<br/>" +
        "Auto tier picks the highest contract your level supports.")
@ConfigGroup("farmingcontract")
public interface FarmingContractConfig extends Config {

    @ConfigSection(
        name = "Contract",
        description = "Contract preferences",
        position = 0
    )
    String contractSection = "contract";

    @ConfigItem(
        keyName = "contractTier",
        name = "Contract Tier",
        description = "Which contract tier to request from Jane",
        position = 1,
        section = contractSection
    )
    default ContractTier contractTier() {
        return ContractTier.AUTO;
    }

    @ConfigItem(
        keyName = "autoDowngrade",
        name = "Auto Downgrade",
        description = "Request an easier contract if seeds are unavailable",
        position = 2,
        section = contractSection
    )
    default boolean autoDowngrade() {
        return true;
    }

    @ConfigItem(
        keyName = "downgradeTree",
        name = "Downgrade Trees",
        description = "Automatically downgrade tree contracts (slow to grow)",
        position = 3,
        section = contractSection
    )
    default boolean downgradeTree() {
        return false;
    }

    @ConfigItem(
        keyName = "downgradeFruitTree",
        name = "Downgrade Fruit Trees",
        description = "Automatically downgrade fruit tree contracts (slow to grow)",
        position = 4,
        section = contractSection
    )
    default boolean downgradeFruitTree() {
        return false;
    }

    @ConfigSection(
        name = "Farming",
        description = "Farming preferences",
        position = 10
    )
    String farmingSection = "farming";

    @ConfigItem(
        keyName = "compostType",
        name = "Compost Type",
        description = "Type of compost to apply before planting",
        position = 11,
        section = farmingSection
    )
    default CompostType compostType() {
        return CompostType.ULTRACOMPOST;
    }

    @ConfigItem(
        keyName = "enableComposting",
        name = "Compost Bin",
        description = "Fill big compost bin with pineapples and collect ultracompost before doing contracts",
        position = 12,
        section = farmingSection
    )
    default boolean enableComposting() {
        return false;
    }

    @ConfigItem(
        keyName = "protectTrees",
        name = "Protect Trees",
        description = "Pay the gardener to protect tree and fruit tree contracts while they grow",
        position = 13,
        section = farmingSection
    )
    default boolean protectTrees() {
        return false;
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractTier {
        AUTO("Auto"),
        EASY("Easy"),
        MEDIUM("Medium"),
        HARD("Hard");

        private final String label;

        @Override
        public String toString() {
            return label;
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum CompostType {
        NONE("None", -1),
        COMPOST("Compost", 6032),
        SUPERCOMPOST("Supercompost", 6034),
        ULTRACOMPOST("Ultracompost", 21483);

        private final String name;
        private final int itemId;

        @Override
        public String toString() {
            return name;
        }
    }
}
