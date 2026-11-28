package net.runelite.client.plugins.microbot.accountstarter.logic;

import lombok.Value;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.accountstarter.model.VoucherReward;

import java.util.Map;
import java.util.Set;

@Value
public class Progress {
    Map<ApTask, TaskState> tasks;
    Map<ApPath, TaskState> paths;
    Set<VoucherReward> voucherClaimed;
    boolean participating;
    boolean altPaths;
    boolean ironman;

    public TaskState of(ApTask task) {
        return tasks.getOrDefault(task, TaskState.NOT_DONE);
    }
}
