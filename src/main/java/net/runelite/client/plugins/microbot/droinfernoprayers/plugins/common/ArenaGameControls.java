package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.common;

import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaInputScope;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaMenuEntry;
import net.runelite.client.plugins.microbot.droinfernoprayers.ArenaMenuInput;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.util.Text;

final class ArenaGameControls {
    private final Client client;
    private final BooleanSupplier allowed;
    ArenaGameControls(Client client,BooleanSupplier allowed){this.client=client;this.allowed=allowed;}
    static boolean safeEntityOption(String option){
        if(option==null||option.isBlank())return false;
        String s=option.toLowerCase(java.util.Locale.ROOT);
        return !s.contains("url")&&!s.contains("website")&&!s.contains("support")&&!s.contains("http")&&!s.contains("wiki");
    }
    static boolean gameWidget(int id){
        return id>=0;
    }
    boolean npc(int index,String option){
        if(!allowed.getAsBoolean()||!safeEntityOption(option))return false;
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean()||client.isWidgetSelected())return false;
            var npc=ArenaMenuInput.npc(client,index);
            if(npc==null)return false;
            var composition=npc.getTransformedComposition();
            int action=composition==null?-1:ArenaMenuInput.action(composition.getActions(),option);
            if(action<1||action>5||npc.getConvexHull()==null)return false;
            MenuAction[] actions={MenuAction.NPC_FIRST_OPTION,MenuAction.NPC_SECOND_OPTION,MenuAction.NPC_THIRD_OPTION,
                MenuAction.NPC_FOURTH_OPTION,MenuAction.NPC_FIFTH_OPTION};
            ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->allowed.getAsBoolean()&&!client.isWidgetSelected());
            entry.param0(0).param1(0).identifier(index).itemId(-1).type(actions[action-1]).option(option).actor(npc)
                .worldViewId(client.getTopLevelWorldView().getId());
            return ArenaMenuInput.tryInvoke(entry,npc.getConvexHull().getBounds());
        }).orElse(false);
    }
    boolean object(int[] ids,String name,String option,int radius){
        if(!allowed.getAsBoolean()||!safeEntityOption(option))return false;
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean()||client.isWidgetSelected())return false;
            var q=Microbot.getRs2TileObjectCache().query();
            if(ids!=null)q=q.withIds(ids);else if(name!=null)q=q.withName(name);else return false;
            var model=q.where(o->o.getWorldView()==client.getTopLevelWorldView()).nearestOnClientThread(radius);
            if(model==null||model.getClickbox()==null)return false;
            var definition=model.getObjectComposition();
            int action=definition==null?-1:ArenaMenuInput.action(definition.getActions(),option);
            var local=net.runelite.api.coords.LocalPoint.fromWorld(client.getTopLevelWorldView(),model.getWorldLocation());
            if(action<1||action>5||local==null)return false;
            MenuAction[] actions={MenuAction.GAME_OBJECT_FIRST_OPTION,MenuAction.GAME_OBJECT_SECOND_OPTION,MenuAction.GAME_OBJECT_THIRD_OPTION,
                MenuAction.GAME_OBJECT_FOURTH_OPTION,MenuAction.GAME_OBJECT_FIFTH_OPTION};
            ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->allowed.getAsBoolean()&&!client.isWidgetSelected());
            entry.param0(local.getSceneX()).param1(local.getSceneY()).identifier(model.getId()).itemId(-1)
                .type(actions[action-1]).option(option).gameObject(model).worldViewId(client.getTopLevelWorldView().getId());
            return ArenaMenuInput.tryInvoke(entry,model.getClickbox().getBounds());
        }).orElse(false);
    }
    boolean widget(int id,Integer child,String requested){
        if(!allowed.getAsBoolean()||!gameWidget(id)||child!=null&&(child<0||child>65535))return false;
        if(requested!=null&&!safeEntityOption(requested))return false;
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean()||client.isWidgetSelected())return false;
            var root=client.getWidget(id);var target=root==null?null:child==null?root:root.getChild(child);
            if(root==null||root.isHidden()||target==null||target.isHidden()||!gameWidget(target.getId()))return false;
            var bounds=target.getBounds();if(bounds==null||bounds.width<=0||bounds.height<=0)return false;
            String[] actions=target.getActions();
            for(int i=0;actions!=null&&i<Math.min(actions.length,10);i++){
                String action=actions[i]==null?null:Text.removeTags(actions[i]);
                if(safeEntityOption(action)&&(requested==null||requested.equalsIgnoreCase(action))){
                    ArenaMenuEntry entry=new ArenaMenuEntry().liveCheck(()->allowed.getAsBoolean()&&!client.isWidgetSelected()
                        &&!root.isHidden()&&!target.isHidden());
                    return ArenaMenuInput.tryInvoke(entry.param0(child==null?-1:child).param1(id).identifier(i+1).itemId(-1)
                        .type(MenuAction.CC_OP).option(action).widget(target).worldViewId(client.getTopLevelWorldView().getId()),bounds);
                }
            }return false;
        }).orElse(false);
    }
    private static boolean has(String[] actions,String option){
        if(actions!=null)for(String a:actions)if(option.equalsIgnoreCase(a))return true;
        return false;
    }
}
