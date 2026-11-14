package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Method;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FossilIslandBankApproachTest {
    @Test void adjacentToCampChestRoutesToBankApproachRatherThanOccupiedObject()throws Exception{
        WorldPoint target=nearest(new WorldPoint(3741,3805,0));
        assertEquals(new WorldPoint(3739,3804,0),target);
        assertNotEquals(new WorldPoint(3742,3805,0),target);
        assertEquals(target,nearest(target));
    }
    @Test void northernBankSelectionRemainsUnchanged()throws Exception{
        assertEquals(BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint(),nearest(new WorldPoint(3764,3898,0)));
    }
    @Test void fossilOverrideDoesNotApplyAwayFromIslandOrWithoutPlayer()throws Exception{
        assertNull(nearest(new WorldPoint(3165,3487,0)));assertNull(nearest(null));
    }
    private static WorldPoint nearest(WorldPoint player)throws Exception{
        Method method=WalkerTestReflection.declaredMethod(EfficientWalker.class, "nearestFossilBank",WorldPoint.class);
        method.setAccessible(true);return (WorldPoint)method.invoke(null,player);
    }
}
