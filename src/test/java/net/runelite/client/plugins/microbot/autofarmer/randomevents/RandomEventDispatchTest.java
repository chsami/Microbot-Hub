package net.runelite.client.plugins.microbot.autofarmer.randomevents;
import org.junit.jupiter.api.Test;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.BlockingEventManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import static org.mockito.Mockito.*;
class RandomEventDispatchTest {
 private static void set(Object object,String name,Object value) throws Exception {
  Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);
 }
 @Test void claimedEventDispatchesWithoutWaitingForFarmingLoop() throws Exception {
  RandomEventSupport support=new RandomEventSupport();BlockingEventManager manager=mock(BlockingEventManager.class);
  set(support,"enabled",true);set(support,"owner",mock(NPC.class));set(support,"manager",manager);
  boolean paused=Microbot.pauseAllScripts.getAndSet(false);
  try(MockedStatic<ScriptPauseController> pause=mockStatic(ScriptPauseController.class)) {
   support.dispatchClaimedEvent();verify(manager).shouldBlockAndProcess();
  } finally {Microbot.pauseAllScripts.set(paused);}
 }
 @Test void pauseAndExternalHelperAndNoOwnerDoNotDispatch() throws Exception {
  RandomEventSupport support=new RandomEventSupport();BlockingEventManager manager=mock(BlockingEventManager.class);
  set(support,"enabled",true);set(support,"manager",manager);
  boolean paused=Microbot.pauseAllScripts.getAndSet(false);
  try(MockedStatic<ScriptPauseController> pause=mockStatic(ScriptPauseController.class)) {
   support.dispatchClaimedEvent();
   set(support,"owner",mock(NPC.class));set(support,"externalHelperActive",true);support.dispatchClaimedEvent();
   set(support,"externalHelperActive",false);Microbot.pauseAllScripts.set(true);support.dispatchClaimedEvent();
   Microbot.pauseAllScripts.set(false);pause.when(ScriptPauseController::isPaused).thenReturn(true);support.dispatchClaimedEvent();
   verifyNoInteractions(manager);
  } finally {Microbot.pauseAllScripts.set(paused);}
 }
}
