package net.runelite.client.plugins.microbot.drocolosseumprayers.plugins.common;

import java.util.function.BooleanSupplier;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaProduct;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaInputScope;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaMenuEntry;
import net.runelite.client.plugins.microbot.drocolosseumprayers.ArenaMenuInput;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.util.Text;

final class ArenaGameControls {
    private final Client client;
    private final BooleanSupplier allowed;
    private final ArenaProduct product;
    private static final Set<Integer> CASH_OUT_COMPONENTS=Set.of(
        InterfaceID.ColosseumIntermission.BUTTON_CLAIM,InterfaceID.ColosseumIntermission.CONFIRM_BUTTON,
        InterfaceID.ColosseumIntermission2.LEFT_BUTTON,InterfaceID.ColosseumIntermission2.CONFIRM_CONFIRM);
    ArenaGameControls(Client client,BooleanSupplier allowed,ArenaProduct product){
        this.client=client;this.allowed=allowed;this.product=product;
    }
    private boolean blocksCashOut(int id){
        return product==ArenaProduct.COLOSSEUM&&CASH_OUT_COMPONENTS.contains(id);
    }
    static boolean safeEntityOption(String option){
        if(option==null||option.isBlank())return false;
        String s=option.toLowerCase(java.util.Locale.ROOT);
        return !s.contains("url")&&!s.contains("website")&&!s.contains("support")&&!s.contains("http")&&!s.contains("wiki");
    }

    static boolean permitsOption(ArenaProduct product,String option){
        return safeEntityOption(option)&&(product!=ArenaProduct.COLOSSEUM
            ||java.util.Arrays.stream(Text.removeTags(option).toLowerCase(java.util.Locale.ROOT).split("[^a-z]+"))
                .noneMatch(word->word.equals("leave")||word.equals("exit")));
    }

    static boolean leaveConfirmationVisible(Client client){
        var options=client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        if(options==null||options.isHidden())return false;
        var lines=options.getDynamicChildren();
        if(lines==null||lines.length==0||lines[0]==null||lines[0].getText()==null)return false;
        return "Are you sure you wish to leave?".equalsIgnoreCase(Text.removeTags(lines[0].getText()).trim());
    }

    static boolean modifierScreenVisible(Client client){
        for(int id:new int[]{InterfaceID.ColosseumIntermission2.MOD_BUTTON_1,InterfaceID.ColosseumIntermission2.RIGHT_BUTTON}){
            var widget=client.getWidget(id);
            if(widget!=null&&!widget.isHidden())return true;
        }
        return false;
    }
    static boolean forfeitConfirmationVisible(Client client){
        var legacy=client.getWidget(InterfaceID.ColosseumIntermission.CONFIRM_BUTTON);
        if(legacy!=null&&!legacy.isHidden())return true;
        var current=client.getWidget(InterfaceID.ColosseumIntermission2.CONFIRM_CONFIRM);
        return current!=null&&!current.isHidden();
    }
    static boolean gameWidget(int id){
        return id>=0;
    }
    boolean npc(int index,String option){
        if(!allowed.getAsBoolean()||!permitsOption(product,option))return false;
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean()||client.isWidgetSelected())return false;
            if(product==ArenaProduct.COLOSSEUM&&"Start-wave".equalsIgnoreCase(option)&&modifierScreenVisible(client))return false;
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
        if(!allowed.getAsBoolean()||!permitsOption(product,option))return false;
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
        if(blocksCashOut(id)||requested!=null&&!permitsOption(product,requested))return false;
        return ArenaInputScope.onClientThread(()->{
            if(!allowed.getAsBoolean()||client.isWidgetSelected())return false;
            var root=client.getWidget(id);var target=root==null?null:child==null?root:root.getChild(child);
            if(root==null||root.isHidden()||target==null||target.isHidden()||!gameWidget(target.getId()))return false;
            if(product==ArenaProduct.COLOSSEUM&&leaveConfirmationVisible(client))return false;
            if(blocksCashOut(id)||blocksCashOut(target.getId())||blocksCashOut(target.getParentId()))return false;
            var bounds=target.getBounds();if(bounds==null||bounds.width<=0||bounds.height<=0)return false;
            String[] actions=target.getActions();
            for(int i=0;actions!=null&&i<Math.min(actions.length,10);i++){
                String action=actions[i]==null?null:Text.removeTags(actions[i]);
                if(permitsOption(product,action)&&(requested==null||requested.equalsIgnoreCase(action))){
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
