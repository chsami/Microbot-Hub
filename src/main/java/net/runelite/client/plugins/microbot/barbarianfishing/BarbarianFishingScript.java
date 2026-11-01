package net.runelite.client.plugins.microbot.barbarianfishing;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Singleton
public class BarbarianFishingScript extends Script
{
    private static final String BARREL_FULL_MESSAGE =
            "The barrel is full. It may be emptied at a bank.";
    private static final String BARREL_EMPTIED_MESSAGE =
            "You empty all of your containers into the bank.";

    public enum Location
    {
        OTTOS_GROTTO("Otto's Grotto", 10038),
        MOUNT_QUIDAMORTEM("Mount Quidamortem (CoX)", 4919),
        OTHER("Other", -1);

        private final String displayName;
        private final int regionId;

        Location(String displayName, int regionId)
        {
            this.displayName = displayName;
            this.regionId = regionId;
        }

        public String getDisplayName()
        {
            return displayName;
        }

        public int getRegionId()
        {
            return regionId;
        }
    }

    public enum CoxTravel
    {
        NONE,
        TO_BANK,
        OPEN_BANK,
        BANKING,
        RETURN_TO_FISH
    }

    private BarbarianFishingConfig config;

    @Getter
    private volatile String status = "Starting";
    @Getter
    private volatile Location location = Location.OTHER;
    @Getter
    private volatile Instant startedAt;

    private long specReadyTime;
    private boolean specActivated;
    private int animationTimeout = 600;

    private volatile boolean xpInitialized;
    private volatile int startFishingXp;
    @Getter
    private volatile int fishingXpGained;
    @Getter
    private volatile int fishCaught;

    @Getter
    private volatile boolean barrelFull;
    @Getter
    private volatile boolean hasBarrel;
    private volatile boolean banking;

    private CoxTravel coxTravel = CoxTravel.NONE;
    private MountQuidamortem.BankApproach bankApproach;
    private long lastBankInteractAt;

    public boolean run(BarbarianFishingConfig config)
    {
        this.config = config;
        this.startedAt = Instant.now();
        this.status = "Initializing";
        this.coxTravel = CoxTravel.NONE;
        this.banking = false;
        this.barrelFull = false;
        this.hasBarrel = false;
        this.specReadyTime = 0;
        this.specActivated = false;
        this.xpInitialized = false;
        this.fishingXpGained = 0;
        this.fishCaught = 0;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!super.run())
                {
                    return;
                }

                if (!Microbot.isLoggedIn())
                {
                    status = "Logged out";
                    return;
                }

                updateLocation();
                initializeXpIfNeeded();

                hasBarrel = config.useFishBarrel() && Rs2Inventory.hasItem("Fish barrel", "Open fish barrel");

                if (!hasBarbarianRod())
                {
                    setStatus("Missing Barbarian rod");
                    shutdown();
                    return;
                }

                if (!hasBait())
                {
                    setStatus("Out of bait");
                    shutdown();
                    return;
                }

                handleDragonHarpoonSpec();

                if (location == Location.MOUNT_QUIDAMORTEM && handleCoxControl())
                {
                    return;
                }

                if (Rs2Inventory.isFull())
                {
                    handleFullInventory();
                    return;
                }

                if (Rs2AntibanSettings.actionCooldownActive)
                {
                    return;
                }

                if (Rs2Player.isAnimating(animationTimeout) || Rs2Player.isMoving())
                {
                    return;
                }

                animationTimeout = (int) Rs2Random.truncatedGauss(600, 1200, 2.0);

                Rs2NpcModel spot = findFishingSpot();
                if (spot == null)
                {
                    if (location == Location.MOUNT_QUIDAMORTEM)
                    {
                        setStatus("Recovering to lake center");
                        Rs2Walker.walkTo(MountQuidamortem.RECOVERY_CENTER, 3);
                    }
                    else
                    {
                        setStatus("Waiting for fishing spot");
                    }
                    return;
                }

                WorldPoint here = Rs2Player.getWorldLocation();
                WorldPoint spotLoc = spot.getWorldLocation();

                if (here != null && spotLoc != null && here.distanceTo(spotLoc) > 7)
                {
                    setStatus("Walking to fishing spot");
                    WorldPoint approach = Rs2Tile.getNearestWalkableTile(spotLoc);
                    Rs2Walker.walkTo(approach != null ? approach : spotLoc, 1);
                    return;
                }

                if (!Rs2Camera.isTileOnScreen(spot.getLocalLocation()))
                {
                    Rs2Camera.turnTo(spot);
                }

                setStatus("Fishing");
                if (spot.click("Use-rod"))
                {
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                }
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                setStatus("Error: " + ex.getClass().getSimpleName());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void updateLocation()
    {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null)
        {
            return;
        }

        if (MountQuidamortem.contains(playerLoc))
        {
            location = Location.MOUNT_QUIDAMORTEM;
        }
        else if (playerLoc.getRegionID() == Location.OTTOS_GROTTO.getRegionId())
        {
            location = Location.OTTOS_GROTTO;
        }
        else
        {
            location = Location.OTHER;
        }
    }

    private void handleFullInventory()
    {
        if (config.useFishBarrel() && hasBarrel && !barrelFull)
        {
            setStatus("Filling Fish barrel");
            Rs2ItemModel barrel = Rs2Inventory.get("Fish barrel", "Open fish barrel");
            if (barrel != null)
            {
                Rs2Inventory.interact(barrel, "Fill");
                sleep(Rs2Random.between(600, 1000));
                if (Rs2Inventory.isFull())
                {
                    barrelFull = true;
                }
                return;
            }
        }

        if (location == Location.MOUNT_QUIDAMORTEM && config.bankAtCox())
        {
            beginCoxBankTrip();
            return;
        }

        setStatus("Dropping fish");
        Rs2Inventory.dropAll(x -> isLeapingFish(x.getName()), config.dropOrder());
    }

    private boolean handleCoxControl()
    {
        if (coxTravel == CoxTravel.NONE)
        {
            return false;
        }

        handleCoxTravel();
        return true;
    }

    private void beginCoxBankTrip()
    {
        bankApproach = MountQuidamortem.randomBankApproach();
        coxTravel = CoxTravel.TO_BANK;
        banking = true;
        Rs2Walker.clearWalkingRoute("CoX bank trip started");
        setStatus("CoX -> bank (" + bankApproach.label + ")");
    }

    private void handleCoxTravel()
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null) return;

        switch (coxTravel)
        {
            case TO_BANK:
                if (bankApproach == null)
                {
                    bankApproach = MountQuidamortem.randomBankApproach();
                }

                setStatus("CoX -> bank (" + bankApproach.label + ")");

                if (MountQuidamortem.isAtBank(here))
                {
                    coxTravel = CoxTravel.OPEN_BANK;
                    break;
                }

                Rs2Walker.walkTo(bankApproach.target, bankApproach.arrivalRadius);
                if (MountQuidamortem.isAtBank(here))
                {
                    coxTravel = CoxTravel.OPEN_BANK;
                }
                break;

            case OPEN_BANK:
                openCoxBankChest();
                break;

            case BANKING:
                bankCoxFishAndBarrel();
                break;

            case RETURN_TO_FISH:
                setStatus("Bank -> fishing");
                WorldPoint returnTarget = MountQuidamortem.randomReturnAnchor();
                Rs2Walker.walkTo(returnTarget, 2);
                coxTravel = CoxTravel.NONE;
                banking = false;
                break;
        }
    }

    private void openCoxBankChest()
    {
        if (Rs2Bank.isOpen())
        {
            coxTravel = CoxTravel.BANKING;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBankInteractAt < 1200L)
        {
            setStatus("Opening CoX bank");
            return;
        }
        lastBankInteractAt = now;

        setStatus("Opening CoX bank chest");
        Rs2TileObjectModel chest = Microbot.getRs2TileObjectCache().query()
                .withId(MountQuidamortem.BANK_CHEST_ID)
                .nearest();
        boolean clicked = false;
        if (chest != null)
        {
            clicked = chest.click("Bank");
            if (!clicked)
            {
                clicked = chest.click("Use");
            }
        }
        if (!clicked)
        {
            clicked = Rs2Bank.openBank();
        }

        if (clicked)
        {
            sleepUntil(Rs2Bank::isOpen, 2800);
        }

        if (Rs2Bank.isOpen())
        {
            coxTravel = CoxTravel.BANKING;
        }
    }

    private void bankCoxFishAndBarrel()
    {
        banking = true;

        if (!Rs2Bank.isOpen())
        {
            coxTravel = CoxTravel.OPEN_BANK;
            return;
        }

        setStatus("Banking inventory");
        Rs2Bank.depositAll(x -> isLeapingFish(x.getName()));
        sleepUntil(() -> !hasAnyLeapingFish(), 2500);

        if (config.useFishBarrel() && hasBarrel)
        {
            setStatus("Emptying Fish barrel");
            Rs2Bank.emptyFishBarrel();
            sleep(Rs2Random.between(300, 600));
            barrelFull = false;
        }

        setStatus("Closing bank");
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2500);

        coxTravel = CoxTravel.RETURN_TO_FISH;
        bankApproach = null;
        Rs2Walker.clearWalkingRoute("CoX bank trip completed");
    }

    private Rs2NpcModel findFishingSpot()
    {
        return Microbot.getRs2NpcCache().query()
                .withIds(FishingSpot.BARB_FISH.getIds())
                .nearest();
    }

    private boolean hasBarbarianRod()
    {
        return Rs2Inventory.hasItem("Barbarian rod")
                || Rs2Inventory.hasItem("Pearl barbarian rod")
                || Rs2Equipment.isWearing("Barbarian rod")
                || Rs2Equipment.isWearing("Pearl barbarian rod");
    }

    private boolean hasBait()
    {
        return Rs2Inventory.hasItem("feather")
                || Rs2Inventory.hasItem("fishing bait")
                || Rs2Inventory.hasItem("fish offal")
                || Rs2Inventory.hasItem("roe")
                || Rs2Inventory.hasItem("caviar");
    }

    private boolean hasAnyLeapingFish()
    {
        return Rs2Inventory.hasItem("Leaping trout", "Leaping salmon", "Leaping sturgeon");
    }

    public static boolean isLeapingFish(String name)
    {
        if (name == null)
        {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("leaping trout")
                || lower.equals("leaping salmon")
                || lower.equals("leaping sturgeon");
    }

    private void handleDragonHarpoonSpec()
    {
        if (!config.useDragonHarpoonSpec())
        {
            return;
        }

        boolean hasHarpoon = Rs2Equipment.isWearing(ItemID.DRAGON_HARPOON)
                || Rs2Inventory.hasItem(ItemID.DRAGON_HARPOON);

        if (!hasHarpoon)
        {
            return;
        }

        if (Rs2Combat.getSpecEnergy() == 1000)
        {
            if (specReadyTime == 0)
            {
                double delay = Math.max(0, Rs2Random.gaussRand(30000, 20000));
                specReadyTime = System.currentTimeMillis() + (long) delay;
            }
            else if (!specActivated && System.currentTimeMillis() >= specReadyTime)
            {
                if (Rs2Combat.setSpecState(true))
                {
                    specActivated = true;
                }
            }
        }
        else
        {
            specReadyTime = 0;
            specActivated = false;
        }
    }

    private void initializeXpIfNeeded()
    {
        if (xpInitialized)
        {
            return;
        }

        int currentXp = Microbot.getClient().getSkillExperience(Skill.FISHING);
        if (currentXp > 0)
        {
            startFishingXp = currentXp;
            xpInitialized = true;
        }
    }

    public void onChatMessage(ChatMessage event)
    {
        String message = event.getMessage();
        if (message == null)
        {
            return;
        }

        if (message.contains(BARREL_FULL_MESSAGE))
        {
            barrelFull = true;
        }
        else if (message.contains(BARREL_EMPTIED_MESSAGE))
        {
            barrelFull = false;
        }
        else if (message.startsWith("You catch a"))
        {
            fishCaught++;
        }
    }

    public void onStatChanged(StatChanged event)
    {
        if (event.getSkill() != Skill.FISHING)
        {
            return;
        }

        int currentXp = event.getXp();
        if (!xpInitialized)
        {
            startFishingXp = currentXp;
            xpInitialized = true;
            return;
        }

        if (currentXp > startFishingXp)
        {
            fishingXpGained = currentXp - startFishingXp;
        }
    }

    public void setStatus(String message)
    {
        this.status = message;
        Microbot.status = message;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        coxTravel = CoxTravel.NONE;
        banking = false;
        Rs2Walker.clearWalkingRoute("Barbarian fishing shutdown");
        setStatus("Stopped");
    }
}
