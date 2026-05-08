package net.runelite.client.plugins.microbot.jad;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class JadScript extends Script {
    private static final int JAD_MAGE_ANIMATION_1 = 7592;
    private static final int JAD_MAGE_ANIMATION_2 = 2656;
    private static final int JAD_RANGE_ANIMATION_1 = 7593;
    private static final int JAD_RANGE_ANIMATION_2 = 2652;
    private static final int MAGE_IMPACT_DELAY_TICKS = 4;
    private static final int RANGE_IMPACT_DELAY_TICKS = 3;
    private static final int PRAYER_SWITCH_LEAD_TICKS = 1;

    public static final Map<Integer, Long> npcAttackCooldowns = new HashMap<>();
    private final Map<Integer, Integer> npcLastAttackAnimations = new HashMap<>();
    private final PriorityQueue<QueuedJadAttack> attackQueue =
            new PriorityQueue<>(Comparator.comparingInt((QueuedJadAttack a) -> a.impactTick).thenComparingLong(a -> a.sequence));
    private long attackSequence = 0;
    private JadAttackStyle queuedPrayerStyle = null;

    public boolean run(JadConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                int currentTick = Microbot.getClient().getTickCount();

                List<Rs2NpcModel> jadNpcs = Microbot.getRs2NpcCache().query()
                        .where(n -> n.getName() != null && n.getName().toLowerCase().contains("jad"))
                        .toList();
                Set<Integer> activeJadIndexes = new HashSet<>();

                for (Rs2NpcModel jadNpc : jadNpcs) {
                    if (jadNpc == null) continue;

                    long currentTimeMillis = System.currentTimeMillis();
                    int npcIndex = jadNpc.getIndex();
                    activeJadIndexes.add(npcIndex);

                    if (npcAttackCooldowns.containsKey(npcIndex)) {
                        if (currentTimeMillis - npcAttackCooldowns.get(npcIndex) < 4600) {
                            continue;
                        } else {
                            npcAttackCooldowns.remove(npcIndex);
                        }
                    }

                    int npcAnimation = jadNpc.getNpc().getAnimation();
                    if (shouldHandleAttackAnimation(npcIndex, npcAnimation)) {
                        queueJadAttack(npcIndex, npcAnimation, currentTick);
                    }
                    if (config.shouldAttackHealers()) {
                        handleHealerInteraction();
                        npcAttackCooldowns.put(npcIndex, currentTimeMillis);
                    }
                }

                removeStaleNpcData(activeJadIndexes);
                processAttackQueue(currentTick);
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
        npcLastAttackAnimations.clear();
        synchronized (this) {
            attackQueue.clear();
        }
        queuedPrayerStyle = null;
    }

    boolean shouldHandleAttackAnimation(int npcIndex, int animationId) {
        if (!isJadAttackAnimation(animationId)) {
            npcLastAttackAnimations.remove(npcIndex);
            return false;
        }

        Integer previousAnimation = npcLastAttackAnimations.put(npcIndex, animationId);
        return previousAnimation == null || previousAnimation != animationId;
    }

    private boolean isJadAttackAnimation(int animationId) {
        return animationId == JAD_MAGE_ANIMATION_1
                || animationId == JAD_MAGE_ANIMATION_2
                || animationId == JAD_RANGE_ANIMATION_1
                || animationId == JAD_RANGE_ANIMATION_2;
    }

    private void queueJadAttack(int npcIndex, int animationId, int currentTick) {
        JadAttackStyle style = getAttackStyle(animationId);
        if (style == null) {
            return;
        }

        int impactDelayTicks = style == JadAttackStyle.MAGIC ? MAGE_IMPACT_DELAY_TICKS : RANGE_IMPACT_DELAY_TICKS;
        int impactTick = currentTick + impactDelayTicks;
        synchronized (this) {
            attackQueue.offer(new QueuedJadAttack(npcIndex, style, impactTick, attackSequence++));
        }
    }

    private JadAttackStyle getAttackStyle(int animationId) {
        if (animationId == JAD_MAGE_ANIMATION_1 || animationId == JAD_MAGE_ANIMATION_2) {
            return JadAttackStyle.MAGIC;
        }
        if (animationId == JAD_RANGE_ANIMATION_1 || animationId == JAD_RANGE_ANIMATION_2) {
            return JadAttackStyle.RANGED;
        }
        return null;
    }

    private void removeStaleNpcData(Set<Integer> activeJadIndexes) {
        npcLastAttackAnimations.keySet().removeIf(index -> !activeJadIndexes.contains(index));
        synchronized (this) {
            attackQueue.removeIf(attack -> !activeJadIndexes.contains(attack.npcIndex));
        }
    }

    private void processAttackQueue(int currentTick) {
        synchronized (this) {
            QueuedJadAttack nextAttack = attackQueue.peek();
            if (nextAttack == null) {
                queuedPrayerStyle = null;
                return;
            }

            int switchTick = Math.max(0, nextAttack.impactTick - PRAYER_SWITCH_LEAD_TICKS);
            if (currentTick < switchTick) {
                return;
            }

            if (queuedPrayerStyle != nextAttack.style) {
                switchPrayer(nextAttack.style);
                queuedPrayerStyle = nextAttack.style;
            }
        }
    }

    synchronized void onTrackedProjectileImpact() {
        if (attackQueue.isEmpty()) {
            queuedPrayerStyle = null;
            return;
        }

        attackQueue.poll();
        QueuedJadAttack nextAttack = attackQueue.peek();
        if (nextAttack == null) {
            queuedPrayerStyle = null;
            return;
        }

        if (queuedPrayerStyle != nextAttack.style) {
            switchPrayer(nextAttack.style);
            queuedPrayerStyle = nextAttack.style;
        }
    }

    boolean isInJadFight() {
        var jad = Microbot.getRs2NpcCache().query()
                .where(n -> n.getName() != null && n.getName().toLowerCase().contains("jad"))
                .nearest();
        return jad != null;
    }

    private void switchPrayer(JadAttackStyle style) {
        if (style == JadAttackStyle.MAGIC) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }
    }

    private static final class QueuedJadAttack {
        private final int npcIndex;
        private final JadAttackStyle style;
        private final int impactTick;
        private final long sequence;

        private QueuedJadAttack(int npcIndex, JadAttackStyle style, int impactTick, long sequence) {
            this.npcIndex = npcIndex;
            this.style = style;
            this.impactTick = impactTick;
            this.sequence = sequence;
        }
    }

    private enum JadAttackStyle {
        MAGIC,
        RANGED
    }
}
