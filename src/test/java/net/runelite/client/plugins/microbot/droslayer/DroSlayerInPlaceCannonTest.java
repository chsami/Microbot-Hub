package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerInPlaceCannonTest {
    @Test void observedOwnedCannonResumesWithoutRetrievalButForeignOrOtherWorldCannonsDoNot() throws Exception {
        for (String scenario : new String[]{"owned", "manual", "foreign", "other-world", "no-stage", "not-at-task"}) {
            var script = new DroSlayerScript();var config = mock(DroSlayerConfig.class);
            set(script,"config",config);set(script,"cannonStartupCheck",!scenario.equals("manual"));
            set(script,"cannonAssemblyRecovery",true);set(script,"cannonStartupPickup",true);
            set(script,"cannonDeploymentWorld",scenario.equals("manual")?0:scenario.equals("other-world")?318:319);
            WorldPoint owner = new WorldPoint(3190,3960,0);
            set(script,"lastCannonPlacementTile",owner);
            Client client = mock(Client.class);when(client.getWorld()).thenReturn(319);
            when(client.getVarpValue(VarPlayer.CANNON_COORD)).thenReturn((owner.getPlane() << 28) | (owner.getX() << 14) | owner.getY());
            ClientThread thread = mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i -> Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            var cache = mock(Rs2TileObjectCache.class,RETURNS_DEEP_STUBS);
            var cannon = mock(Rs2TileObjectModel.class);
            when(cannon.getWorldLocation()).thenReturn(scenario.equals("foreign")?new WorldPoint(3191,3960,0):owner);
            when(cache.query().where(any()).within(any(WorldPoint.class),eq(50)).nearestOnClientThread()).thenReturn(cannon);
            try (var micro=mockStatic(Microbot.class);var player=mockStatic(Rs2Player.class);
                 var bank=mockStatic(Rs2Bank.class);var inventory=mockStatic(Rs2Inventory.class);
                 var runtime=mockStatic(DroSlayerRuntime.class);var location=mockStatic(DroSlayerStartupLocation.class)) {
                micro.when(Microbot::getClient).thenReturn(client);micro.when(Microbot::getClientThread).thenReturn(thread);
                micro.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
                micro.when(() -> Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.DROPCANNON))
                    .thenReturn(scenario.equals("no-stage")?0:4);
                player.when(Rs2Player::getWorldLocation).thenReturn(owner);
                location.when(() -> DroSlayerStartupLocation.atCurrentTask(config)).thenReturn(!scenario.equals("not-at-task"));
                var check=DroSlayerScript.class.getDeclaredMethod("checkStartupCannonSupplies");check.setAccessible(true);
                boolean pending=(boolean)check.invoke(script);
                if (scenario.equals("owned") || scenario.equals("manual")) {
                    assertFalse(pending);assertEquals(false,get(script,"cannonAssemblyRecovery"));
                    assertEquals(false,get(script,"cannonStartupPickup"));assertEquals(true,get(script,"cannonPlaced"));
                    runtime.verifyNoInteractions();bank.verifyNoInteractions();
                    when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
                    when(config.bankForCannonballs()).thenReturn(true);
                    when(config.enableAutoBanking()).thenReturn(true);
                    when(config.cannonballThreshold()).thenReturn(50);
                    set(script,"isUsingCannon",true);
                    try(var slayer=mockStatic(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer.class)) {
                        slayer.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(20);
                        var restock=DroSlayerScript.class.getDeclaredMethod("shouldRestockCannonballs");restock.setAccessible(true);
                        inventory.when(()->Rs2Inventory.itemQuantity("Steel cannonball",true)).thenReturn(300);
                        assertEquals(false,restock.invoke(script),"50 below the 350 refill target is still usable");
                        inventory.when(()->Rs2Inventory.itemQuantity("Steel cannonball",true)).thenReturn(25);
                        micro.when(()->Microbot.getVarbitPlayerValue(VarPlayer.CANNON_AMMO)).thenReturn(30);
                        assertEquals(false,restock.invoke(script),"Loaded ammunition counts on manual startup too");
                        micro.when(()->Microbot.getVarbitPlayerValue(VarPlayer.CANNON_AMMO)).thenReturn(24);
                        assertEquals(true,restock.invoke(script),"Actual low-ammo threshold is preserved");
                    }
                } else {
                    assertTrue(pending,scenario);assertEquals(true,get(script,"cannonAssemblyRecovery"));
                    runtime.verify(() -> DroSlayerRuntime.walkToBankAndOpen(config,"Verify saved cannon recovery supplies"));
                }
            }
        }
    }
    private static void set(Object object,String name,Object value)throws Exception { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value); }
    private static Object get(Object object,String name)throws Exception { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object); }
}
