package net.runelite.client.plugins.microbot.accountstarter.logic;

import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.accountstarter.model.ApPath;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;
import net.runelite.client.plugins.microbot.accountstarter.model.VoucherReward;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

public final class ProgressReader {
    private ProgressReader() {}

    public static Progress read(IntUnaryOperator varbit) {
        Map<ApTask, TaskState> tasks = new EnumMap<>(ApTask.class);
        for (ApTask t : ApTask.values()) {
            tasks.put(t, state(varbit, t.doneVarbit(), t.f2pVarbit()));
        }
        Map<ApPath, TaskState> paths = new EnumMap<>(ApPath.class);
        for (ApPath p : ApPath.values()) {
            paths.put(p, state(varbit, p.doneVarbit(), p.f2pVarbit()));
        }
        Set<VoucherReward> vouchers = EnumSet.noneOf(VoucherReward.class);
        for (VoucherReward v : VoucherReward.values()) {
            if (varbit.applyAsInt(v.varbit()) != 0) {
                vouchers.add(v);
            }
        }
        return new Progress(tasks, paths, vouchers,
                varbit.applyAsInt(ApTask.PARTICIPATING) != 0,
                Arrays.stream(ApTask.ALT_PATH_SELECTORS).anyMatch(id -> varbit.applyAsInt(id) != 0),
                varbit.applyAsInt(VarbitID.IRONMAN) != 0);
    }

    private static TaskState state(IntUnaryOperator varbit, int done, int f2p) {
        if (varbit.applyAsInt(f2p) != 0) return TaskState.CLAIMED;
        return varbit.applyAsInt(done) != 0 ? TaskState.DONE_UNCLAIMED : TaskState.NOT_DONE;
    }
}
