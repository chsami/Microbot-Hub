package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.client.plugins.microbot.util.magic.Runes;

final class RuneRequirementAllocator {


   static Map<Integer, Integer> allocateRequiredRunes(Map<Runes, Integer> required, Map<Integer, Integer> available, IntPredicate usable) {
      EnumMap<Runes, Integer> remaining = new EnumMap<>(Runes.class);
      remaining.putAll(required);
      LinkedHashMap<Integer, Integer> allocated = new LinkedHashMap<>();

      for (Runes requiredRune : Runes.values()) {
         for (Runes suppliedRune : Runes.values()) {
            int missing = remaining.getOrDefault(requiredRune, 0);
            if (missing <= 0) {
               break;
            }

            int itemId = suppliedRune.getItemId();
            if (suppliedRune.providesRune(requiredRune) && usable.test(itemId)) {
               int quantity = Math.min(missing, available.getOrDefault(itemId, 0) - allocated.getOrDefault(itemId, 0));
               if (quantity > 0) {
                  allocated.merge(itemId, quantity, Integer::sum);

                  for (Runes providedRune : Runes.values()) {
                     if (suppliedRune.providesRune(providedRune)) {
                        remaining.computeIfPresent(providedRune, (rune, count) -> Math.max(0, count - quantity));
                     }
                  }
               }
            }
         }

         if (remaining.getOrDefault(requiredRune, 0) > 0) {
            return null;
         }
      }

      return allocated;
   }


}
