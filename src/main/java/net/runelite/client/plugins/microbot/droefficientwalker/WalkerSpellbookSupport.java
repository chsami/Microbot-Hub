package net.runelite.client.plugins.microbot.droefficientwalker;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;

/** Script-requested service. No activity discovery, global mouse settings, or AIO host is imported. */
final class WalkerSpellbookSupport
{
    static final WorldPoint RIMMINGTON_HOUSE_APPROACH = new WorldPoint(2954, 3223, 0);
    interface Travel
    {
        void walk(WorldPoint point);
        void bank();
        void stop();
        EfficientWalker.Status status();
    }

    private final Client client;
    private final EfficientWalkerConfig config;
    private final Travel travel;
    private volatile Session session;
    private ExecutorService executor;
    private Future<?> task;

    WalkerSpellbookSupport(Client client, EfficientWalkerConfig config, Travel travel)
    {
        this.client = client;
        this.config = config;
        this.travel = travel;
    }

    synchronized EfficientWalker.SpellbookStatus request(Rs2Spellbook target)
    {
        Objects.requireNonNull(target, "target");
        if (session != null && session.target == target) return session.state;
        if (!config.autoSpellbook()) return EfficientWalker.SpellbookStatus.DISABLED;
        cancel();
        Session next = new Session(target);
        session = next;
        return next.state;
    }

    boolean matches(Rs2Spellbook target) { Session current = session; return current != null && current.target == target; }
    boolean active() { Session current = session; return current != null && current.state == EfficientWalker.SpellbookStatus.PREPARING; }
    EfficientWalker.SpellbookStatus state()
    {
        Session current = session;
        return current == null ? EfficientWalker.SpellbookStatus.IDLE : current.state;
    }
    String status() { Session current = session; return current == null ? "" : current.message; }

    /** Called by the existing client tick; game reads/input and waits execute on their proper threads. */
    synchronized void tick()
    {
        Session current = session;
        if (current == null || current.state != EfficientWalker.SpellbookStatus.PREPARING) return;
        if (!config.autoSpellbook())
        {
            cancel();
            travel.stop();
            return;
        }
        long now = System.currentTimeMillis();
        boolean held = Microbot.pauseAllScripts.get() || InputArbiter.isHuman()
            || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
            || client.getVarbitValue(542) != 0;
        if (held)
        {
            if (current.heldAt == 0) current.heldAt = now;
            return;
        }
        if (task != null && !task.isDone()) return;
        if (current.heldAt != 0)
        {
            current.flow.resumeAfter(now - current.heldAt);
            current.delayAfterHold(now - current.heldAt);
            current.heldAt = 0;
        }
        if (executor == null || executor.isShutdown())
            executor = WalkerMouseControl.profiledExecutor(Executors.newSingleThreadExecutor(job -> {
                Thread thread = new Thread(job, "EfficientWalker-spellbook");
                thread.setDaemon(true);
                return thread;
            }));
        task = executor.submit(() -> advance(current, now));
    }

    private void advance(Session current, long now)
    {
        try
        {
            current.input();
            boolean ready = current.flow.tick(now);
            if (session != current) return;
            current.message = current.flow.status();
            if (ready) {
                if (current.leaveHostedWorld()) current.state = EfficientWalker.SpellbookStatus.READY;
                else current.message = "Spellbook confirmed; leaving crowded house world";
            }
        }
        catch (SpellbookPreparation.InputHeld ignored) { }
        catch (SpellbookHouseExit.ExitPending pending)
        {
            if (session != current || Thread.currentThread().isInterrupted()) return;
            current.message = pending.getMessage();
        }
        catch (RuntimeException failure)
        {
            if (session != current || Thread.currentThread().isInterrupted()) return;
            current.message = failure.getMessage() == null ? "Spellbook preparation failed" : failure.getMessage();
            current.read(() -> { travel.stop(); return true; });
            current.state = EfficientWalker.SpellbookStatus.BLOCKED;
        }
    }

    synchronized void cancel()
    {
        session = null;
        if (task != null) task.cancel(true);
        task = null;
    }

    synchronized void shutdown()
    {
        cancel();
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    private final class Session implements SpellbookPreparation.Port
    {
        private final Rs2Spellbook target;
        private final SpellbookPreparation flow;
        private volatile EfficientWalker.SpellbookStatus state = EfficientWalker.SpellbookStatus.PREPARING;
        private volatile String message;
        private long heldAt, hostHopAt, hostInputAt, hostEnteredAt, altarRequestedAt, withdrawAt, withdrawnAt, ownEntryAt;
        private int ownEntryId;
        private boolean bankOpening;
        private int bankEpoch;
        private WorldPoint leg;
        private boolean bankLeg;
        private final SpellbookHouseExit houseExit = new SpellbookHouseExit();
        private final Set<String> failedHosts = new HashSet<>();
        private String selectedHost;

        Session(Rs2Spellbook target)
        {
            this.target = target;
            flow = new SpellbookPreparation(this, target, System.currentTimeMillis());
            message = flow.status();
        }

        void delayAfterHold(long duration)
        {
            if (hostHopAt > 0) hostHopAt += duration;
            if (hostInputAt > 0) hostInputAt += duration;
            if (hostEnteredAt > 0) hostEnteredAt += duration;
            if (altarRequestedAt > 0) altarRequestedAt += duration;
            if (withdrawAt > 0) withdrawAt += duration;
            if (withdrawnAt > 0) withdrawnAt += duration;
            if (ownEntryAt > 0) ownEntryAt += duration;
            houseExit.resumeAfter(duration);
        }

        private <T> T read(Supplier<T> supplier)
        {
            if (session != this || Thread.currentThread().isInterrupted()) throw new SpellbookPreparation.InputHeld();
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                if (session != this || Thread.currentThread().isInterrupted()) throw new SpellbookPreparation.InputHeld();
                return java.util.Optional.ofNullable(supplier.get());
            }).orElseThrow(SpellbookPreparation.InputHeld::new).orElse(null);
        }

        private void input()
        {
            if (Microbot.pauseAllScripts.get() || InputArbiter.isHuman()
                || !read(() -> client.getGameState() == GameState.LOGGED_IN
                    && client.getLocalPlayer() != null && client.getVarbitValue(542) == 0))
                throw new SpellbookPreparation.InputHeld();
        }

        /** One setting owns the whole flow: cape, own POH, world 330 house, then the public altar. */
        public boolean preferHostedHouse() { return config.autoSpellbook(); }
        public long houseProgress()
        {
            return read(() -> Objects.hash(Rs2Player.getWorldLocation(), client.isInInstancedRegion(),
                Rs2Inventory.all().stream().mapToLong(item -> item.getId() * 31L + item.getQuantity()).sum()));
        }
        public void nextHostedHouse()
        {
            if (selectedHost != null) failedHosts.add(selectedHost);
            selectedHost = null;
            hostEnteredAt = 0;
            hostInputAt = 0;
            altarRequestedAt = 0;
            houseExit.reset();
        }
        public boolean returnToExchange()
        {
            if (!leaveHostedHouse()) return false;
            WorldPoint exchange = new WorldPoint(3165, 3487, 0);
            if (arrived(exchange)) { cancelTravel(); return true; }
            walk(exchange);
            return false;
        }
        public boolean ownHouseEnabled() { return config.autoSpellbook(); }
        private boolean usedHostedHouse;
        private int returnWorld;
        private long returnHopAt;
        private boolean leaveHostedWorld() {
            if(!usedHostedHouse)return true;
            if(read(client::getGameState)!=GameState.LOGGED_IN)return false;
            if(read(client::getWorld)!=330)return true;
            if(!closeBank())return false;
            if(returnWorld==0){
                var service=Microbot.getWorldService();var worlds=service==null?null:service.getWorlds();
                var selected=WalkerSpellbookWorld.select(worlds==null?null:worlds.getWorlds(),new java.util.Random());
                if(selected.isEmpty())return false;
                returnWorld=selected.getAsInt();
            }
            long now=System.currentTimeMillis();
            if(now>=returnHopAt){input();returnHopAt=now+12_000;Microbot.hopToWorld(returnWorld);}
            return false;
        }
        public boolean canUseCape() {return read(()->client.getRealSkillLevel(net.runelite.api.Skill.MAGIC)>=99);}

        public Rs2Spellbook current() { return read(() -> Rs2Spellbook.fromValue(client.getVarbitValue(net.runelite.api.gameval.VarbitID.SPELLBOOK))); }
        public boolean unlocked(Rs2Spellbook book) { return read(book::isUnlocked); }

        private int carriedCape()
        {
            if(!canUseCape())return 0;
            return read(() -> {
                for (int id : new int[]{ItemID.MAGIC_CAPE, ItemID.MAGIC_CAPET})
                    if (Rs2Inventory.hasItem(id) || Rs2Equipment.isWearing(id)) return id;
                return 0;
            });
        }

        public boolean bankReady()
        {
            boolean personalHouse = ownHouseEnabled();
            if (!canUseCape() && !personalHouse) return true;
            // A carried cape is the first route; otherwise a carried house entry is enough.
            if (carriedCape() != 0) return true;
            if (personalHouse && (inHouse() || houseEntry(false) != 0)) return true;
            if (!config.useItemsFromBank()) return true;
            boolean open = read(Rs2Bank::isOpen);
            if (open)
            {
                if (!read(() -> Rs2Bank.verifyBankMirrorAfterOpen(!bankOpening, bankEpoch))) return false;
                // Magic cape first; without one, withdraw the house entry for the POH altar.
                int magicCape = availableCape();
                if (magicCape != 0) return withdrawCape(magicCape);
                if (personalHouse)
                {
                    if (ownEntryId == 0) ownEntryId = houseEntry(true);
                    if (ownEntryId != 0) return withdrawCape(ownEntryId);
                }
                return true;
            }
            if (!read(() -> Rs2Bank.isNearBank(8)))
            {
                input();
                if (bankLeg && travel.status() == EfficientWalker.Status.BLOCKED)
                    throw new IllegalStateException("No reachable bank for spellbook preparation");
                if (!bankLeg) { travel.bank(); bankLeg = true; leg = null; }
                return false;
            }
            cancelTravel();
            bankEpoch = read(Rs2Bank::getBankLiveEpoch);
            bankOpening = true;
            input();
            Rs2Bank.openBank();
            return false;
        }

        public int availableCape()
        {
            if(!canUseCape())return 0;
            int carried = carriedCape();
            if (carried != 0 || !config.useItemsFromBank() || !read(Rs2Bank::isOpen)) return carried;
            return read(() -> {
                for (int id : new int[]{ItemID.MAGIC_CAPE, ItemID.MAGIC_CAPET})
                    if (Rs2Bank.bankItems().stream().anyMatch(item -> item.getId() == id && item.getQuantity() > 0)) return id;
                return 0;
            });
        }

        public boolean withdrawCape(int id)
        {
            if (read(() -> Rs2Inventory.hasItem(id) || Rs2Equipment.isWearing(id))) return true;
            if (read(Rs2Inventory::isFull)) throw new IllegalStateException("One free inventory slot is needed for the spellbook travel item");
            long now = System.currentTimeMillis();
            if (withdrawnAt != 0)
            {
                if (now - withdrawnAt > 10_000) throw new IllegalStateException("Spellbook travel item withdrawal was not observed");
                return false;
            }
            if (now < withdrawAt) return false;
            input();
            withdrawAt = now + 3000;
            if (Rs2Bank.setWithdrawAsItem() && Rs2Bank.withdrawOne(id)) withdrawnAt = now;
            return false;
        }

        public boolean closeBank()
        {
            if (!read(Rs2Bank::isOpen)) return true;
            input();
            Rs2Bank.closeBank();
            return false;
        }

        public void cape(int id, Rs2Spellbook book)
        {
            input();
            String choice = choice(book);
            if (read(() -> Rs2Dialogue.hasDialogueOption(choice))) Rs2Dialogue.clickOption(choice);
            else if (read(() -> Rs2Equipment.isWearing(id))) Rs2Equipment.interact(id, "Spellbook");
            else Rs2Inventory.interact(id, "Spellbook");
        }

        public boolean arrived(WorldPoint point)
        {
            return read(() -> {
                WorldPoint here = Rs2Player.getWorldLocation();
                return here != null && here.getPlane() == point.getPlane() && here.distanceTo(point) <= 6;
            });
        }

        public void walk(WorldPoint point)
        {
            input();
            if (point.equals(leg) && travel.status() == EfficientWalker.Status.BLOCKED)
                throw new IllegalStateException("No route to the spellbook switch");
            if (!point.equals(leg)) { travel.walk(point); leg = point; bankLeg = false; }
        }

        public void cancelTravel()
        {
            input();
            if (leg != null || bankLeg) read(() -> { travel.stop(); return true; });
            leg = null;
            bankLeg = false;
        }

        private int houseEntry(boolean includeBank)
        {
            return read(() -> {
                int[] ids = Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 0
                    ? new int[]{8013} : new int[]{9789, 9790, 8013};
                for (int id : ids)
                    if (Rs2Inventory.hasItem(id) || Rs2Equipment.isWearing(id)
                        || includeBank && Rs2Bank.bankItems().stream().anyMatch(item -> item.getId() == id && item.getQuantity() > 0)) return id;
                return 0;
            });
        }

        public SpellbookPreparation.HouseStep ownHouse(Rs2Spellbook book)
        {
            input();
            if (inHouse()) return useHouseAltar(book);
            long now = System.currentTimeMillis();
            if (ownEntryAt != 0)
            {
                // A teleport configured to land outside uses only this account's actual entrance.
                var entrance = read(() -> Microbot.getRs2TileObjectCache().query().toList().stream()
                    .filter(object -> MaxPohSupport.currentEntrance(object.getId(), object.getWorldLocation(), MaxPohSupport.currentHouse()))
                    .findFirst().orElse(null));
                if (entrance != null && now >= hostInputAt)
                {
                    hostInputAt = now + 3000;
                    input();
                    entrance.click("Home");
                }
                return now - ownEntryAt < 15_000 ? SpellbookPreparation.HouseStep.IN_PROGRESS : SpellbookPreparation.HouseStep.UNAVAILABLE;
            }
            if (!config.useTeleportations() || read(Rs2Player::isTeleBlocked)
                || read(() -> Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 20))
                return SpellbookPreparation.HouseStep.UNAVAILABLE;
            int id = houseEntry(false);
            if (id == 0) return SpellbookPreparation.HouseStep.UNAVAILABLE;
            cancelTravel();
            ownEntryAt = now;
            input();
            if (read(() -> Rs2Equipment.isWearing(id))) Rs2Equipment.interact(id, "Tele to POH");
            else Rs2Inventory.interact(id, id == 8013 ? "Break" : "Tele to POH");
            return SpellbookPreparation.HouseStep.IN_PROGRESS;
        }

        private SpellbookPreparation.HouseStep useHouseAltar(Rs2Spellbook book)
        {
            long now = System.currentTimeMillis();
            cancelTravel();
            // POH cache coordinates may be templates. Presence is independent of world distance.
            if (read(() -> Rs2Dialogue.hasDialogueOption(choice(book)))) { input(); Rs2Dialogue.clickOption(choice(book)); return SpellbookPreparation.HouseStep.IN_PROGRESS; }
            Rs2Spellbook currentBook = current();
            String[] chosen = new String[1];
            var altar = read(() -> Microbot.getRs2TileObjectCache().query().toList().stream()
                .filter(object -> PohSpellbookAltar.IDS.contains(object.getId()))
                .filter(object -> {
                    // Instance scenes: compare scene levels, not world/template planes.
                    if (client.getLocalPlayer() == null || object.getPlane() != client.getTopLevelWorldView().getPlane()) return false;
                    ObjectComposition definition = object.getObjectComposition();
                    if (definition.getImpostorIds() != null && definition.getImpostor() != null) definition = definition.getImpostor();
                    return PohSpellbookAltar.action(object.getId(), definition.getActions(), currentBook, book) != null;
                }).min(Comparator.comparingInt(this::houseDistance)).orElse(null));
            if (hostEnteredAt == 0) hostEnteredAt = now;
            if (altar == null)
            {
                if (now - hostEnteredAt < 10_000) return SpellbookPreparation.HouseStep.IN_PROGRESS;
                Microbot.log("[EfficientWalker] POH altar unavailable: no altar on this floor offers " + book + " from " + currentBook);
                return SpellbookPreparation.HouseStep.UNAVAILABLE;
            }
            chosen[0] = read(() -> {
                ObjectComposition definition = altar.getObjectComposition();
                if (definition.getImpostorIds() != null && definition.getImpostor() != null) definition = definition.getImpostor();
                return PohSpellbookAltar.action(altar.getId(), definition.getActions(), currentBook, book);
            });
            if (chosen[0] == null) return SpellbookPreparation.HouseStep.UNAVAILABLE;
            if (altarRequestedAt > 0)
            {
                if (now - altarRequestedAt < 15_000) return SpellbookPreparation.HouseStep.IN_PROGRESS;
                Microbot.log("[EfficientWalker] POH altar unavailable: " + chosen[0] + " did not change the spellbook to " + book);
                return SpellbookPreparation.HouseStep.UNAVAILABLE;
            }
            if (now >= hostInputAt) {
                hostInputAt = now + 3000; input();
                if (altar.click(chosen[0])) altarRequestedAt = System.currentTimeMillis();
            }
            return SpellbookPreparation.HouseStep.IN_PROGRESS;
        }

        public SpellbookPreparation.HouseStep hostedHouse(Rs2Spellbook book)
        {
            usedHostedHouse=true;
            input();
            long now = System.currentTimeMillis();
            if (read(client::getWorld) != 330)
            {
                if (read(() -> MotherlodeTransports.inMine(Rs2Player.getWorldLocation())))
                {
                    if (!closeBank()) return SpellbookPreparation.HouseStep.IN_PROGRESS;
                    walk(MotherlodeTransports.CAVE_APPROACH);
                    return SpellbookPreparation.HouseStep.IN_PROGRESS;
                }
                cancelTravel();
                if (!closeBank()) return SpellbookPreparation.HouseStep.IN_PROGRESS;
                if (now >= hostHopAt) { hostHopAt = now + 12_000; input(); Microbot.hopToWorld(330); }
                return SpellbookPreparation.HouseStep.IN_PROGRESS;
            }
            if (inHouse())
            {
                return useHouseAltar(book);
            }
            if (!arrived(RIMMINGTON_HOUSE_APPROACH))
            {
                walk(RIMMINGTON_HOUSE_APPROACH);
                return SpellbookPreparation.HouseStep.IN_PROGRESS;
            }
            cancelTravel();
            if (now < hostInputAt) return SpellbookPreparation.HouseStep.IN_PROGRESS;
            hostInputAt = now + 3000;
            WidgetAction enter = read(() -> hostedHouseEntry(client.getWidget(InterfaceID.PohBoard.BUTTON),
                client.getWidget(InterfaceID.PohBoard.NAME), failedHosts));
            if (enter != null) { selectedHost = enter.host; dispatch(enter); }
            else
            {
                var board = read(() -> Microbot.getRs2TileObjectCache().query().withId(29091).nearestOnClientThread());
                if (board != null) { input(); board.click("View"); }
            }
            return SpellbookPreparation.HouseStep.IN_PROGRESS;
        }

        public boolean leaveHostedHouse()
        {
            input();
            long now = System.currentTimeMillis();
            if (!inHouse())
            {
                WidgetAction close = read(() -> widgetAction(client.getWidget(InterfaceID.PohBoard.FRAME), "Close"));
                if (close != null && now >= hostInputAt) { hostInputAt = now + 3000; dispatch(close); }
                if (close == null) { hostEnteredAt = 0; altarRequestedAt = 0; }
                houseExit.reset();
                return close == null;
            }
            String control = read(net.runelite.client.plugins.microbot.droefficientwalker.support.HouseExitControls::next);
            return houseExit.step(new SpellbookHouseExit.Port() {
                public boolean inside() { return true; }
                public boolean busy() { return read(() -> Rs2Player.isMoving() || Rs2Player.isAnimating()); }
                public int floor() { return 0; }
                public String exitKey() { return "controls:" + control; }
                public boolean click() {
                    input();
                    return net.runelite.client.plugins.microbot.droefficientwalker.support.HouseExitControls.click(control);
                }
            }, now);
        }

        private int houseDistance(net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel object)
        {
            var player = client.getLocalPlayer();
            return player == null || player.getLocalLocation() == null || object.getLocalLocation() == null
                ? Integer.MAX_VALUE : object.getLocalLocation().distanceTo(player.getLocalLocation());
        }

        private boolean inHouse()
        {
            return read(() -> MaxPohSupport.inHouse() || Rs2Player.isInPoh()
                || client.isInInstancedRegion() && Microbot.getRs2TileObjectCache() != null
                    && Microbot.getRs2TileObjectCache().getStream().anyMatch(object -> {
                        int id = object.getId();
                        return id == 4525 || id == 13344 || id == 29150 || id >= 13328 && id <= 13330
                            || id >= 13497 && id <= 13505 || id >= 15332 && id <= 15341
                            || id == 15380 || id == 15381 || id == 15390 || id == 15391;
                    }));
        }

        private void dispatch(WidgetAction action)
        {
            input();
            // Use the same scoped Walker mouse path as its other exact menu interactions.
            Microbot.doInvoke(new NewMenuEntry().option(action.option).target("").identifier(action.identifier)
                .type(MenuAction.CC_OP).param0(action.index).param1(action.widgetId).itemId(-1).forceLeftClick(false), action.bounds);
        }

        public void altar(Rs2Spellbook book)
        {
            input();
            if (read(Rs2Dialogue::hasContinue)) { Rs2Dialogue.clickContinue(); return; }
            Rs2Spellbook source = book == Rs2Spellbook.MODERN ? current() : book;
            boolean tyss = source == Rs2Spellbook.ARCEUUS;
            String option = tyss ? (book == Rs2Spellbook.MODERN
                ? "I'd like to stop using your spellbook now." : "Can I try the magicks myself?") : choice(book);
            if (read(() -> Rs2Dialogue.hasDialogueOption(option))) { Rs2Dialogue.clickOption(option); return; }
            if (tyss)
            {
                var npc = read(() -> Microbot.getRs2NpcCache().query().withName("Tyss").nearestOnClientThread());
                if (npc != null) { input(); npc.click("Spellbook"); }
            }
            else if (source.getGameID() != null)
            {
                var altar = read(() -> Microbot.getRs2TileObjectCache().query().withId(source.getGameID()).nearestOnClientThread());
                String action = altar == null ? null : read(() -> Arrays.stream(altar.getObjectComposition().getActions())
                    .filter(Objects::nonNull).filter(text -> text.toLowerCase(java.util.Locale.ROOT).startsWith("pray")).findFirst().orElse(null));
                if (action != null) { input(); altar.click(action); }
            }
        }
    }

    static String choice(Rs2Spellbook book)
    {
        switch (book)
        {
            case MODERN: return "Standard";
            case ANCIENT: return "Ancient";
            case LUNAR: return "Lunar";
            case ARCEUUS: return "Arceuus";
            default: throw new IllegalStateException("Unsupported spellbook: " + book);
        }
    }

    static final class WidgetAction
    {
        final int widgetId, index, identifier;
        final Rectangle bounds;
        final String option, host;
        WidgetAction(Widget widget, int identifier, String option) { this(widget, identifier, option, ""); }
        WidgetAction(Widget widget, int identifier, String option, String host)
        {
            this.host = host;
            widgetId = widget.getId();
            index = widget.getIndex();
            bounds = new Rectangle(widget.getBounds());
            this.identifier = identifier;
            this.option = option;
        }
    }

    static WidgetAction hostedHouseEntry(Widget root, Widget names, Set<String> excluded)
    {
        if (root == null || root.isHidden()) return null;
        String[] actions = root.getActions();
        Rectangle bounds = root.getBounds();
        if (actions != null && bounds != null && bounds.width > 0 && bounds.height > 0)
            for (int i = 0; i < actions.length; i++) if ("Enter House".equalsIgnoreCase(actions[i]))
            {
                Widget name = names == null || root.getIndex() < 0 ? null : names.getChild(root.getIndex());
                String host = name == null ? "" : Objects.toString(name.getText(), "").replaceAll("<[^>]*>", "").trim().toLowerCase(java.util.Locale.ROOT);
                if (host.isEmpty()) host = "row:" + root.getId() + ":" + root.getIndex();
                if (!excluded.contains(host)) return new WidgetAction(root, i + 1, "Enter House", host);
            }
        for (Widget[] children : new Widget[][]{root.getDynamicChildren(), root.getStaticChildren(), root.getNestedChildren()})
            if (children != null) for (Widget child : children)
            {
                WidgetAction found = hostedHouseEntry(child, names, excluded);
                if (found != null) return found;
            }
        return null;
    }

    /** Select a live visible board row; never pin the recorded host's row or name. Client thread only. */
    static WidgetAction widgetAction(Widget root, String action)
    {
        if (root == null || root.isHidden()) return null;
        String[] actions = root.getActions();
        Rectangle bounds = root.getBounds();
        if (actions != null && bounds != null && bounds.width > 0 && bounds.height > 0)
            for (int i = 0; i < actions.length; i++)
                if (action.equalsIgnoreCase(actions[i])) return new WidgetAction(root, i + 1, action);
        for (Widget[] children : new Widget[][]{root.getDynamicChildren(), root.getStaticChildren(), root.getNestedChildren()})
            if (children != null) for (Widget child : children)
            {
                WidgetAction found = widgetAction(child, action);
                if (found != null) return found;
            }
        return null;
    }
}
