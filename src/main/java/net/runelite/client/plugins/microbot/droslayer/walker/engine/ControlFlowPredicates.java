package net.runelite.client.plugins.microbot.droslayer.walker.engine;

/**
 * Integer mixing helpers used by the engine's branch keys.
 */
public class ControlFlowPredicates {
   /** Public alias for {@link #mixBranchKey(int)}. */
   public static int fxprwmkntroustgs(int value) {
      return mixBranchKey(value);
   }

   /** Public alias for {@link #foldSignedHighBits(int)}. */
   public static int pppobdkgjotijthy(int value) {
      return foldSignedHighBits(value);
   }

   /** Second public alias for the same signed-bit transform. */
   public static int dlaxkfdcxgosnvpd(int value) {
      return foldSignedHighBits(value);
   }

   private static int mixBranchKey(int value) {
      return value != 0 ? ((value * 31) >>> 4) % value ^ (value >>> 16) : 0;
   }

   private static int foldSignedHighBits(int value) {
      // The signed shift is intentional; replacing it with rotateLeft changes negatives.
      return ((value & (7 << 29)) >> 29) | (value << 3);
   }
}
