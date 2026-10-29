package net.runelite.client.plugins.microbot.fourtify.core.engine;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtify.core.client.ClientActions;
import net.runelite.client.plugins.microbot.fourtify.core.game.GroundItem;
import net.runelite.client.plugins.microbot.fourtify.core.game.Npc;
import net.runelite.client.plugins.microbot.fourtify.core.game.Scenery;

/**
 * Everything a task may do to the game. Tasks act only through this, so the same task code drives the live
 * client ({@link ClientActions}) or fake actions in tests.
 * <p>
 * No result here means the action worked: a task states the effect it expects with a {@link WaitFor}, and the
 * {@link TaskList} checks it on the following ticks. Clicks return false only when they could not be sent at
 * all (the target is gone or lacks the action). Dropping and banking return nothing, because Microbot's helpers
 * for them do not report reliably: dropping reports success even when player input stopped it, and opening a
 * bank can report failure with the bank open.
 */
public interface Actions {
    /** Clicks {@code action} on an object from the snapshot. False if the object is gone or lacks the action. */
    boolean interact(Scenery object, String action);

    /** Clicks {@code action} ("Attack", "Talk-to", "Net") on an NPC from the snapshot. False if it is gone. */
    boolean interact(Npc npc, String action);

    /** Clicks {@code action} ("Eat", "Drink", "Wield", "Wear", "Bury") on an inventory item. False if not carried. */
    boolean useItem(int itemId, String action);

    /** Wields or wears an inventory item, whichever its menu offers. False if not carried. */
    boolean equip(int itemId);

    /** Uses an inventory item on an object; Microbot's helper picks the nearest object with that ID. */
    boolean useItemOn(int itemId, Scenery object);

    /** Uses an inventory item on an NPC from the snapshot. */
    boolean useItemOn(int itemId, Npc npc);

    /** Uses one inventory item on another, such as a knife on logs. */
    boolean useItemOnItem(int itemId, int otherItemId);

    /** Clicks "Click here to continue". False when the open dialogue has no continue. */
    boolean continueDialogue();

    /** Chooses the dialogue option containing {@code text}. False when there is none. */
    boolean chooseOption(String text);

    /** Makes as many as the materials allow of the make menu's {@code option}th product (0 is the first). */
    boolean make(int option);

    /** Confirms the open warning screen. False when none is open. Never the Wilderness warning. */
    boolean confirmWarning();

    /** Turns run on or off. */
    void setRun(boolean on);

    /** Clicks "Take" on a ground item from the snapshot. False if it is gone. */
    boolean take(GroundItem item);

    /** Drops every inventory item with this ID, and nothing else. Blocks while it drops. */
    void drop(int itemId);

    /** Walks to the nearest bank and opens it. Blocks while it walks. */
    void openBank();

    /** Deposits every inventory item with this ID, and nothing else. */
    void deposit(int itemId);

    /** Withdraws {@code amount} of this item from the open bank. */
    void withdraw(int itemId, int amount);

    void closeBank();

    /** Exact base ID and quantity, with note withdrawals enabled. */
    default void withdrawForSale(int itemId, int quantity) { throw new UnsupportedOperationException(); }
    default void openExchange() { throw new UnsupportedOperationException(); }
    default void closeExchange() { throw new UnsupportedOperationException(); }
    default void exchangeOverview() { throw new UnsupportedOperationException(); }
    default void offerItem(int inventoryId) { throw new UnsupportedOperationException(); }
    default void offerAll() { throw new UnsupportedOperationException(); }
    default void enterOfferPrice() { throw new UnsupportedOperationException(); }
    default void typeOfferPriceOne() { throw new UnsupportedOperationException(); }
    /** Rechecks the live sell setup and stock before dispatching confirmation. */
    default void confirmSale(int itemId, int quantity) { throw new UnsupportedOperationException(); }
    default void confirmSaleWarning(int itemId, int quantity) { throw new UnsupportedOperationException(); }
    /** Collects one completed sale, without touching other slots or depositing inventory items. */
    default void collectSale(int slot) { throw new UnsupportedOperationException(); }

    /** Walks until within {@code distance} tiles of {@code target}. */
    Walk walkTo(WorldPoint target, int distance);

    enum Walk {
        ARRIVED,
        /** Still on the way; call again. */
        MOVING,
        /** The walker found no path. */
        UNREACHABLE,
        /** Stopped before arriving (script stopped, a break, the player took over); the target is fine. */
        CUT_SHORT
    }
}
