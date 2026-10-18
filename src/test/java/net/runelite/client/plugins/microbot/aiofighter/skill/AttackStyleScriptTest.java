package net.runelite.client.plugins.microbot.aiofighter.skill;

import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public final class AttackStyleScriptTest {

    @Test
    void pickComponentReturnsNullWhenNoStyleMatches() {
        assertNull(AttackStyleScript.pickComponent(Collections.emptyList(), new Random(1)));
        assertNull(AttackStyleScript.pickComponent(null, new Random(1)));
    }

    @Test
    void pickComponentReturnsOnlyCandidate() {
        assertSame(WidgetInfo.COMBAT_STYLE_TWO,
                AttackStyleScript.pickComponent(Collections.singletonList(WidgetInfo.COMBAT_STYLE_TWO), new Random(1)));
    }

    @Test
    void pickComponentReturnsOneOfCandidates() {
        List<WidgetInfo> candidates = Arrays.asList(WidgetInfo.COMBAT_STYLE_ONE, WidgetInfo.COMBAT_STYLE_THREE);
        for (int seed = 0; seed < 20; seed++) {
            assertTrue(candidates.contains(AttackStyleScript.pickComponent(candidates, new Random(seed))));
        }
    }

    @Test
    void weaponTypeStylesEmptyWhenWeaponStylesEnumMissing() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(() -> Microbot.getEnum(anyInt())).thenReturn(null);
            assertEquals(0, AttackStyleScript.getWeaponTypeStyles(0).length);
        }
    }

    @Test
    void weaponTypeStylesEmptyWhenWeaponEnumMissing() {
        EnumComposition weaponStyles = mock(EnumComposition.class);
        when(weaponStyles.getIntValue(0)).thenReturn(5000);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(() -> Microbot.getEnum(EnumID.WEAPON_STYLES)).thenReturn(weaponStyles);
            microbot.when(() -> Microbot.getEnum(5000)).thenReturn(null);
            assertEquals(0, AttackStyleScript.getWeaponTypeStyles(0).length);
        }
    }

    @Test
    void weaponTypeStylesEmptyWhenStructOrNameMissing() {
        EnumComposition weaponStyles = mock(EnumComposition.class);
        when(weaponStyles.getIntValue(0)).thenReturn(5000);
        EnumComposition weaponEnum = mock(EnumComposition.class);
        when(weaponEnum.getIntVals()).thenReturn(new int[]{10, 11});
        StructComposition unnamed = mock(StructComposition.class);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(() -> Microbot.getEnum(EnumID.WEAPON_STYLES)).thenReturn(weaponStyles);
            microbot.when(() -> Microbot.getEnum(5000)).thenReturn(weaponEnum);
            microbot.when(() -> Microbot.getStructComposition(10)).thenReturn(unnamed);
            microbot.when(() -> Microbot.getStructComposition(11)).thenReturn(null);
            assertEquals(0, AttackStyleScript.getWeaponTypeStyles(0).length);
        }
    }

    @Test
    void weaponTypeStylesEmptyWhenStructMissing() {
        EnumComposition weaponStyles = mock(EnumComposition.class);
        when(weaponStyles.getIntValue(0)).thenReturn(5000);
        EnumComposition weaponEnum = mock(EnumComposition.class);
        when(weaponEnum.getIntVals()).thenReturn(new int[]{10, 11});
        StructComposition named = mock(StructComposition.class);
        when(named.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Accurate");
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(() -> Microbot.getEnum(EnumID.WEAPON_STYLES)).thenReturn(weaponStyles);
            microbot.when(() -> Microbot.getEnum(5000)).thenReturn(weaponEnum);
            microbot.when(() -> Microbot.getStructComposition(10)).thenReturn(null);
            microbot.when(() -> Microbot.getStructComposition(11)).thenReturn(named);
            assertEquals(0, AttackStyleScript.getWeaponTypeStyles(0).length);
        }
    }

    @Test
    void weaponTypeStylesResolvedFromStructs() {
        EnumComposition weaponStyles = mock(EnumComposition.class);
        when(weaponStyles.getIntValue(0)).thenReturn(5000);
        EnumComposition weaponEnum = mock(EnumComposition.class);
        when(weaponEnum.getIntVals()).thenReturn(new int[]{10, 11});
        StructComposition accurate = mock(StructComposition.class);
        when(accurate.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Accurate");
        StructComposition aggressive = mock(StructComposition.class);
        when(aggressive.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Aggressive");
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(() -> Microbot.getEnum(EnumID.WEAPON_STYLES)).thenReturn(weaponStyles);
            microbot.when(() -> Microbot.getEnum(5000)).thenReturn(weaponEnum);
            microbot.when(() -> Microbot.getStructComposition(10)).thenReturn(accurate);
            microbot.when(() -> Microbot.getStructComposition(11)).thenReturn(aggressive);
            assertArrayEquals(new AttackStyle[]{AttackStyle.ACCURATE, AttackStyle.AGGRESSIVE},
                    AttackStyleScript.getWeaponTypeStyles(0));
        }
    }
}
