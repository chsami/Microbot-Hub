package net.runelite.client.plugins.microbot.butterflycatcher;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ButterflyCatcherScript extends Script {

    private final ButterflyCatcherPlugin plugin;

	@Inject
	public ButterflyCatcherScript(ButterflyCatcherPlugin plugin) {
		this.plugin = plugin;
	}

    public boolean run() {
        Microbot.enableAutoRunOn = true;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.microBreakDurationLow = 0;
        Rs2AntibanSettings.microBreakDurationHigh = 3;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);

        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                catchButterfly();
            } catch (Exception ex) {
                log.trace("Exception in main loop: ", ex);
            }
        }, 0, 666, TimeUnit.MILLISECONDS);
        return true;
    }

    private void catchButterfly(){
        if (BreakHandlerScript.isBreakActive()) return;


        if (Rs2AntibanSettings.actionCooldownActive) {
            Rs2Antiban.actionCooldown();
            return;
        }

        if(Microbot.getClient().getLocalPlayer().isInteracting()){
            return;
        }

        if(Rs2Inventory.contains(ItemID.HUNTING_BUTTERFLY_NET) && !Rs2Equipment.isWearing(ItemID.HUNTING_BUTTERFLY_NET,
                ItemID.II_MAGIC_BUTTERFLY_NET,
                ItemID.II_MAGIC_BUTTERFLY_NET_DUMMY)){
            Rs2Inventory.equip(ItemID.HUNTING_BUTTERFLY_NET);
            return;
        }

        int hunterLvl = Rs2Player.getBoostedSkillLevel(Skill.HUNTER);
        List<Integer> butterflies = Arrays.stream(Butterfly.values())
                .filter(b -> {
                    if (hunterLvl >= b.getLevelRequired() + 10) {
                        // high enough level, net not required
                        return true;
                    }
                    // otherwise: need both level and net
                    return hunterLvl >= b.getLevelRequired() &&
                            Rs2Equipment.isWearing(
                                    ItemID.HUNTING_BUTTERFLY_NET,
                                    ItemID.II_MAGIC_BUTTERFLY_NET,
                                    ItemID.II_MAGIC_BUTTERFLY_NET_DUMMY
                            );
                })
                .map(Butterfly::getId)
                .collect(Collectors.toList());




        Optional<Rs2NpcModel> currentButterfly = Rs2Npc.getNpcs()
                .filter(npc -> butterflies.contains(npc.getId())) //filter for available butterflies
                .filter(npc -> Microbot.getClient().getTopLevelWorldView().players().stream()
                        .map(Player::getInteracting)
                        .noneMatch(interacting -> interacting != null && interacting.equals(npc)) // only butterflies that aren't being chased already
                ).min(Comparator.comparingInt(npc -> Rs2Player.getRs2WorldPoint().distanceToPath(npc.getWorldLocation())));

        currentButterfly.ifPresent(Rs2Npc::interact);


    }
    
    @Override
    public void shutdown() {
        super.shutdown();
    }
}