package net.runelite.client.plugins.microbot.mmcaves;

import com.google.common.collect.Table;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mmcaves.enums.CombatStyle;
import net.runelite.client.plugins.microbot.mmcaves.enums.LightSources;
import net.runelite.client.plugins.microbot.mmcaves.enums.Mode;
import net.runelite.client.plugins.microbot.mmcaves.enums.State;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class MmCavesScript extends Script {
    private final MmCavesPlugin plugin;

    @Inject
    public MmCavesScript(MmCavesPlugin plugin) {
        this.plugin = plugin;
    }

    private MmCavesConfig config;
    private Mode mode;
    public static State state = State.WALK_TO_START;
    public Instant startTime;
    public static long lastAggroResetTime = System.currentTimeMillis();
    private long lastAttackTime = System.currentTimeMillis();

    public void setConfig(MmCavesConfig config) {
        this.config = config;
        this.mode = config.combatStyle() == CombatStyle.RANGING ? Mode.RANGE : Mode.MAGIC;
    }

    private final WorldPoint START_TILE = new WorldPoint(2572, 9168, 1); // Upstairs
    private final WorldPoint FIGHTING_TILE_A = new WorldPoint(2452, 9159, 1);
    private final WorldPoint FIGHTING_TILE_B = new WorldPoint(2451, 9158, 1);
    private final WorldPoint AGGRO_RESET_TILE = new WorldPoint(2414, 9164, 1);
    private final WorldPoint EXIT_TILE = new WorldPoint(2381, 9168, 1);

    private final int cavesUpstairs = 10383;
    private final long AGGRO_RESET_COOLDOWN = 10 * 60 * 1000; // RESET EVERY 10 MINUTES

    private boolean onTileA = true;

    private boolean firstFightStarted = false;
    private boolean resettingAggro = false;
    public boolean caveIsEmpty = false;

    @Override
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // Check if player has light source
                boolean hasLightSource = Arrays.stream(LightSources.values())
                        .anyMatch(lightSource -> Rs2Inventory.contains(lightSource.getItemName()));
                if (!hasLightSource) {
                    Microbot.log("Player does NOT have a light source");
                    stopAndLog();
                }

                state = getState();
                switch (state) {
                    case WALK_TO_START:
                        handleWalkToStart();
                        break;
                    case CHECK_EMPTY_CAVE:
                        handleCheckEmptyCave();
                        break;
                    case WORLD_HOP:
                        handleWorldHop();
                        break;
                    case ENTER_CAVE:
                        handleEnterCave();
                        break;
                    case WALK_TO_FIGHT_SPOT:
                        handleWalkToFightSpot();
                        break;
                    case FIGHT:
                        handleFight();
                        break;
                    case RESET_AGGRO:
                        handleAggroReset();
                        break;
                    case BANK:
                        handleBanking();
                        break;
                    case STOP:
                        stopAndLog();
                        break;
                    case IDLE:
                        Microbot.log("Idle...");
                        sleep(Rs2Random.between(300, 800));
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Error in MM Caves script: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private State getState() {
        if (mode == Mode.RANGE) {
            boolean wearingChin = Rs2Equipment.items().stream()
                    .anyMatch(name -> name.getName().toLowerCase().contains("chinchompa"));
            if (!wearingChin) {
                Microbot.log("Player does not have any chinchompa's equipped");
                return State.STOP;
            }
        }

        if (mode == Mode.MAGIC) {
            if (
                    !Rs2Magic.hasRequiredRunes(config.magicSpell().getSpell()) &&
                    !Rs2Magic.isSpellbook(Rs2Spellbook.ANCIENT)
            ) {
                Microbot.log("Player does not have enough runes to cast selected spell: " + config.magicSpell().getSpell().getName());

                Map<Runes, Integer> missingRunes = Rs2Magic.getMissingRunes(config.magicSpell().getSpell());
                if (!missingRunes.isEmpty()) {
                    missingRunes.forEach((rune, amount) ->
                            Microbot.log("Missing " + amount + " x " + rune.name()));
                }

                return State.STOP;
            }
            if (config.shouldAutoCast()) {
                if (Rs2Magic.getCurrentAutoCastSpell() != config.magicSpell().getSpell()) {
                    Rs2Combat.setAutoCastSpell(config.magicSpell().getSpell(), false);
                    sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == config.magicSpell().getSpell(), 5000);
                }
            }
        }

        // We need to stop the script if the inventory has no prayer potions
        if (!Rs2Inventory.all().stream().anyMatch(name -> name.getName().toLowerCase().contains("prayer potion"))) return State.STOP;

        // If not in the cave and far from starting tile -> walk to starting tile
        if (
                !isDownstairs() &&
                plugin.getMyWorldPoint().distanceTo(START_TILE) > 5
        ) return State.WALK_TO_START;

        // If close to starter tile and world is not checked -> check if cave is empty
        if (
                plugin.getMyWorldPoint().distanceTo(START_TILE) < 5 &&
                !plugin.isWorldChecked(Microbot.getClient().getWorld()) &&
                !isDownstairs()
        ) return State.CHECK_EMPTY_CAVE;

        //  If close to starter tile and world is checked but is not empty and is not downstairs -> world hop
        if (
                plugin.getMyWorldPoint().distanceTo(START_TILE) < 5 &&
                plugin.isWorldChecked(Microbot.getClient().getWorld()) &&
                !caveIsEmpty &&
                !isDownstairs()
        ) return State.WORLD_HOP;

        //  If close to starter tile and world is checked and is empty and is not downstairs -> enter cave
        if (
                plugin.getMyWorldPoint().distanceTo(START_TILE) < 5 &&
                plugin.isWorldChecked(Microbot.getClient().getWorld()) &&
                caveIsEmpty &&
                !isDownstairs()
        ) return State.ENTER_CAVE;

        // If downstairs but not close to fighting spot -> walk to tile A
        if (
                isDownstairs() &&
                plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A ) > 5 &&
                plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_B ) > 5 &&
                !resettingAggro
        ) return State.WALK_TO_FIGHT_SPOT;

        // Lost aggro
        if (
                firstFightStarted &&
                System.currentTimeMillis() - lastAggroResetTime > AGGRO_RESET_COOLDOWN
        ) {
            resettingAggro = true;
            lastAggroResetTime = System.currentTimeMillis();
            return State.RESET_AGGRO;
        }

        return State.FIGHT;
    }

    private void handleWalkToStart() {
        Microbot.log("Walking to start tile");
        Rs2Walker.walkTo(START_TILE, 0);
        sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(START_TILE) < 5, 1000);
    }

    private void handleCheckEmptyCave() {
        Microbot.log("Checking if cave is empty");
        GroundObject hole = Rs2GameObject.getGroundObject(28772);
        if (hole != null) {
            Rs2GameObject.interact(hole.getWorldLocation(), "Look-in");
            sleepUntil(() -> caveIsEmpty, 3000);
        }
    }

    private void handleWorldHop() {
        Microbot.log("Need to world hop");
        int nextWorld = -1;

        while (true) {
            int candidate = Login.getRandomWorld(true, null);
            if (!plugin.isWorldChecked(candidate)) {
                nextWorld = candidate;
                break; // exit the loop once we find a suitable world
            }
        }

        boolean isHopped = Microbot.hopToWorld(nextWorld);
        if (!isHopped) return;
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
    }

    private void handleEnterCave() {
        Microbot.log("Entering cave...");
        GroundObject hole = Rs2GameObject.getGroundObject(28772);
        if (hole != null) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);

            Rs2GameObject.interact(hole.getWorldLocation(), "Enter");
            sleepUntil(() -> caveIsEmpty, 3000);
        }
    }

    private void handleWalkToFightSpot() {
        Microbot.log("Walking to fight spot...");
        sleep(Rs2Random.between(300, 600));
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        if (plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A) < 8) {
            Microbot.log("Walking Fast Canvas");
            Rs2Walker.walkFastCanvas(FIGHTING_TILE_A, true);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A) < 0, 1000);
        } else {
            Rs2Walker.walkTo(FIGHTING_TILE_A, 0);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(FIGHTING_TILE_A) < 8, 1000);
        }
    }

    private void handleFight() {
        // Initialize timer when fight starts for the first time
        if (!firstFightStarted) {
            Microbot.log("Starting fight: initializing aggro reset timer");
            lastAggroResetTime = System.currentTimeMillis();
            firstFightStarted = true;
        }

        // Manage prayer and inventory
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        Rs2Player.drinkPrayerPotion();
        Rs2Inventory.dropAll("Vial");

        if (Rs2Inventory.emptySlotCount() > 0) {
            Table<WorldPoint, Integer, GroundItem> groundItems = Rs2GroundItem.getGroundItems();
            for (Table.Cell<WorldPoint, Integer, GroundItem> cell : groundItems.cellSet()) {
                GroundItem item = cell.getValue();
                if (item != null) {
                    if (item.getId() == 143) {
                        Microbot.log("Picking up potion from the ground");
                        int previousAmount = Rs2Inventory.count(143);
                        Rs2GroundItem.pickup(143);
                        sleepUntil(() -> Rs2Inventory.hasItemAmount(143, previousAmount+1), 3000);
                    }
                }
            }
        }

        // This is not efficient and needs improvement but sufficient for first release
        // If not it impacts magic a lot due to longer delay
        Optional<Rs2NpcModel> monkey = Rs2Npc.getNpcs("Maniacal monkey")
                .filter(npc -> npc.getWorldLocation().equals(new WorldPoint(2451, 9159, 1)))
                .filter(npc -> !npc.isDead())
                .findFirst();
        Rs2NpcModel target = monkey.orElse(null);

        boolean attacked = attemptAttack(target);
        if (attacked) walkBetweenTiles();
    }

    private void handleAggroReset() {
        Microbot.log("Resetting aggro...");
        if (
                plugin.getMyWorldPoint().distanceTo(AGGRO_RESET_TILE) > 2 &&
                resettingAggro
        ) {
            Rs2Walker.walkTo(AGGRO_RESET_TILE, 0);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(AGGRO_RESET_TILE) < 2, 1000);
            if (plugin.getMyWorldPoint().distanceTo(AGGRO_RESET_TILE) < 5) {
                resettingAggro = false;
            }
        }
    }

    private void handleBanking() {
        Microbot.log("BANKING IS NOT SUPPORTED YET, WALKING TO EXIT TILE TO LOG OUT");
        state = State.STOP;
    }

    private void stopAndLog() {
        while (isDownstairs()) {
            Rs2Walker.walkTo(EXIT_TILE, 2);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(EXIT_TILE) < 3, 1000);

            if ( plugin.getMyWorldPoint().distanceTo(EXIT_TILE) < 3) {
                GameObject rope = Rs2GameObject.getGameObject(28775);
                if (rope != null) {
                    Rs2GameObject.interact(rope, "Climb-up");
                    sleepUntil(() -> plugin.getMyWorldPoint().getRegionID() == cavesUpstairs, 5000);
                }
            }
        }

        Rs2Player.logout();
        Plugin PlayerAssistPlugin = Microbot.getPlugin(MmCavesPlugin.class.getName());
        Microbot.stopPlugin(PlayerAssistPlugin);
    }

    private boolean isDownstairs() {
        int tunnel = 9615;
        int openArea = 9871;

        int region = plugin.getMyWorldPoint().getRegionID();
        return region == tunnel || region == openArea;
    }

    private void walkBetweenTiles()
    {
        WorldPoint targetTile = onTileA ? FIGHTING_TILE_A : FIGHTING_TILE_B;

        int runEnergy = Rs2Player.getRunEnergy();
        if (runEnergy <= 0) {
            return;
        }

        if (!Objects.equals(Rs2Player.getWorldLocation(), targetTile)) {
            Rs2Walker.walkFastCanvas(targetTile, true);
            sleepUntil(() -> plugin.getMyWorldPoint().distanceTo(targetTile) == 0, 1200);
        }
        onTileA = !onTileA;
    }

    private boolean attemptAttack(Rs2NpcModel target) {
        if (target == null) return false;

        long now = System.currentTimeMillis();
        long timeSinceLastAttack = now - lastAttackTime;
        double elapsedSeconds = timeSinceLastAttack / 1000.0;

        boolean attacked;
        int requiredDelay;

        if (config.useCustomDelay()) requiredDelay = config.customAttackDelay();
        else if (mode.equals(Mode.MAGIC)) requiredDelay = 3000;
        else if (mode.equals(Mode.RANGE)) requiredDelay = 1800;
        else requiredDelay = 3000;

        long waitTime = requiredDelay - timeSinceLastAttack;
        if (waitTime > 0) {
            Microbot.log(String.format(
                    "Waiting %.2fs before next attack (elapsed=%.2fs, required=%.2fs).",
                    waitTime / 1000.0,
                    timeSinceLastAttack / 1000.0,
                    requiredDelay / 1000.0
            ));
            sleep((int) waitTime);
            now = System.currentTimeMillis(); // refresh timestamp after sleeping
            timeSinceLastAttack = now - lastAttackTime;
            elapsedSeconds = timeSinceLastAttack / 1000.0;
        }

        if (config.useCustomDelay() && (now - lastAttackTime < config.customAttackDelay())) {
            Microbot.log(String.format(
                    "Too soon to attack again. Elapsed=%.2fs, required=%.2fs.",
                    elapsedSeconds, requiredDelay / 1000.0
            ));
            return false;
        }

        // Mode specific delays if not using custom delay config
         if (!config.useCustomDelay() && ((now - lastAttackTime < 1800) && mode.equals(Mode.RANGE))) {
             Microbot.log(String.format(
                     "Too soon to attack again. Elapsed=%.2fs, required=%.2fs.",
                     elapsedSeconds, requiredDelay / 1000.0
             ));
             return false;
         }

         if (!config.useCustomDelay() && ((now - lastAttackTime < 3000) && mode.equals(Mode.MAGIC))) {
             Microbot.log(String.format(
                     "Too soon to attack again. Elapsed=%.2fs, required=%.2fs.",
                     elapsedSeconds, requiredDelay / 1000.0
             ));
             return false;
         }

        if (!config.shouldAutoCast()) {
            attacked = Rs2Magic.castOn(config.magicSpell().getSpell(), target);
        } else {
            attacked = Rs2Npc.interact(target, "Attack");
        }

        if (attacked) {
            Microbot.log(String.format(
                    "Attacked. Elapsed=%.2fs",
                    elapsedSeconds
            ));
            lastAttackTime = now;
            sleepUntil(Rs2Player::isInteracting, 600);
        } else {
            Microbot.log("Attack failed on: " + target.getName());
        }

        return attacked;
    }
}
