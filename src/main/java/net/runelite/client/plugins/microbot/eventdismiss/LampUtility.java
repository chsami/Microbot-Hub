package net.runelite.client.plugins.microbot.eventdismiss;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.EnumMap;
import java.util.Map;

import static net.runelite.client.plugins.microbot.eventdismiss.LampWidgetConstants.*;

@Slf4j
public class LampUtility {

    private static final Map<Skill, Integer> SKILL_WIDGET_MAP = new EnumMap<>(Skill.class);

    @Getter
    private static int lampsUsed = 0;
    @Getter
    private static long lastLampTime = 0;

    static {
        SKILL_WIDGET_MAP.put(Skill.ATTACK, WIDGET_ATTACK);
        SKILL_WIDGET_MAP.put(Skill.STRENGTH, WIDGET_STRENGTH);
        SKILL_WIDGET_MAP.put(Skill.RANGED, WIDGET_RANGED);
        SKILL_WIDGET_MAP.put(Skill.MAGIC, WIDGET_MAGIC);
        SKILL_WIDGET_MAP.put(Skill.DEFENCE, WIDGET_DEFENCE);
        SKILL_WIDGET_MAP.put(Skill.HITPOINTS, WIDGET_HITPOINTS);
        SKILL_WIDGET_MAP.put(Skill.PRAYER, WIDGET_PRAYER);
        SKILL_WIDGET_MAP.put(Skill.AGILITY, WIDGET_AGILITY);
        SKILL_WIDGET_MAP.put(Skill.HERBLORE, WIDGET_HERBLORE);
        SKILL_WIDGET_MAP.put(Skill.THIEVING, WIDGET_THIEVING);
        SKILL_WIDGET_MAP.put(Skill.CRAFTING, WIDGET_CRAFTING);
        SKILL_WIDGET_MAP.put(Skill.RUNECRAFT, WIDGET_RUNECRAFT);
        SKILL_WIDGET_MAP.put(Skill.SLAYER, WIDGET_SLAYER);
        SKILL_WIDGET_MAP.put(Skill.FARMING, WIDGET_FARMING);
        SKILL_WIDGET_MAP.put(Skill.MINING, WIDGET_MINING);
        SKILL_WIDGET_MAP.put(Skill.SMITHING, WIDGET_SMITHING);
        SKILL_WIDGET_MAP.put(Skill.FISHING, WIDGET_FISHING);
        SKILL_WIDGET_MAP.put(Skill.COOKING, WIDGET_COOKING);
        SKILL_WIDGET_MAP.put(Skill.FIREMAKING, WIDGET_FIREMAKING);
        SKILL_WIDGET_MAP.put(Skill.WOODCUTTING, WIDGET_WOODCUTTING);
        SKILL_WIDGET_MAP.put(Skill.FLETCHING, WIDGET_FLETCHING);
        SKILL_WIDGET_MAP.put(Skill.CONSTRUCTION, WIDGET_CONSTRUCTION);
        SKILL_WIDGET_MAP.put(Skill.HUNTER, WIDGET_HUNTER);
    }

    public static int getSkillWidgetId(Skill skill) {
        if (skill == null) {
            return -1;
        }
        return SKILL_WIDGET_MAP.getOrDefault(skill, -1);
    }

    public static boolean useLamp(Skill skill) {
        if (skill == null || !Rs2Inventory.contains(ItemID.LAMP)) {
            return false;
        }

        if (!Rs2Inventory.interact(ItemID.LAMP, "Rub")) {
            log.warn("Failed to rub lamp");
            return false;
        }

        if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(LAMP_WIDGET_GROUP, LAMP_WIDGET_ROOT), 3000)) {
            log.warn("Lamp interface did not open");
            return false;
        }

        int skillWidgetId = getSkillWidgetId(skill);
        if (skillWidgetId == -1) {
            log.warn("Unsupported lamp skill: {}", skill);
            return false;
        }

        Rs2Widget.clickWidget(LAMP_WIDGET_GROUP, skillWidgetId);
        Global.sleep(600, 1200);

        Rs2Widget.clickWidget(LAMP_WIDGET_GROUP, LAMP_CONFIRM_BUTTON);
        Global.sleep(600, 1200);

        lampsUsed++;
        lastLampTime = System.currentTimeMillis();
        log.info("Used lamp on {}", skill);
        return true;
    }

    public static boolean hasLampInInventory() {
        return Rs2Inventory.contains(ItemID.LAMP);
    }

    public static void reset() {
        lampsUsed = 0;
        lastLampTime = 0;
    }
}
