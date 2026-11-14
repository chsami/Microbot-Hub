package net.runelite.client.plugins.microbot.droefficientwalker;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigItem;

/** Coalesce transport-option edits until the existing journey can be safely replanned. */
final class WalkerRouteSettings {
    private static final Set<String> ROUTE_KEYS = Arrays.stream(EfficientWalkerConfig.class.getMethods())
            .map(method -> method.getAnnotation(ConfigItem.class))
            .filter(item -> item != null && (item.section().equals(EfficientWalkerConfig.routeModificationSection)
                    || item.section().equals(EfficientWalkerConfig.spiritTreeTeleportsSection)
                    || item.section().equals(EfficientWalkerConfig.wildernessSection)
                    && (item.keyName().equals("allowWildernessTravel") || item.keyName().startsWith("use"))))
            .map(ConfigItem::keyName).collect(Collectors.toUnmodifiableSet());
    private final AtomicBoolean pending = new AtomicBoolean();

    static boolean affectsRoute(String group, String key) {
        return EfficientWalkerConfig.GROUP.equals(group) && key != null && ROUTE_KEYS.contains(key);
    }
    void changed() { pending.set(true); }
    boolean pending() { return pending.get(); }
    void clear() { pending.set(false); }
    boolean consume() { return pending.getAndSet(false); }
}
