package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.*;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class VisiblePrayerNativeInputTest {
    private static final class Fixture implements AutoCloseable {
        final Client client=mock(Client.class);
        final Mouse mouse=mock(Mouse.class);
        final Widget widget=mock(Widget.class);
        final NaturalMouse nativeMouse=mock(NaturalMouse.class),previousMouse=Microbot.naturalMouse;
        final Point previousPoint=PointerState.get();
        final MenuEntry previousMenu=Microbot.targetMenu;
        final MockedStatic<Microbot> microbot=mockStatic(Microbot.class);
        final MockedStatic<InputLoop> input=mockStatic(InputLoop.class);
        final MockedStatic<Global> global=mockStatic(Global.class);
        final MockedStatic<Rs2Keyboard> keyboard=mockStatic(Rs2Keyboard.class);
        final InputLoop.Emit emit=mock(InputLoop.Emit.class);
        final List<String> clicks=new ArrayList<>();
        final FcPrayerUi ui=new FcPrayerUi(()->true);
        Fixture() {
            ClientThread thread=mock(ClientThread.class);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(call->
                Optional.ofNullable(((Callable<?>)call.getArgument(0)).call()));
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getMouse).thenReturn(mouse);
            when(client.getVarbitValue(InterfaceTab.PRAYER.getHotkeyVarbit())).thenReturn(9);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(InterfaceTab.PRAYER.getVarcIntIndex());
            when(client.getCanvasWidth()).thenReturn(800);when(client.getCanvasHeight()).thenReturn(600);
            when(client.getWidget(Rs2PrayerEnum.PROTECT_MAGIC.getIndex())).thenReturn(widget);
            when(widget.getBounds()).thenReturn(new Rectangle(650,300,30,30));
            Microbot.naturalMouse=nativeMouse;PointerState.setFromBot(100,100);
            doAnswer(call->{PointerState.setFromBot(call.getArgument(0),call.getArgument(1));return null;})
                .when(nativeMouse).moveTo(anyInt(),anyInt());
            input.when(()->InputLoop.run(any())).thenAnswer(call->{
                ((InputLoop.Gesture)call.getArgument(0)).run(emit);return InputLoop.Result.COMPLETED;
            });
            doAnswer(call->{clicks.add(Microbot.targetMenu==null?"Native button":Microbot.targetMenu.getOption());return null;})
                .when(emit).press(anyInt(),anyInt(),anyInt());
            global.when(()->Global.sleepUntil(any(),any(),eq(80L),eq(4))).thenAnswer(call->{
                Microbot.targetMenu=null;return true;
            });
        }
        public void close() {
            Microbot.naturalMouse=previousMouse;Microbot.targetMenu=previousMenu;
            PointerState.setFromBot(previousPoint.getX(),previousPoint.getY());
            keyboard.close();global.close();input.close();microbot.close();
        }
    }
    @Test public void realWriterUsesExistingNativeMouseAndVisibleCoordinates() {
        try(Fixture f=new Fixture()) {
            assertTrue(f.ui.prayer(Rs2PrayerEnum.PROTECT_MAGIC,true));
            verify(f.nativeMouse,times(1)).moveTo(665,315);
            verify(f.emit).press(665,315,1);assertEquals(List.of("Activate"),f.clicks);
            verify(f.mouse).setLastClick(new Point(665,315));
            f.microbot.verify(()->Microbot.doInvoke(any(),any()),never());
            verify(f.client,never()).menuAction(anyInt(),anyInt(),any(),anyInt(),anyInt(),anyString(),anyString());
        }
    }
    @Test public void realWriterOpensBookWithConfiguredFKeyWithoutMouseClick() {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenReturn(3);
            assertFalse(f.ui.prayer(Rs2PrayerEnum.PROTECT_MAGIC,true));
            f.keyboard.verify(()->Rs2Keyboard.keyPress(KeyEvent.VK_F9));
            verifyNoInteractions(f.nativeMouse);assertTrue(f.clicks.isEmpty());
            verify(f.client,never()).runScript(any());
        }
    }
    @Test public void realFlickWaitsForFirstClickBeforeSecondAndReusesSameMouse() {
        try(Fixture f=new Fixture()) {
            when(f.client.getVarbitValue(Rs2PrayerEnum.PROTECT_MAGIC.getVarbit())).thenReturn(1);
            assertEquals(FcPrayerUi.Result.SENT,f.ui.resetPrayer(Rs2PrayerEnum.PROTECT_MAGIC,()->true,()->true));
            assertEquals(List.of("Deactivate","Activate"),f.clicks);
            verify(f.nativeMouse,times(1)).moveTo(665,315);verify(f.emit,times(2)).press(665,315,1);
            f.global.verify(()->Global.sleepUntil(any(),any(),eq(80L),eq(4)),times(2));
        }
    }
    @Test public void visibleInventoryFallbackUsesNativeMouseInEveryViewportLayout() {
        int[] ids={InterfaceID.Toplevel.STONE3,InterfaceID.ToplevelOsrsStretch.STONE3,InterfaceID.ToplevelPreEoc.STONE3};
        for(int layout=0;layout<ids.length;layout++)try(Fixture f=new Fixture()) {
            when(f.client.isResized()).thenReturn(layout!=0);
            when(f.client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT)).thenReturn(layout==2?1:0);
            when(f.client.getVarbitValue(VarbitID.STONE_INV_KEY)).thenReturn(13);
            when(f.client.getWidget(ids[layout])).thenReturn(f.widget);
            assertFalse(f.ui.inventory());
            assertEquals(List.of("Native button"),f.clicks);
            verify(f.nativeMouse).moveTo(665,315);verify(f.emit).press(665,315,1);
            f.keyboard.verifyNoInteractions();f.global.verifyNoInteractions();
            f.microbot.verify(()->Microbot.doInvoke(any(),any()),never());
            verify(f.client,never()).runScript(any());
        }
    }
    @Test public void liveCaveResumeDoesNotOpenSettingsOrChangeWorkingKeys() {
        try(Fixture f=new Fixture()) {
            FcKeybindings startup=new FcKeybindings(()->true);
            assertTrue(startup.step(true));
            assertTrue(startup.result().contains("retained game bindings"));
            f.keyboard.verifyNoInteractions();f.input.verifyNoInteractions();verifyNoInteractions(f.nativeMouse);
            verify(f.client,never()).setVarbit(anyInt(),anyInt());
            verify(f.client,never()).runScript(any());
        }
    }
    @Test public void unconsumedClickReportsFailureAndClearsArmedMenu() {
        try(Fixture f=new Fixture()) {
            f.global.when(()->Global.sleepUntil(any(),any(),eq(80L),eq(4))).thenReturn(false);
            try {f.ui.prayer(Rs2PrayerEnum.PROTECT_MAGIC,true);fail("Unconsumed input must not report success");}
            catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("not consumed"));}
            assertNull(Microbot.targetMenu);assertEquals(1,f.clicks.size());
        }
    }
    @Test public void clientThreadNeverWaitsForMouseOrTabInput() {
        try(Fixture f=new Fixture()) {
            when(f.client.isClientThread()).thenReturn(true);
            assertFalse(f.ui.prayer(Rs2PrayerEnum.PROTECT_MAGIC,true));assertFalse(f.ui.inventory());
            f.input.verifyNoInteractions();f.keyboard.verifyNoInteractions();verifyNoInteractions(f.nativeMouse);
        }
    }
}
