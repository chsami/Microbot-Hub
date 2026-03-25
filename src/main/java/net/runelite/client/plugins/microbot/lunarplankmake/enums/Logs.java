package net.runelite.client.plugins.microbot.lunarplankmake.enums;

public enum Logs {
    LOGS("Logs", "Plank"),
    OAK_LOGS("Oak logs", "Oak plank"),
    TEAK_LOGS("Teak logs", "Teak plank"),
    MAHOGANY_LOGS("Mahogany logs", "Mahogany plank"),
    CAMPHOR_LOGS("Camphor logs", "Camphor plank"),
    IRONWOOD_LOGS("Ironwood logs", "Ironwood plank"),
    ROSEWOOD_LOGS("Rosewood logs", "Rosewood plank");

    private final String name;
    private final String finished;

    Logs(String name, String finished) {
        this.name = name;
        this.finished = finished;
    }

    public String getName() {
        return name;
    }

    public String getFinished() {
        return finished;
    }

    @Override
    public String toString() {
        return getName();
    }
}
