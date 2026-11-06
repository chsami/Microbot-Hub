package net.runelite.client.plugins.microbot.fourtifystormenteddemons.temple;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GearChoiceTest
{
    private static Map<CombatStyle, Set<Integer>> sets(Object... stylesAndIds)
    {
        Map<CombatStyle, Set<Integer>> result = new EnumMap<>(CombatStyle.class);
        for (int index = 0; index < stylesAndIds.length; index += 2)
        {
            result.put((CombatStyle) stylesAndIds[index], (Set<Integer>) stylesAndIds[index + 1]);
        }
        return result;
    }

    @Test
    public void wornIsNullWhenNoStyleIsConfigured()
    {
        assertNull(GearChoice.worn(sets(), id -> true));
        assertNull(GearChoice.worn(sets(CombatStyle.MAGIC, Set.of(), CombatStyle.MELEE, Set.of()), id -> true));
    }

    @Test
    public void wornFindsTheStyleWhoseItemsAreAllWorn()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.MAGIC, Set.of(1, 2), CombatStyle.MELEE, Set.of(3, 4));
        assertEquals(CombatStyle.MELEE, GearChoice.worn(sets, id -> id == 3 || id == 4));
        assertEquals(CombatStyle.MAGIC, GearChoice.worn(sets, id -> id <= 2));
    }

    @Test
    public void wornChecksMagicThenRangedThenMelee()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.MELEE, Set.of(1), CombatStyle.RANGED, Set.of(1), CombatStyle.MAGIC, Set.of(1));
        assertEquals(CombatStyle.MAGIC, GearChoice.worn(sets, id -> true));
        sets.remove(CombatStyle.MAGIC);
        assertEquals(CombatStyle.RANGED, GearChoice.worn(sets, id -> true));
    }

    @Test
    public void wornFallsBackToTheFirstConfiguredStyleWhenNothingIsFullyWorn()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.RANGED, Set.of(1, 2), CombatStyle.MELEE, Set.of(3));
        assertEquals(CombatStyle.RANGED, GearChoice.worn(sets, id -> id == 1));
        assertEquals(CombatStyle.RANGED, GearChoice.worn(sets, id -> false));
    }

    @Test
    public void wornSkipsBlankStyles()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.MAGIC, Set.of(), CombatStyle.MELEE, Set.of(3));
        assertEquals(CombatStyle.MELEE, GearChoice.worn(sets, id -> false));
    }

    @Test
    public void againstKeepsTheCurrentStyleWhenItIsNotProtected()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.MAGIC, Set.of(1), CombatStyle.MELEE, Set.of(3));
        assertEquals(CombatStyle.MELEE, GearChoice.against(CombatStyle.MELEE, CombatStyle.MAGIC, sets));
        assertEquals(CombatStyle.MELEE, GearChoice.against(CombatStyle.MELEE, null, sets));
    }

    @Test
    public void againstMovesToTheNextConfiguredStyleInOrderWhenProtected()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.MAGIC, Set.of(1), CombatStyle.RANGED, Set.of(2), CombatStyle.MELEE, Set.of(3));
        assertEquals(CombatStyle.RANGED, GearChoice.against(CombatStyle.MAGIC, CombatStyle.MAGIC, sets));
        assertEquals(CombatStyle.MAGIC, GearChoice.against(CombatStyle.RANGED, CombatStyle.RANGED, sets));
        assertEquals(CombatStyle.MAGIC, GearChoice.against(CombatStyle.MELEE, CombatStyle.MELEE, sets));
    }

    @Test
    public void againstSkipsBlankStyles()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.MAGIC, Set.of(1), CombatStyle.RANGED, Set.of(), CombatStyle.MELEE, Set.of(3));
        assertEquals(CombatStyle.MELEE, GearChoice.against(CombatStyle.MAGIC, CombatStyle.MAGIC, sets));
    }

    @Test
    public void againstStaysPutWhenNoOtherStyleIsConfigured()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.MAGIC, Set.of(1));
        assertEquals(CombatStyle.MAGIC, GearChoice.against(CombatStyle.MAGIC, CombatStyle.MAGIC, sets));
    }

    @Test
    public void againstPicksAStyleWhenNoneIsCurrent()
    {
        Map<CombatStyle, Set<Integer>> sets = sets(CombatStyle.RANGED, Set.of(2), CombatStyle.MELEE, Set.of(3));
        assertEquals(CombatStyle.RANGED, GearChoice.against(null, null, sets));
        assertEquals(CombatStyle.MELEE, GearChoice.against(null, CombatStyle.RANGED, sets));
        assertNull(GearChoice.against(null, null, sets()));
    }
}
