package net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.inferno;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaMenuInput;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaMenuEntry;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

public class InfernoCommands
{
	private final Client client;
    private final java.util.function.BooleanSupplier allowed;

	public InfernoCommands(Client client, java.util.function.BooleanSupplier allowed)
	{
		this.client = client;
        this.allowed = allowed;
	}


	public boolean armIce()
	{
        if (!allowed.getAsBoolean()) return false;
        return ArenaInputScope.onClientThread(() -> {
            if(!allowed.getAsBoolean() || !Rs2Magic.isSpellbook(net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT)) return false;
            int level=client.getBoostedSkillLevel(net.runelite.api.Skill.MAGIC);
            MagicAction spell=level>=94 ? MagicAction.ICE_BARRAGE : level>=70 ? MagicAction.ICE_BURST : null;
            if(spell==null) return false;
            int id=spell.getWidgetId();var widget=id<0 ? null : client.getWidget(id);
            if(widget==null||widget.isHidden()||widget.getBounds()==null||widget.getBounds().isEmpty()) return false;
            if(client.isWidgetSelected() && client.getSelectedWidget()!=null && client.getSelectedWidget().getId()==id) return true;
            ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->allowed.getAsBoolean()
                &&Rs2Magic.isSpellbook(net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook.ANCIENT)
                &&client.getBoostedSkillLevel(net.runelite.api.Skill.MAGIC)>=(spell==MagicAction.ICE_BARRAGE?94:70));
            return ArenaMenuInput.tryInvoke(entry
                .param0(-1).param1(id).identifier(1).itemId(-1).type(net.runelite.api.MenuAction.WIDGET_TARGET)
                .option("Cast").target(spell.getName()).widget(widget),new java.awt.Rectangle(widget.getBounds()));
        }).orElse(false);
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
