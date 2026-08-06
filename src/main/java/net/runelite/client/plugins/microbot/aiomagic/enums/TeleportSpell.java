package net.runelite.client.plugins.microbot.aiomagic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;

@Getter
@RequiredArgsConstructor
public enum TeleportSpell {
    VARROCK_TELEPORT(Rs2Spells.VARROCK_TELEPORT),
    LUMBRIDGE_TELEPORT(Rs2Spells.LUMBRIDGE_TELEPORT),
    FALADOR_TELEPORT(Rs2Spells.FALADOR_TELEPORT),
    CAMELOT_TELEPORT(Rs2Spells.CAMELOT_TELEPORT),
    KOUREND_CASTLE_TELEPORT(Rs2Spells.KOUREND_CASTLE_TELEPORT),
    ARDOUGNE_TELEPORT(Rs2Spells.ARDOUGNE_TELEPORT),
    CIVITAS_ILLA_FORTIS_TELEPORT(Rs2Spells.CIVITAS_ILLA_FORTIS_TELEPORT),
    WATCHTOWER_TELEPORT(Rs2Spells.WATCHTOWER_TELEPORT),
    TROLLHEIM_TELEPORT(Rs2Spells.TROLLHEIM_TELEPORT);
    
    private final Rs2Spells rs2Spell;
}
