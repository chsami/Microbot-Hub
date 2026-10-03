package net.runelite.client.plugins.microbot.drokbd;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DroKbdAntibanSnapshotTest
{
    @Test void renamedClientFieldsReturnNoSnapshotWithoutChangingSettings()
    {
        boolean override = Rs2AntibanSettings.overwriteScriptSettings;
        Activity activity = Rs2Antiban.getActivity();
        PlayStyle style = Rs2Antiban.getPlayStyle();
        assertNull(DroKbdAntibanSnapshot.capture(ClientWithRenamedFields.class));
        assertEquals(override, Rs2AntibanSettings.overwriteScriptSettings);
        assertSame(activity, Rs2Antiban.getActivity());
        assertSame(style, Rs2Antiban.getPlayStyle());
    }

    private static final class ClientWithRenamedFields {}

    @Test void restoresEveryGlobalSettingAndPreviousActivity() throws Exception
    {
        DroKbdAntibanSnapshot original = DroKbdAntibanSnapshot.capture();
        try
        {
            Rs2AntibanSettings.overwriteScriptSettings = false;
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);
            Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
            Rs2AntibanSettings.moveMouseRandomlyChance = 0.73;
            Map<Field, Object> before = new LinkedHashMap<>();
            for (Field field : Rs2AntibanSettings.class.getFields())
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))
                    before.put(field, field.get(null));
            Activity activity = Rs2Antiban.getActivity();
            ActivityIntensity intensity = Rs2Antiban.getActivityIntensity();
            PlayStyle style = Rs2Antiban.getPlayStyle();
            double frequency = style.frequency;
            DroKbdAntibanSnapshot snapshot = DroKbdAntibanSnapshot.capture();
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
            Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
            style.frequency = 99;
            snapshot.restore();
            for (Map.Entry<Field, Object> entry : before.entrySet())
                assertEquals(entry.getValue(), entry.getKey().get(null), entry.getKey().getName());
            assertSame(activity, Rs2Antiban.getActivity());
            assertSame(intensity, Rs2Antiban.getActivityIntensity());
            assertSame(style, Rs2Antiban.getPlayStyle());
            assertEquals(frequency, style.frequency);
            snapshot.restore(); // repeated cleanup is safe
        }
        finally { original.restore(); }
    }

    @Test void restoresUnsetActivityWithoutSetterSideEffects()
    {
        DroKbdAntibanSnapshot original = DroKbdAntibanSnapshot.capture();
        try
        {
            Rs2Antiban.resetAntibanSettings(true);
            DroKbdAntibanSnapshot snapshot = DroKbdAntibanSnapshot.capture();
            Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
            snapshot.restore();
            assertNull(Rs2Antiban.getActivity());
            assertNull(Rs2Antiban.getCategory());
            assertNull(Rs2Antiban.getPlayStyle());
        }
        finally { original.restore(); }
    }
}
