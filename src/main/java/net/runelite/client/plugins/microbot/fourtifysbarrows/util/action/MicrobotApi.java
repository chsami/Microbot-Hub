package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import net.runelite.client.plugins.microbot.fourtifysbarrows.util.widget.WidgetQueries;

import java.util.Map;
import javax.inject.Singleton;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.api.Prayer;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;

@Singleton
public class MicrobotApi
{
    private static final int WITHDRAW_WAIT_MILLIS = 3000;

    public MicrobotApi()
    {
    }

    public boolean inventoryInteract(int id, int slot, String option)
    {
        if (Thread.currentThread().isInterrupted())
        {
            return false;
        }
        Rs2ItemModel item = Rs2Inventory.getItemInSlot(slot);
        return item != null && item.getId() == id && !Thread.currentThread().isInterrupted()
            && Rs2Inventory.interact(item, option);
    }

    /** True when the carried item lists {@code option} as a sub-option of its {@code menu} action. */
    public boolean inventorySubOption(int id, int slot, String menu, String option)
    {
        Rs2ItemModel item = Rs2Inventory.getItemInSlot(slot);
        Map.Entry<String, Integer> sub = item == null || item.getId() != id ? null : item.getIndexOfSubAction(option);
        return sub != null && menu.equalsIgnoreCase(sub.getKey());
    }

    public boolean bankDepositAll(int id)
    {
        return !Thread.currentThread().isInterrupted() && Rs2Bank.depositAll(id);
    }

    public boolean bankWithdrawOne(int id)
    {
        return !Thread.currentThread().isInterrupted() && Rs2Bank.withdrawOne(id);
    }

    /** Withdraw-X for the amount; stays busy until the inventory changes so the next count includes it. */
    public boolean bankWithdrawX(int id, int amount)
    {
        return !Thread.currentThread().isInterrupted() && Rs2Bank.withdrawX(id, amount)
            && Rs2Inventory.waitForInventoryChanges(WITHDRAW_WAIT_MILLIS);
    }

    /** Clicks the widget's first action, or sends the named action when it is one of the next four. */
    public boolean widget(Widget widget, String option)
    {
        int operation = widgetOperation(widget, option);
        if (operation < 0 || Thread.currentThread().isInterrupted())
        {
            return false;
        }
        if (operation == 0)
        {
            return Rs2Widget.clickWidget(widget);
        }
        Rs2Widget.clickWidgetFast(widget, widget.getIndex(), operation);
        return true;
    }

    /**
     * Returns 0 for the default click, the one-based menu operation for another action the Microbot API can send
     * from a dynamic child, or -1 when the option is missing or cannot be addressed.
     */
    static int widgetOperation(Widget widget, String option)
    {
        int action = WidgetQueries.indexOf(widget.getActions(), option);
        if (action <= 0)
        {
            return action;
        }
        return action <= 4 && widget.getIndex() >= 0 ? action + 1 : -1;
    }

    public boolean dialogue(int index)
    {
        if (Thread.currentThread().isInterrupted())
        {
            return false;
        }
        return Rs2Dialogue.keyPressForDialogueOption(index);
    }

    public boolean bankRingDestination(String destination)
    {
        return !Thread.currentThread().isInterrupted() && bankDialogueReady(destination)
            && Rs2Dialogue.clickOption(destination, true);
    }

    public boolean bankDialogueReady(String destination)
    {
        return Rs2Dialogue.hasDialogueOptionTitle("Where would you like to teleport to?", true)
            && Rs2Dialogue.hasDialogueOption(destination, true);
    }

    public boolean equippedBankRing(int id, String destination)
    {
        return !Thread.currentThread().isInterrupted() && Rs2Equipment.interact(id, destination);
    }

    public boolean prayer(Prayer prayer, boolean active, boolean switchTab)
    {
        if (Thread.currentThread().isInterrupted() || prayer == null)
        {
            return false;
        }
        Rs2PrayerEnum target;
        switch (prayer)
        {
            case PROTECT_FROM_MAGIC:
                target = Rs2PrayerEnum.PROTECT_MAGIC;
                break;
            case PROTECT_FROM_MISSILES:
                target = Rs2PrayerEnum.PROTECT_RANGE;
                break;
            case PROTECT_FROM_MELEE:
                target = Rs2PrayerEnum.PROTECT_MELEE;
                break;
            default:
                return false;
        }
        return Rs2Prayer.toggle(target, active, switchTab);
    }

    public boolean tab(InterfaceTab tab)
    {
        if (Thread.currentThread().isInterrupted() || tab == null)
        {
            return false;
        }
        return Rs2Tab.switchTo(tab);
    }
    public boolean disableAutoRetaliate()
    {
        return !Thread.currentThread().isInterrupted() && !Microbot.getClientThread().isClientThread()
            && Rs2Combat.setAutoRetaliate(false);
    }

    public boolean pickUp(Tile tile, TileItem item)
    {
        return !Thread.currentThread().isInterrupted() && tile != null && item != null
            && new Rs2TileItemModel(tile, item).pickup();
    }

    /**
     * Once the client has seen "I can't reach that!", the NPC model walks to the NPC before clicking unless the
     * NPC is in line of sight; with line of sight it clears that flag and clicks without walking.
     */
    public boolean npc(NPC npc, String option)
    {
        if (!canClick(npc, option))
        {
            return false;
        }
        Rs2NpcModel model = new Rs2NpcModel(npc);
        return (!(Microbot.isCantReachTargetDetectionEnabled && Microbot.cantReachTarget) || model.hasLineOfSight())
            && model.click(option);
    }

    public boolean object(TileObject object, String option)
    {
        return canClick(object, option) && ObjectClick.click(object, option);
    }

    /** Scene clicks run off the client thread, with no selected widget. */
    private boolean canClick(Object target, String option)
    {
        if (Thread.currentThread().isInterrupted() || target == null || option == null || option.isBlank()
            || Microbot.getClientThread().isClientThread())
        {
            return false;
        }
        boolean selected = Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getClient() == null || Microbot.getClient().isWidgetSelected()).orElse(true);
        return !selected && !Thread.currentThread().isInterrupted();
    }

}
