package net.runelite.client.plugins.microbot.drocolosseumprayers;

import java.util.Map;

public final class ArenaDiagnostic {
    private final Map<String,Object> values;
    public ArenaDiagnostic(Map<String,Object> values){this.values=Map.copyOf(values);}
    public Map<String,Object> values(){return values;}

    private static volatile boolean fullRateMouse;

    public static void fullRateMouse(boolean on){fullRateMouse=on;}
    public static boolean fullRateMouse(){return fullRateMouse;}
}
