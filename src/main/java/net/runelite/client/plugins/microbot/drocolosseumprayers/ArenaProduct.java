package net.runelite.client.plugins.microbot.drocolosseumprayers;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

public enum ArenaProduct {
    INFERNO("inferno",2240,5312), COLOSSEUM("colosseum",1792,3072);
    public final String productName;
    private final int x,y;
    ArenaProduct(String productName,int x,int y){this.productName=productName;this.x=x;this.y=y;}
    public boolean tile(int x,int y,int plane){return plane==0&&area(x,y);}
    boolean area(int x,int y){return x>=this.x&&x<this.x+64&&y>=this.y&&y<this.y+64;}
    public boolean inside(Client c){
        if(c==null||c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null||c.getTopLevelWorldView()==null
            ||!c.getTopLevelWorldView().isInstance()||c.getBoostedSkillLevel(Skill.HITPOINTS)<=0)return false;
        var p=WorldPoint.fromLocalInstance(c,c.getLocalPlayer().getLocalLocation());
        return p!=null&&tile(p.getX(),p.getY(),p.getPlane());
    }

    public boolean instanceHeld(Client c){
        if(c==null||c.getGameState()!=GameState.LOGGED_IN||c.getLocalPlayer()==null||c.getTopLevelWorldView()==null
            ||!c.getTopLevelWorldView().isInstance()||c.getBoostedSkillLevel(Skill.HITPOINTS)<=0)return false;
        var p=WorldPoint.fromLocalInstance(c,c.getLocalPlayer().getLocalLocation());
        return p!=null&&area(p.getX(),p.getY());
    }
}
