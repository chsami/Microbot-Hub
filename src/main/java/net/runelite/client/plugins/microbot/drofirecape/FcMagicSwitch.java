/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.drofirecape.core.MagicTactics.Element;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;

/**
 * The carried magic switch, labelled the Colosseum 1.0.82 way (ColosseumGear, mage style): the best magic weapon by
 * rank, then the best carried piece per slot by magic attack plus three per magic damage percent (prayer breaks
 * ties), a void mage helm over any helm, and the Blue Moon pieces over everything with the Blue Moon spear.
 * Pieces without game stats use the Colosseum stat table, then their name. The pieces worn before the first switch
 * are put back afterwards. A carried item's recorded inventory position is never an identity.
 */
final class FcMagicSwitch {
    static final int HEAD=0,WEAPON=3,SHIELD=5,LEGS=7;
    /** Click order (Colosseum): the weapon first so a two-handed swap frees the shield slot. Ammunition is never switched. */
    static final int[] ORDER={3,5,0,1,2,4,7,9,10,12};

    static final class Piece {
        final int id,slot,inventorySlot,score;
        final boolean twoHanded,magicPenalty;
        final String name,lower,action;
        Piece(int id,int slot,int inventorySlot,int score,boolean twoHanded,boolean magicPenalty,String name,String action) {
            this.id=id;this.slot=slot;this.inventorySlot=inventorySlot;this.score=score;this.twoHanded=twoHanded;
            this.magicPenalty=magicPenalty;this.name=name==null?"":name;this.lower=this.name.toLowerCase(Locale.ROOT);this.action=action;
        }
        FcFrame.ItemSlot item(){return new FcFrame.ItemSlot(inventorySlot,id,1,name,List.of(action));}
        @Override public String toString(){return name+"#"+id;}
    }
    static final class Loadout {
        final List<Piece> carried;
        final Map<Integer,Piece> worn;
        final int freeSlots;
        Loadout(List<Piece> carried,Map<Integer,Piece> worn){this(carried,worn,0);}
        Loadout(List<Piece> carried,Map<Integer,Piece> worn,int freeSlots){this.carried=carried;this.worn=worn;this.freeSlots=freeSlots;}
        Map<Integer,Integer> wornIds(){Map<Integer,Integer> ids=new TreeMap<>();worn.forEach((slot,p)->ids.put(slot,p.id));return ids;}
        boolean wearing(int id){for(Piece p:worn.values())if(p.id==id)return true;return false;}
    }
    /** Highest Ice and Blood tier castable now (3 Barrage, 2 Blitz, 1 Burst, 0 none). */
    static final class Spells {
        static final Spells NONE=new Spells(0,0);
        final int ice,blood;
        Spells(int ice,int blood){this.ice=ice;this.blood=blood;}
        boolean any(){return ice>0||blood>0;}
        Rs2CombatSpells spell(Element element) {
            int tier=tier(element);
            if(tier<=0)return null;
            return element==Element.ICE?ICE[3-tier]:BLOOD[3-tier];
        }
        int tier(Element element){return element==Element.ICE?ice:blood;}
    }
    static final Rs2CombatSpells[] ICE={Rs2CombatSpells.ICE_BARRAGE,Rs2CombatSpells.ICE_BLITZ,Rs2CombatSpells.ICE_BURST};
    static final Rs2CombatSpells[] BLOOD={Rs2CombatSpells.BLOOD_BARRAGE,Rs2CombatSpells.BLOOD_BLITZ,Rs2CombatSpells.BLOOD_BURST};

    // ---- Colosseum 1.0.82 mage labelling (ColosseumGear) ----
    private static final Map<Integer,Integer> MAGE_WEAPON_RANK=new HashMap<>();
    private static final Set<Integer> TWO_HANDED=new HashSet<>();
    /** {slot, magic attack, magic damage %, prayer} for pieces whose game stats are missing. */
    private static final Map<Integer,int[]> KNOWN=new HashMap<>();
    private static final Map<Integer,String> MOON_PIECES=new HashMap<>();
    static final int BLUE_MOON_SPEAR=28988;
    static {
        rank(80,21006);                 // Kodai wand
        rank(70,27624,28262,28260);     // Ancient sceptres
        rank(60,6914);                  // Master wand
        rank(50,BLUE_MOON_SPEAR);       // Blue Moon spear: a bladed staff that autocasts Ancients
        rank(20,4675);                  // Ancient staff
        TWO_HANDED.addAll(List.of(28988,28997,29000,22325,25736,25739,20997,12926,12788,861,25865,25867,25884,25886,25888,25890,25892,25894,25896));
        known(1,4,0,2,21295,21285);                              // Infernal cape (+max)
        known(1,15,0,0,21791,21793,21795,24248,24249,24250);     // Imbued god capes
        known(5,8,0,5,3840,3842,3844,12608,12610,12612);         // Completed god books
        known(5,15,0,0,6889);                                    // Mage's book
        known(5,-3,0,0,8850);known(5,-3,0,0,12954);known(5,-5,0,0,22322); // Rune/dragon/avernic defender
        known(2,10,0,3,6585,12436);                              // Amulet of fury (+or)
        known(2,12,5,0,12002);                                   // Occult necklace
        known(2,0,0,0,19553);known(2,0,0,0,19547);               // Torture, anguish
        for(int id:new int[]{29022,29025,29028,29043,29045,29047})MOON_PIECES.put(id,"blood moon");
        for(int id:new int[]{29004,29007,29010,29031,29033,29035})MOON_PIECES.put(id,"eclipse moon");
        for(int id:new int[]{29013,29016,29019,29037,29039,29041})MOON_PIECES.put(id,"blue moon");
    }
    private static void rank(int rank,int... ids){for(int id:ids)MAGE_WEAPON_RANK.put(id,rank);}
    private static void known(int slot,int magic,int mdmg,int prayer,int... ids){for(int id:ids)KNOWN.put(id,new int[]{slot,magic,mdmg,prayer});}

    /** Equipment slot from the game's stats, the Colosseum table, then the name (Colosseum slot()); -1 when not wearable. */
    static int slot(int id,String name,ItemEquipmentStats stats) {
        if(stats!=null)return stats.getSlot();
        int[] k=KNOWN.get(id);if(k!=null)return k[0];
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        if(magicWeaponName(n))return WEAPON;
        if(n.contains("potion")||n.contains("brew")||n.contains("restore")||n.endsWith(" rune")||n.matches(".*\\(\\d\\)$"))return -1;
        if(n.contains("defender")||n.contains("book")||n.contains("shield")||n.contains("ward")||n.contains("antler guard")||n.contains("buckler"))return SHIELD;
        if(n.contains("amulet")||n.contains("necklace"))return 2;
        if(n.contains("cape")||n.contains("quiver")||n.startsWith("ava's")||n.contains("assembler"))return 1;
        if(n.contains("helm")||n.contains("hat")||n.contains("hood")||n.contains("mask")||n.contains("coif"))return HEAD;
        if(n.contains("body")||n.contains("top")||n.contains("torso")||n.contains("chestplate")||n.contains("brassard"))return 4;
        if(n.contains("legs")||n.contains("chaps")||n.contains("skirt")||n.contains("tassets")||n.contains("bottom")||n.contains("robe"))return LEGS;
        if(n.contains("gloves")||n.contains("vambraces")||n.contains("bracelet"))return 9;
        if(n.contains("boots"))return 10;
        if(n.startsWith("ring ")||n.endsWith(" ring")||n.contains("ring of"))return 12;
        return -1;
    }
    static boolean magicWeaponName(String n) {
        return n.contains("blue moon spear")||n.contains("wand")||n.contains("staff")||n.contains("sceptre")||n.contains("trident")||n.contains("tumeken");
    }
    /** Colosseum mage score: (magic attack + 3 per magic damage %) x 10 + prayer; void mage helm 10,000; else the name. */
    static int score(int id,String name,ItemEquipmentStats stats) {
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        if(n.startsWith("void mage helm")||id==11663)return 10_000;
        if(n.startsWith("void ranger helm")||n.startsWith("void melee helm")||id==11664||id==11665)return 0;
        if(stats!=null)return (stats.getAmagic()+Math.round(3*stats.getMdmg()))*10+stats.getPrayer();
        int[] k=KNOWN.get(id);
        if(k!=null)return (k[1]+3*k[2])*10+k[3];
        boolean magic=n.contains("occult")||n.contains("mystic")||n.contains("ancestral")||n.contains("virtus")||n.contains("ahrim")||n.contains("mage's book")
            ||n.contains("tormented")||n.contains("elder chaos")||n.contains("imbued")||n.contains("god cape")||n.contains("mage")||n.contains("magic max cape")
            ||n.contains("blue moon")||n.contains("infinity")||n.contains("dagon'hai")||n.contains("enchanted")||n.contains("splitbark")||n.contains("swampbark")
            ||n.contains("bloodbark")||n.contains("lunar")||n.contains("wizard")||n.contains("xerician")||n.contains("battle robe")||n.contains("elidinis")
            ||n.contains("arcane")||n.contains("eternal boots")||n.contains("tome of")||n.contains("seers ring")||n.contains("magus ring")||n.contains("3rd age robe")
            ||n.contains("ghostly")||n.contains("skeletal");
        if(magic)return 1000;
        return n.contains("book")||n.contains("antler guard")||n.contains("fury")?500:0;
    }
    /** A magic weapon's rank (Colosseum weapon ranks); 0 for a weapon that is not a magic weapon. */
    static int weaponRank(Piece p) {
        Integer r=MAGE_WEAPON_RANK.get(p.id);if(r!=null)return r;
        if(!magicWeaponName(p.lower)&&p.score<100)return 0;
        if(p.lower.contains("tumeken"))return 90;
        if(p.lower.contains("kodai"))return 80;
        if(p.lower.contains("master"))return 70;
        return Math.min(65,20+p.score/40);
    }
    static String moonSet(Piece weapon) {
        if(weapon==null)return null;
        if(weapon.id==BLUE_MOON_SPEAR||weapon.lower.contains("blue moon spear"))return "blue moon";
        if(weapon.id==28997||weapon.lower.contains("macuahuitl"))return "blood moon";
        if(weapon.id==29000||weapon.lower.contains("atlatl"))return "eclipse moon";
        return null;
    }
    static int setScore(Piece p,String set) {
        int s=p.score;
        if(set!=null&&(set.equals(MOON_PIECES.get(p.id))||p.lower.startsWith(set)&&!p.lower.contains("broken")))s+=20_000;
        return s;
    }

    /**
     * Pure: the pieces to wear for the magic switch, weapon first then {@link #ORDER}. The best magic weapon by rank
     * is wielded when it beats the worn one; any weapon can still cast, so none is required. A two-handed weapon (worn
     * or chosen) never takes an offhand. A slot changes only for a piece that scores higher than the worn one, where
     * an empty slot scores 0.
     */
    static List<Piece> mageSwitch(Loadout l){return mageSwitch(l,Set.of());}
    static List<Piece> mageSwitch(Loadout l,Set<Integer> excluded) {
        Piece worn=l.worn.get(WEAPON),weapon=worn;
        int bestRank=worn==null?0:weaponRank(worn);
        for(Piece p:l.carried)if(p.slot==WEAPON&&!excluded.contains(p.id)&&!needsRoom(l,p)) {
            int r=weaponRank(p);if(r>bestRank){bestRank=r;weapon=p;}
        }
        List<Piece> out=new ArrayList<>();
        if(weapon!=null&&weapon!=worn)out.add(weapon);
        boolean twoHanded=weapon!=null&&twoHanded(weapon);
        String set=moonSet(weapon);
        for(int slot:ORDER) {
            if(slot==WEAPON||slot==SHIELD&&twoHanded)continue;
            Piece current=l.worn.get(slot),best=current;
            int bestScore=current==null?0:setScore(current,set);
            for(Piece p:l.carried) {
                if(p.slot!=slot||excluded.contains(p.id))continue;
                int s=setScore(p,set);
                if(s>bestScore){best=p;bestScore=s;}
            }
            if(best!=null&&best!=current)out.add(best);
        }
        return out;
    }
    /** Pure: the main-set pieces now carried, weapon first. A main two-handed weapon already covers the offhand. */
    static List<Piece> restore(Loadout l,Map<Integer,Integer> main) {
        List<Piece> out=new ArrayList<>();
        for(Piece p:l.carried) {
            Integer wanted=main.get(p.slot);
            Piece worn=l.worn.get(p.slot);
            if(wanted!=null&&wanted==p.id&&(worn==null||worn.id!=p.id)) {
                if(p.slot==WEAPON)out.add(0,p);else out.add(p);
            }
        }
        for(Piece p:out)if(p.slot==WEAPON&&twoHanded(p)){out.removeIf(o->o.slot==SHIELD);break;}
        // A two-handed main weapon is still listed when the worn offhand has no room: the caller waits instead of
        // clearing the main set (gear only comes off into a free inventory slot).
        return out;
    }
    /** The Saradomin brew with the fewest doses, or null: the only item dropped to free a slot. */
    static FcFrame.ItemSlot roomBrew(List<FcFrame.ItemSlot> inventory) {
        FcFrame.ItemSlot best=null;int fewest=Integer.MAX_VALUE;
        for(FcFrame.ItemSlot i:inventory) {
            String n=i.name()==null?"":i.name().toLowerCase(Locale.ROOT);
            if(!n.startsWith("saradomin brew(")||!n.endsWith(")"))continue;
            int doses;try{doses=Integer.parseInt(n.substring(15,n.length()-1));}catch(NumberFormatException e){continue;}
            if(doses<fewest){fewest=doses;best=i;}
        }
        return best;
    }
    static boolean twoHanded(Piece p){return p.twoHanded||TWO_HANDED.contains(p.id);}
    /**
     * Pure: the ranged main set when the cave is entered wearing the magic switch (live 2026-11-06 Firecape test: Master wand, Unholy
     * book and Occult worn with a Rosewood blowpipe and an Amulet of fury carried; the wand was taken as the main
     * set, so every attack autocast Blood Barrage and the blowpipe never came out). Only when a magic weapon is worn
     * and a non-magic weapon is carried: the carried weapon with the lowest mage score (a two-handed one empties the
     * offhand), and in every other slot the carried piece with a lower mage score than the worn one. Empty otherwise,
     * so a magic-only loadout keeps its gear.
     */
    static Map<Integer,Integer> rangedMain(Loadout l) {
        Piece worn=l.worn.get(WEAPON);
        if(worn==null||weaponRank(worn)<=0)return Map.of();
        Piece weapon=null;
        for(Piece p:l.carried)if(p.slot==WEAPON&&weaponRank(p)==0&&(weapon==null||p.score<weapon.score))weapon=p;
        if(weapon==null)return Map.of();
        Map<Integer,Integer> main=new TreeMap<>(l.wornIds());
        main.put(WEAPON,weapon.id);
        if(twoHanded(weapon))main.remove(SHIELD);
        for(int slot:ORDER) {
            if(slot==WEAPON||slot==SHIELD&&twoHanded(weapon))continue;
            Piece current=l.worn.get(slot),best=null;
            for(Piece p:l.carried)if(p.slot==slot&&(current==null?p.score<=0:p.score<current.score)
                &&(best==null||p.score<best.score))best=p;
            if(best!=null)main.put(slot,best.id);
        }
        return main;
    }
    /** Wielding this two-handed weapon would also take the worn offhand off, with no free inventory slot for it. */
    static boolean needsRoom(Loadout l,Piece p){return p.slot==WEAPON&&twoHanded(p)&&l.worn.get(SHIELD)!=null&&l.freeSlots<1;}
    /**
     * Colosseum 1.0.82 attempts (ColosseumGear.switchTo): a piece requested on {@link #ATTEMPTS} separate ticks that is
     * still not worn is set aside for {@link #BLOCK_TICKS} and retried later; a piece requested {@link #ATTEMPTS} times
     * while the switch's weapon was already worn is left out for the rest of the run. A click on an item made within
     * {@link #RECLICK_NANOS} is never repeated (1.0.74 re-click guard: the item still shows in the inventory until the
     * server's tick, and a second click can put the replaced item back on).
     */
    static final class Attempts {
        static final int ATTEMPTS=4,BLOCK_TICKS=25;
        static final long RECLICK_NANOS=1_200_000_000L;
        private final Map<Integer,Integer> attempts=new HashMap<>(),afterWeapon=new HashMap<>(),requestedAt=new HashMap<>(),blockedUntil=new HashMap<>();
        private final Set<Integer> declined=new HashSet<>();
        private final Map<Integer,Long> clickedAt=new HashMap<>();
        void reset(){attempts.clear();afterWeapon.clear();requestedAt.clear();blockedUntil.clear();declined.clear();clickedAt.clear();}
        /** Once per tick, before a switch is chosen. */
        void observe(Loadout l,int tick) {
            Set<Integer> worn=new HashSet<>();for(Piece p:l.worn.values())worn.add(p.id);
            attempts.keySet().removeAll(worn);afterWeapon.keySet().removeAll(worn);requestedAt.keySet().removeAll(worn);
            blockedUntil.keySet().removeAll(worn);blockedUntil.values().removeIf(until->until<=tick);
            requestedAt.entrySet().removeIf(e->{boolean stale=tick-e.getValue()>5;if(stale)attempts.remove(e.getKey());return stale;});
        }
        Set<Integer> excluded(){Set<Integer> out=new HashSet<>(declined);out.addAll(blockedUntil.keySet());return out;}
        /** Counts this tick's requests against the switch weapon (or the worn one). */
        void requested(List<Piece> pieces,Loadout l,int tick) {
            int weapon=-1;for(Piece p:pieces)if(p.slot==WEAPON)weapon=p.id;
            if(weapon<0&&l.worn.get(WEAPON)!=null)weapon=l.worn.get(WEAPON).id;
            for(Piece p:pieces) {
                if(Objects.equals(requestedAt.get(p.id),tick))continue;
                requestedAt.put(p.id,tick);
                int n=attempts.merge(p.id,1,Integer::sum);
                if(p.id!=weapon&&l.wearing(weapon)&&afterWeapon.merge(p.id,1,Integer::sum)>=ATTEMPTS)
                {declined.add(p.id);attempts.remove(p.id);afterWeapon.remove(p.id);continue;}
                if(n>=ATTEMPTS){blockedUntil.put(p.id,tick+BLOCK_TICKS);attempts.remove(p.id);}
            }
        }
        boolean recentlyClicked(int id,long now){Long at=clickedAt.get(id);return at!=null&&now-at<RECLICK_NANOS;}
        void clicked(int id,long now){clickedAt.put(id,now);}
    }

    /** Client thread: carried equipment with a Wield/Wear option and every worn slot, labelled for magic. */
    static Loadout loadout(Client client) {
        List<Piece> carried=new ArrayList<>();Map<Integer,Piece> worn=new TreeMap<>();int free=28;
        ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);
        if(inventory!=null) {
            Item[] items=inventory.getItems();
            for(int i=0;i<items.length;i++) {
                Item item=items[i];if(item==null||item.getId()<=0)continue;
                free--;
                ItemComposition def=client.getItemDefinition(item.getId());
                String action=null;
                for(String option:def.getInventoryActions())
                    if(option!=null&&(option.equals("Wield")||option.equals("Wear")||option.equals("Equip"))){action=option;break;}
                if(action==null)continue;
                ItemEquipmentStats stats=equipment(item.getId());
                int slot=slot(item.getId(),def.getName(),stats);if(slot<0||slot==13)continue;
                carried.add(piece(item.getId(),slot,i,def.getName(),stats,action));
            }
        }
        ItemContainer equipment=client.getItemContainer(InventoryID.EQUIPMENT);
        if(equipment!=null) {
            Item[] items=equipment.getItems();
            for(int i=0;i<items.length;i++) {
                Item item=items[i];if(item==null||item.getId()<=0)continue;
                worn.put(i,piece(item.getId(),i,-1,client.getItemDefinition(item.getId()).getName(),equipment(item.getId()),""));
            }
        }
        return new Loadout(carried,worn,Math.max(0,free));
    }
    private static Piece piece(int id,int slot,int inventorySlot,String name,ItemEquipmentStats stats,String action) {
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        boolean penalty=stats!=null?stats.getAmagic()<0:n.contains("chaps")||n.contains("d'hide")||n.contains("hide");
        boolean twoHanded=stats!=null?stats.isTwoHanded():TWO_HANDED.contains(id);
        return new Piece(id,slot,inventorySlot,score(id,name,stats),twoHanded,penalty,name,action);
    }
    private static ItemEquipmentStats equipment(int id) {
        ItemStats stats=Microbot.getItemManager().getItemStats(id);
        return stats==null||!stats.isEquipable()?null:stats.getEquipment();
    }
    /** Client thread: the Ancient Ice and Blood tiers the current level and runes allow. */
    static Spells spells(Client client) {
        if(client.getVarbitValue(VarbitID.SPELLBOOK)!=1)return Spells.NONE;
        return new Spells(tier(ICE),tier(BLOOD));
    }
    private static int tier(Rs2CombatSpells[] order) {
        for(int i=0;i<order.length;i++)if(Rs2Magic.canCast(order[i]))return 3-i;
        return 0;
    }
}
