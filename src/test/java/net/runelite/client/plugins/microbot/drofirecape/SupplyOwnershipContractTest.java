/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.Protection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SupplyOwnershipContractTest {
    static class Config implements DroFirecapeConfig {
        @Override public int eatPercent(){return 60;}
        @Override public boolean usePurpleSweets(){return false;}
        @Override public boolean rangingPotion(){return true;}
    }
    static class Actions extends FcActions {
        int drinks;
        @Override ItemResult itemStep(FcFrame.ItemSlot item,String action){drinks++;return ItemResult.SENT;}
    }
    static class Owner extends FcTickPrayers {
        boolean protectedNow;
        Owner(){super(new FirecapeTestClient().client,false,s->{},new AtomicLong()::get);}
        @Override boolean ownsInput(){return true;}
        @Override boolean protectionReady(){return protectedNow;}
        @Override boolean optionalInputWindow(int tick,long budget){return false;}
    }
    @Test void urgentSupplyRetainsReadinessGateAndDoesNotRequireNonurgentWindow()throws Exception {
        for(boolean pure:List.of(false,true)) {
            DroFirecapeScript s=pure?new OptionalFirecapeScript():new DroFirecapeScript();Actions a=new Actions();Owner owner=new Owner();
            FirecapeTestClient.set(s,"actions",a);FirecapeTestClient.set(s,"tickPrayers",owner);FirecapeTestClient.set(s,"config",new Config());
            FcFrame f=FirecapeTestClient.frame(100,3024);FirecapeTestClient.set(f,"hp",20);FirecapeTestClient.set(f,"cave",true);
            FirecapeTestClient.set(f,"inventory",List.of(new FcFrame.ItemSlot(0,6685,1,"Saradomin brew(4)",List.of("Drink"))));FirecapeTestClient.set(s,"frame",f);
            assertTrue(s.supplies(f,Protection.NONE));assertEquals(0,a.drinks);
            owner.protectedNow=true;assertTrue(s.supplies(f,Protection.NONE));assertEquals(1,a.drinks);
            FirecapeTestClient.set(s,"lastSupplyAt",0L);assertTrue(s.supplies(f,Protection.NONE));assertEquals(1,a.drinks);
        }
    }
    @Test void optionalBoostCannotBorrowClosedInventoryWindow()throws Exception {
        for(boolean pure:List.of(false,true)) {
            DroFirecapeScript s=pure?new OptionalFirecapeScript():new DroFirecapeScript();Actions a=new Actions();Owner owner=new Owner();owner.protectedNow=true;
            FirecapeTestClient.set(s,"actions",a);FirecapeTestClient.set(s,"tickPrayers",owner);FirecapeTestClient.set(s,"config",new Config());
            FcFrame f=FirecapeTestClient.frame(100,3024);FirecapeTestClient.set(f,"cave",true);FirecapeTestClient.set(f,"ranged",90);
            FirecapeTestClient.set(f,"inventory",List.of(new FcFrame.ItemSlot(0,2444,1,"Ranging potion(4)",List.of("Drink"))));FirecapeTestClient.set(s,"frame",f);
            assertFalse(s.supplies(f,Protection.NONE));assertEquals(0,a.drinks);
        }
    }
}
