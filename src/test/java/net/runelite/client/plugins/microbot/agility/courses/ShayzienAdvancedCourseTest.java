package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ShayzienAdvancedCourseTest {

    private static final int BASE_X = 1464;
    private static final int BASE_Y = 3568;
    private static final int PLANE = 2;
    private static final WorldPoint SECOND_EDGE_LANDING = new WorldPoint(1510, 3620, PLANE);
    private static final WorldPoint BEFORE_SECOND_EDGE = new WorldPoint(1510, 3630, PLANE);
    private static final WorldPoint BEAM_SOUTH_WEST = new WorldPoint(1512, 3619, PLANE);
    private static final WorldPoint BEAM_CENTER = new WorldPoint(1516, 3619, PLANE);
    private static final int BEAM_LENGTH = 9;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private WorldView worldView;
    private GameObject beam;

    @BeforeEach
    void setUp() {
        microbot = mockStatic(Microbot.class);
        player = mockStatic(Rs2Player.class);

        worldView = mock(WorldView.class);
        when(worldView.getBaseX()).thenReturn(BASE_X);
        when(worldView.getBaseY()).thenReturn(BASE_Y);
        when(worldView.getSizeX()).thenReturn(104);
        when(worldView.getSizeY()).thenReturn(104);
        when(worldView.getPlane()).thenReturn(PLANE);

        Client client = mock(Client.class);
        when(client.getTopLevelWorldView()).thenReturn(worldView);
        microbot.when(Microbot::getClient).thenReturn(client);

        LocalPoint beamLocalCenter = localCenter(BEAM_CENTER);
        beam = mock(GameObject.class);
        when(beam.getId()).thenReturn(ObjectID.SHAYZIEN_AGILITY_UP_SWING_JUMP_2);
        when(beam.getPlane()).thenReturn(PLANE);
        when(beam.getWorldView()).thenReturn(worldView);
        when(beam.getSceneMinLocation()).thenReturn(new Point(BEAM_SOUTH_WEST.getX() - BASE_X, BEAM_SOUTH_WEST.getY() - BASE_Y));
        when(beam.sizeX()).thenReturn(BEAM_LENGTH);
        when(beam.sizeY()).thenReturn(1);
        when(beam.getWorldLocation()).thenReturn(BEAM_CENTER);
        when(beam.getLocalLocation()).thenReturn(beamLocalCenter);

        Rs2TileObjectModel beamModel = mock(Rs2TileObjectModel.class);
        when(beamModel.getTileObject()).thenReturn(beam);

        Rs2TileObjectQueryable gameObjects = mock(Rs2TileObjectQueryable.class);
        when(gameObjects.toList()).thenReturn(List.of(beamModel));
        Rs2TileObjectQueryable otherObjects = mock(Rs2TileObjectQueryable.class);
        when(otherObjects.toList()).thenReturn(List.of());
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class, invocation -> {
            if (!invocation.getMethod().getName().equals("ofType")) {
                return invocation.getMock();
            }
            Object[] types = invocation.getArguments();
            return Arrays.equals(types, new Object[]{TileObjectType.GAME}) ? gameObjects : otherObjects;
        });
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
    }

    @AfterEach
    void tearDown() {
        player.close();
        microbot.close();
    }

    @Test
    void measuresBeamDistanceFromItsNearestTile() {
        assertEquals(6, BEAM_CENTER.distanceTo(SECOND_EDGE_LANDING));
        assertEquals(2, AgilityObjects.distanceTo(beam, SECOND_EDGE_LANDING));
    }

    @Test
    void findsSecondGrappleFromSecondEdgeLandingTile() {
        assertSame(beam, courseAt(SECOND_EDGE_LANDING).getCurrentObstacle());
    }

    @Test
    void ignoresSecondGrappleBeforeTheSecondEdgeJump() {
        assertNull(courseAt(BEFORE_SECOND_EDGE).getCurrentObstacle());
    }

    private ShayzienAdvancedCourse courseAt(WorldPoint location) {
        player.when(Rs2Player::getWorldLocation).thenReturn(location);
        return new ShayzienAdvancedCourse() {
            @Override
            public WorldPoint getPlayerWorldLocation() {
                return location;
            }
        };
    }

    private LocalPoint localCenter(WorldPoint point) {
        int half = Perspective.LOCAL_TILE_SIZE / 2;
        return new LocalPoint(
            (point.getX() - BASE_X) * Perspective.LOCAL_TILE_SIZE + half,
            (point.getY() - BASE_Y) * Perspective.LOCAL_TILE_SIZE + half,
            worldView);
    }
}
