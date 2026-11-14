package net.runelite.client.plugins.microbot.droslayer;

import java.util.*;
import java.lang.reflect.Method;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.*;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerBankSuppliesTest {
    @Test public void cannonSuppliesFollowNextTaskInsteadOfPreviousTask() {
        for (String name : List.of("Cannon base","Cannon stand","Cannon barrels","Cannon furnace","Steel cannonball")) {
            assertTrue(keep(name,true,true,null));
            assertFalse(keep(name,true,false,null));
        }
        assertFalse(keep("Rune med helm",true,true,null));
    }
    @Test public void travelRetainsChargedItemsButNotDepletedOrUnrelatedTablets() {
        assertTrue(keep("Burning amulet(1)",true,false,null));
        assertTrue(keep("Amulet of glory(2)",true,false,null));
        assertTrue(keep("Royal seed pod",true,false,null));
        assertFalse(keep("Burning amulet",true,false,null));
        assertFalse(keep("Amulet of glory",true,false,null));
        assertFalse(keep("Burning amulet(3)",false,false,null));
        assertTrue(keep("Ring of dueling(2)",false,false,null));
        assertFalse(keep("Brimhaven teleport",false,false,null));
    }
    @Test public void onlySelectedBraceletAndCurrentRequirementsAreRetained() {
        assertTrue(keep("Bracelet of slaughter",true,false,DroSlayerBracelets.SLAUGHTER));
        assertFalse(keep("Expeditious bracelet",true,false,DroSlayerBracelets.SLAUGHTER));
        assertFalse(keep("Bracelet of slaughter",true,false,null));
        var requirements = List.of(DroSlayerTaskRequirements.Requirement.inventory("Wyrms travel necklace","Skills necklace(1)",1,"Skills necklace(1)"));
        assertTrue(DroSlayerBankSupplies.keep("Skills necklace(1)",false,false,null,requirements));
        assertFalse(DroSlayerBankSupplies.keep("Fungicide spray",false,false,null,requirements));
    }
    @Test public void retentionAddsNoWithdrawalRowsAndDoesNotMutateSavedSetup() {
        InventorySetup saved = saved();
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(saved);
        Rs2ItemModel ammo = item(2,"Steel cannonball",250);
        Rs2ItemModel bracelet = item(3,"Bracelet of slaughter",1);
        DroSlayerLoadout.retainCarried(copy,List.of(ammo,bracelet));
        assertTrue(copy.getAdditionalFilteredItems().containsKey(2));
        assertTrue(copy.getAdditionalFilteredItems().containsKey(3));
        assertTrue(copy.getInventory().isEmpty());
        assertTrue(saved.getAdditionalFilteredItems().isEmpty());
        assertEquals(-1,copy.getAdditionalFilteredItems().get(3).getSlot());
    }
    @Test public void selectedBraceletBecomesRealHandsRequirementWithSavedGlovesUnchanged() {
        InventorySetup saved = saved();
        int hands = EquipmentInventorySlot.GLOVES.getSlotIdx();
        saved.getEquipment().set(hands,row(1,"Barrows gloves"));
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(saved);
        DroSlayerLoadout.overrideHands(copy,item(3,"Bracelet of slaughter",1));
        assertEquals("Bracelet of slaughter",copy.getEquipment().get(hands).getName());
        assertFalse(InventorySetupsItem.itemIsDummy(copy.getEquipment().get(hands)));
        assertEquals("Barrows gloves",saved.getEquipment().get(hands).getName());
    }
    @Test public void consumedTravelChargeSatisfiesSavedFullChargeRowWithoutChangingSavedPreset() {
        InventorySetup saved = saved(); saved.getInventory().add(row(1,"Burning amulet(5)"));
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(saved);
        DroSlayerLoadout.reuseChargedJewelry(copy,List.of(item(2,"Burning amulet(4)",1)));
        assertEquals(2,copy.getInventory().get(0).getId());
        assertEquals(1,saved.getInventory().get(0).getId());
    }
    @Test public void depletedNotedAndMissingJewelryCannotSatisfySavedChargedRequirement() {
        for (Rs2ItemModel carried : List.of(item(2,"Burning amulet",1),item(3,"Burning amulet(4)",1))) {
            boolean noted = carried.getId() == 3;
            when(carried.isNoted()).thenReturn(noted);
            InventorySetup saved = saved(); saved.getInventory().add(row(1,"Burning amulet(5)"));
            DroSlayerLoadout.reuseChargedJewelry(saved,List.of(carried));
            assertEquals(1,saved.getInventory().get(0).getId());
        }
    }
    @Test public void sharedBankCleanupKeepsRuntimeExtrasButDepositsUnwantedLoot() throws Exception {
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(saved());
        Rs2ItemModel ammo = item(2,"Steel cannonball",250);
        Rs2ItemModel spare = item(3,"Bracelet of slaughter",1);
        DroSlayerLoadout.retainCarried(copy,List.of(ammo,spare));
        Rs2InventorySetup loader = new Rs2InventorySetup(copy,null);
        try (var dro = mockStatic(Microbot.class); var inventory = mockStatic(Rs2Inventory.class)) {
            Method idsMethod = Rs2InventorySetup.class.getDeclaredMethod("computeSetupRetainItemIds");
            Method namesMethod = Rs2InventorySetup.class.getDeclaredMethod("computeSetupFuzzyKeepNames");
            Method cleanup = Rs2InventorySetup.class.getDeclaredMethod("needsDepositCleanupBeforeBanking",Set.class,Map.class);
            idsMethod.setAccessible(true); namesMethod.setAccessible(true); cleanup.setAccessible(true);
            @SuppressWarnings("unchecked") Set<Integer> ids = (Set<Integer>) idsMethod.invoke(loader);
            @SuppressWarnings("unchecked") Map<String,Boolean> names = (Map<String,Boolean>) namesMethod.invoke(loader);
            assertTrue(Rs2Bank.isInventoryItemRetainedForSetupDeposit(ammo,ids,names));
            assertTrue(Rs2Bank.isInventoryItemRetainedForSetupDeposit(spare,ids,names));
            inventory.when(Rs2Inventory::items).thenAnswer(call -> List.of(ammo,spare).stream());
            assertEquals(false,cleanup.invoke(loader,ids,names));
            Rs2ItemModel loot = item(4,"Rune med helm",1);
            assertFalse(Rs2Bank.isInventoryItemRetainedForSetupDeposit(loot,ids,names));
            inventory.when(Rs2Inventory::items).thenAnswer(call -> List.of(ammo,spare,loot).stream());
            assertEquals(true,cleanup.invoke(loader,ids,names));
        }
    }
    @Test public void superCombatFallbackPrefersThreeThenTwoWithoutChangingSavedRows() {
        InventorySetup original = saved();
        original.getInventory().clear();
        original.getInventory().add(row(12695, "Super combat potion(4)"));
        original.getInventory().add(row(12695, "Super combat potion(4)"));
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(original);
        DroSlayerLoadout.fallbackSuperCombat(copy, List.of(), List.of(
                item(12699,"Super combat potion(2)",1), item(12697,"Super combat potion(3)",1)));
        assertEquals(12697, copy.getInventory().get(0).getId());
        assertEquals(12699, copy.getInventory().get(1).getId());
        assertEquals(12695, original.getInventory().get(0).getId());
        InventorySetup full = DroSlayerLoadout.copyForFoodOverlay(original);
        DroSlayerLoadout.fallbackSuperCombat(full, List.of(), List.of(
                item(12695,"Super combat potion(4)",1),item(12697,"Super combat potion(3)",5)));
        assertEquals(12695, full.getInventory().get(0).getId());
        InventorySetup one = DroSlayerLoadout.copyForFoodOverlay(original);
        DroSlayerLoadout.fallbackSuperCombat(one, List.of(), List.of(item(12701,"Super combat potion(1)",5)));
        assertEquals(12695, one.getInventory().get(0).getId());
    }

    private static boolean keep(String name,boolean wild,boolean cannon,String bracelet) {
        return DroSlayerBankSupplies.keep(name,wild,cannon,bracelet,List.of());
    }
    private static InventorySetupsItem row(int id,String name) {
        return new InventorySetupsItem(id,name,1,false,InventorySetupsStackCompareID.None,false,-1);
    }
    private static Rs2ItemModel item(int id,String name,int qty) {
        Rs2ItemModel item = mock(Rs2ItemModel.class); when(item.getId()).thenReturn(id);
        when(item.getName()).thenReturn(name); when(item.getQuantity()).thenReturn(qty); return item;
    }
    private static InventorySetup saved() {
        InventorySetup saved = mock(InventorySetup.class);
        when(saved.getInventory()).thenReturn(new ArrayList<>());
        when(saved.getEquipment()).thenReturn(new ArrayList<>(Collections.nCopies(14,InventorySetupsItem.getDummyItem())));
        when(saved.getAdditionalFilteredItems()).thenReturn(new HashMap<>()); return saved;
    }
}
