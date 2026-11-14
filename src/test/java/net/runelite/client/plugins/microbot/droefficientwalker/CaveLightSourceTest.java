package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaveLightSourceTest {
    @Test void candlesTorchesLampsLanternsAndPermanentLightsAuthorizeCaveEntry() {
        for (int id : new int[] {ItemID.LIT_CANDLE, ItemID.LIT_BLACK_CANDLE, ItemID.TORCH_LIT,
                ItemID.OIL_LAMP_LIT, ItemID.OIL_LANTERN_LIT, ItemID.CANDLE_LANTERN_LIT,
                ItemID.CANDLE_LANTERN_BLACK_LIT, ItemID.CAVE_GOBLIN_MINING_HELMET_LIT,
                ItemID.WINT_TORCH, ItemID.WINT_TORCH_OFFHAND, ItemID.ABYSSAL_LANTERN_MAGIC,
                ItemID.SKILLCAPE_FIREMAKING, ItemID.SKILLCAPE_MAX}) assertTrue(CaveLightSource.isSafeLight(id));
    }

    @Test void emptyLanternsAndUnlitItemsNeverAuthorizeCaveEntry() {
        for (int id : new int[] {ItemID.UNLIT_CANDLE, ItemID.TORCH_UNLIT,
                ItemID.OIL_LAMP_EMPTY, ItemID.CANDLE_LANTERN_EMPTY, ItemID.ABYSSAL_LANTERN,
                ItemID.BULLSEYE_LANTERN_UNLIT, ItemID.TOG_SAPPHIRE_LANTERN_UNLIT,
                ItemID.TOG_SAPPHIRE_LANTERN_EMPTY, 4544, 4546}) assertFalse(CaveLightSource.isSafeLight(id));
        assertEquals(-1, CaveLightSource.chooseLit(id -> id == ItemID.BULLSEYE_LANTERN_UNLIT));
        assertEquals(-1, CaveLightSource.chooseUnlit(id -> id == 4546 || id == ItemID.TOG_SAPPHIRE_LANTERN_EMPTY));
        assertEquals(-1, CaveLightSource.litVariant(4546));
    }

    @Test void anOwnedPermanentLightIsPreferredToALanternWithoutEquippingEither() {
        Set<Integer> owned = Set.of(ItemID.BULLSEYE_LANTERN_LIT, ItemID.SEERS_HEADBAND_EASY);
        assertEquals(ItemID.SEERS_HEADBAND_EASY, CaveLightSource.chooseLit(owned::contains));
        assertEquals(-1, CaveLightSource.chooseLit(id -> false));
        assertEquals(ItemID.TOG_SAPPHIRE_LANTERN_LIT, CaveLightSource.litVariant(ItemID.TOG_SAPPHIRE_LANTERN_UNLIT));
        assertEquals(ItemID.BULLSEYE_LANTERN_LIT_LUNAR_QUEST,
                CaveLightSource.litVariant(ItemID.BULLSEYE_LANTERN_UNLIT_LUNAR_QUEST));
    }

    @Test void carriedLightMustBeUnnotedAndObservedWhileAWornPermanentLightAlsoWorks() {
        try (var inventory = mockStatic(Rs2Inventory.class); var equipment = mockStatic(TravelEquipment.class)) {
            Rs2ItemModel item = mock(Rs2ItemModel.class);
            when(item.getId()).thenReturn(ItemID.BULLSEYE_LANTERN_LIT);
            when(item.getQuantity()).thenReturn(1);
            inventory.when(Rs2Inventory::all).thenReturn(List.of(item));
            equipment.when(TravelEquipment::wornItems).thenReturn(Map.of());
            assertTrue(CaveLightSource.hasLight());
            when(item.isNoted()).thenReturn(true);
            assertFalse(CaveLightSource.hasLight());
            equipment.when(TravelEquipment::wornItems).thenReturn(Map.of(ItemID.WINT_TORCH, 1));
            assertTrue(CaveLightSource.hasLight());
            equipment.when(TravelEquipment::wornItems).thenReturn(Map.of(ItemID.BULLSEYE_LANTERN_EMPTY, 1));
            assertFalse(CaveLightSource.hasLight());
        }
    }

    @Test void carriedLightNeverAddsABankDetourAndBankedLitLightNeedsOnlyOneWithdrawal() {
        var carried = CaveLightSource.prepare(id -> id == ItemID.LIT_CANDLE ? 1 : 0, id -> false,
                id -> { fail("A carried light needs no bank check"); return 0; }, 1);
        assertTrue(carried.withdrawals.isEmpty()); assertEquals(-1, carried.unlit);
        var banked = CaveLightSource.prepare(id -> 0, id -> false,
                id -> id == ItemID.WINT_TORCH || id == ItemID.BULLSEYE_LANTERN_LIT ? 1 : 0, 1);
        assertEquals(Map.of(ItemID.WINT_TORCH, 1), banked.withdrawals); assertEquals(-1, banked.unlit);
    }

    @Test void aLevelOneCandleCanBePreparedInsteadOfAnUnusableHighLevelLantern() {
        Set<Integer> owned = Set.of(ItemID.BULLSEYE_LANTERN_UNLIT, ItemID.UNLIT_CANDLE, CaveLightSource.TINDERBOX);
        var preparation = CaveLightSource.prepare(id -> 0, id -> false, id -> owned.contains(id) ? 1 : 0, 1);
        assertEquals(ItemID.UNLIT_CANDLE, preparation.unlit);
        assertEquals(Map.of(ItemID.UNLIT_CANDLE, 1, CaveLightSource.TINDERBOX, 1), preparation.withdrawals);
        assertEquals(ItemID.LIT_CANDLE, CaveLightSource.litVariant(preparation.unlit));
    }

    @Test void unlitSourcesNeedOwnedTinderboxAndTheirOwnLightingLevel() {
        for (int unlit : new int[] {ItemID.UNLIT_CANDLE, ItemID.UNLIT_BLACK_CANDLE, ItemID.TORCH_UNLIT,
                ItemID.CANDLE_LANTERN_UNLIT, ItemID.CANDLE_LANTERN_BLACK_UNLIT, ItemID.OIL_LAMP_UNLIT,
                ItemID.OIL_LANTERN_UNLIT, ItemID.BULLSEYE_LANTERN_UNLIT, ItemID.TOG_SAPPHIRE_LANTERN_UNLIT,
                ItemID.BULLSEYE_LANTERN_UNLIT_LUNAR_QUEST, ItemID.CAVE_GOBLIN_MINING_HELMET_UNLIT}) {
            int level = CaveLightSource.lightingLevel(unlit);
            assertNull(CaveLightSource.prepare(id -> id == unlit ? 1 : 0, id -> false, id -> 0, level));
            var plan = CaveLightSource.prepare(id -> id == unlit || id == CaveLightSource.TINDERBOX ? 1 : 0,
                    id -> false, id -> 0, level);
            assertNotNull(plan); assertEquals(unlit, plan.unlit); assertTrue(plan.withdrawals.isEmpty());
            assertTrue(CaveLightSource.isSafeLight(CaveLightSource.litVariant(unlit)));
            assertNull(CaveLightSource.prepare(id -> id == unlit || id == CaveLightSource.TINDERBOX ? 1 : 0,
                    id -> false, id -> 0, level - 1));
        }
    }

    @Test void carriedUnlitLightDoesNotRequestADuplicateAndDisabledBankPreparationCannotUseTheMirror() {
        var carried = CaveLightSource.prepare(id -> id == ItemID.UNLIT_CANDLE || id == ItemID.TINDERBOX ? 1 : 0,
                id -> false, id -> { fail("Already-carried lightable supplies need no bank query"); return 0; }, 1);
        assertTrue(carried.withdrawals.isEmpty()); assertEquals(ItemID.UNLIT_CANDLE, carried.unlit);
        var plan = CaveLightSource.prepare(id -> id == ItemID.OIL_LANTERN_UNLIT ? 1 : 0, id -> false,
                id -> id == CaveLightSource.TINDERBOX ? 1 : 0, 26);
        assertEquals(Map.of(CaveLightSource.TINDERBOX, 1), plan.withdrawals);
        try (var inventory = mockStatic(Rs2Inventory.class); var equipment = mockStatic(TravelEquipment.class);
             var bank = mockStatic(net.runelite.client.plugins.microbot.util.bank.Rs2Bank.class);
             var player = mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Player.class)) {
            inventory.when(Rs2Inventory::all).thenReturn(List.of());
            equipment.when(TravelEquipment::wornItems).thenReturn(Map.of());
            bank.when(() -> net.runelite.client.plugins.microbot.util.bank.Rs2Bank.count(ItemID.WINT_TORCH)).thenReturn(1);
            assertNull(CaveLightSource.prepare(false));
            bank.verifyNoInteractions();
            assertNotNull(CaveLightSource.prepare(true));
        }
    }
}
