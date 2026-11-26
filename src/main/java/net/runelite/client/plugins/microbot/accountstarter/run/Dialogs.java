package net.runelite.client.plugins.microbot.accountstarter.run;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public final class Dialogs {
    private Dialogs() {}

    /** One step away always closes a dialogue. */
    public static void close() {
        if (!Rs2Dialogue.isInDialogue()) return;
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        if (Global.sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 1200)) return;
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null) return;
        List<int[]> sides = new ArrayList<>(List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1}));
        Collections.shuffle(sides);
        for (int[] d : sides) {
            WorldPoint t = new WorldPoint(me.getX() + d[0], me.getY() + d[1], me.getPlane());
            if (Rs2Tile.isWalkable(t)) {
                Rs2Walker.walkFastCanvas(t);
                Global.sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 2400);
                return;
            }
        }
    }
}
