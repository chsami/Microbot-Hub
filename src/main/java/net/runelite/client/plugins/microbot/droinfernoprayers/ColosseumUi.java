package net.runelite.client.plugins.microbot.droinfernoprayers;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.Dialogue;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.WidgetInfo;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.util.Text;

public final class ColosseumUi {
	private static final int[] CONTINUE = {InterfaceID.ChatLeft.CONTINUE, InterfaceID.ChatRight.CONTINUE, InterfaceID.Messagebox.CONTINUE};

	private static final int OBJECT_BOX = InterfaceID.Objectbox.UNIVERSE;

	private static final int OPTIONS = InterfaceID.Chatmenu.OPTIONS;
	private static final int[] TEXTS = {InterfaceID.ChatLeft.TEXT, InterfaceID.ChatRight.TEXT, InterfaceID.Messagebox.TEXT, InterfaceID.Objectbox.TEXT};

	public Dialogue dialogue()
	{
        if(!live())return null;
        beginSnapshot();
		boolean canContinue = visible(OBJECT_BOX);
		for (int id : CONTINUE)
		{
			canContinue |= visible(id);
		}
		List<String> options = new ArrayList<>();
		String title = null;
		Widget menu = visible(OPTIONS) ? client.getWidget(OPTIONS) : null;
		Widget[] lines = menu == null ? null : menu.getDynamicChildren();
		for (int i = 0; lines != null && i < lines.length; i++)
		{
			String text = lines[i] == null || lines[i].getText() == null ? "" : text(lines[i].getText()).trim();
			if (i == 0)
			{
				title = text.isEmpty() ? null : text;
			}
			else if (!text.isEmpty())
			{
				options.add(text);
			}
		}
		if (!canContinue && options.isEmpty())
		{
			return null;
		}
		Dialogue d = new Dialogue();
		d.canContinue = canContinue;
		String name = textOf(InterfaceID.ChatLeft.NAME);
		d.name = name != null ? name : textOf(InterfaceID.ChatRight.NAME);
		for (int id : TEXTS)
		{
			if (d.text == null)
			{
				d.text = textOf(id);
			}
		}
		if (d.text == null)
		{
			d.text = title;
		}
		d.options = options.isEmpty() ? null : options.toArray(new String[0]);
		return d;
	}

	private boolean visible(int id)
	{
		Widget w = client.getWidget(id);
		return w != null && !w.isHidden();
	}

	private String textOf(int id)
	{
		Widget w = client.getWidget(id);
		if (w == null || w.isHidden() || w.getText() == null)
		{
			return null;
		}
		String text = text(w.getText()).trim();
		return text.isEmpty() ? null : text;
	}

    private final Client client;
    private final BooleanSupplier allowed;
    private final BooleanSupplier beforeWave;
    private int textBudget=8192;
    public void beginSnapshot(){textBudget=8192;}
    public ColosseumUi(Client client,BooleanSupplier allowed){this(client,allowed,()->true);}
    public ColosseumUi(Client client,BooleanSupplier allowed,BooleanSupplier beforeWave){this.client=client;this.allowed=allowed;this.beforeWave=beforeWave;}
    public static boolean readable(int id){return id>=0;}
    public static boolean clickable(int id,Integer child){return readable(id)&&(child==null||child>=0&&child<=65535);}
    public static boolean approvedOption(String option){
        if(option==null||option.isBlank())return false;
        String s=option.toLowerCase(java.util.Locale.ROOT);
        return !s.contains("url")&&!s.contains("website")&&!s.contains("support")&&!s.contains("http")&&!s.contains("wiki");
    }
    private boolean live(){return allowed.getAsBoolean()&&ArenaDropPolicy.insideArena(client);}
    public boolean click(int id,Integer child,String requested){
        if(!clickable(id,child)||requested!=null&&!approvedOption(requested))throw new IllegalArgumentException("Unapproved arena control");
        if(!allowed.getAsBoolean())return false;
        return Microbot.getClientThread().runOnClientThreadOptional(()->{
            if(!live()||client.isWidgetSelected())return false;
            var root=client.getWidget(id);var target=root==null?null:child==null?root:root.getChild(child);
            if(root==null||root.isHidden()||target==null||target.isHidden()||!readable(target.getId()))return false;
            var bounds=target.getBounds();if(bounds==null||bounds.width<=0||bounds.height<=0)return false;
            String[] actions=target.getActions();
            for(int i=0;actions!=null&&i<Math.min(actions.length,10);i++){
                String action=actions[i]==null?null:Text.removeTags(actions[i]);
                if(approvedOption(action)&&(requested==null||requested.equalsIgnoreCase(action))){
                    if((id>>>16)==865&&!Set.of("cancel","close").contains(action.toLowerCase(java.util.Locale.ROOT))&&!beforeWave.getAsBoolean())return false;
                    Microbot.doInvoke(new NewMenuEntry().param0(child==null?-1:child).param1(id).identifier(i+1).itemId(-1)
                        .type(MenuAction.CC_OP).option(action).worldViewId(client.getTopLevelWorldView().getId()),bounds);return true;
                }
            }return false;
        }).orElse(false);
    }

    public WidgetInfo widget(int id){
        if(!readable(id))throw new IllegalArgumentException("Unapproved arena panel");
        if(!live())return null;var w=client.getWidget(id);if(w==null||w.isHidden())return null;
        WidgetInfo info=new WidgetInfo();info.id=id;info.name=text(w.getName());info.text=text(w.getText());
        String[] actions=w.getActions();if(actions!=null){info.actions=new String[Math.min(10,actions.length)];for(int i=0;i<info.actions.length;i++)info.actions[i]=text(actions[i]);}
        var children=w.getDynamicChildren();if(children!=null){info.kids=new String[Math.min(128,children.length)];for(int i=0;i<info.kids.length;i++)info.kids[i]=children[i]==null||!readable(children[i].getId())?"":text(children[i].getText());}
        return info;
    }
    private String text(String value){
        if(value==null)return null;if(textBudget<=0)return "";String text=Text.removeTags(value);String name=client.getLocalPlayer().getName();
        if(name!=null&&!name.isEmpty())text=text.replace(name,"[player]");int length=Math.min(textBudget,Math.min(512,text.length()));textBudget-=length;return text.substring(0,length);
    }
}
