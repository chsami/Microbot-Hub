package net.runelite.client.plugins.microbot.droslayer;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droslayer.profile.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerCannonPurchaseTest {
    @Test public void purchaseCannotExceedAvailableMoneyAndFallsBackTo1000() {
        assertEquals(0, DroSlayerCannonPurchase.affordableQuantity(99_999,100,3000));
        assertEquals(1000, DroSlayerCannonPurchase.affordableQuantity(100_000,100,3000));
        assertEquals(1000, DroSlayerCannonPurchase.affordableQuantity(199_999,100,3000));
        assertEquals(2500, DroSlayerCannonPurchase.affordableQuantity(250_000,100,3000));
        assertEquals(3000, DroSlayerCannonPurchase.affordableQuantity(Long.MAX_VALUE,100,3000));
        assertEquals(0, DroSlayerCannonPurchase.affordableQuantity(Long.MAX_VALUE,0,3000));
    }
    @Test public void ironAccountsNeverFetchPricesOrTravel() {
        var purchase = new DroSlayerCannonPurchase();
        try (var bank = mockStatic(Rs2Bank.class); var player = mockStatic(Rs2Player.class);
             var exchange = mockStatic(Rs2GrandExchange.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            player.when(Rs2Player::isIronman).thenReturn(true);
            assertFalse(purchase.begin());
            assertFalse(purchase.isActive());
            exchange.verifyNoInteractions();
        }
    }
    @Test public void bankCloseMustBeObservedBeforeTravelAndDoesNotReopenBank() throws Exception {
        var purchase = new DroSlayerCannonPurchase();
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var exchange = mockStatic(Rs2GrandExchange.class); var player = mockStatic(Rs2Player.class);
             var runtime = mockStatic(DroSlayerRuntime.class)) {
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count(ItemID.COINS_995)).thenReturn(500_000);
            exchange.when(() -> Rs2GrandExchange.getPrice(ItemID.STEEL_CANNONBALL)).thenReturn(100);
            assertTrue(purchase.begin());
            inventory.when(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995)).thenReturn(500_000);
            runtime.when(() -> DroSlayerRuntime.performAction(any(BaseProfileDro.ActionPhase.class), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            purchase.tick(mock(DroSlayerConfig.class));
            bank.when(Rs2Bank::isOpen).thenReturn(false);
            field(purchase,"retryAt",0L);
            purchase.tick(mock(DroSlayerConfig.class));
            runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(any(),anyString()),never());
        }
    }
    @Test public void delayedBuyResponseDoesNotPlaceDuplicateOfferAndCollectsOnlyOwnedSlot() throws Exception {
        var purchase = new DroSlayerCannonPurchase();
        var client = mock(Client.class);
        var thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any())).thenAnswer(call -> Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
        var offer = mock(GrandExchangeOffer.class);
        when(offer.getState()).thenReturn(GrandExchangeOfferState.EMPTY);
        when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[]{offer});
        try (var bank = mockStatic(Rs2Bank.class); var inventory = mockStatic(Rs2Inventory.class);
             var exchange = mockStatic(Rs2GrandExchange.class); var bot = mockStatic(Microbot.class);
             var runtime = mockStatic(DroSlayerRuntime.class); var player = mockStatic(Rs2Player.class)) {
            bot.when(Microbot::getClient).thenReturn(client);
            bot.when(Microbot::getClientThread).thenReturn(thread);
            bank.when(Rs2Bank::isOpen).thenReturn(true);
            bank.when(() -> Rs2Bank.count(ItemID.COINS_995)).thenReturn(500_000);
            exchange.when(() -> Rs2GrandExchange.getPrice(ItemID.STEEL_CANNONBALL)).thenReturn(100);
            assertTrue(purchase.begin());
            int quantity = (int) get(purchase,"quantity");
            inventory.when(() -> Rs2Inventory.itemQuantity(ItemID.COINS_995)).thenReturn(500_000);
            exchange.when(Rs2GrandExchange::getAvailableSlot).thenReturn(GrandExchangeSlots.values()[0]);
            exchange.when(() -> Rs2GrandExchange.getSlotFromIndex(0)).thenReturn(GrandExchangeSlots.values()[0]);
            runtime.when(() -> DroSlayerRuntime.performAction(any(BaseProfileDro.ActionPhase.class), eq(false), any(BooleanSupplier.class)))
                    .thenAnswer(call -> ((BooleanSupplier) call.getArgument(2)).getAsBoolean());
            phase(purchase,"SUBMIT");
            purchase.tick(mock(DroSlayerConfig.class));
            field(purchase,"retryAt",0L);
            purchase.tick(mock(DroSlayerConfig.class)); // Offer has not arrived yet.
            exchange.verify(() -> Rs2GrandExchange.buyItem("Steel cannonball",120,quantity), times(1));
            when(offer.getItemId()).thenReturn(ItemID.STEEL_CANNONBALL);
            when(offer.getPrice()).thenReturn(120L);
            when(offer.getTotalQuantity()).thenReturn(quantity);
            when(offer.getState()).thenReturn(GrandExchangeOfferState.BOUGHT);
            field(purchase,"retryAt",0L); purchase.tick(mock(DroSlayerConfig.class));
            field(purchase,"retryAt",0L); purchase.tick(mock(DroSlayerConfig.class));
            field(purchase,"retryAt",0L); purchase.tick(mock(DroSlayerConfig.class));
            exchange.verify(() -> Rs2GrandExchange.collectOffer(GrandExchangeSlots.values()[0],true));
            exchange.verify(() -> Rs2GrandExchange.collectAll(true), never());
        }
    }
    private static Object get(Object target,String name) throws Exception {
        var f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void field(Object target,String name,Object value) throws Exception {
        var f=target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void phase(Object target,String value) throws Exception {
        var f=target.getClass().getDeclaredField("phase"); f.setAccessible(true);
        f.set(target,Enum.valueOf((Class)f.getType(),value));
    }
}
