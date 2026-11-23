package net.runelite.client.plugins.microbot.glassmake;

public class GabulhasGlassMakeInfo {
    public static states botStatus = states.Starting;

    public static String statusText() {
        states status = botStatus;
        return (status == null ? states.Starting : status).toString().replace("_", " ");
    }

    public enum states {
        Starting,
        Banking,
        Glassblowing,
        Picking,

    }

    public enum items {
        Seaweed,
        GiantSeaweed
    }

    public static boolean hasMaterials(items item, int seaweedCount, int sandCount) {
        if (item == items.GiantSeaweed) {
            return seaweedCount >= 3 && sandCount >= 18;
        }
        return seaweedCount >= 3 && sandCount >= 13;
    }
}
