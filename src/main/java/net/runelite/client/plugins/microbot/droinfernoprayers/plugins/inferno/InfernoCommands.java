package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputScope;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaMenuInput;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaMenuEntry;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

/** Inferno extras. As with Commands, true means satisfied or submitted, not observed completion. */
public class InfernoCommands
{
	private final Client client;
    private final java.util.function.BooleanSupplier allowed;

	public InfernoCommands(Client client, java.util.function.BooleanSupplier allowed)
	{
		this.client = client;
        this.allowed = allowed;
	}

	/** The nearest item named `name` (or "name(...)", e.g. a dose) within `radius` tiles. */
	public boolean takeGroundItem(String name, int radius)
	{
        if (!allowed.getAsBoolean()) return false;
		if (name==null || name.length()>80 || radius<0 || radius>15) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return ArenaInputScope.onClientThread(() -> {
            if(!allowed.getAsBoolean()||client.isWidgetSelected()) return false;
            Rs2TileItemModel item = Microbot.getRs2TileItemCache().query().fromWorldView()
                .where(Rs2TileItemModel::isLootAble)
                .where(i -> i.getName()!=null && (i.getName().equalsIgnoreCase(name)
                    || i.getName().toLowerCase(java.util.Locale.ROOT).startsWith(lower+"(")))
                .nearest(radius);
            if(item==null || item.getLocalLocation()==null) return false;
            var local=item.getLocalLocation();
            var polygon=net.runelite.api.Perspective.getCanvasTilePoly(client,local);
            if(polygon==null||polygon.getBounds().isEmpty())return false;
            ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->allowed.getAsBoolean()&&!client.isWidgetSelected()
                &&Microbot.getRs2TileItemCache().query().fromWorldView().where(i->i.getId()==item.getId()
                    &&i.isLootAble()&&i.getWorldLocation().equals(item.getWorldLocation())).firstOnClientThread()!=null);
            return ArenaMenuInput.tryInvoke(entry
                .param0(local.getSceneX()).param1(local.getSceneY()).identifier(item.getId()).itemId(-1)
                .type(net.runelite.api.MenuAction.GROUND_ITEM_THIRD_OPTION).option("Take")
                .worldViewId(client.getTopLevelWorldView().getId()),
                polygon.getBounds());
        }).orElse(false);
    }

	/** Select the highest Ice spell the boosted level and carried runes can cast, unless it is already selected. */
	public boolean armIce()
	{
        return armIce(-1);
    }

    /**
     * As {@link #armIce()}, then cast it on the NPC at {@code index} (>= 0) in the same pointer gesture, so no
     * prayer click can clear the selection in between. With the spell already selected only the cast is sent.
     */
	public boolean armIce(int index)
	{
        if (!allowed.getAsBoolean()) return false;
        return ArenaInputScope.onClientThread(() -> {
            if(!allowed.getAsBoolean() || !Rs2Magic.isSpellbook(net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT)) return false;
            int level=client.getBoostedSkillLevel(net.runelite.api.Skill.MAGIC);
            MagicAction spell=castableIce(level);
            if(spell==null) return false;
            int id=spell.getWidgetId();var widget=id<0 ? null : client.getWidget(id);
            if(widget==null||widget.isHidden()||widget.getBounds()==null||widget.getBounds().isEmpty()) return false;
            java.util.function.BooleanSupplier selected=()->client.isWidgetSelected()&&client.getSelectedWidget()!=null
                &&client.getSelectedWidget().getId()==id;
            if(selected.getAsBoolean()) {
                if(index<0) return true;
                var cast=ArenaMenuInput.attackEntry(client,index,allowed);
                return cast!=null&&ArenaMenuInput.tryInvoke(cast,cast.getActor().getConvexHull().getBounds());
            }
            ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->allowed.getAsBoolean()
                &&Rs2Magic.isSpellbook(net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT)
                &&client.getBoostedSkillLevel(net.runelite.api.Skill.MAGIC)>=spell.getLevel());
            entry.param0(-1).param1(id).identifier(1).itemId(-1).type(net.runelite.api.MenuAction.WIDGET_TARGET)
                .option("Cast").target(spell.getName()).widget(widget);
            var bounds=new java.awt.Rectangle(widget.getBounds());
            if(index<0) return ArenaMenuInput.tryInvoke(entry,bounds);
            return ArenaMenuInput.tryInvokeChain(entry,bounds,selected,()->ArenaMenuInput.attackEntry(client,index,allowed));
        }).orElse(false);
    }

    private static final net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells[] ICE_SPELLS={
        net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells.ICE_BARRAGE,
        net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells.ICE_BLITZ,
        net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells.ICE_BURST};

    /** Burst needs chaos runes and Blitz/Barrage need bloods: never select a tier the carried runes can't cast. */
    static MagicAction castableIce(int level){
        for(var spell:ICE_SPELLS)
            if(level>=spell.getMagicAction().getLevel()&&Rs2Magic.hasRequiredRunes(spell))return spell.getMagicAction();
        return null;
    }

    /** ICE_* tier bits of InfernoSnapshot the carried runes support, regardless of the current level. Client thread. */
    public static int iceRuneTiers(){
        int tiers=0;
        if(Rs2Magic.hasRequiredRunes(ICE_SPELLS[2]))tiers|=1;
        if(Rs2Magic.hasRequiredRunes(ICE_SPELLS[1]))tiers|=2;
        if(Rs2Magic.hasRequiredRunes(ICE_SPELLS[0]))tiers|=4;
        return tiers;
    }

	public boolean logout()
	{
        if (!allowed.getAsBoolean()) return false;
        net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.resetEnter();
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean())return false;
            var switcher=client.getWidget(69,3);
            int id=switcher!=null&&!switcher.isHidden()?4522009:11927560;
            var widget=client.getWidget(id);
            if(widget==null||widget.isHidden())return false;
            return ArenaMenuInput.tryInvoke(new net.runelite.client.plugins.microbot.util.menu.NewMenuEntry()
                .param0(-1).param1(id).identifier(1).itemId(-1).type(net.runelite.api.MenuAction.CC_OP)
                .option("Logout").widget(widget),widget.getBounds());
        }).orElse(false);
	}
}
