package net.runelite.client.plugins.microbot.drofirecape.optional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.*;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;

public class PureRangerSpacingTest {
    private CollisionGrid grid() throws Exception {
        int[][] flags=new int[104][104];
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
            "pure-ranger-contact-collision.csv"),StandardCharsets.UTF_8))) {
            reader.readLine();String line;
            while((line=reader.readLine())!=null){String[] r=line.split(",");flags[Integer.parseInt(r[0])][Integer.parseInt(r[1])]=Integer.parseInt(r[2]);}
        }
        return new CollisionGrid(flags);
    }
    private Mob ranger(){return new Mob(55886,Kind.RANGER,new Tile(37,36),3,10,10,713,Protection.MELEE,true);}
    private Snapshot frame(int tick,CollisionGrid grid,Tile player) {
        return new Snapshot(tick,player,grid,List.of(ranger(),
            new Mob(55888,Kind.BAT,new Tile(49,29),1,10,10,-1,Protection.MELEE,true),
            new Mob(55889,Kind.BAT,new Tile(47,31),1,10,10,-1,Protection.MELEE,true),
            new Mob(56474,Kind.BABY,new Tile(48,37),1,10,10,-1,Protection.MELEE,true)),
            74,true,5,Protection.NONE).atWave(12);
    }
    @Test public void recordedPunchScenarioMovesToCommittedNonContactFiringTile() throws Exception {
        CollisionGrid grid=grid();Snapshot source=frame(713,grid,new Tile(41,37));
        PureRangerSpacing controller=new PureRangerSpacing();Plan first=controller.decide(source);
        assertNotNull(first);assertTrue(CombatPlanner.actionable(first,false));
        assertTrue(first.nextStep().distance(source.player())>=3);
        assertTrue(ranger().distance(first.destination())>=3);
        assertTrue(CombatPlanner.playerCanAttack(source,first.destination(),ranger()));
        Plan repeated=controller.decide(frame(714,grid,source.player()));
        assertEquals(first.destination(),repeated.destination());
        assertNull(controller.decide(frame(715,grid,first.destination())));
    }
    @Test public void distantRangerDoesNotTriggerKiting() throws Exception {
        assertNull(new PureRangerSpacing().decide(frame(713,grid(),new Tile(43,37))));
    }
    @Test public void pureFiringApproachKeepsBufferBeforeArriving() throws Exception {
        Snapshot source=frame(713,grid(),new Tile(54,36));
        Plan approach=PureCombatPolicy.approach(source,ranger(),new Tile(54,36),"test approach");
        assertNotNull(approach);assertTrue(ranger().distance(approach.destination())>=3);
    }
}
