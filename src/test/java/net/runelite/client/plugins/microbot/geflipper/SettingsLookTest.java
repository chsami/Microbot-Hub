package net.runelite.client.plugins.microbot.geflipper;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

import static net.runelite.client.plugins.microbot.geflipper.SettingsLook.isActive;
import static org.junit.jupiter.api.Assertions.*;

class SettingsLookTest {
    /** A config answering only the settings named; reading any other one fails the test. */
    private static FlipperConfig config(Object... pairs) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return (FlipperConfig) Proxy.newProxyInstance(FlipperConfig.class.getClassLoader(),
            new Class<?>[]{FlipperConfig.class}, (proxy, method, args) -> {
                if (values.containsKey(method.getName())) return values.get(method.getName());
                throw new AssertionError("Unexpected read of " + method.getName());
            });
    }

    @Test
    void theFatigueClockIsUsedOnlyWithItsPresetIdleMouseAndMouseSpeedAllOn() {
        FlipperConfig.RandomizationPreset fatigue = FlipperConfig.RandomizationPreset.DAY_FATIGUE;
        assertTrue(isActive(config("waitingMouseOffScreen", true, "randomizeMouseSpeed", true,
            "waitingMousePreset", fatigue), "waitingMouseTimeSource", true));
        assertFalse(isActive(config("waitingMouseOffScreen", true, "randomizeMouseSpeed", false,
            "waitingMousePreset", fatigue), "waitingMouseTimeSource", true), "Mouse speed off");
        assertFalse(isActive(config("waitingMouseOffScreen", false, "randomizeMouseSpeed", true,
            "waitingMousePreset", fatigue), "waitingMouseTimeSource", true), "Idle mouse off");
        assertFalse(isActive(config("waitingMouseOffScreen", true, "randomizeMouseSpeed", true,
            "waitingMousePreset", FlipperConfig.RandomizationPreset.CUSTOM), "waitingMouseTimeSource", true));
        assertFalse(isActive(config("waitingMouseOffScreen", true, "randomizeMouseSpeed", true,
            "waitingMousePreset", fatigue, "waitingMouseTimeSource", FlipperConfig.TimeOfDaySource.MORNING),
            "waitingMouseTime", true), "Start time is used only with 'Type a time'");
        assertTrue(isActive(config("waitingMouseOffScreen", true, "randomizeMouseSpeed", true,
            "waitingMousePreset", fatigue, "waitingMouseTimeSource", FlipperConfig.TimeOfDaySource.CUSTOM_TIME),
            "waitingMouseTime", true));
        assertFalse(isActive(config("waitingMouseOffScreen", false), "waitingMouseChance", true));
        assertFalse(isActive(config("waitingMouseOffScreen", false), "waitingMousePreset", true));
    }

    @Test
    void muleSettingsFollowTheAccountsRole() {
        FlipperConfig off = config("muleMode", FlipperConfig.MuleMode.OFF, "muleBeforeUpdate", true);
        FlipperConfig flipper = config("muleMode", FlipperConfig.MuleMode.FLIPPER_SENDER, "muleBeforeUpdate", true);
        FlipperConfig mule = config("muleMode", FlipperConfig.MuleMode.MULE_RECEIVER, "muleBeforeUpdate", true);
        for (String key : new String[]{"muleMode", "muleRsn", "muleThresholdGp", "muleTriggerGp", "muleBeforeUpdate",
            "muleUpdateKeepGp", "muleNowAmount", "muleNow", "muleWorld", "mulePort"}) {
            assertFalse(isActive(off, key, true), key + " with muling off");
            assertTrue(isActive(flipper, key, true), key + " on a Flipper");
        }
        for (String key : new String[]{"muleUpdateKeepGp", "muleNowAmount", "muleNow"}) {
            assertFalse(isActive(mule, key, true), key + " only matters on a Flipper");
        }
        assertFalse(isActive(flipper, "muleNow", false), "Mule now does nothing while GE Flipper is off");
        assertFalse(isActive(config("muleMode", FlipperConfig.MuleMode.FLIPPER_SENDER, "muleBeforeUpdate", false),
            "muleUpdateKeepGp", true), "Keep during update follows its checkbox");
        assertTrue(isActive(mule, "muleWorld", true));
        assertTrue(isActive(mule, "mulePort", true));
        assertFalse(isActive(mule, "muleRsn", true), "Only the Flipper names its Mule");
        assertFalse(isActive(mule, "muleTriggerGp", true));
        assertFalse(isActive(config("muleMode", FlipperConfig.MuleMode.FLIPPER_SENDER, "muleBeforeUpdate", false),
            "muleBeforeUpdate", true));
        // A Mule never runs either update step: it stays ready for the Flipper's last transfer.
        assertFalse(isActive(mule, "muleBeforeUpdate", true));
        assertFalse(isActive(config("muleMode", FlipperConfig.MuleMode.MULE_RECEIVER, "autoFinishOnSystemUpdate", true),
            "autoFinishOnSystemUpdate", true));
        assertTrue(isActive(config("muleMode", FlipperConfig.MuleMode.FLIPPER_SENDER, "autoFinishOnSystemUpdate", true),
            "autoFinishOnSystemUpdate", true));
    }

    @Test
    void switchesAndTheirSettingsAreGreyWhenOff() {
        for (String key : new String[]{"secondScreenAfkMode", "secondScreenAfkMinutes"}) {
            assertFalse(isActive(config("secondScreenAfkMode", FlipperConfig.SecondScreenAfkMode.OFF), key, true));
            assertTrue(isActive(config("secondScreenAfkMode", FlipperConfig.SecondScreenAfkMode.LOGOUT_BETWEEN_CHECKS),
                key, true));
        }
        assertFalse(isActive(config("showOverlay", false), "showOverlay", true));
        assertFalse(isActive(config("verboseLogging", false), "verboseLogging", true));
        assertFalse(isActive(config("blockManualMouseInput", false), "blockManualMouseInput", true));
        assertFalse(isActive(config("autoFinishOnSystemUpdate", false), "autoFinishOnSystemUpdate", true));
        assertFalse(isActive(config(), "finish", false), "Finish does nothing while GE Flipper is off");
        assertTrue(isActive(config(), "finish", true));
    }

    @Test
    void choicesThatAreAlwaysInUseAreNeverGrey() {
        for (String key : new String[]{"selectionMethod", "slotActionMode", "quickStart"}) {
            assertTrue(isActive(config(), key, false), key);
        }
    }

    @Test
    void theHelpIsShortAndKeepsTheCredits() {
        String help = new FlipperConfig() { }.quickStart();
        assertTrue(help.length() <= FlipperConfig.QUICK_START_MAX_CHARS, help.length() + " characters");
        assertTrue(help.contains("First made by chocken."));
        assertTrue(help.contains("afss0") && help.contains("Joinkiee"));
        for (String old : new String[]{"helpStart", "helpStop", "helpSettings", "helpProblems", "helpAbout"}) {
            assertTrue(java.util.Arrays.asList(FlipperConfig.OLD_GUIDE_KEYS).contains(old),
                old + " is cleaned out of saved profiles");
        }
    }

    @Test
    void greyingKeepsAndRestoresTheOwnColour() {
        JLabel label = new JLabel("Detailed logs");
        java.awt.Color own = new java.awt.Color(200, 201, 202);
        label.setForeground(own);
        SettingsLook.style(label, false);
        SettingsLook.style(label, false);
        assertEquals(SettingsLook.DIM, label.getForeground());
        assertEquals(own, SettingsLook.ownForeground(label));
        SettingsLook.style(label, true);
        assertEquals(own, label.getForeground());
        assertNull(label.getClientProperty(SettingsLook.OWN_COLOR));
    }
}
