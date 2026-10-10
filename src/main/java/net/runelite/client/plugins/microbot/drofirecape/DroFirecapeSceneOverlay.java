/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape;
import javax.inject.Inject;
import java.awt.*;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.plugins.microbot.drofirecape.core.*;
import net.runelite.client.plugins.microbot.drofirecape.core.FcModel.*;

public final class DroFirecapeSceneOverlay extends Overlay {
    private final Client client;private final DroFirecapeScript script;private final DroFirecapeConfig config;
    @Inject public DroFirecapeSceneOverlay(Client client,DroFirecapeScript script,DroFirecapeConfig config){this.client=client;this.script=script;this.config=config;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}
    private void tile(Graphics2D g,FcFrame f,Tile tile,int size,Color color,String label) {
        if(tile==null)return;
        LocalPoint p=LocalPoint.fromWorld(client.getTopLevelWorldView(),new WorldPoint(f.baseX+tile.x(),f.baseY+tile.y(),f.plane));if(p==null)return;
        LocalPoint centre=new LocalPoint(p.getX()+(size-1)*64,p.getY()+(size-1)*64);
        Polygon poly=Perspective.getCanvasTileAreaPoly(client,centre,size);
        if(poly!=null){g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),35));g.fill(poly);g.setColor(color);g.draw(poly);}
        net.runelite.api.Point text=Perspective.getCanvasTextLocation(client,g,centre,label,0);
        if(text!=null)OverlayUtil.renderTextLocation(g,text,label,color);
    }
    @Override public Dimension render(Graphics2D graphics) {
        FcFrame f=script.frame();if(config.hideOverlay()||!config.sceneOverlay()||f==null||!f.cave||f.stale())return null;
        if(client.getTopLevelWorldView()==null||client.getTopLevelWorldView().getBaseX()!=f.baseX||client.getTopLevelWorldView().getBaseY()!=f.baseY)return null;
        Plan plan=script.plan();
        if(plan!=null){tile(graphics,f,plan.destination(),1,plan.safe()?Color.GREEN:Color.ORANGE,"Cover");tile(graphics,f,plan.nextStep(),1,Color.CYAN,"Step");
            for(Mob m:f.model.mobs())if(m.index()==plan.targetIndex())tile(graphics,f,m.tile(),m.size(),Color.YELLOW,"Target");}
        int wave=script.wave();
        if(script.predictionReady()&&wave>0&&wave<63)for(WaveBook.Spawned spawn:WaveBook.wave(script.rotation(),wave+1))
            tile(graphics,f,f.spawns.get(spawn.location()),spawn.kind().size,Color.ORANGE,"Next "+spawn.kind());
        return null;
    }
}
