package net.runelite.client.plugins.microbot.crafting.jewelry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum CraftingLocation {

    ANYWHERE(null, null),
    EDGEVILLE(new WorldPoint(3109, 3499, 0), BankLocation.EDGEVILLE),
    PORT_PHASMATYS(new WorldPoint(3687, 3479, 0), BankLocation.PORT_PHASMATYS),
    MOUNT_KARUULM(new WorldPoint(1324, 3808, 0), BankLocation.MOUNT_KARUULM),
    ZANARIS(new WorldPoint(2401, 4473, 0), BankLocation.ZANARIS),
    FALADOR(new WorldPoint(2975, 3369, 0), BankLocation.FALADOR_WEST),
    SHILO_VILLAGE(new WorldPoint(2856, 2967, 0), BankLocation.SHILO_VILLAGE);

    private final WorldPoint furnaceLocation;
    private final BankLocation bankLocation;

    public boolean hasRequirements() {
        switch (this) {
            case PORT_PHASMATYS:
                return Rs2Player.isMember() && Rs2Player.getQuestState(Quest.GHOSTS_AHOY) == QuestState.FINISHED;
            default:
                return true;
        }
    }
}
