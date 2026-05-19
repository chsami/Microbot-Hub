package net.runelite.client.plugins.microbot.GiantSeaweedFarmer;

import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

public class GiantSeaweedFarmerScript extends Script {
    public static final int UNDERWATER_ANCHOR = 30948;
    public static final int FARMGUILD_SPIRITTREE = 33733;
    public static final int BOAT = 30919;
    // Critical section flag to prevent spore looting during compost+plant sequence
    public static volatile boolean inCriticalSection = false;
    private final GiantSeaweedFarmerPlugin giantSeaweedPlugin;
    private GiantSeaweedFarmerConfig config;
    public static GiantSeaweedFarmerStatus BOT_STATE = GiantSeaweedFarmerStatus.BANKING;
    public boolean GSF_Running = true;
    private boolean BankSuccess = false;
    private List<Integer> handledPatches = new ArrayList<>();
    private final List<Integer> patches = List.of(30500, 30501);
    private long lastApparatusCheck = System.currentTimeMillis();

    private static final WorldPoint FossilIslandDiveChest = new WorldPoint(3766, 3899, 0);
    private static final WorldPoint FarmGuildSpiritTree = new WorldPoint(1250, 3749, 0);
    private static final WorldPoint UnderWaterAnchor = new WorldPoint(3731, 10280, 1);
    private static final WorldPoint G_SeaweedPatch = new WorldPoint(3731, 10273, 1);
    private static final WorldPoint INFINITE_SPOT = new WorldPoint(3733, 10272, 1);


    @Inject
    public GiantSeaweedFarmerScript(GiantSeaweedFarmerPlugin giantSeaweedPlugin) {
        this.giantSeaweedPlugin = giantSeaweedPlugin;
    }

    public boolean run(GiantSeaweedFarmerConfig config) {
        Microbot.enableAutoRunOn = false;
        this.config = config;
        if (config.useAntiBan()){GSF_AntiBan_Setup();}

        if (config.override()) {
            BOT_STATE = config.startState();
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                // Respect pause state from other scripts (like spore looting)
                if (Microbot.pauseAllScripts.get()) return;

                switch (BOT_STATE) {
                    case IDLE:
                        if (GSF_Running){
                            BOT_STATE = GiantSeaweedFarmerStatus.TRAVELLING;
                        }
                        break;
                    case TRAVELLING:
                        if (Rs2Player.isInArea(BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint(), 25)) {
                            BOT_STATE = GiantSeaweedFarmerStatus.BANKING;
                        }
                        getToFossilIsland();
                        break;
                    case BANKING:
                        if (BankSuccess) {
                            BOT_STATE = GiantSeaweedFarmerStatus.WALKING_TO_PATCH;
                            break;
                        }
                        handleBanking();
                        break;
                    case WALKING_TO_PATCH:
                        handleDiving();
                        BOT_STATE = GiantSeaweedFarmerStatus.FARMING;
                        break;
                    case FARMING:
                        handleFarming();
                        break;
                    case INFINITE:
                        handleFarming();
                        //ToDo Set timer for 40 minutes ? or just check every tick?
                        //sleep(40 * 60 * 1000); //40min * 60sec * 1000ms
                        //ToDo Check Spores every 15 seconds or make it lazy with settings
                        //ToDo Note Seaweed?
                        //ToDo Check Fishing Apparatus?
                        //ToDo Work on "Other Tasks" and/or Break-handler
                        break;
                    case RETURN_TO_BANK:
                        returnToBank();
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Exception message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }





    private void getToFossilIsland() {
        if (Rs2Player.isInArea(BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint(), 25)) {
            return;
        }
        //Pendant Teleport
        if (config.DigsitePendant()) {
            if (!Rs2Inventory.hasItem("Digsite pendant", false)) {
                Rs2Bank.walkToBankAndUseBank();
                sleepUntil(Rs2Bank::isOpen);
                sleep(100, 200);
                Rs2Bank.withdrawOne("Digsite pendant", false);
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen());
                sleep(100, 200);
            }
        }
        //Farming Cape and Mount Teleport
        if (config.DigsiteInHouse()) {
            if (Rs2Inventory.hasItem("Farming cape", false) || Rs2Inventory.hasItem("Farming cape(t)", false)) {
                Rs2Inventory.interact("Farming cape", "Teleport");
                Rs2Inventory.interact("Farming cape(t)", "Teleport");
                sleep(2000, 3000);
                Rs2Walker.walkTo(FarmGuildSpiritTree);
                sleep(550, 750);
                Microbot.getRs2TileObjectCache().query().interact(FARMGUILD_SPIRITTREE, "Travel");
                sleep(550, 750);
                Rs2Keyboard.keyPress(KeyEvent.VK_C);
                sleep(550, 750);
                Rs2Walker.walkTo(FossilIslandDiveChest);
            }
        }

        if ((!config.DigsitePendant()) && (!config.DigsiteInHouse())) {
            Rs2Walker.walkTo(FossilIslandDiveChest);
        }

    }

    private static String getPatchState(Rs2TileObjectModel objModel) {
        if (objModel == null) return "Unknown";
        ObjectComposition comp = objModel.getObjectComposition();
        if (comp == null) return "Unknown";
        String[] actions = comp.getActions();
        if (actions == null) return "Unknown";
        for (String action : actions) {
            if (action == null) continue;
            if (action.equalsIgnoreCase("Pick")) return "Harvestable";
            if (action.equalsIgnoreCase("Cure")) return "Diseased";
            if (action.equalsIgnoreCase("Clear")) return "Dead";
            if (action.equalsIgnoreCase("Rake")) return "Weeds";
        }
        String name = comp.getName();
        if (name != null && name.equalsIgnoreCase("Seaweed patch")) return "Empty";
        if (name != null && name.equalsIgnoreCase("Seaweed")) return "Growing";
        return "Unknown";
    }


    private void handleBanking() {
        Rs2Bank.walkToBank(BankLocation.FOSSIL_ISLAND_WRECK);
        if (!Rs2Bank.openBank()) {
            BankSuccess = false;
            return;
        }

        // Just deposit all items to ensure low weight when under water
        if (config.DepositEquipment()) {Rs2Bank.depositEquipment();}
        if (config.DepositInventory()) {Rs2Bank.depositAll();}


        // Essential farming equipment
        Rs2Bank.withdrawX("seaweed spore", 2);
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawAndEquip("Fishbowl helmet");
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawAndEquip("Diving apparatus");
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawAndEquip("Flippers");
        Rs2Inventory.waitForInventoryChanges(600);
        if (!config.NoSeedDibber() && (!Rs2Inventory.contains("seed dibber"))) {
            Rs2Bank.withdrawOne("seed dibber", true);
            Rs2Inventory.waitForInventoryChanges(600);
        }
        if ((!config.NoRake()) && (!Rs2Inventory.contains("rake"))){
            Rs2Bank.withdrawOne("rake", true);
            Rs2Inventory.waitForInventoryChanges(600);
        }

        if (!Rs2Inventory.contains("spade")) {
            Rs2Bank.withdrawOne("spade", true);
            Rs2Inventory.waitForInventoryChanges(600);
        }

        if ((config.FarmingCape()) && !(Rs2Inventory.contains("Farming cape") || Rs2Inventory.contains("Farming cape(t)"))){
            Rs2Bank.withdrawOne("Farming cape", true);
            Rs2Bank.withdrawOne("Farming cape(t)", true);
            Rs2Inventory.waitForInventoryChanges(600);
        }



        // Handle compost based on configuration
        if (config.compostType() != GiantSeaweedFarmerConfig.CompostType.NONE) {
            switch (config.compostType()) {
                case COMPOST:
                    Rs2Bank.withdrawX("Compost", 2);
                    break;
                case SUPERCOMPOST:
                    Rs2Bank.withdrawX("Supercompost", 2);
                    break;
                case ULTRACOMPOST:
                    Rs2Bank.withdrawX("Ultracompost", 2);
                    break;
                case BOTTOMLESS_COMPOST_BUCKET:
                    Rs2Bank.withdrawOne("Bottomless compost bucket");
                    break;
            }
        }

        Rs2Bank.closeBank();

        // Validate inventory and equipment
        boolean hasHelmet = Rs2Equipment.isWearing("Fishbowl helmet");
        boolean hasApparatus = Rs2Equipment.isWearing("Diving apparatus");
        boolean hasSpores = Rs2Inventory.contains("seaweed spore");
        boolean hasTools = Rs2Inventory.contains("spade");

        boolean readyToFarm = hasHelmet && hasApparatus && hasSpores && hasTools;
        if (!readyToFarm) {
            StringBuilder missingItems = new StringBuilder("Missing required items: ");

            if (!hasHelmet) missingItems.append("Fishbowl helmet, ");
            if (!hasApparatus) missingItems.append("Diving apparatus, ");
            if (!hasSpores) missingItems.append("Seaweed spores, ");

            if (!hasTools) {
                //if (!Rs2Inventory.contains("seed dibber")) missingItems.append("Seed dibber, ");
                //if (!Rs2Inventory.contains("rake")) missingItems.append("Rake, ");
                if (!Rs2Inventory.contains("spade")) missingItems.append("Spade, ");
            }

            // Remove trailing comma and space if present
            String missingItemsStr = missingItems.toString();
            if (missingItemsStr.endsWith(", ")) {
                missingItemsStr = missingItemsStr.substring(0, missingItemsStr.length() - 2);
            }

            Microbot.log(missingItemsStr + " - shutting down");
            shutdownSequence();
        }
        BankSuccess = true;
    }

    private void handleDiving() {
        Microbot.getRs2TileObjectCache().query().interact(BOAT, "Dive");
        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 1, 5000);
        if (Rs2Player.getWorldLocation().getPlane() != 1) {
            Microbot.log("We failed to get underwater - Make sure to handle the warning dialog manually once");
            shutdownSequence();
            return;
        }
        Rs2Walker.walkTo(G_SeaweedPatch); // Patch

    }

    private void handleFarming() {
        Integer patchToFarm = patches.stream()
                .filter(patch -> !handledPatches.contains(patch))
                .findFirst()
                .orElse(null);

        if (patchToFarm == null) {
            handleFarmNull();
            return;
        }

        logDebug("Attempting to farm patch: " + patchToFarm + ", handled patches: " + handledPatches);

        var handledPatch = handlePatch(patchToFarm);
        if (handledPatch) {
            handledPatches.add(patchToFarm);
        }
    }

    //This is where farming is completed and decisions are made
    private void handleFarmNull(){
        if (config.modeType().equals(GiantSeaweedFarmerConfig.modeType.RUN_ONCE)) {
            Microbot.log("Finished farming, stopping...");
            shutdownSequence();
            return;
        }
        if (config.modeType().equals(GiantSeaweedFarmerConfig.modeType.RUN_ONCE_THEN_BANK)) {
            BOT_STATE = GiantSeaweedFarmerStatus.RETURN_TO_BANK;
            Microbot.log("Finished farming, returning to wreck");
            return;
        }
        if (config.modeType().equals(GiantSeaweedFarmerConfig.modeType.INFINITE)) {
            handledPatches.clear();
            BOT_STATE = GiantSeaweedFarmerStatus.INFINITE;
        }

    }

    private void handleNoting(){
        Rs2NpcModel leprechaun = Microbot.getRs2NpcCache().query().withName("Tool leprechaun").nearestOnClientThread();
        if (leprechaun == null) {return;}
        Rs2ItemModel unNoted = Rs2Inventory.getUnNotedItem("Giant seaweed", true);
        Rs2Inventory.use(unNoted);
        leprechaun.click("Talk-to");
        Rs2Inventory.waitForInventoryChanges(10000);

    }

    private boolean handlePatch(int patchId) {
        if (Rs2Inventory.isFull()) {
            handleNoting();
        }

        var objModel = Microbot.getRs2TileObjectCache().query().withId(patchId).nearest();

        if (objModel == null) {
            objModel = Microbot.getRs2TileObjectCache().query().where(o -> {
                var objComp = o.getObjectComposition();
                if (objComp == null || objComp.getName() == null) return false;
                String name = objComp.getName();
                return name.equalsIgnoreCase("Dead seaweed") ||
                        name.equalsIgnoreCase("Seaweed patch") ||
                        (name.equalsIgnoreCase("Seaweed") && objComp.getId() == patchId);
            }).nearest();
        }

        if (objModel == null) return false;

        final var patchObjModel = objModel;
        var state = getPatchState(patchObjModel);
        logDebug("Patch state detected as: " + state);
        switch (state) {
            case "Harvestable":
                if (config.FarmingCape()) {
                    if (Rs2Inventory.contains("Farming cape") && !Rs2Equipment.isWearing("Farming cape")) {
                        Rs2Inventory.interact("Farming cape", "Wear");
                        sleep(150, 250);
                    }
                    if (Rs2Inventory.contains("Farming cape(t)") && !Rs2Equipment.isWearing("Farming cape(t)")) {
                        Rs2Inventory.interact("Farming cape(t)", "Wear");
                        sleep(150, 250);
                    }
                }

                patchObjModel.click("Pick");
                sleepUntil(() -> !getPatchState(patchObjModel).equals("Harvestable") || Rs2Inventory.isFull(), 20000);

                if (!Rs2Equipment.isWearing("Diving apparatus") && Rs2Inventory.contains("Diving apparatus")) {
                    Rs2Inventory.interact("Diving apparatus", "Wear");
                    sleep(200, 300);
                }
                return false;
            case "Weeds":
                patchObjModel.click("Rake");
                sleepUntil(() -> !getPatchState(patchObjModel).equals("Weeds"), 15000);
                return false;
            case "Empty":
                inCriticalSection = true;
                try {
                    boolean hasCompost = Rs2Inventory.contains("compost") ||
                            Rs2Inventory.contains("Supercompost") ||
                            Rs2Inventory.contains("Ultracompost") ||
                            Rs2Inventory.contains("Bottomless compost bucket");

                    if (hasCompost) {
                        Rs2Inventory.use("compost");
                        patchObjModel.click("Compost");
                        Rs2Player.waitForXpDrop(Skill.FARMING);
                    }

                    if (Rs2Inventory.contains("seaweed spore")) {
                        Rs2Inventory.use(" spore");
                        patchObjModel.click("Plant");
                        sleepUntil(() -> getPatchState(patchObjModel).equals("Growing"), 10000);
                    }
                    return true;
                } finally {
                    inCriticalSection = false;
                }
            case "Dead":
                patchObjModel.click("Clear");
                sleepUntil(() -> !getPatchState(patchObjModel).equals("Dead"), 10000);
                return false;
            case "Diseased":
                Microbot.showMessage("Diseased patch! Please turn off the script and then cure me manually as i cant do this automatically yet.");
                return false;
            case "Growing":
                return true;
            default:
                return false;
        }
    }

    private void returnToBank() {

        if (Rs2Player.getWorldLocation().getPlane() == 1) {
            Rs2Walker.walkTo(UnderWaterAnchor);
            // Brief pause to allow spore detection before climbing
            sleep(300, 500);
            Microbot.getRs2TileObjectCache().query().interact(UNDERWATER_ANCHOR, "Climb");
            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 0, 7000);
        }


        if (Rs2Player.getWorldLocation().getPlane() != 0) {
            Microbot.log("We failed to get back to the surface");
        }
        else {
            Microbot.log("We're back at the bankt");
            if (config.modeType().equals(GiantSeaweedFarmerConfig.modeType.RUN_ONCE_THEN_BANK)) {
                shutdownSequence();
            }
        }



    }

    private void safetyCheck() {
        if (Rs2Player.getWorldLocation().getPlane() != 1) return; // only underwater

        if (!Rs2Equipment.isWearing("Diving apparatus")) {
            // if > 2000 ms without apparatus underwater, force equip
            if (System.currentTimeMillis() - lastApparatusCheck > 2000) {
                if (Rs2Inventory.contains("Diving apparatus")) {
                    Rs2Inventory.interact("Diving apparatus", "Wear");
                    Microbot.log("SAFETY: Diving apparatus re-equipped automatically!");
                }
            }
        } else {
            lastApparatusCheck = System.currentTimeMillis();
        }
    }

    private void GSF_AntiBan_Setup(){
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(VERY_LOW);
    }

    private void logDebug(String msg) {
        if (config.logDebug()) {
            Microbot.log(msg);
        }
    }

    private void shutdownSequence(){
        this.shutdown();
    }

    @Override
    public void shutdown() {
        GSF_Running = false;
        BOT_STATE = GiantSeaweedFarmerStatus.IDLE;
        handledPatches = new ArrayList<>();
        inCriticalSection = false; // Ensure flag is cleared on shutdown
        super.shutdown();
    }
}
