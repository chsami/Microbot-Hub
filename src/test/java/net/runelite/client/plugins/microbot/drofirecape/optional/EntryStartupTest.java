package net.runelite.client.plugins.microbot.drofirecape.optional;
import net.runelite.client.plugins.microbot.drofirecape.DroFirecapeConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.CollisionGrid;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.Protection;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.SupplyAck;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pre-entry potion acknowledgements must progress with the default Any selection. */
public class EntryStartupTest {
    private static void set(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static Object get(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static FcFrame frame(int tick,int itemId)throws Exception {
        Client client=mock(Client.class);WorldView view=mock(WorldView.class);Player player=mock(Player.class);
        when(client.getTickCount()).thenReturn(tick);when(client.getWorld()).thenReturn(307);
        when(client.getWorldView(anyInt())).thenReturn(view);when(client.getTopLevelWorldView()).thenReturn(view);
        when(view.getBaseX()).thenReturn(2368);when(view.getBaseY()).thenReturn(5120);
        when(player.getLocalLocation()).thenReturn(new LocalPoint(70*128+64,48*128+64,WorldView.TOPLEVEL));
        when(player.getWorldLocation()).thenReturn(new WorldPoint(2438,5168,0));
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);when(client.getEnergy()).thenReturn(10000);
        ItemContainer inventory=mock(ItemContainer.class);ItemComposition item=mock(ItemComposition.class);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(inventory.getItems()).thenReturn(new Item[]{new Item(itemId,1)});
        when(client.getItemDefinition(itemId)).thenReturn(item);
        when(item.getName()).thenReturn(itemId==3024?"Super restore(4)":"Super restore(3)");
        when(item.getInventoryActions()).thenReturn(new String[]{"Drink"});
        Constructor<FcFrame> constructor=FcFrame.class.getDeclaredConstructor(Client.class,WorldView.class,Player.class,
            List.class,CollisionGrid.class,Protection.class,Map.class,int.class,Set.class,Map.class,boolean.class);
        constructor.setAccessible(true);
        FcFrame f=constructor.newInstance(client,view,player,List.of(),new CollisionGrid(new int[104][104]),
            Protection.NONE,Map.of(),7,Set.of(),Map.of(),false);
        assertFalse(f.cave);return f;
    }
    private static final class Fixture {
        final DroFirecapeScript script=new DroFirecapeScript();
        final FcActions actions=mock(FcActions.class);
        final DroFirecapeConfig config=mock(DroFirecapeConfig.class);
        final SupplyAck ack;
        final Object predictor=new Object();
        final FcPredictorGate gate;
        Fixture()throws Exception {
            set(script,"actions",actions);
            set(script,"config",config);set(script,"configManager",mock(ConfigManager.class));
            set(script,"state",DroFirecapeScript.State.ROTATION_WAIT);
            ack=(SupplyAck)get(script,"supplyAck");gate=(FcPredictorGate)get(script,"entryGate");
            when(actions.zoomConfirmed()).thenReturn(true);when(actions.entryProtectionReady(false)).thenReturn(true);
            when(actions.enterPredictedRotation(any(),anyInt(),eq(false))).thenReturn(true);
        }
        FcPredictorGate.Sample sample(int second,int rotation) {
            return new FcPredictorGate.Sample(predictor,307,2,rotation,100,second,
                System.currentTimeMillis(),false,true,false,"test predictor");
        }
        void calibrate(int rotation) {
            gate.entryReady(sample(20,rotation),System.currentTimeMillis(),0);
            gate.entryReady(sample(21,rotation),System.currentTimeMillis(),0);
        }
        void waitAtEntrance(FcFrame frame,int second,int rotation)throws Exception {
            when(actions.predictor(false)).thenReturn(sample(second,rotation));
            Method wait=DroFirecapeScript.class.getDeclaredMethod("rotationWait",FcFrame.class);
            wait.setAccessible(true);wait.invoke(script,frame);
        }
    }
    @Test public void defaultAnyEntersAfterObservedStartupRestoreWithoutConfigToggle()throws Exception {
        Fixture f=new Fixture();f.calibrate(6);
        f.ack.sent(3024,1,100,SupplyAck.Kind.RESTORE);set(f.script,"brewDebt",3);
        f.waitAtEntrance(frame(101,3026),22,6);
        assertFalse(f.ack.pending());assertEquals(0,get(f.script,"brewDebt"));
        assertEquals(DroFirecapeScript.State.ENTERING,f.script.state());
        verify(f.actions).enterPredictedRotation(f.gate,6,false);verify(f.actions,never()).idlePrayersOff();
    }
    @Test public void unconsumedStartupDoseTimesOutInsteadOfPermanentlyBlockingAnyEntry()throws Exception {
        Fixture f=new Fixture();f.calibrate(6);f.ack.sent(3024,1,100,SupplyAck.Kind.RESTORE);
        f.waitAtEntrance(frame(101,3024),22,6);
        assertTrue(f.ack.pending());verify(f.actions,never()).enterPredictedRotation(any(),anyInt(),eq(false));
        f.waitAtEntrance(frame(105,3024),23,6);
        assertFalse(f.ack.pending());verify(f.actions).enterPredictedRotation(f.gate,6,false);
    }
    @Test public void explicitSelectionStillWaitsAndChangingBackToAnyWorks()throws Exception {
        Fixture f=new Fixture();f.calibrate(6);when(f.config.entryRotation()).thenReturn(5);
        f.waitAtEntrance(frame(101,3024),22,6);
        verify(f.actions,never()).enterPredictedRotation(any(),anyInt(),eq(false));
        when(f.config.entryRotation()).thenReturn(0);f.waitAtEntrance(frame(102,3024),23,6);
        verify(f.actions).enterPredictedRotation(f.gate,6,false);
    }
    @Test public void supplyAcknowledgementDoesNotBypassUncalibratedPredictor()throws Exception {
        Fixture f=new Fixture();f.ack.sent(3024,1,100,SupplyAck.Kind.RESTORE);
        f.waitAtEntrance(frame(101,3026),22,6);
        assertFalse(f.ack.pending());verify(f.actions,never()).enterPredictedRotation(any(),anyInt(),eq(false));
    }
}
