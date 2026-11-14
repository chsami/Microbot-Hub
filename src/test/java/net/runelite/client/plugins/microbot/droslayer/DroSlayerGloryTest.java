package net.runelite.client.plugins.microbot.droslayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.inventorysetups.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DroSlayerGloryTest {
    @Test public void onlyBestChargedGlorySurvivesAcrossChargeVariants() {
        var six = item(1,"Amulet of glory(6)",1);
        assertEquals(Map.of(2,1,3,1),DroSlayerBankSupplies.excessGlories(List.of(
                six,item(2,"Amulet of glory(3)",1),item(3,"Amulet of glory",1))));
        assertSame(six,DroSlayerBankSupplies.chargedGlory(List.of(item(2,"Amulet of glory(3)",1),six)));
    }

    @Test public void duplicateFullChargeCopiesLeaveExactlyOne() {
        assertEquals(Map.of(1,2),DroSlayerBankSupplies.excessGlories(List.of(
                item(1,"Amulet of glory(6)",1),item(1,"Amulet of glory(6)",1),item(1,"Amulet of glory(6)",1))));
        assertEquals(Map.of(1,2),DroSlayerBankSupplies.excessGlories(List.of(item(1,"Amulet of glory(6)",3))));
    }

    @Test public void spentAndNotedGloriesAreNeverSelectedWhileOtherJewelryIsUnchanged() {
        var noted = item(1,"Amulet of glory(6)",2); when(noted.isNoted()).thenReturn(true);
        var spent = item(2,"Amulet of glory",1);
        var ring = item(3,"Ring of dueling(8)",1);
        assertNull(DroSlayerBankSupplies.chargedGlory(List.of(noted,spent,ring)));
        assertEquals(Map.of(1,2,2,1),DroSlayerBankSupplies.excessGlories(List.of(noted,spent,ring)));
        assertTrue(DroSlayerBankSupplies.excessGlories(List.of(ring)).isEmpty());
    }

    @Test public void onePartiallyChargedOrTrimmedGloryNeedsNoDeposit() {
        assertTrue(DroSlayerBankSupplies.excessGlories(List.of(item(1,"Amulet of glory(1)",1))).isEmpty());
        assertTrue(DroSlayerBankSupplies.excessGlories(List.of(item(1,"Amulet of glory(t)(2)",1))).isEmpty());
    }

    @Test public void runtimeRowsCannotWithdrawExtraGloriesAndSavedSetupIsUnchanged() {
        InventorySetup saved = saved();
        saved.getInventory().add(row(1,"Amulet of glory(6)",3));
        saved.getInventory().add(row(2,"Amulet of glory(3)",1));
        saved.getInventory().add(row(3,"Amulet of glory",1));
        saved.getInventory().add(row(4,"Lockpick",1));
        saved.getAdditionalFilteredItems().put(2,row(2,"Amulet of glory(3)",1));
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(saved);
        DroSlayerLoadout.limitInventoryGlories(copy,item(5,"Amulet of glory(4)",1));
        assertEquals(5,copy.getInventory().get(0).getId());
        assertEquals(1,copy.getInventory().get(0).getQuantity());
        assertTrue(InventorySetupsItem.itemIsDummy(copy.getInventory().get(1)));
        assertTrue(InventorySetupsItem.itemIsDummy(copy.getInventory().get(2)));
        assertEquals(4,copy.getInventory().get(3).getId());
        assertTrue(copy.getAdditionalFilteredItems().isEmpty());
        assertEquals(3,saved.getInventory().get(0).getQuantity());
        assertEquals(1,saved.getAdditionalFilteredItems().size());
        assertEquals(saved.getEquipment(),copy.getEquipment());
    }

    @Test public void runtimeNeverRequiresSpentGloryWhenNoChargedStockExists() {
        InventorySetup copy = DroSlayerLoadout.copyForFoodOverlay(saved());
        copy.getInventory().add(row(1,"Amulet of glory",1));
        DroSlayerLoadout.limitInventoryGlories(copy,null);
        assertTrue(InventorySetupsItem.itemIsDummy(copy.getInventory().get(0)));
    }

    @Test public void bankStepDepositsOnlyExcessAndWaitsBeforeLoadingOrDeparting() throws Exception {
        var kept = item(1,"Amulet of glory(6)",1);
        var spare = item(2,"Amulet of glory(3)",1);
        try (var inventory = mockStatic(Rs2Inventory.class); var bank = mockStatic(Rs2Bank.class);
             var runtime = mockStatic(DroSlayerRuntime.class); var dro = mockStatic(Microbot.class)) {
            dro.when(Microbot::getClient).thenReturn(mock(net.runelite.api.Client.class));
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            inventory.when(() -> Rs2Inventory.getList(any(Predicate.class))).thenReturn(List.of(kept,spare));
            inventory.when(() -> Rs2Inventory.itemQuantity(2)).thenReturn(1,0);
            bank.when(() -> Rs2Bank.depositX(2,1)).thenReturn(true);
            runtime.when(() -> DroSlayerRuntime.performAction(eq(BaseProfileDro.ActionPhase.SETUP),eq(false),
                    any(BooleanSupplier.class))).thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            Method prune = DroSlayerScript.class.getDeclaredMethod("keepSingleChargedGloryAtBank");
            prune.setAccessible(true);
            assertEquals(false,prune.invoke(new DroSlayerScript()));
            bank.verify(() -> Rs2Bank.depositX(2,1));
            bank.verify(() -> Rs2Bank.depositX(eq(1),anyInt()),never());
            bank.verify(() -> Rs2Bank.withdrawOne(anyInt()),never());
            inventory.when(() -> Rs2Inventory.getList(any(Predicate.class))).thenReturn(List.of(kept));
            assertEquals(true,prune.invoke(new DroSlayerScript()));
        }
    }

    private static Rs2ItemModel item(int id,String name,int quantity) {
        Rs2ItemModel model = mock(Rs2ItemModel.class);
        when(model.getId()).thenReturn(id); when(model.getName()).thenReturn(name);
        when(model.getQuantity()).thenReturn(quantity); return model;
    }
    private static InventorySetupsItem row(int id,String name,int quantity) {
        return new InventorySetupsItem(id,name,quantity,false,InventorySetupsStackCompareID.None,false,-1);
    }
    private static InventorySetup saved() {
        InventorySetup saved = mock(InventorySetup.class);
        when(saved.getInventory()).thenReturn(new ArrayList<>());
        when(saved.getEquipment()).thenReturn(new ArrayList<>(Collections.nCopies(14,InventorySetupsItem.getDummyItem())));
        when(saved.getAdditionalFilteredItems()).thenReturn(new HashMap<>()); return saved;
    }
}
