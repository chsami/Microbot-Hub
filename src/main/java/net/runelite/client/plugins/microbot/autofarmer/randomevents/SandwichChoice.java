package net.runelite.client.plugins.microbot.autofarmer.randomevents;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
/** Read on client thread. Uses the tray models, not an inventory render assumption. */
final class SandwichChoice {
 static final class Result {Widget widget;String diagnostic;int food;}
 static Result find(Client client,String speech){
  Result result=new Result();Widget label=client.getWidget(InterfaceID.SandwichTray.SANDWICH_TRAY_LABEL);
  String text=visible(label)?label.getText():"";int food=RandomEventPolicy.requestedFood(text);
  if(food<0)food=RandomEventPolicy.requestedFood(speech);result.food=food;
  List<Widget> choices=new ArrayList<>();Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());
  collect(client.getWidget(InterfaceID.SandwichTray.SANDWHICH_REFRESHMENT_LAYER),choices,seen,0);
  for(int i=6;i<=12;i++)collect(client.getWidget(InterfaceID.SANDWICH_TRAY,i),choices,seen,0);
  StringBuilder models=new StringBuilder();int wantedModel=RandomEventPolicy.trayModel(food);int matches=0;
  for(Widget choice:choices){
   if(!visible(choice) || choice.getBounds()==null || choice.getBounds().width<=0 || choice.getBounds().height<=0)continue;
   models.append(choice.getId()).append(':').append(choice.getModelId()).append('/').append(choice.getItemId()).append(',');
   if(food>0 && (choice.getItemId()==food || wantedModel>0 && choice.getModelId()==wantedModel)){result.widget=choice;matches++;}
  }
  if(matches!=1)result.widget=null;
  result.diagnostic="label="+text+", speech="+speech+", requested="+food+", matches="+matches+", models="+models;
  return result;
 }
 private static void collect(Widget w,List<Widget> choices,Set<Widget> seen,int depth){
  if(w==null || !seen.add(w) || depth>3)return;choices.add(w);
  Widget[][] groups={w.getStaticChildren(),w.getDynamicChildren(),w.getNestedChildren()};
  for(Widget[] group:groups)if(group!=null)for(Widget child:group)collect(child,choices,seen,depth+1);
 }
 private static boolean visible(Widget w){return w!=null && !w.isHidden();}
}
