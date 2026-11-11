package net.runelite.client.plugins.microbot.droslayer;

import java.util.EnumSet;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DroSlayerHopPolicyTest {
    private World world(boolean members) {
        World world = mock(World.class);
        when(world.getId()).thenReturn(441);
        when(world.getPlayers()).thenReturn(100);
        when(world.getTypes()).thenReturn(members ? EnumSet.of(WorldType.MEMBERS) : EnumSet.noneOf(WorldType.class));
        return world;
    }
    @Test public void normalWorldsMustMatchMembershipAndDifferFromCurrentWorld() {
        assertTrue(DroSlayerHopPolicy.eligible(world(true), true, 337));
        assertTrue(DroSlayerHopPolicy.eligible(world(false), false, 337));
        assertFalse(DroSlayerHopPolicy.eligible(world(true), false, 337));
        assertFalse(DroSlayerHopPolicy.eligible(world(false), true, 337));
        assertFalse(DroSlayerHopPolicy.eligible(world(true), true, 441));
        assertFalse(DroSlayerHopPolicy.eligible(null, true, 337));
    }
    @Test public void everySpecialWorldTypeIsExcludedIncludingPvpAndLevelGates() {
        for (WorldType type : WorldType.values()) {
            if (type == WorldType.MEMBERS) continue;
            World world = world(true);
            when(world.getTypes()).thenReturn(EnumSet.of(WorldType.MEMBERS, type));
            assertFalse(DroSlayerHopPolicy.eligible(world, true, 337), type.name());
        }
    }
    @Test public void fullAndOfflineWorldsAreNotSelected() {
        World world = world(true);
        when(world.getPlayers()).thenReturn(-1);
        assertFalse(DroSlayerHopPolicy.eligible(world, true, 337));
        when(world.getPlayers()).thenReturn(1900);
        assertFalse(DroSlayerHopPolicy.eligible(world, true, 337));
        when(world.getPlayers()).thenReturn(1899);
        assertTrue(DroSlayerHopPolicy.eligible(world, true, 337));
    }
}
