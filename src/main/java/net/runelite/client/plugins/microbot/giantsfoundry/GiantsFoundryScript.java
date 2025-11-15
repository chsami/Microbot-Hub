package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.CommissionType;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.SmithableBars;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import net.runelite.client.plugins.microbot.giantsfoundry.enums.State;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get;

public class GiantsFoundryScript extends Script {

    static final int CRUCIBLE = 44776;
    static final int MOULD_JIG = 44777;
    static final int LAVA_POOL = 44631;
    static final int WATERFALL = 44632;

    public static State state;
    static GiantsFoundryConfig config;
    static String firstBarName;
    static String secondBarName;
    static boolean useBars;


    public boolean run(GiantsFoundryConfig config) {
        this.config = config;
        useBars = config.useBars();
        firstBarName = useBars ? config.FirstBar().getName() : config.firstItem();
        secondBarName = useBars ? config.SecondBar().getName() : config.secondItem();

        setState(State.CRAFTING_WEAPON, true);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) {
                    setState(state, true);
                    sleep(2000);
                    return;
                }
                final Rs2ItemModel weapon = get(EquipmentInventorySlot.WEAPON);
                final Rs2ItemModel shield = get(EquipmentInventorySlot.SHIELD);
                if ((weapon != null || shield != null) && !weapon.getName().equalsIgnoreCase("preform")) {
                    Microbot.showMessage(("Please start the script without any weapon or shield in your equipment slot."));
                    sleep(5000);
                    return;
                }
                if (!Rs2Equipment.isWearing("ice gloves") && !Rs2Equipment.isWearing("smiths gloves")) {
                    Microbot.showMessage(("Please start by wearing ice gloves or smiths gloves."));
                    sleep(5000);
                    return;
                }
                if (GiantsFoundryState.getProgressAmount() == 1000) {
                    handIn();
                    sleep(600, 1200);
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                } else {
                    if (weapon != null) {
                        handleGameLoop();

                    } else {
                        getCommission();
                        selectMould();
                        fillCrucible();
                        pickupMould();
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        GiantsFoundryState.reset();
        super.shutdown();
    }

    public boolean hasCommission() {
        CommissionType type1 = CommissionType.forVarbit(Microbot.getVarbitValue(MouldHelper.SWORD_TYPE_1_VARBIT));
        CommissionType type2 = CommissionType.forVarbit(Microbot.getVarbitValue(MouldHelper.SWORD_TYPE_2_VARBIT));
        return type1 != CommissionType.NONE && type2 != CommissionType.NONE;
    }

    public void getCommission() {
        if (!hasCommission()) {
            GiantsFoundryState.reset();
            if (Rs2Npc.interact("kovac", "Commission"))
                sleepUntil(this::hasCommission, 5000);
        }
    }

    private boolean hasSelectedMould() {
        return (Microbot.getVarbitValue(GiantsFoundryState.VARBIT_BLADE_SELECTED) > 0
                && Microbot.getVarbitValue(GiantsFoundryState.VARBIT_TIP_SELECTED) > 0
                && Microbot.getVarbitValue(GiantsFoundryState.VARBIT_FORTE_SELECTED) > 0);
    }

    public void selectMould() {
        if (hasSelectedMould())
            return;

        Rs2GameObject.interact(MOULD_JIG);

        sleepUntil(() -> Rs2Widget.findWidget("Forte", null) != null, 5000);

        Widget forte = Rs2Widget.findWidget("Forte", null);
        if (forte != null) {
            Microbot.getMouse().click(forte.getBounds());
            sleep(600, 1200);
            MouldHelper.selectBest();
            sleep(600, 1200);
        }

        Widget blades = Rs2Widget.findWidget("Blades", null);
        if (blades != null) {
            Microbot.getMouse().click(blades.getBounds());
            sleep(600, 1200);
            MouldHelper.selectBest();
            sleep(600, 1200);
        }
        Widget tips = Rs2Widget.findWidget("Tips", null);
        if (tips != null) {
            Microbot.getMouse().click(tips.getBounds());
            sleep(600, 1200);
            MouldHelper.selectBest();
            sleep(600, 1200);
            Microbot.getMouse().click(forte.getBounds());
        }
        Widget setMould = Rs2Widget.getWidget(47054854);
        if (setMould != null) {
            Microbot.getMouse().click(setMould.getBounds());
        }
    }

    public boolean canPour() {
        ObjectComposition objectComposition = Rs2GameObject.findObjectComposition(CRUCIBLE);
        if (objectComposition == null) return false;
        return objectComposition.getName().toLowerCase().contains("(full)");
    }

    public void fillCrucible() {
        if (!preconditionsMet()) return;

        if (!ensureBarsInInventory()) return;

        if (useBars) {
            addBarsWithWidget(firstBarName, config.FirstBar());
            addBarsWithWidget(secondBarName, config.SecondBar());
        } else {
            addBarsWithInventoryUse(firstBarName);
            addBarsWithInventoryUse(secondBarName);
        }

        if (canPour()) {
            pourCrucible();
        }
    }

    private boolean preconditionsMet() {
        if (!hasSelectedMould()) return false;
        if (Microbot.getVarbitValue(MouldHelper.SWORD_TYPE_1_VARBIT) == 0) return false;
        return Microbot.getVarbitValue(GiantsFoundryState.VARBIT_GAME_STAGE) == 1;
    }

    private boolean ensureBarsInInventory() {
        if ((Rs2Inventory.hasItemAmount(firstBarName, config.firstBarAmount())
                && Rs2Inventory.hasItemAmount(secondBarName, config.secondBarAmount()))
                || canPour()) {
            return true;
        }

        Rs2Bank.openBank();
        if (Rs2Bank.count(firstBarName) < config.firstBarAmount()
                || Rs2Bank.count(secondBarName) < config.secondBarAmount()) {
            Microbot.log("Insufficient bars / items in bank to continue");
            this.shutdown();
            return false;
        }
        Rs2Bank.withdrawDeficit(firstBarName, config.firstBarAmount());
        Rs2Bank.withdrawDeficit(secondBarName, config.secondBarAmount());
        Rs2Bank.closeBank();
        return true;
    }

    private void addBarsWithWidget(String barName, SmithableBars key) {
        if (!Rs2Inventory.hasItem(barName) || canPour()) return;
        Rs2GameObject.interact(CRUCIBLE, "Fill");
        sleepUntil(() -> Rs2Widget.findWidget("What metal would you like to add?", null) != null, 5000);
        Rs2Keyboard.keyPress(getKeyFromBar(key));
        sleepUntil(() -> !Rs2Inventory.hasItem(barName), 5000);
    }

    private void addBarsWithInventoryUse(String barName) {
        if (!Rs2Inventory.hasItem(barName) || canPour()) return;
        Rs2Inventory.use(barName);
        Rs2GameObject.interact(CRUCIBLE);
        sleepUntil(() -> Rs2Widget.findWidget("How many would you like to add?", null) != null, 5000);
        sleep(600, 1200);

        if (Microbot.getClient().getWidget(14352385) != null) {
            Rs2Keyboard.keyPress('4');
            sleepUntil(() -> !Rs2Inventory.hasItem(barName), 5000);
        }
    }

    private void pourCrucible() {
        Rs2GameObject.interact(CRUCIBLE, "Pour");
        sleep(5000);
        sleepUntil(() -> !canPour(), 10000);
    }

    public static char getKeyFromBar(SmithableBars bar) {
        SmithableBars[] bars = SmithableBars.values();
        for (int i = 0; i < bars.length; i++) {
            if (bars[i] == bar) {
                return (char)('0'+i+1);
            }
        }
        return 'x'; // Not found
    }

    public boolean canPickupMould() {
        if (canPour()) return false;
        ObjectComposition objectComposition = Rs2GameObject.findObjectComposition(MOULD_JIG);
        if (objectComposition == null) return false;
        return objectComposition.getName().toLowerCase().contains("poured metal");
    }

    public void pickupMould() {
        if (!canPickupMould()) return;
        if (Rs2Inventory.isEmpty() && GiantsFoundryState.getCurrentStage() == null) {
            Rs2GameObject.interact(MOULD_JIG, "Pick-up");
            sleepUntil(() -> !canPickupMould(), 5000);
        }
    }

    boolean doAction = false;

    public void setState(State state) {
        if (GiantsFoundryScript.state == state) return;
        setState(state, true);
    }

    public void setState(State state, boolean doAction) {
        GiantsFoundryScript.state = state;
        this.doAction = doAction;
    }

    public void handleGameLoop() {

        int remainingDuration = GiantsFoundryState.heatingCoolingState.getRemainingDuration();
        int change = GiantsFoundryState.getHeatChangeNeeded();
        if (remainingDuration == 0 && change == 0 && state != State.CRAFTING_WEAPON) {
            setState(State.CRAFTING_WEAPON);
        }

        if(!Rs2Player.isAnimating(3000)) {
            Microbot.log("Not animating, doAction -> true");
            doAction = true;
        }
        if (!doAction && remainingDuration != 0) return;

        if (change < 0) {
            setState(State.COOLING_DOWN);
        } else if (change > 0) {
            setState(State.HEATING);
        }
        Stage stage = GiantsFoundryState.getCurrentStage();
        if (stage == null) return;

        switch (state) {
            case HEATING:
                boolean isAtLavaTile = Rs2Player.getWorldLocation().equals(new WorldPoint(3371, 11497, 0))
                        || Rs2Player.getWorldLocation().equals(new WorldPoint(3371, 11498, 0));
                if (!doAction && isAtLavaTile) return;
                Rs2GameObject.interact(LAVA_POOL, "Heat-preform");
                GiantsFoundryState.heatingCoolingState.stop();
                GiantsFoundryState.heatingCoolingState.setup(false, true, "heats");
                GiantsFoundryState.heatingCoolingState.start(GiantsFoundryState.getHeatAmount());
                sleepUntil(() -> GiantsFoundryState.heatingCoolingState.getRemainingDuration() <= 1);
                break;
            case COOLING_DOWN:
                boolean isAtWaterFallTile = Rs2Player.getWorldLocation().equals(new WorldPoint(3360, 11489, 0));
                if (!doAction && isAtWaterFallTile) return;
                Rs2GameObject.interact(WATERFALL, "Cool-preform");
                GiantsFoundryState.heatingCoolingState.stop();
                GiantsFoundryState.heatingCoolingState.setup(false, false, "cools");
                GiantsFoundryState.heatingCoolingState.start(GiantsFoundryState.getHeatAmount());
                sleepUntil(() -> GiantsFoundryState.heatingCoolingState.getRemainingDuration() <= 1);
                break;
            case CRAFTING_WEAPON:
                boolean isAtStageTile = stage != null
                        && Rs2Player.getWorldLocation().equals(stage.getLocation());
                if (!doAction && !BonusWidget.isActive() && isAtStageTile) return;
                craftWeapon();
                break;
        }

        doAction = false;
    }



    public void craftWeapon() {
        Stage stage = GiantsFoundryState.getCurrentStage();
        if (stage == null) return;
        GameObject obj = GiantsFoundryState.getStageObject(stage);
        if (obj == null) return;
        Rs2GameObject.interact(obj);
        Rs2Player.waitForAnimation();
    }

    private void handIn() {
        Rs2Npc.interact("kovac", "Hand-in");
    }

}