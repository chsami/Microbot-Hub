package net.runelite.client.plugins.microbot.autofarmer;

import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;

/** Explicit skill-to-widget mapping for standard random-event XP rewards. */
public enum XpLampSkill {
    KEEP_LAMP(null, -1),
    AGILITY(Skill.AGILITY, InterfaceID.Xpreward.AGILITY),
    ATTACK(Skill.ATTACK, InterfaceID.Xpreward.ATTACK),
    CONSTRUCTION(Skill.CONSTRUCTION, InterfaceID.Xpreward.CONSTRUCTION),
    COOKING(Skill.COOKING, InterfaceID.Xpreward.COOKING),
    CRAFTING(Skill.CRAFTING, InterfaceID.Xpreward.CRAFTING),
    DEFENCE(Skill.DEFENCE, InterfaceID.Xpreward.DEFENCE),
    FARMING(Skill.FARMING, InterfaceID.Xpreward.FARMING),
    FIREMAKING(Skill.FIREMAKING, InterfaceID.Xpreward.FIREMAKING),
    FISHING(Skill.FISHING, InterfaceID.Xpreward.FISHING),
    FLETCHING(Skill.FLETCHING, InterfaceID.Xpreward.FLETCHING),
    HERBLORE(Skill.HERBLORE, InterfaceID.Xpreward.HERBLORE),
    HITPOINTS(Skill.HITPOINTS, InterfaceID.Xpreward.HITPOINTS),
    HUNTER(Skill.HUNTER, InterfaceID.Xpreward.HUNTER),
    MAGIC(Skill.MAGIC, InterfaceID.Xpreward.MAGIC),
    MINING(Skill.MINING, InterfaceID.Xpreward.MINING),
    PRAYER(Skill.PRAYER, InterfaceID.Xpreward.PRAYER),
    RANGED(Skill.RANGED, InterfaceID.Xpreward.RANGED),
    RUNECRAFT(Skill.RUNECRAFT, InterfaceID.Xpreward.RUNECRAFT),
    SAILING(Skill.SAILING, InterfaceID.Xpreward.SAILING),
    SLAYER(Skill.SLAYER, InterfaceID.Xpreward.SLAYER),
    SMITHING(Skill.SMITHING, InterfaceID.Xpreward.SMITHING),
    STRENGTH(Skill.STRENGTH, InterfaceID.Xpreward.STRENGTH),
    THIEVING(Skill.THIEVING, InterfaceID.Xpreward.THIEVING),
    WOODCUTTING(Skill.WOODCUTTING, InterfaceID.Xpreward.WOODCUTTING);

    private final Skill skill;
    private final int widgetId;
    XpLampSkill(Skill skill, int widgetId) { this.skill = skill; this.widgetId = widgetId; }
    public Skill skill() { return skill; }
    public int widgetId() { return widgetId; }
    public boolean spendsReward() { return skill != null; }
    @Override public String toString() { return skill == null ? "Keep lamp" : skill.getName(); }
}
