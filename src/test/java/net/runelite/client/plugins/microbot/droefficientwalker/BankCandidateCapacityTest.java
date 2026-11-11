package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Map;
import java.util.stream.IntStream;
import net.runelite.api.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankCandidateCapacityTest {
    @Test void fullPotionsRejectABankedRingWithAnExplicitSpaceReason(){
        Client client=inventory(IntStream.range(0,28).mapToObj(i->new Item(6685,1)).toArray(Item[]::new));
        assertFalse(BankCandidateCapacity.canPrepare(client,Map.of(11988,1)));
        assertEquals("inventory-space requiredSlots=1 availableSlots=0 occupiedSlots=28",BankCandidateCapacity.rejection(client,Map.of(11988,1)));
    }
    @Test void emptyInventoryAndAlreadyCarriedStackHaveRoom(){
        Client client=inventory(new Item[0]);assertTrue(BankCandidateCapacity.canPrepare(client,Map.of(11988,1)));
        Item[] items=IntStream.range(0,28).mapToObj(i->new Item(i==0?995:6685,1)).toArray(Item[]::new);
        client=inventory(items);ItemComposition coins=mock(ItemComposition.class);when(coins.isStackable()).thenReturn(true);when(client.getItemDefinition(995)).thenReturn(coins);
        assertTrue(BankCandidateCapacity.canPrepare(client,Map.of(995,100)));
    }
    @Test void duplicateFoodRemainsReclaimableButRequiredFoodIsPreserved(){
        Client client=inventory(IntStream.range(0,28).mapToObj(i->new Item(385,1)).toArray(Item[]::new));
        ItemComposition food=mock(ItemComposition.class);when(food.getInventoryActions()).thenReturn(new String[]{"Eat"});when(client.getItemDefinition(385)).thenReturn(food);
        assertTrue(BankCandidateCapacity.canPrepare(client,Map.of(11988,1)));
        assertFalse(BankCandidateCapacity.canPrepare(client,Map.of(11988,1,385,1)));
    }
    @Test void bankScreenUsesLiveInvContainerWhenInventoryAliasIsMissing(){
        Client client=mock(Client.class);ItemContainer alternate=mock(ItemContainer.class);
        when(alternate.getItems()).thenReturn(new Item[0]);
        when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(alternate);
        when(client.getItemDefinition(anyInt())).thenAnswer(inv->mock(ItemComposition.class));
        assertTrue(BankCandidateCapacity.canPrepare(client,Map.of(8013,1)));
        verify(client).getItemContainer(net.runelite.api.gameval.InventoryID.INV);
    }
    @Test void missingInventoryAndDefinitionsAreDistinctFromFullInventory(){
        Client client=mock(Client.class);
        assertEquals("inventory-container-unavailable",BankCandidateCapacity.rejection(client,Map.of(11988,1)));
        client=inventory(new Item[0]);when(client.getItemDefinition(11988)).thenReturn(null);
        assertEquals("item-definition-unavailable item=11988",BankCandidateCapacity.rejection(client,Map.of(11988,1)));
        assertFalse(BankCandidateCapacity.canPrepare(client,Map.of(11988,1)));
    }
    private static Client inventory(Item[] items){
        Client client=mock(Client.class);ItemContainer container=mock(ItemContainer.class);
        when(container.getItems()).thenReturn(items);when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
        when(client.getItemDefinition(anyInt())).thenAnswer(i->mock(ItemComposition.class));return client;
    }
}
