package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonObject;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/** Live dose/HP receipts, with the source 55% default and no Inferno-only ranging-wave option. */
final class ColosseumSupplies
{
    enum Kind { BREW, RESTORE, PRAYER, RANGE, COMBAT, STAMINA, REGEN, ANTIDOTE, ANTIVENOM, SANFEW }
    static final int BREW_CEILING_PERCENT=92;
    private int vitals=55,brews,baseline;
    private long requested=-1000,next=-1000;
    private Kind pending;
    private ColosseumSnapshot previous;
    /**
     * Poison and venom (the poison varp, venom from 1,000,000) hit every 30 ticks and change the varp on that tick
     * (local 19:58 wave 9: 1,000,000 to 1,000,018 at 30-frame steps). Sanfew serum cures it; drunk on the tick before
     * the next hit it costs no damage. Without an observed hit the cure goes 8-12 seconds after poisoning, randomized.
     */
    static final int POISON_PERIOD=30,POISON_FALLBACK_MIN=14,POISON_FALLBACK_MAX=20;
    /** With prayer this low a poisoned player drinks the sanfew at once: it restores both. */
    static final int SANFEW_PRAYER=25;
    /** Venom values start here in the poison varp. */
    static final int VENOM=1_000_000;
    private long poisonChanged=-1,poisonDeadline=-1,cureRequested=-1000;
    private int poisonValue;
    private final java.util.Random random=new java.util.Random();
    void configure(int percent){vitals=percent<=0?55:Math.min(99,percent);}
    /** "Potions from wave" (user, 2026-11-05): Ranging and combat re-pots wait until this wave; 1 re-pots from the start. */
    private int potionsFromWave=1,wave;
    void potionsFrom(int fromWave){potionsFromWave=Math.max(1,Math.min(12,fromWave));}
    void wave(int current){wave=current;}
    boolean boostsAllowed(){return wave>=potionsFromWave;}
    /** The Eat at HP % setting: no healing cycle starts at or above it. */
    int vitals(){return vitals;}
    /**
     * HP strictly below Eat at HP % of the modifier-reduced maximum, without integer truncation: 36 of 67 is 53.7%,
     * under 55% (20261027-081024 t2788, 20261030-201332 t3635: the truncated 67*55/100=36 held both brews back).
     */
    static final boolean EAT_FLOOR=!"false".equals(System.getProperty("colo.eatFloor"));
    static final boolean EMERGENCY=!"false".equals(System.getProperty("colo.emergencyBrew"));
    static final int EMERGENCY_PERCENT=35,EMERGENCY_PRAYER=5;
    boolean belowVitals(ColosseumSnapshot f){return f.hp*100<f.effectiveMaxHp*vitals;}
    void reset(){pending=null;previous=null;brews=baseline=0;requested=next=-1000;poisonChanged=poisonDeadline=-1;cureRequested=-1000;poisonValue=0;}
    void observe(ColosseumSnapshot f)
    {
        if(f.poison<=0){poisonChanged=poisonDeadline=-1;}
        else
        {
            // A hit fixes the timing; until one is seen (or after a dose that did not cure) the fallback applies.
            // A cure dose changes the varp too (venom turns to poison): that is not a hit and keeps the fallback.
            // A dose that turns venom into poison is followed by the next cure at the next drink (all four server
            // venom cures: 20261027-090352 t3735/3739, 20261027-131142 t3819/3823 and t5332/5336, 20261030-201332
            // t5848/5852), not by another randomized wait.
            if(poisonValue>=VENOM&&f.poison<VENOM){poisonChanged=-1;poisonDeadline=f.tick;}
            else if(poisonValue>0&&f.poison!=poisonValue&&f.tick-cureRequested>3){poisonChanged=f.tick;poisonDeadline=-1;}
            else if(poisonValue<=0)poisonDeadline=f.tick+fallback();
        }
        poisonValue=f.poison;
        Kind consumed=null;
        if(pending!=null&&f.tick>requested)
        {
            int delta=baseline-doses(f,pending);
            if(delta==1)consumed=pending;
            else if(delta!=0)pending=null;
        }
        // Observe real consumption even when it followed a manual input or resumed control.
        // Require both a single dose mutation and its skill/timer receipt, not a dropped vial.
        if(consumed==null&&previous!=null&&previous.tick==f.tick-1)
        {
            // A hit in the same tick can cancel the brew's heal (20261025-140649 t1502: 49 to 41 HP while Magic fell
            // 94 to 83); the brew's drain of 10% + 2 is its receipt then. A one-point Magic decay is not.
            if(doses(previous,Kind.BREW)-doses(f,Kind.BREW)==1
                &&f.magic<previous.magic&&(f.hp>=previous.hp||previous.magic-f.magic>=2))consumed=Kind.BREW;
            else if(doses(previous,Kind.RESTORE)-doses(f,Kind.RESTORE)==1
                &&(f.magic>previous.magic||f.prayer>previous.prayer))consumed=Kind.RESTORE;
            // A cure dose that changes the poison varp, so a cure the player (or a lost input) drank paces the next
            // drink too (20261027-131142 t3819: the server's sanfew turned venom to poison under our ranging request).
            else if((doses(previous,Kind.SANFEW)-doses(f,Kind.SANFEW)==1||doses(previous,Kind.ANTIDOTE)-doses(f,Kind.ANTIDOTE)==1
                ||doses(previous,Kind.ANTIVENOM)-doses(f,Kind.ANTIVENOM)==1)
                &&previous.poison>0&&f.poison!=previous.poison)consumed=Kind.SANFEW;
            else if(doses(previous,Kind.REGEN)-doses(f,Kind.REGEN)==1
                &&integer(f.player,"prayerRegenTimer",0)>integer(previous.player,"prayerRegenTimer",0))consumed=Kind.REGEN;
        }
        if(consumed!=null)
        {
            if(consumed==Kind.BREW)brews++;if(consumed==Kind.RESTORE)brews=0;
            pending=null;next=f.tick+3;
        }
        previous=f;
    }
    JsonObject next(ColosseumSnapshot f){return next(f,false,false);}
    JsonObject next(ColosseumSnapshot f,boolean mage){return next(f,mage,false);}
    private int fallback(){return POISON_FALLBACK_MIN+random.nextInt(POISON_FALLBACK_MAX-POISON_FALLBACK_MIN+1);}
    /** Next poison hit within two ticks, or the randomized fallback has passed. */
    boolean poisonDue(ColosseumSnapshot f)
    {
        if(f.poison<=0)return false;
        if(poisonChanged>=0&&POISON_PERIOD-(f.tick-poisonChanged)%POISON_PERIOD<=2)return true;
        return poisonDeadline>=0&&f.tick>=poisonDeadline;
    }
    /**
     * @param mage the next attack is a Blood spell: below its Magic level a restore comes before more brews
     *             (local 2026-11-02 t1039-1052: three brews left Magic at 64 and the staff idle for 13 ticks)
     * @param bloodHeal Blood spells are healing the player on the last enemy: no brew beyond the Eat at HP % rule
     */
    JsonObject next(ColosseumSnapshot f,boolean mage,boolean bloodHeal){return next(f,mage,bloodHeal,-1,0);}
    /** Blood heal due with Magic below the spell ({@link ColosseumBlood#drained}): a restore, else a sanfew. */
    JsonObject restoreForBlood(ColosseumSnapshot f)
    {
        if(f.tick<next||pending!=null&&f.tick<=requested)return null;
        JsonObject restore=drink(f,Kind.RESTORE);
        return restore!=null?restore:drink(f,Kind.SANFEW);
    }
    /**
     * @param threat with "Blood spells on the last enemy" on: the unprotected damage predicted over the next four ticks
     *               ({@link #threatDamage}); Eat at HP % is then ignored. Negative: the Eat at HP % rule.
     * @param maxHit the largest single unprotected hit of the enemies present ({@link #maxHit}): the emergency line
     */
    JsonObject next(ColosseumSnapshot f,boolean mage,boolean bloodHeal,int threat,int maxHit)
    {
        if(f.tick<next)return null;
        // The recordings retry an unobserved drink on the next tick. Only a consumed dose
        // starts the four-tick interval; submission alone must not delay a lost input.
        if(pending!=null&&f.tick<=requested)return null;
        // Restore at 15 under every modifier. 1.0.17 raised it by Blasphemy's drain of one big hit (up to 42 at
        // Blasphemy III); the servers kept 15 (their Sol kill 20261102-002336 ran Blasphemy III), and the early
        // restores were 23,600 of the engine's supply clicks the servers never made over 49 runs.
        // 1.0.89: under the Eat at HP % rule, at a third of HP or less the brew comes before the prayer restore (live
        // 2026-11-09 12:56 wave 5: 25 HP and 10 prayer, the restore went first and the brew eight ticks later at 13 HP;
        // death). With the Blood option on the brews stay measured against the threat, as before.
        if(EMERGENCY&&threat<0&&!bloodHeal&&belowVitals(f)&&f.prayer>=EMERGENCY_PRAYER&&f.hp*100<=f.effectiveMaxHp*EMERGENCY_PERCENT)
        {JsonObject brew=drink(f,Kind.BREW);if(brew!=null)return brew;}
        if(f.prayer<f.maxPrayer&&f.prayer<=15)
        {
            // Sanfews are the last prayer source, unless the player is poisoned: then one dose fixes both.
            JsonObject prayer=f.poison>0&&f.prayer<SANFEW_PRAYER?drink(f,Kind.SANFEW):null;
            if(prayer==null)prayer=drink(f,Kind.RESTORE);if(prayer==null)prayer=drink(f,Kind.PRAYER);
            if(prayer==null)prayer=drink(f,Kind.SANFEW);if(prayer!=null)return prayer;
        }
        if(f.poison>0&&(f.prayer<SANFEW_PRAYER||poisonDue(f)))
        {
            // 1.0.97 (user, 2026-11-09: "make sure it can consume antidote++, anti-venom, anti-venom+ etc if they are brought
            // with"): a cure brought along is drunk before a sanfew, which is the prayer supply too. Venom takes an
            // anti-venom first (it cures; an antidote or a sanfew dose only turns venom into poison); poison an antidote
            // first. With prayer low the sanfew still goes first: one dose fixes both.
            JsonObject anti=f.prayer<SANFEW_PRAYER?drink(f,Kind.SANFEW):null;
            if(anti==null)anti=drink(f,f.poison>=VENOM?Kind.ANTIVENOM:Kind.ANTIDOTE);
            if(anti==null)anti=drink(f,f.poison>=VENOM?Kind.ANTIDOTE:Kind.ANTIVENOM);
            if(anti==null)anti=drink(f,Kind.SANFEW);
            // A dose that does not cure (venom turns to poison first) waits another randomized fallback.
            if(anti!=null){poisonDeadline=f.tick+fallback();cureRequested=f.tick;return anti;}
        }
        boolean healed=f.hp*100>f.effectiveMaxHp*BREW_CEILING_PERCENT;
        boolean spellDrained=mage&&f.bloodLevel>0&&f.magic<f.bloodLevel&&f.hp*100>=f.effectiveMaxHp*40;
        // A cycle that runs out of brews ends with its restore (20261027-081024 t3988: the last brew(1) went at t3984,
        // the server restored at 61/74 after two doses).
        boolean outOfBrews=brews>0&&doses(f,Kind.BREW)==0;
        // Outside a counted cycle, Magic calls for a restore only below the Blood spell's level: no server restored
        // Magic drained by a pre-entry brew (20261101-222604, 20261101-234944, 20261102-001727 openings at 69-70 of 77,
        // staff or crossbow; no out-of-cycle restore in 46 runs). A drained Ranged level on a ranged setup still does.
        boolean drained=!mage&&f.ranged<f.baseRanged-5;
        if(brews>=3||brews>0&&healed||outOfBrews||spellDrained||brews==0&&(f.magic<Math.min(f.baseMagic,f.bloodLevel)||drained))
        {JsonObject restore=drink(f,Kind.RESTORE);if(restore!=null)return restore;}
        // Every observed completed healing cycle consumes three doses before restoring.
        // Brew stat drain and reaching base HP do not interrupt a cycle already in progress.
        // The corpus never brews at full HP: 80 requests, cycle continuations at <=90 of 98 (92%). Above that
        // ceiling the cycle ends with its restore instead (2026-11-01: brewed at 98 and on to 114/98).
        // Conservative: a cycle only starts below Eat at HP %; no top-up above it, and none while Blood spells heal.
        // "Blood spells on the last enemy" on: brews are measured against the threat instead of Eat at HP %. A cycle
        // starts, and continues, only while the predicted unprotected hits could take HP to the safety floor, or HP is
        // within one unprotected max hit; the 3:1 cycle and its restore are unchanged.
        // Above the 92% ceiling a brew heals nothing that matters (Frailty disables the overheal): replay with the option
        // on sent 93 of 343 brew requests at or above the reduced maximum before this guard.
        // 1.0.92 (user, 2026-11-09: "it still does not heal up when blood healing is on, I'd like it to respect the % rule"):
        // with the Blood option on, Eat at HP % is the floor; the predicted threat can only start a brew sooner.
        boolean needed=threat<0?belowVitals(f):!healed&&(threatened(f,threat,maxHit)||EAT_FLOOR&&belowVitals(f));
        boolean continues=!bloodHeal&&brews>0&&brews<3&&!healed&&(threat<0||needed);
        if(continues||needed)
        {JsonObject brew=drink(f,Kind.BREW);if(brew!=null)return brew;}
        if(f.prayer<=f.maxPrayer-10&&integer(f.player,"prayerRegenTimer",0)==0)
        {JsonObject regen=drink(f,Kind.REGEN);if(regen!=null)return regen;}
        if(boostsAllowed()&&f.ranged<=f.baseRanged&&f.baseRanged>0)
        {JsonObject range=drink(f,Kind.RANGE);if(range!=null)return range;}
        JsonObject boosted=object(object(f.player,"skills"),"boosted"),real=object(object(f.player,"skills"),"real");
        if(boostsAllowed()&&integer(boosted,"attack",0)<=integer(real,"attack",0)&&integer(real,"attack",0)>0)
        {JsonObject combat=drink(f,Kind.COMBAT);if(combat!=null)return combat;}
        if(f.runEnergy<2500&&!bool(f.player,"staminaActive",false))return drink(f,Kind.STAMINA);
        return null;
    }
    /**
     * HP left after the predicted unprotected hits at or under this: brew. Typeless damage lands whatever the prayers:
     * venom hitsplats reach 20 (46 server runs: 57 venom splats, max 20) and Doom splats 15 (235, p95 11).
     */
    static final int THREAT_FLOOR=20;
    /**
     * Stacked unprotected hits would leave HP at or under the floor, or HP is within one max hit of an attack due. The
     * emergency line only counts when brewing can lift HP out of it (under the 92% ceiling): a Minotaur's 74 against a
     * 67-79 HP account is never brewed out of, and in replay that line alone kept 204 of 290 brew requests going.
     */
    static boolean threatened(ColosseumSnapshot f,int threat,int maxHit)
    {
        boolean emergency=maxHit>0&&f.hp<=maxHit&&maxHit*100<f.effectiveMaxHp*BREW_CEILING_PERCENT;
        return threat>0&&f.hp-threat<=THREAT_FLOOR||emergency;
    }
    /**
     * Damage of one unprotected attack, for brew planning (the {@link ColosseumThreats.Hit} weights are prayer priority,
     * not damage). Derived from the player hitsplats attributed to a single unprotected attack onset 1-5 ticks earlier
     * (no other unprotected onset in that window), 46 server recordings plus 51 local engine runs:
     * <pre>
     * role          n (srv+local)  p95 srv/local  max seen  wiki max  used
     * Archer        58+40          14/14          19        14        14
     * Seer          97+18          16/12          18        12        12   (p95 above the wiki max: misattribution)
     * Shaman        35+203         29/28          40        28        28
     * Javelin       75+76          45/43          48        48        45
     * Berserker     12+5           27/28          28        29        29   (under 20 samples: wiki max)
     * Shockwave     8+4            41/13          41        56        56   (under 20 samples: wiki max)
     * Minotaur      1+1            -              49        74        74   (wiki max)
     * Jaguar        0+3            -              44        47 x3     50   (three claws; 18+0+32 in local 2026-11-04 09:07)
     * Manticore     orbs not attributable alone   -         31/36/31  31/36/31 magic/ranged/melee orb (wiki)
     * </pre>
     * Frailty lowers maximum HP only (effectiveMaxHp), not incoming damage; Relentless raises enemy max hits by 1/3/6
     * per the wiki but is not in the snapshot, so it is not included. Doom and Blasphemy add no hit damage.
     */
    static int damage(Role role,String prayer)
    {
        switch(role)
        {
            case ARCHER:return 14;
            case SEER:return 12;
            case SHAMAN:return 28;
            case JAVELIN:return 45;
            case BERSERKER:return 29;
            case SHOCKWAVE:return 56;
            case MINOTAUR:return 74;
            case JAGUAR:return 50;
            case MANTICORE:return ColosseumThreats.RANGE.equals(prayer)?36:31;
            default:return 0;
        }
    }
    /**
     * Unprotected damage predicted over the windows (index = ticks ahead). Ahead 0 is the attack this tick's prayer
     * protects: every hit not under {@code active} (the protection this tick sets, else the prayers on) lands, the
     * Jaguar override included. Later ticks can still be flicked: the prayer group the protection choice favours
     * (highest weight) is covered and every other group lands. So only stacked hits needing different prayers, or a hit
     * the prayer this tick does not cover, count; a lone attacker the engine protects against never does. The
     * Javelin's sky javelin (weight 1) is left out: it is dodged by the sidestep, not prayed. Untimed hits (certainty 0)
     * are not due.
     */
    static int threatDamage(java.util.List<java.util.List<ColosseumThreats.Hit>> windows,java.util.Set<String> active)
    {return threatDamage(windows,active,false);}
    /** @param manticoreFirst the protection choice puts a Manticore's orb first ({@link ColosseumThreats#manticoreFirst}) */
    static int threatDamage(java.util.List<java.util.List<ColosseumThreats.Hit>> windows,java.util.Set<String> active,boolean manticoreFirst)
    {
        int total=0;
        for(int ahead=0;ahead<windows.size();ahead++)
        {
            java.util.Map<String,int[]> groups=new java.util.HashMap<>();
            for(ColosseumThreats.Hit h:windows.get(ahead))
            {
                if(h.certainty<=0||h.weight<=1||h.npc==null)continue;
                int[] g=groups.computeIfAbsent(h.prayer,k->new int[2]);g[0]+=ColosseumThreats.priority(h,manticoreFirst);g[1]+=damage(h.npc.role,h.prayer);
            }
            String covered=null;int[] best=null;
            if(ahead>0)for(java.util.Map.Entry<String,int[]> e:groups.entrySet())
                if(best==null||e.getValue()[0]>best[0]||e.getValue()[0]==best[0]&&e.getValue()[1]>best[1]){best=e.getValue();covered=e.getKey();}
            for(java.util.Map.Entry<String,int[]> e:groups.entrySet())
                if(ahead==0?!active.contains(e.getKey()):!e.getKey().equals(covered))total+=e.getValue()[1];
        }
        return total;
    }
    /**
     * The largest single hit due in the windows, prayed against or not: the emergency brew line (HP at or under it dies
     * to one failed prayer). Only attacks due in the next four ticks count: every enemy alive put a 67-79 HP account
     * within a Minotaur's 74 or a Shockwave's 56 for whole waves (replay with the option on: brews at 60% HP median).
     */
    static int maxHit(java.util.List<java.util.List<ColosseumThreats.Hit>> windows)
    {
        int max=0;
        for(java.util.List<ColosseumThreats.Hit> w:windows)for(ColosseumThreats.Hit h:w)
            if(h.certainty>0&&h.weight>1&&h.npc!=null)max=Math.max(max,damage(h.npc.role,h.prayer));
        return max;
    }
    /** A Saradomin brew in {@code plan} that drops Magic (10% + 2) under the Blood spell's level. */
    static boolean brewDrainsBlood(ColosseumSnapshot f,java.util.List<JsonObject> plan)
    {
        if(f.bloodLevel<=0)return false;
        for(JsonObject c:plan)
        {
            if(!"inventory.interact".equals(string(c,"op","")))continue;
            JsonObject a=c.getAsJsonObject("args");int id=integer(a,"itemId",-1);
            if(id==6685||id==6687||id==6689||id==6691)return f.magic-(f.magic/10+2)<f.bloodLevel;
        }
        return false;
    }
    private JsonObject drink(ColosseumSnapshot f,Kind kind)
    {
        Item chosen=null;
        for(Item item:f.inventory)if(matches(item,kind)&&item.qty>0&&(!item.lower.startsWith("divine")||f.hp>10))
            if(chosen==null||dose(item)<dose(chosen)||dose(item)==dose(chosen)&&item.slot<chosen.slot)chosen=item;
        if(chosen!=null)
        {
            pending=kind;baseline=doses(f,kind);requested=f.tick;
            return ColosseumDecisionEngine.command("inventory.interact","slot",chosen.slot,"itemId",chosen.id,"option","Drink");
        }
        return null;
    }
    /** A dose that cures or weakens venom is carried: a sanfew, an anti-venom or an antidote. */
    static boolean carriesCure(ColosseumSnapshot f)
    {return doses(f,Kind.SANFEW)>0||doses(f,Kind.ANTIVENOM)>0||doses(f,Kind.ANTIDOTE)>0;}
    private static boolean matches(Item item,Kind kind)
    {
        String n=item.lower;
        switch(kind)
        {
            case BREW:return n.startsWith("saradomin brew(");
            case RESTORE:return n.startsWith("super restore(")||n.startsWith("blighted super restore(");
            case PRAYER:return n.startsWith("prayer potion(");
            case RANGE:return n.contains("ranging potion(")||n.contains("bastion potion(");
            case COMBAT:return n.contains("super combat potion(");
            case STAMINA:return n.contains("stamina potion(");
            case REGEN:return n.startsWith("prayer regeneration potion(");
            case SANFEW:return n.startsWith("sanfew serum(");
            // Antidote+(n), Antidote++(n), Antipoison(n), Superantipoison(n), Relicym's balm(n).
            case ANTIDOTE:return n.startsWith("antidote")||n.startsWith("antipoison(")||n.startsWith("superantipoison(")||n.startsWith("relicym's balm(");
            // Anti-venom(n), Anti-venom+(n), Extended anti-venom+(n).
            case ANTIVENOM:return n.contains("anti-venom")||n.contains("antivenom");
            default:return false;
        }
    }
    private static int doses(ColosseumSnapshot f,Kind kind)
    {
        int total=0;for(Item item:f.inventory)if(matches(item,kind))total+=dose(item)*item.qty;
        return total;
    }
    private static int dose(Item item)
    {
        int at=item.name.lastIndexOf('(');
        if(at>=0&&at+1<item.name.length()){int value=item.name.charAt(at+1)-'0';if(value>=1&&value<=4)return value;}
        return 0;
    }
}
