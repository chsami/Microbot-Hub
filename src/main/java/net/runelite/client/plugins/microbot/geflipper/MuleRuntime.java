package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.IntToLongFunction;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;

/** Live RuneLite/Microbot adapter for MuleSession. */
final class MuleRuntime implements MuleSession.Port {
    private static final WorldArea GRAND_EXCHANGE_AREA = new WorldArea(3136, 3465, 61, 54, 0);
    // Slots 0–5 retain the client's legacy TRADINGPOST_SELL inventory IDs; 6–7 are GE_COLLECT.
    private static final int[] GE_COLLECTION_INVENTORIES = {518, 519, 520, 521, 522, 523, 539, 540};
    private final BooleanSupplier running;
    private final BooleanSupplier varyMouseSpeed;
    private final BooleanSupplier receiving;
    private final IntToLongFunction marketPrices;
    private final GeflipperMouseMotion mouseMotion = new GeflipperMouseMotion();
    private final AtomicLong motionGeneration = new AtomicLong();
    private final MuleNetwork.Server server = new MuleNetwork.Server();
    private final MuleNetwork.Client client = new MuleNetwork.Client();
    private final MuleNetwork.Client healthClient = new MuleNetwork.Client();

    private volatile boolean invalidated;
    private long cachedBankAccount;
    private int cachedBankCoins;
    private int pendingOfferAmount;
    private long nextActionMillis;

    MuleRuntime(BooleanSupplier running, BooleanSupplier varyMouseSpeed) {
        this(running, varyMouseSpeed, running);
    }

    MuleRuntime(BooleanSupplier running, BooleanSupplier varyMouseSpeed, BooleanSupplier receiving) {
        this(running, varyMouseSpeed, receiving, itemId -> Microbot.getItemManager().getItemPrice(itemId));
    }

    MuleRuntime(BooleanSupplier running, BooleanSupplier varyMouseSpeed, IntToLongFunction marketPrices) {
        this(running, varyMouseSpeed, running, marketPrices);
    }

    private MuleRuntime(BooleanSupplier running, BooleanSupplier varyMouseSpeed, BooleanSupplier receiving,
                        IntToLongFunction marketPrices) {
        this.running = running;
        this.varyMouseSpeed = varyMouseSpeed;
        this.receiving = receiving;
        this.marketPrices = marketPrices;
    }

    @Override
    public boolean allowed() {
        return !invalidated && running.getAsBoolean() && !Thread.currentThread().isInterrupted()
            && !FlipperRunGuard.scriptsPaused() && !InputArbiter.isHuman()
            && Boolean.TRUE.equals(read(() -> Microbot.getClient() != null
                && Microbot.getClient().getGameState() == GameState.LOGGED_IN
                && Microbot.getClient().getLocalPlayer() != null
                && !Microbot.getClient().isMenuOpen() && Microbot.targetMenu == null));
    }

    @Override
    public MuleSession.Snapshot read() {
        return read(() -> capture(Microbot.getClient()));
    }

    MuleSession.Snapshot capture(Client rlClient) {
        if (rlClient == null) return null;
        long profileId = configurationProfileId();
        if (rlClient.getGameState() != GameState.LOGGED_IN || rlClient.getLocalPlayer() == null) {
            cachedBankAccount = 0;
            cachedBankCoins = 0;
            return MuleSession.Snapshot.loggedOut();
        }
        long account = rlClient.getAccountHash();
        if (account != cachedBankAccount) {
            cachedBankAccount = account;
            cachedBankCoins = 0;
        }
        String playerName = rlClient.getLocalPlayer().getName();
        int currentWorld = rlClient.getWorld();
        WorldPoint location = rlClient.getLocalPlayer().getWorldLocation();
        boolean atGe = location != null && GRAND_EXCHANGE_AREA.contains(location);
        int tileX = location == null ? 0 : location.getX();
        int tileY = location == null ? 0 : location.getY();
        int tilePlane = location == null ? 0 : location.getPlane();
        ItemContainer inventory = rlClient.getItemContainer(InventoryID.INVENTORY);
        Item[] inventoryItems = inventory == null ? null : inventory.getItems();
        int invCoins = checkedCoins(inventoryItems);
        // Missing inventory is not evidence that a confirmed transfer spent the whole wallet.
        if (invCoins < 0) return null;
        CapitalEstimate capital = computeWealth(rlClient, inventoryItems);

        boolean overviewOpen = visible(rlClient.getWidget(InterfaceID.GeOffers.INDEX));
        boolean setupOpen = visible(rlClient.getWidget(InterfaceID.GeOffers.SETUP));
        boolean detailsOpen = visible(rlClient.getWidget(InterfaceID.GeOffers.DETAILS));
        boolean exchangeOpen = overviewOpen || setupOpen || detailsOpen;
        boolean offerOpen = setupOpen || detailsOpen;
        boolean bankOpen = visible(rlClient.getWidget(12, 1));

        ItemContainer bankContainer = rlClient.getItemContainer(InventoryID.BANK);
        Item[] bankItems = bankContainer == null ? null : bankContainer.getItems();
        int bankCoins = checkedCoins(bankItems);
        if (bankItems == null) {
            // An unavailable bank is an unknown lower-bound contribution, never a stale balance.
            cachedBankCoins = 0;
            capital.complete = false;
        } else if (bankCoins < 0) {
            cachedBankCoins = 0;
            capital.verified = false;
        } else {
            cachedBankCoins = bankCoins;
            capital.add(bankCoins);
        }

        boolean tradeMainOpen = tradeMainOpen(rlClient);
        boolean tradeConfirmOpen = tradeConfirmOpen(rlClient);

        String partnerName = "";
        if (tradeMainOpen) {
            Widget title = rlClient.getWidget(InterfaceID.Trademain.TITLE);
            if (visible(title)) partnerName = MulePolicy.extractTradePartner(title.getText());
        } else if (tradeConfirmOpen) {
            Widget opponent = rlClient.getWidget(InterfaceID.Tradeconfirm.TRADEOPPONENT);
            if (visible(opponent)) partnerName = MulePolicy.extractTradePartner(opponent.getText());
        }

        ItemContainer ourTrade = rlClient.getItemContainer(InventoryID.TRADE);
        int ourCoins = countCoins(ourTrade);
        boolean ourOther = hasNonCoinItems(ourTrade);
        int otherCoins = countCoins(rlClient.getItemContainer(InventoryID.TRADEOTHER));

        if (profileId != configurationProfileId()) return null;
        return new MuleSession.Snapshot(true, account, playerName, currentWorld,
            tileX, tileY, tilePlane, atGe,
            exchangeOpen, offerOpen, bankOpen, invCoins, cachedBankCoins, capital.value, capital.activeBuys,
            tradeMainOpen, tradeConfirmOpen, partnerName, ourCoins, ourOther, otherCoins,
            capital.verified, capital.complete, profileId);
    }

    private static long configurationProfileId() {
        try {
            ConfigProfile profile = Microbot.getConfigManager() == null ? null : Microbot.getConfigManager().getProfile();
            return profile == null ? MuleSession.Snapshot.UNKNOWN_CONFIGURATION_PROFILE_ID : profile.getId();
        } catch (RuntimeException unavailable) {
            return MuleSession.Snapshot.UNKNOWN_CONFIGURATION_PROFILE_ID;
        }
    }

    /** Conservative current capital, excluding trade-offered GP and historical GE fills (ClientThread only). */
    private CapitalEstimate computeWealth(Client rlClient, Item[] inventoryItems) {
        CapitalEstimate estimate = new CapitalEstimate();
        for (Item item : inventoryItems) {
            if (item == null || item.getId() <= 0 || item.getQuantity() == 0) continue;
            if (item.getQuantity() < 0) {
                estimate.verified = false;
                continue;
            }
            estimate.add(item.getId() == ItemID.COINS ? item.getQuantity()
                : marketValue(rlClient, item.getId(), item.getQuantity(), Long.MAX_VALUE, estimate));
        }
        GrandExchangeOffer[] offers = rlClient.getGrandExchangeOffers();
        if (offers == null || offers.length == 0) {
            estimate.complete = false;
            return estimate;
        }
        for (int slot = 0; slot < offers.length; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer == null || offer.getState() == null) {
                estimate.complete = false;
                continue;
            }
            GrandExchangeOfferState state = offer.getState();
            if (state == GrandExchangeOfferState.EMPTY) continue;
            int total = offer.getTotalQuantity(), filled = offer.getQuantitySold();
            long price = offer.getPrice(), spent = offer.getSpent();
            if (offer.getItemId() <= 0 || total <= 0 || filled < 0 || filled > total || price <= 0 || spent < 0
                || ((state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
                    && filled != total)) {
                estimate.verified = false;
                continue;
            }
            long remaining = (long) total - filled;
            if (state == GrandExchangeOfferState.BUYING) {
                estimate.activeBuys = true;
                try {
                    estimate.add(Math.multiplyExact(remaining, price));
                } catch (ArithmeticException overflow) {
                    estimate.verified = false;
                }
            } else if (state == GrandExchangeOfferState.SELLING) {
                long value = marketValue(rlClient, offer.getItemId(), remaining, price, estimate);
                if (remaining > 0 && value == 0) estimate.complete = false;
                estimate.add(value);
            }
            addCollectionCapital(rlClient, slot, offer, state, total, filled, price, spent, estimate);
        }
        return estimate;
    }

    private void addCollectionCapital(Client rlClient, int slot, GrandExchangeOffer offer,
                                     GrandExchangeOfferState state, int total, int filled,
                                     long price, long spent, CapitalEstimate estimate) {
        if (slot >= GE_COLLECTION_INVENTORIES.length) {
            estimate.complete = false;
            return;
        }
        ItemContainer collection = rlClient.getItemContainer(GE_COLLECTION_INVENTORIES[slot]);
        Item[] items = collection == null ? null : collection.getItems();
        if (items == null) {
            if (filled > 0 || state == GrandExchangeOfferState.CANCELLED_BUY
                || state == GrandExchangeOfferState.CANCELLED_SELL) estimate.complete = false;
            return;
        }
        long coins = 0, itemQuantity = 0;
        try {
            for (Item item : items) {
                if (item == null || item.getId() <= 0 || item.getQuantity() == 0) continue;
                if (item.getQuantity() < 0) {
                    estimate.verified = false;
                    return;
                }
                if (item.getId() == ItemID.COINS) {
                    coins = Math.addExact(coins, item.getQuantity());
                } else if (item.getId() == offer.getItemId()) {
                    itemQuantity = Math.addExact(itemQuantity, item.getQuantity());
                } else {
                    // A different item's stale container cannot establish any payout for this offer.
                    estimate.complete = false;
                    return;
                }
            }
            boolean buy = state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
                || state == GrandExchangeOfferState.CANCELLED_BUY;
            long maximumItems = buy ? filled
                : state == GrandExchangeOfferState.CANCELLED_SELL ? (long) total - filled : 0;
            if (itemQuantity > maximumItems) {
                estimate.verified = false;
                return;
            }
            if (coins > 0) {
                long maximumCoins = buy
                    ? Math.subtractExact(Math.multiplyExact(state == GrandExchangeOfferState.CANCELLED_BUY
                        ? total : filled, price), spent) : spent;
                if (coins > maximumCoins) {
                    estimate.verified = false;
                    return;
                }
            }
            // Offer counters are historical. Only the current native collection contents are assets here.
            long ceiling = buy && filled > 0 ? Math.min(price, spent / filled) : price;
            long itemValue = marketValue(rlClient, offer.getItemId(), itemQuantity, ceiling, estimate);
            if (itemQuantity > 0 && itemValue == 0) estimate.complete = false;
            estimate.add(itemValue);
            estimate.add(coins);
        } catch (ArithmeticException overflow) {
            estimate.verified = false;
        }
    }

    private long marketValue(Client rlClient, int itemId, long quantity, long ceiling, CapitalEstimate estimate) {
        if (quantity == 0) return 0;
        try {
            ItemComposition definition = rlClient.getItemDefinition(itemId);
            if (definition != null && definition.getNote() != -1) {
                itemId = definition.getLinkedNoteId();
                definition = itemId > 0 ? rlClient.getItemDefinition(itemId) : null;
            }
            if (definition == null) {
                estimate.complete = false;
                return 0;
            }
            if (!definition.isTradeable()) return 0;
            long price = marketPrices.applyAsLong(itemId);
            if (price < 0) {
                estimate.verified = false;
                return 0;
            }
            if (price == 0) {
                estimate.complete = false;
                return 0;
            }
            return Math.multiplyExact(quantity, Math.min(price, ceiling));
        } catch (ArithmeticException overflow) {
            estimate.verified = false;
            return 0;
        } catch (RuntimeException unavailable) {
            estimate.complete = false;
            return 0;
        }
    }

    private static final class CapitalEstimate {
        private long value;
        private boolean verified = true, complete = true, activeBuys;

        private void add(long amount) {
            try {
                value = Math.addExact(value, amount);
            } catch (ArithmeticException overflow) {
                verified = false;
            }
        }
    }

    private static int checkedCoins(Item[] items) {
        if (items == null) return -1;
        long coins = 0;
        for (Item item : items) {
            if (item == null || item.getId() <= 0) continue;
            if (item.getQuantity() < 0) return -1;
            if (item.getId() == ItemID.COINS) coins += item.getQuantity();
        }
        return coins > Integer.MAX_VALUE ? -1 : (int) coins;
    }

    private static int countCoins(ItemContainer container) {
        if (container == null || container.getItems() == null) return 0;
        long total = 0;
        for (Item item : container.getItems()) {
            if (item != null && item.getId() == ItemID.COINS && item.getQuantity() > 0) {
                total += item.getQuantity();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static boolean hasNonCoinItems(ItemContainer container) {
        if (container == null || container.getItems() == null) return false;
        for (Item item : container.getItems()) {
            if (item != null && item.getId() > 0 && item.getQuantity() > 0 && item.getId() != ItemID.COINS) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void prepareBoundary() {
        if (!allowed() || !readyForAction()) return;
        Control back = read(() -> control(Microbot.getClient().getWidget(InterfaceID.GeOffers.BACK), "Back"));
        invoke(back, () -> control(Microbot.getClient().getWidget(InterfaceID.GeOffers.BACK), "Back"));
    }

    @Override
    public void closeInterfaces() {
        if (!allowed()) return;
        if (Boolean.TRUE.equals(read(() -> tradeMainOpen(Microbot.getClient())
            || tradeConfirmOpen(Microbot.getClient())))) {
            // declineTrade owns the action gate; taking it here first would suppress the decline.
            declineTrade();
            return;
        }
        if (!readyForAction()) return;
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            return;
        }
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
    }

    @Override
    public void withdrawBankCoins(int needed) {
        if (needed <= 0 || !allowed() || !readyForAction()) return;
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            return;
        }
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            return;
        }
        Rs2Bank.withdrawX(false, ItemID.COINS, needed);
    }

    @Override
    public void walkTo(int x, int y, int plane) {
        if (!allowed() || !readyForAction()) return;
        Rs2Walker.walkTo(x, y, plane, 2);
    }

    @Override
    public boolean playerNear(String rsn, int tiles) {
        if (rsn == null || rsn.isEmpty()) return false;
        return Boolean.TRUE.equals(read(() -> {
            Client rl = Microbot.getClient();
            if (rl == null || rl.getLocalPlayer() == null) return false;
            WorldPoint self = rl.getLocalPlayer().getWorldLocation();
            for (net.runelite.api.Player p : rl.getPlayers()) {
                if (p == null || p == rl.getLocalPlayer() || !MulePolicy.sameRsn(p.getName(), rsn)) continue;
                WorldPoint at = p.getWorldLocation();
                if (self != null && at != null && self.getPlane() == at.getPlane()
                    && Math.max(Math.abs(self.getX() - at.getX()), Math.abs(self.getY() - at.getY())) <= tiles) {
                    return true;
                }
            }
            return false;
        }));
    }

    @Override
    public void logout() {
        if (invalidated || !running.getAsBoolean() || FlipperRunGuard.scriptsPaused() || InputArbiter.isHuman()
            || !readyForAction()) return;
        Rs2Player.logout();
    }

    @Override
    public void login(int world) {
        final Thread owner = Thread.currentThread();
        final long generation = motionGeneration.get();
        try {
            MuleLogin.login(new MuleLogin.Port() {
                public boolean allowed() { return loginAllowed(owner, generation); }
                public Object profileIdentity() {
                    ConfigProfile profile = LoginManager.getActiveProfile();
                    return profile == null ? null : profile.getId();
                }
                public MuleNetwork.Lease lease() { return server.activeLease(); }
                public int loginIndex() {
                    Integer index = read(() -> Microbot.getClient() == null ? null : Microbot.getClient().getLoginIndex());
                    return index == null ? -1 : index;
                }
                public boolean perform(MuleLogin.Step step, int expectedIndex, int targetWorld, BooleanSupplier owned) {
                    return performLoginStep(step, expectedIndex, targetWorld, owned);
                }
                public boolean pause(int millis, BooleanSupplier owned) {
                    for (int remaining = millis; remaining > 0; remaining -= 25) {
                        if (!owned.getAsBoolean()) return false;
                        try {
                            Thread.sleep(Math.min(remaining, 25));
                        } catch (InterruptedException interrupted) {
                            owner.interrupt();
                            return false;
                        }
                    }
                    return owned.getAsBoolean();
                }
            }, world);
        } catch (RuntimeException unavailable) {
            // Login is optional. Unknown state cannot authorize another credential write or input.
        }
    }

    private boolean loginAllowed(Thread owner, long generation) {
        Client rl = Microbot.getClient();
        return !invalidated && motionGeneration.get() == generation && !owner.isInterrupted()
            && running.getAsBoolean() && receiving.getAsBoolean() && !FlipperRunGuard.scriptsPaused()
            && !InputArbiter.isHuman() && !LoginManager.isLoginAttemptActive() && rl != null
            && rl.getGameState() == GameState.LOGIN_SCREEN && !rl.isMenuOpen() && Microbot.targetMenu == null;
    }

    private boolean performLoginStep(MuleLogin.Step step, int expectedIndex, int world, BooleanSupplier owned) {
        if (step == MuleLogin.Step.DISMISS || step == MuleLogin.Step.CONTINUE) {
            Point point = read(() -> {
                Client rl = Microbot.getClient();
                if (!owned.getAsBoolean() || rl.getLoginIndex() != expectedIndex) return null;
                int y = step == MuleLogin.Step.DISMISS ? 308 : expectedIndex == 10 ? 250 : 300;
                return new Point(rl.getCanvasWidth() / 2 - 402 + 365, y);
            });
            return point != null && mouseMotion.clickValidated(point, varyMouseSpeed.getAsBoolean(), owned,
                () -> Boolean.TRUE.equals(read(() -> owned.getAsBoolean()
                    && Microbot.getClient().getLoginIndex() == expectedIndex
                    && Microbot.getClient().getCanvasWidth() / 2 - 402 + 365 == point.getX())));
        }
        return Boolean.TRUE.equals(read(() -> {
            Client rl = Microbot.getClient();
            // This check also guards a client-thread callback that executes after its worker was cancelled.
            if (!owned.getAsBoolean() || rl.getLoginIndex() != expectedIndex) return false;
            if (step == MuleLogin.Step.WORLD) {
                // The public method returns void in the minimum SDK and boolean in newer clients.
                // Reflect only this public operation so one JAR links against both binary signatures.
                try {
                    LoginManager.class.getMethod("setWorld", int.class).invoke(null, world);
                } catch (ReflectiveOperationException unavailable) {
                    return false;
                }
                return owned.getAsBoolean() && rl.getWorld() == world;
            }
            if (step == MuleLogin.Step.CREDENTIALS) {
                ConfigProfile profile = LoginManager.getActiveProfile();
                if (profile == null || !owned.getAsBoolean()) return false;
                String username = profile.getName();
                String encrypted = profile.getPassword();
                if (username == null || username.isBlank() || encrypted == null || encrypted.isBlank()) return false;
                String password;
                try {
                    password = Encryption.decrypt(encrypted);
                } catch (Exception unavailable) {
                    return false;
                }
                if (!owned.getAsBoolean() || rl.getLoginIndex() != expectedIndex) return false;
                rl.setUsername(username);
                if (!owned.getAsBoolean()) return false;
                rl.setPassword(password);
                return true;
            }
            Rs2Keyboard.enter();
            return true;
        }));
    }

    @Override
    public int profileWorld() {
        try {
            net.runelite.client.config.ConfigProfile profile = LoginManager.getActiveProfile();
            Integer selected = profile == null ? null : profile.getSelectedWorld();
            return selected == null || selected <= 0 ? 0 : selected;
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    @Override
    public boolean hopToWorld(int world) {
        if (world <= 0 || !allowed() || !readyForAction()) return false;
        return Microbot.hopToWorld(world);
    }

    @Override
    public boolean tradePlayer(String rsn) {
        if (rsn == null || rsn.isEmpty() || !allowed() || !readyForAction()) return false;
        Rs2PlayerModel target = Rs2Player.getPlayers(p -> p != null && MulePolicy.sameRsn(p.getName(), rsn))
            .findFirst().orElse(null);
        return target != null && Rs2Player.trade(target);
    }

    @Override
    public boolean offerCoins(int amount, int inventoryCoins) {
        if (amount <= 0 || !allowed() || !readyForAction()) return false;
        if (pendingOfferAmount > 0 && Boolean.TRUE.equals(read(this::isEnterAmountPromptOpen))) {
            int value = pendingOfferAmount;
            Boolean set = read(() -> {
                if (!isEnterAmountPromptOpen()) return false;
                Widget input = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
                input.setText(value + "*");
                Microbot.getClient().setVarcStrValue(359, String.valueOf(value));
                return true;
            });
            if (Boolean.TRUE.equals(set) && allowed()
                && Boolean.TRUE.equals(read(() -> pendingOfferAmount == value && isEnterAmountPromptOpen()
                    && String.valueOf(value).equals(Microbot.getClient().getVarcStrValue(359))))
                && allowed()) {
                Rs2Keyboard.enter();
                pendingOfferAmount = 0;
                return true;
            }
            return false;
        }
        String option = amount >= inventoryCoins ? "Offer-All" : "Offer-X";
        Control offerControl = read(() -> findTradeSideCoinsControl(option));
        if (offerControl == null) return false;
        boolean clicked = invoke(offerControl, () -> findTradeSideCoinsControl(option));
        if (clicked && "Offer-X".equalsIgnoreCase(option)) {
            pendingOfferAmount = amount;
        }
        return clicked;
    }

    private boolean isEnterAmountPromptOpen() {
        Client rl = Microbot.getClient();
        return rl != null && visible(rl.getWidget(InterfaceID.Trademain.WHOLESCREEN))
            && visible(rl.getWidget(InterfaceID.Chatbox.MES_TEXT2))
            && (rl.getVarcIntValue(5) == 7 || rl.getVarcIntValue(5) == 30);
    }

    private Control findTradeSideCoinsControl(String expectedAction) {
        Client rl = Microbot.getClient();
        if (rl == null) return null;
        Widget side = rl.getWidget(InterfaceID.Tradeside.SIDE_LAYER);
        if (!visible(side)) return null;
        Widget[] children = side.getDynamicChildren();
        if (children == null || children.length == 0) children = side.getChildren();
        if (children == null) return null;
        for (Widget child : children) {
            if (visible(child) && child.getItemId() == ItemID.COINS && child.getItemQuantity() > 0) {
                Control ctrl = control(child, expectedAction);
                if (ctrl != null) return ctrl;
            }
        }
        return null;
    }

    @Override
    public boolean acceptTradeMain() {
        if (!allowed() || !readyForAction()) return false;
        Control accept = read(() -> control(Microbot.getClient().getWidget(InterfaceID.Trademain.ACCEPT), "Accept"));
        return invoke(accept, () -> control(Microbot.getClient().getWidget(InterfaceID.Trademain.ACCEPT), "Accept"));
    }

    @Override
    public boolean acceptTradeConfirm() {
        if (!allowed() || !readyForAction()) return false;
        Control accept = read(() -> control(Microbot.getClient().getWidget(InterfaceID.Tradeconfirm.TRADE2ACCEPT), "Accept"));
        return invoke(accept, () -> control(Microbot.getClient().getWidget(InterfaceID.Tradeconfirm.TRADE2ACCEPT), "Accept"));
    }

    @Override
    public boolean declineTrade() {
        if (!allowed() || !readyForAction()) return false;
        Control decline = read(() -> {
            Client rl = Microbot.getClient();
            Control main = control(rl.getWidget(InterfaceID.Trademain.DECLINE), "Decline");
            return main != null ? main : control(rl.getWidget(InterfaceID.Tradeconfirm.TRADE2DECLINE), "Decline");
        });
        return invoke(decline, () -> {
            Client rl = Microbot.getClient();
            Control main = control(rl.getWidget(InterfaceID.Trademain.DECLINE), "Decline");
            return main != null ? main : control(rl.getWidget(InterfaceID.Tradeconfirm.TRADE2DECLINE), "Decline");
        });
    }

    @Override
    public MuleNetwork.MuleReply requestMuleLease(String host, int port, String muleRsn, String flipperRsn, int amountGp) {
        return client.requestLease(host, port, muleRsn, flipperRsn, amountGp);
    }

    @Override
    public MuleNetwork.MuleReply heartbeatMuleLease(String host, int port, String muleRsn, String flipperRsn, int amountGp) {
        return client.heartbeat(host, port, muleRsn, flipperRsn, amountGp);
    }

    @Override
    public void releaseMuleLease(String host, int port, String muleRsn, String flipperRsn, boolean completed) {
        client.releaseLease(host, port, muleRsn, flipperRsn, completed);
    }

    @Override
    public synchronized boolean ensureServer(int port) {
        return !Thread.currentThread().isInterrupted() && receiving.getAsBoolean() && server.ensureStarted(port);
    }

    MuleNetwork.MuleReply probeMuleHealth(String expectedMule, String ownRsn, int port) {
        return healthClient.probeHealth("auto", port, expectedMule, ownRsn);
    }

    MuleNetwork.ServerHealth muleServerHealth() {
        return server.health();
    }

    @Override
    public void updateServerState(boolean ready, String muleRsn, int world, int x, int y, int plane) {
        server.updateMuleState(ready, muleRsn, world, x, y, plane);
    }

    @Override
    public MuleNetwork.Lease activeServerLease() {
        return server.activeLease();
    }

    @Override
    public void clearServerLease(MuleNetwork.Lease expected) {
        server.clearLease(expected);
    }

    @Override
    public synchronized void stopServer() {
        server.close();
    }

    @Override
    public void reset() {
        invalidated = true;
        motionGeneration.incrementAndGet();
        invalidated = false;
        pendingOfferAmount = 0;
        nextActionMillis = 0;
    }

    private boolean readyForAction() {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        if (now < nextActionMillis) return false;
        nextActionMillis = now + 900;
        return true;
    }

    private Rectangle bounds(Widget widget) {
        if (!visible(widget)) return null;
        Rectangle bounds = widget.getBounds();
        if (bounds == null || bounds.width <= 1 || bounds.height <= 1 || bounds.x < 0 || bounds.y < 0
            || (long) bounds.x + bounds.width > Microbot.getClient().getCanvasWidth()
            || (long) bounds.y + bounds.height > Microbot.getClient().getCanvasHeight()) return null;
        return new Rectangle(bounds);
    }

    private Control control(Widget widget, String expectedAction) {
        Rectangle bounds = bounds(widget);
        String[] actions = widget == null ? null : widget.getActions();
        if (bounds == null || actions == null) return null;
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] == null) continue;
            String action = Rs2UiHelper.stripColTags(actions[i]).trim();
            if (!action.isEmpty() && (expectedAction == null || expectedAction.equalsIgnoreCase(action))) {
                int itemId = widget.getItemId() > 0 ? widget.getItemId() : -1;
                return new Control(widget.getId(), widget.getIndex(), i + 1, action, bounds, itemId);
            }
        }
        return null;
    }

    private boolean invoke(Control target, Callable<Control> refresh) {
        if (target == null || !allowed()) return false;
        Control current = read(refresh);
        if (!target.same(current) || !allowed()) return false;
        NewMenuEntry entry = new NewMenuEntry().option(current.action).target("")
            .identifier(current.operation).type(MenuAction.CC_OP).param0(current.index)
            .param1(current.id).itemId(current.itemId).forceLeftClick(false);
        boolean varying = varyMouseSpeed.getAsBoolean();
        BooleanSupplier guard = motionGuard(varying);
        Point point = clickingPoint(current.bounds);
        return point != null && mouseMotion.invokeValidated(entry, point, varying, guard,
            () -> Boolean.TRUE.equals(read(() -> allowed() && target.same(refresh.call()))));
    }

    private static Point clickingPoint(Rectangle bounds) {
        Point point = Rs2UiHelper.getClickingPoint(bounds, true);
        return point != null && bounds.contains(point.getX(), point.getY()) ? point : null;
    }

    private BooleanSupplier motionGuard(boolean varying) {
        long expectedGeneration = motionGeneration.get();
        return () -> !invalidated && motionGeneration.get() == expectedGeneration
            && running.getAsBoolean() && !Thread.currentThread().isInterrupted()
            && !FlipperRunGuard.scriptsPaused() && !InputArbiter.isHuman()
            && Microbot.isLoggedIn() && varyMouseSpeed.getAsBoolean() == varying;
    }

    private static final class Control {
        final int id, index, operation, itemId;
        final String action;
        final Rectangle bounds;

        Control(int id, int index, int operation, String action, Rectangle bounds, int itemId) {
            this.id = id;
            this.index = index;
            this.operation = operation;
            this.action = action;
            this.bounds = bounds;
            this.itemId = itemId;
        }

        boolean same(Control other) {
            return other != null && id == other.id && index == other.index && operation == other.operation
                && itemId == other.itemId && action.equals(other.action) && bounds.equals(other.bounds);
        }
    }

    private static boolean visible(Widget widget) {
        return widget != null && !widget.isHidden();
    }

    private static boolean tradeMainOpen(Client client) {
        return client != null && (visible(client.getWidget(InterfaceID.Trademain.WHOLESCREEN))
            || visible(client.getWidget(InterfaceID.Trademain.FRAME))
            || visible(client.getWidget(InterfaceID.Trademain.ACCEPT)));
    }

    private static boolean tradeConfirmOpen(Client client) {
        return client != null && (visible(client.getWidget(InterfaceID.Tradeconfirm.ROOT_RECT0))
            || visible(client.getWidget(InterfaceID.Tradeconfirm.FRAME))
            || visible(client.getWidget(InterfaceID.Tradeconfirm.TRADE2ACCEPT)));
    }

    private static <T> T read(Callable<T> call) {
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(call).orElse(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
