package net.runelite.client.plugins.microbot.drorecorder;

import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DroRecorderColosseumModifiersTest {
    @Test void everyModifierVarbitIsNamedAndSnapshotsReadTheGameValues(){
        assertEquals("solarflare",DroRecorderColosseumModifiers.name(VarbitID.COLOSSEUM_MODIFIER_SOLARFLARE_STACKS_CLIENT));
        assertEquals("doom",DroRecorderColosseumModifiers.name(VarbitID.COLOSSEUM_MODIFIER_DOOM_STACKS_CLIENT));
        assertEquals("doomPoints",DroRecorderColosseumModifiers.name(VarbitID.COLOSSEUM_DOOM_STACKS_CLIENT));
        assertNull(DroRecorderColosseumModifiers.name(VarbitID.COLOSSEUM_GLORIA_MET));
        Map<String,Integer> snapshot=DroRecorderColosseumModifiers.snapshot(id->id==VarbitID.COLOSSEUM_MODIFIER_SOLARFLARE_STACKS_CLIENT?3:0);
        assertEquals(List.of("selected","blasphemy","toxicity","reentry","myopia","frailty","solarflare","relentless",
            "volatility","mantimayhem","doom","doomPoints"),List.copyOf(snapshot.keySet()));
        assertEquals(3,snapshot.get("solarflare"));assertTrue(DroRecorderColosseumModifiers.any(snapshot));
        assertFalse(DroRecorderColosseumModifiers.any(DroRecorderColosseumModifiers.snapshot(id->0)),"Inferno and the lobby have no modifiers");
    }
    @Test void onlyTheGamesWaveLineStartsAWaveSnapshot(){
        assertEquals(10,DroRecorderColosseumModifiers.wave("Wave: 10"));
        assertEquals(1,DroRecorderColosseumModifiers.wave(" Wave: 1 "));
        for(String other:new String[]{null,"","Wave 9 completed! Wave duration: 3:57.00","Wave: x","Next Wave: 3"})
            assertEquals(-1,DroRecorderColosseumModifiers.wave(other),String.valueOf(other));
    }
}
