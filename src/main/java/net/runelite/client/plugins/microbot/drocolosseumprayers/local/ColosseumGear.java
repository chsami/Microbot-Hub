package net.runelite.client.plugins.microbot.drocolosseumprayers.local;

import com.google.gson.*;
import java.util.*;
import static net.runelite.client.plugins.microbot.drocolosseumprayers.local.ColosseumSnapshot.*;

/**
 * Labels every carried item by equipment slot and by how much it helps each style, then builds a style's
 * switch as the best item per slot. Multi-style items (a god book or antler guard for mage and melee, the
 * Infernal cape) are simply scored for each style; a stronger piece for one style (Dizana's quiver for
 * range, an imbued or max cape for magic) wins that slot. Warband counter-style is independent of
 * first-target priority.
 */
final class ColosseumGear
{
    enum Style { MAGE, RANGED, MELEE }
    /**
     * Click order. The weapon goes first so a two-handed swap frees the shield slot before the off-hand is worn
     * (recorded server sets: weapon, then cape/amulet/book). Ammunition is never switched.
     */
    static final int[] ORDER={3,5,0,1,2,4,7,9,10,12};
    private static final int WEAPON=3,SHIELD=5,AMMO=13;
    final ColosseumEvidence evidence;
    ColosseumGear(ColosseumEvidence evidence){this.evidence=evidence;}
    /**
     * A piece requested on {@link #ATTEMPTS} separate ticks that is still not worn (a level requirement, no Wear
     * option) is set aside for {@link #BLOCK_TICKS}: the switch counts as ready without it, so attacks are never held
     * waiting for it. It is retried afterwards, so a temporary hold (modifier choice, paused input) costs nothing.
     * A piece requested on {@link #ATTEMPTS} ticks while the switch's weapon was already worn (counted across that
     * style's switches until the piece is seen worn with that style's weapon) shows input working and only that piece staying off: it is left
     * out of that style's switch for the rest of the run (server runs 10-30 to 11-02: the mage setup never wears the carried
     * Unholy book; every later mage switch otherwise held the attack 4 ticks to retry it). Other styles keep it.
     */
    static final int ATTEMPTS=4,BLOCK_TICKS=25;
    private final Map<String,Integer> attempts=new HashMap<>(),afterWeapon=new HashMap<>();
    private final Map<String,Long> requestedAt=new HashMap<>();
    private final Map<Integer,Long> blockedUntil=new HashMap<>();
    private final Map<Style,Set<Integer>> declined=new EnumMap<>(Style.class);
    void reset(){attempts.clear();afterWeapon.clear();requestedAt.clear();blockedUntil.clear();declined.clear();}
    private static String key(Style style,int id){return style.ordinal()+":"+id;}
    private static int id(String key){return Integer.parseInt(key.substring(key.indexOf(':')+1));}
    /** Once per snapshot, before any switch is chosen. */
    void observe(ColosseumSnapshot f)
    {
        Set<Integer> worn=new HashSet<>();for(Item item:f.equipment)worn.add(item.id);
        attempts.keySet().removeIf(k->worn.contains(id(k)));
        // Worn with another style's weapon says nothing about this style (the melee setup wears the book).
        Style holding=type(f.weapon());afterWeapon.keySet().removeIf(k->worn.contains(id(k))&&holding!=null&&k.startsWith(holding.ordinal()+":"));
        requestedAt.keySet().removeIf(k->worn.contains(id(k)));
        blockedUntil.keySet().removeAll(worn);
        blockedUntil.values().removeIf(until->until<=f.tick);
        // A request that stopped (the style changed) starts its count again next time.
        requestedAt.entrySet().removeIf(e->{boolean stale=f.tick-e.getValue()>5;if(stale)attempts.remove(e.getKey());return stale;});
    }
    private boolean blocked(int id){return blockedUntil.containsKey(id);}
    private boolean blocked(Style style,int id){return blocked(id)||declined.getOrDefault(style,Set.of()).contains(id);}
    // Item IDs (cache 2740) so switches work before item names are cached.
    private static final Map<Integer,Style> WEAPONS=new HashMap<>();
    private static final Map<Integer,Integer> WEAPON_RANK=new HashMap<>();
    private static final Set<Integer> TWO_HANDED=new HashSet<>();
    /** Stats for common pieces, used when the game's item stats aren't in the snapshot. */
    private static final Map<Integer,JsonObject> KNOWN=new HashMap<>();
    static
    {
        weapons(Style.MELEE,90,22325,25736,25739);          // Scythe of vitur (+ornaments)
        weapons(Style.MELEE,80,26219);                      // Osmumten's fang
        weapons(Style.MELEE,75,28997);                      // Dual macuahuitl (Blood Moon set weapon)
        weapons(Style.MELEE,70,4151,12773,12774,12006);     // Abyssal whip (+lava/ice), tentacle
        weapons(Style.MELEE,60,4587,20000);                 // Dragon scimitar (+or)
        weapons(Style.MELEE,50,21742);                      // Granite hammer
        weapons(Style.MELEE,45,11037);                      // Brine sabre
        weapons(Style.MELEE,40,1333);                       // Rune scimitar
        weapons(Style.RANGED,90,20997);                     // Twisted bow
        weapons(Style.RANGED,85,25865,25867,25884,25886,25888,25890,25892,25894,25896); // Bow of faerdhinen (c)
        weapons(Style.RANGED,80,11785);                     // Armadyl crossbow
        weapons(Style.RANGED,75,29000);                     // Eclipse atlatl (Eclipse Moon set weapon)
        weapons(Style.RANGED,70,21902,21012,12926);         // Dragon/dragon hunter crossbow, blowpipe
        weapons(Style.RANGED,40,12788,861);                 // Magic shortbow (i), magic shortbow
        weapons(Style.MAGE,80,21006);                       // Kodai wand
        weapons(Style.MAGE,70,27624,28262,28260);           // Ancient sceptres
        weapons(Style.MAGE,60,6914);                        // Master wand
        weapons(Style.MAGE,50,28988);                       // Blue Moon spear: a bladed staff, autocasts Ancients (not a spear)
        weapons(Style.MAGE,20,4675);                        // Ancient staff
        TWO_HANDED.addAll(List.of(28988,28997,29000,22325,25736,25739,20997,12926,12788,861,25865,25867,25884,25886,25888,25890,25892,25894,25896));
        // Void helms carry no bonuses; the set effect is what makes each one the pick for its style.
        known(0,0,0,0,0,0,0,0,0,11663,11664,11665);
        known(1,4,4,4,1,1,8,0,2,21295,21285);               // Infernal cape (+max)
        known(1,0,0,0,0,18,0,3,0,28951);                    // Dizana's quiver (other variants by name)
        known(1,0,0,0,15,0,0,0,0,21791,21793,21795,24248,24249,24250); // Imbued god capes; max capes by name
        known(1,0,0,0,0,4,0,0,0,10499);                     // Ava's accumulator
        known(5,8,8,8,8,8,0,0,5,3840,3842,3844,12608,12610,12612); // Completed god books: every style
        known(5,0,0,0,15,0,0,0,0,6889);                     // Mage's book
        known(5,20,19,18,-3,-2,5,0,0,8850);                 // Rune defender
        known(5,25,24,23,-3,-2,6,0,0,12954);                // Dragon defender
        known(5,30,29,28,-5,-4,8,0,0,22322);                // Avernic defender
        known(2,10,10,10,10,10,8,0,3,6585,12436);           // Amulet of fury (+or)
        known(2,0,0,0,12,0,0,0,0,12002);                    // Occult necklace (+5% magic damage below)
        KNOWN.get(12002).addProperty("mdmg",5);
        known(2,15,15,15,0,0,10,0,0,19553);                 // Amulet of torture
        known(2,0,0,0,0,15,0,5,0,19547);                    // Necklace of anguish
    }
    private static void weapons(Style style,int rank,int... ids){for(int id:ids){WEAPONS.put(id,style);WEAPON_RANK.put(id,rank);}}
    private static void known(int eq,int stab,int slash,int crush,int magic,int range,int str,int rstr,int prayer,int... ids)
    {
        for(int id:ids)
        {
            JsonObject g=new JsonObject();g.addProperty("eq",eq);g.addProperty("stab",stab);g.addProperty("slash",slash);
            g.addProperty("crush",crush);g.addProperty("magic",magic);g.addProperty("range",range);g.addProperty("str",str);
            g.addProperty("rstr",rstr);g.addProperty("prayer",prayer);KNOWN.put(id,g);
        }
    }
    /** The snapshot's own stats when present, else the built-in table. */
    private static JsonObject stats(Item item){return item.gear!=null?item.gear:KNOWN.get(item.id);}
    static boolean twoHanded(Item item)
    {
        if(item==null)return false;if(item.gear!=null&&item.gear.has("twoHanded"))return bool(item.gear,"twoHanded",false);
        if(TWO_HANDED.contains(item.id))return true;String n=item.lower;
        return n.contains("scythe")||n.contains("tumeken")||n.contains("blowpipe")||n.contains("chinchompa")||n.contains("halberd")
            ||n.contains("bow")&&!n.contains("crossbow");
    }
    static Style counter(Npc target,Style general)
    {
        if(target.role==Role.BERSERKER)return Style.MAGE;
        if(target.role==Role.SEER)return Style.RANGED;
        if(target.role==Role.ARCHER)return Style.MELEE;
        return general;
    }
    static Style type(Item item)
    {
        if(item==null)return null;Style known=WEAPONS.get(item.id);if(known!=null)return known;String n=item.lower;
        if(n.contains("blue moon spear"))return Style.MAGE;
        if(n.contains("atlatl"))return Style.RANGED;
        if(n.contains("macuahuitl"))return Style.MELEE;
        if(n.contains("wand")||n.contains("staff")||n.contains("sceptre")||n.contains("trident")||n.contains("tumeken"))return Style.MAGE;
        if(n.contains("bow")||n.contains("blowpipe")||n.contains("chinchompa")||n.contains("ballista"))return Style.RANGED;
        if(n.contains("whip")||n.contains("abyssal tentacle")||n.contains("scimitar")||n.contains("sabre")||n.contains("granite hammer")||n.contains("sword")||n.contains("scythe")||n.contains("fang")||n.contains("rapier")||n.contains("halberd")||n.contains("dagger")||n.contains("spear")||n.contains("lance")||n.contains("mace")||n.contains("bludgeon")||n.contains("claws")||n.contains("axe"))return Style.MELEE;
        // Unnamed weapons: a staff's crush bonus can exceed its magic bonus, so ranged/magic attack decides first.
        JsonObject g=item.gear;
        if(g==null||integer(g,"eq",-1)!=WEAPON)return null;
        if(integer(g,"range",0)>0)return Style.RANGED;
        if(integer(g,"magic",0)>=10)return Style.MAGE;
        return Math.max(integer(g,"stab",0),Math.max(integer(g,"slash",0),integer(g,"crush",0)))>0?Style.MELEE:null;
    }
    /** Equipment slot this item goes in, or -1 when it can't be worn. */
    static int slot(Item item)
    {
        if(item.worn)return item.slot;
        JsonObject g=stats(item);if(g!=null)return integer(g,"eq",-1);
        if(type(item)!=null)return WEAPON;
        String n=item.lower;
        // Potions and runes are never worn; their dose suffix would otherwise read as a charged ring or amulet.
        if(n.contains("potion")||n.contains("brew")||n.contains("restore")||n.endsWith(" rune")||n.matches(".*\\(\\d\\)$"))return -1;
        if(n.contains("arrow")||n.contains("bolts"))return AMMO;
        if(n.contains("defender")||n.contains("book")||n.contains("shield")||n.contains("ward")||n.contains("antler guard")||n.contains("buckler"))return SHIELD;
        if(n.contains("amulet")||n.contains("necklace"))return 2;
        if(n.contains("cape")||n.contains("quiver")||n.startsWith("ava's")||n.contains("assembler"))return 1;
        if(n.contains("helm")||n.contains("hat")||n.contains("hood")||n.contains("mask")||n.contains("coif"))return 0;
        if(n.contains("body")||n.contains("top")||n.contains("torso")||n.contains("chestplate")||n.contains("brassard"))return 4;
        if(n.contains("legs")||n.contains("chaps")||n.contains("skirt")||n.contains("tassets")||n.contains("bottom")||n.contains("robe"))return 7;
        if(n.contains("gloves")||n.contains("vambraces")||n.contains("bracelet"))return 9;
        if(n.contains("boots"))return 10;
        if(n.startsWith("ring ")||n.endsWith(" ring")||n.contains("ring of"))return 12;
        return -1;
    }
    /**
     * How much this item helps a style: attack bonus plus damage bonus, with the void helm matching its style on
     * top (its set effect has no listed bonus). Unknown pieces fall back to their name.
     */
    static int score(Item item,Style style)
    {
        String n=item.lower;
        int voidHelm=n.startsWith("void mage helm")||item.id==11663?(style==Style.MAGE?10_000:0)
            :n.startsWith("void ranger helm")||item.id==11664?(style==Style.RANGED?10_000:0)
            :n.startsWith("void melee helm")||item.id==11665?(style==Style.MELEE?10_000:0):-1;
        if(voidHelm>=0)return voidHelm;
        JsonObject g=stats(item);
        if(g!=null)
        {
            int bonus;
            switch(style)
            {
                case MELEE:bonus=Math.max(integer(g,"stab",0),Math.max(integer(g,"slash",0),integer(g,"crush",0)))+integer(g,"str",0);break;
                case RANGED:bonus=integer(g,"range",0)+integer(g,"rstr",0);break;
                default:bonus=integer(g,"magic",0)+Math.round(3*(g.has("mdmg")?g.get("mdmg").getAsFloat():0));
            }
            // Prayer bonus only breaks ties.
            return bonus*10+integer(g,"prayer",0);
        }
        boolean magic=n.contains("occult")||n.contains("mystic")||n.contains("ancestral")||n.contains("virtus")||n.contains("ahrim")||n.contains("mage's book")
            ||n.contains("tormented")||n.contains("elder chaos")||n.contains("imbued")||n.contains("god cape")||n.contains("mage")||n.contains("magic max cape")
            ||n.contains("blue moon")||n.contains("infinity")||n.contains("dagon'hai")||n.contains("enchanted")||n.contains("splitbark")||n.contains("swampbark")
            ||n.contains("bloodbark")||n.contains("lunar")||n.contains("wizard")||n.contains("xerician")||n.contains("battle robe")||n.contains("elidinis")
            ||n.contains("arcane")||n.contains("eternal boots")||n.contains("tome of")||n.contains("seers ring")||n.contains("magus ring")||n.contains("3rd age robe")
            ||n.contains("ghostly")||n.contains("skeletal");
        boolean ranged=n.contains("anguish")||n.contains("d'hide")||n.contains("masori")||n.contains("karil")||n.contains("crystal body")||n.contains("crystal legs")
            ||n.contains("crystal helm")||n.contains("quiver")||n.startsWith("ava's")||n.contains("assembler")||n.contains("armadyl")||n.contains("zaryte")
            ||n.contains("eclipse moon");
        boolean melee=n.contains("torture")||n.contains("bandos")||n.contains("torva")||n.contains("fighter torso")||n.contains("defender")
            ||n.contains("infernal cape")||n.contains("fire cape")||n.contains("berserker")||n.contains("ferocious")||n.contains("blood moon");
        boolean shared=n.contains("book")||n.contains("antler guard")||n.contains("fury");
        if(style==Style.MAGE&&magic||style==Style.RANGED&&ranged||style==Style.MELEE&&melee)return 1000;
        return shared?500:0;
    }
    Item weapon(ColosseumSnapshot f,Style style)
    {
        List<Integer> recorded=evidence.recordedGear(style.name().toLowerCase(Locale.ROOT),f);
        for(int id:recorded)for(Item item:all(f))if(item.id==id&&type(item)==style&&!blocked(style,id))return item;
        Item best=null;
        for(Item item:all(f))if(type(item)==style&&!blocked(style,item.id)&&(best==null||rank(item,style)>rank(best,style)))best=item;
        return best;
    }
    /** Another weapon of the style is carried and not set aside: the switch can fall back to it. */
    private boolean spare(ColosseumSnapshot f,Style style,int weapon)
    {
        for(Item item:all(f))if(item.id!=weapon&&type(item)==style&&!blocked(style,item.id))return true;
        return false;
    }
    private static int rank(Item item,Style style)
    {
        Integer rank=WEAPON_RANK.get(item.id);if(rank!=null)return rank;
        String n=item.lower;
        if(n.contains("twisted")||n.contains("tumeken")||n.contains("scythe"))return 90;
        if(n.contains("faerdhinen")||n.contains("kodai")||n.contains("fang"))return 80;
        if(n.contains("blowpipe")||n.contains("whip")||n.contains("master")||n.contains("zaryte"))return 70;
        // Unlisted weapons rank between the listed tiers by their own bonuses.
        return item.gear!=null?Math.min(65,20+score(item,style)/40):20;
    }
    static List<Item> all(ColosseumSnapshot f){List<Item> all=new ArrayList<>(f.equipment);all.addAll(f.inventory);return all;}
    /** The style's switch: weapon first, then each slot's best carried piece, in {@link #ORDER}. */
    List<Integer> selected(ColosseumSnapshot f,Style style)
    {
        Item weapon=weapon(f,style);if(weapon==null)return List.of();
        List<Integer> ids=new ArrayList<>();
        // A recorded server set is kept as recorded; the labeler only fills the slots it doesn't cover.
        List<Integer> exact=evidence.recordedGear(style.name().toLowerCase(Locale.ROOT),f);
        Set<Integer> covered=new HashSet<>();
        if(!exact.isEmpty())
        {
            for(int id:exact)if(!blocked(style,id))ids.add(id);
            for(int id:exact)for(Item item:all(f))if(item.id==id)covered.add(slot(item));
        }
        else ids.add(weapon.id);
        covered.add(WEAPON);
        boolean twoHanded=twoHanded(weapon);
        String set=moonSet(weapon);
        boolean atlatl=style==Style.RANGED&&"eclipse moon".equals(set);
        Map<Integer,Item> worn=new HashMap<>();for(Item item:f.equipment)worn.put(item.slot,item);
        for(int slot:ORDER)
        {
            if(covered.contains(slot)||slot==SHIELD&&twoHanded)continue;
            Item current=worn.get(slot),best=current;
            // An empty slot scores 0: a carried piece is worn only if it helps this style.
            int bestScore=current==null?0:setScore(current,style,set)+atlatlStrength(current,atlatl);
            for(Item item:f.inventory)
            {
                if(slot(item)!=slot||ids.contains(item.id)||blocked(style,item.id))continue;
                int s=setScore(item,style,set)+atlatlStrength(item,atlatl);
                if(s>bestScore){best=item;bestScore=s;}
            }
            if(best!=null&&best!=current)ids.add(best.id);
        }
        return ids;
    }
    /**
     * The Moon sets' effects need the helm, chestplate and tassets worn with that set's weapon (Blood Moon: dual
     * macuahuitl, Eclipse Moon: atlatl, Blue Moon: spear; user, 2026-11-05). With the weapon chosen, its set's pieces
     * win their slots, above a void helm. Degraded pieces keep the name and count too.
     */
    static String moonSet(Item weapon)
    {
        if(weapon==null)return null;String n=weapon.lower;
        if(weapon.id==28997||n.contains("macuahuitl"))return "blood moon";
        if(weapon.id==29000||n.contains("atlatl"))return "eclipse moon";
        if(weapon.id==28988||n.contains("blue moon spear"))return "blue moon";
        return null;
    }
    private static final Map<Integer,String> MOON_PIECES=new HashMap<>();
    static
    {
        for(int id:new int[]{29022,29025,29028,29043,29045,29047})MOON_PIECES.put(id,"blood moon");
        for(int id:new int[]{29004,29007,29010,29031,29033,29035})MOON_PIECES.put(id,"eclipse moon");
        for(int id:new int[]{29013,29016,29019,29037,29039,29041})MOON_PIECES.put(id,"blue moon");
    }
    /**
     * The atlatl's max hit comes from the melee strength bonus, not ranged strength (wiki); its accuracy is still
     * ranged. With it worn for range, a piece's melee strength counts in place of its ranged strength.
     */
    static int atlatlStrength(Item item,boolean atlatl)
    {
        JsonObject g=atlatl?stats(item):null;
        return g==null?0:(integer(g,"str",0)-integer(g,"rstr",0))*10;
    }
    static int setScore(Item item,Style style,String set)
    {
        int s=score(item,style);
        if(set!=null&&(set.equals(MOON_PIECES.get(item.id))||item.lower.startsWith(set)&&!item.lower.contains("broken")))s+=20_000;
        return s;
    }
    boolean ready(ColosseumSnapshot f,Style style)
    {
        List<Integer> ids=selected(f,style);return !ids.isEmpty()&&ids.stream().allMatch(f::worn);
    }
    JsonObject switchTo(ColosseumSnapshot f,Style style)
    {
        List<Integer> ids=selected(f,style);if(ids.isEmpty())return null;
        Item held=weapon(f,style);int weapon=held==null?-1:held.id;
        for(int id:ids)
        {
            String k=key(style,id);
            if(f.worn(id)||Objects.equals(requestedAt.get(k),f.tick))continue;
            requestedAt.put(k,f.tick);
            int n=attempts.merge(k,1,Integer::sum);
            if(id!=weapon&&f.worn(weapon)&&afterWeapon.merge(k,1,Integer::sum)>=ATTEMPTS)
            {declined.computeIfAbsent(style,x->new HashSet<>()).add(id);attempts.remove(k);afterWeapon.remove(k);continue;}
            if(n>=ATTEMPTS)
            {
                // 1.0.95: the style's only weapon is never set aside. Without it the style has no weapon at all, and the
                // engine held every action (attacks, walking, the Javelin sidestep) for the 25 ticks: live 2026-11-09
                // 17:44 wave 3 t492-515 (four Wield clicks lost to prayer clicks, then 24 ticks frozen beside two Seers
                // and an Archer, 38 from a Javelin special never stepped off); the same freeze in the 13:44 and 16:18 runs.
                if(id!=weapon||spare(f,style,id))blockedUntil.put(id,f.tick+BLOCK_TICKS);
                attempts.remove(k);
            }
        }
        JsonObject packet=ColosseumDecisionEngine.command("equipment.set","name",style.name().toLowerCase(Locale.ROOT));
        JsonArray items=new JsonArray();for(int id:ids)items.add(id);packet.getAsJsonObject("args").add("items",items);return packet;
    }
    /**
     * User rule (2026-11-05): before the last-enemy Blood heal, take off worn legs that carry a magic attack penalty (ranged
     * d'hide/Hueycoatl chaps) when a free inventory slot can take them: higher Blood Barrage accuracy. The next ranged
     * switch puts them back on (they stay in the setup). Null when nothing to remove, no room, or asked within 3 ticks.
     */
    JsonObject shedLegsForMagic(ColosseumSnapshot f)
    {
        if(f.inventory.size()>=28||f.tick-legsShedAt<3)return null;
        for(Item i:f.equipment)
        {
            if(i.slot!=LEGS_SLOT)continue;
            boolean penalty=i.gear!=null?i.stat("magic")<0:i.lower.contains("chaps")||i.lower.contains("d'hide")||i.lower.contains("hide");
            if(!penalty)return null;
            legsShedAt=f.tick;
            return ColosseumDecisionEngine.command("equipment.slot","slot",LEGS_SLOT,"option","Remove");
        }
        return null;
    }
    static final int LEGS_SLOT=7;
    private long legsShedAt=-1000;
    int range(ColosseumSnapshot f,Style style)
    {
        Item weapon=weapon(f,style);if(weapon==null)return 0;if(style==Style.MELEE)return 1;
        String n=weapon.lower;
        int base=n.contains("blowpipe")||n.contains("atlatl")||weapon.id==29000?5:n.contains("crossbow")||n.contains("ballista")?8:
            n.contains("shortbow")||weapon.id==12788||weapon.id==861?7:10;
        return Math.max(1,base-2*f.myopia);
    }
    /** Ticks between attacks: the weapon's own speed, one faster on Rapid for a ranged weapon. */
    static int speed(ColosseumSnapshot f,Item weapon)
    {
        if(weapon==null)return 0;
        int base=weapon.stat("speed");
        String n=weapon.lower;
        if(base<=0)
        {
            if(n.contains("blowpipe"))base=3;
            else if(n.contains("whip")||n.contains("abyssal tentacle")||n.contains("scimitar")||n.contains("rapier")||n.contains("sabre")
                ||n.contains("granite hammer")||n.contains("shortbow")||n.contains("faerdhinen")||n.contains("crystal bow")
                ||n.contains("atlatl")||n.contains("macuahuitl")
                ||Set.of(4151,12773,12774,12006,4587,20000,1333,11037,21742,12788,861,28997,29000).contains(weapon.id))base=4;
            else if(n.contains("crossbow")||n.contains("twisted bow"))base=6;
            else base=5;
            // Crossbows and the twisted bow list 6 and fire at 5 on Rapid; spells always take 5.
        }
        if(type(weapon)==Style.MAGE)return 5;
        if(type(weapon)==Style.RANGED&&f.attackStyle()==1)base--;
        return Math.max(1,base);
    }
}
