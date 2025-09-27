package net.runelite.client.plugins.microbot.amethystminer.requirements;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.amethystminer.AmethystMiningConfig;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

import java.util.List;

/**
 * Enhanced implementation showing how to use ItemRequirementCollection for a mining plugin.
 * Demonstrates the new standardized approach to equipment, outfit requirements, and location requirements.
 * 
 * Now includes dynamic location requirements based on the selected ore type with quest and skill requirements.
 */
@Slf4j
public class MiningPrePostScheduleRequirements extends PrePostScheduleRequirements {
    final AmethystMiningConfig amethystMiningConfig;

    public MiningPrePostScheduleRequirements(AmethystMiningConfig config) {
        super("Mining", "Mining", false);
        this.amethystMiningConfig = config;
        //TODO Set location pre-schedule requirements - near mining spots for amethyst

        
        initializeRequirements();
    }
    

    @Override
    protected boolean initializeRequirements() {
        this.getRegistry().clear();
        // Register complete outfit and equipment collections using ItemRequirementCollection
        //set location post-schedule requirements - go to grand exchange after mining
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE,true,-1, TaskContext.POST_SCHEDULE, RequirementPriority.MANDATORY));
        // Mining pickaxes - progression-based from bronze to crystal
        ItemRequirementCollection.registerPickAxes(this,RequirementPriority.MANDATORY, TaskContext.PRE_SCHEDULE);
        
        // Prospector/Motherlode Mine outfit - provides XP bonus for mining (includes all variants)
        // we must ensure we equip the varrock armour if available ?  becasue of the bonus or is the motherlode outfit also providing the same bonus? ->  check wiki
        // TODO: Update ItemRequirementCollection.registerProspectorOutfit to accept TaskContext
         ItemRequirementCollection.registerProspectorOutfit(this, RequirementPriority.RECOMMENDED,8, TaskContext.PRE_SCHEDULE, false, false, false, false);
        
        // Varrock diary armour - provides benefits like chance of smelting ore while mining
        // TODO: Update ItemRequirementCollection.registerVarrockDiaryArmour to accept TaskContext
        ItemRequirementCollection.registerVarrockDiaryArmour(this, RequirementPriority.RECOMMENDED, TaskContext.PRE_SCHEDULE);
        
        return this.isInitialized();
    }

}
