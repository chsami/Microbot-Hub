package net.runelite.client.plugins.microbot.farmingcontract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmingContractCompatibilityTest {

    @Test
    void normalizesLegacyContractDialogueNamesToCurrentProduceNames() {
        assertEquivalent("Potatoes", "Potato");
        assertEquivalent("Tomatoes", "Tomato");
        assertEquivalent("Strawberries", "Strawberry");
        assertEquivalent("Limpwurt roots", "Limpwurt");
        assertEquivalent("White lilies", "White lily");
        assertEquivalent("White lillies", "White lily");
        assertEquivalent("Cadava berries", "Cadavaberry");
        assertEquivalent("Poison ivy berries", "Poison ivy");
        assertEquivalent("Oak tree", "Oak");
        assertEquivalent("Pineapple plant", "Pineapple");
        assertEquivalent("Potato cacti", "Potato cactus");
    }

    private static void assertEquivalent(String dialogueName, String produceName) {
        assertEquals(
            FarmingContractScript.normalizeContractCropName(produceName),
            FarmingContractScript.normalizeContractCropName(dialogueName)
        );
    }
}
