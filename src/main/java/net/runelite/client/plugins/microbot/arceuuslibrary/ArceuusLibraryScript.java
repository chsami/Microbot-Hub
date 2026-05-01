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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /**
     * Section sweep tuning. The radius is a BFS-tile cap that keeps the sweep within
     * one library wing — the wings are ~10–14 tiles across, so 14 lets us sweep the
     * whole section we're standing in without straying into the next building. The
     * per-round cap bounds the worst case where solver=INCOMPLETE keeps surfacing
     * informative neighbours; 6 covers a typical wing pass with one wanted-fetch.
     */
    private static final int SECTION_SWEEP_RADIUS = 14;
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
                // Wanted book is in inventory. Before delivering, sweep nearby
                // unsearched bookcases — narrows the solver and may stash books
                // we don't already hold for future deliveries.
                if (trySectionSweep()) return;
                handleDeliver(wanted);
                return;
            }

            // Fetch path. While solver is INCOMPLETE the wanted candidate may be
            // far away with closer informative bookcases between us — sweep them
            // first. Radius caps the detour; once nothing useful is in range, fall
            // through to the long walk.
            if (trySectionSweep()) return;
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

                log.info("[{}] no candidate clickable; walking to {}'s roam {}",
                        reason, untried.getNpcName(), roam);
                Rs2Walker.walkTo(roam, 4);
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
        if (!isInWalkingReach(here, customerLoc))
        {
            // Out of scene or behind walls — let Rs2Walker route via stairs/transports.
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
            log.info("[{}] walking to {} at {}", reason, customer.getName(), customerLoc);
            Rs2Walker.walkTo(customerLoc, CUSTOMER_REACH);
            return;
        }

        // In scene reach. Take over from Rs2Walker — click("Help") will auto-walk
        // the remaining tiles, faster than Rs2Walker's final approach.
        Rs2Walker.setTarget(null);

        // Don't re-click during click-auto-walk or any animation.
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

        log.info("[{}] helping {} at {}", reason, customer.getName(), customerLoc);
        Rs2Npc.hoverOverActor(customer.getNpc());
        if (customer.click("Help"))
        {
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
            int booksHeldBefore = countLibraryBooksHeld();
            advanceDialogue();
            boolean gotRequest = bridge.getCustomerBook().isPresent();
            boolean gaveBook = countLibraryBooksHeld() < booksHeldBefore;
            if (gotRequest || gaveBook)
            {
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
        log.info("No customer in scene; walking to ground-floor hub {} from {}",
                CUSTOMER_HUB, Rs2Player.getWorldLocation());
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

        if (!isInWalkingReach(here, loc))
        {
            // Out of scene or behind walls — let Rs2Walker route via stairs/transports.
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
            if (state != ArceuusLibraryState.WALK_TO_BOOKCASE)
            {
                log.info("Walking to bookcase {} (plane {}); {}/{} candidates, solver={}",
                        loc, loc.getPlane(), candidates.size(), bridge.getBookcases().size(), solverState);
            }
            state = ArceuusLibraryState.WALK_TO_BOOKCASE;
            Rs2Walker.walkTo(loc, 5);
            return;
        }

        // In scene reach. Take over from Rs2Walker — its final approach is slow,
        // and the menu-invoke from interact() will auto-walk the remaining tiles.
        Rs2Walker.setTarget(null);

        // Don't re-click during menu-invoke auto-walk or the search animation.
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

        state = ArceuusLibraryState.SEARCH_BOOKCASE;
        searchBookcase(loc, wanted);
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
        sleepUntil(Rs2Player::isAnimating, 1_500);
        sleepUntil(() -> Rs2Inventory.hasItem(wanted.getItemId()) || !Rs2Player.isAnimating(), 5_000);
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

    /**
     * Walking-reachable gate. Same plane and either {@code loc} itself or one of its
     * cardinal neighbours sits within {@link #IN_SCENE_REACH} BFS tiles of the player.
     *
     * Replaces a {@link WorldPoint#distanceTo}-based gate that produced false positives:
     * Chebyshev measures straight-line tile distance and ignores walls, so a bookcase 18
     * tiles away on the same upstairs plane can read "in scene" while the actual walking
     * route requires going down stairs, across, and back up. The BFS uses the local
     * collision map, so walls are honoured. Bookcases are walls themselves — we test the
     * standing tile (a cardinal neighbour) — while NPC tiles are walkable so the direct
     * lookup catches them.
     */
    private boolean isInWalkingReach(WorldPoint here, WorldPoint loc)
    {
        if (here == null || loc == null) return false;
        if (here.getPlane() != loc.getPlane()) return false;
        Map<WorldPoint, Integer> reachable = Rs2Tile.getReachableTilesFromTile(here, IN_SCENE_REACH);
        if (reachable.containsKey(loc)) return true;
        return nearestReachableNeighborDist(loc, reachable) <= IN_SCENE_REACH;
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

        BookcaseSnapshot best = nearestInformativeOnSamePlane(here, SECTION_SWEEP_RADIUS);
        if (best == null) return false;

        // Representative book for the search-completion wait predicate. For multi-
        // possible bookcases this is a guess; the animation-stop fallback in
        // searchBookcase() handles a wrong guess correctly.
        BookSnapshot expected = best.getPossible().iterator().next();
        WorldPoint loc = best.getLocation();

        if (Rs2Dialogue.isInDialogue())
        {
            advanceDialogue();
            return true;
        }

        if (!isInWalkingReach(here, loc))
        {
            // Out of scene or behind walls — let Rs2Walker route via stairs/transports.
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return true;
            if (state != ArceuusLibraryState.SECTION_SWEEP)
            {
                log.info("Sweep: walking to {} (possible={}); {}/{}",
                        loc, best.getPossible().size(),
                        sweepSearchesThisTrip + 1, SECTION_SWEEP_MAX_PER_ROUND);
            }
            state = ArceuusLibraryState.SECTION_SWEEP;
            Rs2Walker.walkTo(loc, 5);
            return true;
        }

        // In scene reach. Take over from Rs2Walker — its final approach is slow,
        // and the menu-invoke from interact() will auto-walk the remaining tiles.
        Rs2Walker.setTarget(null);

        // Don't re-click during menu-invoke auto-walk or the search animation.
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return true;

        state = ArceuusLibraryState.SECTION_SWEEP;
        searchBookcase(loc, expected);
        sweepSearchesThisTrip++;
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
        BookcaseSnapshot best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BookcaseSnapshot bc : bridge.getBookcases())
        {
            if (bc.isBookSet()) continue;
            if (bc.getPossible().isEmpty()) continue;
            if (bc.getLocation().getPlane() != here.getPlane()) continue;
            if (bc.getPossible().size() == 1
                    && holdsBook(bc.getPossible().iterator().next())) continue;
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
        Rs2NpcModel customer = activeId != -1
                ? Microbot.getRs2NpcCache().query().withId(activeId).nearestOnClientThread()
                : findCustomerNotAdjacent();
        if (customer == null)
        {
            // Active customer isn't reachable in cache — walk to their roam tile to
            // bring them into scene. Don't deliver to anyone else.
            Customer target = activeId != -1 ? Customer.byId(activeId) : null;
            if (target != null)
            {
                currentCustomerLabel = target.getNpcName() + " (loading)";
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
                // If we're already standing at the roam tile and the cache still misses,
                // re-issuing the same walkTo is a no-op (walker reports ARRIVED). Bounce
                // to the customer hub instead — actually moving the player refreshes the
                // scene/NPC cache, and from the hub we'll bounce back to roam next tick.
                WorldPoint here = Rs2Player.getWorldLocation();
                WorldPoint roam = target.getRoamCenter();
                WorldPoint dest = (here != null
                        && here.getPlane() == roam.getPlane()
                        && here.distanceTo(roam) <= 4)
                        ? CUSTOMER_HUB
                        : roam;
                log.info("Active customer {} not in cache (at {}); walking to {}",
                        target.getNpcName(), here, dest);
                Rs2Walker.walkTo(dest, 4);
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
        if (!isInWalkingReach(here, customerLoc))
        {
            // Out of scene or behind walls — let Rs2Walker route via stairs/transports.
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;
            log.info("Walking to deliver to {} at {}", customer.getName(), customerLoc);
            Rs2Walker.walkTo(customerLoc, CUSTOMER_REACH);
            return;
        }

        // In scene reach. Take over from Rs2Walker — its final approach is slow;
        // click("Help") will auto-walk the remaining tiles.
        Rs2Walker.setTarget(null);

        // Don't re-click during click-auto-walk or any animation (read anim for
        // Soul Journey, walking to NPC, etc.).
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

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

    @Override
    public void shutdown()
    {
        super.shutdown();
        state = ArceuusLibraryState.IDLE;
    }
}
