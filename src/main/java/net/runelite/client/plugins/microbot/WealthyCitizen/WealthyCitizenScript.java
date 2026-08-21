package net.runelite.client.plugins.microbot.WealthyCitizen;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public class WealthyCitizenScript extends Script {

    public final AtomicBoolean distractionActive = new AtomicBoolean(false);

    // Hoe lang we zoeken naar de citizen die naar de urchin toe loopt
    private static final long SEARCH_WINDOW_MS = 10_000;
    private long searchingUntil = 0;

    // Hoelang de auto-pickpocket periode duurt na onze klik
    private static final long DISTRACTION_DURATION_MS = 21_000;
    private final AtomicLong distractionEndsAt = new AtomicLong(0);

    private static final List<String> URCHIN_NAMES = Arrays.asList("Leo", "Julia", "Aurelia");
    private static final int MAX_DISTANCE_TO_TRIGGER = 1;

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (distractionActive.compareAndSet(true, false)) {
                    searchingUntil = System.currentTimeMillis() + SEARCH_WINDOW_MS;
                    log.info("=== Distraction gedetecteerd, zoeken naar gedistraheerde citizen ===");
                }

                // Fase 1: zoeken naar de citizen die vlak bij een urchin staat
                if (searchingUntil != 0 && System.currentTimeMillis() < searchingUntil) {
                    List<Rs2NpcModel> urchins = Rs2Npc.getNpcs()
                            .filter(npc -> npc.getName() != null &&
                                    URCHIN_NAMES.stream().anyMatch(name -> name.equalsIgnoreCase(npc.getName())))
                            .collect(Collectors.toList());

                    if (!urchins.isEmpty()) {
                        List<Rs2NpcModel> citizens = Rs2Npc.getNpcs("Wealthy Citizen").collect(Collectors.toList());

                        Rs2NpcModel closestCitizen = null;
                        int closestDistance = Integer.MAX_VALUE;

                        for (Rs2NpcModel citizen : citizens) {
                            int dist = urchins.stream()
                                    .mapToInt(u -> citizen.getWorldLocation().distanceTo(u.getWorldLocation()))
                                    .min()
                                    .orElse(Integer.MAX_VALUE);
                            if (dist < closestDistance) {
                                closestDistance = dist;
                                closestCitizen = citizen;
                            }
                        }

                        if (closestCitizen != null && closestDistance <= MAX_DISTANCE_TO_TRIGGER) {
                            log.info(">>> Gedistraheerde citizen gevonden op {} (afstand {}), pickpocketen! <<<",
                                    closestCitizen.getWorldLocation(), closestDistance);
                            boolean success = Rs2Npc.interact(closestCitizen, "pickpocket");
                            if (success) {
                                distractionEndsAt.set(System.currentTimeMillis() + DISTRACTION_DURATION_MS);
                                searchingUntil = 0; // stop met zoeken, we hebben 'm
                            }
                        }
                    }
                } else if (searchingUntil != 0) {
                    searchingUntil = 0;
                    log.warn("Zoekvenster verstreken zonder match gevonden");
                }

                // Fase 2: wachten tot de auto-pickpocket periode voorbij is, dan coin pouches openen
                long endsAt = distractionEndsAt.get();
                if (endsAt != 0 && System.currentTimeMillis() < endsAt) {
                    return;
                }
                if (endsAt != 0) {
                    distractionEndsAt.set(0);
                    if (Rs2Inventory.hasItem("Coin pouch")) {
                        Rs2Inventory.interact("Coin pouch", "Open-all");
                    }
                }

            } catch (Exception ex) {
                log.error("Fout in WealthyCitizenScript: ", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }
}