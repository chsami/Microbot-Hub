package net.runelite.client.plugins.microbot.drozulrah;
import net.runelite.api.coords.LocalPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Spawn/timing/Jad fixture from the supplied helper; stands pinned to working v1.1. */
public class ZulrahRotationReferenceTest {
@Test public void rotationAMatchesReference() { check(ZulrahRotation.A, new int[][]{{2042,6720,7616,0,7488,7872,28},{2043,6720,7616,0,7488,7872,21},{2044,6720,7616,0,7488,7872,18},{2042,6720,6208,0,6208,7232,39},{2043,6720,7616,0,6208,7232,22},{2044,5440,7360,0,6208,7232,20},{2042,6720,6208,0,7232,7232,28},{2044,6720,6208,0,7232,7104,36},{2042,5440,7360,1,6208,7232,48},{2043,6720,7616,0,7488,7872,21}}); }
@Test public void rotationBMatchesReference() { check(ZulrahRotation.B, new int[][]{{2042,6720,7616,0,7488,7872,28},{2043,6720,7616,0,7488,7872,21},{2044,6720,7616,0,7488,7872,18},{2042,5440,7360,0,6208,7232,28},{2044,6720,6208,0,6208,7104,39},{2043,6720,7616,0,6208,7232,21},{2042,8000,7360,0,6720,6848,20},{2044,6720,6208,0,6208,7232,36},{2042,5440,7360,1,6208,7232,48},{2043,6720,7616,0,7488,7872,21}}); }
@Test public void rotationCMatchesReference() { check(ZulrahRotation.C, new int[][]{{2042,6720,7616,0,7488,7872,28},{2042,8000,7360,0,7488,7872,30},{2043,6720,7616,0,6208,8000,40},{2044,5440,7360,0,6208,7232,20},{2042,6720,6208,0,7232,7232,20},{2044,8000,7360,0,7232,7232,20},{2042,6720,7616,0,6208,7232,25},{2042,5440,7360,0,6208,7232,20},{2044,6720,7616,0,7232,7232,36},{2044,8000,7360,1,7232,7232,35},{2044,6720,7616,0,7488,7872,18}}); }
@Test public void rotationDMatchesReference() { check(ZulrahRotation.D, new int[][]{{2042,6720,7616,0,7488,7872,28},{2044,8000,7360,0,7488,7872,36},{2042,6720,6208,0,6208,7232,24},{2044,5440,7360,0,6208,7232,30},{2043,6720,7616,0,7232,7232,28},{2042,8000,7360,0,7232,7232,17},{2042,6720,6208,0,7232,7232,34},{2044,5440,7360,0,6208,7232,33},{2042,6720,7616,0,7232,7232,20},{2044,6720,7616,0,7232,7232,27},{2044,8000,7360,1,7232,7232,29},{2044,6720,7616,0,7488,7872,18}}); }
private void check(ZulrahRotation rotation, int[][] phases) {
 assertEquals(phases.length, rotation.size());
 for(int i=0;i<phases.length;i++) {
  int[] p=phases[i];
  assertTrue(rotation.matches(i,p[0],new LocalPoint(p[1],p[2])), "spawn at phase " + i);
  assertEquals(p[3]==1,rotation.isJad(i),"Jad at phase " + i);
  assertEquals(p[4],rotation.stand(i).local().getX());
  assertEquals(p[5],rotation.stand(i).local().getY());
  assertEquals(p[6],rotation.ticks(i));
 }
}
}
