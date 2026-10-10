/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

import java.util.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

/** Progress toward an intention, not acknowledgement of each successive short click. */
public final class LureProgress {
    private final Set<Tile> visited=new HashSet<>();
    private Tile goal,last;
    private int tick=-1,lastNewTile=-1,revisits;
    public void reset(){visited.clear();goal=last=null;tick=lastNewTile=-1;revisits=0;}
    public boolean stalled(Snapshot s,Tile destination) {
        if(destination==null||destination.equals(s.player())){reset();return false;}
        if(!destination.equals(goal)||s.tick()<tick||tick>=0&&s.tick()-tick>18) {
            reset();goal=destination;lastNewTile=s.tick();
        }
        tick=s.tick();
        if(!s.player().equals(last)) {
            if(visited.add(s.player())){lastNewTile=tick;revisits=0;}
            else revisits++;
            last=s.player();
        }
        // Curved routes may move away from the endpoint. New terrain still counts;
        // A-B-A-B and NPCs following those clicks do not indefinitely renew a lure.
        return revisits>=3||tick-lastNewTile>=18;
    }
    public boolean visited(Tile tile){return visited.contains(tile);}
}
