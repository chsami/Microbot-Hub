package net.runelite.client.plugins.microbot.delveprayerhelper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Projectile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperState;
import net.runelite.client.plugins.microbot.delveprayerhelper.enums.DelvePrayerHelperProjectile;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
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

                handleProjectiles();

            } catch (Exception ex) {
                log.trace("Exception in main loop: ", ex);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    void handleProjectiles() {
        int currentCycle = Microbot.getClient().getGameCycle();
        Rs2NpcModel boss = Microbot.getRs2NpcCache().query().where(n -> n.getName() != null && n.getName().contains("Doom of Mokhaiotl")).nearest();

        incomingProjectiles.entrySet().removeIf(e -> e.getKey() < currentCycle);

        boolean bossAlive = boss != null && !boss.getNpc().isDead();

        if (!bossAlive) {
            Rs2Prayer.disableAllPrayers();

            state = DelvePrayerHelperState.IDLE;

            Microbot.status = state.toString();

            return;
        }

        if (incomingProjectiles.isEmpty()) {
            long now = System.currentTimeMillis();

            if (now - lastProjectileTime > 1000) {
                Rs2Prayer.toggle(Rs2Prayer.getActiveProtectionPrayer(), false);
                toggleOffensivePrayer();

                state = DelvePrayerHelperState.IDLE;

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
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            toggleOffensivePrayer();

            state = DelvePrayerHelperState.PRAY_MELEE;
        }
        else if (projectile.getId() == DelvePrayerHelperProjectile.MAGE.getProjectileID()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
            toggleOffensivePrayer();

            state = DelvePrayerHelperState.PRAY_MAGE;
        }
        else if (projectile.getId() == DelvePrayerHelperProjectile.RANGE.getProjectileID()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
            toggleOffensivePrayer();

            state = DelvePrayerHelperState.PRAY_RANGE;
        }

        Microbot.status = state.toString();
    }

    private void toggleOffensivePrayer() {
        if(config.offensivePrayer()) {
            var doom = Microbot.getRs2NpcCache().query().where(n -> n.getName() != null && n.getName().contains("Doom of Mokhaiotl")).nearest();
            boolean shielded = doom != null && doom.getName().contains("(Shielded)");
            Rs2Prayer.toggle(Rs2Prayer.getBestRangePrayer(), !config.noOffensivePrayerInShieldPhase() || !shielded);
        }
    }

    public boolean isOffensivePrayerOn() {
        return Rs2Prayer.isPrayerActive(Rs2Prayer.getBestRangePrayer());
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}