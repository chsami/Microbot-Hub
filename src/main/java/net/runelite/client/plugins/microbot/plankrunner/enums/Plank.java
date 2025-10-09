package net.runelite.client.plugins.microbot.plankrunner.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;

import java.awt.event.KeyEvent;

@Getter
@RequiredArgsConstructor
public enum Plank {
    PLANK("Wood", ItemID.LOGS, ItemID.WOODPLANK, 100, InterfaceID.Skillmulti.A),
    OAK_PLANK("Oak", ItemID.OAK_LOGS, ItemID.PLANK_OAK, 250, InterfaceID.Skillmulti.B),
    TEAK_PLANK("Teak - 500gp", ItemID.TEAK_LOGS, ItemID.PLANK_TEAK, 500, InterfaceID.Skillmulti.C),
    MAHOGANY_PLANK("Mahogany", ItemID.MAHOGANY_LOGS, ItemID.PLANK_MAHOGANY, 1500, InterfaceID.Skillmulti.D);

    private final String dialogueOption;
    private final int logItemId;
    private final int plankItemId;
    private final int costPerPlank;
    private final int widget;
}
