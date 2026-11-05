package net.runelite.client.plugins.microbot.fourtifysagility.tasks;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.fourtifysagility.FourtifysAgilityPlugin;
import net.runelite.client.plugins.microbot.fourtifysagility.config.AgilityConfig;
import net.runelite.client.plugins.microbot.fourtifysagility.config.BrimhavenVoucherReward;
import net.runelite.client.plugins.microbot.fourtifysagility.course.BrimhavenObstacles;
import net.runelite.client.plugins.microbot.fourtifysagility.course.BrimhavenRewards;
import net.runelite.client.plugins.microbot.fourtifysagility.course.BrimhavenRoute;
import net.runelite.client.plugins.microbot.fourtifysagility.course.CourseObstacle;
import net.runelite.client.plugins.microbot.fourtifysagility.course.AgilityCourse;
import net.runelite.client.plugins.microbot.fourtifysagility.course.ObstacleObjectType;
import net.runelite.client.plugins.microbot.fourtifysagility.util.action.AgilityActions;
import net.runelite.client.plugins.microbot.fourtifysagility.util.navigation.BrimhavenMovement;
import net.runelite.client.plugins.microbot.fourtifysagility.util.task.FourtifysTask;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.util.Text;

/** Runs the dynamic Brimhaven arena using only its own platform routes. */
public final class BrimhavenTask implements FourtifysTask
{
    private static final int TICKET = 29480;
    private static final int VOUCHER = 29482;
    private static final WorldPoint EXIT = new WorldPoint(2805, 9590, 3);
    private final FourtifysAgilityPlugin plugin;
    private final Client client;
    private final AgilityActions actions;
    private final AgilityConfig config;
    private final BrimhavenMovement movement;
    private WorldPoint target;
    private WorldPoint taggedTarget;
    private WorldPoint pendingTag;
    private int lastActionTick = -100;
    private int ticketCount = -1;
    private int pendingTagTick;
    private int previousAvailability = -1;
    private WorldPoint lastAttemptFrom;
    private WorldPoint lastAttemptTo;
    private int attempts;
    private boolean shopRequested;
    private int shopRequestTick;
    private int voucherBefore = -1;
    private int rewardBefore;
    private int voucherTick;
    private BrimhavenVoucherReward pendingReward;
    private boolean recovering;

    public BrimhavenTask(FourtifysAgilityPlugin plugin, Client client, AgilityConfig config, AgilityActions actions)
    {
        this.plugin = plugin;
        this.client = client;
        this.config = config;
        this.actions = actions;
        this.movement = new BrimhavenMovement(client, actions);
    }

    public void reset()
    {
        movement.cancel();
        target = null;
        taggedTarget = null;
        pendingTag = null;
        previousAvailability = -1;
        ticketCount = -1;
        lastActionTick = -100;
        lastAttemptFrom = null;
        lastAttemptTo = null;
        attempts = 0;
        shopRequested = false;
        voucherBefore = -1;
        pendingReward = null;
        recovering = false;
    }

    public void message(String text)
    {
        if (text == null || pendingTag == null)
        {
            return;
        }
        String plain = Text.removeTags(text).toLowerCase(Locale.ROOT);
        if (plain.contains("received") && plain.contains("agility arena ticket")
            || plain.contains("only get one ticket at a time"))
        {
            taggedTarget = pendingTag;
            pendingTag = null;
        }
    }

    public void observeHint()
    {
        Player player = client.getLocalPlayer();
        WorldPoint position = player == null ? null : player.getWorldLocation();
        if (position != null && position.getY() >= 5000 && position.getPlane() == 0
            && CourseObstacle.BRIMHAVEN_FALL.extendedWorldArea.contains(position))
        {
            if (!recovering)
            {
                recovering = true;
                movement.cancel();
                pendingTag = null;
                lastAttemptFrom = null;
                lastAttemptTo = null;
                attempts = 0;
                lastActionTick = -100;
            }
            return;
        }
        if (recovering && position != null && position.getPlane() == BrimhavenRoute.PLANE)
        {
            recovering = false;
            lastAttemptFrom = null;
            lastAttemptTo = null;
            attempts = 0;
        }
        if (!config.brimhavenLeave() && target != null
            && !target.equals(BrimhavenRoute.platformAt(client.getHintArrowPoint())))
        {
            movement.cancel();
        }
    }

    public void cancelMovement()
    {
        movement.cancel();
    }

    public boolean isValid()
    {
        return client.getLocalPlayer() != null;
    }

    public String getStatus()
    {
        return plugin.getStatus();
    }

    public void execute()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }
        if (player.isDead())
        {
            movement.cancel();
            plugin.setStatus("Waiting after defeat");
            return;
        }
        WorldPoint position = player.getWorldLocation();
        if (position.getY() < 5000)
        {
            if (config.brimhavenLeave())
            {
                if (config.brimhavenVoucherReward() != BrimhavenVoucherReward.NONE)
                {
                    redeemVouchers();
                    return;
                }
                plugin.setStatus("Arena exited; turn off Leave the arena to re-enter");
                return;
            }
            enterArena();
            return;
        }
        if (position.getPlane() == 0)
        {
            if (player.getAnimation() != -1)
            {
                plugin.setStatus("Waiting to recover from the fall");
                return;
            }
            GameObject ladder = actions.findNearestByIds(List.of(3610));
            if (ladder == null)
            {
                plugin.setStatus("Walk closer to an arena recovery ladder");
                return;
            }
            actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), ladder);
            plugin.setStatus("Climbing back into the arena");
            return;
        }
        if (position.getPlane() != BrimhavenRoute.PLANE)
        {
            plugin.setStatus("Walk to the Brimhaven arena");
            return;
        }
        Widget rewardText = client.getWidget(193, 2);
        if (actions.visible(193 << 16) && rewardText != null && rewardText.getText() != null
            && rewardText.getText().toLowerCase(Locale.ROOT).contains("agility arena ticket"))
        {
            actions.continueDialogue(193 << 16, 0);
            plugin.setStatus("Continuing after the ticket reward");
            return;
        }
        boolean leaving = config.brimhavenLeave();
        WorldPoint arrow = leaving ? EXIT : client.getHintArrowPoint();
        WorldPoint destination = BrimhavenRoute.platformAt(arrow);
        if (destination == null)
        {
            plugin.setStatus("Waiting for the arena dispenser arrow");
            return;
        }
        if (!destination.equals(target))
        {
            target = destination;
            taggedTarget = null;
            ticketCount = Rs2Inventory.itemQuantity(TICKET);
            movement.cancel();
            lastAttemptFrom = null;
            attempts = 0;
        }
        int available = client.getVarbitValue(5962);
        if (!leaving && previousAvailability == 0 && available == 1 && destination.equals(taggedTarget))
        {
            taggedTarget = null;
        }
        if (!leaving && previousAvailability == 1 && available == 0 && pendingTag != null)
        {
            taggedTarget = pendingTag;
            pendingTag = null;
        }
        previousAvailability = available;
        int count = Rs2Inventory.itemQuantity(TICKET);
        if (ticketCount >= 0 && count > ticketCount && pendingTag != null)
        {
            taggedTarget = pendingTag;
            pendingTag = null;
        }
        ticketCount = count;
        if (!leaving && pendingTag != null && !pendingTag.equals(target))
        {
            if (client.getTickCount() - pendingTagTick < 10)
            {
                plugin.setStatus("Waiting for the previous tag result");
                return;
            }
            pendingTag = null;
        }
        if (!leaving && destination.equals(taggedTarget))
        {
            plugin.setStatus("Waiting for the next dispenser");
            return;
        }
        int slots = (Rs2Inventory.contains(TICKET) ? 0 : 1) + (Rs2Inventory.contains(VOUCHER) ? 0 : 1);
        if (!leaving && Rs2Inventory.emptySlotCount() < slots)
        {
            plugin.setStatus("Free inventory space for tickets and vouchers");
            return;
        }
        int elapsed = client.getTickCount() - lastActionTick;
        LocalPoint localDestination = client.getLocalDestinationLocation();
        WorldPoint worldDestination = localDestination == null ? null : WorldPoint.fromLocal(client, localDestination);
        boolean walking = worldDestination != null && position.distanceTo(worldDestination) > 1;
        if (elapsed < 4 || (player.getAnimation() != -1
            || walking) && elapsed < 25)
        {
            plugin.setStatus("Crossing the arena");
            return;
        }
        WorldPoint platform = BrimhavenRoute.platformAt(position);
        if (platform == null)
        {
            plugin.setStatus("Move onto an arena platform");
            return;
        }
        if (platform.equals(destination))
        {
            if (leaving)
            {
                GameObject ladder = actions.findNearestByIds(List.of(3618));
                if (ladder == null)
                {
                    plugin.setStatus("Waiting for the arena exit ladder");
                    return;
                }
                actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), ladder);
                lastActionTick = client.getTickCount();
                plugin.setStatus("Leaving the arena");
                return;
            }
            TileObject dispenser = sceneObjects().stream()
                .filter(object -> (object.getId() == 3608 || object.getId() == 3581)
                    && object.getWorldLocation().equals(arrow))
                .findFirst().orElse(null);
            if (dispenser == null)
            {
                plugin.setStatus("Waiting for the active dispenser to load");
                return;
            }
            actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), dispenser);
            pendingTag = target;
            pendingTagTick = client.getTickCount();
            lastActionTick = client.getTickCount();
            plugin.setStatus("Tagging the active dispenser");
            return;
        }
        Optional<List<BrimhavenRoute.Step>> path = BrimhavenRoute.find(position, destination,
            client.getBoostedSkillLevel(Skill.AGILITY));
        if (path.isEmpty() || path.get().isEmpty())
        {
            plugin.setStatus("No available route at the current Agility level");
            return;
        }
        BrimhavenRoute.Step step = path.get().get(0);
        if (!position.equals(lastAttemptFrom) || !step.to().equals(lastAttemptTo))
        {
            attempts = 0;
            lastAttemptFrom = position;
            lastAttemptTo = step.to();
        }
        if (attempts >= 3)
        {
            plugin.setStatus("Arena crossing blocked; move to the next platform");
            return;
        }
        Optional<WorldPoint> walk = BrimhavenObstacles.walkTarget(step);
        if (walk.isPresent())
        {
            if (!movement.walk(walk.get(), leaving ? null : target))
            {
                plugin.setStatus("Unable to walk to the next arena platform");
                return;
            }
        }
        else
        {
            Optional<TileObject> obstacle = BrimhavenObstacles.select(step, position, sceneObjects());
            if (obstacle.isEmpty())
            {
                plugin.setStatus("Waiting for a safe arena obstacle");
                return;
            }
            actions.interactObject(MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), obstacle.get());
        }
        attempts++;
        lastActionTick = client.getTickCount();
        plugin.setStatus("Crossing " + step.obstacle().name().toLowerCase(Locale.ROOT).replace('_', ' '));
    }

    private void redeemVouchers()
    {
        BrimhavenVoucherReward reward = config.brimhavenVoucherReward();
        int vouchers = Rs2Inventory.itemQuantity(VOUCHER);
        int items = Rs2Inventory.itemQuantity(reward.itemId());
        if (voucherBefore >= 0)
        {
            if (BrimhavenRewards.purchaseConfirmed(pendingReward, voucherBefore, vouchers, rewardBefore,
                Rs2Inventory.itemQuantity(pendingReward.itemId())))
            {
                voucherBefore = -1;
                pendingReward = null;
            }
            else
            {
                plugin.setStatus(client.getTickCount() - voucherTick < 15
                    ? "Waiting for voucher purchase" : "Voucher purchase not confirmed; check inventory and store");
                return;
            }
        }
        if (vouchers < reward.price())
        {
            plugin.setStatus("Not enough vouchers for " + reward + "; turn off Leave the arena to re-enter");
            return;
        }
        if (!BrimhavenRewards.hasRoom(reward, items, Rs2Inventory.emptySlotCount()))
        {
            plugin.setStatus("Free inventory space for " + reward);
            return;
        }
        Widget frame = client.getWidget(819, 7);
        if (frame != null && !frame.isHidden())
        {
            Optional<BrimhavenRewards.Purchase> purchase = BrimhavenRewards.findVoucherPurchase(client, reward.toString());
            if (client.getVarpValue(3874) != 583 || purchase.isEmpty())
            {
                plugin.setStatus("Cannot verify the selected arena reward");
                return;
            }
            BrimhavenRewards.Purchase selected = purchase.get();
            boolean queued = actions.requestWidget(selected.widget(), selected.action(),
                () -> client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
                    && client.getLocalPlayer().getWorldLocation().distanceTo(new WorldPoint(2811, 3192, 0)) <= 12
                    && plugin.getActiveCourse() == AgilityCourse.BRIMHAVEN
                    && config.brimhavenLeave() && config.brimhavenVoucherReward() == reward
                    && client.getVarpValue(3874) == 583 && Rs2Inventory.itemQuantity(VOUCHER) == vouchers
                    && Rs2Inventory.itemQuantity(reward.itemId()) == items
                    && BrimhavenRewards.hasRoom(reward, items, Rs2Inventory.emptySlotCount())
                    && BrimhavenRewards.validVoucher(client, selected, reward.toString()),
                () -> {
                    voucherBefore = vouchers;
                    rewardBefore = items;
                    pendingReward = reward;
                    voucherTick = client.getTickCount();
                });
            plugin.setStatus(queued ? "Buying " + reward : "Waiting for the arena store");
            return;
        }
        openRewardShop();
    }

    private void openRewardShop()
    {
        if (shopRequested)
        {
            plugin.setStatus(client.getTickCount() - shopRequestTick < 15
                ? "Opening the arena store" : "Open the Agility Arena Store to spend vouchers");
            return;
        }
        NPC jackie = actions.findNearest(7650);
        if (jackie == null)
        {
            jackie = actions.findNearest(7651);
        }
        if (jackie == null)
        {
            plugin.setStatus("Walk closer to Pirate Jackie to spend vouchers");
            return;
        }
        actions.interactNpc(MenuAction.NPC_THIRD_OPTION.getId(), jackie);
        shopRequested = true;
        shopRequestTick = client.getTickCount();
        plugin.setStatus("Opening the arena store");
    }

    private void enterArena()
    {
        if (actions.visible(193 << 16))
        {
            actions.continueDialogue(193 << 16, 1);
            return;
        }
        if (actions.visible(14221312))
        {
            actions.continueDialogue(14221317, -1);
            return;
        }
        if (actions.visible(15138816))
        {
            actions.continueDialogue(15138821, -1);
            return;
        }
        if (client.getVarbitValue(5964) == 0)
        {
            if (Rs2Inventory.itemQuantity(995) < 200)
            {
                plugin.setStatus("Bring 200 coins for the arena entry fee");
                return;
            }
            NPC izzy = actions.findNearest(5789);
            if (izzy == null)
            {
                plugin.setStatus("Walk closer to Cap'n Izzy");
                return;
            }
            actions.interactNpc(MenuAction.NPC_THIRD_OPTION.getId(), izzy);
            plugin.setStatus("Paying the arena entry fee");
        }
        else
        {
            actions.interactObstacle(ObstacleObjectType.GAME,
                3617, MenuAction.GAME_OBJECT_FIRST_OPTION.getId());
            plugin.setStatus("Entering the arena");
        }
    }

    private List<TileObject> sceneObjects()
    {
        return actions.sceneObjects();
    }
}
