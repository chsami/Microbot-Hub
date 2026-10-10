package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MinigameInputTest {
    @Test public void groupingUsesVisibleWidgetOperationAndRejectsHiddenControls()throws Exception {
        try(MockedStatic<Microbot> microbot=mockStatic(Microbot.class)) {
            Client client=mock(Client.class);ClientThread thread=mock(ClientThread.class);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(thread);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            when(client.getCanvasWidth()).thenReturn(1000);when(client.getCanvasHeight()).thenReturn(800);
            Widget widget=mock(Widget.class);when(widget.getId()).thenReturn(46333957);
            when(widget.getIndex()).thenReturn(-1);when(widget.getBounds()).thenReturn(new Rectangle(720,430,160,30));
            Method click=FcActions.class.getDeclaredMethod("clickMinigameWidget",Widget.class,String.class,int.class);click.setAccessible(true);
            FcActions actions=new FcActions();assertTrue((Boolean)click.invoke(actions,widget,"Grouping",1));
            microbot.verify(()->Microbot.doInvoke(argThat((NewMenuEntry e)->e.getParam1()==46333957&&e.getParam0()==-1&&e.getIdentifier()==1&&e.getType()==net.runelite.api.MenuAction.CC_OP),eq(new Rectangle(722,432,156,26))),times(1));
            when(widget.isHidden()).thenReturn(true);assertFalse((Boolean)click.invoke(actions,widget,"Grouping",1));
            when(widget.isHidden()).thenReturn(false);when(widget.getBounds()).thenReturn(new Rectangle(-50,-40,20,20));
            assertFalse((Boolean)click.invoke(actions,widget,"Grouping",1));
            microbot.verify(()->Microbot.doInvoke(any(NewMenuEntry.class),any(Rectangle.class)),times(1));
        }
    }

    private static final class SelectionFixture implements AutoCloseable {
        final Client client=mock(Client.class);final FcActions actions=new FcActions();
        final Player player=mock(Player.class);final Mouse mouse=mock(Mouse.class);
        final NaturalMouse nativeMouse=mock(NaturalMouse.class),oldMouse=Microbot.naturalMouse;
        final Point oldPoint=PointerState.get();final MenuEntry oldMenu=Microbot.targetMenu;
        final boolean paused=Microbot.pauseAllScripts.get();
        final InputLoop.Emit emit=mock(InputLoop.Emit.class);
        final MockedStatic<InputLoop> inputs=mockStatic(InputLoop.class);
        final MockedStatic<InputArbiter> arbiter=mockStatic(InputArbiter.class);
        final Widget grouping=mock(Widget.class),label=mock(Widget.class),dropdown=mock(Widget.class),list=mock(Widget.class),row=mock(Widget.class),teleport=mock(Widget.class),teleportText=mock(Widget.class);
        final MockedStatic<Microbot> microbot=mockStatic(Microbot.class);
        final MockedStatic<net.runelite.client.plugins.microbot.util.tabs.Rs2Tab> tabs=mockStatic(net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.class);
        SelectionFixture() {
            ClientThread thread=mock(ClientThread.class);microbot.when(Microbot::getClient).thenReturn(client);microbot.when(Microbot::getClientThread).thenReturn(thread);
            when(thread.runOnClientThreadOptional(any())).thenAnswer(i->Optional.ofNullable(((Callable<?>)i.getArgument(0)).call()));
            when(client.getCanvasWidth()).thenReturn(1000);when(client.getCanvasHeight()).thenReturn(800);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);
            when(player.getLocalLocation()).thenReturn(new LocalPoint(6400,6400));
            microbot.when(Microbot::getMouse).thenReturn(mouse);
            Microbot.pauseAllScripts.set(false);Microbot.naturalMouse=nativeMouse;PointerState.setFromBot(100,100);
            inputs.when(()->InputLoop.run(any())).thenAnswer(i->{((InputLoop.Gesture)i.getArgument(0)).run(emit);return InputLoop.Result.COMPLETED;});
            doAnswer(i->{PointerState.setFromBot(i.getArgument(0),i.getArgument(1));return null;}).when(nativeMouse).moveTo(anyInt(),anyInt());
            tabs.when(net.runelite.client.plugins.microbot.util.tabs.Rs2Tab::getCurrentTab).thenReturn(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.CHAT);
            when(client.getWidget(46333957)).thenReturn(grouping);when(grouping.getOnOpListener()).thenReturn(new Object[]{489,0,0});
            when(client.getWidget(4980747)).thenReturn(label);when(label.getText()).thenReturn("Barbarian Assault");
            when(client.getWidget(4980760)).thenReturn(dropdown);when(dropdown.getSpriteId()).thenReturn(773);
            when(dropdown.getBounds()).thenReturn(new Rectangle(720,270,160,20));
            when(client.getWidget(4980758)).thenReturn(list);when(list.getId()).thenReturn(4980758);
            when(list.getBounds()).thenReturn(new Rectangle(720,290,160,170));when(list.getDynamicChildren()).thenReturn(new Widget[]{row});
            when(row.getText()).thenReturn("<col=ff9040>TzHaar Fight Pit</col>");when(row.getIndex()).thenReturn(21);
            when(row.getBounds()).thenReturn(new Rectangle(720,910,160,16));
            when(client.getWidget(4980768)).thenReturn(teleport);when(teleport.getId()).thenReturn(4980768);when(teleport.getIndex()).thenReturn(-1);
            when(teleport.getBounds()).thenReturn(new Rectangle(720,470,160,30));
            when(client.getWidget(InterfaceID.Grouping.TELEPORT_TEXT1)).thenReturn(teleportText);
            when(teleportText.getText()).thenReturn("Teleport");when(teleportText.getParent()).thenReturn(teleport);
            when(teleportText.getBounds()).thenReturn(new Rectangle(724,473,152,24));
        }
        void ready()throws Exception {java.lang.reflect.Field f=FcActions.class.getDeclaredField("uiAt");f.setAccessible(true);f.setLong(actions,0);}
        public void close(){Microbot.naturalMouse=oldMouse;Microbot.targetMenu=oldMenu;Microbot.pauseAllScripts.set(paused);
            PointerState.setFromBot(oldPoint.getX(),oldPoint.getY());arbiter.close();inputs.close();tabs.close();microbot.close();}
    }
    @Test public void offscreenDestinationKeepsOriginalIndexSelectionThenWaitsForObservedLabel()throws Exception {
        try(SelectionFixture f=new SelectionFixture()) {
            assertEquals("Selecting TzHaar Fight Pit",f.actions.minigameStep());
            f.microbot.verify(()->Microbot.doInvoke(argThat((NewMenuEntry e)->e.getParam1()==4980758&&e.getParam0()==21&&e.getIdentifier()==1),
                eq(new Rectangle(722,292,156,166))),times(1));
            assertNotEquals("TELEPORT_SENT",f.actions.minigameStep());
            when(f.label.getText()).thenReturn("<col=ff9040>TzHaar Fight Pit</col>");f.ready();
            assertEquals("TELEPORT_SENT",f.actions.minigameStep());
            verify(f.emit).press(800,485,1);
            f.microbot.verify(()->Microbot.doInvoke(argThat((NewMenuEntry e)->e.getParam1()==4980768),any(Rectangle.class)),never());
        }
    }
    @Test public void selectionDoesNotRequireDestinationBoundsAndClipsItsAnchorToParent() {
        try(SelectionFixture f=new SelectionFixture()) {
            when(f.row.getBounds()).thenReturn(null);when(f.row.isHidden()).thenReturn(true);
            Widget parent=mock(Widget.class);when(f.list.getParent()).thenReturn(parent);
            when(parent.getBounds()).thenReturn(new Rectangle(700,300,200,120));
            assertEquals("Selecting TzHaar Fight Pit",f.actions.minigameStep());
            f.microbot.verify(()->Microbot.doInvoke(any(NewMenuEntry.class),eq(new Rectangle(722,302,156,116))),times(1));
        }
    }
    @Test public void hiddenListOrMissingDestinationCannotClaimSelectionOrTeleport() {
        for(boolean hidden:new boolean[]{true,false})try(SelectionFixture f=new SelectionFixture()) {
            when(f.list.isHidden()).thenReturn(hidden);
            if(!hidden)when(f.list.getDynamicChildren()).thenReturn(new Widget[0]);
            assertTrue(f.actions.minigameStep().startsWith("TzHaar selection not sent"));
            f.microbot.verify(()->Microbot.doInvoke(any(NewMenuEntry.class),any(Rectangle.class)),never());
        }
    }
    @Test public void zeroSizeListCanUseVisibleDropdownAnchor() {
        try(SelectionFixture f=new SelectionFixture()) {
            when(f.list.getBounds()).thenReturn(new Rectangle());
            assertEquals("Selecting TzHaar Fight Pit",f.actions.minigameStep());
            f.microbot.verify(()->Microbot.doInvoke(any(NewMenuEntry.class),eq(new Rectangle(722,272,156,16))),times(1));
        }
    }
    @Test public void nativeTeleportClearsStaleWalkActionAndClicksOnlyTheVisibleLabel() {
        try(SelectionFixture f=new SelectionFixture()) {
            when(f.label.getText()).thenReturn("TzHaar Fight Pit");
            Microbot.targetMenu=new NewMenuEntry().type(MenuAction.WALK).option("Walk here");
            doAnswer(i->{assertNull(Microbot.targetMenu);return null;}).when(f.emit).press(anyInt(),anyInt(),anyInt());
            assertEquals("TELEPORT_SENT",f.actions.minigameStep());
            verify(f.nativeMouse).moveTo(800,485);verify(f.emit).press(800,485,1);verify(f.emit).release(800,485,1);
            verify(f.mouse).setLastClick(new Point(800,485));verify(f.mouse,never()).click(any(Rectangle.class));
            f.microbot.verify(()->Microbot.doInvoke(any(NewMenuEntry.class),any(Rectangle.class)),never());
        }
    }
    @Test public void minimapOverlapRejectsTeleportInAllLayouts() {
        for(int id:new int[]{InterfaceID.Toplevel.MINIMAP,InterfaceID.ToplevelOsrsStretch.MINIMAP,InterfaceID.ToplevelPreEoc.MINIMAP})
        try(SelectionFixture f=new SelectionFixture()) {
            when(f.label.getText()).thenReturn("TzHaar Fight Pit");Widget map=mock(Widget.class);
            when(f.client.getWidget(id)).thenReturn(map);when(map.getBounds()).thenReturn(new Rectangle(700,400,200,200));
            assertNotEquals("TELEPORT_SENT",f.actions.minigameStep());verifyNoInteractions(f.nativeMouse,f.emit);
            f.microbot.verify(()->Microbot.doInvoke(any(NewMenuEntry.class),any(Rectangle.class)),never());
        }
    }
    @Test public void minimapOverlapRejectsDestinationSelectionToo() {
        try(SelectionFixture f=new SelectionFixture()) {
            Widget map=mock(Widget.class);when(f.client.getWidget(InterfaceID.Toplevel.MINIMAP)).thenReturn(map);
            when(map.getBounds()).thenReturn(new Rectangle(700,250,200,230));
            assertTrue(f.actions.minigameStep().startsWith("TzHaar selection not sent"));
            f.microbot.verify(()->Microbot.doInvoke(any(NewMenuEntry.class),any(Rectangle.class)),never());verifyNoInteractions(f.emit);
        }
    }
    @Test public void runningOrQueuedWalkWaitsBeforeTeleportInput() {
        for(boolean queued:new boolean[]{false,true})try(SelectionFixture f=new SelectionFixture()) {
            when(f.label.getText()).thenReturn("TzHaar Fight Pit");
            if(queued)when(f.client.getLocalDestinationLocation()).thenReturn(new LocalPoint(10000,10000));
            else when(f.player.getPoseAnimation()).thenReturn(824);
            assertEquals("Waiting to stop moving before minigame teleport",f.actions.minigameStep());
            verifyNoInteractions(f.nativeMouse,f.emit);
        }
    }
    @Test public void buttonDisappearingDuringMouseTravelCannotClickThrough() {
        try(SelectionFixture f=new SelectionFixture()) {
            when(f.label.getText()).thenReturn("TzHaar Fight Pit");
            doAnswer(i->{PointerState.setFromBot(i.getArgument(0),i.getArgument(1));when(f.teleport.isHidden()).thenReturn(true);return null;})
                .when(f.nativeMouse).moveTo(anyInt(),anyInt());
            assertNotEquals("TELEPORT_SENT",f.actions.minigameStep());verify(f.emit,never()).press(anyInt(),anyInt(),anyInt());
        }
    }
    @Test public void movementBeginningDuringMouseTravelCancelsTeleportPress() {
        try(SelectionFixture f=new SelectionFixture()) {
            when(f.label.getText()).thenReturn("TzHaar Fight Pit");
            doAnswer(i->{PointerState.setFromBot(i.getArgument(0),i.getArgument(1));when(f.player.getPoseAnimation()).thenReturn(824);return null;})
                .when(f.nativeMouse).moveTo(anyInt(),anyInt());
            assertNotEquals("TELEPORT_SENT",f.actions.minigameStep());verify(f.emit,never()).press(anyInt(),anyInt(),anyInt());
        }
    }
    @Test public void startupSettingsButtonCannotUseMinimapBounds()throws Exception {
        try(SelectionFixture f=new SelectionFixture()) {
            Widget button=mock(Widget.class),map=mock(Widget.class);when(button.getBounds()).thenReturn(new Rectangle(750,80,50,30));
            when(f.client.getWidget(InterfaceID.Toplevel.MINIMAP)).thenReturn(map);when(map.getBounds()).thenReturn(new Rectangle(700,20,200,190));
            Method bounds=FcKeybindings.class.getDeclaredMethod("bounds",Widget.class);bounds.setAccessible(true);
            assertNull(bounds.invoke(null,button));
        }
    }
    @Test public void hiddenParentWrongLabelAndMenuOverlayBlockTeleport() {
        for(int scenario=0;scenario<3;scenario++)try(SelectionFixture f=new SelectionFixture()) {
            when(f.label.getText()).thenReturn("TzHaar Fight Pit");
            if(scenario==0){Widget parent=mock(Widget.class);when(parent.isHidden()).thenReturn(true);when(f.teleport.getParent()).thenReturn(parent);}
            if(scenario==1)when(f.teleportText.getText()).thenReturn("Join");
            if(scenario==2)when(f.client.isMenuOpen()).thenReturn(true);
            assertNotEquals("TELEPORT_SENT",f.actions.minigameStep());verifyNoInteractions(f.nativeMouse,f.emit);
        }
    }

    @Test public void settingsPanelVisibilityIsNotLostWhenItCoversUnderlyingMinimap()throws Exception {
        try(SelectionFixture f=new SelectionFixture()) {
            Widget panel=mock(Widget.class),map=mock(Widget.class);
            when(f.client.getWidget(InterfaceID.Settings.UNIVERSE)).thenReturn(panel);
            when(panel.getBounds()).thenReturn(new Rectangle(100,20,800,700));
            when(f.client.getWidget(InterfaceID.Toplevel.MINIMAP)).thenReturn(map);
            when(map.getBounds()).thenReturn(new Rectangle(700,20,200,190));
            Method visible=FcKeybindings.class.getDeclaredMethod("visible",int.class);visible.setAccessible(true);
            assertEquals(Boolean.TRUE,visible.invoke(null,InterfaceID.Settings.UNIVERSE));
            assertNull(FcActions.startupBounds(panel));
        }
    }

    private void setField(Object object,String name,Object value)throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private Widget settingsPanel(SelectionFixture f) {
        Widget panel=mock(Widget.class),close=mock(Widget.class);
        when(f.client.getWidget(InterfaceID.Settings.UNIVERSE)).thenReturn(panel);
        when(panel.getBounds()).thenReturn(new Rectangle(50,40,650,550));
        when(f.client.getWidget(InterfaceID.Settings.CLOSE)).thenReturn(close);
        when(close.getParent()).thenReturn(panel);when(close.getBounds()).thenReturn(new Rectangle(655,50,25,25));
        return panel;
    }
    @Test public void settingsCloseUsesConfiguredEscapeOnceAndWaitsForObservedClosure()throws Exception {
        try(SelectionFixture f=new SelectionFixture();MockedStatic<net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard> keys=
            mockStatic(net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.class)) {
            Widget panel=settingsPanel(f);
            when(f.client.getVarbitValue(net.runelite.api.gameval.VarbitID.KEYBINDING_ESC_TO_CLOSE)).thenReturn(1);
            FcKeybindings setup=new FcKeybindings(()->true);setField(setup,"startedAt",System.currentTimeMillis()-19000);
            for(int n=0;n<3;n++){setField(setup,"actionAt",0L);assertFalse("Do not finish with settings still open",setup.step(false));}
            keys.verify(()->net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE),times(1));
            verifyNoInteractions(f.emit,f.nativeMouse);
            when(panel.isHidden()).thenReturn(true);setField(setup,"actionAt",0L);
            assertFalse(setup.step(false));assertTrue(setup.step(false));
        }
    }
    @Test public void delayedSettingsCloseCannotSendASecondNativeClickToTheSameLocation()throws Exception {
        try(SelectionFixture f=new SelectionFixture()) {
            settingsPanel(f);FcKeybindings setup=new FcKeybindings(()->true);
            setField(setup,"startedAt",System.currentTimeMillis()-13000);
            for(int n=0;n<4;n++){setField(setup,"actionAt",0L);assertFalse(setup.step(false));}
            verify(f.emit,times(1)).press(anyInt(),anyInt(),eq(1));
        }
    }
    @Test public void settingsRootDisappearingDuringTravelRejectsItsStaleCloseWidget()throws Exception {
        try(SelectionFixture f=new SelectionFixture()) {
            Widget panel=settingsPanel(f);
            // A stale close widget can retain its own bounds after the modal disappears.
            Widget close=f.client.getWidget(InterfaceID.Settings.CLOSE);when(close.getParent()).thenReturn(null);
            doAnswer(a->{PointerState.setFromBot(a.getArgument(0),a.getArgument(1));when(panel.isHidden()).thenReturn(true);return null;})
                .when(f.nativeMouse).moveTo(anyInt(),anyInt());
            FcKeybindings setup=new FcKeybindings(()->true);setField(setup,"startedAt",System.currentTimeMillis()-13000);
            assertFalse(setup.step(false));verify(f.emit,never()).press(anyInt(),anyInt(),anyInt());
        }
    }

}
