package net.runelite.client.plugins.microbot.barbarianvillagefisher;

import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class BarbarianVillageFisherScriptTest
{
    private static Set<Integer> droppable()
    {
        return Arrays.stream(BarbarianVillageFisherScript.DroppableFish).boxed().collect(Collectors.toSet());
    }

    @Test
    public void dropsOnlyFishThePluginProduces()
    {
        assertEquals(Set.of(
                ItemID.RAW_TROUT, ItemID.RAW_SALMON, ItemID.RAW_PIKE,
                ItemID.TROUT, ItemID.SALMON, ItemID.PIKE,
                ItemID.BURNTFISH2), droppable());
    }

    @Test
    public void keepsToolsAndSupplies()
    {
        Set<Integer> droppable = droppable();
        assertFalse(droppable.contains(ItemID.TINDERBOX));
        assertFalse(droppable.contains(ItemID.BRONZE_AXE));
        assertFalse(droppable.contains(ItemID.KNIFE));
        assertFalse(droppable.contains(ItemID.COINS));
        assertFalse(droppable.contains(ItemID.FLY_FISHING_ROD));
        assertFalse(droppable.contains(ItemID.FEATHER));
        assertFalse(droppable.contains(ItemID.FISHING_ROD));
        assertFalse(droppable.contains(ItemID.FISHING_BAIT));
    }
}
