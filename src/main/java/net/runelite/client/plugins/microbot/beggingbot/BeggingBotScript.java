package net.runelite.client.plugins.microbot.beggingbot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.client.util.Text;

/**
 * Begging loop. The only thing it ever does in a trade is press Accept: it never adds an item or coins to its own
 * side, and it declines a final confirmation that would have it give anything away.
 */
public class BeggingBotScript extends Script
{
    private static final int COINS = 995;
    /** Farther than this from the GE centre and we walk back. */
    private static final int GE_LEASH = 20;
    /** From wherever the script starts, keep walking until we are this close to the GE centre. */
    private static final int ARRIVE_RADIUS = 6;
    /** A trade window that sits open this long with no progress is declined so the bot can go back to begging. */
    private static final long TRADE_TIMEOUT_MS = 60_000;
    private static final long TRADE_REQUEST_TTL_MS = 25_000;
    private static final long MIN_HOP_GAP_MS = 30_000;
    private static final int WORLD_POOL = 25;
    private static final int RECENT_WORLDS = 6;

    private static final int TRADE_ACCEPT = InterfaceID.Trademain.ACCEPT;
    private static final int TRADE_DECLINE = InterfaceID.Trademain.DECLINE;
    private static final int CONFIRM_ACCEPT = InterfaceID.Tradeconfirm.TRADE2ACCEPT;
    private static final int CONFIRM_DECLINE = InterfaceID.Tradeconfirm.TRADE2DECLINE;
    private static final int CONFIRM_YOUR_OFFER = InterfaceID.Tradeconfirm.YOUR_OFFER;

    private static final String[] MESSAGES = {
        "First to give me 100K will not regret it",
        "First to give me 1M will not regret it",
        "Anyone spare 100k? You will not regret it",
        "Spare 100k and I will be forever grateful",
        "Trade me 50k and you will not regret it",
        "Please trade me some gp, every bit helps",
        "Just need a little gp, anything is appreciated",
        "Any gp helps, I really appreciate it",
        "Trade me any amount of gp, thank you so much",
        "Be a legend and spare me some gp",
        "Kind souls, please spare some coins",
        "Every coin counts, trade me and make my day",
        "Feeling generous? Trade me some gp",
        "Small or big, all gp is welcome, thanks",
        "Help a fellow adventurer out with some gp",
        "Saving up for my first bond, any gp helps",
        "First to trade me 100k gets my eternal thanks",
        "Whoever spares 100k today will not regret it",
    };

    private final BeggingBotConfig config;

    private volatile String pendingTradeFrom;
    private volatile long pendingTradeAt;
    private volatile long lastTradeAt;
    private volatile int tradesCompleted;

    private final Deque<Integer> recentWorlds = new ArrayDeque<>();
    private final List<String> messageBag = new ArrayList<>();
    private String lastMessage = "";

    private long nextChatAt;
    private long noPlayersSince;
    private long lastRepositionAt;
    private long lastTradeClickAt;
    private long tradeOpenedAt;
    private long lastTradeAttemptAt;
    private long lastHopAt;
    private long settleUntil;
    private int startCoins = -1;
    private boolean arrivedAtGe;

    // stats for the paint
    private volatile long startedAt;
    private volatile int currentCoins;
    private volatile int tradesOpened;
    private volatile int messagesSent;
    private volatile int hops;
    private volatile int nearbyCount;

    public BeggingBotScript(BeggingBotConfig config)
    {
        this.config = config;
    }

    @Override
    public boolean run()
    {
        long now = System.currentTimeMillis();
        startedAt = now;
        arrivedAtGe = false;
        lastTradeAt = now;
        nextChatAt = now + between(1500, 4000);
        noPlayersSince = 0;
        startCoins = -1;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                step();
            }
            catch (Exception ex)
            {
                StringBuilder trace = new StringBuilder("Begging Bot: " + ex);
                StackTraceElement[] frames = ex.getStackTrace();
                for (int i = 0; i < Math.min(8, frames.length); i++) trace.append(" | at ").append(frames[i]);
                Microbot.log(trace.toString());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        Microbot.status = "";
    }

    // ---- paint accessors ----

    long startedAt() { return startedAt; }
    int coinsGained() { return Math.max(0, currentCoins - Math.max(0, startCoins)); }
    int tradesCompleted() { return tradesCompleted; }
    int tradesOpened() { return tradesOpened; }
    int messagesSent() { return messagesSent; }
    int hops() { return hops; }
    int nearbyCount() { return nearbyCount; }
    long lastTradeAt() { return lastTradeAt; }
    long nextChatAt() { return nextChatAt; }

    // ---- events from the plugin ----

    void onTradeRequest(String playerName)
    {
        pendingTradeFrom = normalise(playerName);
        pendingTradeAt = System.currentTimeMillis();
    }

    void onTradeCompleted()
    {
        tradesCompleted++;
        lastTradeAt = System.currentTimeMillis();
    }

    // ---- main loop ----

    private void step()
    {
        if (Microbot.isHopping()) return;
        WorldPoint here = onClient(() -> Microbot.getClient().getLocalPlayer().getWorldLocation());
        String myName = onClient(() -> Microbot.getClient().getLocalPlayer().getName());
        if (here == null || myName == null) return;
        long now = System.currentTimeMillis();
        Integer coins = onClient(() -> Rs2Inventory.itemQuantity(COINS));
        if (coins != null) currentCoins = coins;
        if (startCoins < 0) startCoins = currentCoins;
        if (now < settleUntil) return; // let the new world's players load in after a hop

        if (handleTradeWindow(now)) return;

        if (Boolean.TRUE.equals(onClient(Rs2GrandExchange::isOpen)))
        {
            Rs2GrandExchange.closeExchange();
            return;
        }
        if (Boolean.TRUE.equals(onClient(Rs2Bank::isOpen))) return;

        WorldPoint ge = BankLocation.GRAND_EXCHANGE.getWorldPoint();
        int geDistance = here.distanceTo(ge); // Integer.MAX_VALUE on another plane
        if (geDistance > (arrivedAtGe ? GE_LEASH : ARRIVE_RADIUS))
        {
            arrivedAtGe = false;
            Microbot.status = "Walking to the Grand Exchange";
            Rs2Walker.walkTo(ge, 3);
            return;
        }
        arrivedAtGe = true;

        List<Seen> nearby = nearbyPlayers(myName, here);
        if (answerTradeRequest(now)) return;
        if (hopIfNeeded(now, nearby)) return;

        nearbyCount = nearby.size();
        Microbot.status = "Begging";
        moveTowardPlayers(here, nearby, ge, now);
        chat(now);
    }

    // ---- trading ----

    private static boolean visible(int packedId)
    {
        return Rs2Widget.isWidgetVisible(packedId);
    }

    private boolean handleTradeWindow(long now)
    {
        boolean confirm = visible(CONFIRM_ACCEPT);
        boolean first = !confirm && visible(TRADE_ACCEPT);
        if (!confirm && !first)
        {
            tradeOpenedAt = 0;
            return false;
        }
        if (tradeOpenedAt == 0)
        {
            tradeOpenedAt = now;
            tradesOpened++;
            lastTradeAt = now;
            pendingTradeFrom = null;
            Microbot.status = "Trading";
            sleep(700, 1500);
            return true;
        }
        if (now - tradeOpenedAt > TRADE_TIMEOUT_MS)
        {
            Rs2Widget.clickWidget(confirm ? CONFIRM_DECLINE : TRADE_DECLINE);
            tradeOpenedAt = 0;
            return true;
        }
        if (now - lastTradeClickAt < 1800) return true;
        lastTradeClickAt = now;
        if (confirm)
        {
            // Never hand anything over: our side of the confirmation must read "Absolutely nothing!".
            Rs2Widget.clickWidget(offerIsEmpty() ? CONFIRM_ACCEPT : CONFIRM_DECLINE);
        }
        else
        {
            Rs2Widget.clickWidget(TRADE_ACCEPT);
        }
        return true;
    }

    private boolean offerIsEmpty()
    {
        String raw = onClient(() ->
        {
            Widget offer = Rs2Widget.getWidget(CONFIRM_YOUR_OFFER);
            return offer == null ? null : offer.getText();
        });
        if (raw == null) return true;
        String text = Text.removeTags(raw).trim().toLowerCase();
        return text.isEmpty() || text.contains("nothing");
    }

    private boolean answerTradeRequest(long now)
    {
        String from = pendingTradeFrom;
        if (from == null) return false;
        if (now - pendingTradeAt > TRADE_REQUEST_TTL_MS)
        {
            pendingTradeFrom = null;
            return false;
        }
        if (now - lastTradeAttemptAt < 3500) return false;
        Rs2PlayerModel requester = onClient(() -> Rs2Player.getPlayers(p -> p.getName() != null && from.equals(normalise(p.getName())))
            .findFirst().orElse(null));
        if (requester == null) return false;
        lastTradeAttemptAt = now;
        Microbot.status = "Accepting trade from " + from;
        sleep(500, 1400);
        Rs2Player.trade(requester);
        sleepUntil(() -> visible(TRADE_ACCEPT), 5000);
        return true;
    }

    // ---- players / world hopping ----

    /** A player as seen on one client-thread pass; the name and tile can't be read safely later from the script thread. */
    private static final class Seen
    {
        final String name;
        final WorldPoint at;

        Seen(String name, WorldPoint at)
        {
            this.name = name;
            this.at = at;
        }
    }

    private List<Seen> nearbyPlayers(String myName, WorldPoint here)
    {
        int radius = config.playerRadius();
        List<Seen> seen = onClient(() -> Rs2Player.getPlayers(p -> p.getName() != null && !p.getName().equals(myName)
            && p.getWorldLocation().distanceTo(here) <= radius)
            .map(p -> new Seen(p.getName(), p.getWorldLocation())).collect(Collectors.toList()));
        return seen == null ? new ArrayList<>() : seen;
    }

    private static <T> T onClient(Callable<T> call)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(call).orElse(null);
    }

    private boolean hopIfNeeded(long now, List<Seen> nearby)
    {
        if (nearby.isEmpty())
        {
            if (noPlayersSince == 0) noPlayersSince = now;
        }
        else
        {
            noPlayersSince = 0;
        }
        if (now - lastHopAt < MIN_HOP_GAP_MS) return false;
        if (noPlayersSince != 0 && now - noPlayersSince >= config.noPlayersHopSeconds() * 1000L)
        {
            hop("nobody nearby");
            return true;
        }
        if (now - lastTradeAt >= config.noTradeHopMinutes() * 60_000L)
        {
            hop("no trades in " + config.noTradeHopMinutes() + " minutes");
            return true;
        }
        return false;
    }

    private void hop(String why)
    {
        lastHopAt = System.currentTimeMillis();
        Integer currentWorld = onClient(() -> Microbot.getClient().getWorld());
        int current = currentWorld == null ? -1 : currentWorld;
        int world = pickWorld(current);
        if (world <= 0 || world == current) return;
        Microbot.status = "Hopping to world " + world + " (" + why + ")";
        Microbot.log("Begging Bot: hopping to world " + world + " - " + why);
        Microbot.hopToWorld(world);
        sleepUntil(Microbot::isHopping, 4000);
        sleepUntil(() -> !Microbot.isHopping(), 30_000);
        hops++;
        recentWorlds.addLast(current);
        while (recentWorlds.size() > RECENT_WORLDS) recentWorlds.removeFirst();
        long after = System.currentTimeMillis();
        noPlayersSince = 0;
        lastTradeAt = after;
        pendingTradeFrom = null;
        tradeOpenedAt = 0;
        settleUntil = after + between(3000, 6000);
        nextChatAt = settleUntil + between(1500, 4000);
    }

    /** Ranks accessible worlds by population and picks one at random, squared weights so the busiest are favoured most. */
    private int pickWorld(int current)
    {
        List<Integer> ranked = new ArrayList<>(Rs2WorldUtil.getAccessibleWorldsByPopulation(WORLD_POOL));
        ranked.removeIf(w -> w == current || recentWorlds.contains(w));
        if (ranked.isEmpty()) return Login.getRandomWorld(Boolean.TRUE.equals(onClient(Rs2Player::isMember)));
        long total = 0;
        for (int i = 0; i < ranked.size(); i++) total += (long) (ranked.size() - i) * (ranked.size() - i);
        long roll = ThreadLocalRandom.current().nextLong(total);
        for (int i = 0; i < ranked.size(); i++)
        {
            roll -= (long) (ranked.size() - i) * (ranked.size() - i);
            if (roll < 0) return ranked.get(i);
        }
        return ranked.get(0);
    }

    private void moveTowardPlayers(WorldPoint here, List<Seen> nearby, WorldPoint ge, long now)
    {
        if (nearby.isEmpty() || now - lastRepositionAt < 12_000) return;
        Seen nearest = Collections.min(nearby, (a, b) -> Integer.compare(a.at.distanceTo(here), b.at.distanceTo(here)));
        if (nearest.at.distanceTo(here) <= 3) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        WorldPoint target = nearest.at.dx(rng.nextInt(-1, 2)).dy(rng.nextInt(-1, 2));
        if (target.distanceTo(ge) > GE_LEASH) return;
        lastRepositionAt = now;
        Microbot.status = "Moving closer to " + nearest.name;
        Rs2Walker.walkTo(target, 2);
    }

    // ---- begging ----

    private void chat(long now)
    {
        if (now < nextChatAt || visible(TRADE_ACCEPT) || visible(CONFIRM_ACCEPT)) return;
        String message = nextMessage();
        Rs2Keyboard.enter();
        sleep(350, 800);
        Rs2Keyboard.typeString(message);
        sleep(300, 750);
        Rs2Keyboard.enter();
        messagesSent++;
        int min = Math.min(config.minChatSeconds(), config.maxChatSeconds());
        int max = Math.max(config.minChatSeconds(), config.maxChatSeconds());
        nextChatAt = now + between(min * 1000, max * 1000);
    }

    private String nextMessage()
    {
        if (messageBag.isEmpty())
        {
            Collections.addAll(messageBag, MESSAGES);
            for (String extra : config.customMessages().split("\\|"))
            {
                String trimmed = extra.trim();
                if (!trimmed.isEmpty()) messageBag.add(trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed);
            }
            Collections.shuffle(messageBag);
            if (messageBag.size() > 1 && messageBag.get(messageBag.size() - 1).equals(lastMessage))
            {
                Collections.swap(messageBag, 0, messageBag.size() - 1);
            }
        }
        lastMessage = messageBag.remove(messageBag.size() - 1);
        return lastMessage;
    }

    // ---- helpers ----

    private static String normalise(String name)
    {
        return name == null ? "" : Text.removeTags(name).replace((char) 160, ' ').trim().toLowerCase();
    }

    private static int between(int min, int max)
    {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
