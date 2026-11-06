package net.runelite.client.plugins.microbot.fourtifysagility.course;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysagility.config.BrimhavenVoucherReward;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrimhavenRewardsTest
{
    private static final class State
    {
        boolean hidden;
        String text;
        String name;
        String[] actions;
        Widget[] children;

        Widget widget()
        {
            return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "isHidden": return hidden;
                        case "getText": return text;
                        case "getName": return name;
                        case "getActions": return actions;
                        case "getDynamicChildren": return children;
                        case "getChild":
                            int index = (Integer) args[0];
                            return children != null && index >= 0 && index < children.length ? children[index] : null;
                        default: return null;
                    }
                });
        }
    }

    private static final class Fixture
    {
        final State title = new State();
        final State frame = new State();
        final State list = new State();
        final State xp = new State();
        final Widget xpWidget = xp.widget();
        final Map<Integer, Widget> widgets = new HashMap<>();
        final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getWidget") && args != null && args.length == 2)
                {
                    return (Integer) args[0] == 819 ? widgets.get((Integer) args[1]) : null;
                }
                return null;
            });

        Fixture()
        {
            title.text = "Agility Arena Store";
            frame.children = new Widget[] {null, title.widget()};
            xp.name = "<col=ff9040>Agility XP</col>";
            xp.actions = new String[] {"Select", "Buy-1", "Buy-5", "Buy-10", "Buy-50"};
            list.children = new Widget[] {xpWidget};
            widgets.put(7, frame.widget());
            widgets.put(38, list.widget());
        }
    }

    @Test
    public void voucherPurchaseRequiresTheExactVisibleRewardAndOneUnitAction()
    {
        Fixture f = new Fixture();
        f.xp.name = "<col=ff9040>Toadflax</col>";
        BrimhavenRewards.Purchase purchase = BrimhavenRewards.findVoucherPurchase(f.client, "Toadflax").get();
        assertEquals(1, purchase.quantity());
        assertEquals("Buy-1", purchase.action());
        assertTrue(BrimhavenRewards.validVoucher(f.client, purchase, "Toadflax"));
        assertFalse(BrimhavenRewards.validVoucher(f.client, purchase, "Snapdragon"));
        f.title.text = "Other Store";
        assertFalse(BrimhavenRewards.validVoucher(f.client, purchase, "Toadflax"));
        f.title.text = "Agility Arena Store";
        State duplicate = new State();
        duplicate.name = "Toadflax";
        duplicate.actions = new String[] {"Buy-1"};
        f.list.children = new Widget[] {f.xpWidget, duplicate.widget()};
        assertFalse(BrimhavenRewards.findVoucherPurchase(f.client, "Toadflax").isPresent());
        f.list.children = new Widget[] {f.xpWidget};
        f.xp.actions = new String[] {"Buy-5"};
        assertFalse(BrimhavenRewards.validVoucher(f.client, purchase, "Toadflax"));
    }

    @Test
    public void voucherPurchaseNeedsRoomAndBothPaymentAndReward()
    {
        assertFalse(BrimhavenRewards.hasRoom(BrimhavenVoucherReward.NONE, 0, 28));
        assertFalse(BrimhavenRewards.hasRoom(BrimhavenVoucherReward.TOADFLAX, 0, 0));
        assertTrue(BrimhavenRewards.hasRoom(BrimhavenVoucherReward.TOADFLAX, 5, 0));
        assertFalse(BrimhavenRewards.hasRoom(BrimhavenVoucherReward.AMYLASE_PACK, 5, 0));
        assertTrue(BrimhavenRewards.hasRoom(BrimhavenVoucherReward.AMYLASE_PACK, 0, 1));
        assertTrue(BrimhavenRewards.purchaseConfirmed(BrimhavenVoucherReward.TOADFLAX, 15, 12, 4, 5));
        assertFalse(BrimhavenRewards.purchaseConfirmed(BrimhavenVoucherReward.TOADFLAX, 15, 12, 4, 4));
        assertFalse(BrimhavenRewards.purchaseConfirmed(BrimhavenVoucherReward.TOADFLAX, 15, 15, 4, 5));
    }
}
