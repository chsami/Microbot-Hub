package net.runelite.client.plugins.microbot.droinfernoprayers;

/** Immutable local display data. No credentials, network bodies or live entities reach render. */
public final class ArenaHud {
    public final ArenaProduct product;
    public final String status;
    public final int wave,hp,prayer,spec,brewDoses,restoreDoses;
    public final long startedAt,waveAt;
    public ArenaHud(ArenaProduct product,String status,int wave,int hp,int prayer,int spec,int brewDoses,int restoreDoses,long startedAt,long waveAt){
        this.product=product;this.status=status;this.wave=wave;this.hp=hp;this.prayer=prayer;this.spec=spec;
        this.brewDoses=brewDoses;this.restoreDoses=restoreDoses;this.startedAt=startedAt;this.waveAt=waveAt;
    }
    public String title(){return product==ArenaProduct.INFERNO?"Inferno":"Colosseum";}
}
