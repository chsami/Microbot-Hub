package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

/** Immutable counts captured together on the client thread from a verified open bank. */
final class ArenaSupplyReceipt {
    final Map<Integer, Long> bank, inventory, equipment, stored;
    final int epoch;
    final Map<Integer,String> names;
    ArenaSupplyReceipt(int epoch, Map<Integer, Long> bank, Map<Integer, Long> inventory,
                       Map<Integer, Long> equipment, Map<Integer, Long> stored) {
        this(epoch,bank,inventory,equipment,stored,Map.of());
    }
    ArenaSupplyReceipt(int epoch, Map<Integer, Long> bank, Map<Integer, Long> inventory,
                       Map<Integer, Long> equipment, Map<Integer, Long> stored,Map<Integer,String> names) {
        if (epoch <= 0) throw new IllegalArgumentException("A live bank receipt is required");
        this.epoch = epoch;
        this.bank = Map.copyOf(bank); this.inventory = Map.copyOf(inventory);
        this.equipment = Map.copyOf(equipment); this.stored = Map.copyOf(stored);
        this.names=Map.copyOf(names);
    }
    long bank(int id) { return bank.getOrDefault(id, 0L); }
    long inventory(int id) { return inventory.getOrDefault(id, 0L); }
    long equipment(int id) { return equipment.getOrDefault(id, 0L); }
    long owned(int id) { return Math.addExact(Math.addExact(bank(id), inventory(id)), equipment(id)); }
    long coins() { return Math.addExact(bank(995), inventory(995)); }
    int deficit(int id, int target) { return Math.toIntExact(Math.max(0, (long) target - owned(id))); }
    /** Allocate captured stacks once, with reassignment for overlapping native accepted variants. */
    Map<ArenaSupplies.Supply,Integer> deficits(List<ArenaSupplies.Supply> supplies){
        Map<Integer,Long> available=new HashMap<>();
        for(Map<Integer,Long> counts:List.of(bank,inventory,equipment))counts.forEach((id,qty)->available.merge(id,qty,Math::addExact));
        List<Integer> ids=new ArrayList<>(new java.util.TreeSet<>(available.keySet()));
        List<ArenaSupplies.Supply> owners=new ArrayList<>();List<ArenaSupplies.Match> demands=new ArrayList<>();
        for(ArenaSupplies.Supply supply:supplies)for(ArenaSupplies.Match match:supply.matches){owners.add(supply);demands.add(match);}
        int firstItem=1,firstDemand=firstItem+ids.size(),sink=firstDemand+demands.size();
        Flow flow=new Flow(sink+1);
        for(int i=0;i<ids.size();i++){
            int id=ids.get(i);long owned=available.get(id);if(owned<=0)continue;
            flow.add(0,firstItem+i,owned);
            for(int d=0;d<demands.size();d++)if(demands.get(d).accepts(id,names.get(id)))
                flow.add(firstItem+i,firstDemand+d,Math.min(owned,(long)demands.get(d).quantity));
        }
        List<Flow.Edge> residual=new ArrayList<>();
        for(int d=0;d<demands.size();d++)residual.add(flow.add(firstDemand+d,sink,demands.get(d).quantity));
        flow.assign(0,sink);
        Map<ArenaSupplies.Supply,Integer> missing=new LinkedHashMap<>();
        for(int d=0;d<residual.size();d++)if(residual.get(d).capacity>0)
            missing.merge(owners.get(d),Math.toIntExact(residual.get(d).capacity),Math::addExact);
        return missing;
    }
    /** Capacitated bipartite matching: nodes represent stacks/requirements, never individual arrows. */
    private static final class Flow {
        private static final class Edge {
            final int to,reverse;long capacity;
            Edge(int to,int reverse,long capacity){this.to=to;this.reverse=reverse;this.capacity=capacity;}
        }
        final List<List<Edge>> edges=new ArrayList<>();final int[] level,next;
        Flow(int size){level=new int[size];next=new int[size];for(int i=0;i<size;i++)edges.add(new ArrayList<>());}
        Edge add(int from,int to,long capacity){
            if(capacity<0)throw new IllegalArgumentException("Negative owned supply capacity");
            Edge forward=new Edge(to,edges.get(to).size(),capacity),backward=new Edge(from,edges.get(from).size(),0);
            edges.get(from).add(forward);edges.get(to).add(backward);return forward;
        }
        void assign(int source,int sink){
            while(levels(source,sink)){
                java.util.Arrays.fill(next,0);
                while(send(source,sink,Long.MAX_VALUE)>0){ /* Reassign existing matches through residual edges. */ }
            }
        }
        boolean levels(int source,int sink){
            java.util.Arrays.fill(level,-1);level[source]=0;
            var queue=new java.util.ArrayDeque<Integer>();queue.add(source);
            while(!queue.isEmpty()){
                int node=queue.remove();
                for(Edge edge:edges.get(node))if(edge.capacity>0&&level[edge.to]<0){level[edge.to]=level[node]+1;queue.add(edge.to);}
            }
            return level[sink]>=0;
        }
        long send(int node,int sink,long amount){
            if(node==sink)return amount;
            for(;next[node]<edges.get(node).size();next[node]++){
                Edge edge=edges.get(node).get(next[node]);if(edge.capacity<=0||level[edge.to]!=level[node]+1)continue;
                long moved=send(edge.to,sink,Math.min(amount,edge.capacity));
                if(moved>0){edge.capacity-=moved;Edge reverse=edges.get(edge.to).get(edge.reverse);reverse.capacity=Math.addExact(reverse.capacity,moved);return moved;}
            }
            return 0;
        }
    }
}
