package net.runelite.client.plugins.microbot.accountstarter.logic;

import lombok.Data;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;

@Data
public final class PlannerMemory {
    private Skill lastSkill;
    private ApTask currentTask;
    private long blockEndsAtMs;
}
