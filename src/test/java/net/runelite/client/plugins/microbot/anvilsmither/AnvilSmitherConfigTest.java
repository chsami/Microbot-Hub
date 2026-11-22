package net.runelite.client.plugins.microbot.anvilsmither;

import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilSmitherConfigTest {
    @Test
    void theConfigPanelReadsTheWayItWasDesigned() {
        ConfigInformation info = AnvilSmitherConfig.class.getAnnotation(ConfigInformation.class);
        assertNotNull(info);
        assertTrue(info.value().contains("<b>You need</b>"));
        assertTrue(info.value().contains("Smithing level"));
        assertTrue(info.value().contains("Pandemonium"));
        // dragon keel parts are made at the Dragon Forge, never at an anvil
        assertFalse(info.value().toLowerCase().contains("dragon"));
        assertTrue(info.value().contains("hammer"));
        assertTrue(info.value().contains("Song of the Elves"));

        List<String> sections = Arrays.stream(AnvilSmitherConfig.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ConfigSection.class))
                .sorted(Comparator.comparingInt((Field f) -> f.getAnnotation(ConfigSection.class).position()))
                .map(f -> f.getAnnotation(ConfigSection.class).name()).collect(Collectors.toList());
        assertEquals(List.of("Setup", "Overlay"), sections);

        List<ConfigItem> items = Arrays.stream(AnvilSmitherConfig.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(ConfigItem.class)).filter(a -> a != null).collect(Collectors.toList());
        // saved settings: a renamed key silently resets that setting for every user
        assertEquals(Set.of("location", "barType", "smithObject", "alch", "logout", "debug", "infoPanel"),
                items.stream().map(ConfigItem::keyName).collect(Collectors.toSet()));
        assertEquals("AnvilSmither", AnvilSmitherConfig.GROUP);
        for (ConfigItem i : items) {
            assertFalse(i.description().isBlank(), i.keyName());
            assertTrue(i.description().endsWith("."), i.keyName());
        }
    }
}
