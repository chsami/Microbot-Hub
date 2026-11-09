package net.runelite.client.plugins.microbot.herbrun;

import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.enums.Herbs;

import java.util.HashMap;
import java.util.Objects;

@Getter
public class HerbPatch {
    private final FarmingPatch patch;
    private final String regionName;
    private String displayName;
    private String toggleName;
    private final CropState prediction;
    private final WorldPoint location;
    private boolean enabled;
    private final HashMap<String, Integer> items = new HashMap<>();

    public HerbPatch(FarmingPatch patch, HerbrunConfig config, FarmingHandler farmingHandler) {
        this.patch = patch;
        this.regionName = patch.getRegion().getName();
        this.prediction = farmingHandler.predictPatch(patch);
        this.location = getHerbFromName(regionName).getWorldPoint();
        this.displayName = regionName;
        switch (regionName) {
            case "Ardougne":
//                if (Rs2Bank.hasItem("Ardougne cloak")) {
//                    this.items.put("Ardougne cloak", 1);
//                }
                this.toggleName = "Enable Ardougne Patch";
                this.enabled = config.enableArdougne();
                break;
            case "Catherby":
                this.items.put("Camelot teleport", 1);
                this.toggleName = "Enable Catherby Patch";
                this.enabled = config.enableCatherby();
                break;
            case "Civitas illa Fortis":
                this.items.put("Civitas illa fortis teleport", 1);
                this.displayName = "Civitas illa Fortis (Varlamore)";
                this.toggleName = "Enable Varlamore Patch";
                this.enabled = config.enableVarlamore();
                break;
            case "Falador":
                this.items.put("Explorer's ring", 1);
                this.toggleName = "Enable Falador Patch";
                this.enabled = config.enableFalador();
                break;
            case "Farming Guild":
                this.items.put("Skills necklace(", 1);
                this.toggleName = "Enable Farming Guild Patch";
                this.enabled = config.enableGuild();
                break;
            case "Kourend":
                this.items.put("Xeric's talisman", 1);
                this.displayName = "Kourend (Hosidius)";
                this.toggleName = "Enable Hosidius Patch";
                this.enabled = config.enableHosidius();
                break;
            case "Morytania":
                this.items.put("Ectophial", 1);
                this.toggleName = "Enable Morytania Patch";
                this.enabled = config.enableMorytania();
                break;
            case "Troll Stronghold":
                this.items.put("Stony basalt", 1);
                this.displayName = "Troll Stronghold (Trollheim)";
                this.toggleName = "Enable Trollheim Patch";
                this.enabled = config.enableTrollheim() && hasCompleted(Quest.MY_ARMS_BIG_ADVENTURE);
                break;
            case "Weiss":
                this.items.put("Icy basalt", 1);
                this.toggleName = "Enable Weiss Patch";
                this.enabled = config.enableWeiss() && hasCompleted(Quest.MAKING_FRIENDS_WITH_MY_ARM);
                break;
            case "Harmony":
                this.enabled = false;
                break;

        }
    }

    private boolean hasCompleted(Quest quest) {
        QuestState state = Rs2Player.getQuestState(quest);
        if (state == QuestState.FINISHED) {
            return true;
        }
        if (state == null) {
            Microbot.log("Could not read the status of " + quest.getName() + " yet, skipping the " + displayName + " herb patch this run");
            return false;
        }
        Microbot.log("Skipping " + displayName + " herb patch: " + quest.getName() + " is not completed. Finish the quest or turn off '" + toggleName + "' to hide this message.");
        return false;
    }

    /**
     * Gets a Herbs enum value from its string name
     * @param regionName The region name (e.g., "Ardougne")
     * @return The matching Herbs enum value, or NONE if not found
     */
    private static Herbs getHerbFromName(String regionName) {
        for (Herbs herb : Herbs.values()) {
            if (herb.getName().equalsIgnoreCase(regionName)) {
                return herb;
            }
        }
        return Herbs.NONE;
    }

    public boolean isInRange(int distance) {
        if(Objects.equals(regionName, "Weiss")) {
         return Rs2Player.getWorldLocation().getRegionID() == 11325;

        } else if(Objects.equals(regionName, "Troll Stronghold")) {
            return Rs2Player.getWorldLocation().getRegionID() == 11321;
        } else {
            return Rs2Player.getWorldLocation().distanceTo(location) < distance;
        }
    }


    public boolean contains(WorldPoint worldPoint) {
        return location.equals(worldPoint);
    }

    public boolean contains(String regionName) {
        return this.regionName.equals(regionName);
    }

}
