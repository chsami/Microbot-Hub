package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Map;

/** Local, sanitized diagnostic fields. Never construct this from raw server JSON. */
public final class DroRecorderDiagnostic {
    private final Map<String,Object> values;
    public DroRecorderDiagnostic(Map<String,Object> values){this.values=Map.copyOf(values);}
    public Map<String,Object> values(){return values;}

    private static volatile boolean fullRateMouse;
    /** While an arena server session is connected, every mouse move is stored instead of one per sample interval. */
    public static void fullRateMouse(boolean on){fullRateMouse=on;}
    public static boolean fullRateMouse(){return fullRateMouse;}
}
