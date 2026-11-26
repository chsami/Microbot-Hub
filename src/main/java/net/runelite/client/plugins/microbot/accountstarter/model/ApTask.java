package net.runelite.client.plugins.microbot.accountstarter.model;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Completed/F2P/P2P varbits are always consecutive. */
public enum ApTask {
    ATTACK_5(ApPath.COMBAT, Skill.ATTACK, 5, 10174, 1, new RewardItem(1295, 1)),
    ATTACK_10(ApPath.COMBAT, Skill.ATTACK, 10, 10177, 2, new RewardItem(1297, 1)),
    ATTACK_20(ApPath.COMBAT, Skill.ATTACK, 20, 10180, 3, new RewardItem(1299, 1)),
    STRENGTH_5(ApPath.COMBAT, Skill.STRENGTH, 5, 10183, 4, new RewardItem(333, 5), new RewardItem(1061, 1), new RewardItem(1059, 1)),
    STRENGTH_10(ApPath.COMBAT, Skill.STRENGTH, 10, 10186, 5, new RewardItem(333, 5), new RewardItem(113, 1)),
    STRENGTH_20(ApPath.COMBAT, Skill.STRENGTH, 20, 10189, 6, new RewardItem(329, 20), new RewardItem(113, 2)),
    DEFENCE_5(ApPath.COMBAT, Skill.DEFENCE, 5, 10192, 7, new RewardItem(1141, 1)),
    DEFENCE_10(ApPath.COMBAT, Skill.DEFENCE, 10, 10195, 8, new RewardItem(1105, 1)),
    DEFENCE_20(ApPath.COMBAT, Skill.DEFENCE, 20, 10198, 9, new RewardItem(1109, 1)),
    RANGED_5(ApPath.COMBAT, Skill.RANGED, 5, 10201, 10, new RewardItem(1167, 1), new RewardItem(882, 75)),
    RANGED_10(ApPath.COMBAT, Skill.RANGED, 10, 10204, 11, new RewardItem(843, 1), new RewardItem(882, 275)),
    RANGED_20(ApPath.COMBAT, Skill.RANGED, 20, 10207, 12, new RewardItem(849, 1), new RewardItem(884, 75), new RewardItem(1169, 1)),
    MAGIC_5(ApPath.COMBAT, Skill.MAGIC, 5, 10210, 13, new RewardItem(558, 50), new RewardItem(555, 75), new RewardItem(1381, 1)),
    MAGIC_10(ApPath.COMBAT, Skill.MAGIC, 10, 10213, 14, new RewardItem(558, 250), new RewardItem(556, 250), new RewardItem(1385, 1)),
    MAGIC_20(ApPath.COMBAT, Skill.MAGIC, 20, 10216, 15, new RewardItem(558, 300), new RewardItem(556, 300), new RewardItem(1387, 1)),
    FISHING_5(ApPath.GATHERER, Skill.FISHING, 5, 10219, 1, new RewardItem(307, 1), new RewardItem(313, 100)),
    WOODCUTTING_5(ApPath.GATHERER, Skill.WOODCUTTING, 5, 10222, 2, new RewardItem(1349, 1), new RewardItem(1511, 20)),
    MINING_5(ApPath.GATHERER, Skill.MINING, 5, 10225, 3, new RewardItem(1267, 1), new RewardItem(438, 20), new RewardItem(436, 20));

    public static final int JON_NPC_ID = 9244;
    public static final WorldPoint JON_TILE = new WorldPoint(3232, 3235, 0);
    public static final int PARTICIPATING = 9340;
    public static final int[] ALT_PATH_SELECTORS = {10517, 10518, 10519};
    public static final int QUEST_LAMP_ID = 34751;
    public static final int LAMP_ID = ItemID.ADVENTUREPATH_GATHERER_LAMP;
    public static final int VOUCHER_ID = ItemID.ADVENTUREPATH_COMBAT_VOUCHER;
    public static final int STARTER_KIT_ID = ItemID.ADVENTUREPATH_COMBAT_KIT;

    private final ApPath path;
    private final Skill skill;
    private final int target;
    private final int doneVarbit;
    private final int focusIndex;
    private final List<RewardItem> f2pRewards;

    ApTask(ApPath path, Skill skill, int target, int doneVarbit, int focusIndex, RewardItem... f2pRewards) {
        this.path = path;
        this.skill = skill;
        this.target = target;
        this.doneVarbit = doneVarbit;
        this.focusIndex = focusIndex;
        this.f2pRewards = Collections.unmodifiableList(Arrays.asList(f2pRewards));
    }

    public ApPath path() { return path; }
    public Skill skill() { return skill; }
    public int target() { return target; }
    public int doneVarbit() { return doneVarbit; }
    public int f2pVarbit() { return doneVarbit + 1; }
    public int p2pVarbit() { return doneVarbit + 2; }
    public int focusIndex() { return focusIndex; }
    public List<RewardItem> f2pRewards() { return f2pRewards; }

    public static List<ApTask> of(Skill skill) {
        return Arrays.stream(values()).filter(t -> t.skill == skill).collect(Collectors.toList());
    }

    public static List<ApTask> of(ApPath path) {
        return Arrays.stream(values()).filter(t -> t.path == path).collect(Collectors.toList());
    }
}
