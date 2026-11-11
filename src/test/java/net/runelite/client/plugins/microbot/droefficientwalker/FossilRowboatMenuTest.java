package net.runelite.client.plugins.microbot.droefficientwalker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FossilRowboatMenuTest {
 @Test void usesDestinationRatherThanAssumingTheNumberedKey(){
  assertEquals("To Sea",FossilRowboatMenu.destination("4. To Sea"));
  assertEquals("North of Island",FossilRowboatMenu.destination("2. North of Island"));
 }
 @Test void destinationMustRemainVisibleBeforeSelecting(){
  FossilRowboatMenu menu=new FossilRowboatMenu(350);
  assertFalse(menu.ready(false,0));assertFalse(menu.ready(true,100));
  assertFalse(menu.ready(true,449));assertTrue(menu.ready(true,450));
 }
 @Test void interruptedMenuMustSettleAgain(){
  FossilRowboatMenu menu=new FossilRowboatMenu(350);
  menu.ready(true,100);assertFalse(menu.ready(false,400));
  assertFalse(menu.ready(true,500));assertFalse(menu.ready(true,700));assertTrue(menu.ready(true,850));
 }
 @Test void eachNewMenuSamplesAgainButPollingDoesNotChangeItsDeadline(){
  java.util.concurrent.atomic.AtomicInteger draws=new java.util.concurrent.atomic.AtomicInteger();
  FossilRowboatMenu menu=new FossilRowboatMenu(()->draws.getAndIncrement()==0?300L:500L);
  assertFalse(menu.ready(false,0));assertEquals(0,draws.get());
  assertFalse(menu.ready(true,100));assertFalse(menu.ready(true,399));
  assertTrue(menu.ready(true,400));assertEquals(1,draws.get());
  assertFalse(menu.ready(false,450));assertFalse(menu.ready(true,500));
  assertFalse(menu.ready(true,999));assertTrue(menu.ready(true,1000));
  assertEquals(2,draws.get());
 }
 @Test void productionDelaysRemainWithinRequestedBounds(){
  for(int i=0;i<100;i++){
   FossilRowboatMenu menu=new FossilRowboatMenu();
   assertFalse(menu.ready(true,0));assertFalse(menu.ready(true,299));
   assertTrue(menu.ready(true,500));
  }
 }
}
