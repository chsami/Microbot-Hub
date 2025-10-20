package net.runelite.client.plugins.microbot.thievingstalls.constants;

import lombok.AllArgsConstructor;
import net.runelite.client.plugins.microbot.thievingstalls.model.*;

import javax.inject.Inject;
import java.util.Map;

@AllArgsConstructor(onConstructor_ = @Inject)
public class ThievingSpotMapper {

    public IStallThievingSpot getThievingSpot(final ThievingSpot thievingSpot)
    {
        final Map<ThievingSpot, IStallThievingSpot> map = Map.of(
                ThievingSpot.VARROCK_TEA_STALL, varrockTeaStallThievingSpot,
                ThievingSpot.ARDY_BAKER, ardyBakerThievingSpot,
                ThievingSpot.ARDY_SILK, ardySilkThievingSpot,
                ThievingSpot.HOSIDIUS_FRUIT, hosidiusFruitThievingSpot,
                ThievingSpot.FORTIS_GEM_STALL, fortisGemStallThievingSpot
        );

        return map.get(thievingSpot);
    }

    private VarrockTeaStallThievingSpot varrockTeaStallThievingSpot;
    private ArdyBakerThievingSpot ardyBakerThievingSpot;
    private ArdySilkThievingSpot ardySilkThievingSpot;
    private HosidiusFruitThievingSpot hosidiusFruitThievingSpot;
    private FortisGemStallThievingSpot fortisGemStallThievingSpot;
}
