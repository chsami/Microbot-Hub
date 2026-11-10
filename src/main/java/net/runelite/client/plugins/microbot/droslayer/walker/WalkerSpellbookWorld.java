package net.runelite.client.plugins.microbot.droslayer.walker;

import java.util.*;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/** A normal members world after visiting a hosted spellbook altar. */
final class WalkerSpellbookWorld {
    static OptionalInt select(List<World> worlds,Random random){
        if(worlds==null)return OptionalInt.empty();
        List<World> eligible=new ArrayList<>();
        for(World world:worlds)if(world!=null&&world.getId()!=330&&world.getPlayers()>=0&&world.getPlayers()<1950
            &&world.getTypes()!=null&&world.getTypes().contains(WorldType.MEMBERS)
            &&world.getTypes().stream().allMatch(type->type==WorldType.MEMBERS))eligible.add(world);
        return eligible.isEmpty()?OptionalInt.empty():OptionalInt.of(eligible.get(random.nextInt(eligible.size())).getId());
    }
    private WalkerSpellbookWorld(){}
}
