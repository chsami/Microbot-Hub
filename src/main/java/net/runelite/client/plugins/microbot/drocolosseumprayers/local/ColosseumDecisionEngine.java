package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.*;
import java.util.regex.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumGear.Style;

/** Local, game-API-free decision owner. Inference is confined to the bounded decision worker. */
public final class ColosseumDecisionEngine
{
    private String status = "Loading Colosseum recording evidence";
    private ColosseumEvidence evidence;
    private ColosseumGear gear;
    private final ColosseumThreats threats=new ColosseumThreats();
    private final ColosseumSupplies supplies=new ColosseumSupplies();
    private final ColosseumOffense offense=new ColosseumOffense();
    private final ColosseumSol sol=new ColosseumSol();
    private final ColosseumModifiers modifiers=new ColosseumModifiers();
    private final ColosseumFlares flares=new ColosseumFlares();
    private boolean flareEscapePending;
    private Style killWith=Style.RANGED;
    private Role firstWarband=Role.BERSERKER;
    private long previousTick=-1,attackRequested=-1000,moveRequested=-1000,gearRequested=-1000,widgetRequested=-1000,cameraRequested=-1000;
    private int cameraAttempts;
    /** Every server-era recording starts its waves at this zoom and the top-down pitch; tiles must be clickable. */
    static final int CAMERA_ZOOM=132,CAMERA_PITCH=383,CAMERA_ATTEMPTS=3;
    private int wave,targetRequested=-1,selectedModifier=-1,selectedWave=-1,modifierBaseline;
    private long continueSince=-1;static final int MODIFIER_RETRY=5;
    private Tile destination;
    private boolean waveFinished,modifierDelivered,widgetInFlight;
    private boolean modifierWasVisible,continueRequested,openingApproachSent,approachingPillar;
    private long modifierClosed=-1000;
    private int enteringWave;
    private int openingPhase,dropItem=-1,dropSlot=-1,dropAttempts;
    private long dropRequested=-1000,pickupRequested=-1000;
    private Style requestedStyle;
    /**
     * Tile/target pairs the game refused to shoot from: an attack request walked the player instead (no move was
     * asked for). The local sight model accepted cover at the pillar against the Javelin Colossus and Shaman, so the
     * attack pulled the player into both and the next tick walked back (2026-11-01 wave 2 death). Cleared per wave.
     */
    private final Set<String> pulled=new HashSet<>();
    private Tile attackTile;
    private int pulledWave=-1;
    /** First tick spent out of reach of the target without walking anywhere; -1 otherwise. */
    private long stalledSince=-1;
    private static final int STALL_GRACE=3;
    private static final double STALL_COST=12;
    private static final Pattern WAVE=Pattern.compile("(?i)wave\\s*:?\\s+(\\d+)");
    /**
     * Javelin Colossus sky javelin (animation 10893). Its landing marker (1446/2674) appears on the player's
     * tile three ticks later and the 40-damage hit lands on the sixth. In three server runs (245 specials) the
     * player stepped off at +3 every time, one tile (233), cardinal (225), keeping sight of its target (227) and
     * never onto a tile more ranged enemies can see (223); it usually stepped back to the tile it came from.
     */
    static final int JAVELIN_SPECIAL=10893,JAVELIN_DODGE_AGE=3;
    private static final Set<Role> RANGED_ROLES=EnumSet.of(Role.SHAMAN,Role.ARCHER,Role.SEER,Role.JAVELIN,Role.MANTICORE,Role.SHOCKWAVE);
    private long javelinOnset=-1000,javelinDodged=-1000;
    private Tile javelinFrom;
    private final Map<Tile,Long> javelinLanding=new HashMap<>();

    public void reset()
    {
        status="Waiting for a complete arena snapshot";if(gear!=null)gear.reset();waiting.reset();waitingHold=false;threats.reset();supplies.reset();offense.reset();sol.reset();modifiers.reset();flares.reset();flareEscapePending=false;
        previousTick=-1;wave=0;waveFinished=false;destination=null;targetRequested=-1;selectedModifier=-1;selectedWave=-1;
        attackRequested=moveRequested=gearRequested=widgetRequested=-1000;modifierDelivered=widgetInFlight=false;requestedStyle=null;
        openingPhase=dropAttempts=brewTakes=0;bloodHealing=bloodRestoring=false;dropItem=dropSlot=-1;dropRequested=pickupRequested=-1000;
        modifierWasVisible=continueRequested=openingApproachSent=approachingPillar=false;
        modifierClosed=-1000;enteringWave=0;cameraRequested=-1000;cameraAttempts=0;
        javelinOnset=javelinDodged=-1000;javelinFrom=null;javelinLanding.clear();flareFrom=null;flareFromTick=specRequested=-1000;
        pulled.clear();attackTile=null;pulledWave=-1;stalledSince=-1;pulledFrom=null;coverHold=-1;soloWave=-1;holding=false;meleeTile.clear();meleeMoved.clear();
        late.reset();lateActive=false;
    }
    public void configure(Map<String, Object> options)
    {
        if(evidence==null){evidence=ColosseumEvidence.load();gear=new ColosseumGear(evidence);}
        String style=String.valueOf(options.getOrDefault("killWith","ranged"));
        killWith="mage".equalsIgnoreCase(style)?Style.MAGE:"melee".equalsIgnoreCase(style)?Style.MELEE:Style.RANGED;
        String first=String.valueOf(options.getOrDefault("warbandFirst","melee"));
        firstWarband="ranger".equalsIgnoreCase(first)?Role.ARCHER:"mager".equalsIgnoreCase(first)?Role.SEER:Role.BERSERKER;
        Object value=options.getOrDefault("vitalsPercent",55);supplies.configure(value instanceof Number?((Number)value).intValue():55);
        Object from=options.getOrDefault("potionsFromWave",1);supplies.potionsFrom(from instanceof Number?((Number)from).intValue():1);
        prayersOnly=Boolean.TRUE.equals(options.get("prayersOnly"));
        manualMovement=Boolean.TRUE.equals(options.get("manualMovement"));
        bloodHealLast=Boolean.TRUE.equals(options.get("bloodHealLast"));
        status=evidence.failure==null?"Local controller ready; "+evidence.frames+" recorded frames":evidence.failure;
        late.share(evidence);late.configure(options);
    }
    /**
     * 1.0.50: waves 1-4 are played by this engine (1.0.39), wave 5 on by the 1.0.13 engine with 1.0.39's Jaguar
     * trapping (user, 2026-11-05: 1.0.39 stalled on the wave-5 Shaman). Both see every snapshot and result so the one
     * taking over knows the run; only the active one's commands are sent.
     */
    static final int LATE_FROM_WAVE=Integer.getInteger("colo.lateFrom",5);
    private final ColosseumLateEngine late=new ColosseumLateEngine();
    private boolean lateActive;
    public List<JsonObject> decide(JsonObject snapshot)
    {
        List<JsonObject> plan=plan(snapshot);
        List<JsonObject> ordered=recordedOrder(prayersOnly?prayersOnly(snapshot,plan):plan);
        // Switched once per run; the late engine drops what it believed it had sent while inactive.
        if(!lateActive&&Math.max(wave,modifiersTaken(snapshot))>=LATE_FROM_WAVE){lateActive=true;late.takeOver();}
        List<JsonObject> latePlan=late.decide(snapshot);
        if(lateActive){latePlan=watchdog(snapshot,latePlan);liveState=liveState(latePlan,status());return latePlan;}
        ordered=watchdog(snapshot,ordered);
        liveState=liveState(ordered,status());
        return ordered;
    }
    /**
     * Failsafe (1.0.61, user 2026-11-05: the six-hour stall). In a wave, enemies alive and none of them hit for
     * {@link #STALL_FAILSAFE} ticks: whatever the engine believes, attack the nearest enemy and let the game walk into
     * range; prayers and drinks of the plan are kept. Each further window moves on to the next nearest enemy. Simulator
     * 1.0.60: 37 of 400 runs stood at a pillar until the time limit (18 in wave 4, Shaman and Manticore untouched).
     */
    static final int STALL_FAILSAFE=50;
    /** Simulator comparisons only: -Dcolo.failsafes=false runs without both 1.0.61 failsafes. */
    static final boolean FAILSAFES=!"false".equals(System.getProperty("colo.failsafes"));
    private long lastProgress=-1;private int lastEnemies=-1,failsafeTurns;
    List<JsonObject> watchdog(JsonObject snapshot,List<JsonObject> plan)
    {
        failsafeStatus=null;
        if(!FAILSAFES||manualMovement||prayersOnly)return plan;   // Prayers only (user): the user attacks, the failsafe never does
        ColosseumSnapshot f=new ColosseumSnapshot(snapshot);
        List<Npc> enemies=new ArrayList<>();
        for(Npc n:f.npcs)if(n.alive()&&n.enemy()&&failsafeTarget(n))enemies.add(n);
        boolean progress=enemies.isEmpty()||enemies.size()!=lastEnemies||lastProgress<0||f.tick<lastProgress||(lateActive?late.waitingHold():waitingHold);
        for(JsonElement e:f.events)
        {
            JsonObject ev=e.getAsJsonObject();
            if(!"hitsplat".equals(string(ev,"e",""))||!ev.has("actor")||!ev.get("actor").isJsonPrimitive()||!ev.get("actor").getAsJsonPrimitive().isNumber())continue;
            int actor=ev.get("actor").getAsInt();
            for(Npc n:enemies)if(n.index==actor)progress=true;
        }
        for(Npc n:f.npcs)if(n.role==Role.SOL&&n.alive())progress=true;
        lastEnemies=enemies.size();
        if(progress){lastProgress=f.tick;failsafeTurns=0;return plan;}
        if(f.tick-lastProgress<STALL_FAILSAFE)return plan;
        failsafeTurns=(int)((f.tick-lastProgress)/STALL_FAILSAFE)-1;
        List<JsonObject> out=lateActive?late.recover(f,plan,failsafeTurns):recover(f,plan,failsafeTurns);
        if(out==null)return plan;
        failsafeStatus="Failsafe: no enemy hit for "+(f.tick-lastProgress)+" ticks";
        return out;
    }
    private static boolean failsafeTarget(Npc n)
    {return n.role!=Role.FLARE&&n.role!=Role.CRYSTAL&&n.role!=Role.BEES&&n.role!=Role.SCORPION&&n.role!=Role.TOTEM&&n.role!=Role.SPECTATOR&&n.role!=Role.PILLAR;}
    /**
     * Modifiers taken so far, from the native stack varbits: one per wave started. Some modifiers have no varbit, so it
     * can only undercount; it places a client started mid-run (wave unknown until the next wave message) from wave 5 on.
     */
    static int modifiersTaken(JsonObject snapshot)
    {
        JsonObject levels=object(object(snapshot,"player"),"modifiers");int taken=0;
        for(Map.Entry<String,JsonElement> e:levels.entrySet())
            if(!e.getKey().equals("doomPoints")&&e.getValue().isJsonPrimitive())taken+=e.getValue().getAsInt();
        return taken;
    }
    boolean lateActive(){return lateActive;}

    private volatile String liveState="Wait";
    /** One word for the overlay: what this tick's plan does (Heal, Recover, Dodge, Move, Switch, Attack, Pray, Wait). */
    public String liveState(){return liveState;}
    /** Active modifiers and tiers, from the offers taken and the native stack varbits. */
    public Map<String,Integer> activeModifiers(){return modifiers.active();}
    static String liveState(List<JsonObject> plan,String status)
    {
        Set<String> ops=new HashSet<>();String drink="";
        for(JsonObject c:plan)
        {
            String op=string(c,"op","");ops.add(op);
            if(op.equals("inventory.interact"))drink=string(object(c,"args"),"name","")+" "+integer(object(c,"args"),"itemId",-1);
        }
        String s=status==null?"":status;
        if(ops.contains("inventory.interact"))return BREWS.contains(drinkId(drink))?"Heal":"Recover";
        if(ops.contains("ground.take"))return "Loot";
        if(ops.contains("movement.tile"))
            return s.contains("Sidestep")||s.contains("flare")||s.contains("Flare")||s.matches("Sol: (spear|shield) \\d \\+[01]")?"Dodge":"Move";
        if(ops.contains("equipment.set"))return "Switch";
        if(ops.contains("npc.attack")||s.startsWith("Wave ")&&s.contains(": ")||s.startsWith("Sol"))return "Attack";
        if(ops.contains("prayer.set")||ops.contains("prayer.flick"))return "Pray";
        return s.startsWith("Moving")?"Move":"Wait";
    }
    /** Saradomin brew (4)-(1). */
    private static final Set<Integer> BREWS=Set.of(6685,6687,6689,6691);
    private static int drinkId(String drink)
    {try{return Integer.parseInt(drink.substring(drink.lastIndexOf(' ')+1));}catch(RuntimeException e){return -1;}}

    /** "Prayers only": the player fights; the engine only switches prayers. */
    private boolean prayersOnly;
    /**
     * "Manual movement": the player walks; the engine plays everything else from wherever the player stands. No walk is
     * planned, so attacks are not held back for one. The opening skips the brew-drop corner; wave 1 still starts once
     * the player stands on the start tile.
     */
    private boolean manualMovement;
    /** "Blood spells on the last enemy" (1.0.40, kept with the 1.0.39 engine in 1.0.49). */
    private boolean bloodHealLast,bloodHealing,bloodRestoring;
    static final int BLOOD_TARGET_PERCENT=ColosseumBlood.TARGET_PERCENT;
    private final ColosseumBlood blood=new ColosseumBlood();
    static final int THREAT_AHEAD=3;
    private long playerEngaged=-1000;
    /**
     * Keeps this tick's protection prayer commands. Offensive prayer follows the weapon the player is wielding and
     * stays on while the player is attacking (an attack target within the last three ticks), instead of the
     * engine's own planned target and style.
     */
    private List<JsonObject> prayersOnly(JsonObject snapshot,List<JsonObject> plan)
    {
        List<JsonObject> kept=new ArrayList<>();
        for(JsonObject c:plan)
        {
            String op=string(c,"op","");
            if(op.startsWith("prayer.")&&string(c.getAsJsonObject("args"),"prayer","").startsWith("PROTECT_"))kept.add(c);
        }
        ColosseumSnapshot f=new ColosseumSnapshot(snapshot);
        if(!f.playable())return kept;
        if(f.interacting>=0)playerEngaged=f.tick;
        Style worn=ColosseumGear.type(f.weapon());
        offensive(kept,f,f.tick-playerEngaged<=3?worn:null);
        return kept;
    }

    /**
     * Every recording sends a tick's protection prayer first, then offensive prayers, then everything else
     * (success run: protect then offense 2,246 times, the reverse once; a potion never precedes a prayer).
     */
    static List<JsonObject> recordedOrder(List<JsonObject> commands)
    {
        if (commands.size() < 2) return commands;
        List<JsonObject> ordered = new ArrayList<>(commands);
        ordered.sort(Comparator.comparingInt(command -> {
            String op = command.has("op") ? command.get("op").getAsString() : "";
            if (!op.startsWith("prayer.")) return 2;
            JsonObject args = command.getAsJsonObject("args");
            String prayer = args != null && args.has("prayer") ? args.get("prayer").getAsString() : "";
            return prayer.startsWith("PROTECT_") ? 0 : 1;
        }));
        return ordered;
    }

    private List<JsonObject> plan(JsonObject snapshot)
    {
        if(evidence==null)configure(Map.of());
        List<JsonObject> plan=new ArrayList<>();
        if(longValue(snapshot,"tick",-1)<=previousTick)return plan;
        ColosseumSnapshot f=new ColosseumSnapshot(snapshot,modifiers.observe(snapshot));
        gear.observe(f);
        previousTick=f.tick;observeWave(f);supplies.wave(wave);supplies.observe(f);threats.observe(f);offense.observe(f);flares.observe(f);flareEscapePending=false;
        observeJavelin(f);
        if(!f.playable()||!bool(snapshot,"inputAllowed",true))
        {status="Waiting for live Colosseum input permission";return plan;}
        if(evidence.failure!=null){status=evidence.failure;return plan;}
        camera(f,plan);
        Npc boss=null;for(Npc n:f.npcs)if(n.alive()&&n.role==Role.SOL){boss=n;break;}
        if(boss!=null)
        {
            wave=12;openingPhase=3;
            if(!gear.ready(f,Style.MELEE))
            {JsonObject change=gear.switchTo(f,Style.MELEE);if(change!=null){plan.add(change);gearRequested=f.tick;}}
            // 1.0.56: Sol brews against his largest typeless hit, not Eat at HP %: a crystal laser is 60-79, a slam 20-44,
            // so HP is kept above one hit (simulator: 100 Sol starts died with a median 8 brews left, at 55% Eat).
            JsonObject supply=supplies.next(f,false,false,0,ColosseumSol.maxHit(f));if(supply!=null)plan.add(supply);
            plan.addAll(sol.decide(f,boss,new ColosseumCollision(f)));status=sol.status();return plan;
        }
        // 1.0.59: only an NPC of no known role holds the engine. The Red Flag Minotaur (12813) and Bees (12823) are known
        // roles the recordings never showed; holding on them froze every action at the reinforcement (simulator: all 56
        // wave-7 Minotaur deaths of 400 seeds took Red Flag, the engine standing still until killed).
        for(Npc n:f.npcs)if(n.enemy()&&n.role==Role.UNKNOWN&&!evidence.observedEnemies.contains(n.id))
        {status="Unrecorded Colosseum enemy/modifier "+n.id+"; actions held for manual control";return plan;}
        ColosseumCollision collision=new ColosseumCollision(f);Tile current=new Tile(f.x,f.y);
        observePull(f,current);observeMelee(f);waiting.observe(f,wave);waitingHold=false;
        quietNow=ColosseumWait.ON&&collision.available()&&ColosseumSolo.aliveStyles(f)>=2&&ColosseumWait.quiet(f,collision,current);
        boolean modifierScreen=hasModifierScreen(f);
        if(modifierWasVisible&&!modifierScreen&&continueRequested)
        {
            modifierClosed=f.tick;enteringWave=wave+1;continueRequested=false;brewTakes=0;
        }
        if(modifierScreen&&!modifierWasVisible)
        {selectedModifier=-1;modifierDelivered=widgetInFlight=false;}
        modifierWasVisible=modifierScreen;
        long launchAge=f.tick-modifierClosed;
        String protection=threats.protection(f,collision,current);
        // All five recordings use the same screen-close-relative wave countdown. This is
        // a live UI transition, independent of recording ticks, run IDs and actor indices.
        if(enteringWave>=1&&enteringWave<=11&&launchAge>=2&&launchAge<=6&&f.y<=3112)
        {
            if(enteringWave==1||enteringWave==4||enteringWave==8||enteringWave==11)
                protection="PROTECT_FROM_MAGIC";
            else if(enteringWave!=10)protection="PROTECT_FROM_MISSILES";
        }
        if(protection!=null||collision.available())setProtection(plan,f,protection);
        if(modifierScreen)selectModifier(f,plan);
        List<Npc> enemies=new ArrayList<>();for(Npc n:f.npcs)if(attackable(n))enemies.add(n);
        boolean mage=ColosseumGear.type(f.weapon())==Style.MAGE||requestedStyle==Style.MAGE;
        // Option "Blood spells on the last enemy": with one enemy left, cast the staff's Blood autocast until HP is 90%.
        // Decided before the supplies so no brew cycle continues on the tick the Blood healing starts. The game shows
        // the Blood autocast only while the staff is worn, so the level last seen carries over (1.0.41 manual
        // 2026-11-04 23:30 t488-509: one Shaman left at 85/98 HP in ranged gear, no heal).
        // Two Blood heals (user): the last enemy, and a wall-trapped Jaguar while no shooter can hit the player.
        Npc jaguarHeal=bloodHealLast&&!manualMovement?blood.jaguarHeal(f,collision,gear):null;
        bloodHealing=bloodHealLast&&(blood.heal(f,enemies.size(),gear)||jaguarHeal!=null);
        bloodRestoring=bloodHealLast&&!bloodHealing&&blood.drained(f,enemies.size(),gear);
        JsonObject supply=bloodRestoring?supplies.restoreForBlood(f):null;
        if(supply==null&&bloodHealLast&&collision.available())
        {
            // "Blood spells on the last enemy": brews follow the predicted stacked hits, not Eat at HP %.
            List<List<ColosseumThreats.Hit>> windows=new ArrayList<>();
            for(int ahead=0;ahead<=THREAT_AHEAD;ahead++)windows.add(threats.hits(f,collision,current,ahead));
            Set<String> covered=protection!=null?Set.of(protection):f.prayers;
            supply=supplies.next(f,mage,bloodHealing,ColosseumSupplies.threatDamage(windows,covered),ColosseumSupplies.maxHit(windows));
        }
        else if(supply==null)supply=supplies.next(f,mage,bloodHealing);
        if(supply!=null)plan.add(supply);
        if(enteringWave>=1&&enteringWave<=11&&launchAge==OPENING_AGE&&collision.available())
        {
            Tile pillar=new Tile(1815,3115);
            // The opening brew goes first; the warband approach then walks to the pillar.
            if(brewWanted(f,launchAge))approachingPillar=true;
            else if(!current.equals(pillar)&&collision.paths(current,32).distance(pillar)>=0&&!hazard(f,pillar))
            {move(plan,f,pillar);approachingPillar=true;}
            openingPhase=3;
        }
        if(enemies.isEmpty())
        {
            if(f.npcs.stream().anyMatch(n->n.enemy()&&n.role!=Role.FLARE))
            {status="Active arena hazards remain; next wave held";return plan;}
            if(enteringWave>0&&launchAge>=OPENING_AGE&&launchAge<=8)
            {status="Next wave: approaching the northwest pillar";return plan;}
            if(wave>=12){status="Waiting for Sol's arena arrival";return plan;}
            if(!opening(f,collision,plan))return plan;
            intermission(f,collision,plan,modifierScreen);
            return plan;
        }
        openingPhase=3;
        waveFinished=false;
        if(pickupBrew(f,plan,launchAge)){offensive(plan,f,null);status="Picking up the opening brew";return plan;}
        ColosseumEvidence.Hint hint=evidence.hint(f);
        Npc target=target(f,enemies,hint,collision);
        if(jaguarHeal!=null&&bloodHealing)target=jaguarHeal;
        if(target==null){status="No supported live target";return plan;}
        Style style=ColosseumGear.counter(target,killWith);
        if(bloodHealing)style=Style.MAGE;
        int range=gear.range(f,style);
        if(range==0){status="Required "+style.name().toLowerCase(Locale.ROOT)+" weapon is missing from current setup";return plan;}
        if(!collision.available())
        {
            // Protection and receipts can continue; an unknown map cannot certify a path or attack pull.
            status="Waiting for current arena collision before movement/attacks";return plan;
        }
        Tile dodge=javelinDodge(f,collision,current,target,style,range);
        if(dodge!=null)
        {
            // The recorded dodge is a lone click; the attack follows from the new tile on the next tick.
            move(plan,f,dodge,true);javelinDodged=javelinOnset;javelinFrom=current;stalledSince=-1;
            offensive(plan,f,null);status="Sidestepping the Javelin Colossus special";return plan;
        }
        // Manticore alive: an attack the game walked is walked straight back (local 10:41 wave 6 t3393: an attack on
        // the Jaguar walked the player from (1814,3115) to (1816,3116), into the Manticore's and both Javelins' sight).
        Tile back=pulledFrom;pulledFrom=null;
        if(back!=null&&manticoreAlive(f)&&!back.equals(current)&&collision.free(back.x,back.y)&&!hazard(f,back)
            &&!flares.projected(back)&&collision.paths(current,4).distance(back)>=0)
        {
            move(plan,f,back);offensive(plan,f,null);stalledSince=-1;
            status="Walking back from an attack the game walked";return plan;
        }
        holding=false;
        // 1.0.96 (ColosseumWait): a quiet pillar tile is held until the reinforcements have come, and a Jaguar or Minotaur
        // on its way is fought from it.
        boolean waitHere=!manualMovement&&!bloodHealing&&!approachingPillar&&!hazard(f,current)&&!flares.projected(current)
            &&!flareTile(f,current)&&!javelinLanding.containsKey(current)&&waiting.hold(f,collision,current);
        if(waitHere)
        {
            Npc comer=null;int nearest=Integer.MAX_VALUE;
            for(Npc n:enemies)
            {
                if(n.role!=Role.JAGUAR&&n.role!=Role.MINOTAUR)continue;
                Style s=ColosseumGear.counter(n,killWith);int r=gear.range(f,s),gap=n.distance(f.x,f.y);
                if(r>0&&gap>0&&gap<=r&&gap<nearest&&shot(collision,s,current,n)){comer=n;nearest=gap;}
            }
            if(comer==null)
            {
                if(f.moving&&destination!=null&&!destination.equals(current))move(plan,f,current);
                offensive(plan,f,null);stalledSince=-1;waitingHold=true;
                status=waiting.pending(f)?"Holding the safe spot until the reinforcements arrive":"Holding the safe spot for the melee enemy on its way";
                return plan;
            }
            target=comer;style=ColosseumGear.counter(comer,killWith);range=gear.range(f,style);waitingHold=true;
        }
        Tile chosen=waitHere?current:chooseTile(f,collision,target,style,range,hint,current);
        if(current.equals(new Tile(1815,3115)))approachingPillar=false;
        if(approachingPillar&&target.warband()&&f.y<3115)
        {
            Tile pillar=new Tile(1815,3115);
            if(!hazard(f,pillar)&&collision.paths(current,16).distance(pillar)>=0)
                chosen=f.interacting>=0||!f.moving?pillar:current;
        }
        boolean inRange=target.distance(f.x,f.y)<=range&&target.distance(f.x,f.y)>0&&shot(collision,style,current,target);
        // 1.0.89: the opening run to the pillar is not broken off to attack (see OPENING_RUN).
        if(OPENING_RUN&&approachingPillar&&target.warband()&&current.y<WEST_FACE_ROW&&!manualMovement&&!hazard(f,current))inRange=false;
        // 1.0.87: holding a tile with no shot at the target but a shot at another shooter, that shooter is fought instead.
        // 1.0.95: also when the target is in sight but its click would walk the player (Manticore alive, the firing ray to
        // the target's closest tile blocked): the click is held below, and a shooter the ray reaches is fought meanwhile.
        boolean walks=inRange&&style!=Style.MELEE&&(manticoreAlive(f)||rayless(collision,current,target,style))&&!collision.firingSight(target,current);
        if(walks&&rayless(collision,current,target,style))inRange=false;
        if((!inRange||walks)&&(chosen==null||chosen.equals(current))&&!bloodHealing)
        {
            Npc other=alternate(f,collision,current,target);
            if(other!=null&&(!walks||collision.firingSight(other,current)))
            {target=other;style=ColosseumGear.counter(other,killWith);range=gear.range(f,style);inRange=true;}
        }
        if(chosen!=null&&!chosen.equals(current))move(plan,f,chosen);
        boolean walking=chosen!=null&&!chosen.equals(current)||f.moving;
        // Only progress resets the stall: standing in a shot, or heading for a tile that has one. Shuffling between
        // blind tiles (wave 5, 97 ticks north of a pillar with the warband south of it) must keep raising the cost.
        Tile heading=chosen!=null&&!chosen.equals(current)?chosen:f.moving&&destination!=null?destination:null;
        boolean headingToShot=heading!=null&&(target.distance(heading.x,heading.y)>0&&target.distance(heading.x,heading.y)<=range
            &&shot(collision,style,heading,target)||alternate(f,collision,heading,target)!=null);
        if(inRange||headingToShot||holding)stalledSince=-1;else if(stalledSince<0)stalledSince=f.tick;
        // Holding cover: a walk already under way out of it is stopped on the current tile.
        if(holding&&f.moving&&destination!=null&&!destination.equals(current)&&plan.stream().noneMatch(p->"movement.tile".equals(string(p,"op",""))))
            move(plan,f,current);
        // Recorded nonwarband switches wait for a firing position. Preserve the worn setup
        // while walking around the pillar; switch, observe it worn, then request the attack.
        if(!inRange)
        {
            offensive(plan,f,null);
            status=!waitingHold?"Moving into firing position for "+target.role
                :chosen==null||chosen.equals(current)?"Waiting on the quiet side for the enemy that can follow":"Walking to the quiet side one enemy can follow to";
            return plan;
        }
        if(!gear.ready(f,style))
        {
            // The source retries a switch on the next snapshot until the worn state changes.
            // A submitted equipment command is not a receipt, and no attack shares this branch.
            JsonObject change=gear.switchTo(f,style);
            if(change!=null){plan.add(change);requestedStyle=style;gearRequested=f.tick;}
            boolean switchInRange=target.distance(f.x,f.y)>0&&target.distance(f.x,f.y)<=range&&shot(collision,style,current,target);
            offensive(plan,f,switchInRange&&offense.needed(f,true)?style:null);
            status="Switching to "+style.name().toLowerCase(Locale.ROOT)+" for "+target.role;return plan;
        }
        requestedStyle=null;
        // User rule: ranged legs off for the Blood heal when there is room (magic accuracy); the ranged switch re-equips them.
        if(bloodHealing&&style==Style.MAGE){JsonObject shed=gear.shedLegsForMagic(f);if(shed!=null)plan.add(shed);}
        // Older source snapshots do not contain autocast observations. Preserve their contract;
        // when native observations are supplied, an explicit unusable setup still holds attacks.
        boolean mageReady=style!=Style.MAGE||!f.player.has("bloodAutocast")
            ||f.bloodLevel>0&&f.magic>=f.bloodLevel;
        if(!mageReady){offensive(plan,f,null);status="Waiting for observed Blood autocast and its Magic level";return plan;}
        if(style==Style.MAGE&&ColosseumSupplies.brewDrainsBlood(f,plan))
        {offensive(plan,f,null);status="Holding the cast: this tick's brew drains Magic below the Blood spell";return plan;}
        if(inRange&&!hazard(f,current))
        {
            boolean movingCommand=plan.stream().anyMatch(p->"movement.tile".equals(string(p,"op","")));
            // Manticore alive: only an attack the game fires in place, so it does not walk the player. 1.0.53: that is the
            // firing ray (player to the target's closest tile): of 2,727 recorded standing attack clicks, ray clear -> fired
            // in place 96% even with NPC-to-player sight blocked; the old npcSight check held every attack on two Shamans
            // the ray could hit and stalled wave 4 until restores ran out (simulator report 026).
            boolean sure=!(manticoreAlive(f)||SURE&&pullFrees(target))||style==Style.MELEE||collision.firingSight(target,current);
            // 1.0.64 (user, 2026-11-05): with Manual movement the user owns every step. Only an attack the game fires from the
            // current tile; an attack on a pillar-trapped Jaguar walked the player out of the safe spot.
            // Manual movement attacks only the Fremennik warband and the last-enemy Blood heal casts (user, 2026-11-05),
            // both from where the player stands; the user attacks everything else.
            boolean inPlace=!manualMovement||(target.warband()||bloodHealing)&&shot(collision,style,current,target);
            boolean requesting=f.interacting!=target.index&&!movingCommand&&(!flareEscapePending||manualMovement)&&sure&&!landingBeside(current)&&inPlace;
            if(requesting)
            {plan.add(command("npc.attack","index",target.index));targetRequested=target.index;attackRequested=f.tick;attackTile=current;}
            else special(f,target,plan);
            // All recorded attack/offensive pairs put the attack command first.
            offensive(plan,f,offense.needed(f,requesting)?style:null);
        }
        else offensive(plan,f,null);
        status="Wave "+(wave==0?"?":wave)+": "+target.role+"; local "+style.name().toLowerCase(Locale.ROOT)+(bloodHealing?" (blood to "+BLOOD_TARGET_PERCENT+"%)":bloodRestoring?" (restoring Magic for blood)":"");
        return plan;
    }

    private void observeWave(ColosseumSnapshot f)
    {
        for(JsonElement e:f.events)
        {
            JsonObject event=e.getAsJsonObject();if(!"chat".equals(string(event,"e","")))continue;
            String text=string(event,"text","");Matcher m=WAVE.matcher(text);
            if(m.find())
            {int seen=Integer.parseInt(m.group(1));if(seen>=1&&seen<=12){wave=Math.max(wave,seen);waveFinished=text.toLowerCase(Locale.ROOT).contains("complete");}}
            if(text.contains("Sol Heredit jumps")){wave=12;waveFinished=false;}
        }
    }
    private static boolean attackable(Npc n)
    {return n.enemy()&&n.role!=Role.FLARE&&n.role!=Role.CRYSTAL&&n.role!=Role.BEES&&n.role!=Role.SCORPION;}
    private Npc target(ColosseumSnapshot f,List<Npc> enemies,ColosseumEvidence.Hint hint,ColosseumCollision collision)
    {
        // 790/792 recorded attacks began without a live current target. Do not churn a live engagement.
        for(Npc n:enemies)if(n.index==f.interacting)return n;
        boolean warband=enemies.stream().anyMatch(Npc::warband);
        Npc best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(Npc n:enemies)
        {
            if(warband&&!n.warband())continue;
            double score=(n.role==firstWarband?1000:0)+(n.role==Role.BERSERKER?300:n.role==Role.SEER?200:n.role==Role.ARCHER?100:0);
            if(n.id==hint.targetId&&n.x==hint.targetX&&n.y==hint.targetY)score+=400;
            if(n.index==f.interacting)score+=25;
            if(collision.playerSight(new Tile(f.x,f.y),n))score+=60;
            score-=n.distance(f.x,f.y)*3;
            if(n.role==Role.MINOTAUR)score+=160;
            if(best==null||score>bestScore){best=n;bestScore=score;}
        }
        return best;
    }
    static final int WEST_SIDE_X=1818;
    private static boolean jaguarHunting(ColosseumSnapshot f)
    {
        for(Npc n:f.npcs)if(n.alive()&&n.role==Role.JAGUAR&&n.targetsPlayer())return true;
        return false;
    }
    /** Stalled ticks after which a pillar-trapped Jaguar no longer keeps the player west of the pillar. */
    private static final int TUCK_STALL=10;
    private static boolean jaguarsTrapped(ColosseumSnapshot f,ColosseumCollision collision,Tile current)
    {
        for(Npc n:f.npcs)
            if(n.alive()&&n.role==Role.JAGUAR&&n.targetsPlayer()&&(n.distance(current.x,current.y)<=1||!ColosseumTrap.trapped(collision,n,current)))
                return false;
        return true;
    }
    private Tile chooseTile(ColosseumSnapshot f,ColosseumCollision collision,Npc target,Style style,int range,ColosseumEvidence.Hint hint,Tile current)
    {
        // 1.0.88: two styles due together on this tile: the nearest pillar tile one style can fire at comes before the
        // opening hold and before any cover (ColosseumSolo).
        if(!hazard(f,current)&&!flares.projected(current)&&!flareTile(f,current)&&threats.danger(f,collision,current)>=40)
        {Tile solo=soloTile(f,collision,current,target.warband()||waiting.waitable(f,collision,current));if(solo!=null){soloWave=wave;return solo;}}
        // 1.0.96 (ColosseumWait, step 3): nothing to shoot from a quiet tile and nothing left to wait for: the pillar face
        // one style can follow to, then wait there for that one.
        if(!manualMovement&&!bloodHealing&&!approachingPillar&&!hazard(f,current)&&!flares.projected(current)&&!flareTile(f,current)
            &&!(target.distance(f.x,f.y)>0&&target.distance(f.x,f.y)<=range&&shot(collision,style,current,target)&&!rayless(collision,current,target,style))
            &&rayAlternate(f,collision,current,target)==null)
        {
            if(waiting.luring(f,collision,current)){holding=true;waitingHold=true;return current;}
            ColosseumCollision.Paths lurePaths=collision.movementPaths(current,14);
            Tile lure=waiting.lure(f,collision,current,t->!hazard(f,t)&&!flares.projected(t)&&!landingBeside(t)
                &&!meleeReach(f,collision,t)&&safeRoute(f,lurePaths,current,t));
            if(lure!=null){waitingHold=true;return lure;}
        }
        // The opening warband marches to the reached pillar. Across all five recordings,
        // a fresh approaching Berserker never makes the player step out to meet it.
        if(current.equals(new Tile(1815,3115))&&target.role==Role.BERSERKER&&!hazard(f,current))
        {
            int gap=target.distance(f.x,f.y);
            if(gap>0&&target.hpRatio<0&&target.animation<0)return current;
            if(gap==0&&!f.moving)
            {
                Tile corner=new Tile(1814,3114);
                if(collision.free(corner.x,corner.y)&&!hazard(f,corner)
                    &&collision.movementPaths(current,2).distance(corner)>=0)return corner;
            }
        }
        boolean canAttack=target.distance(f.x,f.y)>0&&target.distance(f.x,f.y)<=range&&shot(collision,style,current,target)
            &&!rayless(collision,current,target,style);
        // Maging the Fremennik warband, the player stays on or south of the start tile's row; anything that took it
        // further north (a walk, or an attack the game walked) is walked back first (user, after 1.0.14 16:35 and 17:14
        // stood on (1814,3116)/(1815,3116) for the whole wave 1 warband).
        boolean south=target.warband()&&style==Style.MAGE&&soloWave!=wave,north=south&&current.y>OPENING_NORTH_ROW;
        boolean projected=flares.projected(current);
        // No attack exception: a re-sent attack that had not registered kept the player on the flare's next tile until
        // it arrived (1.0.10 t519-521, t942, t950-951; each a Doom point). 1.0.5 removed it for the same reason.
        if(projected&&!hazard(f,current)&&pendingFlareEscape(f,collision,current))
        {flareEscapePending=true;return current;}
        boolean onFlare=flareTile(f,current);
        if(projected&&!hazard(f,current)||onFlare)
        {
            Tile step=flareStep(f,collision,target,style,range,current);
            if(step!=null){if(flareFrom==null||f.tick-flareFromTick>FLARE_RETURN)flareFrom=current;flareFromTick=f.tick;return step;}
        }
        Tile back=flareReturn(f,collision,current);
        if(back!=null)return back;
        double currentDanger=threats.danger(f,collision,current);
        boolean untrapped=untrappedMelee(f,collision,current)>0;
        if(canAttack&&!hazard(f,current)&&!projected&&currentDanger<40&&!untrapped&&!north)return current;
        boolean manticore=manticoreAlive(f);
        // Manticore alive and no shooter after the player can see this tile: hold it rather than let the stall walk
        // the player into the stack (local 10:41 wave 6 t3299: out to (1817,3110), Manticore, Shaman and both Javelins
        // at once). The servers held (1815,3113)/(1815,3114) through wave 6. Capped, then the usual scoring resumes.
        if(manticore&&!hazard(f,current)&&!projected&&!untrapped&&!north&&covered(f,collision,current))
        {
            // 1.0.88: a tile only one prayer style can fire at beats waiting in cover (ColosseumSolo).
            Tile solo=soloTile(f,collision,current,target.warband());
            if(solo!=null){coverHold=-1;return solo;}
            if(coverHold<0)coverHold=f.tick;
            if(f.tick-coverHold<COVER_HOLD){holding=true;return current;}
        }
        else coverHold=-1;
        int trappedFree=untrappedMelee(f,collision,current);
        ColosseumCollision.Paths paths=collision.paths(current,16);
        // With a Jaguar after the player the servers stayed off the pillars' east faces: east of x=1818 in 137 of 18,374
        // ticks (24 runs), mostly the west face. 1.0.9 walked out to (1820,3119) when one spawned and was clawed to death.
        // A Jaguar the pillar traps never comes out, so the tuck only waits. 1.0.13 wave 5 (t1415-1460, range 3) held
        // x<=1818 for 45 ticks with the last Shaman at (1822,3113): every tile in range of it lay east, until a click.
        boolean tucked=jaguarHunting(f)&&!hazard(f,current)
            &&!(stalledSince>=0&&f.tick-stalledSince>=TUCK_STALL&&jaguarsTrapped(f,collision,current));
        if(f.moving&&destination!=null&&!(tucked&&destination.x>WEST_SIDE_X)&&!(south&&destination.y>OPENING_NORTH_ROW)
            &&collision.free(destination.x,destination.y)&&!hazard(f,destination)
            &&untrappedMelee(f,collision,destination)==0
            &&(!projected||!flares.projected(destination))&&safeRoute(f,paths,current,destination)
            &&threats.danger(f,collision,destination)<=currentDanger+5)return current;
        double currentScore=tileScore(f,collision,target,style,range,current,0,projected);
        if(north)currentScore+=OFF_NORTH;
        // 1.0.88 (user, 2026-11-09): out of sight every shooter becomes ready, so the next tile two styles can fire at
        // is a same-tick double. From cover or a one-style tile, such tiles are not candidates.
        // 1.0.94: not while standing on a hazard. Simulator seed 15 wave 3: on molten sand with every clean tile in two
        // styles' sight, the rule left no candidate and the player stood in the sand until it died.
        boolean escaping=hazard(f,current)||projected||flareTile(f,current)||javelinLanding.containsKey(current);
        boolean solved=ColosseumSolo.ON&&!escaping&&ColosseumSolo.styles(f,collision,current)<=1;
        List<Tile> candidates=new ArrayList<>();Map<Tile,Double> scores=new HashMap<>();
        for(Tile candidate:paths.reachable)
        {
            int distance=paths.distance(candidate);if(distance>16||candidate.equals(current))continue;
            if(tucked&&candidate.x>WEST_SIDE_X)continue;
            if(south&&candidate.y>OPENING_NORTH_ROW)continue;
            if(solved&&ColosseumSolo.styles(f,collision,candidate)>1)continue;
            // Manticore alive: never step where a pillar-trapped Jaguar/Minotaur can walk up (local 10:41 wave 5 t2940:
            // the step to (1817,3110) let the Jaguar out; its claw and the Manticore's volley came due together, 68).
            if(manticore&&(untrappedMelee(f,collision,candidate)>trappedFree||frees(f,collision,candidate)))continue;
            double score=tileScore(f,collision,target,style,range,candidate,distance,projected);
            if(hint.movement!=null&&candidate.equals(hint.movement)&&score<1000)score-=4;
            if(score<currentScore-0.75){candidates.add(candidate);scores.put(candidate,score);}
        }
        candidates.sort(Comparator.comparingDouble(scores::get));
        // Never click a destination whose route crosses a Solarflare, or steps where one moves next.
        // A blocked best route falls back to the next safe candidate instead of standing in danger.
        int checked=0;
        for(Tile candidate:candidates){if(safeRoute(f,paths,current,candidate))return candidate;if(++checked>=24)break;}
        // The user's taught tiles only as a fallback (user, 2026-11-04): nothing above beats standing, the player has no
        // shot and has stalled past the grace. 1.0.25-1.0.32 restricted every move to taught/server tiles instead.
        if(!canAttack&&stalledSince>=0&&f.tick-stalledSince>STALL_GRACE)
        {
            Tile taught=taughtFallback(f,collision,paths,target,style,range,current,currentDanger,projected,tucked,south);
            if(taught!=null)return taught;
        }
        return current;
    }
    /** A live Manticore: the stack whose volley lands with everything else. Rules gated on it leave waves 1-3 as they were. */
    static boolean manticoreAlive(ColosseumSnapshot f)
    {
        for(Npc n:f.npcs)if(n.alive()&&n.role==Role.MANTICORE)return true;
        return false;
    }
    /** Ticks a Jaguar/Minotaur after the player must stand still out of reach to count as trapped where the player is. */
    static final int PARKED_MELEE=5;
    private final Map<Integer,Tile> meleeTile=new HashMap<>();
    private final Map<Integer,Long> meleeMoved=new HashMap<>();
    private void observeMelee(ColosseumSnapshot f)
    {
        Set<Integer> seen=new HashSet<>();
        for(Npc n:f.npcs)
        {
            if(!n.alive()||n.role!=Role.JAGUAR&&n.role!=Role.MINOTAUR)continue;
            seen.add(n.index);Tile t=new Tile(n.x,n.y);Tile was=meleeTile.put(n.index,t);
            if(was==null||!was.equals(t))meleeMoved.put(n.index,f.tick);
        }
        meleeTile.keySet().retainAll(seen);meleeMoved.keySet().retainAll(seen);
    }
    /**
     * Stepping to {@code tile} could free a Jaguar/Minotaur that has stood still out of reach for {@link #PARKED_MELEE}
     * ticks: the game shows it trapped where the player stands, and the model cannot say which other tiles keep it so.
     * It called the Jaguar free from (1816,3110) and trapped from (1817,3110); it stayed 70 ticks with the player on the
     * first and walked out four ticks after the step to the second (local 10:41 wave 5). Lapses after a long stall.
     */
    private boolean frees(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        if(tile.x==f.x&&tile.y==f.y||stalledSince>=0&&f.tick-stalledSince>=COVER_HOLD)return false;
        for(Npc n:f.npcs)
            if(n.alive()&&(n.role==Role.JAGUAR||n.role==Role.MINOTAUR)&&n.targetsPlayer()&&n.distance(f.x,f.y)>1
                &&f.tick-meleeMoved.getOrDefault(n.index,f.tick)>=PARKED_MELEE)return true;
        return false;
    }
    /** No ranged enemy after the player has firing sight of {@code tile}. */
    private static boolean covered(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        for(Npc n:f.npcs)if(n.alive()&&n.targetsPlayer()&&RANGED_ROLES.contains(n.role)&&collision.firingSight(n,tile))return false;
        return true;
    }
    /** The wave in which a solved tile was taken: it lifts the stay-south rule of the warband opening for that wave. */
    private int soloWave=-1;
    /** The engine's own standing rules for a solved tile (ColosseumSolo). */
    private Tile soloTile(ColosseumSnapshot f,ColosseumCollision collision,Tile current,boolean coverCounts)
    {
        if(manualMovement)return null;
        int meleeHere=untrappedMelee(f,collision,current);boolean hunted=jaguarHunting(f);
        ColosseumCollision.Paths paths=collision.movementPaths(current,12);
        return ColosseumSolo.find(f,collision,current,coverCounts,t->!hazard(f,t)&&!flares.projected(t)&&!landingBeside(t)
            &&untrappedMelee(f,collision,t)<=meleeHere&&!meleeReach(f,collision,t)&&!(hunted&&t.x>WEST_SIDE_X)
            &&!(manticoreAlive(f)&&frees(f,collision,t))&&safeRoute(f,paths,current,t));
    }
    /** Ticks the player holds cover with a Manticore alive before the usual scoring may walk it out. */
    static final int COVER_HOLD=60;
    private long coverHold=-1;
    private boolean holding;
    private final ColosseumWait waiting=new ColosseumWait();
    /** This tick's plan waits on a quiet tile (ColosseumWait): the stall failsafe leaves it alone. */
    private boolean waitingHold;
    /** The tile an attack the game walked took the player off; read once on the next tick. */
    private Tile pulledFrom;
    /** The start tile's row (1815,3115); maging the warband the player stays on or south of it. */
    static final int OPENING_NORTH_ROW=3115;
    private static final double OFF_NORTH=1000;
    /**
     * The best taught tile at the player's pillar with a shot at the target: reachable on a safe route, no hazard, no
     * more danger than the current tile, and no Jaguar/Minotaur that can walk up to it. Null when none qualifies.
     */
    private Tile taughtFallback(ColosseumSnapshot f,ColosseumCollision collision,ColosseumCollision.Paths paths,Npc target,Style style,
        int range,Tile current,double currentDanger,boolean projected,boolean tucked,boolean south)
    {
        Map<Tile,Integer> tiles=taughtTiles(f,nearPillar(current));
        if(tiles==null)return null;
        Tile best=null;double bestScore=Double.MAX_VALUE;
        for(Tile t:tiles.keySet())
        {
            int distance=paths.distance(t),gap=target.distance(t.x,t.y);
            if(t.equals(current)||distance<1||distance>16||!collision.free(t.x,t.y)||hazard(f,t)||projected&&flares.projected(t))continue;
            if(tucked&&t.x>WEST_SIDE_X||south&&t.y>OPENING_NORTH_ROW)continue;
            if(gap<=0||gap>range||!shot(collision,style,t,target)||untrappedMelee(f,collision,t)>0)continue;
            if(manticoreAlive(f)&&frees(f,collision,t))continue;
            if(threats.danger(f,collision,t)>currentDanger+5||!safeRoute(f,paths,current,t))continue;
            double score=tileScore(f,collision,target,style,range,t,distance,projected);
            if(score<bestScore){best=t;bestScore=score;}
        }
        return best;
    }
    /** South-west tiles of the arena's four 3x3 pillars. */
    static final Tile[] PILLARS={new Tile(1816,3113),new Tile(1816,3098),new Tile(1831,3113),new Tile(1831,3098)};
    static final Tile NORTH_WEST_PILLAR=new Tile(1816,3113),SOUTH_WEST_PILLAR=new Tile(1816,3098);
    /** From this far off a pillar its taught tiles still apply. */
    static final int HUG_REACH=5;
    /** The nearest pillar whose footprint lies within {@link #HUG_REACH} tiles of {@code p}, or null in the open yard. */
    static Tile nearPillar(Tile p)
    {
        Tile best=null;int bestGap=HUG_REACH+1;
        for(Tile q:PILLARS){int[] d=outside(p,q);int gap=Math.max(d[0],d[1]);if(gap<bestGap){best=q;bestGap=gap;}}
        return best;
    }
    /** Tiles outside the 3x3 footprint in x and y (0 when level with it). */
    private static int[] outside(Tile p,Tile q)
    {
        int dx=p.x<q.x?q.x-p.x:p.x>q.x+2?p.x-q.x-2:0,dy=p.y<q.y?q.y-p.y:p.y>q.y+2?p.y-q.y-2:0;
        return new int[]{dx,dy};
    }
    /**
     * The user's own tiles at the north-west pillar (local 19:58, waves 1-6 and 8; ticks on each, 20 or more). With a
     * Jaguar after the player: 479 of 848 ticks on (1815,3114), where the 2x2 Jaguar sticks in the north slot
     * (1815,3116) and is shot. The north-west corner tiles (1815,3116) and (1815,3117) are left out: they are not
     * behind the pillar (user).
     */
    static final Map<Tile,Integer> TAUGHT_JAGUAR=taught(1815,3114,479, 1815,3113,127, 1819,3114,83, 1816,3116,58,
        1816,3112,35, 1816,3117,27);
    static final Map<Tile,Integer> TAUGHT=taught(1815,3114,450, 1817,3116,211, 1815,3112,181, 1818,3116,111,
        1819,3114,102, 1815,3113,77, 1814,3114,60, 1817,3112,58, 1818,3112,53, 1816,3116,43, 1819,3115,41,
        1816,3112,41, 1817,3117,40, 1815,3115,26);
    /** The servers' tiles at the south-west pillar after their crossings (100+ ticks each). */
    static final Map<Tile,Integer> TAUGHT_SOUTH=taught(1815,3100,312, 1816,3097,297, 1814,3100,287, 1816,3096,275,
        1818,3097,223, 1815,3098,191, 1817,3097,187, 1815,3097,184, 1817,3096,172, 1816,3102,152, 1819,3096,147,
        1817,3095,114);
    private static Map<Tile,Integer> taught(int... v)
    {
        Map<Tile,Integer> m=new HashMap<>();for(int i=0;i<v.length;i+=3)m.put(new Tile(v[i],v[i+1]),v[i+2]);
        return Collections.unmodifiableMap(m);
    }
    /** The taught tiles at {@code pillar} (the Jaguar set while one is after the player), or null where none were taught. */
    static Map<Tile,Integer> taughtTiles(ColosseumSnapshot f,Tile pillar)
    {return NORTH_WEST_PILLAR.equals(pillar)?(jaguarHunting(f)?TAUGHT_JAGUAR:TAUGHT):SOUTH_WEST_PILLAR.equals(pillar)?TAUGHT_SOUTH:null;}
    /**
     * One step off a flare's line. When a flare was coming onto the player the servers clicked one tile 2 ticks ahead
     * of it (203 of 238 arrivals, 1 contact): beside its line 99 times, back past it 45, onto the tile ahead 85 (then
     * stepping again). The local engine stepped ahead and stood (1.0.10: 5 contacts in 10). Off the next two path
     * tiles first; never into a Jaguar/Minotaur's reach, east of the pillar with a Jaguar after the player, or onto a tile
     * more ranged enemies can see; then cardinal, then the usual tile score. Null when no neighbour is clear.
     */
    private Tile flareStep(ColosseumSnapshot f,ColosseumCollision collision,Npc target,Style style,int range,Tile current)
    {
        int currentSeen=rangedSight(f,collision,current);boolean tucked=jaguarHunting(f);
        Tile best=null;int[] bestKey=null;double bestScore=Double.MAX_VALUE;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
        {
            Tile tile=new Tile(current.x+dx,current.y+dy);
            if(tile.equals(current)||!collision.free(tile.x,tile.y)||!collision.playerStep(current,tile)||hazard(f,tile)||flares.projected(tile))continue;
            // Past an NPC's corner only to get off the line: the Jaguar, Shaman and pillar left (1818,3116) no other
            // step (1.0.10 t519-521), and the tile ahead that led there was the only one without an NPC corner (t517).
            int[] key={meleeReach(f,collision,tile)?1:0,flares.onPath(tile,2)?1:0,collision.step(current,tile)?0:1,
                tucked&&tile.x>WEST_SIDE_X?1:0,rangedSight(f,collision,tile)>currentSeen?1:0,dx==0||dy==0?0:1};
            double score=tileScore(f,collision,target,style,range,tile,1,true);
            int order=bestKey==null?-1:Arrays.compare(key,bestKey);
            if(order<0||order==0&&score<bestScore){best=tile;bestKey=key;bestScore=score;}
        }
        return best;
    }
    /** Ticks after a flare step in which the engine walks back to the tile it stepped off. */
    static final int FLARE_RETURN=12;
    private Tile flareFrom;
    private long flareFromTick=-1000;
    /**
     * Back onto the tile a flare pushed the player off, once the flare has passed it: not a flare tile, not its next
     * two path tiles, no Jaguar/Minotaur reach or approaching melee there that the current tile does not have, and a
     * safe route of at most two tiles.
     */
    private Tile flareReturn(ColosseumSnapshot f,ColosseumCollision collision,Tile current)
    {
        if(flareFrom==null)return null;
        if(current.equals(flareFrom)||f.tick-flareFromTick>FLARE_RETURN){flareFrom=null;return null;}
        if(hazard(f,current)||hazard(f,flareFrom)||flares.projected(flareFrom)||flares.onPath(flareFrom,2))return null;
        if(meleeReach(f,collision,flareFrom)&&!meleeReach(f,collision,current))return null;
        if(untrappedMelee(f,collision,flareFrom)>untrappedMelee(f,collision,current))return null;
        ColosseumCollision.Paths paths=collision.paths(current,2);
        int distance=paths.distance(flareFrom);
        if(distance<1||distance>2||!safeRoute(f,paths,current,flareFrom))return null;
        return flareFrom;
    }
    private static boolean flareTile(ColosseumSnapshot f,Tile tile)
    {
        for(Npc n:f.npcs)if(n.alive()&&n.role==Role.FLARE&&n.distance(tile.x,tile.y)==0)return true;
        return false;
    }
    /** A live Jaguar or Minotaur can claw {@code tile} from where it stands. */
    private static boolean meleeReach(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        for(Npc n:f.npcs)if(n.alive()&&(n.role==Role.JAGUAR||n.role==Role.MINOTAUR)&&collision.melee(n,tile))return true;
        return false;
    }
    /** Route tiles are clear of flares and projectiles; the first steps are also clear of each flare's next tile. */
    private boolean shot(ColosseumCollision collision,Style style,Tile tile,Npc target)
    {
        if(style==Style.MELEE)return collision.melee(target,tile);
        // 1.0.54: the game's own shot test is the firing ray (player to the target's closest tile). Recorded standing
        // attack clicks: WorldArea player sight clear but the ray blocked -> the game walked the player 61 of 67; ray clear
        // -> fired in place 96% (simulator report 026). The WorldArea test held 1.0.53 on a tile it could not shoot from.
        return collision.playerSight(tile,target)&&(!SURE||!pullFrees(target)||collision.firingSight(target,tile))&&!pulled.contains(pullKey(tile,target));
    }
    private static String pullKey(Tile tile,Npc target){return tile.x+":"+tile.y+":"+target.index+":"+target.x+":"+target.y;}
    /** A ranged/magic attack request followed by an unrequested step means the game found no shot from there. */
    private void observePull(ColosseumSnapshot f,Tile current)
    {
        pulledFrom=null;
        if(pulledWave!=wave){pulled.clear();pulledWave=wave;}
        if(attackTile==null)return;
        if(f.tick-attackRequested>3||moveRequested>=attackRequested){attackTile=null;return;}
        if(current.equals(attackTile))return;
        for(Npc n:f.npcs)if(n.index==targetRequested&&n.alive()&&n.distance(attackTile.x,attackTile.y)>1)
            {pulled.add(pullKey(attackTile,n));pulledFrom=attackTile;}
        attackTile=null;
    }
    private boolean safeRoute(ColosseumSnapshot f,ColosseumCollision.Paths paths,Tile current,Tile goal)
    {
        if(paths.distance(goal)<0)return false;
        List<Tile> path=paths.to(goal);int step=0;
        for(Tile tile:path)
        {
            if(tile.equals(current))continue;
            if(hazard(f,tile)||++step<=2&&flares.projected(tile))return false;
        }
        // The game walks diagonally first, which can differ from this search's route: the click to (1817,3116) from
        // (1813,3115) crossed the flare on (1815,3116) (1.0.10 t1256-1257, a Doom point).
        Tile at=current;step=0;
        while(!at.equals(goal))
        {
            Tile next=new Tile(at.x+Integer.signum(goal.x-at.x),at.y+Integer.signum(goal.y-at.y));
            // Walls end the straight walk (the game then routes around as the search does); NPCs and flares do not.
            if(!paths.playerStep(at,next))break;
            if(hazard(f,next)||++step<=2&&flares.projected(next))return false;
            at=next;
        }
        return true;
    }
    /** Zoomed-in or tilted cameras leave arena tiles off the viewport, so walks cannot be clicked. */
    private void camera(ColosseumSnapshot f,List<JsonObject> plan)
    {
        JsonObject camera=object(f.raw,"camera");
        if(!camera.has("zoom")||cameraAttempts>=CAMERA_ATTEMPTS||f.tick-cameraRequested<3)return;
        if(integer(camera,"zoom",CAMERA_ZOOM)<=CAMERA_ZOOM+8&&integer(camera,"pitch",CAMERA_PITCH)>=CAMERA_PITCH-13)return;
        plan.add(command("camera.set","pitch",CAMERA_PITCH,"zoom",CAMERA_ZOOM));cameraRequested=f.tick;cameraAttempts++;
    }
    private double tileScore(ColosseumSnapshot f,ColosseumCollision collision,Npc target,Style style,int range,Tile tile,int distance,boolean escapingProjection)
    {
        // The directional projection triggers a local escape. It must not invalidate a
        // distant normal route: the recorded flare may clear its endpoint before arrival.
        if(hazard(f,tile)||escapingProjection&&flares.projected(tile))return 10000+distance;
        double score=threats.danger(f,collision,tile)+distance*.8;
        // Recorded play never waits on a flare's next tile (12 of 34,913 flare frames touched one).
        if(flares.projected(tile))score+=50;
        int gap=target.distance(tile.x,tile.y);
        boolean attack=gap>0&&gap<=range&&shot(collision,style,tile,target);
        // Enemies waiting out of sight never attack, so cover is free forever unless waiting has a cost.
        long stalled=stalledSince<0?0:Math.max(0,f.tick-stalledSince-STALL_GRACE);
        if(!attack&&alternate(f,collision,tile,target)!=null)score+=ALTERNATE_COST;
        else if(!attack)score+=35+Math.max(0,gap-range)*2+stalled*STALL_COST;
        if(style!=Style.MELEE&&range>1&&gap<=1)score+=100;
        score+=untrappedMelee(f,collision,tile)*TRAP_MISS;
        return score;
    }
    /**
     * 1.0.87, one shooter at a time. Live pure runs 2026-11-05..08 (47 recordings, 6,162 damage): 45% landed on ticks
     * where two prayer styles were thrown together, 1,969 of it a Javelin and a Shaman launching on the same tick (97
     * times); the servers let that pair launch together 4 times in 1,202. Both are ready behind the pillar, so the tile
     * that shows the target shows both and they stay in step. The engine chose its target first and scored tiles only for
     * a shot at it, so a tile that showed one of them alone counted as waiting and lost to the stall cost.
     * <p>
     * A tile with a shot at another shooter is a firing tile too: the threat score then prefers the tile only one style
     * can see, and the player fights whichever shooter it has from there. Off for the warband, the Blood heals and
     * Manual movement. -Dcolo.isolate=false restores 1.0.86 for simulator comparisons.
     */
    static final boolean ISOLATE=!"false".equals(System.getProperty("colo.isolate"));
    private static final double ALTERNATE_COST=2;
    /** {@link #alternate}, and on a quiet tile only one the game fires at in place. */
    private Npc rayAlternate(ColosseumSnapshot f,ColosseumCollision collision,Tile tile,Npc target)
    {
        Npc other=alternate(f,collision,tile,target);
        return other==null||rayless(collision,tile,other,ColosseumGear.counter(other,killWith))?null:other;
    }
    private Npc alternate(ColosseumSnapshot f,ColosseumCollision collision,Tile tile,Npc target)
    {
        if(!ISOLATE||manualMovement||bloodHealing||target.warband()||!collision.available())return null;
        Npc best=null;int bestGap=Integer.MAX_VALUE;
        for(Npc n:f.npcs)
        {
            if(n.index==target.index||!attackable(n)||!n.alive()||n.warband()||!RANGED_ROLES.contains(n.role))continue;
            Style style=ColosseumGear.counter(n,killWith);int range=gear.range(f,style),gap=n.distance(tile.x,tile.y);
            if(range<=1||gap<=1||gap>range||!shot(collision,style,tile,n))continue;
            if(gap<bestGap){best=n;bestGap=gap;}
        }
        return best;
    }
    /**
     * 1.0.89, the opening run. Shooters that see the player throw their first shot three ticks after the wave spawns, all
     * on the same tick. Three ticks after the spawn the servers stood on the pillar's west face, (1815,3113) or beyond,
     * in 235 of 323 waves; the live pure runs in 35 of 85, most often one tile short on (1815,3112), because each attack
     * on the approaching Berserker stopped the walk for a tick. Live 2026-11-09: 13:36 wave 2 attacked at t187 and t189
     * and took the Javelin's first throw (44) on (1815,3112) beside the Shaman's; 12:56 wave 5 walked back from
     * (1815,3114) to (1815,3112) for an attack. No attack is sent until the player is level with the pillar.
     * -Dcolo.openingRun=false restores 1.0.88 for simulator comparisons.
     */
    static final boolean OPENING_RUN=!"false".equals(System.getProperty("colo.openingRun"));
    static final int WEST_FACE_ROW=3113;
    /**
     * 1.0.89: an attack on a Jaguar or Minotaur is only sent where the game fires it in place (1.0.53 required that of
     * every target with a Manticore alive). Live 2026-11-09 13:36 wave 2 t361: an attack on a wall-trapped Jaguar from
     * (1818,3111), no firing ray, walked the player to (1819,3113) beside it; it came free and clawed with the Shaman's
     * cast (44, 27, death). -Dcolo.sure=false restores 1.0.88.
     */
    static final boolean SURE=!"false".equals(System.getProperty("colo.sure"));
    /**
     * 1.0.96: from a pillar tile nothing can fire at, with two or more shooter styles alive, an attack is only clicked
     * when the game fires it in place: its ray goes to the target's closest tile, and with that blocked it walks the
     * player off the tile. Live clicks 2026-11-09 (10 runs, ranged or magic, no walk of the engine's own within a tick):
     * ray clear, fired in place 234 of 237; sight to some tile of the target but the ray blocked, walked 7 of 8. Live
     * 20:22 wave 2 t191-202: six clicks on the Javelin from (1816,3116) each walked the player to the corner where the
     * Javelin and the Shaman threw together. Kept to the quiet tile: with the ray in every tile choice the simulator lost
     * 0.2 waves (seeds 13-108). -Dcolo.ray=false turns it off for simulator comparisons.
     */
    static final boolean RAY=!"false".equals(System.getProperty("colo.ray"));
    /** This tick's tile is a quiet pillar tile with two or more shooter styles alive (ColosseumWait). */
    private boolean quietNow;
    private boolean rayless(ColosseumCollision collision,Tile tile,Npc target,Style style)
    {return RAY&&quietNow&&style!=Style.MELEE&&!collision.firingSight(target,tile);}
    private static boolean pullFrees(Npc target){return target.role==Role.JAGUAR||target.role==Role.MINOTAUR;}
    /** Ticks after the modifier screen closes at which the walk to the pillar starts (1.0.88: 4). */
    static final int OPENING_AGE=Integer.getInteger("colo.openingAge",3);
    /** Cost per approaching Jaguar/Minotaur that a tile does not trap behind cover. */
    private static final double TRAP_MISS=30;
    /** Approaching Jaguars and Minotaurs that can walk up to {@code tile}; the warband keeps its recorded pillar opening. */
    private static int untrappedMelee(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        int count=0;
        for(Npc n:f.npcs)
            if(n.alive()&&n.enemy()&&(n.role==Role.JAGUAR||n.role==Role.MINOTAUR)&&n.targetsPlayer()
                &&n.distance(f.x,f.y)>1&&!ColosseumTrap.trapped(collision,n,tile))count++;
        return count;
    }
    private boolean pendingFlareEscape(ColosseumSnapshot f,ColosseumCollision collision,Tile current)
    {
        JsonObject observed=object(f.player,"destination");
        Tile nativeDestination=new Tile(integer(observed,"tx",f.x),integer(observed,"ty",f.y));
        if(integer(observed,"plane",f.plane)==f.plane&&!nativeDestination.equals(current)
            &&collision.free(nativeDestination.x,nativeDestination.y)&&!hazard(f,nativeDestination)
            &&!flares.projected(nativeDestination))return true;
        return f.moving&&destination!=null&&!destination.equals(current)&&f.tick-moveRequested<=2
            &&collision.free(destination.x,destination.y)&&!hazard(f,destination)&&!flares.projected(destination);
    }
    private void observeJavelin(ColosseumSnapshot f)
    {
        javelinLanding.values().removeIf(expiry->expiry<f.tick);
        for(JsonElement e:f.events)
        {
            JsonObject event=e.getAsJsonObject();String kind=string(event,"e","");
            if("anim".equals(kind)&&integer(event,"anim",-1)==JAVELIN_SPECIAL)
            {Npc actor=f.npc(integer(event,"actor",-1));if(actor!=null&&actor.role==Role.JAVELIN)javelinOnset=f.tick;}
            int id=integer(event,"id",-1);
            if("gfxobj".equals(kind)&&(id==1446||id==2674))
                javelinLanding.put(new Tile(integer(event,"tx",-100),integer(event,"ty",-100)),f.tick+3);
        }
    }
    /** One cardinal step off a locked javelin landing, or null when no dodge is due. */
    /** A Jaguar or Minotaur after the player, held by walls against {@code from}, that {@code to} would let through. */
    private static boolean freesTrapped(ColosseumSnapshot f,ColosseumCollision collision,Tile from,Tile to)
    {
        for(Npc n:f.npcs)
            if(n.alive()&&(n.role==Role.JAGUAR||n.role==Role.MINOTAUR)&&n.targetsPlayer()&&n.distance(from.x,from.y)>1
                &&ColosseumTrap.trapped(collision,n,from)&&!ColosseumTrap.trapped(collision,n,to))return true;
        return false;
    }
    private Tile javelinDodge(ColosseumSnapshot f,ColosseumCollision collision,Tile current,Npc target,Style style,int range)
    {
        long age=f.tick-javelinOnset;
        // A landing marker (1446 with 2674 Javelin / 2677 Reentry) on the player's tile is dodged whatever threw it.
        // The single onset clock lost the first of two Javelins 3 ticks apart: 1.0.48 manual 2026-11-05 00:03, specials
        // t555 and t558, markers on the player at t558/t561/t583/t608 not dodged. Reentry's acid uses the same marker.
        boolean marked=javelinLanding.containsKey(current);
        if(marked)
        {
            // A dodge already walking off the mark is not sent again; one that left the player on it is.
            if(f.moving&&destination!=null&&!destination.equals(current)&&f.tick-moveRequested<=1)return null;
        }
        else if(javelinDodged==javelinOnset||age!=JAVELIN_DODGE_AGE||!javelinLanding.isEmpty())return null;
        int currentSeen=rangedSight(f,collision,current);
        Tile best=null;int[] bestKey=null;
        Tile[] order={new Tile(current.x,current.y+1),new Tile(current.x-1,current.y),new Tile(current.x+1,current.y),new Tile(current.x,current.y-1),
            new Tile(current.x-1,current.y+1),new Tile(current.x+1,current.y+1),new Tile(current.x-1,current.y-1),new Tile(current.x+1,current.y-1)};
        for(int i=0;i<order.length;i++)
        {
            Tile tile=order[i];
            if(!collision.free(tile.x,tile.y)||!collision.step(current,tile)||hazard(f,tile)||javelinLanding.containsKey(tile)||flares.projected(tile))continue;
            // User 2026-11-08: the sidestep never steps into harm. Never into a Jaguar/Minotaur's reach, and never off a
            // tile that holds one trapped ("if it sidesteps west one tile with a trapped jaguar ... it's going to free the
            // jaguar"). With no such tile the player stays and takes the special.
            if(meleeReach(f,collision,tile)||freesTrapped(f,collision,current,tile))continue;
            int gap=target.distance(tile.x,tile.y);
            int sight=gap>0&&gap<=range&&shot(collision,style,tile,target)?0:1;
            int seen=rangedSight(f,collision,tile);
            // Never into a Jaguar/Minotaur's reach: no server dodge with one within 3 tiles entered it (local
            // 2026-11-02 death stepped north into a Jaguar; the server stepped south from that tile).
            // Then cardinal, keep the shot, never more exposed, prefer the tile we came from.
            int[] key={meleeNext(f,collision,tile)?1:0,i<4?0:1,sight,seen>currentSeen?1:0,tile.equals(javelinFrom)?0:1,seen,i};
            if(bestKey==null||Arrays.compare(key,bestKey)<0){bestKey=key;best=tile;}
        }
        // Always dodge (user, 2026-11-03): a step into a Jaguar/Minotaur's next reach only ranks last.
        return best;
    }
    /** A live Jaguar or Minotaur can claw {@code tile} now or after its next approach step. */
    private static boolean meleeNext(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        for(Npc n:f.npcs)if(n.alive()&&(n.role==Role.JAGUAR||n.role==Role.MINOTAUR)
            &&(collision.melee(n,tile)||collision.meleeAfterApproach(n,tile)))return true;
        return false;
    }
    /**
     * A Javelin/Reentry landing still to come on or next to {@code tile}. The attack is held until it lands: an attack
     * the game walks for line of sight can take the player straight back onto it (1.0.50 2026-11-05 01:03 wave 2 t311
     * and t337: dodged (1819,3112) to (1818,3112) beside the pillar corner, the next tick's attack walked back, 38 and 42).
     */
    /**
     * Stall recovery for the facade watchdog (1.0.61, Astra 033): never cancels an escape and never lets the game path.
     * Yields (returns null) while the current tile is dangerous: a hazard, a flare's next tile, a Javelin landing beside it,
     * or a melee enemy able to claw it. Otherwise: attack an enemy shootable from where the player stands; else walk an
     * explicitly checked safe route to the nearest safe tile with a shot that frees no parked melee; else null.
     */
    List<JsonObject> recover(ColosseumSnapshot f,List<JsonObject> plan,int rotation)
    {
        ColosseumCollision collision=new ColosseumCollision(f);
        if(!collision.available())return null;
        Tile current=new Tile(f.x,f.y);
        if(hazard(f,current)||flares.projected(current)||landingBeside(current)||meleeReach(f,collision,current)||meleeNear(f,collision,current))return null;
        List<Npc> enemies=new ArrayList<>();
        for(Npc n:f.npcs)if(n.alive()&&n.enemy()&&n.role!=Role.FLARE&&n.role!=Role.CRYSTAL&&n.role!=Role.BEES&&n.role!=Role.SCORPION&&n.role!=Role.TOTEM&&n.role!=Role.SOL)enemies.add(n);
        if(enemies.isEmpty())return null;
        enemies.sort(Comparator.comparingInt(n->n.distance(f.x,f.y)));
        java.util.Collections.rotate(enemies,-(rotation%enemies.size()));
        List<JsonObject> keep=new ArrayList<>();
        for(JsonObject c:plan){String op=string(c,"op","");if(!op.equals("movement.tile")&&!op.equals("npc.attack"))keep.add(c);}
        for(Npc n:enemies)if(recoveryShot(f,collision,current,n))
        {keep.add(command("npc.attack","index",n.index));return keep;}
        if(manualMovement)return null;
        Tile solo=soloTile(f,collision,current,false);
        if(solo!=null){keep.add(command("movement.tile","x",solo.x,"y",solo.y,"plane",f.plane));return keep;}
        ColosseumCollision.Paths paths=collision.paths(current,16);
        Tile best=null;int bestDistance=Integer.MAX_VALUE;int meleeHere=untrappedMelee(f,collision,current);
        for(Tile t:paths.reachable)
        {
            int d=paths.distance(t);if(d<=0||d>=bestDistance)continue;
            if(hazard(f,t)||flares.projected(t)||landingBeside(t)||untrappedMelee(f,collision,t)>meleeHere||meleeReach(f,collision,t)||meleeNear(f,collision,t))continue;
            boolean any=false;for(Npc n:enemies)if(recoveryShot(f,collision,t,n)){any=true;break;}
            if(any&&safeRoute(f,paths,current,t)){best=t;bestDistance=d;}
        }
        // 1.0.94: no tile with a shot keeps a trapped Jaguar off: any safe tile with a shot, so the wave ends.
        if(best==null)for(Tile t:paths.reachable)
        {
            int d=paths.distance(t);if(d<=0||d>=bestDistance)continue;
            if(hazard(f,t)||flares.projected(t)||landingBeside(t)||meleeReach(f,collision,t))continue;
            boolean any=false;for(Npc n:enemies)if(recoveryShot(f,collision,t,n)){any=true;break;}
            if(any&&safeRoute(f,paths,current,t)){best=t;bestDistance=d;}
        }
        if(best==null)return null;
        keep.add(command("movement.tile","x",best.x,"y",best.y,"plane",f.plane));return keep;
    }
    private boolean recoveryShot(ColosseumSnapshot f,ColosseumCollision collision,Tile tile,Npc n)
    {
        Style style=ColosseumGear.counter(n,killWith);int range=gear==null?0:gear.range(f,style);
        int gap=n.distance(tile.x,tile.y);
        return range>0&&gap>0&&gap<=range&&shot(collision,style,tile,n);
    }
    /** A live, untrapped Jaguar or Minotaur within two tiles of {@code tile}. */
    private static boolean meleeNear(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        for(Npc n:f.npcs)if(n.alive()&&(n.role==Role.JAGUAR||n.role==Role.MINOTAUR)&&n.distance(tile.x,tile.y)<=2&&!ColosseumTrap.trapped(collision,n,tile))return true;
        return false;
    }
    private boolean landingBeside(Tile tile)
    {
        for(Tile t:javelinLanding.keySet())if(Math.abs(t.x-tile.x)<=1&&Math.abs(t.y-tile.y)<=1)return true;
        return false;
    }
    private static int rangedSight(ColosseumSnapshot f,ColosseumCollision collision,Tile tile)
    {
        int seen=0;
        for(Npc n:f.npcs)if(n.alive()&&RANGED_ROLES.contains(n.role)&&collision.npcSight(n,tile))seen++;
        return seen;
    }
    static boolean hazard(ColosseumSnapshot f,Tile tile)
    {
        // Three runs: the server never sent a move onto molten sand (0/2049) and stood on it in 5 of 6,000 frames.
        if(f.sand.contains(tile))return true;
        // The live size-one footprint is hazardous. A 3x3 expansion contradicted 751 stable
        // adjacent reference frames and made the controller abandon its pillar unnecessarily.
        for(Npc n:f.npcs)if(n.alive()&&n.role==Role.FLARE&&n.distance(tile.x,tile.y)==0)return true;
        for(JsonElement e:f.projectiles)
        {
            JsonObject p=e.getAsJsonObject();JsonElement target=p.get("target");
            if(integer(p,"id",-1)==2673&&(target==null||target.isJsonNull())
                &&integer(p,"ticksLeft",99)<=3&&Math.abs(integer(p,"tx",-100)-tile.x)<=1&&Math.abs(integer(p,"ty",-100)-tile.y)<=1)return true;
        }
        if(f.raw.has("graphicsObjects"))
        {
            for(JsonElement e:array(f.raw,"graphicsObjects"))
            {JsonObject g=e.getAsJsonObject();if(integer(g,"id",-1)==2674&&!bool(g,"finished",false)
                &&integer(g,"tx",-100)==tile.x&&integer(g,"ty",-100)==tile.y)return true;}
            return false;
        }
        for(JsonElement e:f.events)
        {
            JsonObject g=e.getAsJsonObject();
            if("gfxobj".equals(string(g,"e",""))&&integer(g,"id",-1)==2674
                &&integer(g,"tx",-100)==tile.x&&integer(g,"ty",-100)==tile.y)return true;
        }
        return false;
    }
    private void move(List<JsonObject> plan,ColosseumSnapshot f,Tile tile){move(plan,f,tile,false);}
    /**
     * The Javelin sidestep is the one walk Manual movement keeps. It is marked "snap": the pointer jumps to the tile
     * and clicks, so the player's own mouse cannot pull a Natural mouse walk off target.
     */
    private void move(List<JsonObject> plan,ColosseumSnapshot f,Tile tile,boolean dodge)
    {
        // An attack can replace the ground route while the native moving flag remains set.
        // Reissue the route after that observed interaction instead of suppressing it.
        if(manualMovement&&!dodge)return;
        if(destination!=null&&destination.equals(tile)&&f.moving&&f.interacting<0&&f.tick-moveRequested<4)return;
        plan.add(manualMovement?command("movement.tile","x",tile.x,"y",tile.y,"plane",0,"snap",true)
            :command("movement.tile","x",tile.x,"y",tile.y,"plane",0));
        destination=tile;moveRequested=f.tick;
    }
    private Style openingStyle()
    {
        return firstWarband==Role.BERSERKER?Style.MAGE:firstWarband==Role.SEER?Style.RANGED:Style.MELEE;
    }
    /** Recorded entrance waypoint, optional space-making drop, then the northwest start tile. */
    private boolean opening(ColosseumSnapshot f,ColosseumCollision collision,List<JsonObject> plan)
    {
        if(wave>0||openingPhase>=3){openingPhase=3;return true;}
        if(!collision.available()){status="Waiting for current collision before opening setup";return false;}
        Style style=openingStyle();
        Tile corner=new Tile(1813,3108),pivot=new Tile(1815,3108),current=new Tile(f.x,f.y);
        boolean openingMove=false;
        if(!openingApproachSent)
        {
            openingApproachSent=true;
            Tile entry=new Tile(1814,3107);
            if(current.distance(entry)>7&&collision.paths(current,32).distance(entry)>=0)
            {move(plan,f,entry);openingMove=true;status="Opening: approaching the northwest pillar";}
        }
        // Manual movement: no walk to the corner. A full inventory still drops one brew, wherever the player stands
        // near a pillar (user: the place does not matter). Without the free slot the two-handed ranged swap failed
        // from t336 to t419 until the user dropped a brew by hand (1.0.41 manual 2026-11-04 23:30).
        if(manualMovement&&openingPhase==0)
        {
            if(f.inventory.size()<28||brewOnGround(f,current,1))openingPhase=2;
            else if(nearPillarTile(current))openingPhase=1;
            else status="Opening: stand near a pillar to drop a brew";
        }
        if(!openingMove&&openingPhase==0&&!manualMovement)
        {
            if(f.inventory.size()<28||brewOnGround(f,corner,0))openingPhase=2;
            else if(!current.equals(corner))
            {
                if(collision.paths(current,32).distance(corner)>=0&&!hazard(f,corner))move(plan,f,corner);
                status="Opening: reach the recorded brew-drop corner";openingMove=true;
            }
            else openingPhase=1;
        }
        if(!openingMove&&openingPhase==1)
        {
            boolean ground=manualMovement?brewOnGround(f,current,1):brewOnGround(f,corner,0);
            boolean gone=dropSlot>=0&&f.inventory.stream().noneMatch(i->i.slot==dropSlot&&i.id==dropItem);
            // A player who walks on after the drop leaves it out of view; the emptied slot is enough then.
            if(ground&&(dropItem<0||gone)||manualMovement&&gone||dropItem<0&&f.inventory.size()<28)openingPhase=2;
            else
            {
                if(dropAttempts<3&&f.tick>dropRequested)
                {
                    Item brew=null;
                    for(Item item:f.inventory)if(item.lower.equals("saradomin brew(4)")&&(dropItem<0||item.id==dropItem&&item.slot==dropSlot)){brew=item;break;}
                    if(brew!=null)
                    {
                        dropItem=brew.id;dropSlot=brew.slot;dropRequested=f.tick;dropAttempts++;
                        plan.add(command("inventory.interact","slot",brew.slot,"itemId",brew.id,"option","Drop"));
                    }
                }
                status="Opening: waiting for the dropped brew and free slot";
            }
        }
        if(!openingMove&&openingPhase==2&&!current.equals(pivot))
        {
            if(collision.paths(current,16).distance(pivot)>=0&&!hazard(f,pivot))move(plan,f,pivot);
            status="Opening: move to the recorded start tile";
        }
        if(!gear.ready(f,style))
        {
            JsonObject change=gear.switchTo(f,style);
            if(change!=null){plan.add(change);gearRequested=f.tick;requestedStyle=style;}
            return false;
        }
        if(openingPhase<2||!current.equals(pivot))return false;
        if(style==Style.MAGE&&f.player.has("bloodAutocast")&&(f.bloodLevel==0||f.magic<f.bloodLevel))
        {status="Opening: waiting for observed Blood autocast and its Magic level";return false;}
        openingPhase=3;return true;
    }
    /** Within five tiles of a pillar's 3x3 footprint (the pillars' south-west tiles). */
    static boolean nearPillarTile(Tile tile)
    {
        for(Tile q:new Tile[]{new Tile(1816,3113),new Tile(1816,3098),new Tile(1831,3113),new Tile(1831,3098)})
            if(tile.x>=q.x-5&&tile.x<=q.x+7&&tile.y>=q.y-5&&tile.y<=q.y+7)return true;
        return false;
    }
    private static boolean brewOnGround(ColosseumSnapshot f,Tile tile,int radius)
    {
        for(JsonElement e:f.ground)
        {
            JsonObject item=e.getAsJsonObject();
            if(string(item,"name","").startsWith("Saradomin brew")&&Math.max(Math.abs(integer(item,"tx",-100)-tile.x),Math.abs(integer(item,"ty",-100)-tile.y))<=radius)return true;
        }
        return false;
    }
    /** Ticks after the modifier screen closes in which the opening brew is fetched. */
    static final int BREW_WINDOW=20,BREW_TAKES=3;
    private int brewTakes;
    /**
     * The brew dropped before wave 1 (1813,3108) lies within two tiles and the inventory keeps a free slot after taking
     * it (a two-handed swap needs one), early in a wave: right after the modifier screen, once its enemies have spawned
     * and before they reach the start tile (user). The 1.0.13 rule waited for the player to stand on the drop tile,
     * which the pillar approach never does.
     */
    private boolean brewWanted(ColosseumSnapshot f,long launchAge)
    {
        Tile current=new Tile(f.x,f.y);
        return enteringWave>=1&&enteringWave<=11&&launchAge>=0&&launchAge<=BREW_WINDOW&&brewTakes<BREW_TAKES
            &&f.inventory.size()<=26&&brewOnGround(f,current,2)&&!hazard(f,current);
    }
    /** True while the brew pickup owns this tick: a Take is sent every three ticks until the brew leaves the ground. */
    private boolean pickupBrew(ColosseumSnapshot f,List<JsonObject> plan,long launchAge)
    {
        if(!brewWanted(f,launchAge))return false;
        if(f.tick-pickupRequested>=3)
        {plan.add(command("ground.take","name","Saradomin brew","radius",2));pickupRequested=f.tick;brewTakes++;}
        return true;
    }
    /**
     * Rosewood blowpipe "Rapid Burst" (two darts, 25% energy, no heal). The servers' Toxic blowpipe rule without its
     * HP condition (that spec heals): blowpipe worn, already attacking the target, energy for the spec and the bar off;
     * a lone toggle that the next dart uses (225 of 239 server specs went out alone, never with an attack request), and
     * another as soon as it is spent (gaps of two ticks).
     */
    private long specRequested=-1000;
    private void special(ColosseumSnapshot f,Npc target,List<JsonObject> plan)
    {
        Item weapon=f.weapon();
        if(!ColosseumSpecs.due(f,weapon)||f.interacting!=target.index||f.specOn||f.tick-specRequested<2)return;
        plan.add(command("combat.spec"));specRequested=f.tick;
    }
    private static void setProtection(List<JsonObject> plan,ColosseumSnapshot f,String wanted)
    {
        if(wanted!=null)
        {
            if(f.prayers.contains(wanted))plan.add(command("prayer.flick","prayer",wanted));
            else plan.add(command("prayer.set","prayer",wanted,"on",true));
        }
        else for(String prayer:f.prayers)if(ColosseumThreats.PROTECTIONS.contains(prayer))plan.add(command("prayer.set","prayer",prayer,"on",false));
    }
    private static void offensive(List<JsonObject> plan,ColosseumSnapshot f,Style style)
    {
        String wanted=style==null?"":style==Style.MELEE?ColosseumSol.meleeOffensive(f):string(object(f.player,"bestOffensive"),style==Style.MAGE?"magic":"range","");
        Set<String> offensive=Set.of("AUGURY","RIGOUR","PIETY","MYSTIC_MIGHT","MYSTIC_VIGOUR","EAGLE_EYE","DEADEYE","ULTIMATE_STRENGTH","SUPERHUMAN_STRENGTH","INCREDIBLE_REFLEXES","CHIVALRY","MYSTIC_LORE","HAWK_EYE","BURST_OF_STRENGTH","SHARP_EYE","MYSTIC_WILL");
        if(!wanted.isEmpty()&&!f.prayers.contains(wanted))plan.add(command("prayer.set","prayer",wanted,"on",true));
        for(String p:f.prayers)if(offensive.contains(p)&&!p.equals(wanted))plan.add(command("prayer.set","prayer",p,"on",false));
    }
    private static boolean hasModifierScreen(ColosseumSnapshot f)
    {
        for(JsonElement e:array(f.raw,"widgets"))
        {int id=integer(e.getAsJsonObject(),"id",-1);if(id>=56688655&&id<=56688657)return true;}
        return false;
    }
    private void intermission(ColosseumSnapshot f,ColosseumCollision collision,List<JsonObject> plan,boolean modifierScreen)
    {
        Tile pivot=new Tile(1815,3108),current=new Tile(f.x,f.y);
        if(wave>0&&!current.equals(pivot)&&collision.available()
            &&collision.paths(current,40).distance(pivot)>=0&&!hazard(f,pivot))move(plan,f,pivot);
        Style nextStyle=wave>=11?Style.MELEE:openingStyle();
        if(!gear.ready(f,nextStyle))
        {JsonObject change=gear.switchTo(f,nextStyle);if(change!=null){plan.add(change);gearRequested=f.tick;}}
        offensive(plan,f,null);
        if(!modifierScreen&&wave==0&&current.equals(pivot))
        {
            for(Npc n:f.npcs)if(n.alive()&&n.role==Role.MINIMUS)
            {plan.add(command("npc.interact","index",n.index,"option","Start-wave"));break;}
        }
        status=modifierScreen?"Waiting for the selected next wave":"Between waves; returning to the start tile";
    }
    /**
     * Preference among modifiers no server run ranked, best first (user's 1.0.20 fix, kept in 1.0.39). Reentry's sand
     * is already a hazard the engine walks around; Volatility explodes enemies the engine kills in melee beside it; Doom
     * III kills at five stacks.
     */
    private static final List<String> UNRANKED_ORDER=List.of("quartet","totemic","red flag","dynamic duo","bees",
        "relentless","solarflare","mantimayhem","blasphemy","myopia","frailty","reentry","volatility","doom");
    /**
     * The recorded server rank of an offer. Offers the servers never ranked tie at 0 (or -1 for an unseen tier); the
     * leftmost used to win, which picked Volatility over Reentry and Bees (local 18:56, 1.0.13: 59 damage from dying
     * Archers at the pillar). Those ties follow {@link #UNRANKED_ORDER}, below every ranked offer.
     */
    static double modifierPreference(ColosseumEvidence evidence,JsonObject widget){return modifierPreference(evidence,widget,99);}
    /**
     * 1.0.95, two rules for a player who takes unprotected hits (wiki figures):
     * <ul>
     * <li>Blasphemy drains prayer by 20/40/60% of the damage taken. The servers took it first because they are rarely hit;
     * here it emptied the prayer bar. Live 2026-11-09 17:44 (Blasphemy III, 45 prayer): wave 4, one late prayer click on a
     * Manticore volley, 32 damage and 19 prayer gone, prayer 6 then 0, the next two orbs unprotected (70 damage, 7 HP
     * left); wave 5, 21 and 27 damage drained 12 and the last 7, death at 0 prayer. The 12:56 run died on Blasphemy III
     * too (102 prayer drained). Tiers II and III now lose to every offer but Bees and Doom.</li>
     * <li>Relentless I/II let enemies bypass 33/66% of the Defence level and hit 1/3 higher. At Defence
     * {@link #LOW_DEFENCE} or less there is nothing to bypass, so it is the cheapest offer and is taken first.</li>
     * </ul>
     */
    static final boolean BLASPHEMY_CAP=!"false".equals(System.getProperty("colo.blasphemyCap")),
        RELENTLESS_FIRST=!"false".equals(System.getProperty("colo.relentlessFirst"));
    static final int LOW_DEFENCE=10;
    static double modifierPreference(ColosseumEvidence evidence,JsonObject widget,int defence)
    {
        String token=ColosseumEvidence.modifierToken(widget);
        // Doom: every hit adds a stack and we take many hits; it loses to every other offer, ranked or not (user, 2026-11-05).
        if(token!=null&&token.startsWith("doom:"))return -2;
        // 1.0.60: Bees last but Doom. The server runs never took Bees (user, 2026-11-05); the swarm deals up to 10 a tick
        // under the player and the engine never attacks it (simulator 1.0.59: 35 of 400 deaths to Bees).
        if(token!=null&&token.startsWith("bees:"))return -1.5;
        // Above -1, the line an offer must pass to be picked at all: it is still taken when the others are Bees and Doom.
        if(BLASPHEMY_CAP&&("blasphemy:2".equals(token)||"blasphemy:3".equals(token)))return -0.9;
        if(RELENTLESS_FIRST&&defence<=LOW_DEFENCE&&("relentless:1".equals(token)||"relentless:2".equals(token)))return 1000;
        double s=evidence.modifierScore(widget);if(s>0)return s;
        int order=token==null?-1:UNRANKED_ORDER.indexOf(token.substring(0,token.indexOf(':')));
        return (s<0?-0.5:0)+(order<0?0:(UNRANKED_ORDER.size()-order)*0.01);
    }
    private void selectModifier(ColosseumSnapshot f,List<JsonObject> plan)
    {
        JsonObject best=null,continuation=null;double score=-1;
        for(JsonElement e:array(f.raw,"widgets"))
        {
            JsonObject w=e.getAsJsonObject();int id=integer(w,"id",-1);if((id>>>16)!=865)continue;
            if(id==56688681)continuation=w;
            if(id<56688655||id>56688657)continue;
            double s=modifierPreference(evidence,w,f.baseDefence);if(s>score){score=s;best=w;}
        }
        if(best==null)return;
        if(selectedModifier<0||selectedWave!=wave)
        {
            selectedModifier=integer(best,"id",-1);selectedWave=wave;modifierDelivered=false;widgetInFlight=true;widgetRequested=f.tick;
            modifierBaseline=integer(f.player,"selectedModifier",Integer.MIN_VALUE);
            modifiers.selected(best);
            plan.add(command("widget.click","id",selectedModifier,"option","Set"));status="Selecting recorded modifier preference";return;
        }
        // 1.0.61 failsafe: a Set click the game never registered left Continue repeating forever (simulator 1.0.60: 18 of
        // 400 runs stuck at a modifier screen). Still on the screen five ticks after the first Continue: select again.
        if(ColosseumDecisionEngine.FAILSAFES&&continueRequested&&continueSince>=0&&f.tick-continueSince>=MODIFIER_RETRY)
        {selectedModifier=-1;continueRequested=false;continueSince=-1;status="Modifier not taken; selecting again";return;}
        if(f.tick>widgetRequested&&continuation!=null)
        {
            if(!continueRequested)continueSince=f.tick;
            // Source policy sends Continue on the next observation and retries while visible.
            // The old checkpoint waited for an added varbit absent from the source wire.
            plan.add(command("widget.click","id",56688681,"option","Continue"));
            widgetInFlight=true;continueRequested=true;widgetRequested=f.tick;
            modifiers.continued();
            status="Waiting for observed next-wave arrival";return;
        }
        status="Waiting for modifier click delivery";
    }
    public void onResult(JsonObject result)
    {
        String op=string(result,"op","");
        if("widget.click".equals(op)&&widgetInFlight)
        {
            widgetInFlight=false;
            if(!bool(result,"delivered",false)&&!continueRequested){selectedModifier=-1;modifiers.rejectedSelection();}
        }
        if("movement.tile".equals(op)&&!bool(result,"delivered",false))destination=null;
        if("npc.attack".equals(op)&&!bool(result,"delivered",false))targetRequested=-1;
        late.onResult(result);
    }
    private String failsafeStatus;
    public String status() { return failsafeStatus!=null?failsafeStatus:lateActive?late.status():status; }

    /** Worker-published receipt: Minimus may start only after the local opening reaches its pivot. */
    public boolean readyForFirstWave()
    {
        return openingPhase>=3&&evidence!=null&&evidence.failure==null;
    }

    static JsonObject command(String operation, Object... pairs)
    {
        JsonObject packet = new JsonObject(), args = new JsonObject();
        packet.addProperty("op", operation);
        for (int i = 0; i < pairs.length; i += 2)
        {
            String key = (String) pairs[i]; Object value = pairs[i + 1];
            if (value instanceof Number) args.addProperty(key, (Number) value);
            else if (value instanceof Boolean) args.addProperty(key, (Boolean) value);
            else args.addProperty(key, String.valueOf(value));
        }
        packet.add("args", args); return packet;
    }
}
