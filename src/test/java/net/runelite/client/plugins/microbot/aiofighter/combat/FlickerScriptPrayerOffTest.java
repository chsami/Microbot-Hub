package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

public final class FlickerScriptPrayerOffTest {

    @Test
    void quickPrayerFlickTurnsOffOnlyTheOrb() {
        try (MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class)) {
            prayer.when(Rs2Prayer::isQuickPrayerEnabled).thenReturn(true);

            FlickerScript.disableFlickPrayers(true);

            prayer.verify(() -> Rs2Prayer.toggleQuickPrayer(false));
            prayer.verify(Rs2Prayer::disableAllPrayers, never());
        }
    }

    @Test
    void quickPrayerFlickWithOrbOffDisablesActivePrayers() {
        try (MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class)) {
            prayer.when(Rs2Prayer::isQuickPrayerEnabled).thenReturn(false);

            FlickerScript.disableFlickPrayers(true);

            prayer.verify(Rs2Prayer::disableAllPrayers);
            prayer.verify(() -> Rs2Prayer.toggleQuickPrayer(false), never());
        }
    }

    @Test
    void protectionPrayerFlickDisablesActivePrayersWithoutTouchingTheOrb() {
        try (MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class)) {
            prayer.when(Rs2Prayer::isQuickPrayerEnabled).thenReturn(true);

            FlickerScript.disableFlickPrayers(false);

            prayer.verify(Rs2Prayer::disableAllPrayers);
            prayer.verify(() -> Rs2Prayer.toggleQuickPrayer(false), never());
        }
    }
}
