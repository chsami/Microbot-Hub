package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.*;
import net.runelite.client.plugins.microbot.util.magic.*;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Local preparation only. One observed step per poll; never exposes a server command. */
final class ArenaCombatSetup {
    enum Result { WAITING, READY, STOPPED }
    interface Port {
        boolean allowed();
        int weapon(); int shield(); List<Integer> ranged(); int mage(); int magicLevel();
        boolean rapid(); boolean bloodSelected(Rs2CombatSpells spell);
        void equip(int id); void rapidStep(); void bloodStep(Rs2CombatSpells spell);
        boolean full(); boolean twoHanded(int id); int restore(); int count(int id);
        WorldPoint position(); boolean onGround(int id,WorldPoint tile);
        void drop(int id); void take(int id,WorldPoint tile);
    }
    private final Port port;
    private final LongSupplier clock;
    private final List<Integer> ranged = new ArrayList<>();
    private int originalWeapon, originalShield, mage, index, rapidAttempts;
    private long nextRapidAttempt;
    private Rs2CombatSpells blood;
    private long started = -1, nextAction;
    private boolean bloodDone,dropConfirmed;
    private int droppedRestore,restoreCount;
    private WorldPoint dropTile;
    private String failure;
    private long recoveryStarted;
    private Result result = Result.WAITING;
    private String status = "Checking Rapid and Blood autocast locally";
    ArenaCombatSetup(BooleanSupplier allowed) { this(new LocalPort(allowed), System::currentTimeMillis); }
    ArenaCombatSetup(Port port, LongSupplier clock) { this.port=port; this.clock=clock; }
    String status() { return status; }
    void resumeAfter(long millis) { if(millis>0){if(started>=0)started+=millis;nextAction+=millis;nextRapidAttempt+=millis;if(recoveryStarted>0)recoveryStarted+=millis;} }
    static Rs2CombatSpells bloodForLevel(int level) {
        return level>=92?Rs2CombatSpells.BLOOD_BARRAGE:level>=80?Rs2CombatSpells.BLOOD_BLITZ:
            level>=68?Rs2CombatSpells.BLOOD_BURST:level>=56?Rs2CombatSpells.BLOOD_RUSH:null;
    }
    Result tick() {
        if(result!=Result.WAITING || !port.allowed()) return result;
        long now=clock.getAsLong();
        if(started<0) {
            started=now; originalWeapon=port.weapon(); originalShield=port.shield();
            ranged.addAll(port.ranged()); mage=port.mage(); blood=bloodForLevel(port.magicLevel());
            if(originalWeapon<=0 || mage<=0 || blood==null) {
                status="Cannot prepare Blood autocast: select a mage weapon and at least 56 Magic";
                return result=Result.STOPPED;
            }
        }
        if(failure==null&&now-started>90_000)
            return fail("Combat setup timed out: check equipment space, Ancient Magicks, Blood runes and staff autocast support",now);
        if(failure!=null&&now-recoveryStarted>30_000) {
            status=failure+"; could not finish equipment/restore recovery at the setup tile";
            return result=Result.STOPPED;
        }
        if(now<nextAction) return result;
        if(droppedRestore==0&&failure==null&&originalShield>0&&port.shield()>0&&port.full()
            &&(ranged.stream().anyMatch(port::twoHanded)||port.twoHanded(mage))) {
            int restore=port.restore();WorldPoint tile=port.position();
            if(restore>0&&tile!=null&&port.count(restore)>0) {
                // Record before issuing the drop. Never drop another item without a receipt.
                droppedRestore=restore;restoreCount=port.count(restore);dropTile=tile;
                status="Temporarily dropping one Super restore for combat setup space";
                act(()->port.drop(restore),now);return result;
            }
        }
        if(droppedRestore>0&&!dropConfirmed&&failure==null) {
            status="Waiting for the dropped Super restore and free inventory slot";
            int count=port.count(droppedRestore);
            if(count<0||count>=restoreCount||port.full()||!port.onGround(droppedRestore,dropTile))return result;
            dropConfirmed=true;
        }
        if(failure!=null)return restoreSetup(now);
        if(index<ranged.size()) {
            int id=ranged.get(index); status="Verifying Rapid on ranged weapon " + id;
            if(port.weapon()!=id) act(() -> port.equip(id),now);
            else if(port.rapid()) {index++;rapidAttempts=0;nextRapidAttempt=0;}
            else if(now>=nextRapidAttempt) {
                if(rapidAttempts>=3) {
                    return fail("Rapid not confirmed on weapon "+id+" after bounded retries; inspect attack mode",now);
                }
                rapidAttempts++;nextRapidAttempt=now+6_000L;act(port::rapidStep,now);
            }
        } else if(!bloodDone) {
            status="Verifying non-defensive " + blood.name().replace('_',' ') + " autocast";
            if(port.weapon()!=mage) act(() -> port.equip(mage),now);
            else if(!port.bloodSelected(blood)) act(() -> port.bloodStep(blood),now);
            else bloodDone=true;
        } else return restoreSetup(now);
        return result;
    }
    private Result fail(String reason,long now) {
        status=reason;
        if(droppedRestore==0)return result=Result.STOPPED;
        failure=reason;recoveryStarted=now;
        return result;
    }
    private Result restoreSetup(long now) {
        if(port.weapon()!=originalWeapon) {
            status="Restoring setup weapon";act(()->port.equip(originalWeapon),now);
        } else if(originalShield>0&&port.shield()!=originalShield) {
            status="Restoring setup shield";act(()->port.equip(originalShield),now);
        } else if(droppedRestore>0&&port.count(droppedRestore)<restoreCount) {
            status="Recovering the dropped Super restore before leaving setup";
            if(!port.full()&&port.onGround(droppedRestore,dropTile))act(()->port.take(droppedRestore,dropTile),now);
        } else if(failure!=null) {
            status=failure;result=Result.STOPPED;
        } else {
            status="Rapid and Blood autocast verified; setup weapon, shield and inventory restored";result=Result.READY;
        }
        return result;
    }
    private void act(Runnable action,long now) { if(port.allowed()) { action.run(); nextAction=now+1_200; } }
    // For the supported bows, blowpipe and crossbows, mode 1 is the Rapid button.
    // ATTACK_STYLE_NAME is the XP category (Ranging), not the selected button name.
    static boolean rapidSelected(Client client) {return client!=null&&client.getVarpValue(VarPlayerID.COM_MODE)==1;}
    private static final class LocalPort implements Port {
        private final BooleanSupplier allowed;
        LocalPort(BooleanSupplier allowed) {this.allowed=allowed;}
        public boolean allowed() {return allowed.getAsBoolean();}
        private <T> T read(Supplier<T> value,T fallback) {
            return Microbot.getClientThread().runOnClientThreadOptional(value::get).orElse(fallback);
        }
        private int equipped(EquipmentInventorySlot slot) {
            return read(() -> {Rs2ItemModel item=Rs2Equipment.get(slot);return item==null?-1:item.getId();},-1);
        }
        public int weapon() {return equipped(EquipmentInventorySlot.WEAPON);}
        public int shield() {return equipped(EquipmentInventorySlot.SHIELD);}
        private Stream<Rs2ItemModel> carried() {return Stream.concat(Rs2Inventory.items(),Rs2Equipment.all());}
        public List<Integer> ranged() {return read(() -> carried().filter(i -> {
            String n=i.getName().toLowerCase(Locale.ROOT);
            return n.equals("toxic blowpipe") || n.startsWith("bow of faerdhinen") ||
                n.equals("twisted bow") || n.contains("crossbow");
        }).map(Rs2ItemModel::getId).distinct().collect(Collectors.toList()),Collections.emptyList());}
        public int mage() {return read(() -> carried().filter(i -> {
            String n=i.getName().toLowerCase(Locale.ROOT);
            return n.contains("wand") || n.contains("staff") || n.contains("sceptre");
        }).mapToInt(Rs2ItemModel::getId).findFirst().orElse(-1),-1);}
        public int magicLevel() {return read(() -> Microbot.getClient().getRealSkillLevel(Skill.MAGIC),0);}
        public boolean rapid() {return read(() -> rapidSelected(Microbot.getClient()),false);}
        public boolean bloodSelected(Rs2CombatSpells spell) {return read(() ->
            Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT) && Rs2Magic.getCurrentAutoCastSpell()==spell &&
            Microbot.getVarbitValue(Varbits.DEFENSIVE_CASTING_MODE)==0,false);}
        public void equip(int id) {if(allowed()) Rs2Inventory.wield(id);}
        public boolean full() {return read(Rs2Inventory::isFull,true);}
        public boolean twoHanded(int id) {return read(()->{
            var stats=Microbot.getItemManager().getItemStats(id);
            return stats!=null&&stats.getEquipment()!=null&&stats.getEquipment().isTwoHanded();
        },false);}
        public int restore() {return read(()->Rs2Inventory.items()
            .filter(i->i.getId()==3024||i.getId()==3026||i.getId()==3028||i.getId()==3030)
            .mapToInt(Rs2ItemModel::getId).findFirst().orElse(-1),-1);}
        public int count(int id) {return read(()->Rs2Inventory.count(id),-1);}
        public WorldPoint position() {return read(()->Microbot.getClient().getLocalPlayer()==null?null:
            Microbot.getClient().getLocalPlayer().getWorldLocation(),null);}
        public boolean onGround(int id,WorldPoint tile) {return read(()->Microbot.getRs2TileItemCache().query()
            .withId(id).where(i->tile.equals(i.getWorldLocation())).firstOnClientThread()!=null,false);}
        public void drop(int id) {if(allowed())Rs2Inventory.drop(id);}
        public void take(int id,WorldPoint tile) {
            var item=read(()->Microbot.getRs2TileItemCache().query().withId(id)
                .where(i->tile.equals(i.getWorldLocation())).firstOnClientThread(),null);
            if(item!=null&&allowed())item.click("Take");
        }
        private boolean combatTab() {
            if(read(() -> Rs2Tab.getCurrentTab()==InterfaceTab.COMBAT,false)) return true;
            if(allowed()) Rs2Tab.switchToCombatOptionsTab(); return false;
        }
        public void rapidStep() {
            if(!allowed() || rapid() || !combatTab()) return;
            Widget widget=read(() -> Microbot.getClient().getWidget(WidgetInfo.COMBAT_STYLE_TWO),null);
            if(widget!=null && allowed()) Rs2Widget.clickWidget(widget);
        }
        public void bloodStep(Rs2CombatSpells spell) {
            if(!allowed() || !combatTab()) return;
            Widget target=read(() -> {
                if(!Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT) || !Rs2Magic.canCast(spell.getMagicAction())) return null;
                Widget options=Rs2Widget.getWidget(201,1);
                if(options!=null && !options.isHidden())
                    return Rs2Widget.findWidget(spell.getMagicAction().getSprite(),List.of(options));
                return Microbot.getClient().getWidget(WidgetInfo.COMBAT_SPELL_BOX);
            },null);
            if(target!=null && allowed()) Rs2Widget.clickWidget(target);
        }
    }
}
