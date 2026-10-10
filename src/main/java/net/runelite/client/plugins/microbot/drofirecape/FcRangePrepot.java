/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;

/** One observed action per bank-side pre-dose/refill step. */
final class FcRangePrepot {
    interface Input {
        boolean bankOpen();
        void closeBank();
        void openBank();
        FcActions.ItemResult drink(FcFrame.ItemSlot item);
        boolean stocked(String name);
        void deposit(int id);
        void withdraw(String name);
    }
    private enum Phase { START, DRINK, DOSE, OPEN, DEPOSIT, DEPOSITED, WITHDRAW, FULL, DONE }
    private Phase phase=Phase.START;
    private String full,partial,status="";
    private int fullCount,partialCount,at=-1;
    private boolean failed;
    void reset(){phase=Phase.START;full=partial=null;at=-1;failed=false;status="";}
    String status(){return status;}
    boolean failed(){return failed;}
    private void next(Phase p,int tick){phase=p;at=tick;}
    private static int count(List<FcFrame.ItemSlot> inv,String name){return inv.stream().filter(i->i.name().equalsIgnoreCase(name)).mapToInt(FcFrame.ItemSlot::quantity).sum();}
    boolean step(int tick,int ranged,int base,List<FcFrame.ItemSlot> inv,Input input) {
        if(phase==Phase.DONE)return true;
        if(at>=0&&tick-at>30){failed=true;status="Pre-dose/refill not observed: "+phase;return false;}
        if(phase==Phase.START) {
            FcFrame.ItemSlot dose=inv.stream().filter(i->FcSupplyPolicy.rangedPotion(i.name())&&i.name().endsWith("(4)")).findFirst().orElse(null);
            if(dose==null||ranged>base){phase=Phase.DONE;return true;}
            full=dose.name();partial=full.substring(0,full.length()-2)+"3)";
            fullCount=count(inv,full);partialCount=count(inv,partial);next(Phase.DRINK,tick);
        }
        status="Pre-dose / refill: "+phase;
        switch(phase) {
            case DRINK:
                if(input.bankOpen()){input.closeBank();return false;}
                FcFrame.ItemSlot dose=inv.stream().filter(i->i.name().equalsIgnoreCase(full)).findFirst().orElse(null);
                if(dose!=null&&input.drink(dose)==FcActions.ItemResult.SENT)next(Phase.DOSE,tick);
                return false;
            case DOSE:
                if(count(inv,full)==fullCount-1&&count(inv,partial)==partialCount+1&&ranged>base)next(Phase.OPEN,tick);
                return false;
            case OPEN:
                if(!input.bankOpen()){input.openBank();return false;}
                if(!input.stocked(full)){failed=true;status="Bank needs a "+full+" to replace the pre-dose";return false;}
                next(Phase.DEPOSIT,tick);return false;
            case DEPOSIT:
                if(!input.bankOpen()){input.openBank();return false;}
                FcFrame.ItemSlot used=inv.stream().filter(i->i.name().equalsIgnoreCase(partial)).findFirst().orElse(null);
                if(used!=null){input.deposit(used.id());next(Phase.DEPOSITED,tick);}return false;
            case DEPOSITED:
                if(count(inv,partial)==partialCount)next(Phase.WITHDRAW,tick);return false;
            case WITHDRAW:
                if(!input.bankOpen()){input.openBank();return false;}
                input.withdraw(full);next(Phase.FULL,tick);return false;
            case FULL:
                if(count(inv,full)==fullCount&&count(inv,partial)==partialCount){phase=Phase.DONE;return true;}
                return false;
            default:return false;
        }
    }
}
