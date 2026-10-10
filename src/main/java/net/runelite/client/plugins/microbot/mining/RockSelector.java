package net.runelite.client.plugins.microbot.mining;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

final class RockSelector {
    private RockSelector() {
    }

    static <T> T nearestReachable(Collection<T> candidates, ToIntFunction<T> distance, Predicate<T> reachable) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(distance))
                .filter(reachable)
                .findFirst()
                .orElse(null);
    }
}
