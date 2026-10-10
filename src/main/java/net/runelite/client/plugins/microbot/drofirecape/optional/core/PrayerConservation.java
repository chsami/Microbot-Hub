package net.runelite.client.plugins.microbot.drofirecape.optional.core;
import net.runelite.client.plugins.microbot.drofirecape.optional.core.FcModel.*;
/** Optional demand policy only. The prayer clocks and atomic reset transport are unchanged. */
public final class PrayerConservation {
    private PrayerConservation(){}
    public static boolean offence(boolean configured,boolean conservation,int wave,Snapshot scene) {
        if(!configured)return false;
        if(!conservation||wave<0||wave>=31)return true;
        // A resumed/unknown wave with a live mage or Jad is never treated as an early wave.
        return scene!=null&&scene.mobs().stream().anyMatch(m->m.kind()==Kind.MAGER||m.kind()==Kind.JAD);
    }
    public static boolean earlyGap(boolean enabled,int nextWave,Snapshot scene) {
        return enabled&&nextWave>=0&&nextWave<=30&&scene!=null&&scene.mobs().isEmpty();
    }
    /** Pure pauses are not mandatory every configured wave. Emergency healing,
     * active input deadlines and the existing pause safety gates still run first. */
    public static boolean recoveryNeeded(int hp,int maximum) {
        return maximum>0&&hp>0&&(long)hp*100<=maximum*70L;
    }
    /** Distance-only admission, not a claim that a nearby shooter is trapped.
     * Reserve four tiles for player/NPC approach; a checked long route can still
     * explicitly pre-arm its required prayer after this idle-hold decision. */
    public static boolean remote(Snapshot s) {
        if(s==null||s.meleeMode())return false;
        for(Mob m:s.mobs())if(m.kind()==Kind.MAGER||m.kind()==Kind.JAD
            ||m.distance(s.player())<=m.kind().range+4)return false;
        return true;
    }
}
