package net.runelite.client.plugins.microbot.gotr;

import net.runelite.api.GameObject;
import net.runelite.api.ObjectID;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.gotr.data.CellType;
import net.runelite.client.plugins.microbot.gotr.data.GuardianPortalInfo;
import net.runelite.client.plugins.microbot.gotr.data.Mode;
import net.runelite.client.plugins.microbot.gotr.data.RuneType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GotrAltarSelectionTest
{
    private Map<Integer, GuardianPortalInfo> saved;

    @BeforeEach
    void setUp()
    {
        saved = new HashMap<>(GotrScript.guardianPortalInfo);
        GotrScript.guardianPortalInfo.clear();
        GotrScript.guardianPortalInfo.put(ObjectID.GUARDIAN_OF_AIR, info("AIR", RuneType.ELEMENTAL, CellType.WEAK));
        GotrScript.guardianPortalInfo.put(ObjectID.GUARDIAN_OF_MIND, info("MIND", RuneType.CATALYTIC, CellType.WEAK));
        GotrScript.guardianPortalInfo.put(ObjectID.GUARDIAN_OF_FIRE, info("FIRE", RuneType.ELEMENTAL, CellType.OVERCHARGED));
        GotrScript.guardianPortalInfo.put(ObjectID.GUARDIAN_OF_NATURE, info("NATURE", RuneType.CATALYTIC, CellType.STRONG));
    }

    @AfterEach
    void tearDown()
    {
        GotrScript.guardianPortalInfo.clear();
        GotrScript.guardianPortalInfo.putAll(saved);
    }

    @Test
    void catalyticModePicksCatalyticAltarFirst()
    {
        List<GameObject> sorted = GotrScript.sortAltarsByMode(sceneOrder(), Mode.CATALYTIC, 0, 50);
        assertEquals(RuneType.CATALYTIC, runeTypeOf(sorted.get(0)));
        assertEquals(List.of("MIND", "NATURE", "AIR", "FIRE"), names(sorted));
    }

    @Test
    void elementalModePicksElementalAltarFirstEvenWhenElementalPointsAreAhead()
    {
        List<GameObject> sorted = GotrScript.sortAltarsByMode(catalyticFirstSceneOrder(), Mode.ELEMENTAL, 80, 10);
        assertEquals(RuneType.ELEMENTAL, runeTypeOf(sorted.get(0)));
        assertEquals(List.of("AIR", "FIRE", "MIND", "NATURE"), names(sorted));
    }

    @Test
    void balancedModePicksElementalWhenElementalPointsAreLower()
    {
        List<GameObject> sorted = GotrScript.sortAltarsByMode(catalyticFirstSceneOrder(), Mode.BALANCED, 10, 40);
        assertEquals(RuneType.ELEMENTAL, runeTypeOf(sorted.get(0)));
    }

    @Test
    void balancedModePicksCatalyticWhenCatalyticPointsAreLower()
    {
        List<GameObject> sorted = GotrScript.sortAltarsByMode(sceneOrder(), Mode.BALANCED, 63, 7);
        assertEquals(RuneType.CATALYTIC, runeTypeOf(sorted.get(0)));
    }

    @Test
    void pointsModePicksStrongestCellFirst()
    {
        List<GameObject> sorted = GotrScript.sortAltarsByMode(sceneOrder(), Mode.POINTS, 0, 0);
        assertEquals("FIRE", nameOf(sorted.get(0)));
    }

    @Test
    void preferredRuneTypePerMode()
    {
        assertEquals(RuneType.CATALYTIC, GotrScript.preferredRuneType(Mode.CATALYTIC, 0, 100));
        assertEquals(RuneType.ELEMENTAL, GotrScript.preferredRuneType(Mode.ELEMENTAL, 100, 0));
        assertEquals(RuneType.ELEMENTAL, GotrScript.preferredRuneType(Mode.BALANCED, 1, 2));
        assertEquals(RuneType.CATALYTIC, GotrScript.preferredRuneType(Mode.BALANCED, 2, 1));
        assertNull(GotrScript.preferredRuneType(Mode.POINTS, 0, 0));
        assertNull(GotrScript.preferredRuneType(null, 0, 0));
    }

    private static List<GameObject> sceneOrder()
    {
        return List.of(
                altar(ObjectID.GUARDIAN_OF_AIR),
                altar(ObjectID.GUARDIAN_OF_MIND),
                altar(ObjectID.GUARDIAN_OF_FIRE),
                altar(ObjectID.GUARDIAN_OF_NATURE));
    }

    private static List<GameObject> catalyticFirstSceneOrder()
    {
        return List.of(
                altar(ObjectID.GUARDIAN_OF_MIND),
                altar(ObjectID.GUARDIAN_OF_AIR),
                altar(ObjectID.GUARDIAN_OF_NATURE),
                altar(ObjectID.GUARDIAN_OF_FIRE));
    }

    private static GameObject altar(int id)
    {
        GameObject altar = mock(GameObject.class);
        when(altar.getId()).thenReturn(id);
        return altar;
    }

    private static GuardianPortalInfo info(String name, RuneType runeType, CellType cellType)
    {
        return new GuardianPortalInfo(name, 1, 0, 0, 0, runeType, cellType, QuestState.FINISHED);
    }

    private static RuneType runeTypeOf(GameObject altar)
    {
        return GotrScript.guardianPortalInfo.get(altar.getId()).getRuneType();
    }

    private static String nameOf(GameObject altar)
    {
        return GotrScript.guardianPortalInfo.get(altar.getId()).getName();
    }

    private static List<String> names(List<GameObject> altars)
    {
        return altars.stream().map(GotrAltarSelectionTest::nameOf).collect(Collectors.toList());
    }
}
