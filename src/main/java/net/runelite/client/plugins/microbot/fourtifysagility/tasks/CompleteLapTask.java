package net.runelite.client.plugins.microbot.fourtifysagility.tasks;

import net.runelite.client.plugins.microbot.fourtifysagility.util.task.FourtifysTask;

import java.util.List;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.course.CourseHandoff;
import net.runelite.client.plugins.microbot.fourtifysagility.course.CourseObstacle;
import net.runelite.client.plugins.microbot.fourtifysagility.course.ObstacleObjectType;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;
import net.runelite.client.plugins.microbot.fourtifysagility.util.navigation.CourseMovement;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.Spellbook;
import net.runelite.client.plugins.microbot.fourtifysagility.util.scene.TargetRange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.util.Text;

public final class CompleteLapTask implements FourtifysTask
{
    private static final String SEERS_TELEPORT_FAILED = "Seers teleport failed: it needs the Kandarin hard diary, Camelot"
        + " Teleport set to Seers' Village, 45 Magic and its runes. Turn off Use diary teleport, or turn the plugin off and on"
        + " to try again";
    private final FourtifysAgilityPlugin plugin;
    private final Client client;
    private final AgilityConfig config;
    private final AgilityActions actions;
    private final Spellbook spells;
    private final BrimhavenTask brimhaven;
    private final CourseMovement movement;
    private GameObject prifddinasPortal;
    private int wildernessObstacle = 0;
    private WorldPoint seersReadyPosition;
    private int seersReadyTick;
    private WorldPoint seersCastOrigin;
    private int seersCastTick;
    private boolean seersTeleportFailed;

    public CompleteLapTask(FourtifysAgilityPlugin plugin, Client client, AgilityConfig config,
        AgilityActions actions, Spellbook spells)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.actions = actions;
        this.spells = spells;
        this.brimhaven = new BrimhavenTask(plugin, client, config, actions);
        this.movement = new CourseMovement(client, actions);
    }
    public boolean isValid()
    {
        return true;
    }

    public String getStatus()
    {
        return "Running the course";
    }

    public void resetSeersTeleport()
    {
        brimhaven.reset();
        seersReadyPosition = null;
        seersCastOrigin = null;
        seersTeleportFailed = false;
    }

    public void observeBrimhavenHint()
    {
        if (plugin.getActiveCourse() == AgilityCourse.BRIMHAVEN)
        {
            brimhaven.observeHint();
        }
    }

    /** Drops queued arena and course-start walks, and the walker goal a course-start walk set. */
    public void cancelMovement()
    {
        brimhaven.cancelMovement();
        movement.cancelStep();
    }

    public void seersTeleportMessage(String text)
    {
        brimhaven.message(text);
        if (seersCastOrigin != null && text != null)
        {
            String lower = Text.removeTags(text).toLowerCase(Locale.ROOT);
            if (lower.contains("kandarin") && lower.contains("diary") && lower.contains("complete"))
            {
                seersCastOrigin = null;
                seersTeleportFailed = true;
            }
        }
    }

    public boolean waitingForSeersTeleport(Player player)
    {
        if (seersTeleportFailed)
        {
            plugin.setStatus(SEERS_TELEPORT_FAILED);
            return true;
        }
        if (seersCastOrigin == null)
        {
            return false;
        }
        WorldPoint position = player.getWorldLocation();
        if (position != null && position.getPlane() == 0 && position.getX() >= 2724 && position.getX() <= 2728
            && position.getY() >= 3482 && position.getY() <= 3486)
        {
            seersCastOrigin = null;
            seersReadyPosition = null;
            return false;
        }
        if (client.getTickCount() - seersCastTick >= 10)
        {
            seersCastOrigin = null;
            seersTeleportFailed = true;
            plugin.setStatus(SEERS_TELEPORT_FAILED);
        }
        else
        {
            plugin.setStatus("Waiting for Seers teleport");
        }
        return true;
    }

    private boolean seersReady(Player player, WorldPoint origin)
    {
        return player != null && !player.isDead() && origin.equals(player.getWorldLocation())
            && player.getAnimation() == -1 && player.getPoseAnimation() == player.getIdlePoseAnimation()
            && client.getVarbitValue(12393) == 0 && config.seersTeleport()
            && plugin.getActiveCourse() == AgilityCourse.SEERS_VILLAGE;
    }

    private void teleportToSeers(Player player)
    {
        WorldPoint origin = player.getWorldLocation();
        if (origin == null || !seersReady(player, origin))
        {
            seersReadyPosition = null;
            plugin.setStatus("Waiting for obstacle to finish");
            return;
        }
        if (!origin.equals(seersReadyPosition))
        {
            seersReadyPosition = origin;
            seersReadyTick = client.getTickCount();
            plugin.setStatus("Waiting for obstacle to finish");
            return;
        }
        if (client.getTickCount() - seersReadyTick < 1)
        {
            return;
        }
        spells.camelotTeleport(2,
            () -> seersCastOrigin == null && !seersTeleportFailed && seersReady(client.getLocalPlayer(), origin),
            () -> {
                seersCastOrigin = origin;
                seersCastTick = client.getTickCount();
            },
            () -> {
                seersTeleportFailed = true;
                plugin.setStatus(SEERS_TELEPORT_FAILED);
            });
    }

    public void execute()
    {
        Player player = this.client.getLocalPlayer();
        if (player != null)
        {
            if (this.client.getBoostedSkillLevel(Skill.HITPOINTS) < this.config.minHealth())
            {
                if (this.plugin.getActiveCourse().equals(AgilityCourse.WILDERNESS))
                {
                    this.actions.curePoison();
                    if (Rs2Inventory.contains(24595, 24589, 24592))
                    {
                        if (Rs2Inventory.contains(24595))
                        {
                            this.actions.eat(24595);
                            return;
                        }

                        if (Rs2Inventory.contains(24589))
                        {
                            this.actions.eat(24589);
                            return;
                        }

                        if (Rs2Inventory.contains(24592))
                        {
                            this.actions.eat(24592);
                            return;
                        }
                    }
                }

                if (Rs2Inventory.contains(AgilityActions.foodTiers(this.config.foodId()).stream()
                    .mapToInt(Integer::intValue).toArray()))
                {
                    this.actions.eat(this.config.foodId());
                }
                else
                {
                    this.plugin.setStatus(this.config.foodId() > 0
                        ? "Hitpoints below " + this.config.minHealth() + " and no food (ID " + this.config.foodId() + ") left; add food to continue"
                        : "Hitpoints below " + this.config.minHealth() + "; heal, or set Food item ID under Healing");
                }

                return;
            }

            if (this.plugin.getActiveCourse() == AgilityCourse.BRIMHAVEN)
            {
                brimhaven.execute();
                return;
            }

            if (this.plugin.getActiveCourse().equals(AgilityCourse.AGILITY_PYRAMID) && player.getWorldLocation().getPlane() == 0 && player.getWorldLocation().getX() < 3352)
            {
                GroundObject entrance = this.actions.findNearestGroundObject(11949);
                if (entrance != null)
                {
                    this.actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), entrance);
                    return;
                }
            }

            CourseObstacle start = CourseHandoff.startObstacle(this.plugin.getActiveCourse(), player.getWorldLocation(),
                candidate -> this.actions.obstacleInRange(candidate.type, candidate.id));
            for (CourseObstacle obstacle : CourseObstacle.values())
            {
                if (obstacle.area == this.plugin.getActiveCourse()
                    && (obstacle == start || player.getWorldArea().intersectsWith(obstacle.extendedWorldArea)
                    && player.getWorldLocation().getPlane() == obstacle.extendedWorldArea.getPlane()))
                {
                    int agility = this.client.getBoostedSkillLevel(Skill.AGILITY);
                    // The Wyrm branch and shared zipline also serve the level-50 basic course.
                    int required = (obstacle == CourseObstacle.COLOSSAL_WYRM_THREE && agility < 62)
                        || obstacle == CourseObstacle.COLOSSAL_WYRM_SIX ? 50 : obstacle.level;
                    if (agility < required)
                    {
                        if (Rs2Inventory.contains(7220))
                        {
                            this.actions.eat(7220);
                            return;
                        }

                        if (Rs2Inventory.contains(7218))
                        {
                            this.actions.eat(7218);
                            return;
                        }
                    }

                    if (obstacle.equals(CourseObstacle.SEERS_ZERO) && this.config.seersTeleport() && player.getWorldLocation().getY() < 3476)
                    {
                        this.teleportToSeers(player);
                        return;
                    }

                    if (this.takeMarkOfGrace(obstacle.extendedWorldArea))
                    {
                        return;
                    }

                    if (agility < required)
                    {
                        // A summer pie boosts Agility by 5 from the real level, so it cannot cover a larger gap.
                        this.plugin.setStatus(this.client.getRealSkillLevel(Skill.AGILITY) + 5 >= required
                            ? "Agility below " + required + "; add a summer pie to continue"
                            : "Agility " + required + " needed for this course; choose a lower course");
                        return;
                    }

                    if (obstacle == CourseObstacle.PYRAMID_ZERO
                        && player.getWorldLocation().distanceTo(new WorldPoint(3354, 2831, 0)) > TargetRange.GAME_OBJECT_SEARCH_RADIUS)
                    {
                        this.walkToCourseTile(new WorldPoint(3364, 2830, 0), "the Agility Pyramid start");
                        return;
                    }

                    if (this.enterPrifddinasPortal())
                    {
                        return;
                    }

                    switch (obstacle)
                    {
                        case WILDERNESS_ONE:
                            this.wildernessObstacle = 1;
                            if (this.client.getVarbitValue(10676) == 0)
                            {
                                this.interactObstacleAt(ObstacleObjectType.GAME, 23137, MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), new WorldPoint(3004, 3938, 0));
                            }
                            else
                            {
                                this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            }
                            break;
                        case WILDERNESS_TWO:
                            this.interactObstacleAt(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), new WorldPoint(3004, 3938, 0));
                            break;
                        case WILDERNESS_THREE:
                            this.wildernessObstacle = 3;
                            this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            break;
                        case WILDERNESS_FOUR:
                            this.wildernessObstacle = 4;
                            this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            break;
                        case WILDERNESS_FIVE:
                            this.wildernessObstacle = 5;

                            for (Player otherPlayer : this.client.getTopLevelWorldView().players())
                            {
                                if (otherPlayer != player && otherPlayer.getSkullIcon() != -1 && Math.abs(otherPlayer.getCombatLevel() - player.getCombatLevel()) < 50)
                                {
                                    if (!player.getWorldLocation().equals(new WorldPoint(3001, 3943, 0)))
                                    {
                                        this.walkToCourseTile(new WorldPoint(3001, 3943, 0), "a safe spot away from the skulled player");
                                        return;
                                    }
                                    break;
                                }
                            }

                            this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            break;
                        case WILDERNESS_SIX:
                            if (!CourseObstacle.WILDERNESS_FOUR.extendedWorldArea.contains(player.getWorldLocation())
                                && !CourseObstacle.WILDERNESS_FIVE.extendedWorldArea.contains(player.getWorldLocation()))
                            {
                                this.wildernessObstacle = 5;
                            }
                            this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            break;
                        case WILDERNESS_NINE:
                            switch (this.wildernessObstacle)
                            {
                                case 3:
                                    this.walkToCourseTile(new WorldPoint(3005, 3953, 0), "the rope swing");
                                    continue;
                                case 4:
                                    this.walkToCourseTile(new WorldPoint(3002, 3960, 0), "the stepping stones");
                                    continue;
                                case 5:
                                    this.walkToCourseTile(new WorldPoint(3002, 3946, 0), "the log balance");
                                    continue;
                                default:
                                    this.plugin.setStatus("Walk back to the obstacle you were attempting");
                                    continue;
                            }
                        case PYRAMID_TWENTY_ONE:
                            this.interactObstacleAt(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), new WorldPoint(3048, 4695, 2));
                            break;
                        case PYRAMID_TWENTY_FOUR:
                            if (this.client.getVarbitValue(1556) == 0)
                            {
                                this.interactObstacle(ObstacleObjectType.DECOR, 10851, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            }
                            else
                            {
                                this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            }

                            this.plugin.setTickDelay(2);
                            break;
                        case PYRAMID_THREE:
                            if (!this.client.getLocalPlayer().getWorldLocation().equals(new WorldPoint(3375, 2849, 1)))
                            {
                                this.walkToCourseTile(new WorldPoint(3375, 2849, 1), "the tile beside the moving block");
                                return;
                            }

                            if (this.actions.findNearest(5788) != null
                                && this.actions.findNearest(5788).getWorldLocation().equals(new WorldPoint(3372, 2847, 1)))
                            {
                                if (this.actions.isRunEnabled())
                                {
                                    this.plugin.forEachReadyPyramidBlock(5788, () ->
                                        this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId()));
                                }
                                else
                                {
                                    this.actions.toggleRun();
                                }

                                return;
                            }
                            break;
                        case PYRAMID_SIXTEEN:
                            if (player.getWorldLocation().getPlane() == 3)
                            {
                                if (!this.client.getLocalPlayer().getWorldLocation().equals(new WorldPoint(3365, 2848, 3)))
                                {
                                    this.walkToCourseTile(new WorldPoint(3365, 2848, 3), "the tile beside the moving block");
                                    return;
                                }

                                if (this.actions.findNearest(5787) != null
                                    && this.actions.findNearest(5787).getWorldLocation().equals(new WorldPoint(3366, 2845, 3)))
                                {
                                    if (this.actions.isRunEnabled())
                                    {
                                        this.plugin.forEachReadyPyramidBlock(5787, () ->
                                            this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId()));
                                    }
                                    else
                                    {
                                        this.actions.toggleRun();
                                    }

                                    return;
                                }
                                break;
                            }
                        case PYRAMID_FOUR:
                        case PYRAMID_SEVEN:
                        case PYRAMID_NINE:
                            this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            this.plugin.setTickDelay(2);
                            break;
                        case COLOSSAL_WYRM_THREE:
                            if (this.client.getBoostedSkillLevel(Skill.AGILITY) >= 62)
                            {
                                this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            }
                            break;
                        case COLOSSAL_WYRM_SEVEN:
                            if (this.client.getBoostedSkillLevel(Skill.AGILITY) < 62)
                            {
                                this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            }
                            break;
                        case APE_ATOLL_ONE:
                            if (!new WorldArea(2753, 2742, -3, -4, 0).contains(player.getWorldLocation()))
                            {
                                this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            }
                            break;
                        case APE_ATOLL_FIVE:
                            if (!player.getWorldLocation().equals(new WorldPoint(2747, 2741, 0)))
                            {
                                this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                            }
                            break;
                        default:
                            if (obstacle == CourseHandoff.firstObstacle(obstacle.area)
                                && !this.actions.obstacleInRange(obstacle.type, obstacle.id))
                            {
                                this.walkToCourseStart(obstacle.area);
                                break;
                            }
                            this.movement.cancelStep();
                            this.interactObstacle(obstacle.type, obstacle.id, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
                    }
                }
            }
        }
    }

    /** The lap ended beyond the object range of the first obstacle: walk back where the course has a known start. */
    private void walkToCourseStart(AgilityCourse course)
    {
        WorldPoint start = CourseMovement.COURSE_STARTS.get(course);
        if (start != null && this.movement.walkToStart(start))
        {
            this.plugin.setStatus("Walking to the " + course.getDisplayName() + " start");
        }
        else
        {
            this.plugin.setStatus("Walk closer to the " + course.getDisplayName() + " start");
        }
    }

    private void walkToCourseTile(WorldPoint destination, String name)
    {
        if (this.movement.walk(destination) || this.movement.isWalkingTo(destination))
        {
            this.plugin.setStatus("Walking to " + name);
        }
        else
        {
            this.plugin.setStatus("Walk closer to " + name);
        }
    }

    private void interactObstacle(ObstacleObjectType type, int id, int opcode)
    {
        this.actions.interactObstacle(type, id, opcode);
    }

    private void interactObstacleAt(ObstacleObjectType type, int id, int opcode, WorldPoint point)
    {
        this.actions.interactObstacleAt(type, id, opcode, point);
    }

    private boolean takeMarkOfGrace(WorldArea definition)
    {
        if (this.config.ignoreMarks())
        {
            return false;
        }
        else
        {
            return this.client.getLocalPlayer().getWorldLocation().equals(new WorldPoint(2747, 2741, 0))
                ? false
                : this.actions.takeNearestGroundItemInArea(11849, definition);
        }
    }

    private boolean enterPrifddinasPortal()
    {
        this.prifddinasPortal = this.actions.findNearestByIds(List.of(36241, 36242, 36243, 36244, 36245, 36246));
        if (this.prifddinasPortal != null)
        {
            ObjectComposition definition = this.client.getObjectDefinition(this.prifddinasPortal.getId());
            if (definition != null && definition.getImpostor() != null && definition.getImpostor().getName().toLowerCase().contains("portal"))
            {
                this.actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), this.prifddinasPortal);
                return true;
            }
        }

        return false;
    }
}
