package net.runelite.client.plugins.microbot.MmCaves.enums;

import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

public enum MagicSpell {
    ICE_BURST("Ice Burst", Rs2CombatSpells.ICE_BURST),
    ICE_BARRAGE("Ice Barrage", Rs2CombatSpells.ICE_BARRAGE),
    BLOOD_BURST("Blood Burst", Rs2CombatSpells.BLOOD_BURST),
    BLOOD_BARRAGE("Blood Barrage", Rs2CombatSpells.BLOOD_BARRAGE),
    SHADOW_BURST("Shadow Burst", Rs2CombatSpells.SHADOW_BURST),
    SHADOW_BARRAGE("Shadow Barrage", Rs2CombatSpells.SHADOW_BARRAGE),
    SMOKE_BURST("Smoke Burst", Rs2CombatSpells.SMOKE_BURST),
    SMOKE_BARRAGE("Smoke Barrage", Rs2CombatSpells.SMOKE_BARRAGE);

    private final String spellName;
    private final Rs2CombatSpells spell;

    MagicSpell(String spellName, Rs2CombatSpells spell) {
        this.spellName = spellName;
        this.spell = spell;
    }

    public String getSpellName() {
        return spellName;
    }

    public Rs2CombatSpells getSpell() {
        return spell;
    }

    @Override
    public String toString() {
        return spellName;
    }
}
