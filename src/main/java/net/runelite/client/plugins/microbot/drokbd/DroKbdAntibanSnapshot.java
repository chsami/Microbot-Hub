package net.runelite.client.plugins.microbot.drokbd;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Saves the in-memory profile; it does not write or reload the user's saved config. */
final class DroKbdAntibanSnapshot
{
    private final List<SavedField> fields = new ArrayList<>();

    static DroKbdAntibanSnapshot capture()
    {
        return capture(Rs2Antiban.class);
    }

    /** A missing or inaccessible client field must not prevent plugin startup. */
    static DroKbdAntibanSnapshot capture(Class<?> antibanClass)
    {
        DroKbdAntibanSnapshot snapshot = new DroKbdAntibanSnapshot();
        try
        {
            for (Field field : Rs2AntibanSettings.class.getFields())
            {
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))
                    snapshot.save(field, null);
            }
            // Client 2.6.25 has no public snapshot API. Direct field restoration also
            // preserves a null activity and avoids setActivity's play-style side effects.
            for (String name : new String[]{"activity", "activityIntensity", "category", "playStyle"})
                snapshot.save(antibanClass.getDeclaredField(name), null);
            // Activity selection mutates these enum instances, not just the style reference.
            for (PlayStyle style : PlayStyle.values())
            {
                for (Field field : PlayStyle.class.getDeclaredFields())
                {
                    if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()))
                        snapshot.save(field, style);
                }
            }
            return snapshot;
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return null;
        }
    }

    private void save(Field field, Object target) throws IllegalAccessException
    {
        field.setAccessible(true);
        fields.add(new SavedField(field, target, field.get(target)));
    }

    void restore()
    {
        try
        {
            for (SavedField saved : fields) saved.field.set(saved.target, saved.value);
        }
        catch (IllegalAccessException e)
        {
            throw new IllegalStateException("Cannot restore the global antiban profile", e);
        }
    }

    private static final class SavedField
    {
        private final Field field;
        private final Object target;
        private final Object value;

        private SavedField(Field field, Object target, Object value)
        {
            this.field = field;
            this.target = target;
            this.value = value;
        }
    }
}
