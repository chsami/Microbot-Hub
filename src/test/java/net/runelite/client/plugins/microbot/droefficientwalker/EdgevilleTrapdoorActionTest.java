package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Method;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.droefficientwalker.compat.SceneObjects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EdgevilleTrapdoorActionTest {
    @Test public void closedTrapdoorOpensBeforeClimbAndOpenStateUsesClimb() throws Exception {
        var edge = SlayerDungeonTransports.create().stream().filter(EdgevilleDungeonAccess::isTrapdoor)
            .findFirst().orElseThrow();
        TileObject object = mock(TileObject.class);
        Method action = WalkerTestReflection.declaredMethod(NetworkTransportSupport.class, "catalogObjectAction",
            net.runelite.client.plugins.microbot.shortestpath.Transport.class, TileObject.class);
        action.setAccessible(true);
        try (var objects = mockStatic(SceneObjects.class)) {
            objects.when(() -> SceneObjects.hasAction(object, "Open", true)).thenReturn(true);
            assertEquals("Open", action.invoke(null, edge, object));
            objects.when(() -> SceneObjects.hasAction(object, "Open", true)).thenReturn(false);
            objects.when(() -> SceneObjects.hasAction(object, "Climb-down", true)).thenReturn(true);
            assertEquals("Climb-down", action.invoke(null, edge, object));
        }
    }
}
