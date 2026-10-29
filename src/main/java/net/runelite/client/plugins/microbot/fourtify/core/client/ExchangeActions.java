package net.runelite.client.plugins.microbot.fourtify.core.client;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.fourtify.core.game.Bonds;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.util.function.Function;
import java.util.function.Supplier;
import java.awt.Rectangle;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Transaction actions stay short; the task's later snapshots acknowledge their effects. */
public final class ExchangeActions extends ClientActions {
    private static <T> T client(Supplier<T> read) {
        return Microbot.getClientThread().runOnClientThreadOptional(read::get)
                .orElseThrow(() -> new IllegalStateException("client thread unavailable"));
    }

    private static void allowed(int id) {
        if (Bonds.IDS.contains(id)) { throw new IllegalArgumentException("refused to sell or withdraw a bond"); }
    }

    @Override
    public void withdrawForSale(int id, int quantity) {
        allowed(id);
        if (quantity <= 0 || !Rs2Bank.isOpen()) { throw new IllegalStateException("bank withdrawal unavailable"); }
        // The game's bank search toggle refuses while a native tag is active; do not click a filtered row blindly.
        if (client(() -> Microbot.getClient().getVarcIntValue(1380) != -1)) {
            throw new IllegalStateException("close the native bank tag before starting the bank seller");
        }
        if (client(() -> bankSearch(Microbot.getClient()))) {
            Rs2Widget.clickWidget(12, 42); // bankmain:search, Search op1; game scripts 281 and 514.
            if (!sleepUntil(() -> client(() -> !bankSearch(Microbot.getClient())), 5000)) {
                throw new IllegalStateException("bank search did not clear");
            }
        }
        int available = client(() -> {
            Client c = Microbot.getClient();
            ItemContainer bank = c.getItemContainer(InventoryID.BANK);
            return bank == null || !c.getItemDefinition(id).isGeTradeable() ? 0 : bank.count(id);
        });
        if (available < quantity) { throw new IllegalStateException("bank stock changed before withdrawal"); }
        Rs2Bank.openTab(0); // Bank's All tab; keep row IDs independent of tab layout.
        if (!Rs2Bank.setWithdrawAsNote()) { throw new IllegalStateException("note mode did not activate"); }
        // Confirm the exact row first: withdrawAll(int) only falls back to a same-name variant when it is missing.
        if (Rs2Bank.get(row -> row.getId() == id && client(() -> {
            ItemContainer bank = Microbot.getClient().getItemContainer(InventoryID.BANK);
            return bank != null && bank.getItem(row.getSlot()) != null
                    && bank.getItem(row.getSlot()).getId() == id;
        })) == null || !Rs2Bank.withdrawAll(id)) { throw new IllegalStateException("exact bank row unavailable"); }
    }

    static boolean bankSearch(Client c) {
        int mode = c.getVarcIntValue(VarClientID.MESLAYERMODE);
        String input = c.getVarcStrValue(VarClientID.MESLAYERINPUT);
        return mode == 11 || mode <= 0 && input != null && !input.isEmpty();
    }

    @Override public void openExchange() { Rs2GrandExchange.openExchange(); }
    @Override public void closeExchange() { Rs2GrandExchange.closeExchange(); }
    @Override public void exchangeOverview() { Rs2GrandExchange.backToOverview(); }

    @Override
    public void offerItem(int inventoryId) {
        allowed(inventoryId);
        if (!Rs2GrandExchange.isOpen()) { throw new IllegalStateException("exchange is closed"); }
        Rs2Inventory.interact(inventoryId, "Offer");
    }

    private static void click(int child, int dynamic, String action) {
        Widget target = client(() -> {
            Widget parent = Microbot.getClient().getWidget(ExchangeWidgets.GROUP, child);
            Widget widget = parent == null || dynamic < 0 ? parent : parent.getChild(dynamic);
            if (widget == null || widget.isHidden()) { throw new IllegalStateException(action + " button unavailable"); }
            return widget;
        });
        Rs2Widget.clickWidget(target);
    }

    @Override public void offerAll() { click(ExchangeWidgets.SETUP, ExchangeWidgets.ALL, "All"); }
    @Override public void enterOfferPrice() { click(ExchangeWidgets.SETUP, ExchangeWidgets.PRICE, "Enter price"); }

    @Override
    public void typeOfferPriceOne() {
        // Replace the input buffer on the client thread, then send Enter from the script thread.
        if (!client(() -> {
            Client c = Microbot.getClient();
            if (!ExchangeWidgets.priceInput(c)) { return false; }
            c.setVarcStrValue(VarClientID.MESLAYERINPUT, "1");
            c.getWidget(net.runelite.api.gameval.InterfaceID.Chatbox.MES_TEXT2).setText("1*");
            return true;
        })) { throw new IllegalStateException("expected a price input; leaving unknown input untouched"); }
        if (!client(() -> ExchangeWidgets.priceInput(Microbot.getClient())
                && "1".equals(Microbot.getClient().getVarcStrValue(VarClientID.MESLAYERINPUT)))) {
            throw new IllegalStateException("price input did not contain 1");
        }
        Rs2Keyboard.enter();
    }

    private static void checkSale(int id, int quantity) {
        allowed(id);
        if (!client(() -> validSale(Microbot.getClient(), id, quantity))) {
            throw new IllegalStateException("sale setup or inventory changed before confirmation");
        }
    }

    static boolean validSale(Client c, int id, int quantity) {
        if (Bonds.IDS.contains(id) || quantity <= 0 || !c.getItemDefinition(id).isGeTradeable()
                || c.getVarpValue(ExchangeWidgets.ITEM_VARP) != id
                || c.getVarbitValue(ExchangeWidgets.TYPE_VARBIT) != 1
                || c.getVarbitValue(ExchangeWidgets.QUANTITY_VARBIT) != quantity
                || ExchangeWidgets.unitPrice(c) != 1L) { return false; }
        ItemContainer inventory = c.getItemContainer(InventoryID.INV);
        if (inventory == null) { return false; }
        ItemComposition base = c.getItemDefinition(id);
        long stock = inventory.count(id);
        if (base.getLinkedNoteId() > 0) { stock += inventory.count(base.getLinkedNoteId()); }
        return Math.min(Integer.MAX_VALUE, stock) == quantity;
    }

    @Override
    public void confirmSale(int id, int quantity) {
        checkSale(id, quantity);
        click(ExchangeWidgets.CONFIRM, -1, "Confirm");
    }

    @Override
    public void confirmSaleWarning(int id, int quantity) {
        checkSale(id, quantity);
        // Only the low-price warning is confirmed: the popup is shared, so its text is checked again in the click hop.
        if (!client(() -> ExchangeWidgets.priceWarning(Microbot.getClient()))) { throw new IllegalStateException("sale warning changed"); }
        invoke(() -> {
            Client c = Microbot.getClient();
            return ExchangeWidgets.priceWarning(c) ? ExchangeWidgets.warningYes(c) : null;
        }, widget -> "Yes");
    }

    @Override
    public void collectSale(int slot) {
        if (!client(() -> ExchangeWidgets.collectable(Microbot.getClient(), slot))) {
            throw new IllegalStateException("exchange slot changed before collection");
        }
        if (!client(() -> ExchangeWidgets.visible(Microbot.getClient(), ExchangeWidgets.DETAILS)
                && Microbot.getClient().getVarbitValue(4439) == slot + 1)) {
            Widget view = client(() -> {
                Widget panel = Microbot.getClient().getWidget(ExchangeWidgets.GROUP, 7 + slot);
                Widget button = panel == null ? null : panel.getChild(2);
                if (button == null || button.isHidden() || ExchangeWidgets.actionIndex(button, "View offer") < 1) {
                    throw new IllegalStateException("finished offer view unavailable");
                }
                return button;
            });
            invoke(view, "View offer");
        }
        for (int taken = 0; taken < ExchangeWidgets.COLLECT_SLOTS; taken++) {
            // A later pass finds the slot already cleared when the first click took everything.
            if (taken > 0 && !client(() -> ExchangeWidgets.collectable(Microbot.getClient(), slot))) { return; }
            boolean toBank = Rs2Inventory.isFull();
            // A live run on 2026-10-28 showed the panel opening before the coin button existed.
            // Use the client's usual five-second widget wait, then revalidate the exact slot and action.
            if (!sleepUntil(() -> client(() -> ExchangeWidgets.collectionButton(Microbot.getClient(), slot, toBank) != null),
                    taken == 0 ? 5000 : 1500)) {
                if (taken == 0) { throw new IllegalStateException("finished offer collect button did not load"); }
                return;
            }
            int remaining = client(() -> ExchangeWidgets.collectionButtons(Microbot.getClient(), slot, toBank).size());
            invoke(() -> ExchangeWidgets.collectionButton(Microbot.getClient(), slot, toBank),
                    widget -> ExchangeWidgets.collectAction(widget, toBank));
            // Wait for this slot's contents to leave the panel, so the next pass sees the other one.
            sleepUntil(() -> client(() -> !ExchangeWidgets.collectable(Microbot.getClient(), slot)
                    || ExchangeWidgets.collectionButtons(Microbot.getClient(), slot, toBank).size() < remaining), 3000);
        }
    }

    private static void invoke(Widget widget, String action) {
        invoke(() -> widget, w -> action);
    }

    private static void invoke(Supplier<Widget> target, Function<Widget, String> actionOf) {
        // Resolve and validate again in the same client-thread hop that builds the menu action.
        MenuClick click = client(() -> {
            Widget widget = target.get();
            if (widget == null) { throw new IllegalStateException("click target changed"); }
            String action = actionOf.apply(widget);
            int identifier = action == null ? -1 : ExchangeWidgets.actionIndex(widget, action);
            if (identifier < 1) { throw new IllegalStateException(action + " action unavailable"); }
            return new MenuClick(new NewMenuEntry().option(action).target("").identifier(identifier)
                    .type(MenuAction.CC_OP).param0(widget.getIndex()).param1(widget.getId())
                    .itemId(widget.getItemId()).forceLeftClick(false), widget.getBounds());
        });
        Microbot.doInvoke(click.entry, click.bounds);
    }

    private static final class MenuClick {
        private final NewMenuEntry entry;
        private final Rectangle bounds;
        private MenuClick(NewMenuEntry entry, Rectangle bounds) { this.entry = entry; this.bounds = bounds; }
    }
}
