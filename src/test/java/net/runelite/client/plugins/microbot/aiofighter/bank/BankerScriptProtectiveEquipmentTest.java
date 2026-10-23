package net.runelite.client.plugins.microbot.aiofighter.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BankerScriptProtectiveEquipmentTest {

    @Test
    void noSlayerTaskRequiresNoProtectiveEquipment() {
        assertFalse(BankerScript.requiresProtectiveEquipment(null));
    }

    @Test
    void taskWithoutProtectiveEquipmentRequiresNone() {
        assertFalse(BankerScript.requiresProtectiveEquipment("None"));
    }

    @Test
    void taskWithProtectiveEquipmentRequiresIt() {
        assertTrue(BankerScript.requiresProtectiveEquipment("Earmuffs"));
        assertTrue(BankerScript.requiresProtectiveEquipment("Rock hammer"));
    }

    @Test
    void slayerHelmetBypassHandlesNullItem() {
        assertDoesNotThrow(() -> BankerScript.isBypassedBySlayerHelmet(null));
        assertFalse(BankerScript.isBypassedBySlayerHelmet(null));
    }

    @Test
    void slayerHelmetBypassesHeadProtection() {
        assertTrue(BankerScript.isBypassedBySlayerHelmet("Earmuffs"));
        assertTrue(BankerScript.isBypassedBySlayerHelmet("Nose peg"));
        assertFalse(BankerScript.isBypassedBySlayerHelmet("Rock hammer"));
        assertFalse(BankerScript.isBypassedBySlayerHelmet("Mirror shield"));
    }
}
