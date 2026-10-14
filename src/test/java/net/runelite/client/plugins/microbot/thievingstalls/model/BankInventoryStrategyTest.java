package net.runelite.client.plugins.microbot.thievingstalls.model;

import net.runelite.client.plugins.microbot.thievingstalls.StallThievingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankInventoryStrategyTest {

    private void runWithAlwaysDrop(String alwaysDrop, int... expectedIds) {
        BotApi botApi = mock(BotApi.class);
        DropInventoryStrategy dropInventoryStrategy = mock(DropInventoryStrategy.class);
        StallThievingConfig config = mock(StallThievingConfig.class);
        IStallThievingSpot spot = mock(IStallThievingSpot.class);
        when(config.alwaysDrop()).thenReturn(alwaysDrop);
        when(botApi.isInventoryFull()).thenReturn(true, false);

        BankInventoryStrategy strategy = new BankInventoryStrategy(botApi, dropInventoryStrategy, config);

        assertDoesNotThrow(() -> strategy.execute(spot));
        verify(botApi).dropAll(expectedIds);
    }

    @Test
    void itemNamesAreIgnoredInsteadOfThrowing() {
        runWithAlwaysDrop("Bread Chocolate slice ");
    }

    @Test
    void numericIdsAreKeptAlongsideInvalidEntries() {
        runWithAlwaysDrop(" 2309 , Bread,1901, 99999999999 ,", 2309, 1901);
    }

    @Test
    void defaultIdListIsParsed() {
        runWithAlwaysDrop("2309,1901", 2309, 1901);
    }

    @Test
    void blankConfigDropsNothing() {
        runWithAlwaysDrop("   ");
    }
}
