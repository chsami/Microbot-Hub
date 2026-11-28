package net.runelite.client.plugins.microbot.aiofighter.safety;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public final class SafetyScriptTest {

    private static final int COINS = 995;

    private static AIOFighterConfig config(boolean bank, boolean useInventorySetup) {
        AIOFighterConfig config = mock(AIOFighterConfig.class);
        when(config.bank()).thenReturn(bank);
        when(config.useInventorySetup()).thenReturn(useInventorySetup);
        return config;
    }

    private static List<InventorySetup> setups(String name, int itemId) {
        InventorySetupsItem item = mock(InventorySetupsItem.class);
        when(item.getId()).thenReturn(itemId);
        InventorySetup setup = mock(InventorySetup.class);
        when(setup.getName()).thenReturn(name);
        when(setup.getInventory()).thenReturn(Collections.singletonList(item));
        return Collections.singletonList(setup);
    }

    private static List<InventorySetup> foodSetups() {
        return setups("Melee", Rs2Food.SHARK.getId());
    }

    @Test
    void bankWithoutSelectedSetupDoesNotRestockFood() {
        assertFalse(SafetyScript.bankRestocksFood(config(true, false), "", foodSetups()));
        assertFalse(SafetyScript.bankRestocksFood(config(true, true), "", foodSetups()));
        assertFalse(SafetyScript.bankRestocksFood(config(true, true), null, foodSetups()));
    }

    @Test
    void selectedSetupWithInventorySetupOffDoesNotRestockFood() {
        assertFalse(SafetyScript.bankRestocksFood(config(true, false), "Melee", foodSetups()));
    }

    @Test
    void nonSlayerSetupWithFoodRestocksFoodWithoutSlayerCurrentSetup() {
        AIOFighterConfig config = config(true, true);
        when(config.currentInventorySetup()).thenReturn(null);
        assertTrue(SafetyScript.bankRestocksFood(config, "Melee", foodSetups()));
    }

    @Test
    void setupWithoutFoodDoesNotSuppressLowHealthStop() {
        assertFalse(SafetyScript.bankRestocksFood(config(true, true), "Melee", setups("Melee", COINS)));
    }

    @Test
    void unresolvedSetupDoesNotSuppressLowHealthStop() {
        assertFalse(SafetyScript.bankRestocksFood(config(true, true), "Melee", setups("Ranged", Rs2Food.SHARK.getId())));
        assertFalse(SafetyScript.bankRestocksFood(config(true, true), "Melee", null));
    }

    @Test
    void bankingOffDoesNotRestockFood() {
        assertFalse(SafetyScript.bankRestocksFood(config(false, true), "Melee", foodSetups()));
    }

    @Test
    void lowHealthReasonWithPercentSignStillLogsOutAndStops() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<AIOFighterPlugin> plugin = mockStatic(AIOFighterPlugin.class);
             MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
             MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class)) {
            plugin.when(AIOFighterPlugin::getState).thenReturn(State.COMBAT);
            player.when(Rs2Player::isMoving).thenReturn(false);
            Plugin aioFighter = mock(Plugin.class);
            microbot.when(() -> Microbot.getPlugin(AIOFighterPlugin.class.getName())).thenReturn(aioFighter);

            new SafetyScript().stopAndLog("Low health: 20%");

            microbot.verify(() -> Microbot.log("Low health: 20%", Level.WARN));
            microbot.verify(() -> Microbot.log(eq("Low health: 20%"), any(Object[].class)), never());
            bank.verify(Rs2Bank::walkToBank);
            player.verify(Rs2Player::logout);
            microbot.verify(() -> Microbot.stopPlugin(aioFighter));
        }
    }
}
