package net.runelite.client.plugins.microbot.farmtreerun;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Rs2Leprechaun;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.farmtreerun.enums.CompostType;
import net.runelite.client.plugins.microbot.farmtreerun.enums.HardTreeEnums;
import net.runelite.client.plugins.microbot.farmtreerun.enums.FruitTreeEnum;
import net.runelite.client.plugins.microbot.farmtreerun.enums.TreeEnums;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;


/**
 * There are two tree types:
 * 1. 'Tree' refers to regular trees (e.g., maple, yew).
 * 2. 'Fruit Tree' refers to fruit trees (e.g., apple, banana).
 */
public class FarmTreeRunScript extends Script {
    public static net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState botStatus;
    public static boolean test = false;
    public static Integer compostItemId = null;
    private List<FarmingItem> items = new ArrayList<>();
    private final FarmTreeRunPlugin plugin;
    private final FarmTreeRunConfig config;

    private enum TreeKind {
        FRUIT_TREE,
        TREE,
        HARD_TREE
    }

    private enum PaymentKind {
        PROTECT,
        CLEAR
    }


    @Inject
    public FarmTreeRunScript(FarmTreeRunPlugin plugin, FarmTreeRunConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Patch {
        GNOME_STRONGHOLD_FRUIT_TREE_PATCH(7962, new WorldPoint(2473, 3446, 0), TreeKind.FRUIT_TREE, 1, 0),
        GNOME_STRONGHOLD_TREE_PATCH(19147, new WorldPoint(2437, 3417, 0), TreeKind.TREE, 1, 0),
        TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH(7963, new WorldPoint(2490, 3181, 0), TreeKind.FRUIT_TREE, 1, 0),
        FARMING_GUILD_TREE_PATCH(33732, new WorldPoint(1234, 3734, 0), TreeKind.TREE, 65, 0),
        FARMING_GUILD_FRUIT_TREE_PATCH(34007, new WorldPoint(1244, 3757, 0), TreeKind.FRUIT_TREE, 85, 0),
        TAVERLEY_TREE_PATCH(8388, new WorldPoint(2936, 3440, 0), TreeKind.TREE, 1, 0),
        FALADOR_TREE_PATCH(8389, new WorldPoint(3001, 3374, 0), TreeKind.TREE, 1, 0),
        LUMBRIDGE_TREE_PATCH(8391, new WorldPoint(3195, 3228, 0), TreeKind.TREE, 1, 0),
        VARROCK_TREE_PATCH(8390, new WorldPoint(3226, 3458, 0), TreeKind.TREE, 1, 0),
        BRIMHAVEN_FRUIT_TREE_PATCH(7964, new WorldPoint(2765, 3213, 0), TreeKind.FRUIT_TREE, 1, 0),
        CATHERBY_FRUIT_TREE_PATCH(7965, new WorldPoint(2858, 3432, 0), TreeKind.FRUIT_TREE, 1, 0),
        LLETYA_FRUIT_TREE_PATCH(26579, new WorldPoint(2345, 3163, 0), TreeKind.FRUIT_TREE, 1, 0),
        FOSSIL_TREE_PATCH_A(30482, new WorldPoint(3718, 3835, 0), TreeKind.HARD_TREE, 1, 0),
        FOSSIL_TREE_PATCH_B(30480, new WorldPoint(3709, 3836, 0), TreeKind.HARD_TREE, 1, 0),
        FOSSIL_TREE_PATCH_C(30481, new WorldPoint(3701, 3840, 0), TreeKind.HARD_TREE, 1, 0),
        AUBURNVALE_TREE_PATCH(56953, new WorldPoint(1365, 3320, 0), TreeKind.TREE, 1, 0),
        KASTORI_FRUIT_TREE_PATCH(56955, new WorldPoint(1349, 3058, 0), TreeKind.FRUIT_TREE, 1, 12765),
        PRIFFDDINAS_CRYSTAL_TREE_PATCH(34906, new WorldPoint(3291, 6117, 0), TreeKind.TREE, 74, 0),
        AVIUM_SAVANNAH_HARDWOOD_PATCH(50692, new WorldPoint(1684, 2974, 0), TreeKind.HARD_TREE,1,0),
        ANGLERS_RETREAT_HARDWOOD_PATCH(58834, new WorldPoint(2472, 2705, 0), TreeKind.HARD_TREE,1,0);

        private final int id;
        private final WorldPoint location;
        private final TreeKind kind;
        private final int farmingLevel;
        private final int leprechaunId;

        public boolean hasRequiredLevel() {
            if (Rs2Player.getSkillRequirement(Skill.FARMING, this.farmingLevel))
                return true;
            Microbot.showMessage(this.name() + " requires level " + this.farmingLevel + " farming.");
            return false;
        }
    }

    public boolean run(FarmTreeRunConfig config) {
        preferHouseTravel = config.preferHouseTravel();
        houseAttemptTarget = null;
        Microbot.log("[FarmTreeRun] Starting diagnostic build; banking=" + config.banking());
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);

        botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.BANKING;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    reportLoopStatus("Waiting for login");
                    return;
                }
                if (!super.run()) {
                    reportLoopStatus("Blocked by client guard; paused=" + Microbot.pauseAllScripts.get()
                            + " interrupted=" + Thread.currentThread().isInterrupted());
                    return;
                }

                long startTime = System.currentTimeMillis();
                if (Rs2AntibanSettings.actionCooldownActive) {
                    reportLoopStatus("Waiting for action cooldown");
                    return;
                }
                reportLoopStatus("Running state=" + botStatus);
                if(!config.travelTablets() && !config.preferHouseTravel() && !Rs2Magic.isSpellbook(Rs2Spellbook.MODERN)){
                    Microbot.log("Not on modern spell book");
                    shutdown();
                    return;
                }
                calculatePatches(config);
                checkSaplingLevelRequirement(config);
                if (!validateSpecialPatches(config)) return;

                dropCrap();
                Patch patch = null;
                boolean handledPatch = false;

				switch (botStatus) {
					case BANKING:
						if (config.banking()) {
							bank(config);
						} else {
							if (isCompostEnabled(config)) {
								compostItemId = config.compostType().getItemId();
							}
							botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_GNOME_STRONGHOLD_FRUIT_PATCH;
						}
						break;
					case HANDLE_GNOME_STRONGHOLD_FRUIT_PATCH:
						patch = Patch.GNOME_STRONGHOLD_FRUIT_TREE_PATCH;
						if (config.enableFruitTrees() && config.gnomeStrongholdFruitTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_GNOME_STRONGHOLD_TREE_PATCH;
						break;
					case HANDLE_GNOME_STRONGHOLD_TREE_PATCH:
						patch = Patch.GNOME_STRONGHOLD_TREE_PATCH;
						if (config.enableTrees() && config.gnomeStrongholdTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH;
						break;
					case HANDLE_FARMING_GUILD_TREE_PATCH:
						patch = Patch.FARMING_GUILD_TREE_PATCH;
						if (config.enableTrees() && config.farmingGuildTreePatch() && patch.hasRequiredLevel()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_FARMING_GUILD_FRUIT_PATCH;
						break;
					case HANDLE_FARMING_GUILD_FRUIT_PATCH:
						patch = Patch.FARMING_GUILD_FRUIT_TREE_PATCH;
						if (config.enableFruitTrees() && config.farmingGuildFruitTreePatch() && patch.hasRequiredLevel()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_TAVERLEY_TREE_PATCH;
						break;
					case HANDLE_BRIMHAVEN_FRUIT_TREE_PATCH:
						patch = Patch.BRIMHAVEN_FRUIT_TREE_PATCH;
						if (config.enableFruitTrees() && config.brimhavenFruitTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_CATHERBY_FRUIT_TREE_PATCH;
						break;
					case HANDLE_TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH:
						patch = Patch.TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH;
						if (config.enableFruitTrees() && config.treeGnomeVillageFruitTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_FARMING_GUILD_TREE_PATCH;
						break;
					case HANDLE_TAVERLEY_TREE_PATCH:
						patch = Patch.TAVERLEY_TREE_PATCH;
						if (config.enableTrees() && config.taverleyTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch) return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_FALADOR_TREE_PATCH;
						break;
					case HANDLE_FALADOR_TREE_PATCH:
						patch = Patch.FALADOR_TREE_PATCH;
						if (config.enableTrees() && config.faladorTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch) return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_LUMBRIDGE_TREE_PATCH;
						break;
					case HANDLE_LUMBRIDGE_TREE_PATCH:
						patch = Patch.LUMBRIDGE_TREE_PATCH;
						if (config.enableTrees() && config.lumbridgeTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_VARROCK_TREE_PATCH;
						break;
					case HANDLE_VARROCK_TREE_PATCH:
						patch = Patch.VARROCK_TREE_PATCH;
						if (config.enableTrees() && config.varrockTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_BRIMHAVEN_FRUIT_TREE_PATCH;
						break;
					case HANDLE_CATHERBY_FRUIT_TREE_PATCH:
						patch = Patch.CATHERBY_FRUIT_TREE_PATCH;
						if (config.enableFruitTrees() && config.catherbyFruitTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_FOSSIL_TREE_PATCH_A;
						break;
					case HANDLE_FOSSIL_TREE_PATCH_A:
						patch = Patch.FOSSIL_TREE_PATCH_A;
						if (config.enableHardTrees() && config.fossilTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_FOSSIL_TREE_PATCH_B;
						break;
					case HANDLE_FOSSIL_TREE_PATCH_B:
						patch = Patch.FOSSIL_TREE_PATCH_B;
						if (config.enableHardTrees() && config.fossilTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_FOSSIL_TREE_PATCH_C;
						break;
					case HANDLE_FOSSIL_TREE_PATCH_C:
						patch = Patch.FOSSIL_TREE_PATCH_C;
						if (config.enableHardTrees() && config.fossilTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_LLETYA_FRUIT_TREE_PATCH;
						break;
					case HANDLE_LLETYA_FRUIT_TREE_PATCH:
						patch = Patch.LLETYA_FRUIT_TREE_PATCH;
						if (config.enableFruitTrees() && config.lletyaFruitTreePatch()) {
							if (walkToLocation(patch.getLocation())) {
								handledPatch = handlePatch(config, patch);
							}
							if (!handledPatch)
								return;
						}
						botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_AUBURNVALE_TREE_PATCH;
						break;

                    case HANDLE_AUBURNVALE_TREE_PATCH: {
                        patch = Patch.AUBURNVALE_TREE_PATCH;
                        if (config.enableTrees() && config.auburnTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch) return;  // stay in this state until done
                        }
                        botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_KASTORI_FRUIT_TREE_PATCH;
                        break;
                    }
                    case HANDLE_KASTORI_FRUIT_TREE_PATCH: {
                        patch = Patch.KASTORI_FRUIT_TREE_PATCH;
                        if (config.enableFruitTrees() && config.kastoriFruitTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch) return;
                        }
                        botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_PRIFFDDINAS_CRYSTAL_TREE_PATCH;
                        break;
                    }
                    case HANDLE_PRIFFDDINAS_CRYSTAL_TREE_PATCH: {
                        patch = Patch.PRIFFDDINAS_CRYSTAL_TREE_PATCH;
                        if (config.enableTrees() && config.priffddinasCrystalTreePatch() && patch.hasRequiredLevel()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch) return;
                        }
                        botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_AVIUM_SAVANNAH_HARDWOOD_PATCH;
                        break;
                    }

                    case HANDLE_AVIUM_SAVANNAH_HARDWOOD_PATCH: {
                        patch = Patch.AVIUM_SAVANNAH_HARDWOOD_PATCH;
                        if (config.enableHardTrees() && config.aviumSavannahHardwoodPatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch) return;
                        }
                        botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.FINISHED;
                        break;
                    }


                    case HANDLE_ANGLERS_RETREAT_HARDWOOD_PATCH:
                        patch = Patch.ANGLERS_RETREAT_HARDWOOD_PATCH;
                        if (config.enableHardTrees() && config.anglersRetreatHardwoodPatch()) {
                            if (!walkToLocation(patch.getLocation())) return;
                            if (!handlePatch(config, patch)) return;
                        }
                        botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_ANGLERS_RETREAT_HARDWOOD_PATCH;
                        break;

                    case FINISHED:
						if (!Rs2Bank.isOpen()) {
							if (!Rs2Bank.walkToBank()) return;
							if (!Rs2Bank.openBank()) return;
						}
						Rs2Bank.depositAll();
						sleepUntil(() -> Rs2Inventory.isEmpty(), 3000);
						Rs2Bank.closeBank();
						Microbot.getClientThread().runOnClientThreadOptional(() -> {
								Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Tree run completed.", "Acun", false);
								Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Made with love by Acun.", "Acun", false);
								return null;
							}
						);
						shutdown();
						break;
				}

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                if (isInterruption(ex)) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void calculatePatches(FarmTreeRunConfig config) {
        if (getSelectedTreePatches(config).isEmpty() && getSelectedFruitTreePatches(config).isEmpty() && getSelectedHardTreePatches(config).isEmpty()) {
            Microbot.showMessage("You must select at least one patch. Shut down.");
            shutdown();

        }
    }

    private boolean validateSpecialPatches(FarmTreeRunConfig config) {
        if (!getSelectedHardTreePatches(config).isEmpty()) {
            String missing = null;
            if (Rs2Player.getRealSkillLevel(Skill.FARMING) < config.selectedHardTree().getFarmingLevel()) {
                missing = config.selectedHardTree().getFarmingLevel() + " Farming for " + config.selectedHardTree();
            } else if (config.fossilTreePatch() && Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED) {
                missing = "Bone Voyage for Fossil Island";
            } else if (config.aviumSavannahHardwoodPatch() && Rs2Player.getQuestState(Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE) != QuestState.FINISHED) {
                missing = "The Ribbiting Tale of a Lily Pad Labour Dispute for Locus Oasis";
            } else if (config.anglersRetreatHardwoodPatch() && Rs2Player.getRealSkillLevel(Skill.SAILING) < 51) {
                missing = "51 Sailing for Anglers' Retreat";
            }
            if (missing != null) {
                Microbot.showMessage("Requires " + missing + ". Change the hardwood selection or disable the patch before starting.");
                shutdown();
                return false;
            }
        }
        if (config.enableTrees() && config.priffddinasCrystalTreePatch()) {
            int farmingLevel = Rs2Player.getRealSkillLevel(Skill.FARMING);
            if (farmingLevel < 74) {
                Microbot.showMessage("Prifddinas Crystal tree requires 74 Farming (you have " + farmingLevel + "). Disable the Prifddinas patch or train Farming before starting. Shutting down.");
                shutdown();
                return false;
            }
            if (Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) != QuestState.FINISHED) {
                Microbot.showMessage("Prifddinas Crystal tree requires Song of the Elves to be completed. Disable the Prifddinas patch before starting. Shutting down.");
                shutdown();
                return false;
            }
        }
        return true;
    }

    private void checkSaplingLevelRequirement(FarmTreeRunConfig config) {
        if (!getSelectedTreePatches(config).isEmpty())
            config.selectedTree().hasRequiredLevel();

        if (!getSelectedFruitTreePatches(config).isEmpty())
            config.selectedFruitTree().hasRequiredLevel();
    }

    private void dropCrap() {
        int[] junk = {ItemID.EMPTY_PLANT_POT, ItemID.BUCKET};
        for (int id : junk) {
            if (Rs2Inventory.hasItem(id)) {
                Rs2Inventory.dropAll(id);
                sleepUntil(() -> !Rs2Inventory.hasItem(id), 8000);
            }
        }
    }

    private boolean walkToLocation(WorldPoint location) {
        if (!canContinueInteraction()) return false;
        if (!isNearPatch(Rs2Player.getWorldLocation(), location)) {
            tryPreferredHouseTravel(location);
            if (!canContinueInteraction()) return false;
            reportLoopStatus("Walking to patch=" + location);
            if (!Rs2Walker.walkTo(location, 3)) return false;
        }
        return canContinueInteraction() && isNearPatch(Rs2Player.getWorldLocation(), location);
    }

    private static boolean isNearPatch(WorldPoint player, WorldPoint patch) {
        // Route length can be zero for a missing route or short for a teleport.
        // Neither means the player is physically close enough to interact.
        return player != null && patch != null && player.getPlane() == patch.getPlane()
                && player.distanceTo(patch) < 16;
    }

    private boolean preferHouseTravel;
    private WorldPoint houseAttemptTarget;

    private void tryPreferredHouseTravel(WorldPoint target) {
        if (!preferHouseTravel || target.equals(houseAttemptTarget)) return;
        houseAttemptTarget = target;
        if (!Rs2Inventory.hasItem(net.runelite.api.ItemID.TELEPORT_TO_HOUSE)) return;
        var pathConfig = net.runelite.client.plugins.microbot.util.walker.Rs2PathApi.getPathfinderConfig();
        if (pathConfig == null) return;
        var saved = net.runelite.client.plugins.microbot.shortestpath.PohPanel.getAvailableTransports(pathConfig.getAllTransports());
        if (saved == pathConfig.getAllTransports()) return;
        Set<WorldPoint> houseOrigins = new HashSet<>();
        for (var transports : saved.values()) for (var transport : transports) {
            if (transport instanceof net.runelite.client.plugins.microbot.util.poh.PohTransport)
                houseOrigins.add(transport.getOrigin());
        }
        net.runelite.client.plugins.microbot.shortestpath.Transport best = null;
        int bestScore = Integer.MAX_VALUE;
        for (var transports : saved.values()) for (var transport : transports) {
            boolean nexus = transport instanceof net.runelite.client.plugins.microbot.util.poh.PohTransport
                    && ((net.runelite.client.plugins.microbot.util.poh.PohTransport) transport).getTeleport()
                    instanceof net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
            boolean ring = transport.getType() == net.runelite.client.plugins.microbot.shortestpath.TransportType.FAIRY_RING
                    && houseOrigins.contains(transport.getOrigin());
            if (!nexus && !ring) continue;
            int distance = transport.getDestination().distanceTo(target);
            if (distance > 120) continue;
            int score = distance + (nexus ? 0 : 1000);
            if (score < bestScore) { best = transport; bestScore = score; }
        }
        if (best == null) return;
        Microbot.log("[FarmTreeRun] Preferred house exit=" + best.getDestination());
        if (!net.runelite.client.plugins.microbot.util.poh.PohTeleports.isInHouse()) {
            if (!Rs2Inventory.interact(net.runelite.api.ItemID.TELEPORT_TO_HOUSE, "Break")) return;
            sleepUntil(net.runelite.client.plugins.microbot.util.poh.PohTeleports::isInHouse, 8000);
        }
        if (!canContinueInteraction() || !net.runelite.client.plugins.microbot.util.poh.PohTeleports.isInHouse()) return;
        if (best instanceof net.runelite.client.plugins.microbot.util.poh.PohTransport) {
            ((net.runelite.client.plugins.microbot.util.poh.PohTransport) best).execute();
        } else {
            Rs2Walker.walkTo(best.getDestination(), 3);
        }
    }

    private boolean canContinueInteraction() {
        boolean ready = !Thread.currentThread().isInterrupted() && isRunning() && Microbot.isLoggedIn();
        if (!ready) reportLoopStatus("Interaction blocked; running=" + isRunning()
                + " interrupted=" + Thread.currentThread().isInterrupted());
        return ready;
    }

    private String lastLoopStatus;
    private long lastLoopStatusTime;

    private void reportLoopStatus(String status) {
        long now = System.currentTimeMillis();
        if (!status.equals(lastLoopStatus) || now - lastLoopStatusTime >= 15000) {
            Microbot.log("[FarmTreeRun] " + status);
            lastLoopStatus = status;
            lastLoopStatusTime = now;
        }
    }

    private static boolean isInterruption(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException) return true;
            if (cause == cause.getCause()) break;
        }
        return false;
    }

    private void bank(FarmTreeRunConfig config) {
        items.clear();
        if (Rs2Bank.openBank() || Rs2Bank.walkToBank()) {
            sleepUntil(Rs2Bank::isOpen, 5000);
            if (!Rs2Bank.isOpen())
                return;
            sleep(600, 2200);

            if (config.useGraceful() && !alreadyWearingGraceful() && Rs2Equipment.isWearing()) {
                Rs2Bank.depositEquipment();
                sleepUntil(() -> !Rs2Equipment.isWearing());
                sleep(500, 2200);
            }

            if (config.useGraceful())
                equipGraceful();


            // Add must have items
            items.add(new FarmingItem(ItemID.COINS_995, 10000));
            items.add(new FarmingItem(ItemID.SPADE, 1));
            items.add(new FarmingItem(ItemID.RAKE, 1));

            if(config.useEnergyPotion()) {
                if (Rs2Bank.hasItem(ItemID.ENERGY_POTION4)) {
                    items.add(new FarmingItem(ItemID.ENERGY_POTION4, 1));
                } else if (Rs2Bank.hasItem(ItemID.ENERGY_POTION3)) {
                    items.add(new FarmingItem(ItemID.ENERGY_POTION3, 1));
                } else if (Rs2Bank.hasItem(ItemID.ENERGY_POTION2)) {
                    items.add(new FarmingItem(ItemID.ENERGY_POTION2, 1));
                } else if (Rs2Bank.hasItem(ItemID.ENERGY_POTION1)) {
                    items.add(new FarmingItem(ItemID.ENERGY_POTION1, 2));
                }
            }

            if (isCompostEnabled(config)) {
                CompostType compostType = config.compostType();
                compostItemId = compostType.getItemId();
                if (compostType.isReusable()) {
                    if (Rs2Bank.hasItem(compostItemId)) {
                        items.add(new FarmingItem(compostItemId, 1));
                    } else {
                        Microbot.log("Bottomless compost bucket not found in bank. Skipping composting.");
                        compostItemId = null;
                    }
                }
            }

            if(config.enableHardTrees() && config.fossilTreePatch()){
                if (Rs2Bank.hasItem(ItemID.DIGSITE_PENDANT_5)) {
                    items.add(new FarmingItem(ItemID.DIGSITE_PENDANT_5, 1));
                } else if (Rs2Bank.hasItem(ItemID.DIGSITE_PENDANT_4)) {
                    items.add(new FarmingItem(ItemID.DIGSITE_PENDANT_4, 1));
                } else if (Rs2Bank.hasItem(ItemID.DIGSITE_PENDANT_3)) {
                    items.add(new FarmingItem(ItemID.DIGSITE_PENDANT_3, 1));
                } else if (Rs2Bank.hasItem(ItemID.DIGSITE_PENDANT_2)) {
                    items.add(new FarmingItem(ItemID.DIGSITE_PENDANT_2, 1));
                } else if (Rs2Bank.hasItem(ItemID.DIGSITE_PENDANT_1)) {
                    items.add(new FarmingItem(ItemID.DIGSITE_PENDANT_1, 1));
                }
            }

            // Construction cape (99 Construction): useful for POH/teleport options
            if (Rs2Player.getRealSkillLevel(Skill.CONSTRUCTION) >= 99) {
                if (Rs2Bank.hasItem(ItemID.CONSTRUCT_CAPET)) {
                    items.add(new FarmingItem(ItemID.CONSTRUCT_CAPET, 1, false, true));
                } else if (Rs2Bank.hasItem(ItemID.CONSTRUCT_CAPE)) {
                    items.add(new FarmingItem(ItemID.CONSTRUCT_CAPE, 1, false, true));
                }
            }

            if (config.useSkillsNecklace() && ((config.enableTrees() && config.farmingGuildTreePatch()) || (config.enableFruitTrees() && config.farmingGuildFruitTreePatch()))) {
                if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE6)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE6, 1, false, true));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE5)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE5, 1, false, true));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE4)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE4, 1, false, true));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE3)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE3, 1, false, true));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE2)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE2, 1, false, true));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE1)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE1, 1, false, true));
                } else {
                    Microbot.log("No skills necklace found in bank. Skipping.");
                }
            }

            TreeEnums selectedTree = config.selectedTree();
            FruitTreeEnum selectedFruitTree = config.selectedFruitTree();
            HardTreeEnums selectedHardTree = config.selectedHardTree();


            int treeSaplingsCount = getSelectedTreePatches(config).size();
            int fruitTreeSaplingsCount = getSelectedFruitTreePatches(config).size();
            int hardTreeSaplingsCount = getSelectedHardTreePatches(config).size();

            // Crystal tree patch uses its own sapling, not the selected regular tree sapling
            boolean priffEnabled = config.priffddinasCrystalTreePatch()
                    && Patch.PRIFFDDINAS_CRYSTAL_TREE_PATCH.hasRequiredLevel();
            int regularTreeSaplingsCount = priffEnabled ? treeSaplingsCount - 1 : treeSaplingsCount;

            if (regularTreeSaplingsCount > 0)
                items.add(new FarmingItem(selectedTree.getSaplingId(), regularTreeSaplingsCount));

            if (priffEnabled)
                items.add(new FarmingItem(ItemID.CRYSTAL_SAPLING, 1));

            if (fruitTreeSaplingsCount > 0)
                items.add(new FarmingItem(selectedFruitTree.getSaplingId(), fruitTreeSaplingsCount));

            if (hardTreeSaplingsCount > 0)
                items.add(new FarmingItem(selectedHardTree.getSaplingId(), hardTreeSaplingsCount));

            if (config.enableTrees() && config.protectTrees() && regularTreeSaplingsCount > 0)
                items.add(new FarmingItem(selectedTree.getPaymentId(), selectedTree.getPaymentAmount() * regularTreeSaplingsCount, true));

            if (config.enableHardTrees() && config.protectHardTrees())
                items.add(new FarmingItem(selectedHardTree.getPaymentId(), selectedHardTree.getPaymentAmount() * hardTreeSaplingsCount, true));

            if (config.enableFruitTrees() && config.protectFruitTrees())
                items.add(new FarmingItem(selectedFruitTree.getPaymentId(), selectedFruitTree.getPaymentAmount() * fruitTreeSaplingsCount, true));

            if (config.enableTrees() && config.taverleyTreePatch())
                items.add(new FarmingItem(ItemID.TAVERLEY_TELEPORT, 1, false, true));

            if (config.enableFruitTrees() && config.lletyaFruitTreePatch()) {
                if (Rs2Bank.hasItem(ItemID.ETERNAL_TELEPORT_CRYSTAL)) {
                    items.add(new FarmingItem(ItemID.ETERNAL_TELEPORT_CRYSTAL, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_1)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_1, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_2)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_2, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_3)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_3, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_4)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_4, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_5)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_5, 1));
                } else {
                    Microbot.showMessage("Would not be able to teleport to Lleyta");
                    shutdown();
                }
            }

            if (config.travelTablets()) {
                addAvailableTravelItem(net.runelite.api.ItemID.VARROCK_TELEPORT, 2);
                addAvailableTravelItem(net.runelite.api.ItemID.LUMBRIDGE_TELEPORT, 1);
                addAvailableTravelItem(net.runelite.api.ItemID.FALADOR_TELEPORT, 2);
                addAvailableTravelItem(net.runelite.api.ItemID.CAMELOT_TELEPORT, 1);
                if (!config.preferHouseTravel()) addAvailableTravelItem(net.runelite.api.ItemID.TELEPORT_TO_HOUSE, 3);
            } else {
            items.add(new FarmingItem(ItemID.LAW_RUNE, 10));
            items.add(new FarmingItem(ItemID.FIRE_RUNE, 30));
            items.add(new FarmingItem(ItemID.AIR_RUNE, 30));
            items.add(new FarmingItem(ItemID.EARTH_RUNE, 30));
            items.add(new FarmingItem(ItemID.WATER_RUNE, 30));
            }
            if (config.travelKourendBook()) {
                addFirstTravelItem(net.runelite.api.ItemID.BOOK_OF_THE_DEAD, net.runelite.api.ItemID.KHAREDSTS_MEMOIRS);
            }
            if (config.preferHouseTravel()) {
                items.add(new FarmingItem(net.runelite.api.ItemID.TELEPORT_TO_HOUSE, 10));
            }
            if (config.travelQuetzal()) {
                addFirstTravelItem(net.runelite.api.ItemID.PERFECTED_QUETZAL_WHISTLEI,
                        net.runelite.api.ItemID.PERFECTED_QUETZAL_WHISTLE,
                        net.runelite.api.ItemID.ENHANCED_QUETZAL_WHISTLE,
                        net.runelite.api.ItemID.BASIC_QUETZAL_WHISTLE);
            }
            if (config.travelFairyStaff()) {
                addFirstTravelItem(net.runelite.api.ItemID.DRAMEN_STAFF, net.runelite.api.ItemID.LUNAR_STAFF);
            }

//              TODO: Need to handle what happens if a required item does not exist

            // Merge entries with the same itemId + noted flag so withdrawal doesn't under-count
            Map<Long, FarmingItem> merged = new LinkedHashMap<>();
            for (FarmingItem item : items) {
                long key = ((long) item.getItemId() << 1) | (item.isNoted() ? 1 : 0);
                merged.merge(key, item, (a, b) ->
                    new FarmingItem(a.getItemId(), a.getQuantity() + b.getQuantity(), a.isNoted(), a.isOptional()));
            }
            items = new ArrayList<>(merged.values());

            // Deposit only what we don't need: keep desired ids and their noted variants
            Set<Integer> keepIds = new HashSet<>();
            for (FarmingItem item : items) {
                keepIds.add(item.getItemId());
                Integer linked = getLinkedId(item.getItemId());
                if (linked != null) keepIds.add(linked);
            }
            if (!keepIds.isEmpty()) {
                Rs2Bank.depositAllExcept(keepIds.toArray(new Integer[0]));
                Rs2Inventory.waitForInventoryChanges(1500);
            }

            List<FarmingItem> unnotedItems = items.stream()
                    .filter(i -> !i.isNoted())
                    .collect(Collectors.toList());
            List<FarmingItem> notedItems = items.stream()
                    .filter(FarmingItem::isNoted)
                    .collect(Collectors.toList());

            {
                boolean toggled = Rs2Bank.setWithdrawAsItem();
                if (!toggled || !Rs2Bank.hasWithdrawAsItem()) {
                    Microbot.log("Failed to toggle bank to item mode");
                    shutdown();
                    return;
                }
            }
            for (FarmingItem item : new ArrayList<>(unnotedItems)) {
                int itemId = item.getItemId();
                int desiredQty = item.getQuantity();
                int haveQty = Rs2Inventory.itemQuantity(itemId);
                int needQty = Math.max(0, desiredQty - haveQty);
                if (needQty <= 0) continue;
                checkIfPlayerHasItem(itemId, needQty, item.isOptional());
                if (!isRunning()) return;
                if (needQty == 1) Rs2Bank.withdrawOne(itemId); else Rs2Bank.withdrawX(itemId, needQty);
                sleep(250, 1200);
            }

            if (!notedItems.isEmpty()) {
                boolean toggled = Rs2Bank.setWithdrawAsNote();
                if (!toggled || !Rs2Bank.hasWithdrawAsNote()) {
                    Microbot.log("Failed to toggle bank to noted mode");
                    shutdown();
                    return;
                }
                sleep(300, 900);
                for (FarmingItem item : new ArrayList<>(notedItems)) {
                    int itemId = item.getItemId();
                    int desiredQty = item.getQuantity();
                    int haveQty = getInventoryQuantityIncludingLinked(itemId);
                    int needQty = Math.max(0, desiredQty - haveQty);
                    if (needQty <= 0) continue;
                    checkIfPlayerHasItem(itemId, needQty, item.isOptional());
                    if (!isRunning()) return;
                    if (needQty == 1) Rs2Bank.withdrawOne(itemId); else Rs2Bank.withdrawX(itemId, needQty);
                    sleep(250, 1200);
                }
            }

            Rs2Bank.closeBank();
            botStatus = net.runelite.client.plugins.microbot.farmtreerun.enums.FarmTreeRunState.HANDLE_GNOME_STRONGHOLD_FRUIT_PATCH;
        }
    }

    private void checkIfPlayerHasItem(FarmingItem item) {
        if (!Rs2Bank.hasItem(new int[]{item.getItemId()}, item.getQuantity()) && !item.isOptional()) {
            Microbot.showMessage("Not enough items: " + Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(item.getItemId()).getName()) + ". " +
                    "Need " + item.getQuantity() + ". Shut down.");
            shutdown();

        }
    }

    private void checkIfPlayerHasItem(int itemId, int quantity, boolean optional) {
        if (!Rs2Bank.hasItem(new int[]{itemId}, quantity) && !optional) {
            Microbot.showMessage("Not enough items: " + Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(itemId).getName()) + ". Need " + quantity + ". Shut down.");
            shutdown();
        }
    }

    private void addAvailableTravelItem(int id, int quantity) {
        int available = Rs2Inventory.itemQuantity(id) + Rs2Bank.count(id);
        if (available > 0) items.add(new FarmingItem(id, Math.min(quantity, available), false, true));
    }

    private void addFirstTravelItem(int... ids) {
        for (int id : ids) {
            if (Rs2Equipment.isWearing(id)) return;
            if (Rs2Inventory.hasItem(id) || Rs2Bank.hasItem(id)) {
                addAvailableTravelItem(id, 1);
                return;
            }
        }
        Microbot.log("[FarmTreeRun] Optional travel item unavailable; leaving route selection to WebWalker.");
    }

    private boolean handlePatch(FarmTreeRunConfig config, Patch patch) {
        if (!canContinueInteraction()) return false;
        String[] possibleActions = {"Check", "Chop", "Pick", "Rake", "Clear", "Inspect"};
        GameObject treePatch = null;
        String foundAction = null;
        String exactAction = null;

        // Loop through the possible actions and try to find the tree patch with any valid action
        for (String action : possibleActions) {
            if (!canContinueInteraction()) return false;
            treePatch = Rs2GameObject.findObjectByImposter(patch.getId(), action, false);  // Find object by patchId and action
            if (treePatch != null) {
                foundAction = action;
                if (!foundAction.contains("Inspect")) {
                    break;
                }
            }
        }

        if (treePatch == null) {
            return false;
        }

        // Gagex named actions differently, sometimes it's Pick-fruit and sometimes Pick-banana.
        // Also seen "Chop down" and "Chop-down".
        List<String> exactTreeActions = Arrays.stream(Rs2GameObject.findObjectComposition(treePatch.getId()).getActions()).collect(Collectors.toList());
        for (String action : exactTreeActions) {
            if (action == null)
                continue;
            if (action.startsWith(foundAction) || action.equals(foundAction)) {
                exactAction = action;
                break;
            }
        }

        // If no tree patch is, print an error and return
        if (treePatch == null) {
            System.out.println("Tree patch not found with any of the possible actions. Report this in Discord: " + patch.getId());
            return false;
        }

        boolean done = false;
        boolean treePlanted = false;
        boolean protectionHandled = false;

        Microbot.log("[FarmTreeRun] patch=" + patch + " action=" + exactAction);
        // Handle the patch based on the action found
        switch (foundAction) {
            case "Check":
                handleCheckHealth(treePatch);
                break;
            case "Chop":
                handlePayment(config, patch, PaymentKind.CLEAR);
                break;
            case "Pick":
                handlePickingFruit(treePatch, patch, exactAction);
                break;
            case "Rake":
                handleRakeAction(treePatch);
                break;
            case "Clear":
                handleClearAction(treePatch);
                break;
            case "Inspect":
                if (handlePlantingTree(treePatch, patch, config))
                    treePlanted = true;
                if (treePlanted && handlePayment(config, patch, PaymentKind.PROTECT))
                    protectionHandled = true;
                if (treePlanted && protectionHandled)
                    done = true;
                break;
            default:
                System.out.println("Unexpected action found on tree patch: " + foundAction);
                break;
        }
        return done;
    }

    private void handleNotingFruit(Patch patch) {
        // Array of fruit item IDs
        int[] fruitIds = {
                ItemID.COOKING_APPLE,
                ItemID.BANANA,
                ItemID.ORANGE,
                ItemID.CURRY_LEAF,
                ItemID.PINEAPPLE,
                ItemID.PAPAYA_FRUIT,
                ItemID.COCONUT,
                ItemID.DRAGONFRUIT
        };

        if (!Rs2Inventory.hasItem(fruitIds)) return;

        // Iterate through the fruit IDs
        for (int fruitId : fruitIds) {
            if (Rs2Inventory.hasItem(fruitId)) {
                // Interact with the specific fruit found
                Rs2Inventory.useItemOnNpc(fruitId, patch.getLeprechaunId());
                Rs2Inventory.waitForInventoryChanges(3000);
                return; // Return false if any fruit is found and interacted with
            }
        }
    }

    /**
     * Handles tree clearing and protecting payments
     *
     * @param config
     * @return {@code true} if payment was successful, else {@code false}
     */
    private boolean handlePayment(FarmTreeRunConfig config, Patch patch, PaymentKind action) {
        if (isTreePatch(patch) && !isPatchEmpty(patch) && !shouldProtectTree(config) && action != PaymentKind.CLEAR)
            return true;

        if (isFruitTreePatch(patch) && !isPatchEmpty(patch) && !shouldProtectFruitTree(config) && action != PaymentKind.CLEAR)
            return true;

        if (isHardTreePatch(patch) && !isPatchEmpty(patch) && !shouldProtectHardTree(config) && action != PaymentKind.CLEAR)
            return true;

        int paymentId = isHardTreePatch(patch) ? config.selectedHardTree().getPaymentId()
                : isFruitTreePatch(patch) ? config.selectedFruitTree().getPaymentId()
                : config.selectedTree().getPaymentId();
        int before = getInventoryQuantityIncludingLinked(paymentId);
        Rs2NpcModel treeGardener = Rs2Npc.getNearestNpcWithAction("Pay");
        if (treeGardener != null && !Rs2Npc.interact(treeGardener, "Pay")) return false;

        if (treeGardener == null) {
            handleExoticGardeners();
        }

        long deadline = System.currentTimeMillis() + 8000;
        boolean selected = false;
        while (isRunning() && Microbot.isLoggedIn() && System.currentTimeMillis() < deadline) {
            if (action == PaymentKind.CLEAR && isPatchEmpty(patch)) return true;
            if (action == PaymentKind.PROTECT && (getInventoryQuantityIncludingLinked(paymentId) < before
                    || Rs2Dialogue.hasDialogueText("Leave it with me")
                    || Rs2Dialogue.hasDialogueText("already looking after that patch"))) {
                Rs2Dialogue.clickContinue();
                return true;
            }
            if (Rs2Dialogue.hasSelectAnOption()) {
                if (!selected) {
                    selected = action == PaymentKind.PROTECT && Rs2Dialogue.clickOption("don't ask");
                    if (!selected) selected = Rs2Dialogue.clickOption("Yes");
                    if (!selected) break;
                }
            } else if (Rs2Dialogue.isInDialogue()) {
                Rs2Dialogue.clickContinue();
            }
            sleep(200);
        }
        Microbot.log("[FarmTreeRun] Payment not confirmed: patch=" + patch + " type=" + action);
        return false;
    }

    private boolean handlePlantingTree(GameObject treePatch, Patch patch, FarmTreeRunConfig config) {
        // Skip if patch is not empty
        if (!isPatchEmpty(patch))
            return true;

        int saplingToUse = getSaplingToUse(patch, config);

        if (useCompostOnPatch(config, patch)) {
            boolean hasCompost = Rs2Inventory.hasItem(compostItemId);
            if (!hasCompost && !config.compostType().isReusable()) {
                hasCompost = withdrawCompostFromLeprechaun(config.compostType());
                if (!hasCompost) {
                    Microbot.showMessage("Tool Leprechaun has no " + config.compostType() + ". Store compost with the leprechaun before starting.");
                    shutdown();
                    return false;
                }
            }
            if (hasCompost) {
                Rs2Inventory.useItemOnObject(compostItemId, treePatch.getId());
                Rs2Player.waitForXpDrop(Skill.FARMING, 2000);
            }
        }

        if (!Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(patch.id), "Rake")) {
            Rs2Inventory.useItemOnObject(saplingToUse, treePatch.getId());
            sleepUntil(() -> !isPatchEmpty(patch), 5000);
            return !isPatchEmpty(patch);
        }
        Rs2Inventory.deselect();
        return false;
    }

    private void handlePickingFruit(GameObject fruitTreePatch, Patch patch, String exactAction) {
        System.out.println("Picking fruit...");
        if (!Rs2GameObject.interact(fruitTreePatch, exactAction)) return;
        // Wait for the picking to complete (player stops animating and patch no longer has the "Pick" action)
        sleepUntil(() -> !Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(fruitTreePatch.getId()), exactAction), 12000);
        handleNotingFruit(patch);
    }

    private void handleCheckHealth(GameObject treePatch) {
        System.out.println("Checking health...");

        // Rake the patch
        if (!Rs2GameObject.interact(treePatch, "Check-health")) return;
        sleepUntil(() -> !Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(treePatch.getId()), "Check-health"), 5000);
    }

    private void handleRakeAction(GameObject treePatch) {
        System.out.println("Raking the patch...");

        if (!Rs2GameObject.interact(treePatch, "Rake")) return;
        sleepUntil(() -> !Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(treePatch.getId()), "Rake"), 30000);
        sleep(400, 1200);
        if (Rs2Inventory.hasItem(ItemID.WEEDS)) {
            Rs2Inventory.dropAll(ItemID.WEEDS);
            sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.WEEDS), 5000);
        }
    }

    private void handleClearAction(GameObject treePatch) {
        System.out.println("Clearing dead tree...");

        boolean interactionSuccess = Rs2GameObject.interact(treePatch, "clear");
        if (!interactionSuccess) {
            System.out.println("Failed to interact with the tree patch to clear it.");
            return;
        }
        sleepUntil(() -> !Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(treePatch.getId()), "Clear"), 6000);
    }

    private void equipGraceful() {
        checkBeforeWithdrawAndEquip("GRACEFUL GLOVES");
        checkBeforeWithdrawAndEquip("GRACEFUL LEGS");
        checkBeforeWithdrawAndEquip("GRACEFUL CAPE");
        checkBeforeWithdrawAndEquip("GRACEFUL BOOTS");
        checkBeforeWithdrawAndEquip("GRACEFUL HOOD");
        checkBeforeWithdrawAndEquip("GRACEFUL TOP");
    }

    private void checkBeforeWithdrawAndEquip(String itemName) {
        if (!Rs2Equipment.isWearing(itemName)) {
            Rs2Bank.withdrawAndEquip(itemName);
            sleep(500, 1000);
        }
    }

    private boolean alreadyWearingGraceful() {
        return Rs2Equipment.isWearing("GRACEFUL LEGS")
                && Rs2Equipment.isWearing("GRACEFUL TOP")
                && Rs2Equipment.isWearing("GRACEFUL HOOD")
                && Rs2Equipment.isWearing("GRACEFUL BOOTS")
                && Rs2Equipment.isWearing("GRACEFUL GLOVES")
                && Rs2Equipment.isWearing("GRACEFUL CAPE");
    }

    private boolean shouldProtectTree(FarmTreeRunConfig config) {
        return config.protectTrees();
    }
    private boolean shouldProtectHardTree(FarmTreeRunConfig config) {
        return config.protectHardTrees();
    }
    private boolean shouldProtectFruitTree(FarmTreeRunConfig config) {
        return config.protectFruitTrees();
    }

    private boolean isTreePatch(Patch patch) {
        return patch.kind == TreeKind.TREE;
    }

    private boolean isHardTreePatch(Patch patch) {
        return patch.kind == TreeKind.HARD_TREE;
    }

    private boolean isFruitTreePatch(Patch patch) {
        return patch.kind == TreeKind.FRUIT_TREE;
    }

    private boolean withdrawCompostFromLeprechaun(CompostType compostType) {
        return Rs2Leprechaun.withdrawCompost(compostType.getItemId());
    }

    private boolean useCompostOnPatch(FarmTreeRunConfig config, Patch patch) {
        if (config.compostType() == CompostType.NONE || compostItemId == null)
            return false;

        if (!config.protectTrees() && patch.kind == TreeKind.TREE)
            return true;

        if (!config.protectHardTrees() && patch.kind == TreeKind.HARD_TREE)
            return true;

        return !config.protectFruitTrees() && patch.kind == TreeKind.FRUIT_TREE;
    }

    private List<BooleanSupplier> getSelectedTreePatches(FarmTreeRunConfig config) {
        if (!config.enableTrees()) return Collections.emptyList();
        List<BooleanSupplier> allTreePatches = List.of(
                config::faladorTreePatch,
                config::gnomeStrongholdTreePatch,
                config::lumbridgeTreePatch,
                config::taverleyTreePatch,
                config::varrockTreePatch,
                config::farmingGuildTreePatch,
                config::auburnTreePatch,
                config::priffddinasCrystalTreePatch
        );

        // Filter the patches to include only those that return true
        return allTreePatches.stream()
                .filter(BooleanSupplier::getAsBoolean) // Filter patches that return true
                .collect(Collectors.toList()); // Collect into a new list
    }

    private List<BooleanSupplier> getSelectedHardTreePatches(FarmTreeRunConfig config) {
        if (!config.enableHardTrees()) return Collections.emptyList();
        List<BooleanSupplier> allHardTreePatches = List.of(
                config::fossilTreePatch,
                config::fossilTreePatch,
                config::fossilTreePatch,
                config::aviumSavannahHardwoodPatch,
                config::anglersRetreatHardwoodPatch
        );

        // Filter the patches to include only those that return true
        return allHardTreePatches.stream()
                .filter(BooleanSupplier::getAsBoolean) // Filter patches that return true
                .collect(Collectors.toList()); // Collect into a new list
    }

    private List<BooleanSupplier> getSelectedFruitTreePatches(FarmTreeRunConfig config) {
        if (!config.enableFruitTrees()) return Collections.emptyList();
        List<BooleanSupplier> allFruitTreePatches = List.of(
                config::brimhavenFruitTreePatch,
                config::catherbyFruitTreePatch,
                config::farmingGuildFruitTreePatch,
                config::lletyaFruitTreePatch,
                config::gnomeStrongholdFruitTreePatch,
                config::treeGnomeVillageFruitTreePatch,
                config::kastoriFruitTreePatch
        );

        // Filter the patches to include only those that return true
        return allFruitTreePatches.stream()
                .filter(BooleanSupplier::getAsBoolean)
                .collect(Collectors.toList());
    }

    /**
     * Method to check whether player wants to use compost
     *
     * @param config
     * @return true if configured by player, else false
     */
    private boolean isCompostEnabled(FarmTreeRunConfig config) {
        if (config.compostType() == CompostType.NONE)
            return false;

        if (!getSelectedTreePatches(config).isEmpty() && !config.protectTrees())
            return true;

        if (!getSelectedHardTreePatches(config).isEmpty() && !config.protectHardTrees())
            return true;

        return !getSelectedFruitTreePatches(config).isEmpty() && !config.protectFruitTrees();
    }

    private boolean isPatchEmpty(Patch patch) {
        String name = Rs2GameObject.getObjectComposition(patch.getId()).getName().toLowerCase();
        return name.endsWith("patch");
    }

    private Integer getLinkedId(int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemComposition comp = Microbot.getItemManager().getItemComposition(id);
            int linked = comp.getLinkedNoteId();
            return linked > 0 ? linked : null;
        }).orElse(null);
    }

    private int getInventoryQuantityIncludingLinked(int id) {
        int qty = Rs2Inventory.itemQuantity(id);
        Integer linked = getLinkedId(id);
        if (linked != null) qty += Rs2Inventory.itemQuantity(linked);
        return qty;
    }

    

    private static int getSaplingToUse(Patch patch, FarmTreeRunConfig config) {
        if (patch.getKind() == TreeKind.HARD_TREE) {
            return config.selectedHardTree().getSaplingId();

        } else if (patch == Patch.PRIFFDDINAS_CRYSTAL_TREE_PATCH) {
            return ItemID.CRYSTAL_SAPLING;

        } else return patch.kind == TreeKind.TREE ?
                config.selectedTree().getSaplingId() :
                config.selectedFruitTree().getSaplingId();
    }

    /**
     * Handles gardeners at new location because Gagex is not consistent,
     * and they introduce new action for each new gardener.
     * TODO: This method can be replaced/improved if we hardcode each gardener's id inside patch enum.
     * Note that Gagex is also not consistent with action names
     *
     * @return true if gardener interaction successful, else false
     */
    private void handleExoticGardeners() {
        var nikkie = Microbot.getRs2NpcCache().query().withName("Nikkie").nearestOnClientThread();

        var rosie = Microbot.getRs2NpcCache().query().withName("Rosie").nearestOnClientThread();

        String paymentAction = "";
        net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel npcToInteract = null;

        if (rosie == null && nikkie == null) {
            Microbot.log("Gardeners in farming guild not found. Report this bug.");
            shutdown();

        } else if (nikkie != null && Rs2Player.distanceTo(nikkie.getWorldLocation()) <= 10) {
            npcToInteract = nikkie;
            paymentAction = "Pay (Fruit tree)";
        } else if (rosie != null && Rs2Player.distanceTo(rosie.getWorldLocation()) <= 10) {
            npcToInteract = rosie;
            paymentAction = "Pay (tree patch)";
        }

        if (npcToInteract != null) npcToInteract.click(paymentAction);
    }

    @Override
    public void shutdown() {
        if(isRunning()) {
            items.clear();
            super.shutdown();
            Microbot.stopPlugin(plugin);
        }
    }
}

/**
 * Tracks the farming patches from TimeTrackingPlugin.
 * Checks if specified patches are fully grown before going for run.
 * specified in the {@code ikiFarmConfig} object.
 *
 * @param config Configurations for this plugin. It should not be {@code null}.
 * @return {@code true} if all specified farming patches are fully grown and to check health,
 * {@code false} otherwise.
 */
//    private boolean trackFarmingPatches(ikiFarmConfig config) {
/// /        If fruit tree patch -> use compost (Can we use compost on weeded patch???)
/// /        If !weed on patch -> Use sapling on tree patch
/// /        Check if patch contains the planted tree. Can we use Runelite time tracking plugin?
//
//
//        TimeTrackingPlugin timeTrackingPlugin = (TimeTrackingPlugin) Microbot.getPlugin(TimeTrackingPlugin.class.getName());
//
//        FarmingWorld farmingWorld = timeTrackingPlugin.farmingTracker.farmingWorld;
//        WorldPoint location = client.getLocalPlayer().getWorldLocation();
//        Collection<FarmingRegion> newRegions = farmingWorld.getRegionsForLocation(location);
//        for (FarmingRegion region : newRegions) {
//            for (FarmingPatch patch : region.getPatches()) {
//                PatchPrediction prediction = timeTrackingPlugin.farmingTracker.predictPatch(patch);
//                Microbot.log(String.valueOf(prediction.getProduce()));
//                Microbot.log(String.valueOf(prediction.getCropState()));
//                Microbot.log(prediction.getStage() + "/" + prediction.getStages());
//            }
//        }
//
//        return true;
//    }
