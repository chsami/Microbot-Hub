/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VisiblePrayerUiTest {
    private static final Rs2PrayerEnum PRAYER=Rs2PrayerEnum.PROTECT_MAGIC;
    private static final class Input implements FcPrayerUi.Input {
        int tab=InterfaceTab.INVENTORY.getVarcIntIndex(),prayerKey=KeyEvent.VK_F9,inventoryKey=KeyEvent.VK_F2;
        Rectangle button=new Rectangle(650,300,30,30);
        Rectangle tabButton;
        boolean active,allowed=true,early=true,rejectClick;
        long now=1000;
        Runnable afterMove=()->{},afterClick=()->{};
        final List<Integer> keys=new ArrayList<>();
        final List<Point> points=new ArrayList<>();
        final List<NewMenuEntry> entries=new ArrayList<>();
        final FcPrayerUi ui=new FcPrayerUi(this,()->allowed,()->now);
        public FcPrayerUi.View capture(InterfaceTab target,Rs2PrayerEnum prayer) {
            return new FcPrayerUi.View(tab,target==InterfaceTab.PRAYER?prayerKey:inventoryKey,button,active);
        }
        public void key(int key){keys.add(key);}
        public Rectangle tabButton(InterfaceTab tab){return tabButton;}
        public void move(Point point,BooleanSupplier permitted){if(permitted.getAsBoolean())afterMove.run();}
        public boolean click(Point point,NewMenuEntry entry,BooleanSupplier permitted) {
            if(rejectClick||!permitted.getAsBoolean())return false;
            points.add(point);entries.add(entry);afterClick.run();return true;
        }
        void prayerOpen(){tab=InterfaceTab.PRAYER.getVarcIntIndex();}
    }

    @Test public void usesConfiguredFKeyAndWaitsForRealPrayerTab() {
        Input input=new Input();
        assertFalse(input.ui.prayer(PRAYER,true));
        assertEquals(List.of(KeyEvent.VK_F9),input.keys);assertTrue(input.entries.isEmpty());
        assertFalse(input.ui.prayer(PRAYER,true));assertEquals(1,input.keys.size());
        input.prayerOpen();assertTrue(input.ui.prayer(PRAYER,true));
        assertEquals(PRAYER.getIndex(),input.entries.get(0).getParam1());
        assertEquals("Activate",input.entries.get(0).getOption());
        Point point=input.points.get(0);assertTrue(input.button.contains(point.getX(),point.getY()));
    }
    @Test public void neverClicksHiddenOrMissingButton() {
        Input input=new Input();input.prayerOpen();input.button=null;
        assertFalse(input.ui.prayer(PRAYER,true));assertTrue(input.entries.isEmpty());assertTrue(input.keys.isEmpty());
    }
    @Test public void doesNotToggleAnAlreadyObservedDesiredPrayer() {
        Input input=new Input();input.active=true;
        assertFalse(input.ui.prayer(PRAYER,true));assertTrue(input.entries.isEmpty());assertTrue(input.keys.isEmpty());
    }
    @Test public void preparationMovesToVisibleButtonWithoutClickingOrChangingTabs() {
        Input input=new Input();input.prayerOpen();
        final boolean[] moved={false};input.afterMove=()->moved[0]=true;
        assertTrue(input.ui.preparePrayer(PRAYER,()->true));assertTrue(moved[0]);
        assertTrue(input.entries.isEmpty());assertTrue(input.keys.isEmpty());
        input.tab=InterfaceTab.INVENTORY.getVarcIntIndex();moved[0]=false;
        assertFalse(input.ui.preparePrayer(PRAYER,()->true));assertFalse(moved[0]);
        assertTrue(input.keys.isEmpty());
    }
    @Test public void lateSwitchCannotClickAfterMouseTravelConsumesItsDeadline() {
        Input input=new Input();input.prayerOpen();
        input.afterMove=()->input.now+=250;
        assertFalse(input.ui.prayer(Rs2PrayerEnum.PROTECT_MELEE,true,()->input.now<=1180));
        assertTrue(input.entries.isEmpty());
    }
    @Test public void escapeOrUnboundKeysCannotSubstituteForFKeys() {
        Input input=new Input();input.prayerKey=KeyEvent.VK_ESCAPE;
        assertFalse(input.ui.prayer(PRAYER,true));assertTrue(input.keys.isEmpty());
        assertTrue(input.ui.problem().contains("F-key"));
        input.prayerKey=-1;assertFalse(input.ui.prayer(PRAYER,true));assertTrue(input.keys.isEmpty());
    }
    @Test public void inventoryEscapeUsesVisibleTabAndStillRequiresTabAcknowledgement() {
        Input input=new Input();input.prayerOpen();input.inventoryKey=KeyEvent.VK_ESCAPE;
        input.tabButton=new Rectangle(650,200,30,30);
        assertFalse(input.ui.inventory());assertTrue(input.keys.isEmpty());
        assertEquals(1,input.points.size());assertNull(input.entries.get(0));
        assertTrue(input.tabButton.contains(input.points.get(0).getX(),input.points.get(0).getY()));
        assertFalse(input.ui.inventory());assertEquals(1,input.points.size());
        input.tab=InterfaceTab.INVENTORY.getVarcIntIndex();assertTrue(input.ui.inventory());
        assertTrue(input.keys.isEmpty());
    }
    @Test public void unboundInventoryStillCannotEmitAnInventedShortcut() {
        Input input=new Input();input.prayerOpen();input.inventoryKey=-1;
        assertFalse(input.ui.inventory());assertTrue(input.keys.isEmpty());
        assertTrue(input.ui.missingKey());
    }
    @Test public void unboundInventoryCanUseVisibleTabForSupplies() {
        Input input=new Input();input.prayerOpen();input.inventoryKey=-1;
        input.tabButton=new Rectangle(650,200,30,30);
        assertFalse(input.ui.inventory());assertTrue(input.keys.isEmpty());
        assertEquals(1,input.points.size());assertFalse(input.ui.missingKey());
        input.tab=InterfaceTab.INVENTORY.getVarcIntIndex();assertTrue(input.ui.inventory());
    }
    @Test public void failedFKeyFallsBackOnlyAfterAcknowledgementTimeout() {
        Input input=new Input();input.prayerOpen();input.tabButton=new Rectangle(650,200,30,30);
        assertFalse(input.ui.inventory());assertEquals(List.of(KeyEvent.VK_F2),input.keys);
        input.now+=399;assertFalse(input.ui.inventory());assertTrue(input.points.isEmpty());
        input.now+=1;assertFalse(input.ui.inventory());assertEquals(1,input.points.size());
        assertEquals(List.of(KeyEvent.VK_F2),input.keys);
        input.tab=InterfaceTab.INVENTORY.getVarcIntIndex();assertTrue(input.ui.inventory());
    }
    @Test public void fallbackRechecksVisibleTabBoundsAfterMouseTravel() {
        Input input=new Input();input.prayerOpen();input.inventoryKey=-1;
        input.tabButton=new Rectangle(650,200,30,30);input.afterMove=()->input.tabButton=null;
        assertFalse(input.ui.inventory());assertTrue(input.points.isEmpty());assertTrue(input.keys.isEmpty());
    }
    @Test public void fallbackDoesNotCloseTabWhichOpenedDuringMouseTravel() {
        Input input=new Input();input.prayerOpen();input.inventoryKey=-1;
        input.tabButton=new Rectangle(650,200,30,30);
        input.afterMove=()->input.tab=InterfaceTab.INVENTORY.getVarcIntIndex();
        assertFalse(input.ui.inventory());assertTrue(input.points.isEmpty());
        assertTrue(input.ui.inventory());
    }
    @Test public void pendingInventorySwitchBlocksOptionalOffence() {
        Input input=new Input();input.prayerOpen();
        assertFalse(input.ui.inventory());assertEquals(List.of(KeyEvent.VK_F2),input.keys);
        assertFalse(input.ui.prayer(Rs2PrayerEnum.EAGLE_EYE,true));assertTrue(input.entries.isEmpty());
        input.tab=InterfaceTab.INVENTORY.getVarcIntIndex();assertTrue(input.ui.inventory());
        input.ui.inventoryUsed();assertFalse(input.ui.prayer(PRAYER,true));
        assertEquals(List.of(KeyEvent.VK_F2,KeyEvent.VK_F9),input.keys);
        input.prayerOpen();assertTrue(input.ui.prayer(PRAYER,true));
    }
    @Test public void missingProtectionPreemptsSupplyTab() {
        Input input=new Input();input.prayerOpen();assertFalse(input.ui.inventory());
        input.tab=InterfaceTab.INVENTORY.getVarcIntIndex();
        assertFalse(input.ui.prayer(PRAYER,true));
        assertEquals(List.of(KeyEvent.VK_F2,KeyEvent.VK_F9),input.keys);
        assertFalse(input.ui.prayer(PRAYER,true));assertEquals(2,input.keys.size());
        input.prayerOpen();assertTrue(input.ui.prayer(PRAYER,true));
    }
    @Test public void supplyRetryCannotStealUrgentPrayerSwitch() {
        Input input=new Input();assertFalse(input.ui.prayer(PRAYER,true));
        assertFalse(input.ui.inventory());assertEquals(List.of(KeyEvent.VK_F9),input.keys);
        input.prayerOpen();assertTrue(input.ui.prayer(PRAYER,true));
        assertFalse(input.ui.inventory());assertEquals(List.of(KeyEvent.VK_F9,KeyEvent.VK_F2),input.keys);
    }
    @Test public void resetCannotStealAcknowledgedInventoryTab() {
        Input input=new Input();input.active=true;assertTrue(input.ui.inventory());
        assertEquals(FcPrayerUi.Result.WAITING,input.ui.resetPrayer(PRAYER,()->true,()->true));
        assertTrue(input.keys.isEmpty());assertTrue(input.entries.isEmpty());
    }
    @Test public void inventoryWidgetRechecksBoundsAfterTravel() {
        Input input=new Input();input.afterMove=()->input.button=null;
        assertFalse(input.ui.widget(InterfaceTab.INVENTORY,new NewMenuEntry(),()->input.button));
        assertTrue(input.entries.isEmpty());
    }
    @Test public void failedInventorySwitchHasBoundedOwnership() {
        Input input=new Input();input.prayerOpen();assertFalse(input.ui.inventory());
        input.now+=401;assertTrue(input.ui.prayer(PRAYER,true));
    }
    @Test public void retriesTabKeyOnlyAfterAcknowledgementTimeout() {
        Input input=new Input();assertFalse(input.ui.prayer(PRAYER,true));
        input.now+=399;assertFalse(input.ui.prayer(PRAYER,true));assertEquals(1,input.keys.size());
        input.now+=1;assertFalse(input.ui.prayer(PRAYER,true));assertEquals(2,input.keys.size());
    }
    @Test public void rechecksBoundsAfterNaturalMouseTravel() {
        Input input=new Input();input.prayerOpen();input.afterMove=()->input.button=null;
        assertFalse(input.ui.prayer(PRAYER,true));assertTrue(input.entries.isEmpty());
    }
    @Test public void takeoverDuringTravelCancelsClick() {
        Input input=new Input();input.prayerOpen();input.afterMove=()->input.allowed=false;
        assertFalse(input.ui.prayer(PRAYER,true));assertTrue(input.entries.isEmpty());
    }
    @Test public void successfulFlickClicksOffAndOnOnSameVisibleButton() {
        Input input=new Input();input.prayerOpen();input.active=true;
        assertEquals(FcPrayerUi.Result.SENT,input.ui.resetPrayer(PRAYER,()->input.early,()->true));
        assertEquals(2,input.entries.size());assertEquals("Deactivate",input.entries.get(0).getOption());
        assertEquals("Activate",input.entries.get(1).getOption());assertEquals(input.points.get(0),input.points.get(1));
    }
    @Test public void slowTravelSkipsFlickWithoutTurningPrayerOff() {
        Input input=new Input();input.prayerOpen();input.active=true;input.afterMove=()->input.early=false;
        assertEquals(FcPrayerUi.Result.SKIPPED,input.ui.resetPrayer(PRAYER,()->input.early,()->true));
        assertTrue(input.entries.isEmpty());
    }
    @Test public void onHalfRepairsStartedFlickEvenWhenWindowEnds() {
        Input input=new Input();input.prayerOpen();input.active=true;input.afterClick=()->input.early=false;
        assertEquals(FcPrayerUi.Result.SENT,input.ui.resetPrayer(PRAYER,()->input.early,()->true));
        assertEquals(2,input.entries.size());assertEquals("Activate",input.entries.get(1).getOption());
    }
    @Test public void rejectedOffDoesNotSendIsolatedOnToggle() {
        Input input=new Input();input.prayerOpen();input.active=true;input.rejectClick=true;
        assertEquals(FcPrayerUi.Result.WAITING,input.ui.resetPrayer(PRAYER,()->true,()->true));
        assertTrue(input.entries.isEmpty());
    }
    @Test public void hiddenBookCannotFlickEvenWithLoadedPrayerWidget() {
        Input input=new Input();input.active=true;
        assertEquals(FcPrayerUi.Result.WAITING,input.ui.resetPrayer(PRAYER,()->true,()->true));
        assertEquals(List.of(KeyEvent.VK_F9),input.keys);assertTrue(input.entries.isEmpty());
    }
    @Test public void expiredOrInvalidatedRequestDoesNotStartOff() {
        Input input=new Input();input.prayerOpen();input.active=true;
        assertEquals(FcPrayerUi.Result.SKIPPED,input.ui.resetPrayer(PRAYER,()->false,()->true));
        assertEquals(FcPrayerUi.Result.WAITING,input.ui.resetPrayer(PRAYER,()->true,()->false));
        assertTrue(input.entries.isEmpty());
    }
    @Test public void visibleBoundsStayInsideBothWidgetAndCanvas() {
        Rectangle button=new Rectangle(790,590,30,30);
        Rectangle clipped=FcPrayerUi.visibleBounds(button,800,600);assertNotNull(clipped);
        assertTrue(button.contains(clipped));assertTrue(new Rectangle(2,2,796,596).contains(clipped));
        assertNull(FcPrayerUi.visibleBounds(new Rectangle(-40,-40,30,30),800,600));
        assertNull(FcPrayerUi.visibleBounds(new Rectangle(20,20,0,30),800,600));
        assertNull(FcPrayerUi.visibleBounds(null,800,600));
        assertNull(FcPrayerUi.visibleBounds(button,0,0));
    }
}
