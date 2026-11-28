package net.runelite.client.plugins.microbot.banksshopper;

import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BanksShopperBankActionTest {

    @Test
    void closedChestWithoutBankActionIsRejected() {
        assertNull(BanksShopperScript.bankActionOf(objectWithActions("Open", null, null, null, "Examine")));
    }

    @Test
    void openChestWithoutBankActionIsRejected() {
        assertNull(BanksShopperScript.bankActionOf(objectWithActions("Search", "Close", null, null, "Examine")));
    }

    @Test
    void bankBoothUsesBankAction() {
        assertEquals("Bank", BanksShopperScript.bankActionOf(objectWithActions(null, "Bank", "Collect", null, "Examine")));
    }

    @Test
    void bankChestUsesUseAction() {
        assertEquals("Use", BanksShopperScript.bankActionOf(objectWithActions("Use", null, "Collect", null, "Examine")));
    }

    @Test
    void depositBoxUsesDepositAction() {
        assertEquals("Deposit", BanksShopperScript.bankActionOf(objectWithActions("Deposit", null, null, null, "Examine")));
    }

    @Test
    void depositAllOnlyObjectUsesDepositAllAction() {
        assertEquals("Deposit-all", BanksShopperScript.bankActionOf(objectWithActions("Deposit-all", null, null, null, "Examine")));
    }

    private static Rs2TileObjectModel objectWithActions(String... actions) {
        List<String> list = Arrays.asList(actions);
        Rs2TileObjectModel object = mock(Rs2TileObjectModel.class);
        when(object.hasAction(anyString())).thenAnswer(invocation -> list.stream()
                .anyMatch(a -> a != null && a.equalsIgnoreCase(invocation.getArgument(0))));
        return object;
    }
}
