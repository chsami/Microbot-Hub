package net.runelite.client.plugins.microbot.accountstarter;

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

class AccountStarterConfigTest {
    @Test
    void theConfigPanelReadsTheWayItWasDesigned() throws Exception {
        ConfigInformation info = AccountStarterConfig.class.getAnnotation(ConfigInformation.class);
        assertNotNull(info);
        assertTrue(info.value().contains("Adventure Paths"));
        assertTrue(info.value().contains("ironman"));

        List<Field> sections = Arrays.stream(AccountStarterConfig.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ConfigSection.class))
                .sorted(Comparator.comparingInt(f -> f.getAnnotation(ConfigSection.class).position()))
                .collect(Collectors.toList());
        assertEquals(List.of("Setup", "Choose skills", "Overlay"),
                sections.stream().map(f -> f.getAnnotation(ConfigSection.class).name()).collect(Collectors.toList()));

        List<ConfigItem> items = Arrays.stream(AccountStarterConfig.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(ConfigItem.class)).filter(a -> a != null).collect(Collectors.toList());
        // saved settings: a renamed key silently resets that setting for every user
        assertEquals(Set.of("preset", "pacing", "buryBones", "cookFood", "lampSkill", "allowOptIn", "doMudfoot", "questOnly", "questLampSkill", "logOutWhenDone", "recoverWhenStuck",
                        "attackTarget", "strengthTarget", "defenceTarget", "rangedTarget", "magicTarget",
                        "fishingTarget", "woodcuttingTarget", "miningTarget",
                        "buyAtVarrockShops", "pickUpArrows", "coinBudget", "coinReserve",
                        "deathBudget", "useDeathsOffice", "showPanel", "compactPanel"),
                items.stream().map(ConfigItem::keyName).collect(Collectors.toSet()));
        String setup = (String) sections.get(0).get(null);
        ConfigItem lastSetup = items.stream().filter(i -> i.section().equals(setup))
                .max(Comparator.comparingInt(ConfigItem::position)).orElseThrow();
        assertEquals("recoverWhenStuck", lastSetup.keyName());
        assertEquals("Recover when stuck", lastSetup.name());
        for (ConfigItem i : items) {
            assertFalse(i.description().isBlank(), i.keyName());
            assertTrue(i.description().endsWith("."), i.keyName());
        }
    }
}
