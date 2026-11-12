package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.*;
import java.util.regex.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumCollision.Tile;
import net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumGear.Style;

public final class ColosseumDecisionEngine
{
    private String status = "Loading";
    private ColosseumTables plans;
    private final ColosseumThreats threats=new ColosseumThreats();
    private final ColosseumSol sol=new ColosseumSol();
    private final ColosseumModifiers modifiers=new ColosseumModifiers();
    private long previousTick=-1,widgetRequested=-1000;
    private int wave,selectedModifier=-1,selectedWave=-1,modifierBaseline;
    private long continueSince=-1;static final int MODIFIER_RETRY=5;
    private boolean waveFinished,modifierDelivered,widgetInFlight;
    private boolean modifierWasVisible,continueRequested;
    private long modifierClosed=-1000;
    private int enteringWave;
    private long playerEngaged=-1000;
    private boolean handedOver;
    static final int HANDOVER_WAVE=5;
    private static final Pattern WAVE=Pattern.compile("(?i)wave\\s*:?\\s+(\\d+)");
    private volatile String liveState="Wait";

    public void reset()
    {
        status="Waiting for a complete arena snapshot";threats.reset();sol.reset();modifiers.reset();
        previousTick=-1;wave=0;selectedModifier=-1;selectedWave=-1;widgetRequested=-1000;modifierDelivered=widgetInFlight=false;
        modifierWasVisible=continueRequested=false;modifierClosed=-1000;enteringWave=0;continueSince=-1;playerEngaged=-1000;handedOver=false;
    }
    public void configure(Map<String, Object> options)
    {
        if(plans==null)plans=ColosseumTables.load();
        status=plans.failure==null?"Ready":plans.failure;
    }
    public List<JsonObject> decide(JsonObject snapshot)
    {
        List<JsonObject> ordered=commandOrder(prayersOnly(snapshot,plan(snapshot)));
        liveState=ordered.isEmpty()?"Wait":"Pray";
        return ordered;
    }
    public String liveState(){return liveState;}
    public Map<String,Integer> activeModifiers(){return modifiers.active();}
    public String status(){return status;}
    public boolean readyForFirstWave(){return false;}

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

    static List<JsonObject> commandOrder(List<JsonObject> commands)
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
        if(plans==null)configure(Map.of());
        List<JsonObject> plan=new ArrayList<>();
        if(longValue(snapshot,"tick",-1)<=previousTick)return plan;
        ColosseumSnapshot f=new ColosseumSnapshot(snapshot,modifiers.observe(snapshot));
        previousTick=f.tick;observeWave(f);threats.observe(f);
        if(!handedOver&&Math.max(wave,modifiersTaken(snapshot))>=HANDOVER_WAVE)
        {handedOver=true;selectedModifier=selectedWave=-1;modifierDelivered=widgetInFlight=continueRequested=false;}
        if(!f.playable()||!bool(snapshot,"inputAllowed",true))
        {status="Waiting for live Colosseum input permission";return plan;}
        if(plans.failure!=null){status=plans.failure;return plan;}
        Npc boss=null;for(Npc n:f.npcs)if(n.alive()&&n.role==Role.SOL){boss=n;break;}
        if(boss!=null)
        {
            wave=12;
            plan.addAll(sol.decide(f,boss,new ColosseumCollision(f)));status=sol.status();return plan;
        }

        for(Npc n:f.npcs)if(n.enemy()&&n.role==Role.UNKNOWN&&!plans.observedEnemies.contains(n.id))
        {status="Unknown Colosseum enemy/modifier "+n.id+"; actions held for manual control";return plan;}
        ColosseumCollision collision=new ColosseumCollision(f);Tile current=new Tile(f.x,f.y);
        boolean modifierScreen=hasModifierScreen(f);
        if(modifierWasVisible&&!modifierScreen&&continueRequested)
        {
            modifierClosed=f.tick;enteringWave=wave+1;continueRequested=false;
        }
        if(modifierScreen&&!modifierWasVisible)
        {selectedModifier=-1;modifierDelivered=widgetInFlight=false;}
        modifierWasVisible=modifierScreen;
        long launchAge=f.tick-modifierClosed;
        String protection=threats.protection(f,collision,current);

        if(enteringWave>=1&&enteringWave<=11&&launchAge>=2&&launchAge<=6&&f.y<=3112)
        {
            if(enteringWave==1||enteringWave==4||enteringWave==8||enteringWave==11)
                protection="PROTECT_FROM_MAGIC";
            else if(enteringWave!=10)protection="PROTECT_FROM_MISSILES";
        }
        if(protection!=null||collision.available())setProtection(plan,f,protection);
        if(modifierScreen)selectModifier(f,plan);
        status=protectionStatus(protection);
        return plan;
    }

    private static String protectionStatus(String protection)
    {
        if(protection==null)return "Wave tracking";
        return protection.equals("PROTECT_FROM_MAGIC")?"Protect from Magic":protection.equals("PROTECT_FROM_MISSILES")?"Protect from Missiles":"Protect from Melee";
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

    static int modifiersTaken(JsonObject snapshot)
    {
        JsonObject levels=object(object(snapshot,"player"),"modifiers");int taken=0;
        for(Map.Entry<String,JsonElement> e:levels.entrySet())
            if(!e.getKey().equals("doomPoints")&&e.getValue().isJsonPrimitive())taken+=e.getValue().getAsInt();
        return taken;
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

    private static final List<String> UNRANKED_ORDER=List.of("quartet","totemic","red flag","dynamic duo","bees",
        "relentless","solarflare","mantimayhem","blasphemy","myopia","frailty","reentry","volatility","doom");
    static double modifierPreference(ColosseumTables plans,JsonObject widget){return modifierPreference(plans,widget,99);}
    static final boolean BLASPHEMY_CAP=true,RELENTLESS_FIRST=true;
    static final int LOW_DEFENCE=10;
    static double modifierPreference(ColosseumTables plans,JsonObject widget,int defence)
    {
        String token=ColosseumTables.modifierToken(widget);

        if(token!=null&&token.startsWith("doom:"))return -2;

        if(token!=null&&token.startsWith("bees:"))return -1.5;

        if(BLASPHEMY_CAP&&("blasphemy:2".equals(token)||"blasphemy:3".equals(token)))return -0.9;
        if(RELENTLESS_FIRST&&defence<=LOW_DEFENCE&&("relentless:1".equals(token)||"relentless:2".equals(token)))return 1000;
        double s=plans.modifierScore(widget);if(s>0)return s;
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
            double s=modifierPreference(plans,w,f.baseDefence);if(s>score){score=s;best=w;}
        }
        if(best==null)return;
        if(selectedModifier<0||selectedWave!=wave)
        {
            selectedModifier=integer(best,"id",-1);selectedWave=wave;modifierDelivered=false;widgetInFlight=true;widgetRequested=f.tick;
            modifierBaseline=integer(f.player,"selectedModifier",Integer.MIN_VALUE);
            modifiers.selected(best);
            plan.add(command("widget.click","id",selectedModifier,"option","Set"));status="Selecting the preferred modifier";return;
        }

        if(continueRequested&&continueSince>=0&&f.tick-continueSince>=MODIFIER_RETRY)
        {selectedModifier=-1;continueRequested=false;continueSince=-1;status="Modifier not taken; selecting again";return;}
        if(f.tick>widgetRequested&&continuation!=null)
        {
            if(!continueRequested)continueSince=f.tick;

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
