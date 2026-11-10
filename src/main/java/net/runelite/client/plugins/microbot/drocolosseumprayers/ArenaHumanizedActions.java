package net.runelite.client.plugins.microbot.drocolosseumprayers;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.drocolosseumprayers.BaseProfileDro;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
/** Uses the real DRO profile for coordinator actions, without independent breaks, cameras or login ticks. */
public final class ArenaHumanizedActions {
 private final BaseProfileDro profile=new BaseProfileDro(new BaseProfileDro.Settings().nativeAntibanEnabled(false)
  .activityIntensity(ActivityIntensity.LOW).customBreaksEnabled(false).actionDelayScale(0.35).startupCameraEnabled(false).actionCameraNudges(false).overlayEnabled(false).writeBreakStatusToMicrobot(false));
 private long nextActionAt;
 public boolean perform(BooleanSupplier action,Runnable permission){
  if(net.runelite.client.plugins.microbot.Microbot.getClientThread()!=null&&net.runelite.client.plugins.microbot.Microbot.getClientThread().isClientThread())throw new IllegalStateException("Humanized input must run on the script worker");
  Global.sleepUntil(()->System.nanoTime()/1_000_000>=nextActionAt,1500);
  profile.beforeAction(BaseProfileDro.ActionPhase.SETUP,false);permission.run();
  // Reapply after the input gate: a child profile may have changed these between preparation steps.
  Rs2AntibanSettings.naturalMouse=true;
  Rs2AntibanSettings.simulateMistakes=true;
  Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
  try{return action.getAsBoolean();}finally{nextActionAt=System.nanoTime()/1_000_000+600;profile.afterAction(BaseProfileDro.ActionPhase.SETUP,false,false);}
 }
 public void close(){profile.shutdown();}
}
