package net.runelite.client.plugins.microbot.arceuuslibrary;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.BookSnapshot;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.BookcaseSnapshot;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.Solved;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ArceuusLibraryScript extends Script
{
    private static final int LIBRARY_REGION = 6459;
    /** Tiles within which we trust the menu-invoke click to walk the player itself. */
    private static final int IN_SCENE_REACH = 20;
    private static final int CUSTOMER_REACH = 2;
    private static final int BOOK_OF_ARCANE_KNOWLEDGE = ItemID.ARCEUUS_LIBRARY_REWARD;
    /**
     * Ground-floor central tile near Professor Gracklebone — walking here loads all
     * three customer NPCs (Sam, Villia, Gracklebone) into the scene cache. Used as a
     * fallback when we need a customer but none are visible (e.g. we're on plane 1/2).
     */
    private static final WorldPoint CUSTOMER_HUB = new WorldPoint(1627, 3801, 0);

    private final KourendLibraryBridge bridge;
    private ArceuusLibraryConfig config;

    @Getter private volatile ArceuusLibraryState state = ArceuusLibraryState.IDLE;
    @Getter private volatile int delivered = 0;

    private volatile String solverState = "—";
    private volatile String wantedBookLabel = "—";
    private volatile String currentCustomerLabel = "—";
    private volatile int candidateCount = 0;

    public ArceuusLibraryScript(KourendLibraryBridge bridge)
    {
        this.bridge = bridge;
    }

    public boolean run(ArceuusLibraryConfig config)
    {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::tick, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private long tickCount = 0;
    private void tick()
    {
        try
        {
            tickCount++;
            if (tickCount % 25 == 1)
            {
                log.info("[hb] tick #{} state={} solver={} wanted={} customerLabel={}",
                        tickCount, state, solverState, wantedBookLabel, currentCustomerLabel);
            }
            if (!super.run() || !Microbot.isLoggedIn()) return;

            if (!bridge.isUpstreamRunning())
            {
                state = ArceuusLibraryState.UPSTREAM_DISABLED;
                if (!bridge.ensureUpstreamEnabled()) return;
            }

            // Always claim a pending reward before starting the next loop
            if (Rs2Inventory.hasItem(BOOK_OF_ARCANE_KNOWLEDGE))
            {
                claimReward();
                return;
            }

            Solved solved = bridge.getState();
            solverState = solved.name();

            Optional<BookSnapshot> wantedOpt = bridge.getCustomerBook();
            wantedBookLabel = wantedOpt.map(BookSnapshot::getShortName).orElse("—");
            BookSnapshot held = wantedOpt.isPresent() ? null : findHeldLibraryBook();
            if (held != null) wantedBookLabel = held.getShortName() + " (held)";

            // Anything except plain bookcase-fetch needs a customer in scene. If we'd
            // hit a customer-bound branch without one, walk to the hub first.
            boolean needsCustomer = wantedOpt.isPresent()
                    ? Rs2Inventory.hasItem(wantedOpt.get().getItemId())  // about to deliver
                    : held != null;                                      // resume with held book or cycle for a request
            if (needsCustomer && !ensureCustomerReachableInvariant()) return;

            if (!wantedOpt.isPresent())
            {
                if (held != null) { talkToCustomer("held-book"); return; }
                talkToCustomer("no-customer");
                return;
            }

            BookSnapshot wanted = wantedOpt.get();
            if (Rs2Inventory.hasItem(wanted.getItemId()))
            {
                handleDeliver(wanted);
                return;
            }

            handleFetch(wanted);
        }
        catch (Exception e)
        {
            Microbot.log("Arceuus Library tick error: " + e.getMessage());
            log.warn("tick failed", e);
        }
    }

    /**
     * Returns true if a customer is resolvable (upstream id or in NPC cache);
     * otherwise issues a walk to the ground-floor hub and returns false. Callers
     * should early-return on false so the walk progresses without further work.
     */
    private boolean ensureCustomerReachableInvariant()
    {
        // Don't trust bridge.getCustomerId() — that's upstream's last-known customer and
        // can be set while the NPC is on a different floor. Only "in our NPC cache right
        // now" counts as reachable.
        if (findNearestCustomer() != null) return true;
        currentCustomerLabel = "(searching)";
        if (walkToCustomerHubIfFar()) return false;
        ensureInsideLibrary();
        return false;
    }

    /* --------------- TALK_TO_CUSTOMER ------------------ */

    /**
     * Unified "find customer, walk to them, click Help" path. Used by both the
     * no-customer branch (cycle for a fresh request) and the held-book branch
     * (resume after restart). The invariant pass guarantees a customer is
     * resolvable; if findActiveCustomer still misses (race), we fall through.
     */
    private void talkToCustomer(String reason)
    {
        if (Rs2Dialogue.isInDialogue())
        {
            state = ArceuusLibraryState.TALK_TO_CUSTOMER;
            advanceDialogue();
            return;
        }

        Rs2NpcModel customer = findActiveCustomer();
        if (customer == null)
        {
            state = ArceuusLibraryState.IDLE;
            currentCustomerLabel = "(searching)";
            return;
        }

        currentCustomerLabel = customer.getName();
        state = ArceuusLibraryState.TALK_TO_CUSTOMER;
        WorldPoint customerLoc = customer.getWorldLocation();
        if (customerLoc == null) return;

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return;
        if (here.getPlane() != customerLoc.getPlane() || here.distanceTo(customerLoc) > IN_SCENE_REACH)
        {
            if (Rs2Player.isMoving()) return;
            log.info("[{}] walking to {} at {}", reason, customer.getName(), customerLoc);
            Rs2Walker.walkTo(customerLoc, CUSTOMER_REACH);
            return;
        }
        log.info("[{}] helping {} at {}", reason, customer.getName(), customerLoc);
        if (customer.click("Help"))
        {
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
            advanceDialogue();
        }
    }

    /**
     * If the player isn't near the ground-floor customer hub, walk there. Returns true
     * when a walk was issued. Used when we need a customer but none are in the NPC cache
     * (typically because we're on a different floor).
     */
    private boolean walkToCustomerHubIfFar()
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return false;
        if (here.getPlane() == CUSTOMER_HUB.getPlane()
                && here.distanceTo(CUSTOMER_HUB) <= IN_SCENE_REACH)
        {
            return false;
        }
        if (Rs2Player.isMoving()) return true;
        log.info("No customer in scene; walking to ground-floor hub {}", CUSTOMER_HUB);
        Rs2Walker.walkTo(CUSTOMER_HUB, 5);
        return true;
    }

    private void advanceDialogue()
    {
        long deadline = System.currentTimeMillis() + 6_000;
        while (System.currentTimeMillis() < deadline && Rs2Dialogue.isInDialogue())
        {
            if (Rs2Dialogue.hasContinue())
            {
                Rs2Dialogue.clickContinue();
            }
            else if (Rs2Dialogue.hasSelectAnOption())
            {
                if (!Rs2Dialogue.clickOption("Yes"))
                {
                    Rs2Dialogue.keyPressForDialogueOption(1);
                }
            }
            sleep(400, 700);
            if (bridge.getCustomerBook().isPresent()) return;
        }
    }

    /* --------------- LOCATE / SEARCH BOOKCASE ------------------ */

    private void handleFetch(BookSnapshot wanted)
    {
        if (Rs2Dialogue.isInDialogue())
        {
            advanceDialogue();
            return;
        }

        List<BookcaseSnapshot> candidates = candidatesFor(wanted);
        candidateCount = candidates.size();
        if (candidates.isEmpty())
        {
            state = ArceuusLibraryState.LOCATE_BOOK;
            return;
        }

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return;

        BookcaseSnapshot target = candidates.get(0);
        WorldPoint loc = target.getLocation();

        if (here.getPlane() != loc.getPlane() || here.distanceTo(loc) > IN_SCENE_REACH)
        {
            if (state != ArceuusLibraryState.WALK_TO_BOOKCASE)
            {
                log.info("Walking to bookcase {} (plane {}); {}/{} candidates, solver={}",
                        loc, loc.getPlane(), candidates.size(), bridge.getBookcases().size(), solverState);
            }
            state = ArceuusLibraryState.WALK_TO_BOOKCASE;
            if (Rs2Player.isMoving()) return;
            Rs2Walker.walkTo(loc, 5);
            return;
        }

        state = ArceuusLibraryState.SEARCH_BOOKCASE;
        searchBookcase(loc, wanted);
    }

    @SuppressWarnings("deprecation")
    private void searchBookcase(WorldPoint loc, BookSnapshot wanted)
    {
        log.info("Searching bookcase at {} for {}", loc, wanted.getShortName());
        if (!Rs2GameObject.interact(loc, "Search"))
        {
            log.warn("Search interaction returned false at {}", loc);
            sleep(400, 700);
            return;
        }
        // Exit as soon as upstream has a definitive outcome: isBookSet flips on either the
        // item-box widget appearing OR the "you don't find anything useful" chat line.
        sleepUntil(() -> Rs2Inventory.hasItem(wanted.getItemId())
                        || isBookcaseSearched(loc),
                10_000);
    }

    private boolean isBookcaseSearched(WorldPoint loc)
    {
        // Reads upstream's Bookcase.isBookSet directly — same signal the upstream overlay
        // uses to drop the white-square highlight. Avoids snapshot allocation in the poll loop.
        return bridge.isBookcaseSearchedAt(loc);
    }

    private List<BookcaseSnapshot> candidatesFor(BookSnapshot wanted)
    {
        // possibleBooks is only populated when solver state != NO_DATA. Once narrowed,
        // an empty possibleBooks means "ruled out for this layout" — do NOT search.
        boolean noData = bridge.getState() == Solved.NO_DATA;
        List<BookcaseSnapshot> raw = bridge.getBookcases();
        List<BookcaseSnapshot> filtered = new ArrayList<>();
        for (BookcaseSnapshot bc : raw)
        {
            // Already searched: include only if confirmed to hold the wanted book.
            // Empty searched bookcases and bookcases known to hold a different book
            // are skipped permanently.
            if (bc.isBookSet())
            {
                if (bc.getKnown() != null
                        && bc.getKnown().getEnumName().equals(wanted.getEnumName()))
                {
                    filtered.add(bc);
                }
                continue;
            }
            // Not yet searched.
            if (bc.getPossible().isEmpty())
            {
                // NO_DATA: solver hasn't started inferring; every unsearched bookcase is fair game.
                // Otherwise (INCOMPLETE/COMPLETE): empty possibleBooks = excluded by inference.
                if (noData) filtered.add(bc);
                continue;
            }
            for (BookSnapshot p : bc.getPossible())
            {
                if (p.getEnumName().equals(wanted.getEnumName()))
                {
                    filtered.add(bc);
                    break;
                }
            }
        }
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here != null) sortByReachableDistance(filtered, here);
        return filtered;
    }

    /**
     * Wall-aware ordering via a single local-scene BFS. Library bookcases ARE walls, so a
     * bookcase 1 tile away by Euclidean may require a long detour around the row. We BFS
     * from the player's tile through the collision map, then look up each candidate's
     * nearest cardinal-neighbor distance (the tile a player stands on to search). This
     * gives true walking distance without the per-candidate pathfinder cost.
     *
     * Cost: ~5ms per call (single BFS over ~3600 tiles at radius 60). Cross-plane
     * candidates fall through to a same-plane-first / Euclidean tiebreak.
     */
    private void sortByReachableDistance(List<BookcaseSnapshot> list, WorldPoint here)
    {
        Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(here, 60);
        list.sort(Comparator
                .comparingInt((BookcaseSnapshot b) -> b.getLocation().getPlane() == here.getPlane() ? 0 : 1)
                .thenComparingInt(b -> nearestReachableNeighborDist(b.getLocation(), reachable))
                .thenComparingInt(b -> b.getLocation().distanceTo(here)));
    }

    private int nearestReachableNeighborDist(WorldPoint bookcase, Map<WorldPoint, Integer> reachable)
    {
        int min = Integer.MAX_VALUE;
        Integer d;
        if ((d = reachable.get(bookcase.dx(1))) != null) min = Math.min(min, d);
        if ((d = reachable.get(bookcase.dx(-1))) != null) min = Math.min(min, d);
        if ((d = reachable.get(bookcase.dy(1))) != null) min = Math.min(min, d);
        if ((d = reachable.get(bookcase.dy(-1))) != null) min = Math.min(min, d);
        return min;
    }

    /* --------------- DELIVER ------------------ */

    private void handleDeliver(BookSnapshot wanted)
    {
        state = ArceuusLibraryState.DELIVER;

        if (config.readSoulJourney() && "SOUL_JOURNEY".equals(wanted.getEnumName()))
        {
            Rs2Inventory.interact(wanted.getItemId(), "Read");
            sleep(800, 1200);
            advanceDialogue();
        }

        Rs2NpcModel customer = findActiveCustomer();
        if (customer == null)
        {
            currentCustomerLabel = "(searching)";
            return;
        }
        currentCustomerLabel = customer.getName();
        WorldPoint customerLoc = customer.getWorldLocation();
        if (customerLoc == null) return;

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return;
        if (here.getPlane() != customerLoc.getPlane() || here.distanceTo(customerLoc) > IN_SCENE_REACH)
        {
            if (Rs2Player.isMoving()) return;
            log.info("Walking to deliver to {} at {}", customer.getName(), customerLoc);
            Rs2Walker.walkTo(customerLoc, CUSTOMER_REACH);
            return;
        }

        log.info("Delivering {} to {} at {}", wanted.getShortName(), customer.getName(), customerLoc);
        if (customer.click("Help"))
        {
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
            advanceDialogue();
            sleepUntil(() -> !Rs2Inventory.hasItem(wanted.getItemId()), 5_000);
            if (!Rs2Inventory.hasItem(wanted.getItemId()))
            {
                delivered++;
                state = ArceuusLibraryState.IDLE;
            }
        }
    }

    /* --------------- REWARD CLAIM ------------------ */

    private void claimReward()
    {
        RewardXp choice = config.rewardXp();
        if (!Rs2Inventory.interact(BOOK_OF_ARCANE_KNOWLEDGE, "Read"))
        {
            sleep(400, 700);
            return;
        }
        sleepUntil(Rs2Dialogue::isInDialogue, 3_000);

        long deadline = System.currentTimeMillis() + 5_000;
        boolean picked = false;
        while (System.currentTimeMillis() < deadline && Rs2Dialogue.isInDialogue())
        {
            if (Rs2Dialogue.hasSelectAnOption() && !picked)
            {
                if (Rs2Dialogue.clickOption(choice.getDialogueOption(), false))
                {
                    picked = true;
                }
            }
            else if (Rs2Dialogue.hasContinue())
            {
                Rs2Dialogue.clickContinue();
            }
            sleep(300, 500);
        }
        sleepUntil(() -> !Rs2Inventory.hasItem(BOOK_OF_ARCANE_KNOWLEDGE), 3_000);
    }

    /* --------------- helpers ------------------ */

    /**
     * Resolve the customer NPC the upstream solver is tracking. When upstream has a
     * {@code customerId} (set after talking to a customer), this is the authoritative
     * choice — it's the same NPC the upstream overlay highlights with a green square.
     * When upstream has no active customer (e.g. just after a delivery), we pick a
     * customer NPC we're not currently adjacent to so we don't re-talk to the
     * customer we just delivered to (who would respond with "Thank you for finding my
     * book. It is most interesting." — no new request, per wiki transcript).
     */
    private Rs2NpcModel findActiveCustomer()
    {
        int id = bridge.getCustomerId();
        if (id != -1)
        {
            Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                    .withId(id)
                    .nearestOnClientThread();
            if (npc != null) return npc;
        }
        return findCustomerNotAdjacent();
    }

    /**
     * Closest customer NPC strictly farther than 1 tile from the player. Used to
     * cycle past whichever customer we just talked to (since we end up standing
     * next to them after dialogue, we'll naturally pick a different one next).
     * Filtered by NPC IDs — never matches stray same-named NPCs elsewhere.
     */
    private Rs2NpcModel findCustomerNotAdjacent()
    {
        Rs2NpcModel candidate = findCustomer(1);
        return candidate != null ? candidate : findCustomer(Integer.MIN_VALUE);
    }

    private Rs2NpcModel findNearestCustomer()
    {
        return findCustomer(Integer.MIN_VALUE);
    }

    /**
     * Closest library customer NPC (by id) strictly farther than {@code minDist}
     * tiles from the player.
     */
    private Rs2NpcModel findCustomer(int minDist)
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        Rs2NpcModel best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Rs2NpcModel npc : Microbot.getRs2NpcCache().query()
                .withIds(Customer.allIds())
                .toListOnClientThread())
        {
            if (npc.getWorldLocation() == null) continue;
            int d = here == null ? 0 : here.distanceTo(npc.getWorldLocation());
            if (d <= minDist) continue;
            if (d < bestDist)
            {
                best = npc;
                bestDist = d;
            }
        }
        return best;
    }

    private BookSnapshot findHeldLibraryBook()
    {
        return Rs2Inventory.items()
                .map(item -> bridge.bookForItemId(item.getId()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void ensureInsideLibrary()
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return;
        if (here.getRegionID() == LIBRARY_REGION) return;
        Microbot.log("Player not in Arceuus Library (region " + here.getRegionID() + ") — please position manually");
    }

    public String getSolverState() { return solverState; }
    public String getWantedBookLabel() { return wantedBookLabel; }
    public String getCurrentCustomerLabel() { return currentCustomerLabel; }
    public int getCandidateCount() { return candidateCount; }

    @Override
    public void shutdown()
    {
        super.shutdown();
        state = ArceuusLibraryState.IDLE;
    }
}
