package net.runelite.client.plugins.microbot.jad;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JadScript extends Script {
    public static final Map<Integer, Long> npcAttackCooldowns = new HashMap<>();

    public boolean run(JadConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                List<Rs2NpcModel> jadNpcs = Microbot.getRs2NpcCache().query()
                        .where(n -> n.getName() != null && n.getName().toLowerCase().contains("jad"))
                        .toList();

                for (Rs2NpcModel jadNpc : jadNpcs) {
                    if (jadNpc == null) continue;

                    long currentTimeMillis = System.currentTimeMillis();
                    int npcIndex = jadNpc.getIndex();

                    if (npcAttackCooldowns.containsKey(npcIndex)) {
                        if (currentTimeMillis - npcAttackCooldowns.get(npcIndex) < 4600) {
                            continue;
                        } else {
                            npcAttackCooldowns.remove(npcIndex);
                        }
                    }

                    int npcAnimation = jadNpc.getNpc().getAnimation();
                    handleJadPrayer(npcAnimation);
                    if (config.shouldAttackHealers()) {
                        handleHealerInteraction();
                        npcAttackCooldowns.put(npcIndex, currentTimeMillis);
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 10, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleHealerInteraction() {
        var healer = Microbot.getRs2NpcCache().query()
                .where(n -> n.getName() != null && n.getName().toLowerCase().contains("hurkot") && !n.isInteractingWithPlayer())
                .nearest();

        if (healer != null) {
            healer.click("Attack");
        } else {
            var npc = Rs2Player.getInteracting();
            if (npc == null || npc.getName().contains("hurkot")) {
                var jad = Microbot.getRs2NpcCache().query()
                        .where(n -> n.getName() != null && n.getName().toLowerCase().contains("jad"))
                        .nearest();
                if (jad != null) {
                    jad.click("Attack");
                }
            }
        }

    }

    @Override
    public void shutdown() {
        super.shutdown();
        npcAttackCooldowns.clear();
    }

    private void handleJadPrayer(int animationId) {
        if (animationId == 7592 || animationId == 2656) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else if (animationId == 7593 || animationId == 2652) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }
    }
}