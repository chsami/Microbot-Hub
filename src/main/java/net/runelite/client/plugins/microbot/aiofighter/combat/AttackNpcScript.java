package net.runelite.client.plugins.microbot.aiofighter.combat;

import lombok.SneakyThrows;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyle;
import net.runelite.client.plugins.microbot.aiofighter.enums.AttackStyleMapper;
import net.runelite.client.plugins.microbot.aiofighter.enums.State;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2Cannon;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.item.Rs2EnsouledHead;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.VarbitID.*;

public class AttackNpcScript extends Script {

    public static Actor currentNpc = null;
    public static AtomicReference<List<Rs2NpcModel>> filteredAttackableNpcs = new AtomicReference<>(new ArrayList<>());
    public static Rs2WorldArea attackableArea = null;
    public static volatile int cachedTargetNpcIndex = -1;
    private boolean messageShown = false;
    private int noNpcCount = 0;

    public static void skipNpc() {
        currentNpc = null;
    }

    @SneakyThrows
    public void run(AIOFighterConfig config) {
        try {
            Rs2NpcManager.loadJson();
            Rs2Antiban.resetAntibanSettings();
            Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
            Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run())
                    return;

                if (config.toggleCannon()
                        && !config.state().equals(State.BANKING)
                        && !config.state().equals(State.WALKING)) {
                    if (Rs2Cannon.repair()) return;
                    Rs2Cannon.refill();
                }

                if (config.useSpecialAttack()
                        && Rs2Equipment.all("guthan's").count() != 4
                        && Rs2Player.isInteracting()) {
                    Microbot.getSpecialAttackConfigs().useSpecWeapon();
                }

                if (!config.toggleCombat())
                    return;

                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation != null
                        && config.centerLocation().distanceTo(playerLocation) < config.attackRadius()
                        && !config.centerLocation().equals(new WorldPoint(0, 0, 0))
                        && AIOFighterPlugin.getState() != State.BANKING) {
                    if (ShortestPathPlugin.getPathfinder() != null)
                        Rs2Walker.setTarget(null);
                    AIOFighterPlugin.setState(State.IDLE);
                }

                if (config.state().equals(State.BANKING) || config.state().equals(State.WALKING))
                    return;

                if (config.reanimateEnsouledHeads()) {
                    Rs2EnsouledHead head = Rs2EnsouledHead.getReanimatableHead();
                    if (head != null) {
                        boolean prevPause = Microbot.pauseAllScripts.getAndSet(true);
                        try {
                            if (head.reanimate()) {
                                sleepUntil(() -> findReanimatedHeadOnPlayer() != null, 15000);
                            }
                        } finally {
                            Microbot.pauseAllScripts.set(prevPause);
                        }
                    }
                    Rs2NpcModel reanimated = findReanimatedHeadOnPlayer();
                    if (reanimated != null) {
                        reanimated.click("Attack");
                        return;
                    }
                }


                attackableArea = new Rs2WorldArea(config.centerLocation().toWorldArea());
                attackableArea = attackableArea.offset(config.attackRadius());
                final Set<String> npcsToAttack = Arrays.stream(config.attackableNpcs().split(","))
                        .map(x -> x.trim().toLowerCase())
                        .filter(x -> !x.isEmpty())
                        .collect(Collectors.toSet());
                final Player localPlayer = Microbot.getClient().getLocalPlayer();
                final WorldPoint centerLocation = config.centerLocation();
                final int attackRadius = config.attackRadius();
                final boolean requireReachable = config.attackReachableNpcs();
                final Rs2WorldPoint rs2PlayerPoint = Rs2Player.getRs2WorldPoint();
                // Inside an instance, npc.getWorldLocation() returns the instance-side coord
                // (high-corner template region), but centerLocation/centre-tile and
                // Rs2Player.getWorldLocation() are template/overworld coords. Convert via the
                // npc's scene-local point so both sides of the radius/path checks match.
                final WorldView worldView = Microbot.getClient().getTopLevelWorldView();
                final boolean instanced = worldView != null && worldView.getScene().isInstance();

                List<Rs2NpcModel> attackableNpcs = Microbot.getRs2NpcCache().query()
                        .where(npc -> npc.getCombatLevel() > 0 && !npc.isDead())
                        .where(npc -> !npc.isInteracting() || Objects.equals(npc.getInteracting(), localPlayer))
                        .where(npc -> {
                            WorldPoint loc = npcWorldLocation(npc, instanced);
                            if (loc == null) return false;
                            if (loc.distanceTo(centerLocation) > attackRadius) return false;
                            return !requireReachable || rs2PlayerPoint.distanceToPath(loc) < Integer.MAX_VALUE;
                        })
                        .where(npc -> {
                            String name = npc.getName();
                            return name != null && !npcsToAttack.isEmpty() && npcsToAttack.contains(name.toLowerCase());
                        })
                        .toList()
                        .stream()
                        .sorted(Comparator
                                .comparingInt((Rs2NpcModel npc) -> Objects.equals(npc.getInteracting(), localPlayer) ? 0 : 1)
                                .thenComparingInt(npc -> rs2PlayerPoint.distanceToPath(npcWorldLocation(npc, instanced))))
                        .collect(Collectors.toList());

                filteredAttackableNpcs.set(attackableNpcs);

                // Check if we should pause while looting is happening
                if (Microbot.pauseAllScripts.get()) {
                    return; // Don't attack while looting
                }

                // Check if we need to update our cached target (but not while waiting for loot)
                if (!AIOFighterPlugin.isWaitingForLoot()) {
                    Actor currentInteracting = Rs2Player.getInteracting();
                    if (currentInteracting instanceof NPC) {
                        NPC interactingNpc = (NPC) currentInteracting;
                        // Update our cached target to who we're fighting
                        if (interactingNpc.getHealthRatio() > 0 && !interactingNpc.isDead()) {
                            cachedTargetNpcIndex = interactingNpc.getIndex();
                        }
                    }
                }

                // Check if our cached target died
                if (config.toggleWaitForLoot() && !AIOFighterPlugin.isWaitingForLoot() && cachedTargetNpcIndex != -1) {
                    final int targetIndex = cachedTargetNpcIndex;
                    Rs2NpcModel cachedNpcModel = Microbot.getRs2NpcCache().query()
                            .where(npc -> npc.getIndex() == targetIndex)
                            .first();

                    if (cachedNpcModel != null && (cachedNpcModel.isDead() || (cachedNpcModel.getHealthRatio() == 0 && cachedNpcModel.getHealthScale() > 0))) {
                        AIOFighterPlugin.setWaitingForLoot(true);
                        AIOFighterPlugin.setLastNpcKilledTime(System.currentTimeMillis());
                        Microbot.status = "Waiting for loot...";
                        Microbot.log("NPC died, waiting for loot...");
                        cachedTargetNpcIndex = -1;
                        return;
                    }
                }

                // Check if we're waiting for loot
                if (config.toggleWaitForLoot() && AIOFighterPlugin.isWaitingForLoot()) {
                    long timeSinceKill = System.currentTimeMillis() - AIOFighterPlugin.getLastNpcKilledTime();
                    int timeoutMs = config.lootWaitTimeout() * 1000;
                    if (timeSinceKill >= timeoutMs) {
                        // Timeout reached, resume combat
                        AIOFighterPlugin.clearWaitForLoot("Loot wait timeout reached, resuming combat");
                        cachedTargetNpcIndex = -1; // Clear cached NPC on timeout
                    } else {
                        // Still waiting for loot, don't attack
                        int secondsLeft = (int) Math.max(1, TimeUnit.MILLISECONDS.toSeconds(timeoutMs - timeSinceKill));
                        Microbot.status = "Waiting for loot... " + secondsLeft + "s";
                        return;
                    }
                }

                if (config.toggleCenterTile() && config.centerLocation().getX() == 0
                        && config.centerLocation().getY() == 0) {
                    if (!messageShown) {
                        Microbot.showMessage("Please set a center location");
                        messageShown = true;
                    }
                    return;
                }
                messageShown = false;

                if (Rs2AntibanSettings.antibanEnabled && Rs2AntibanSettings.actionCooldownChance > 0) {
                    if (Rs2AntibanSettings.actionCooldownActive) {
                        AIOFighterPlugin.setState(State.COMBAT);
                        handleItemOnNpcToKill(config);
                        return;
                    }
                } else {
                    if (Rs2Combat.inCombat()) {
                        AIOFighterPlugin.setState(State.COMBAT);
                        handleItemOnNpcToKill(config);
                        return;
                    }
                }

                if (!attackableNpcs.isEmpty()) {
                    noNpcCount = 0;

                    Rs2NpcModel npc = attackableNpcs.get(0);

                    if (!Rs2Camera.isTileOnScreen(npc.getLocalLocation()))
                        Rs2Camera.turnTo(npc);

                    npc.click("attack");
                    Microbot.status = "Attacking " + npc.getName();
                    Rs2Antiban.actionCooldown();
                    //sleepUntil(Rs2Player::isInteracting, 1000);

                    if (config.togglePrayer()) {
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
                    }


                } else {
                    if (Rs2Player.getWorldLocation().isInArea(attackableArea)) {
                        Microbot.log(Level.INFO, "No attackable NPC found");
                        noNpcCount++;
                        if (noNpcCount > 60 && config.slayerMode()) {
                            Microbot.log(Level.INFO, "No attackable NPC found for 60 ticks, resetting slayer task");
                            AIOFighterPlugin.addBlacklistedSlayerNpcs(Rs2Slayer.slayerTaskMonsterTarget);
                            noNpcCount = 0;
                            SlayerScript.reset();
                        }
                    } else {
                        Rs2Walker.walkTo(config.centerLocation(), 0);
                        AIOFighterPlugin.setState(State.WALKING);
                    }

                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }


    /**
     * item on npcs that need to kill like rockslug
     */
    private void handleItemOnNpcToKill(AIOFighterConfig config) {
        final Player localPlayer = Microbot.getClient().getLocalPlayer();
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                .where(n -> n.isDead() && Objects.equals(n.getInteracting(), localPlayer))
                .first();
        if (npc == null) return;
        List<String> lizardVariants = new ArrayList<>(Arrays.asList("Lizard", "Desert Lizard", "Small Lizard"));
        // Rs2Inventory.useItemOnNpc only accepts the legacy util.npc.Rs2NpcModel — wrap our underlying NPC at the call site.
        net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel legacyNpc =
                new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(npc.getNpc());
        if (Microbot.getVarbitValue(SLAYER_AUTOKILL_DESERTLIZARDS) == 0 && lizardVariants.contains(npc.getName()) && npc.getHealthRatio() < 5) {
            Rs2Inventory.useItemOnNpc(ItemID.SLAYER_ICY_WATER, legacyNpc);
            Rs2Player.waitForAnimation();
        } else if (Microbot.getVarbitValue(SLAYER_AUTOKILL_ROCKSLUGS) == 0 && npc.getName().equalsIgnoreCase("rockslug") && npc.getHealthRatio() < 5) {
            Rs2Inventory.useItemOnNpc(ItemID.SLAYER_BAG_OF_SALT, legacyNpc);
            Rs2Player.waitForAnimation();
        } else if (Microbot.getVarbitValue(SLAYER_AUTOKILL_GARGOYLES) == 0 && npc.getName().equalsIgnoreCase("gargoyle") && npc.getHealthRatio() < 3) {
            Rs2Inventory.useItemOnNpc(ItemID.SLAYER_ROCK_HAMMER, legacyNpc);
            Rs2Player.waitForAnimation();
        }
    }

    /**
     * Finds a reanimated head NPC the player is currently interacting with.
     * Replaces the legacy {@code Rs2Npc.getNpcsForPlayer(Rs2EnsouledHead::isNpcReanimated)} call.
     */
    private Rs2NpcModel findReanimatedHeadOnPlayer() {
        final Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) return null;
        return Microbot.getRs2NpcCache().query()
                .where(npc -> Objects.equals(npc.getInteracting(), localPlayer))
                .where(npc -> {
                    String name = npc.getName();
                    return name != null && name.contains("Reanimated");
                })
                .first();
    }

    /**
     * Returns the npc's world location in the same coordinate space as
     * {@code config.centerLocation()} / {@code Rs2Player.getWorldLocation()}.
     * Inside an instance the raw {@code npc.getWorldLocation()} reports the
     * instance-side coord (high-corner template region) while the centre tile
     * is stored as a template/overworld coord, so distances would never match.
     */
    private static WorldPoint npcWorldLocation(Rs2NpcModel npc, boolean instanced) {
        if (instanced) {
            LocalPoint lp = npc.getLocalLocation();
            if (lp != null) {
                return WorldPoint.fromLocalInstance(Microbot.getClient(), lp);
            }
        }
        return npc.getWorldLocation();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
