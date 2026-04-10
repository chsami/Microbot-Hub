package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyleMapper;
import net.runelite.client.plugins.microbot.aiofighter.enums.PrayerStyle;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PrayerScript extends Script {

    public boolean run(AIOFighterConfig config) {
        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                handlePrayer(config);
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handlePrayer(AIOFighterConfig config) {
        if (!Microbot.isLoggedIn() || !config.togglePrayer()) return;
        if (config.prayerStyle() != PrayerStyle.CONTINUOUS && config.prayerStyle() != PrayerStyle.ALWAYS_ON) return;
        if (config.prayerStyle() == PrayerStyle.CONTINUOUS) {
            final Player localPlayer = Microbot.getClient().getLocalPlayer();
            // Logout race: isLoggedIn() returned true above but the player ref can be null
            // mid-tick. Legacy Rs2Npc.getNpcsForPlayer guards this; we have to as well.
            if (localPlayer == null) return;
            boolean underAttack = Microbot.getRs2NpcCache().query()
                    .where(npc -> Objects.equals(npc.getInteracting(), localPlayer))
                    .where(npc -> !npc.isDead() && npc.getCombatLevel() > 1)
                    .first() != null
                || Rs2Combat.inCombat();
            if(!underAttack) {
                Rs2Prayer.disableAllPrayers();
                return;
            }
            if ((Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE) || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC) || Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE) || Rs2Prayer.isQuickPrayerEnabled())) {
                return;
            }

            Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                    .where(n -> Objects.equals(n.getInteracting(), localPlayer))
                    .where(n -> !n.isDead() && n.getCombatLevel() > 1)
                    .nearest();
            if (npc == null) {
                return;
            }
            if (!config.toggleQuickPray()) {

                AttackStyle attackStyle = AttackStyleMapper
                        .mapToAttackStyle(Rs2NpcManager.getAttackStyle(npc.getId()));
                if (attackStyle != null) {
                    switch (attackStyle) {
                        case MAGE:
                            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
                            break;
                        case MELEE:
                            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                            break;
                        case RANGED:
                            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
                            break;
                    }
                }
            } else {
                Rs2Prayer.toggleQuickPrayer(true);
            }
        } else {
			Rs2Prayer.toggleQuickPrayer(config.prayerStyle() == PrayerStyle.ALWAYS_ON);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
