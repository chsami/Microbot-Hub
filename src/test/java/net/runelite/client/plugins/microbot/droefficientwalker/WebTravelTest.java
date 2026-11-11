package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.util.inventory.*;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WebTravelTest {
    @Test public void adjacentFloorCanStartSlashingWithoutWalkingOntoBlockedOrigin() {
        Transport edge=net.runelite.client.plugins.microbot.droefficientwalker.support.TransportFactory.row(
            Map.of("Origin","3095 3957 0","Destination","3094 3957 0","menuOption menuTarget objectID","Slash;Web;733"),TransportType.TRANSPORT);
        WorldPoint object=new WorldPoint(3095,3958,0);
        assertTrue(WebTravel.canInteractFrom(new WorldPoint(3095,3959,0),edge,object));
        assertFalse(WebTravel.canInteractFrom(new WorldPoint(3095,3961,0),edge,object));
        assertFalse(WebTravel.canInteractFrom(new WorldPoint(3095,3959,1),edge,object));
    }
    @Test public void knifeOrEquippedSlashWeaponCanCutButStabOnlyWeaponCannot() {
        ClientThread thread=mock(ClientThread.class);ItemManager manager=mock(ItemManager.class);
        Rs2ItemModel tentacle=mock(Rs2ItemModel.class);when(tentacle.getId()).thenReturn(12006);
        when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(c->Optional.ofNullable(((Callable<?>)c.getArgument(0)).call()));
        try(var micro=mockStatic(Microbot.class);var inventory=mockStatic(Rs2Inventory.class);var equipment=mockStatic(Rs2Equipment.class)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);micro.when(Microbot::getItemManager).thenReturn(manager);
            inventory.when(()->Rs2Inventory.hasItem(946)).thenReturn(true);
            assertTrue(WebTravel.canCut());
            inventory.when(()->Rs2Inventory.hasItem(946)).thenReturn(false);
            equipment.when(()->Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(tentacle);
            ItemStats stats=mock(ItemStats.class);when(manager.getItemStats(12006)).thenReturn(stats);
            when(stats.getEquipment()).thenReturn(ItemEquipmentStats.builder().aslash(90).build());
            assertTrue(WebTravel.canCut());
            when(stats.getEquipment()).thenReturn(ItemEquipmentStats.builder().astab(90).build());
            assertFalse(WebTravel.canCut());
            equipment.when(()->Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(null);
            assertFalse(WebTravel.canCut());
        }
    }

    @Test public void openPassageCrossesWithoutWaitingForMissingWebObject() throws Exception {
        WorldPoint from=new WorldPoint(3095,3959,0),to=new WorldPoint(3094,3957,0);
        AtomicReference<WorldPoint> player=new AtomicReference<>(from);
        Transport transport=net.runelite.client.plugins.microbot.droefficientwalker.support.TransportFactory.row(
            Map.of("Origin","3095 3957 0","Destination","3094 3957 0","menuOption menuTarget objectID","Slash;Web;733"),TransportType.TRANSPORT);
        Client client=mock(Client.class);Player local=mock(Player.class);when(client.getLocalPlayer()).thenReturn(local);
        when(local.getWorldLocation()).thenAnswer(c->player.get());
        NetworkTransportSupport bridge=new NetworkTransportSupport(client,mock(EfficientWalkerConfig.class),
            getClass().getClassLoader(),Object.class,new Object(),"unused",mock(CatalogTeleportSupport.class));
        ClientThread thread=mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(c->Optional.ofNullable(((Callable<?>)c.getArgument(0)).call()));
        when(thread.invoke(any(java.util.function.Supplier.class))).thenAnswer(c->((java.util.function.Supplier<?>)c.getArgument(0)).get());
        var execute=WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "executeWebTransport",Transport.class);execute.setAccessible(true);
        try(var micro=mockStatic(Microbot.class);var people=mockStatic(Rs2Player.class);var tiles=mockStatic(Rs2Tile.class);
            var walking=mockStatic(net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2Walker.class);
            var cutting=mockStatic(WebTravel.class,CALLS_REAL_METHODS)) {
            micro.when(Microbot::getClientThread).thenReturn(thread);
            people.when(Rs2Player::getWorldLocation).thenAnswer(c->player.get());
            tiles.when(()->Rs2Tile.getReachableTilesFromTile(from,8)).thenReturn(new HashMap<>(Map.of(to,1)));
            walking.when(()->net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2Walker.walkFastCanvas(to))
                .thenAnswer(c->{player.set(to);return true;});
            assertEquals("SUCCESS",execute.invoke(bridge,transport).toString());
            walking.verify(()->net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2Walker.walkFastCanvas(to));
            // Missing/blocked passage with no cutting tool must not turn into a crossing click.
            player.set(from);
            cutting.when(WebTravel::canCut).thenReturn(false);
            tiles.when(()->Rs2Tile.getReachableTilesFromTile(from,8)).thenReturn(new HashMap<>());
            assertEquals("FAILED",execute.invoke(bridge,transport).toString());
            walking.verify(()->net.runelite.client.plugins.microbot.droefficientwalker.support.Rs2Walker.walkFastCanvas(to),times(1));
        }
    }
}
