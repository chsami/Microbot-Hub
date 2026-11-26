package net.runelite.client.plugins.microbot.irkedmlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigSection;
import org.junit.jupiter.api.Test;

/** The config opens with a brief that names the requirements, Setup comes first and Overlay last. */
class ConfigLayoutTest {

    @Test
    void theBriefNamesTheLevelsNeeded() {
        String brief = IrkedMLMConfig.class.getAnnotation(ConfigInformation.class).value();
        assertTrue(brief.contains("30 Mining"), brief);
        assertTrue(brief.contains("57 Mining"), brief);
        assertTrue(brief.contains("You need"), brief);
        assertTrue(brief.contains("Recommended"), brief);
    }

    @Test
    void setupIsFirstAndOverlayIsLast() {
        List<String> order = Arrays.stream(IrkedMLMConfig.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ConfigSection.class))
                .sorted(Comparator.comparingInt((Field f) -> f.getAnnotation(ConfigSection.class).position()))
                .map(f -> f.getAnnotation(ConfigSection.class).name())
                .collect(Collectors.toList());
        assertEquals("Setup", order.get(0));
        assertEquals("Overlay", order.get(order.size() - 1));
    }
}
