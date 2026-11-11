package net.runelite.client.plugins.microbot.droslayer;

import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import net.runelite.client.plugins.slayer.Task;

/** Native startup owns supplies, configured task variants and cannon ownership. */
public final class DroSlayerStartupLocation {
    private DroSlayerStartupLocation() {}

    public static boolean atCurrentTask(DroSlayerConfig config) {
        if (config == null || Microbot.getClientThread() == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (!Microbot.isLoggedIn() || Rs2Slayer.getSlayerTaskSize() <= 0) return false;
            WorldPoint here = Rs2Player.getWorldLocation();
            String name = DroSlayerWildernessTaskReader.getTaskName();
            Task task = name == null ? null : Task.getTask(name);
            if (here == null || task == null) return false;
            boolean wilderness = config.slayerMaster() == DroSlayerMaster.KRYSTILIA;
            if (wilderness != DroSlayerWildernessTaskReader.isKrystiliaAssignment()) return false;
            if (wilderness) {
                var assignment = DroSlayerWildernessTasks.forChaosDruidPreference(
                    DroSlayerWildernessTasks.forAssignedTask(name), config.wildernessElderChaosDruids());
                if (assignment == null || !assignment.isSupported()) return false;
                // Use the selected native assignment, not another task's nearby NPCs.
                return nearTask(here, assignment.getDestination(), config.attackRadius());
            }
            // Ordinary task variants can be configured independently. Native startup resolves
            // that choice; only a live assigned target proves this is a task area at all.
            var cache = Microbot.getRs2NpcCache();
            return cache != null && cache.query().within(here, Math.max(1, config.attackRadius()))
                .where(npc -> !npc.isDead() && Arrays.stream(task.getTargetNames())
                    .anyMatch(target -> DroSlayerTargetNames.sameVariant(target, npc.getName())))
                .first() != null;
        }).orElse(false);
    }

    static boolean nearTask(WorldPoint here, WorldPoint target, int radius) {
        return here != null && target != null && here.getPlane() == target.getPlane()
            && here.distanceTo2D(target) <= Math.max(1, radius);
    }
}
