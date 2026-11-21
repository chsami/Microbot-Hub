package net.runelite.client.plugins.microbot.aiofighter.bank;

import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class BankerScriptRestockSetupTest {

    private static final int COINS = 995;

    private static AIOFighterConfig config(boolean slayerMode, boolean useInventorySetup, boolean hasSetup, boolean hasCurrentSetup) {
        AIOFighterConfig config = mock(AIOFighterConfig.class);
        when(config.slayerMode()).thenReturn(slayerMode);
        when(config.useInventorySetup()).thenReturn(useInventorySetup);
        when(config.inventorySetup()).thenReturn(hasSetup ? mock(InventorySetup.class) : null);
        when(config.currentInventorySetup()).thenReturn(hasCurrentSetup ? mock(InventorySetup.class) : null);
        return config;
    }

    private static InventorySetup setup(String name, Integer... itemIds) {
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn(name);
        List<InventorySetupsItem> items = new java.util.ArrayList<>();
        for (Integer itemId : itemIds) {
            InventorySetupsItem item = mock(InventorySetupsItem.class);
            when(item.getId()).thenReturn(itemId);
            items.add(item);
        }
        when(setup.getInventory()).thenReturn(items);
        return setup;
    }

    @Test
    void nonSlayerSetupRestocksWithoutSlayerCurrentSetup() {
        assertTrue(BankerScript.hasRestockSetup(config(false, true, true, false)));
    }

    @Test
    void nonSlayerWithoutSetupOrWithSetupsOffDoesNotRestock() {
        assertFalse(BankerScript.hasRestockSetup(config(false, true, false, true)));
        assertFalse(BankerScript.hasRestockSetup(config(false, false, true, true)));
    }

    @Test
    void slayerModeStillNeedsCurrentSetup() {
        assertFalse(BankerScript.hasRestockSetup(config(true, true, true, false)));
        assertTrue(BankerScript.hasRestockSetup(config(true, false, false, true)));
    }

    @Test
    void setupWithFoodRestocksFood() {
        assertTrue(BankerScript.setupRestocksFood(
                Collections.singletonList(setup("Melee", COINS, Rs2Food.SHARK.getId())), "melee"));
    }

    @Test
    void setupWithoutFoodDoesNotRestockFood() {
        assertFalse(BankerScript.setupRestocksFood(
                Collections.singletonList(setup("Melee", COINS, -1)), "Melee"));
    }

    @Test
    void missingSetupDoesNotRestockFood() {
        assertFalse(BankerScript.setupRestocksFood(
                Collections.singletonList(setup("Ranged", Rs2Food.SHARK.getId())), "Melee"));
        assertFalse(BankerScript.setupRestocksFood(null, "Melee"));
        assertFalse(BankerScript.setupRestocksFood(Collections.emptyList(), "Melee"));
    }

    @Test
    void fallsBackToDefaultSetupLikeCore() {
        assertTrue(BankerScript.setupRestocksFood(
                Arrays.asList(setup("Ranged", COINS), setup("default", Rs2Food.SHARK.getId())), "Melee"));
    }
}
