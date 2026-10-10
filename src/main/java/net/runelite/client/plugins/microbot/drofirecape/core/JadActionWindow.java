package net.runelite.client.plugins.microbot.drofirecape.core;

/** Prayer-first scheduling for optional tab/healer actions, not a prayer predictor. */
public final class JadActionWindow {
    private int consumedAttack=-1000;
    public void reset(){consumedAttack=-1000;}
    public boolean available(int tick,int lastAttack,boolean overheadConfirmed) {
        int age=tick-lastAttack;
        return overheadConfirmed && lastAttack>=0 && age>=1 && age<=4 && consumedAttack!=lastAttack;
    }
    public void consumed(int lastAttack){consumedAttack=lastAttack;}
}
