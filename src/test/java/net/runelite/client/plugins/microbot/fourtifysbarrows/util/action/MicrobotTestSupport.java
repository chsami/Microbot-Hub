package net.runelite.client.plugins.microbot.fourtifysbarrows.util.action;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Prayer;
import net.runelite.api.TileObject;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;

/** Test boundary: semantic helper requests become records on the existing fake client. */
public final class MicrobotTestSupport
{
    private MicrobotTestSupport()
    {
    }

    public static void attach(BarrowsActions actions, Client client) throws ReflectiveOperationException
    {
        inject(actions, "client", client);
        inject(actions, "api", new ClickLogApi(client));
        inject(actions, "worker", new ImmediateWorker());
    }

    private static void inject(BarrowsActions actions, String name, Object value) throws ReflectiveOperationException
    {
        Field field = BarrowsActions.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(actions, value);
    }

    /** Worker concurrency and cancellation are tested separately in InteractionWorkerTest. */
    private static final class ImmediateWorker extends InteractionWorker
    {
        private ImmediateWorker()
        {
            super((ClientThread) null);
        }

        @Override
        public boolean submit(BooleanSupplier valid, BooleanSupplier operation, Runnable onStarted)
        {
            if (!valid.getAsBoolean())
            {
                return false;
            }
            onStarted.run();
            operation.getAsBoolean();
            return true;
        }
    }

    private static final class ClickLogApi extends MicrobotApi
    {
        private final Client client;

        private ClickLogApi(Client client)
        {
            this.client = client;
        }

        @Override
        public boolean widget(Widget widget, String option)
        {
            int index = action(widget.getActions(), option);
            client.menuAction(widget.getIndex(), widget.getId(), index >= 5 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP,
                index + 1, widget.getItemId(), option, widget.getName());
            return true;
        }

        @Override
        public boolean npc(NPC npc, String option)
        {
            MenuAction[] options = {MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
                MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION};
            client.menuAction(0, 0, options[action(npc.getTransformedComposition().getActions(), option)],
                npc.getIndex(), -1, option, npc.getName());
            return true;
        }

        @Override
        public boolean object(TileObject object, String option)
        {
            net.runelite.api.ObjectComposition definition = client.getObjectDefinition(object.getId());
            if (definition.getImpostorIds() != null)
            {
                definition = definition.getImpostor();
            }
            int index = action(definition.getActions(), option);
            MenuAction[] options = {MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
                MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION};
            Point location = object instanceof GameObject ? ((GameObject) object).getSceneMinLocation() : null;
            int x = location != null ? location.getX() : object.getLocalLocation().getSceneX();
            int y = location != null ? location.getY() : object.getLocalLocation().getSceneY();
            client.menuAction(x, y, options[index], object.getId(), -1, option, definition.getName());
            return true;
        }

        @Override
        public boolean inventoryInteract(int id, int slot, String option)
        {
            Widget parent = client.getWidget(149 << 16);
            for (Widget child : parent.getDynamicChildren())
            {
                if (child != null && child.getIndex() == slot && child.getItemId() == id)
                {
                    return action(child.getActions(), option) >= 0 ? widget(child, option) : subOption(child, option);
                }
            }
            return false;
        }

        /** Mirrors the Microbot API: the sub-option is found by name; its menu action is named by the item definition. */
        @Override
        public boolean inventorySubOption(int id, int slot, String menu, String option)
        {
            return subOptionMenu(id, option) != null && menu.equalsIgnoreCase(subOptionMenu(id, option));
        }

        private String subOptionMenu(int id, String option)
        {
            net.runelite.api.ItemComposition definition = client.getItemDefinition(id);
            String[][] subops = definition.getSubops();
            for (int menu = 0; subops != null && menu < subops.length; menu++)
            {
                if (action(subops[menu], option) >= 0)
                {
                    return definition.getInventoryActions()[menu];
                }
            }
            return null;
        }

        /** Mirrors the Microbot API: a sub-option goes out as CC_OP with identifier (sub + 1) * 65536 + (widget menu index + 1). */
        private boolean subOption(Widget item, String option)
        {
            String menu = subOptionMenu(item.getItemId(), option);
            int sub = action(client.getItemDefinition(item.getItemId()).getSubops()[
                java.util.Arrays.asList(client.getItemDefinition(item.getItemId()).getInventoryActions()).indexOf(menu)], option);
            int main = action(item.getActions(), menu);
            if (menu == null || main < 0)
            {
                return false;
            }
            client.menuAction(item.getIndex(), item.getId(), MenuAction.CC_OP, (sub + 1) * 65536 + main + 1,
                item.getItemId(), option, "");
            return true;
        }

        @Override
        public boolean bankWithdrawOne(int id)
        {
            return itemAction(client.getWidget(InterfaceID.Bankmain.ITEMS), id, "Withdraw-1");
        }

        @Override
        public boolean bankWithdrawX(int id, int amount)
        {
            return itemAction(client.getWidget(InterfaceID.Bankmain.ITEMS), id, "Withdraw-X");
        }

        @Override
        public boolean bankDepositAll(int id)
        {
            Widget parent = client.getWidget(15, 3);
            return itemAction(parent == null ? client.getWidget(192, 24) : parent, id, "Deposit-All");
        }

        private boolean itemAction(Widget parent, int id, String option)
        {
            if (parent != null && parent.getDynamicChildren() != null)
            {
                for (Widget child : parent.getDynamicChildren())
                {
                    if (child != null && child.getItemId() == id)
                    {
                        return widget(child, option);
                    }
                }
            }
            return false;
        }

        @Override
        public boolean prayer(Prayer prayer, boolean active, boolean switchTab)
        {
            if (switchTab)
            {
                tab(InterfaceTab.PRAYER);
            }
            int child = prayer == Prayer.PROTECT_FROM_MAGIC ? 21 : prayer == Prayer.PROTECT_FROM_MISSILES ? 22 : 23;
            int id = (541 << 16) | child;
            Widget widget = client.getWidget(id);
            client.menuAction(widget == null ? -1 : widget.getIndex(), id, MenuAction.CC_OP, 1,
                widget == null ? -1 : widget.getItemId(), active ? "Activate" : "Deactivate", widget == null ? "" : widget.getName());
            return true;
        }

        @Override
        public boolean tab(InterfaceTab tab)
        {
            client.runScript(915, tab.getVarcIntIndex());
            return true;
        }

        @Override
        public boolean dialogue(int index)
        {
            client.menuAction(index, (219 << 16) | 1, MenuAction.CC_OP, 1, -1, "Continue", "");
            return true;
        }

        private static int action(String[] options, String option)
        {
            if (options != null)
            {
                for (int index = 0; index < options.length; index++)
                {
                    if (option.equalsIgnoreCase(options[index]))
                    {
                        return index;
                    }
                }
            }
            return -1;
        }
    }
}
