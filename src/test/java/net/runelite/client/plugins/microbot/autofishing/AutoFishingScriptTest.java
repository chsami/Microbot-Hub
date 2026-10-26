package net.runelite.client.plugins.microbot.autofishing;

import net.runelite.client.plugins.microbot.autofishing.enums.Fish;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoFishingScriptTest {
    @Test
    void karambwanjiBaitIsRequiredItemNotCatch() {
        assertTrue(AutoFishingScript.isRequiredItem(Fish.KARAMBWAN, "Raw karambwanji"));
        assertFalse(AutoFishingScript.isRequiredItem(Fish.KARAMBWAN, "Raw karambwan"));
    }

    @Test
    void caughtFishAreNeverRequiredItems() {
        for (Fish fish : Fish.values()) {
            for (String item : fish.getItemNames()) {
                assertFalse(AutoFishingScript.isRequiredItem(fish, item), fish + " " + item);
            }
        }
    }

    @Test
    void consumablesAreRecognised() {
        assertTrue(AutoFishingScript.isConsumable("Feather"));
        assertTrue(AutoFishingScript.isConsumable("Fishing bait"));
        assertTrue(AutoFishingScript.isConsumable("Dark fishing bait"));
        assertTrue(AutoFishingScript.isConsumable("Sandworms"));
        assertTrue(AutoFishingScript.isConsumable("Raw karambwanji"));
    }

    @Test
    void toolsAreNotConsumables() {
        assertFalse(AutoFishingScript.isConsumable("Fly fishing rod"));
        assertFalse(AutoFishingScript.isConsumable("Oily fishing rod"));
        assertFalse(AutoFishingScript.isConsumable("Karambwan vessel"));
        assertFalse(AutoFishingScript.isConsumable("Barbarian rod"));
        assertFalse(AutoFishingScript.isConsumable("Lobster pot"));
        assertFalse(AutoFishingScript.isConsumable("Big fishing net"));
        assertFalse(AutoFishingScript.isConsumable("Hammer"));
        assertFalse(AutoFishingScript.isConsumable("Knife"));
    }
}
