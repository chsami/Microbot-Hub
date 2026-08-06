package net.runelite.client.plugins.microbot.leaguesfiremaking;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum LogType {
    LOGS("Logs", ItemID.LOGS, 1),
    OAK("Oak logs", ItemID.OAK_LOGS, 15),
    WILLOW("Willow logs", ItemID.WILLOW_LOGS, 30),
    TEAK("Teak logs", ItemID.TEAK_LOGS, 35),
    MAPLE("Maple logs", ItemID.MAPLE_LOGS, 45),
    MAHOGANY("Mahogany logs", ItemID.MAHOGANY_LOGS, 50),
    YEW("Yew logs", ItemID.YEW_LOGS, 60),
    MAGIC("Magic logs", ItemID.MAGIC_LOGS, 75),
    REDWOOD("Redwood logs", ItemID.REDWOOD_LOGS, 90);

    private final String logName;
    private final int itemId;
    private final int levelRequired;

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.FIREMAKING, levelRequired);
    }

    public static LogType getBestForLevel() {
        LogType best = LOGS;
        for (LogType log : values()) {
            if (log.hasRequiredLevel()) {
                best = log;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return logName;
    }
}
