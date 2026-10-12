package net.runelite.client.plugins.microbot.drozulrah;

import java.lang.reflect.Field;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DroZulrahProfileConfigTest
{
    private Object setting(BaseProfileDro.Settings settings, String name) throws Exception {
        Field field = BaseProfileDro.Settings.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(settings);
    }

    @Test public void defaultsPreserveTheEntireExistingBreakPolicy() throws Exception {
        BaseProfileDro.Settings defaults = new BaseProfileDro.Settings();
        BaseProfileDro.Settings actual = DroZulrahScript.profileSettings(new DroZulrahConfig() {});
        for (String name : new String[]{"customBreaksEnabled", "minBreakIntervalMinutes", "maxBreakIntervalMinutes",
                "logoutBreakChance", "afkBreakMinMinutes", "afkBreakMaxMinutes", "logoutBreakMinMinutes",
                "logoutBreakMaxMinutes", "postLoginSettleSeconds"})
            assertEquals(setting(defaults, name), setting(actual, name), name);
    }

    @Test public void allConfigValuesReachTheBreakHandler() throws Exception {
        DroZulrahConfig config = new DroZulrahConfig() {
            @Override public boolean smartBreaks(){ return false; }
            @Override public int minBreakIntervalMinutes(){ return 55; }
            @Override public int maxBreakIntervalMinutes(){ return 88; }
            @Override public int logoutBreakChance(){ return 33; }
            @Override public int afkBreakMinMinutes(){ return 4; }
            @Override public int afkBreakMaxMinutes(){ return 9; }
            @Override public int logoutBreakMinMinutes(){ return 12; }
            @Override public int logoutBreakMaxMinutes(){ return 22; }
            @Override public int postLoginSettleSeconds(){ return 11; }
        };
        BaseProfileDro.Settings actual = DroZulrahScript.profileSettings(config);
        assertEquals(false, setting(actual, "customBreaksEnabled"));
        assertEquals(55, setting(actual, "minBreakIntervalMinutes"));
        assertEquals(88, setting(actual, "maxBreakIntervalMinutes"));
        assertEquals(33, setting(actual, "logoutBreakChance"));
        assertEquals(4, setting(actual, "afkBreakMinMinutes"));
        assertEquals(9, setting(actual, "afkBreakMaxMinutes"));
        assertEquals(12, setting(actual, "logoutBreakMinMinutes"));
        assertEquals(22, setting(actual, "logoutBreakMaxMinutes"));
        assertEquals(11, setting(actual, "postLoginSettleSeconds"));
    }

    @Test public void invertedRangesKeepBaseProfileClamping() throws Exception {
        BaseProfileDro.Settings actual = DroZulrahScript.profileSettings(new DroZulrahConfig() {
            @Override public int minBreakIntervalMinutes(){ return 50; }
            @Override public int maxBreakIntervalMinutes(){ return 10; }
            @Override public int afkBreakMinMinutes(){ return 8; }
            @Override public int afkBreakMaxMinutes(){ return 2; }
            @Override public int logoutBreakMinMinutes(){ return 12; }
            @Override public int logoutBreakMaxMinutes(){ return 5; }
        });
        assertEquals(50, setting(actual, "maxBreakIntervalMinutes"));
        assertEquals(8, setting(actual, "afkBreakMaxMinutes"));
        assertEquals(12, setting(actual, "logoutBreakMaxMinutes"));
    }

    @Test public void breakChoicesCannotEnableParkingOrSlowMouse() throws Exception {
        BaseProfileDro.Settings actual = DroZulrahScript.profileSettings(new DroZulrahConfig() {
            @Override public int logoutBreakChance(){ return 0; }
        });
        assertEquals(BaseProfileDro.MouseActivity.ACTIVE, actual.getMouseActivity());
        assertEquals(BaseProfileDro.AfkParkSide.NONE, actual.getParkSide());
        assertEquals(ActivityIntensity.HIGH, setting(actual, "activityIntensity"));
        assertEquals(0, setting(actual, "logoutBreakChance"));
        assertFalse(new BaseProfileDro(actual).forceParkCompletelyOffScreen());
    }
    @Test public void allNineControlsAreDiscoverableInExpandedSmartBreakSection() throws Exception {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (java.lang.reflect.Method method : DroZulrahConfig.class.getMethods()) {
            net.runelite.client.config.ConfigItem item = method.getAnnotation(net.runelite.client.config.ConfigItem.class);
            if (item != null && item.section().equals(DroZulrahConfig.breakSection)) keys.add(item.keyName());
        }
        assertEquals(9,keys.size());
        assertTrue(keys.contains("smartBreaks"));assertTrue(keys.contains("minBreakIntervalMinutes"));
        assertTrue(keys.contains("maxBreakIntervalMinutes"));assertTrue(keys.contains("postLoginSettleSeconds"));
        assertFalse(DroZulrahConfig.class.getField("breakSection")
                .getAnnotation(net.runelite.client.config.ConfigSection.class).closedByDefault());
    }
}
