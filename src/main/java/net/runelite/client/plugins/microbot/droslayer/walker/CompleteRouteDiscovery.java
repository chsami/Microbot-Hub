package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2RouteResult;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.droslayer.walker.support.Rs2TransportLoadout;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Bounded comparison of complete, supply-validated journeys. Never issues game actions. */
final class CompleteRouteDiscovery
{
    static final int INF = Integer.MAX_VALUE / 4;
    private final Function<Rs2RouteRequest, Rs2RouteResult> planner;
    private final BiFunction<List<Rs2TransportEdge>, Map<Integer,Integer>, Rs2TransportLoadout> supplies;
    private final Consumer<String> log;
    private final LongSupplier clock;
    private final Map<List<Object>, Rs2RouteResult> cache = new HashMap<>();
    private long deadline;
    private Choice best;
    private Consumer<Choice> progress = choice -> { };
    CompleteRouteDiscovery withProgress(Consumer<Choice> progress) { this.progress=progress; return this; }
    CompleteRouteDiscovery(Function<Rs2RouteRequest, Rs2RouteResult> planner,
        BiFunction<List<Rs2TransportEdge>, Map<Integer,Integer>, Rs2TransportLoadout> supplies,
        Consumer<String> log, LongSupplier clock) {
        this.planner=planner; this.supplies=supplies; this.log=log; this.clock=clock;
    }
    Choice compare(WorldPoint start, WorldPoint target, boolean bank,
        List<WeightedRouteAdvisor.BankedTeleportOption> options,
        BiFunction<WorldPoint,WorldPoint,Integer> priority) {
        cache.clear();
        best=null;
        deadline=clock.getAsLong()+8_000_000_000L;
        // Each graph search includes ALL eligible graph teleports and downstream transports.
        // Explicit provider searches cover providers not represented in that graph.
        evaluate("carried complete route",start,target,false,null);
        if (bank) evaluate("bank-funded complete route",start,target,true,null);
        if (bank) {
            List<WeightedRouteAdvisor.BankedTeleportOption> sorted=new ArrayList<>(options);
            Map<WorldPoint,Integer> ranks=new HashMap<>();
            for(var o:sorted) ranks.computeIfAbsent(o.landing,p->priority.apply(p,target));
            sorted.sort(Comparator.comparingInt(o->ranks.get(o.landing)));
            for(var option:sorted) evaluate(option.name,option.landing,target,true,option);
        }
        log.accept("finished evaluatedSearches="+cache.size()+" winner="+(best==null?"none":best.name)
            +" estimatedSeconds="+(best==null?"unknown":RouteTimeCost.seconds(best.cost))
            +" globalOptimalityProven=false");
        return best;
    }
    private void evaluate(String name,WorldPoint start,WorldPoint target,boolean bank,
        WeightedRouteAdvisor.BankedTeleportOption prefix) {
        if(Thread.currentThread().isInterrupted())throw new CancellationException();
        List<Object> key=List.of(start,target,bank);
        if(!cache.containsKey(key)) {
            if(cache.size()>=12||clock.getAsLong()>=deadline) {
                log.accept("candidate="+name+" status=NOT_EVALUATED_BUDGET");return;
            }
            cache.put(key,planner.apply(Rs2RouteRequest.to(start,target).withBankItems(bank)));
        }
        Rs2RouteResult route=cache.get(key);
        if(route==null||!route.isTargetReached(0)||route.getPath().isEmpty()
            ||!target.equals(route.getPath().get(route.getPath().size()-1))) {
            log.accept("candidate="+name+" status="+(route==null?"UNAVAILABLE":route.getTerminationReason()));return;
        }
        Rs2TransportLoadout loadout=supplies.apply(TransportSupplyPlanner.edges(route),prefix==null?Map.of():prefix.requirements);
        if(!loadout.isSatisfiable()||(!bank&&!loadout.getWithdrawals().isEmpty())) {
            log.accept("candidate="+name+" status=UNSATISFIABLE_LOADOUT");return;
        }
        long total=WeightedRouteAdvisor.routeCost(route.getSteps())+(prefix==null?0:RouteTimeCost.ITEM_TELEPORT)
            +(loadout.getWithdrawals().isEmpty()?0:RouteTimeCost.BANK+4L*loadout.getWithdrawals().size())
            +4L*loadout.getEquipmentItemIds().size();
        if(total>=INF)return;
        Choice choice=new Choice(name,route,prefix,loadout,(int)total);
        log.accept("candidate="+name+" status=COMPLETE estimatedSeconds="+RouteTimeCost.seconds(choice.cost)
            +" withdrawals="+loadout.getWithdrawals()+" equipment="+loadout.getEquipmentItemIds()
            +" transports="+TransportSupplyPlanner.edges(route).stream().map(Rs2TransportEdge::getDisplayInfo).collect(java.util.stream.Collectors.toList()));
        if(best==null||choice.cost<best.cost) { best=choice; progress.accept(choice); }
    }
    static final class Choice {
        final String name;final Rs2RouteResult route;final WeightedRouteAdvisor.BankedTeleportOption prefix;
        final Rs2TransportLoadout loadout;final int cost;
        Choice(String name,Rs2RouteResult route,WeightedRouteAdvisor.BankedTeleportOption prefix,Rs2TransportLoadout loadout,int cost){
            this.name=name;this.route=route;this.prefix=prefix;this.loadout=loadout;this.cost=cost;
        }
        WorldPoint firstTeleportLanding(){
            if(prefix!=null)return prefix.landing;
            if(route.getSteps().isEmpty())return null;
            var first=route.getSteps().get(0).getTransport().orElse(null);
            return first!=null&&first.isTeleport()?first.getDestination():null;
        }
    }
}
