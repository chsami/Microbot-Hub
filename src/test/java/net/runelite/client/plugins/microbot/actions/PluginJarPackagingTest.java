package net.runelite.client.plugins.microbot.actions;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

        Set<String> entries = new LinkedHashSet<>();
        try (JarFile jar = new JarFile(pluginJar)) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                entries.add(e.nextElement().getName());
            }
        }

        for (String actionClass : EXPECTED_ACTION_CLASSES) {
            String path = ACTIONS_PACKAGE_PREFIX + actionClass;
            assertTrue(entries.contains(path),
                    () -> pluginJar.getName() + " should bundle shared framework class " + path);
        }

        assertTrue(entries.stream().noneMatch(name -> name.endsWith("shared-sources.txt")),
                "the shared-sources.txt marker file must be excluded from the JAR");
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
