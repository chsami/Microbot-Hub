package net.runelite.client.plugins.microbot.mining.data;

import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MiningRockLocationsMembersTest {

    private static final WorldPoint RIMMINGTON = new WorldPoint(2975, 3262, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private Client client;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);
        client = mock(Client.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::isLoggedIn).thenReturn(true);
        player.when(Rs2Player::getWorldLocation).thenReturn(RIMMINGTON);
    }

    @AfterEach
    void tearDown() {
        player.close();
        microbot.close();
    }

    @Test
    void membersOnlyLocationIsNotAccessibleOnFreeWorld() {
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));

        assertFalse(new LocationOption(RIMMINGTON, "members mine", true).hasRequirements());
        assertTrue(new LocationOption(RIMMINGTON, "free mine", false).hasRequirements());
    }

    @Test
    void membersOnlyLocationIsAccessibleOnMembersWorld() {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));

        assertTrue(new LocationOption(RIMMINGTON, "members mine", true).hasRequirements());
    }

    @Test
    void bestIronLocationFromRimmingtonOnFreeWorldIsFreeToPlay() {
        when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));

        LocationOption best = MiningRockLocations.getBestAccessibleLocation(Rocks.IRON);

        assertFalse(best.isMembersOnly());
        assertTrue(MiningRockLocations.getAccessibleLocationsForRock(Rocks.IRON).stream().noneMatch(LocationOption::isMembersOnly));
    }

    @Test
    void bestIronLocationFromRimmingtonOnMembersWorldStaysNearest() {
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));

        LocationOption best = MiningRockLocations.getBestAccessibleLocation(Rocks.IRON);

        assertEquals("Ardougne South East Mine", best.getName());
    }
}
