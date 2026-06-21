package net.runelite.client.plugins.microbot.vorkathhelper;

import lombok.Getter;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public class VorkathHelperScript extends Script {

    private final VorkathHelperConfig config;

    private static final int VORKATH_ID = 8061;
    private static final int FIREBALL_PROJECTILE = 1481;
    private static final int SPAWN_PROJECTILE = 395;
    private static final int ACID_PROJECTILE = 1483;
    private static final int ATTACKS_PER_CYCLE = 6;
    private static final int ACID_ANIMATION = 7957;
    private static final int VORKATH_WAKE_UP_ANIMATION = 7950;
    private static final int CRUMBLE_UNDEAD_PROJECTILE = 146;
    private long lastCrumbleCastAttempt = 0;
    private static final long CRUMBLE_COOLDOWN_MS = 100;
    private static final Set<Integer> NORMAL_ATTACKS = Set.of(7951, 7952, 7960);

    public enum VorkathSpecial {
        ACID_SPEC,
        SPAWN_SPEC
    }

    @Getter
    private final VorkathVars vorkathVars = new VorkathVars();

    @Inject
    public VorkathHelperScript(VorkathHelperConfig config) {
        this.config = config;
    }


    @Override
    public boolean run() {

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {

                if (!Microbot.isLoggedIn())
                    return;
                updateNpcs();

                if (config.autoCastCrumbleUndead()) {
                    handleZombieSpawn();
                }

                if (vorkathVars.getVorkath() == null) {
                    resetFightState();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }, 0, 70, TimeUnit.MILLISECONDS);

        return true;
    }


    private void handleZombieSpawn() {
        Rs2NpcModel zombieSpawn = vorkathVars.getZombieSpawn();

        if (zombieSpawn == null) return;
        if (zombieSpawn.isDead()) return;
        if (doesProjectileExist(CRUMBLE_UNDEAD_PROJECTILE)) return;
        if (System.currentTimeMillis() - lastCrumbleCastAttempt < CRUMBLE_COOLDOWN_MS) return;

        lastCrumbleCastAttempt = System.currentTimeMillis();

        Microbot.getClientThread().invoke(() -> {
            Microbot.getClient().menuAction(
                    -1,
                    InterfaceID.MagicSpellbook.CRUMBLE_UNDEAD,
                    MenuAction.WIDGET_TARGET,
                    1,
                    -1,
                    "Cast",
                    MagicAction.CRUMBLE_UNDEAD.getName()
            );
            Microbot.getClient().menuAction(
                    0,
                    0,
                    MenuAction.WIDGET_TARGET_ON_NPC,
                    zombieSpawn.getIndex(),
                    -1,
                    "Cast",
                    zombieSpawn.getName()
            );
        });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        updateFireball();
    }

    private boolean doesProjectileExist(int projectileId) {
        for (Projectile projectile : Microbot.getClient().getProjectiles()) {
            if (projectile.getId() == projectileId) {
                return true;
            }
        }
        return false;
    }

    private void updateFireball() {
        if (vorkathVars.getFireballProjectile() == null)
            return;

        boolean exists = false;

        for (Projectile projectile : Microbot.getClient().getProjectiles()) {
            if (projectile == vorkathVars.getFireballProjectile()) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            vorkathVars.setFireballProjectile(null);
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {

        if (!(event.getActor() instanceof NPC))
            return;

        NPC npc = (NPC) event.getActor();

        if (npc.getId() != VORKATH_ID)
            return;

        int animation = npc.getAnimation();

        if (vorkathVars.getLastVorkathAnimation() == ACID_ANIMATION
                && animation != ACID_ANIMATION) {
            endAcidPhase();
        }

        vorkathVars.setLastVorkathAnimation(animation);

        if (animation == ACID_ANIMATION) {
            vorkathVars.setAcidPhase(true);
            vorkathVars.setNextSpecial(VorkathSpecial.SPAWN_SPEC);
            return;
        }

        if (animation == VORKATH_WAKE_UP_ANIMATION) {
            vorkathVars.setAttackCount(0);
            return;
        }
        if (NORMAL_ATTACKS.contains(animation)) {
            vorkathVars.setAttackCount(vorkathVars.getAttackCount() + 1);

            if (vorkathVars.getAttackCount() > ATTACKS_PER_CYCLE) {
                vorkathVars.setAttackCount(0);
            }
        }
    }

    private void endAcidPhase() {
        vorkathVars.setAcidPhase(false);
        vorkathVars.getAcidTiles().clear();
        vorkathVars.getTrackedAcidProjectiles().clear();
    }

    private void updateNpcs() {
        vorkathVars.setVorkath(Microbot.getRs2NpcCache()
                .query()
                .withId(VORKATH_ID)
                .first());

        vorkathVars.setZombieSpawn(Microbot.getRs2NpcCache()
                .query()
                .withName("Zombified Spawn")
                .first());
    }

    private void resetFightState() {
        vorkathVars.reset();
    }

    public int getFireballTicksRemaining() {
        if (vorkathVars.getFireballProjectile() == null)
            return -1;

        return (int) Math.ceil(
                vorkathVars.getFireballProjectile().getRemainingCycles() / 30.0
        );
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {

        Projectile projectile = event.getProjectile();

        switch (projectile.getId()) {
            case SPAWN_PROJECTILE:
                handleSpawnProjectile();
                break;

            case ACID_PROJECTILE:
                handleAcidProjectile(projectile);
                break;

            case FIREBALL_PROJECTILE:
                handleFireballProjectile(projectile);
                break;
        }
    }

    private void handleSpawnProjectile() {
        vorkathVars.setAttackCount(0);
        vorkathVars.setNextSpecial(VorkathSpecial.ACID_SPEC);
    }

    private void handleFireballProjectile(Projectile projectile) {
        if (vorkathVars.getFireballProjectile() == null) {
            vorkathVars.setFireballProjectile(projectile);
        }
    }

    private void handleAcidProjectile(Projectile projectile) {
        if (!vorkathVars.getTrackedAcidProjectiles().add(projectile))
            return;

        LocalPoint target = projectile.getTarget();

        if (target == null)
            return;

        WorldPoint tile = WorldPoint.fromLocal(
                Microbot.getClient(),
                target
        );
        vorkathVars.getAcidTiles().add(tile);
    }

}