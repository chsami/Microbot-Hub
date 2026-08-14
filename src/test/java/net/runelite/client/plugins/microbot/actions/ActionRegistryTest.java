package net.runelite.client.plugins.microbot.actions;

import com.google.inject.Guice;
import com.google.inject.Injector;
import net.runelite.client.plugins.microbot.actions.fixtures.AbstractTestAction;
import net.runelite.client.plugins.microbot.actions.fixtures.ActionAlpha;
import net.runelite.client.plugins.microbot.actions.fixtures.ActionBeta;
import net.runelite.client.plugins.microbot.actions.fixtures.ActionGamma;
import net.runelite.client.plugins.microbot.actions.fixtures.TestAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Discovery: {@link ActionRegistry#discover} finds every CONCRETE implementation in the scanned
 * package, skips the interface and abstract bases, and builds each instance through Guice.
 */
class ActionRegistryTest {

    @Test
    void discoversEveryConcreteActionInThePackage() {
        Injector injector = Guice.createInjector();

        List<TestAction> discovered = ActionRegistry.discover(TestAction.class, injector);

        Set<Class<?>> classes = discovered.stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        // The three concrete fixtures, and nothing else, are discovered.
        assertEquals(
                Set.of(ActionAlpha.class, ActionBeta.class, ActionGamma.class),
                classes,
                "discovery should return exactly the concrete TestAction implementations in the package");
    }

    @Test
    void skipsTheInterfaceAndAbstractBases() {
        Injector injector = Guice.createInjector();

        Set<Class<?>> classes = ActionRegistry.discover(TestAction.class, injector).stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        assertFalse(classes.contains(AbstractTestAction.class), "abstract implementors must be skipped");
        assertFalse(classes.contains(TestAction.class), "the interface itself must be skipped");
    }

    @Test
    void buildsInstancesThroughGuice() {
        Injector injector = Guice.createInjector();

        List<TestAction> discovered = ActionRegistry.discover(TestAction.class, injector);

        assertFalse(discovered.isEmpty(), "expected at least one discovered action");
        assertTrue(discovered.stream().allMatch(a -> a instanceof TestAction),
                "every discovered instance should be a usable TestAction built by the injector");
    }
}
