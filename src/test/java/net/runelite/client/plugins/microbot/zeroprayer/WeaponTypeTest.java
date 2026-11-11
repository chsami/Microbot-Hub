package net.runelite.client.plugins.microbot.zeroprayer;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeaponTypeTest {

    private static final int STYLE_ENUM_BASE = 9000;
    private static final int STRUCT_BASE = 100;

    private Client client;
    private EnumComposition weaponStyles;
    private int nextStruct;

    @BeforeEach
    void setUp() {
        client = mock(Client.class);
        weaponStyles = mock(EnumComposition.class);
        when(weaponStyles.getIntValue(anyInt())).thenReturn(-1);
        when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(weaponStyles);
        nextStruct = STRUCT_BASE;
    }

    private void category(int weaponType, String... styleNames) {
        int[] structs = new int[styleNames.length];
        for (int i = 0; i < styleNames.length; i++) {
            int structId = nextStruct++;
            StructComposition struct = mock(StructComposition.class);
            when(struct.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn(styleNames[i]);
            when(client.getStructComposition(structId)).thenReturn(struct);
            structs[i] = structId;
        }
        EnumComposition styleEnum = mock(EnumComposition.class);
        when(styleEnum.getIntVals()).thenReturn(structs);
        when(weaponStyles.getIntValue(weaponType)).thenReturn(STYLE_ENUM_BASE + weaponType);
        when(client.getEnum(STYLE_ENUM_BASE + weaponType)).thenReturn(styleEnum);
    }

    @Test
    void poweredStaffCategoryResolvesToCastingStyles() {
        category(29, "Casting", "Casting", "Other", "Defensive", "Other", "Other");

        assertArrayEquals(
                new AttackStyle[]{AttackStyle.CASTING, AttackStyle.CASTING, null, AttackStyle.DEFENSIVE_CASTING, null, null},
                WeaponType.getAttackStyles(client, 29));
    }

    @Test
    void meleeCategoryKeepsDefensiveAsMelee() {
        category(23, "Accurate", "Aggressive", "Aggressive", "Defensive", "Other", "Other");

        assertArrayEquals(
                new AttackStyle[]{AttackStyle.ACCURATE, AttackStyle.AGGRESSIVE, AttackStyle.AGGRESSIVE, AttackStyle.DEFENSIVE, null, null},
                WeaponType.getAttackStyles(client, 23));
    }

    @Test
    void staffCategoryMapsSixthSlotToDefensiveCasting() {
        category(18, "Accurate", "Aggressive", "Other", "Defensive", "Casting", "Defensive");

        assertArrayEquals(
                new AttackStyle[]{AttackStyle.ACCURATE, AttackStyle.AGGRESSIVE, null, AttackStyle.DEFENSIVE, AttackStyle.CASTING, AttackStyle.DEFENSIVE_CASTING},
                WeaponType.getAttackStyles(client, 18));
    }

    @Test
    void rangedCategoryResolvesToRangedStyles() {
        category(3, "Ranging", "Ranging", "Other", "Longrange", "Other", "Other");

        assertArrayEquals(
                new AttackStyle[]{AttackStyle.RANGING, AttackStyle.RANGING, null, AttackStyle.LONGRANGE, null, null},
                WeaponType.getAttackStyles(client, 3));
    }

    @Test
    void blueMoonSpearHasCastingSlots() {
        AttackStyle[] styles = WeaponType.getAttackStyles(client, 22);

        assertEquals(6, styles.length);
        assertEquals(AttackStyle.CASTING, styles[4]);
        assertEquals(AttackStyle.DEFENSIVE_CASTING, styles[5]);
    }

    @Test
    void unknownCategoryHasNoStyles() {
        assertEquals(0, WeaponType.getAttackStyles(client, 99).length);
    }

    @Test
    void missingStyleDataHasNoStyles() {
        when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(null);

        assertEquals(0, WeaponType.getAttackStyles(client, 29).length);
    }

    @Test
    void unrecognisedStyleNameLeavesSlotEmpty() {
        category(40, "Accurate", "Brand new style");

        assertArrayEquals(
                new AttackStyle[]{AttackStyle.ACCURATE, null},
                WeaponType.getAttackStyles(client, 40));
    }
}
