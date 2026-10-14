package net.runelite.client.plugins.microbot.thievingstalls.model;

import lombok.AllArgsConstructor;
import net.runelite.client.plugins.microbot.thievingstalls.StallThievingConfig;

import javax.inject.Inject;
import java.util.Arrays;

@AllArgsConstructor(onConstructor_ = @Inject)
public class BankInventoryStrategy implements IInventoryStrategy {
    private BotApi botApi;
    private DropInventoryStrategy dropInventoryStrategy;
    private StallThievingConfig config;

    @Override
    public void execute(IStallThievingSpot stallThievingSpot) {
        if (!botApi.isInventoryFull())
        {
            return;
        }

        botApi.dropAll(getItemIdsToAlwaysDrop());

        if (!botApi.isInventoryFull())
        {
            return;
        }

        try
        {
            stallThievingSpot.bank();
            return;
        }
        catch (final UnsupportedOperationException ex)
        {
            System.out.println("Banking not supported for this thieving spot. Dropping items");
        }

        dropInventoryStrategy.execute(stallThievingSpot);
    }

    private int[] getItemIdsToAlwaysDrop()
    {
        final String alwaysDrop = config.alwaysDrop();
        if (alwaysDrop == null || alwaysDrop.isBlank())
        {
            return new int[0];
        }
        return Arrays.stream(alwaysDrop.split(","))
                .map(String::trim)
                .filter(token -> token.matches("\\d{1,9}"))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
