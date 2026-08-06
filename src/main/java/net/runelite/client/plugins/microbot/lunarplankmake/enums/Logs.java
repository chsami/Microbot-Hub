package net.runelite.client.plugins.microbot.lunarplankmake.enums;

import net.runelite.api.gameval.ItemID;

public enum Logs {
    LOGS("Logs", "Plank", ItemID.LOGS, ItemID.WOODPLANK, 70),
    OAK_LOGS("Oak logs", "Oak plank", ItemID.OAK_LOGS, ItemID.PLANK_OAK, 175),
    TEAK_LOGS("Teak logs", "Teak plank", ItemID.TEAK_LOGS, ItemID.PLANK_TEAK, 350),
    MAHOGANY_LOGS("Mahogany logs", "Mahogany plank", ItemID.MAHOGANY_LOGS, ItemID.PLANK_MAHOGANY, 1050),
    CAMPHOR_LOGS("Camphor logs", "Camphor plank", ItemID.CAMPHOR_LOGS, ItemID.PLANK_CAMPHOR, 1750),
    IRONWOOD_LOGS("Ironwood logs", "Ironwood plank", ItemID.IRONWOOD_LOGS, ItemID.PLANK_IRONWOOD, 3500),
    ROSEWOOD_LOGS("Rosewood logs", "Rosewood plank", ItemID.ROSEWOOD_LOGS, ItemID.PLANK_ROSEWOOD, 5250);

    private final String name;
    private final String finished;
    private final int logItemId;
    private final int plankItemId;
    private final int plankMakeCoinFee;

    Logs(String name, String finished, int logItemId, int plankItemId, int plankMakeCoinFee) {
        this.name = name;
        this.finished = finished;
        this.logItemId = logItemId;
        this.plankItemId = plankItemId;
        this.plankMakeCoinFee = plankMakeCoinFee;
    }

    public String getName() {
        return name;
    }

    public String getFinished() {
        return finished;
    }

    public int getLogItemId() {
        return logItemId;
    }

    public int getPlankItemId() {
        return plankItemId;
    }

    /**
     * Coins removed per log by Plank Make (70% of sawmill fee); see OSRS wiki Plank Make.
     */
    public int getPlankMakeCoinFee() {
        return plankMakeCoinFee;
    }

    @Override
    public String toString() {
        return getName();
    }
}
