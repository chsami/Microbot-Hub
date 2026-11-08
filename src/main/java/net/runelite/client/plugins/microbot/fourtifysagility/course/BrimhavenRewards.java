package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysagility.config.BrimhavenVoucherReward;
import java.util.Arrays;
import net.runelite.client.util.Text;

/** Identifies a voucher purchase in the Brimhaven arena store. */
public final class BrimhavenRewards
{
    private static final int GROUP = 819;
    private static final int FRAME = 7;
    private static final int LIST = 38;
    private static final String TITLE = "Agility Arena Store";

    public static final class Purchase
    {
        private final Widget widget;
        private final String action;
        private final int quantity;

        private Purchase(Widget widget, String action, int quantity)
        {
            this.widget = widget;
            this.action = action;
            this.quantity = quantity;
        }

        public Widget widget()
        {
            return widget;
        }

        public String action()
        {
            return action;
        }

        public int quantity()
        {
            return quantity;
        }
    }

    private BrimhavenRewards()
    {
    }

    /** Selects one exact voucher reward, avoiding partial bulk purchases. */
    public static Optional<Purchase> findVoucherPurchase(Client client, String name)
    {
        if (client == null || name == null || name.isEmpty())
        {
            return Optional.empty();
        }
        Widget frame = client.getWidget(GROUP, FRAME);
        Widget title = frame == null ? null : frame.getChild(1);
        Widget list = client.getWidget(GROUP, LIST);
        if (!visible(frame) || !visible(title) || !TITLE.equals(plain(title.getText()))
            || !visible(list) || list.getDynamicChildren() == null)
        {
            return Optional.empty();
        }
        Widget row = null;
        for (Widget candidate : list.getDynamicChildren())
        {
            if (visible(candidate) && name.equals(plain(candidate.getName())))
            {
                if (row != null)
                {
                    return Optional.empty();
                }
                row = candidate;
            }
        }
        if (row == null)
        {
            return Optional.empty();
        }
        if (row.getActions() != null && Arrays.asList(row.getActions()).contains("Buy-1"))
        {
            return Optional.of(new Purchase(row, "Buy-1", 1));
        }
        return Optional.empty();
    }

    public static boolean validVoucher(Client client, Purchase purchase, String name)
    {
        if (purchase == null)
        {
            return false;
        }
        Optional<Purchase> current = findVoucherPurchase(client, name);
        return current.isPresent() && current.get().widget == purchase.widget
            && current.get().action.equals(purchase.action);
    }

    public static boolean hasRoom(BrimhavenVoucherReward reward, int items, int freeSlots)
    {
        return reward != null && reward != BrimhavenVoucherReward.NONE
            && (freeSlots > 0 || reward.stackable() && items > 0);
    }

    public static boolean purchaseConfirmed(BrimhavenVoucherReward reward, int vouchersBefore, int vouchersAfter,
        int itemsBefore, int itemsAfter)
    {
        return reward != null && reward != BrimhavenVoucherReward.NONE
            && vouchersAfter == vouchersBefore - reward.price() && itemsAfter == itemsBefore + 1;
    }

    private static boolean visible(Widget widget)
    {
        return widget != null && !widget.isHidden();
    }

    private static String plain(String text)
    {
        return text == null ? "" : Text.removeTags(text).trim();
    }
}
