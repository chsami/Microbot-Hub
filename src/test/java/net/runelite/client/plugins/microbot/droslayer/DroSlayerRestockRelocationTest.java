package net.runelite.client.plugins.microbot.droslayer;

import java.lang.reflect.Field;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DroSlayerRestockRelocationTest {
    @Test void partialStockCannotMasqueradeAsAFullTrip(){
        assertTrue(DroSlayerCannonPolicy.needsPurchase(41,0,350));
        assertTrue(DroSlayerCannonPolicy.needsPurchase(17,0,350));
        assertTrue(DroSlayerCannonPolicy.needsPurchase(0,17,350));
        assertFalse(DroSlayerCannonPolicy.needsPurchase(333,17,350));
        assertFalse(DroSlayerCannonPolicy.needsPurchase(Integer.MAX_VALUE,Integer.MAX_VALUE,350));
    }
    @Test void manualTeleportWithAnActiveMammothTaskBanksEvenAfterCannonWasDisabled()throws Exception{
        var script=new DroSlayerScript();var config=mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.enableAutoTravel()).thenReturn(true);when(config.enableAutoBanking()).thenReturn(true);
        when(config.bankForCannonballs()).thenReturn(true);when(config.wildernessPreferCannon()).thenReturn(true);
        when(config.cannonballThreshold()).thenReturn(30);
        var assignment=DroSlayerWildernessTasks.forAssignedTask("Mammoths");assertNotNull(assignment);
        set(script,"config",config);set(script,"wildernessAssignment",assignment);set(script,"taskDestination",assignment.getDestination());
        Field f=DroSlayerScript.class.getDeclaredField("cannonPolicy");f.setAccessible(true);
        var policy=(DroSlayerCannonPolicy)f.get(script);policy.selectTask("Mammoths");policy.disable();
        try(var player=mockStatic(Rs2Player.class);var pvp=mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Pvp.class);var inventory=mockStatic(Rs2Inventory.class);
            var reader=mockStatic(DroSlayerWildernessTaskReader.class);var runtime=mockStatic(DroSlayerRuntime.class)){
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Mammoths");
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3164,3487,0));
            DroSlayerPlugin.setState(DroSlayerState.FIGHTING);
            assertTrue(recover(script));assertEquals(DroSlayerState.BANKING,DroSlayerPlugin.getState());
            runtime.verify(DroSlayerRuntime::cancelWalk);assertEquals(assignment.getDestination(),get(script,"taskDestination"));
        }finally{DroSlayerPlugin.setState(DroSlayerState.IDLE);}
    }
    @Test void realBankPurchaseGateStartsForPartialStockRatherThanLeavingWithSeventeenShots()throws Exception{
        var script=new DroSlayerScript();var config=mock(DroSlayerConfig.class);
        when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);
        when(config.wildernessPreferCannon()).thenReturn(true);when(config.enableAutoBanking()).thenReturn(true);
        when(config.bankForCannonballs()).thenReturn(true);when(config.autoBuyCannonballs()).thenReturn(true);
        when(config.cannonballThreshold()).thenReturn(30);
        var assignment=DroSlayerWildernessTasks.forAssignedTask("Mammoths");
        set(script,"config",config);set(script,"wildernessAssignment",assignment);set(script,"taskDestination",assignment.getDestination());
        try(var bank=mockStatic(net.runelite.client.plugins.microbot.util.bank.Rs2Bank.class);
            var inventory=mockStatic(Rs2Inventory.class);var player=mockStatic(Rs2Player.class);
            var reader=mockStatic(DroSlayerWildernessTaskReader.class);
            var count=mockStatic(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer.class);
            var spots=mockStatic(CannonSpot.class);
            var exchange=mockStatic(net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange.class)){
            reader.when(DroSlayerWildernessTaskReader::getTaskName).thenReturn("Mammoths");
            bank.when(net.runelite.client.plugins.microbot.util.bank.Rs2Bank::isOpen).thenReturn(true);
            inventory.when(()->Rs2Inventory.hasItem(anyString())).thenReturn(true);
            inventory.when(()->Rs2Inventory.hasItem(net.runelite.api.ItemID.COINS_995)).thenReturn(true);
            spots.when(()->CannonSpot.isAllowed(any(),anyString())).thenReturn(true);
            bank.when(()->net.runelite.client.plugins.microbot.util.bank.Rs2Bank.count("Steel cannonball",true)).thenReturn(17);
            bank.when(()->net.runelite.client.plugins.microbot.util.bank.Rs2Bank.count(net.runelite.api.ItemID.COINS_995)).thenReturn(500000);
            exchange.when(()->net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange.getPrice(net.runelite.api.ItemID.STEEL_CANNONBALL)).thenReturn(100);
            count.when(net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer::getSlayerTaskSize).thenReturn(70);
            var method=DroSlayerScript.class.getDeclaredMethod("tryBuyCannonballsAtBank");method.setAccessible(true);
            assertEquals(true,method.invoke(script));
        }
    }
    @Test void animationMovementAndNearbyCombatDoNotCauseRelocation()throws Exception{
        var script=new DroSlayerScript();var config=mock(DroSlayerConfig.class);
        when(config.enableAutoTravel()).thenReturn(true);when(config.slayerMaster()).thenReturn(DroSlayerMaster.KRYSTILIA);set(script,"config",config);
        var destination=new WorldPoint(3168,3595,0);set(script,"taskDestination",destination);
        try(var player=mockStatic(Rs2Player.class);var pvp=mockStatic(net.runelite.client.plugins.microbot.util.player.Rs2Pvp.class);var runtime=mockStatic(DroSlayerRuntime.class)){
            player.when(Rs2Player::getWorldLocation).thenReturn(destination);assertFalse(recover(script));
            player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3164,3487,0));
            player.when(Rs2Player::isMoving).thenReturn(true);assertFalse(recover(script));
            player.when(Rs2Player::isMoving).thenReturn(false);player.when(Rs2Player::isInCombat).thenReturn(true);
            assertFalse(recover(script));
            player.when(Rs2Player::isInCombat).thenReturn(false);
            pvp.when(net.runelite.client.plugins.microbot.util.player.Rs2Pvp::isInWilderness).thenReturn(true);
            assertFalse(recover(script),"Native Wilderness travel/combat remains authoritative");
            pvp.when(net.runelite.client.plugins.microbot.util.player.Rs2Pvp::isInWilderness).thenReturn(false);
            when(config.slayerMaster()).thenReturn(DroSlayerMaster.DURADEL);
            assertFalse(recover(script),"Ordinary Slayer dungeon coordinate handling is unchanged");
            runtime.verifyNoInteractions();
        }
    }
    private boolean recover(DroSlayerScript script)throws Exception{
        var method=DroSlayerScript.class.getDeclaredMethod("recoverDisplacedTask",boolean.class,int.class);
        method.setAccessible(true);return (boolean)method.invoke(script,true,70);
    }
    private void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private Object get(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
}
