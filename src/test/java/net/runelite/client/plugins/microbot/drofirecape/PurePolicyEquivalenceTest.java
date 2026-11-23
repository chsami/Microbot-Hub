/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PurePolicyEquivalenceTest {
    private static final Map<String,String[]> GOLDEN=new LinkedHashMap<>();
    static {
        GOLDEN.put("main-027",new String[]{"eb4e39e9f0bf30ab","acbe9ae197f7b2a6","cb4cb4b7b27e1a65","b7dd82ca2309ba13"});
        GOLDEN.put("main-029",new String[]{"cbbd7e90d6e7f2dd","1b09671d6de4c084","88ccfab3716a1de6","13cb6688227e4143"});
        GOLDEN.put("pure-057",new String[]{"b59369ac21965481","6ad4e4a0f7aae8d6","807db0bbbdbf9ad6","f3330c5e85f9dc9c"});
        GOLDEN.put("pure-066",new String[]{"119f467c252db67b","379038f8c49b2436","ac53030b2ab80d61","ff4ff66084268ad8"});
    }
    private static final Protection[] PRAYERS={Protection.NONE,Protection.MELEE,Protection.RANGE,Protection.MAGIC};

    private static List<Snapshot> scenes(String run)throws Exception{return new RetainedRunRegressionTest().scenes(run);}
    private static Snapshot at(Snapshot s,Tile player){return new Snapshot(s.tick(),player,s.grid(),s.mobs(),s.runEnergy(),s.running(),s.weaponRange(),s.jadStyle(),s.meleeMode()).atWave(s.wave());}
    private static String run(Supplier<Object> action){try{return String.valueOf(action.get());}catch(RuntimeException e){return "!"+e.getClass().getSimpleName();}}
    private static List<Snapshot> variants(Snapshot s) {
        List<Snapshot> out=new ArrayList<>();out.add(s);
        for(int dx=-2;dx<=2;dx+=2)for(int dy=-2;dy<=2;dy+=2) {
            Tile t=s.player().add(dx,dy);
            if((dx!=0||dy!=0)&&s.grid().open(t)&&s.mobs().stream().noneMatch(m->m.occupies(t)))out.add(at(s,t));
        }
        return out;
    }
    private static String digest(String text)throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(int i=0;i<8;i++)hex.append(String.format("%02x",hash[i]));return hex.toString();
    }

    static String plannerTrace(String run,boolean pure)throws Exception {
        StringBuilder out=new StringBuilder();
        CombatPlanner sequential=pure?new PureCombatPlanner():new CombatPlanner();
        for(Snapshot frame:scenes(run)) {
            out.append(run(()->sequential.plan(frame,null))).append('\n');
            for(Snapshot s:variants(frame)) {
                CombatPlanner p=pure?new PureCombatPlanner():new CombatPlanner();
                out.append(s.player()).append(run(()->p.plan(s,null))).append(run(()->p.plan(s,s.player().add(4,0))))
                    .append(run(()->p.hold(s))).append(run(()->p.chooseCamp(s))).append(run(()->p.route(s,s.player().add(-3,2))))
                    .append(run(()->p.firingRoute(s,s.player().add(2,-2)))).append('\n');
                for(Mob m:s.mobs())for(Protection prayer:PRAYERS)
                    out.append(run(()->p.attackAllowed(s,m,prayer,false))).append(run(()->p.attackAllowed(s,m,prayer,true)));
                out.append('\n');
                p.recover(s);out.append(run(()->p.plan(s,null))).append('\n');
            }
        }
        return out.toString();
    }

    private static Plan decide(LureController l,CombatPlanner p,Snapshot s,int target,Tile[] a) {
        return l.decide(s,p,target,List.of(a[0],a[1]),0,a[0],a[1],a[2],a[3],a[4]);
    }
    static String lureTrace(String run,boolean pure)throws Exception {
        StringBuilder out=new StringBuilder();
        Tile home=new Tile(54,36);
        List<Tile[]> anchors=List.of(
            new Tile[]{home,home.add(8,17),null,home.add(-2,0),home.add(-1,-5)},
            new Tile[]{RecordedLureBook.ITALY,RecordedLureBook.PULL,RecordedLureBook.NORTHWEST,RecordedLureBook.WEST_PEEK,RecordedLureBook.MELEE_WALL});
        for(Tile[] a:anchors) {
            LureController sequential=pure?new PureLureController():new LureController();
            CombatPlanner sequentialPlanner=pure?new PureCombatPlanner():new CombatPlanner();
            for(Snapshot frame:scenes(run)) {
                int first=frame.mobs().isEmpty()?-1:frame.mobs().get(0).index();
                out.append(run(()->decide(sequential,sequentialPlanner,frame,first,a))).append(run(()->decide(sequential,sequentialPlanner,frame,first,a)))
                    .append(run(()->sequential.hasPendingReturn())).append('\n');
                for(Snapshot s:variants(frame)) {
                    Tile[] local={a[0]==home?s.player():a[0],a[1],a[2],a[3],a[4]};
                    for(Tile[] use:new Tile[][]{a,local})for(int target:new int[]{-1,first}) {
                        LureController l=pure?new PureLureController():new LureController();
                        CombatPlanner p=pure?new PureCombatPlanner():new CombatPlanner();
                        out.append(run(()->decide(l,p,s,target,use))).append(run(()->decide(l,p,s,target,use)))
                            .append(run(()->l.hasPendingReturn()));
                        l.movementFailed();out.append(run(()->decide(l,p,s,target,use)));
                        l.recover();out.append(run(()->l.recoverRecorded(s,use[0],use[3]))).append(run(()->l.finishReturn(s)))
                            .append(run(()->l.recoverBlockedRoute(s,use[0])));
                        l.rebase(1,-1);out.append(run(()->decide(l,p,s,target,use)));
                        l.reset();l.finishMeleeTrap();out.append(run(()->decide(l,p,s,target,use)));
                        if(pure) {
                            PureLureController pl=(PureLureController)l;pl.reset();
                            for(Mob m:s.mobs())out.append(run(()->pl.allowsImmediateShot(s,m)))
                                .append(run(()->pl.allowsImmediateShot(s,m,p.hold(s))));
                            out.append(run(()->decide(pl,p,s,target,use)));pl.rangerSpacingAccepted();
                            for(Mob m:s.mobs())out.append(run(()->pl.allowsImmediateShot(s,m)));
                            out.append(run(()->decide(pl,p,s,target,use)));
                        }
                        out.append('\n');
                    }
                }
            }
        }
        return out.toString();
    }

    @Test void pureAndRegularDecisionsMatchPreRefactorControllersOnEveryFixture()throws Exception {
        for(Map.Entry<String,String[]> golden:GOLDEN.entrySet()) {
            String run=golden.getKey();String[] expected=golden.getValue();
            String regularPlanner=plannerTrace(run,false),purePlanner=plannerTrace(run,true);
            assertEquals(expected[0],digest(regularPlanner),"CombatPlanner decisions changed on "+run);
            assertEquals(expected[1],digest(purePlanner),"PureCombatPlanner decisions changed on "+run);
            assertNotEquals(regularPlanner,purePlanner,"Pure planner hooks must alter decisions on "+run);
            String regularLure=lureTrace(run,false),pureLure=lureTrace(run,true);
            assertEquals(expected[2],digest(regularLure),"LureController decisions changed on "+run);
            assertEquals(expected[3],digest(pureLure),"PureLureController decisions changed on "+run);
            assertNotEquals(regularLure,pureLure,"Pure lure hooks must alter decisions on "+run);
        }
    }
}
