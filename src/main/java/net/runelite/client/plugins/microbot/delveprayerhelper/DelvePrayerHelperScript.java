package net.runelite.client.plugins.microbot.delveprayerhelper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperState;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperProjectile;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DelvePrayerHelperScript extends Script {

    private final DelvePrayerHelperPlugin plugin;
	private final DelvePrayerHelperConfig config;

    private DelvePrayerHelperState state = DelvePrayerHelperState.IDLE;

    final Map<Integer, Projectile> incomingProjectiles = new HashMap<>();
    private long lastProjectileTime = 0;

	@Inject
	public DelvePrayerHelperScript(DelvePrayerHelperPlugin plugin, DelvePrayerHelperConfig config) {
		this.plugin = plugin;
		this.config = config;
	}

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                handleProjectiles();

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                log.info("Total time for loop {}ms", totalTime);

            } catch (Exception ex) {
                log.trace("Exception in main loop: ", ex);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    void handleProjectiles() {
        int currentCycle = Microbot.getClient().getGameCycle();
        Rs2NpcModel boss = Rs2Npc.getNpc("Doom of Mokhaiotl", false);

        incomingProjectiles.entrySet().removeIf(e -> e.getKey() < currentCycle);

        boolean bossAlive = boss != null && !boss.isDead();

        if (!bossAlive) {
            state = DelvePrayerHelperState.IDLE;

            Rs2Prayer.disableAllPrayers();

            Microbot.status = state.toString();

            return;
        }

        if (incomingProjectiles.isEmpty()) {
            long now = System.currentTimeMillis();

            if (now - lastProjectileTime > 1000) {
                state = DelvePrayerHelperState.IDLE;

                Rs2Prayer.toggle(Rs2Prayer.getActiveProtectionPrayer(), false);
                toggleOffensivePrayer();

                Microbot.status = state.toString();
            }

            return;
        }

        // Find soonest impact
        Map.Entry<Integer, Projectile> next = incomingProjectiles.entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getKey))
                .orElse(null);

        if (next == null) {
            return;
        }

        lastProjectileTime = System.currentTimeMillis();
        int ticksUntilImpact = (next.getKey() - currentCycle) / 30;

        if (ticksUntilImpact <= 1)  {
            switchPrayer(next.getValue());
        }
    }

    void switchPrayer(Projectile projectile) {
        if (projectile.getId() == DelvePrayerHelperProjectile.MELEE.getProjectileID()) {
            state = DelvePrayerHelperState.PRAY_MELEE;

            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            toggleOffensivePrayer();
        }
        else if (projectile.getId() == DelvePrayerHelperProjectile.MAGE.getProjectileID()) {
            state = DelvePrayerHelperState.PRAY_MAGE;

            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
            toggleOffensivePrayer();
        }
        else if (projectile.getId() == DelvePrayerHelperProjectile.RANGE.getProjectileID()) {
            state = DelvePrayerHelperState.PRAY_RANGE;

            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
            toggleOffensivePrayer();
        }

        Microbot.status = state.toString();
    }

    private void toggleOffensivePrayer() {
        if(config.offensivePrayer()) {
            Rs2Prayer.toggle(Rs2Prayer.getBestRangePrayer(), !config.noOffensivePrayerInShieldPhase()
                    || !Rs2Npc.getNpc("Doom of Mokhaiotl", false).getName().contains("(Shielded)"));
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}