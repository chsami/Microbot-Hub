package net.runelite.client.plugins.microbot.actions;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Final JAR packaging: because the Zulrah plugin opts into the shared {@code microbot/actions/**}
 * package via its {@code shared-sources.txt}, the built plugin JAR must BUNDLE those framework classes
 * (they live outside the plugin's own directory and the client does not provide them, so a missing
 * bundle would be a runtime {@code ClassNotFoundException}). The marker file itself must be excluded.
 *
 * <p>This needs a built artifact, so it runs against {@code build/libs} when present and self-skips
 * otherwise (e.g. a bare {@code ./gradlew test}). Build it first with:
 * {@code ./gradlew build -PpluginList=ZulrahSlayerPlugin} to exercise this assertion.
 */
class PluginJarPackagingTest {

    private static final String PLUGIN_MARKER =
            "net/runelite/client/plugins/microbot/zulrahslayer/ZulrahSlayerPlugin.class";
    private static final String ACTIONS_PACKAGE_PREFIX =
            "net/runelite/client/plugins/microbot/actions/";
    private static final String ZULRAH_ACTIONS_PREFIX =
            "net/runelite/client/plugins/microbot/zulrahslayer/actions/";

    private static final String[] EXPECTED_ACTION_CLASSES = {
            "Action.class",
            "ActionRegistry.class",
            "ActionRunner.class",
            "ActionScript.class",
            "ActionState.class",
            "ScriptState.class",
    };

    @Test
    void pluginJarBundlesTheSharedActionsFrameworkAndNotTheMarkerFile() throws IOException {
        File pluginJar = findZulrahPluginJar();
        assumeTrue(pluginJar != null,
                "no built Zulrah plugin JAR under build/libs — run `./gradlew build -PpluginList=ZulrahSlayerPlugin` first");

        List<String> entries = new ArrayList<>();
        try (JarFile jar = new JarFile(pluginJar)) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                entries.add(e.nextElement().getName());
            }
        }

        for (String actionClass : EXPECTED_ACTION_CLASSES) {
            String path = ACTIONS_PACKAGE_PREFIX + actionClass;
            long count = entries.stream().filter(path::equals).count();
            // Present, and present EXACTLY ONCE — a second copy would mean the class got bundled twice
            // (e.g. overlapping shared-source globs), which risks split-package / duplicate-class oddities.
            assertEquals(1L, count,
                    () -> pluginJar.getName() + " should bundle shared framework class " + path + " exactly once");
        }

        assertFalse(entries.stream().anyMatch(name -> name.endsWith("shared-sources.txt")),
                "the shared-sources.txt marker file must be excluded from the JAR");
    }

    @Test
    void everyZulrahActionClassInTheJarLoadsAndImplementsTheSharedActionInterface() throws Exception {
        File pluginJar = findZulrahPluginJar();
        assumeTrue(pluginJar != null,
                "no built Zulrah plugin JAR under build/libs — run `./gradlew build -PpluginList=ZulrahSlayerPlugin` first");

        // The shared ZulrahAction interface (extends the framework Action) — loaded from the test
        // classpath, which is byte-identical to the jar's copy since both come from the same compile.
        Class<?> zulrahActionType =
                Class.forName("net.runelite.client.plugins.microbot.zulrahslayer.actions.ZulrahAction");

        List<String> actionClassNames = new ArrayList<>();
        try (JarFile jar = new JarFile(pluginJar)) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                String name = e.nextElement().getName();
                // Concrete action classes shipped in the plugin: zulrahslayer/actions/*Action.class,
                // excluding the ZulrahAction interface itself and any nested ($) classes.
                if (name.startsWith(ZULRAH_ACTIONS_PREFIX) && name.endsWith("Action.class")
                        && !name.endsWith("/ZulrahAction.class") && !name.contains("$")) {
                    actionClassNames.add(name
                            .substring(0, name.length() - ".class".length())
                            .replace('/', '.'));
                }
            }
        }

        assertFalse(actionClassNames.isEmpty(), "the plugin JAR should ship a set of Zulrah action classes");

        int instantiated = 0;
        for (String className : actionClassNames) {
            Class<?> actionClass = Class.forName(className);
            assertTrue(zulrahActionType.isAssignableFrom(actionClass),
                    className + " should implement the shared ZulrahAction interface");
            assertFalse(actionClass.isInterface() || Modifier.isAbstract(actionClass.getModifiers()),
                    className + " should be a concrete action");
            // Instantiate the ones with a no-arg constructor to prove the packaged action set is
            // constructible; DI-only actions (with @Inject constructors) are exercised live by the client.
            if (hasNoArgConstructor(actionClass)) {
                Object instance = actionClass.getDeclaredConstructor().newInstance();
                assertTrue(zulrahActionType.isInstance(instance));
                instantiated++;
            }
        }
        assertTrue(instantiated > 0, "at least one packaged Zulrah action should be directly instantiable");
    }

    private static boolean hasNoArgConstructor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * The per-plugin release artifact under build/libs, or null if not built. We match the plugin's own
     * {@code <PluginClass>-<version>.jar} by name so we don't accidentally pick the aggregate
     * {@code Microbot-Hub[-all].jar} application shadow jars — those bundle every plugin's resources
     * (including the marker file) and are not what the Hub distributes per plugin.
     */
    private static File findZulrahPluginJar() throws IOException {
        File libs = new File("build/libs");
        File[] jars = libs.listFiles((dir, name) ->
                name.startsWith("ZulrahSlayerPlugin") && name.endsWith(".jar"));
        if (jars == null) {
            return null;
        }
        for (File jar : jars) {
            try (JarFile jf = new JarFile(jar)) {
                if (jf.getEntry(PLUGIN_MARKER) != null) {
                    return jar;
                }
            }
        }
        return null;
    }
}
