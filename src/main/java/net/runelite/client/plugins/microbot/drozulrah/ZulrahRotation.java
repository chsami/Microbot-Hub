package net.runelite.client.plugins.microbot.drozulrah;

import net.runelite.api.coords.LocalPoint;

/** Rotation data distilled from the supplied Zulrah helper and recorder sessions. */
public enum ZulrahRotation {
    // Names intentionally match the attached RotationType.java exactly.
    A(new int[]{2042,2043,2044,2042,2043,2044,2042,2044,2042,2043},
      new Spot[]{Spot.CENTER,Spot.CENTER,Spot.CENTER,Spot.NORTH,Spot.CENTER,Spot.EAST,Spot.NORTH,Spot.NORTH,Spot.EAST,Spot.CENTER},
      new Stand[]{Stand.SW,Stand.SW,Stand.SW,Stand.EP,Stand.EP,Stand.EP,Stand.WP,Stand.WPN,Stand.EP,Stand.SW},
      new int[]{28,21,18,39,22,20,28,36,48,21}, 8),
    B(new int[]{2042,2043,2044,2042,2044,2043,2042,2044,2042,2043},
      new Spot[]{Spot.CENTER,Spot.CENTER,Spot.CENTER,Spot.EAST,Spot.NORTH,Spot.CENTER,Spot.WEST,Spot.NORTH,Spot.EAST,Spot.CENTER},
      new Stand[]{Stand.SW,Stand.SW,Stand.SW,Stand.EP,Stand.EPN,Stand.EP,Stand.MIDDLE,Stand.EP,Stand.EP,Stand.SW},
      new int[]{28,21,18,28,39,21,20,36,48,21}, 8),
    C(new int[]{2042,2042,2043,2044,2042,2044,2042,2042,2044,2044,2044},
      new Spot[]{Spot.CENTER,Spot.WEST,Spot.CENTER,Spot.EAST,Spot.NORTH,Spot.WEST,Spot.CENTER,Spot.EAST,Spot.CENTER,Spot.WEST,Spot.CENTER},
      new Stand[]{Stand.SW,Stand.SW,Stand.SE,Stand.EP,Stand.WP,Stand.WP,Stand.EP,Stand.EP,Stand.WP,Stand.WP,Stand.SW},
      new int[]{28,30,40,20,20,20,25,20,36,35,18}, 9),
    D(new int[]{2042,2044,2042,2044,2043,2042,2042,2044,2042,2044,2044,2044},
      new Spot[]{Spot.CENTER,Spot.WEST,Spot.NORTH,Spot.EAST,Spot.CENTER,Spot.WEST,Spot.NORTH,Spot.EAST,Spot.CENTER,Spot.CENTER,Spot.WEST,Spot.CENTER},
      new Stand[]{Stand.SW,Stand.SW,Stand.EP,Stand.EP,Stand.WP,Stand.WP,Stand.WP,Stand.EP,Stand.WP,Stand.WP,Stand.WP,Stand.SW},
      new int[]{28,36,24,30,28,17,34,33,20,27,29,18}, 10);

    public enum Spot {
        CENTER(6720,7616), WEST(8000,7360), EAST(5440,7360), NORTH(6720,6208);
        final int x,y; Spot(int x,int y){this.x=x;this.y=y;}
        boolean matches(LocalPoint p){ return p != null && Math.abs(p.getX()-x) <= 128 && Math.abs(p.getY()-y) <= 128; }
    }

    public enum Stand {
        SW(7488,7872), SW_MELEE(7232,8000),
        WP(7232,7232), WPN(7232,7104),
        EP(6208,7232), EPN(6208,7104),
        SE(6208,8000), SE_MELEE(5952,7744),
        MIDDLE(6720,6848);
        public final int x,y; Stand(int x,int y){this.x=x;this.y=y;}
        public LocalPoint local(){ return new LocalPoint(x,y); }
        public Stand meleeAlternate(){
            switch(this){
                case SW: return SW_MELEE; case SW_MELEE: return SW;
                case SE: return SE_MELEE; case SE_MELEE: return SE;
                case WP: case WPN: return SW_MELEE;
                case EP: case EPN: return SE_MELEE;
                default: return SW_MELEE;
            }
        }
    }

    final int[] types; final Spot[] spots; final Stand[] stands; final int[] ticks; final int jadIndex;
    ZulrahRotation(int[] types, Spot[] spots, Stand[] stands, int[] ticks, int jadIndex){
        this.types=types; this.spots=spots; this.stands=stands; this.ticks=ticks; this.jadIndex=jadIndex;
    }
    public boolean matches(int index,int npcId,LocalPoint p){ return index < types.length && types[index]==npcId && spots[index].matches(p); }
    public Stand stand(int index){ return index >= 0 && index < stands.length ? stands[index] : Stand.SW; }
    public int ticks(int index){ return index >= 0 && index < ticks.length ? ticks[index] : 24; }
    public boolean isJad(int index){ return index==jadIndex; }
    public int size(){ return types.length; }
}
