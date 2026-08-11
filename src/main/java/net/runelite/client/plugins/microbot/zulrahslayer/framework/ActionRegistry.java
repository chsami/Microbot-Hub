package net.runelite.client.plugins.microbot.zulrahslayer.framework;

import com.google.common.reflect.ClassPath;
import com.google.inject.Injector;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Discovers action implementations by scanning the package of the given interface, so a new action
 * is picked up automatically just by adding the class. Each is built through Guice
 * ({@link Injector#getInstance}) so actions may declare their own {@code @Inject} dependencies.
 * Ordering is left to {@link ActionRunner}, which sorts on {@link Action#order()}.
 */
@Slf4j
public final class ActionRegistry {

    private ActionRegistry() {
    }

    /**
     * Returns one instance of every concrete implementation of {@code type} found in {@code type}'s
     * package. The result is unsorted; wrap it in an {@link ActionRunner} to order by priority.
     */
    public static <T> List<T> discover(Class<T> type, Injector injector) {
        String packageName = type.getPackage().getName();
        List<T> instances = new ArrayList<>();
        try {
            ClassPath classPath = ClassPath.from(type.getClassLoader());
            for (ClassPath.ClassInfo info : classPath.getTopLevelClasses(packageName)) {
                Class<?> clazz;
                try {
                    clazz = info.load();
                } catch (Throwable t) {
                    log.warn("Skipping unloadable class {}", info.getName(), t);
                    continue;
                }
                if (type.isAssignableFrom(clazz)
                        && !clazz.isInterface()
                        && !Modifier.isAbstract(clazz.getModifiers())) {
                    instances.add(type.cast(injector.getInstance(clazz)));
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan package {} for {} implementations", packageName, type.getSimpleName(), e);
        }

        if (instances.isEmpty()) {
            log.error("No {} implementations discovered in {} — the action pipeline will be EMPTY!",
                    type.getSimpleName(), packageName);
        } else {
            log.info("Discovered {} {}(s): {}", instances.size(), type.getSimpleName(),
                    instances.stream()
                            .map(a -> a.getClass().getSimpleName())
                            .sorted()
                            .collect(Collectors.joining(", ")));
        }
        return instances;
    }
}
