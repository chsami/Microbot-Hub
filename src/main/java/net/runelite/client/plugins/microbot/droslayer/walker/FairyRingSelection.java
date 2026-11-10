package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

/** Confirm observed dial state before issuing a fairy-ring teleport, for every destination. */
public final class FairyRingSelection
{
    private static final Map<Object, FairyRingSelection> STATES = new WeakHashMap<>();
    private String code;
    private int observed = -1, stableSince = -1, lastClick = -100, attempts;
    private boolean confirmed;

    public static synchronized int next(Object helper, Client client, Object edge)
    {
        try {
            Field destinations = helper.getClass().getDeclaredField("codesByDestination"); destinations.setAccessible(true);
            Field destination = edge.getClass().getDeclaredField("destination"); destination.setAccessible(true);
            String code = (String) ((Map<?, ?>) destinations.get(helper)).get((WorldPoint) destination.get(edge));
            int[] values = {client.getVarbitValue(3985), client.getVarbitValue(3986), client.getVarbitValue(3987)};
            int child = FairyRingNetwork.getNextDialWidgetChild(code, values);
            Widget widget = child < 0 ? null : client.getWidget(398, child);
            return STATES.computeIfAbsent(helper, ignored -> new FairyRingSelection())
                .step(code, values, client.getTickCount(), FairyRingNetwork.isFirstWidgetActionEnabled(client, widget));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot inspect fairy-ring destination", ex);
        }
    }
    public static synchronized void reset(Object helper) { STATES.remove(helper); }

    int step(String target, int[] values, int tick, boolean clickable)
    {
        int child = FairyRingNetwork.getNextDialWidgetChild(target, values);
        if (child < 0) return -1;
        int packed = values[0] * 16 + values[1] * 4 + values[2];
        if (!java.util.Objects.equals(code, target)) {
            code = target; observed = -1; lastClick = -100; confirmed = false;
        }
        if (observed != packed) { observed = packed; stableSince = tick; attempts = 0; }
        if (!clickable) return 0;
        // A dial animation can expose intermediate values. Require two ticks of the
        // requested code before confirming; never re-send confirmation while awaiting landing.
        if (confirmed) return tick - lastClick >= 16 ? -1 : 0;
        if (child == 26 && tick - stableSince < 2) return 0;
        if (tick - lastClick < 4) return 0;
        if (attempts >= 3) return -1;
        lastClick = tick; attempts++;
        if (child == 26) confirmed = true;
        return child;
    }
}
