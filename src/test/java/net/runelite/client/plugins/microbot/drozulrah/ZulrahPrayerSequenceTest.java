package net.runelite.client.plugins.microbot.drozulrah;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class ZulrahPrayerSequenceTest {
 @Test public void greenJadPreparesMagicAfterFirstRangeShot() {
  ZulrahPrayerSequence p=new ZulrahPrayerSequence();p.reset(false);
  assertTrue(p.attack(10));assertFalse(p.attack(14));
 }
 @Test public void blueJadPreparesRangeAfterFirstMagicShot() {
  ZulrahPrayerSequence p=new ZulrahPrayerSequence();p.reset(true);
  assertFalse(p.attack(10));assertTrue(p.attack(14));
 }
 @Test public void duplicateAnimationDoesNotFlipTwice() {
  ZulrahPrayerSequence p=new ZulrahPrayerSequence();p.reset(false);
  assertTrue(p.attack(10));assertTrue(p.attack(10));
 }
 @Test public void projectileConfirmsRatherThanDoubleFlipsAnimation() {
  ZulrahPrayerSequence p=new ZulrahPrayerSequence();p.reset(false);
  assertTrue(p.attack(10));assertTrue(p.projectile(300,true));
  assertFalse(p.attack(14));assertFalse(p.projectile(420,false));
  assertFalse(p.projectile(300,true));
 }
 @Test public void resetStartsANewPhaseWithoutStaleEvents() {
  ZulrahPrayerSequence p=new ZulrahPrayerSequence();p.reset(false);
  p.attack(100);p.reset(true);assertFalse(p.attack(10));
 }
}
