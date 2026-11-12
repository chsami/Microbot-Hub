package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationEngineIsolationTest {
    @Test
    void independentWalkersOwnTheirPlannerAdmissionAndConfigurationStatics() throws Exception {
        ClassLoader first = engineLoader();
        ClassLoader second = engineLoader();
        String name = "net.runelite.client.plugins.microbot.droefficientwalker.engine.CollisionRoutePlanner";
        Class<?> firstPlanner = Class.forName(name, true, first);
        Class<?> secondPlanner = Class.forName(name, true, second);
        assertSame(first, firstPlanner.getClassLoader());
        assertSame(second, secondPlanner.getClassLoader());
        assertNotSame(firstPlanner, secondPlanner);
        Field firstOrigin = firstPlanner.getDeclaredField("aioTravelOrigin");
        Field secondOrigin = secondPlanner.getDeclaredField("aioTravelOrigin");
        firstOrigin.setAccessible(true);
        secondOrigin.setAccessible(true);
        WorldPoint origin = new WorldPoint(3165, 3487, 0);
        firstOrigin.set(null, origin);
        assertEquals(origin, firstOrigin.get(null));
        assertNull(secondOrigin.get(null));
        Field firstPermission = firstPlanner.getField("allowWildernessTravel");
        Field secondPermission = secondPlanner.getField("allowWildernessTravel");
        firstPermission.setAccessible(true);
        secondPermission.setAccessible(true);
        firstPermission.setBoolean(null, true);
        assertFalse(secondPermission.getBoolean(null));
    }

    private ClassLoader engineLoader() throws Exception {
        Class<?> type = Class.forName(EfficientWalker.class.getName() + "$NavigationEngineClassLoader");
        Constructor<?> constructor = type.getDeclaredConstructor(ClassLoader.class);
        constructor.setAccessible(true);
        return (ClassLoader) constructor.newInstance(getClass().getClassLoader());
    }
}
