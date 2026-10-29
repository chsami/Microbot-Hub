package net.runelite.client.plugins.microbot.fourtify.core.client;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.fourtify.core.engine.Actions;
import net.runelite.client.plugins.microbot.fourtify.core.game.Bonds;
import net.runelite.client.plugins.microbot.fourtify.core.game.Rewards;
import net.runelite.client.plugins.microbot.fourtify.core.game.GroundItem;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;
import net.runelite.client.plugins.microbot.fourtify.core.game.Scenery;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link Actions} on the live client through Microbot's helpers. Call from the script thread.
 * <p>
 * This is the only code that clicks, so the account rule lives here: the bonds are never dropped, deposited,
 * withdrawn or used (a bond's item action redeems it), whatever a task asks; and the special items an activity
 * hands out (clue scrolls and bottles, scroll boxes, bird nests, caskets and the like, {@link Rewards}) are never
 * dropped.
 */
public class ClientActions implements Actions {
    private static void neverABond(int itemId, String action) {
        if (Bonds.IDS.contains(itemId)) {
            throw new IllegalArgumentException("refused to " + action + " a bond (item " + itemId + ")");
        }
    }

    private static void neverAReward(int itemId, String action) {
        if (Rewards.IDS.contains(itemId)) {
            throw new IllegalArgumentException("refused to " + action + " a special item (item " + itemId + ")");
        }
    }

    @Override
    public boolean interact(Scenery object, String action) {
        Rs2TileObjectModel model = Microbot.getRs2TileObjectCache().query()
                .withId(object.getId())
                .where(o -> object.getLocation().equals(o.getWorldLocation()))
                .firstOnClientThread();
        if (model == null) {
            return false;
        }
        // A walk still in progress would pull the player away from the object.
        Rs2Walker.setTarget(null, "interact");
        // click(action) refuses when the object lacks the action, instead of sending its first option.
        return model.click(action);
    }

    @Override
    public boolean interact(Npc npc, String action) {
        Rs2NpcModel model = npc(npc);
        if (model == null) {
            return false;
        }
        Rs2Walker.setTarget(null, "interact");
        return model.click(action);
    }

    /** The NPC from the snapshot, found again by its index, which is unique while it exists. */
    private static Rs2NpcModel npc(Npc npc) {
        return Microbot.getRs2NpcCache().query().where(n -> n.getIndex() == npc.getIndex()).firstOnClientThread();
    }

    @Override
    public boolean useItem(int itemId, String action) {
        neverABond(itemId, action);
        return Rs2Inventory.interact(itemId, action);
    }

    @Override
    public boolean equip(int itemId) {
        neverABond(itemId, "equip");
        // Rs2Inventory.wield falls back to "Wear" when the item's menu has no "Wield".
        return Rs2Inventory.wield(itemId);
    }

    @Override
    public boolean useItemOn(int itemId, Scenery object) {
        neverABond(itemId, "use");
        return Rs2Inventory.useItemOnObject(itemId, object.getId());
    }

    @Override
    public boolean useItemOn(int itemId, Npc npc) {
        neverABond(itemId, "use");
        Rs2NpcModel model = npc(npc);
        // Rs2Inventory's NPC overload is deprecated for removal; it takes the older NPC model, built from the NPC.
        return model != null && Rs2Inventory.useItemOnNpc(itemId,
                new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel((NPC) model.getActor()));
    }

    @Override
    public boolean useItemOnItem(int itemId, int otherItemId) {
        neverABond(itemId, "use");
        neverABond(otherItemId, "use");
        return Rs2Inventory.combine(itemId, otherItemId);
    }

    @Override
    public boolean continueDialogue() {
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            return true;
        }
        if (Rs2Widget.isWidgetVisible(ClientReader.LEVEL_UP, ClientReader.LEVEL_UP_CONTINUE)) {
            // The game binds the space key to the level-up message's continue.
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            return true;
        }
        return false;
    }

    @Override
    public boolean chooseOption(String text) {
        // The whole text first, so "Yes" is not answered with "Yes, please"; an option drawn with colour tags
        // only matches in part.
        return Rs2Dialogue.keyPressForDialogueOption(text, true) || Rs2Dialogue.keyPressForDialogueOption(text, false);
    }

    @Override
    public boolean make(int option) {
        Widget all = Rs2Widget.getWidget(MakeMenu.GROUP, MakeMenu.ALL);
        if (all != null && Rs2Widget.isWidgetVisible(MakeMenu.GROUP, MakeMenu.ALL)
                && all.getActions() != null && Arrays.asList(all.getActions()).contains("All")) {
            Rs2Widget.clickWidget(all);
        }
        return Rs2Widget.clickWidget(MakeMenu.GROUP, MakeMenu.FIRST_SLOT + option);
    }

    @Override
    public boolean confirmWarning() {
        if (Rs2Widget.isWidgetVisible(Warnings.WILDERNESS, Warnings.WILDERNESS_TEXT)) {
            throw new IllegalStateException("never confirms the Wilderness warning");
        }
        for (int screen : Warnings.SCREENS) {
            if (Rs2Widget.isWidgetVisible(screen, Warnings.TEXT)) {
                return Rs2Widget.clickWidget(screen, Warnings.INFORM_ONLY.contains(screen) ? Warnings.CLOSE : Warnings.YES);
            }
        }
        return false;
    }

    @Override
    public void setRun(boolean on) {
        Rs2Player.toggleRunEnergy(on);
    }

    @Override
    public boolean take(GroundItem item) {
        Rs2TileItemModel model = Microbot.getRs2TileItemCache().query()
                .withId(item.getId())
                .where(i -> item.getLocation().equals(i.getWorldLocation()))
                .firstOnClientThread();
        return model != null && model.pickup();
    }

    @Override
    public void drop(int itemId) {
        neverABond(itemId, "drop");
        neverAReward(itemId, "drop");
        Rs2Inventory.dropAll(itemId);
    }

    @Override
    public void openBank() {
        walking(Rs2Bank::walkToBankAndUseBank);
    }

    @Override
    public void deposit(int itemId) {
        neverABond(itemId, "deposit");
        Rs2Bank.depositAll(itemId);
    }

    @Override
    public void withdraw(int itemId, int amount) {
        neverABond(itemId, "withdraw");
        // The bank keeps the player's last note setting, and notes cannot be used: switch to items first.
        if (!Rs2Bank.setWithdrawAsItem()) {
            return;
        }
        if (amount == 1) {
            Rs2Bank.withdrawOne(itemId);
        } else {
            Rs2Bank.withdrawX(itemId, amount);
        }
    }

    @Override
    public void closeBank() {
        Rs2Bank.closeBank();
    }

    @Override
    public Walk walkTo(WorldPoint target, int distance) {
        WalkerState state = walking(() -> Rs2Walker.walkWithState(target, distance));
        switch (state) {
            case ARRIVED:
                return Walk.ARRIVED;
            case MOVING:
                return Walk.MOVING;
            case UNREACHABLE:
                return Walk.UNREACHABLE;
            default:
                return Walk.CUT_SHORT;
        }
    }

    /**
     * Runs a walk with the walker's teleports off. The walker picks the shortest way by its own costs, so a free
     * teleport can win the way out but not the way back: a live run banked from the Falador oaks at Lumbridge by
     * the home teleport (30-minute cooldown) and then had to walk all the way back. The bank helper plans with the
     * walker's transports as last refreshed, so the planning is refreshed with teleports off first (its refresh
     * key includes the setting): right after the client started, it still counted the teleport and walked the
     * whole way to Lumbridge. The walker's setting is put back afterwards.
     */
    private static <T> T walking(Supplier<T> walk) {
        boolean before = Rs2Walker.disableTeleports;
        Rs2Walker.disableTeleports = true;
        try {
            Rs2PathApi.refreshPlanningConfiguration();
            return walk.get();
        } finally {
            Rs2Walker.disableTeleports = before;
        }
    }
}
