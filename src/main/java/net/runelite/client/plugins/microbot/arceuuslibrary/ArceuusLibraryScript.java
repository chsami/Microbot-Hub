package net.runelite.client.plugins.microbot.arceuuslibrary;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.BookSnapshot;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.BookcaseSnapshot;
import net.runelite.client.plugins.microbot.arceuuslibrary.KourendLibraryBridge.Solved;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.events.PluginMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ArceuusLibraryScript extends Script
{
    private static final int LIBRARY_REGION = 6459;
    private static final int CUSTOMER_REACH = 2;
    private static final int BOOK_OF_ARCANE_KNOWLEDGE = ItemID.ARCEUUS_LIBRARY_REWARD;
    /**
     * Ground-floor central tile near Professor Gracklebone — walking here loads all
     * three customer NPCs (Sam, Villia, Gracklebone) into the scene cache. Used as a
     * fallback when we need a customer but none are visible (e.g. we're on plane 1/2).
     */
    private static final WorldPoint CUSTOMER_HUB = new WorldPoint(1627, 3801, 0);
    /**
     * Section sweep tuning. With state=COMPLETE only ~5 layout bookcases per plane have
     * non-empty {@code possibleBooks}; radius 25 lets one same-plane sweep reach across
     * two adjacent wings (each ~10–15 tiles), which is the granularity at which sibling
     * candidates cluster. The per-round cap bounds the worst-case detour when
     * solver=INCOMPLETE keeps surfacing informative neighbours.
     */
    private static final int SECTION_SWEEP_RADIUS = 25;
    private static final int SECTION_SWEEP_MAX_PER_ROUND = 6;

    private final KourendLibraryBridge bridge;
    private ArceuusLibraryConfig config;
    /**
     * The customer we most recently delivered to. They will not request another book
     * until we deliver to one of the other two customers, so we must exclude them from
     * the "find next customer to talk to" fallback. Cleared/replaced on each delivery.
     */
    private volatile int lastDeliveredCustomerId = -1;
    /**
     * Counter of section-sweep searches performed since the current wanted book was
     * fetched. Reset on each successful delivery. Capped by {@link #SECTION_SWEEP_MAX_PER_ROUND}.
     */
    private volatile int sweepSearchesThisTrip = 0;
    /**
     * Sticky sweep target. Once {@link #trySectionSweep} commits to a candidate we keep
     * walking toward it across ticks, even if the player crosses planes mid-walk and a
     * fresh same-plane scan would return no candidates. Cleared on successful search,
     * delivery, or when the candidate becomes invalid (already searched / book held).
     * Without this the dispatcher silently abandons sweep mid-trip — observed in logs
     * as {@code state=SECTION_SWEEP} ticks with zero matching {@code Searching bookcase}
     * follow-ups, because the sweep returned false on a later tick (player on different
     * plane than committed target) and {@code handleDeliver} re-routed to the customer.
     */
    private volatile WorldPoint pendingSweepTarget = null;
    /**
     * Customer IDs we've clicked "Help" on since the last time we made progress
     * (got a new request or handed off a held book). Used to round-robin when the
     * upstream solver doesn't know who's active and the first customer we reach
     * declines (they tell us to "speak to whoever is asking for a book"). Cleared
     * the moment any customer accepts our held book or gives us a new request.
     */
    private final Set<Integer> recentlyAttempted = new HashSet<>();

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
        restrictPathfinderToLocal();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::tick, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private long tickCount = 0;
    private void tick()
    {
        try
        {
            tickCount++;
            boolean superRun = super.run();
            boolean loggedIn = Microbot.isLoggedIn();
            if (tickCount % 10 == 1)
            {
                log.info("[hb] tick #{} state={} solver={} wanted={} customer={} super={} login={}",
                        tickCount, state, solverState, wantedBookLabel, currentCustomerLabel,
                        superRun, loggedIn);
            }
            if (!superRun || !loggedIn) return;

            if (!bridge.isUpstreamRunning())
            {
                log.info("[tick#{}] upstream not running, attempting enable", tickCount);
                state = ArceuusLibraryState.UPSTREAM_DISABLED;
                if (!bridge.ensureUpstreamEnabled()) return;
                log.info("[tick#{}] upstream enabled successfully", tickCount);
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

            if (tickCount % 10 == 1)
            {
                log.info("[tick#{}] solver={} wanted={} held={} custId={}",
                        tickCount, solverState, wantedBookLabel,
                        held != null ? held.getShortName() : "none",
                        bridge.getCustomerId());
            }

            // Anything except plain bookcase-fetch needs a customer in scene. If we'd
            // hit a customer-bound branch without one, walk to the hub first.
            boolean needsCustomer = wantedOpt.isPresent()
                    ? Rs2Inventory.hasItem(wantedOpt.get().getItemId())  // about to deliver
                    : held != null;                                      // resume with held book or cycle for a request
            if (needsCustomer && !ensureCustomerReachableInvariant())
            {
                log.info("[tick#{}] ensureCustomerReachable=false, yielding", tickCount);
                return;
            }

            if (!wantedOpt.isPresent())
            {
                if (held != null)
                {
                    log.info("[tick#{}] branch=held-book → talkToCustomer", tickCount);
                    talkToCustomer("held-book");
                    return;
                }
                log.info("[tick#{}] branch=no-customer → talkToCustomer", tickCount);
                talkToCustomer("no-customer");
                return;
            }

            BookSnapshot wanted = wantedOpt.get();
            if (Rs2Inventory.hasItem(wanted.getItemId()))
            {
                if (trySectionSweep()) return;
                log.info("[tick#{}] branch=deliver {}", tickCount, wanted.getShortName());
                handleDeliver(wanted);
                return;
            }

            if (trySectionSweep()) return;
            log.info("[tick#{}] branch=fetch {}", tickCount, wanted.getShortName());
            handleFetch(wanted);
        }
        catch (Exception e)
        {
            Microbot.log("Arceuus Library tick error: " + e.getMessage());
            log.warn("tick failed", e);
        }
    }

    /**
     * Returns true if downstream handlers can take over routing; otherwise issues a
     * walk to the customer hub and returns false so the caller early-returns.
     *
     * When upstream knows the active customer ({@link KourendLibraryBridge#getCustomerId()}
     * != -1), {@link #handleDeliver} owns the routing — its cache-miss branch walks
     * directly to that customer's roam tile. Pre-walking to a generic hub on every
     * cross-floor return trip is a detour for every customer that isn't Gracklebone
     * (Sam's roam is +11 east, Villia's is +14 east and +12 north of the hub).
     *
     * Only when there's no active customer (we just delivered, or we're cycling for
     * a fresh request via the held-book branch) do we fall back to the generic hub —
     * that brings any customer into scene so {@link #talkToCustomer} can pick one.
     */
    private boolean ensureCustomerReachableInvariant()
    {
        if (bridge.getCustomerId() > 0) return true;
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
            // Nobody clickable in cache. If we've recently attempted some customers
            // without progress, the un-attempted one is most likely the actual active
            // customer and just isn't loaded — walk to their roam tile to bring them
            // into scene. Otherwise let the dispatcher's invariant pass handle it.
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
            Customer untried = pickUnattemptedCustomer();
            if (untried != null)
            {
                state = ArceuusLibraryState.TALK_TO_CUSTOMER;
                currentCustomerLabel = untried.getNpcName() + " (loading)";

                // If we already walked to this customer's roam and they still aren't in
                // the NPC cache, re-issuing the same walkTo each tick won't help. Mark
                // them as attempted so the rotation advances to the next customer (who
                // may already be in cache, just adjacent or excluded earlier).
                WorldPoint here = Rs2Player.getWorldLocation();
                WorldPoint roam = untried.getRoamCenter();
                if (here != null
                        && here.getPlane() == roam.getPlane()
                        && here.distanceTo(roam) <= 4)
                {
                    log.info("[{}] {} not in cache at roam {}, marking attempted",
                            reason, untried.getNpcName(), roam);
                    recentlyAttempted.add(untried.getNpcId());
                    if (recentlyAttempted.size() >= Customer.values().length)
                    {
                        log.info("[{}] all customers attempted without progress; resetting rotation", reason);
                        recentlyAttempted.clear();
                    }
                    return;
                }

                log.info("[{}] no candidate clickable; walking toward {}'s roam {}",
                        reason, untried.getNpcName(), roam);
                walkInBackground(roam);
                return;
            }
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
        if (here.getPlane() != customerLoc.getPlane()
                || !isWalkReachable(here, customerLoc))
        {
            log.info("[{}] walking to {} at {}", reason, customer.getName(), customerLoc);
            walkInBackground(customerLoc);
            return;
        }

        // Same plane and reachable — cancel walker and interact immediately.
        cancelBackgroundWalk();

        log.info("[{}] helping {} at {}", reason, customer.getName(), customerLoc);
        Rs2Npc.hoverOverActor(customer.getNpc());
        if (customer.click("Help"))
        {
            sleepUntil(Rs2Dialogue::isInDialogue, 10_000);
            int booksHeldBefore = countLibraryBooksHeld();
            advanceDialogue();
            boolean gotRequest = bridge.getCustomerBook().isPresent();
            boolean gaveBook = countLibraryBooksHeld() < booksHeldBefore;
            if (gotRequest || gaveBook)
            {
                lastDeliveredCustomerId = customer.getId();
                recentlyAttempted.clear();
            }
            else
            {
                recentlyAttempted.add(customer.getId());
                if (recentlyAttempted.size() >= Customer.values().length)
                {
                    log.info("[{}] all customers declined; resetting rotation", reason);
                    recentlyAttempted.clear();
                }
            }
        }
    }

    private int countLibraryBooksHeld()
    {
        return (int) Rs2Inventory.items()
                .map(item -> bridge.bookForItemId(item.getId()))
                .filter(java.util.Objects::nonNull)
                .count();
    }

    private Customer pickUnattemptedCustomer()
    {
        for (Customer c : Customer.values())
        {
            if (recentlyAttempted.contains(c.getNpcId())) continue;
            if (c.getNpcId() == lastDeliveredCustomerId) continue;
            return c;
        }
        return null;
    }

    /**
     * Walk to the ground-floor customer hub. Caller guarantees we got here because no
     * customer is in the NPC cache, so we must keep moving toward the hub regardless
     * of how {@link WorldPoint#distanceTo} measures "close" — that's Chebyshev, which
     * can read 19 from the west wing of the library while the customer NPCs are still
     * outside the loaded scene chunks. {@link Rs2Walker#walkTo} is idempotent: if we're
     * already within {@code reach=5} of the destination it returns without moving, so
     * re-issuing each tick is safe.
     */
    private boolean walkToCustomerHubIfFar()
    {
        if (Rs2Player.isMoving()) return true;
        log.info("No customer in scene; walking to ground-floor hub from {}", Rs2Player.getWorldLocation());
        walkInBackground(CUSTOMER_HUB);
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

        if (here.getPlane() == loc.getPlane()
                && Rs2GameObject.findObjectByLocation(loc) != null
                && isWalkReachable(here, loc))
        {
            cancelBackgroundWalk();
            state = ArceuusLibraryState.SEARCH_BOOKCASE;
            searchBookcase(loc, wanted);
            return;
        }

        // Not yet interactable (different plane, not in scene, or same plane but
        // blocked by walls/rooms) — Rs2Walker navigates on a background thread.
        // Re-issue if the previous walk completed without arriving (e.g. stuck/failed).
        if (state != ArceuusLibraryState.WALK_TO_BOOKCASE
                || (backgroundWalk != null && backgroundWalk.isDone()))
        {
            if (state != ArceuusLibraryState.WALK_TO_BOOKCASE)
            {
                log.info("Walking to bookcase {} (plane {}); {}/{} candidates, solver={}",
                        loc, loc.getPlane(), candidates.size(), bridge.getBookcases().size(), solverState);
            }
            state = ArceuusLibraryState.WALK_TO_BOOKCASE;
            walkInBackground(loc);
        }
    }

    @SuppressWarnings("deprecation")
    private void searchBookcase(WorldPoint loc, BookSnapshot wanted)
    {
        log.info("Searching bookcase at {} for {}", loc, wanted.getShortName());
        TileObject bookcase = Rs2GameObject.findObjectByLocation(loc);
        if (bookcase != null) Rs2GameObject.hoverOverObject(bookcase);
        if (!Rs2GameObject.interact(loc, "Search"))
        {
            log.warn("Search interaction returned false at {}", loc);
            sleep(400, 700);
            return;
        }
        // Wait for the search animation to actually run, then complete. Polling
        // upstream's isBookSet doesn't work as the search-completion signal because
        // it stays true for the whole layout — on a re-visit (modal-dismiss click,
        // or stale-state re-search) the condition is true at entry and we'd exit
        // before the animation even started, producing spam-clicks.
        sleepUntil(() -> isBookcaseSearched(loc), 10_000);
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
            // Already searched: include only if upstream still believes it holds the
            // wanted book. That covers two cases this layout: (a) we just searched it
            // and the modal is pending — re-clicking dismisses the modal and the book
            // lands in inventory; (b) we previously delivered this book to another
            // customer and upstream's known=wanted is stale — re-clicking searches
            // the now-empty bookcase, upstream observes "nothing useful" and clears
            // known to null, so candidatesFor excludes it next tick.
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
        if (reachable == null || reachable.isEmpty())
        {
            list.sort(Comparator
                    .comparingInt((BookcaseSnapshot b) -> b.getLocation().getPlane() == here.getPlane() ? 0 : 1)
                    .thenComparingInt(b -> b.getLocation().distanceTo(here)));
            return;
        }
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

    /* --------------- SECTION SWEEP ------------------ */

    /**
     * Opportunistic search of a nearby unsearched bookcase. Two regimes:
     *  - Solver narrowed to 1 (size==1): search yields that exact book. Skip if we
     *    already hold it.
     *  - Solver still ambiguous (size>1): search returns one of the possibilities
     *    and collapses the others via upstream inference — pure narrowing value
     *    even when the yielded book is one we already hold.
     *
     * Capped per fetch+deliver round via {@link #SECTION_SWEEP_MAX_PER_ROUND}; counter
     * resets on successful delivery. Returns true when an action was taken (caller
     * should yield the tick); false when there's nothing to sweep.
     */
    private boolean trySectionSweep()
    {
        if (sweepSearchesThisTrip >= SECTION_SWEEP_MAX_PER_ROUND) return false;

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return false;

        BookcaseSnapshot best = resolveSweepTarget(here);
        if (best == null) return false;

        // Representative book for the search-completion wait predicate. For multi-
        // possible bookcases this is a guess; the animation-stop fallback in
        // searchBookcase() handles a wrong guess correctly.
        BookSnapshot expected = best.getPossible().iterator().next();
        WorldPoint loc = best.getLocation();
        pendingSweepTarget = loc;

        if (Rs2Dialogue.isInDialogue())
        {
            advanceDialogue();
            return true;
        }

        if (here.getPlane() == loc.getPlane()
                && Rs2GameObject.findObjectByLocation(loc) != null
                && isWalkReachable(here, loc))
        {
            cancelBackgroundWalk();
            state = ArceuusLibraryState.SECTION_SWEEP;
            log.info("Sweep: searching {} ({}/{}) for {}",
                    loc, sweepSearchesThisTrip + 1, SECTION_SWEEP_MAX_PER_ROUND, expected.getShortName());
            searchBookcase(loc, expected);
            sweepSearchesThisTrip++;
            pendingSweepTarget = null;
            return true;
        }

        // Sweeps are opportunistic same-floor detours — never chase across planes.
        if (here.getPlane() != loc.getPlane())
        {
            log.info("Sweep: abandoning {} (player plane {} != target plane {})",
                    loc, here.getPlane(), loc.getPlane());
            pendingSweepTarget = null;
            return false;
        }

        // Not yet interactable — Rs2Walker navigates on a background thread.
        if (state != ArceuusLibraryState.SECTION_SWEEP
                || (backgroundWalk != null && backgroundWalk.isDone()))
        {
            if (state != ArceuusLibraryState.SECTION_SWEEP)
            {
                log.info("Sweep: walking to {} on plane {} (possible={}); {}/{}",
                        loc, loc.getPlane(), best.getPossible().size(),
                        sweepSearchesThisTrip + 1, SECTION_SWEEP_MAX_PER_ROUND);
            }
            state = ArceuusLibraryState.SECTION_SWEEP;
            walkInBackground(loc);
        }
        return true;
    }

    /**
     * Stick with {@link #pendingSweepTarget} while it remains useful so a single fetch
     * trip commits to one detour bookcase even if the player crosses planes mid-walk.
     * If the pending target was searched / its book is now held / it was ruled out,
     * fall back to a fresh nearest-on-same-plane scan.
     */
    private BookcaseSnapshot resolveSweepTarget(WorldPoint here)
    {
        if (pendingSweepTarget != null)
        {
            BookcaseSnapshot pending = bookcaseAt(pendingSweepTarget);
            if (pending != null && isUsefulSweepCandidate(pending))
            {
                return pending;
            }
            pendingSweepTarget = null;
        }
        return nearestInformativeOnSamePlane(here, SECTION_SWEEP_RADIUS);
    }

    private BookcaseSnapshot bookcaseAt(WorldPoint loc)
    {
        for (BookcaseSnapshot bc : bridge.getBookcases())
        {
            if (loc.equals(bc.getLocation())) return bc;
        }
        return null;
    }

    private boolean isUsefulSweepCandidate(BookcaseSnapshot bc)
    {
        if (bc.isBookSet()) return false;
        if (bc.getPossible().isEmpty()) return false;
        if (bc.getPossible().size() == 1
                && holdsBook(bc.getPossible().iterator().next())) return false;
        return true;
    }

    /**
     * Same-plane BFS: find the closest unsearched bookcase within {@code radius}
     * whose search would either yield a useful book or narrow the upstream solver.
     *
     * Skip rules:
     *  - {@code isBookSet} (already searched, empty)
     *  - {@code possible.isEmpty()} (NO_DATA before solver runs, or ruled out)
     *  - {@code possible.size()==1} and we already hold that book (no gain)
     *
     * Library bookcases are walls, so we BFS through the local collision map and
     * read each candidate's cardinal-neighbor tile (the tile a player stands on
     * to search).
     */
    private BookcaseSnapshot nearestInformativeOnSamePlane(WorldPoint here, int radius)
    {
        Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(here, radius);
        if (reachable == null || reachable.isEmpty()) return null;
        BookcaseSnapshot best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BookcaseSnapshot bc : bridge.getBookcases())
        {
            if (!isUsefulSweepCandidate(bc)) continue;
            if (bc.getLocation().getPlane() != here.getPlane()) continue;
            int d = nearestReachableNeighborDist(bc.getLocation(), reachable);
            if (d == Integer.MAX_VALUE || d > radius) continue;
            if (d < bestDist)
            {
                best = bc;
                bestDist = d;
            }
        }
        return best;
    }

    private boolean holdsBook(BookSnapshot book)
    {
        return book != null && Rs2Inventory.hasItem(book.getItemId());
    }

    /* --------------- DELIVER ------------------ */

    private void handleDeliver(BookSnapshot wanted)
    {
        state = ArceuusLibraryState.DELIVER;

        // Upstream tells us *exactly* who asked for this book. Don't fall back to
        // findCustomerNotAdjacent here — that picks the nearest non-adjacent customer
        // (e.g. Gracklebone) when the actual active one (e.g. Sam) is briefly out of
        // the NPC cache, and we'd walk to and click the wrong NPC.
        int activeId = bridge.getCustomerId();
        Rs2NpcModel customer = activeId > 0
                ? Microbot.getRs2NpcCache().query().withId(activeId).nearestOnClientThread()
                : findCustomerNotAdjacent();
        if (customer == null)
        {
            // Active customer isn't in cache — walk toward their roam tile (non-blocking)
            // to bring them into scene. Don't deliver to anyone else.
            Customer target = activeId != -1 ? Customer.byId(activeId) : null;
            if (target != null)
            {
                currentCustomerLabel = target.getNpcName() + " (loading)";
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
                WorldPoint here = Rs2Player.getWorldLocation();
                WorldPoint roam = target.getRoamCenter();
                log.info("Active customer {} not in cache; walking toward {}",
                        target.getNpcName(), roam);
                walkInBackground(roam);
            }
            else
            {
                currentCustomerLabel = "(searching)";
            }
            return;
        }
        currentCustomerLabel = customer.getName();
        WorldPoint customerLoc = customer.getWorldLocation();
        if (customerLoc == null) return;

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return;
        if (here.getPlane() != customerLoc.getPlane()
                || !isWalkReachable(here, customerLoc))
        {
            log.info("Walking to deliver to {} at {}", customer.getName(), customerLoc);
            walkInBackground(customerLoc);
            return;
        }

        // Same plane and reachable — cancel walker and interact immediately.
        cancelBackgroundWalk();

        if (config.readSoulJourney() && "SOUL_JOURNEY".equals(wanted.getEnumName()))
        {
            Rs2Inventory.interact(wanted.getItemId(), "Read");
            sleep(800, 1200);
            advanceDialogue();
        }

        // Don't click the customer if we don't actually hold the book yet — that path
        // produces a non-progressing dialogue and a spurious delivered++ when
        // sleepUntil(!hasItem) trivially returns. The walk above (re-entering this
        // method from the dispatcher) dismisses any pending item-box modal; we'll
        // come back next tick with the book in inventory.
        if (!Rs2Inventory.hasItem(wanted.getItemId())) return;

        log.info("Delivering {} to {} at {}", wanted.getShortName(), customer.getName(), customerLoc);
        Rs2Npc.hoverOverActor(customer.getNpc());
        if (customer.click("Help"))
        {
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
            advanceDialogue();
            sleepUntil(() -> !Rs2Inventory.hasItem(wanted.getItemId()), 5_000);
            if (!Rs2Inventory.hasItem(wanted.getItemId()))
            {
                delivered++;
                lastDeliveredCustomerId = customer.getId();
                sweepSearchesThisTrip = 0;
                pendingSweepTarget = null;
                recentlyAttempted.clear();
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
        if (id > 0)
        {
            Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                    .withId(id)
                    .nearestOnClientThread();
            if (npc != null) return npc;
        }
        return findCustomerNotAdjacent();
    }

    /**
     * Closest customer NPC strictly farther than 1 tile from the player, excluding
     * the last-delivered customer (who is "satisfied" until we deliver to one of the
     * other two and so will only respond with "go talk to someone else") and any
     * customer we've already attempted in this no-progress cycle. Falls back to
     * ignoring the adjacency filter — but never the exclusion filters.
     */
    private Rs2NpcModel findCustomerNotAdjacent()
    {
        Rs2NpcModel candidate = findCustomer(1, true, true);
        return candidate != null ? candidate : findCustomer(Integer.MIN_VALUE, true, true);
    }

    private Rs2NpcModel findNearestCustomer()
    {
        return findCustomer(Integer.MIN_VALUE, false, false);
    }

    /**
     * Closest library customer NPC (by id) strictly farther than {@code minDist}
     * tiles from the player. When {@code excludeLastDelivered} is true, skips the
     * customer we most recently delivered to (they cannot be the next requester).
     * When {@code excludeRecentlyAttempted} is true, skips customers already clicked
     * in this no-progress cycle so we round-robin through the three.
     */
    private Rs2NpcModel findCustomer(int minDist, boolean excludeLastDelivered, boolean excludeRecentlyAttempted)
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        Rs2NpcModel best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Rs2NpcModel npc : Microbot.getRs2NpcCache().query()
                .withIds(Customer.allIds())
                .toListOnClientThread())
        {
            if (npc.getWorldLocation() == null) continue;
            if (excludeLastDelivered && npc.getId() == lastDeliveredCustomerId) continue;
            if (excludeRecentlyAttempted && recentlyAttempted.contains(npc.getId())) continue;
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

    /**
     * BFS reachability check: can the player walk from {@code from} to a cardinal
     * neighbor of {@code target} without crossing planes or using transports?
     * Returns false when the target is in scene but in a different room (common in
     * the multi-staircase Arceuus Library upper floors).
     */
    private boolean isWalkReachable(WorldPoint from, WorldPoint target)
    {
        if (from.getPlane() != target.getPlane()) return false;
        int dist = from.distanceTo(target);
        Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(from, Math.max(dist + 5, 30));
        if (reachable == null || reachable.isEmpty()) return false;
        return nearestReachableNeighborDist(target, reachable) < Integer.MAX_VALUE;
    }

    private volatile CompletableFuture<Void> backgroundWalk = null;

    private void walkInBackground(WorldPoint target)
    {
        if (backgroundWalk != null && !backgroundWalk.isDone()) return;
        log.info("[walk] starting background walk to {}", target);
        backgroundWalk = CompletableFuture.runAsync(() -> {
            try
            {
                Rs2Walker.walkTo(target, 2);
            }
            catch (Exception e)
            {
                log.warn("[walk] background walk to {} failed: {}", target, e.getMessage());
            }
        });
    }

    private void cancelBackgroundWalk()
    {
        Rs2Walker.setTarget(null);
        if (backgroundWalk != null) backgroundWalk.cancel(true);
        backgroundWalk = null;
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
    public int getSweepSearchesThisTrip() { return sweepSearchesThisTrip; }

    /**
     * Count of distinct library books currently in the player's inventory. Used by
     * the overlay to surface prefetch progress (target: 16 unique books, after which
     * every assignment is satisfied without a fetch trip until the next reshuffle).
     */
    public int distinctBooksHeldCount()
    {
        return (int) Rs2Inventory.items()
                .map(item -> bridge.bookForItemId(item.getId()))
                .filter(java.util.Objects::nonNull)
                .map(BookSnapshot::getEnumName)
                .distinct()
                .count();
    }

    /**
     * Disable all teleports/transports in the pathfinder so it only searches
     * local walking + staircase routes. Cuts pathfinding from ~1.5s / 2M nodes
     * to near-instant for intra-library walks.
     */
    private void restrictPathfinderToLocal()
    {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("useTeleportationSpells", false);
        overrides.put("useTeleportationItems", "NONE");
        overrides.put("useTeleportationMinigames", false);
        overrides.put("useTeleportationPortals", false);
        overrides.put("useTeleportationLevers", false);
        overrides.put("useFairyRings", false);
        overrides.put("useGnomeGliders", false);
        overrides.put("useSpiritTrees", false);
        overrides.put("useBoats", false);
        overrides.put("useCanoes", false);
        overrides.put("useCharterShips", false);
        overrides.put("useShips", false);
        overrides.put("useMagicCarpets", false);
        overrides.put("useWildernessObelisks", false);
        overrides.put("useMinecarts", false);
        overrides.put("useQuetzals", false);

        Map<String, Object> data = new HashMap<>();
        data.put("config", overrides);
        Microbot.getEventBus().post(new PluginMessage("shortestpath", "path", data));
        log.info("Pathfinder restricted to local routes (no teleports/transports)");
    }

    private void clearPathfinderRestrictions()
    {
        Microbot.getEventBus().post(new PluginMessage("shortestpath", "clear"));
        log.info("Pathfinder restrictions cleared");
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        clearPathfinderRestrictions();
        state = ArceuusLibraryState.IDLE;
    }
}
