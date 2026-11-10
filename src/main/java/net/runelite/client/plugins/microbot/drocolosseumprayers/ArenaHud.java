package net.runelite.client.plugins.microbot.drocolosseumprayers;

/** Immutable local display data. No credentials, network bodies or live entities reach render. */
public final class ArenaHud {
    public final ArenaProduct product;
    public final String status;
    public final int wave,hp,prayer,spec,brewDoses,restoreDoses;
    public final long startedAt,waveAt;
    /** One word for what the engine is doing now (Attack, Move, Dodge, Heal, Recover, ...), or null. */
    public final String state;
    /** The run's modifiers as "key:level" tokens, e.g. "blasphemy:3", in the overlay's order. */
    public final java.util.List<String> modifiers;
    public ArenaHud(ArenaProduct product,String status,int wave,int hp,int prayer,int spec,int brewDoses,int restoreDoses,long startedAt,long waveAt){
        this(product,status,wave,hp,prayer,spec,brewDoses,restoreDoses,startedAt,waveAt,null,java.util.List.of());
    }
    public ArenaHud(ArenaProduct product,String status,int wave,int hp,int prayer,int spec,int brewDoses,int restoreDoses,long startedAt,long waveAt,
                    String state,java.util.List<String> modifiers){
        this.product=product;this.status=status;this.wave=wave;this.hp=hp;this.prayer=prayer;this.spec=spec;
        this.brewDoses=brewDoses;this.restoreDoses=restoreDoses;this.startedAt=startedAt;this.waveAt=waveAt;
        this.state=state;this.modifiers=java.util.List.copyOf(modifiers);
    }
    public String title(){return product==ArenaProduct.INFERNO?"Inferno":"Colosseum";}
}
