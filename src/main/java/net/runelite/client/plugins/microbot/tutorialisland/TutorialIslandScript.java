package net.runelite.client.plugins.microbot.tutorialisland;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.NameGenerator;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.*;
import static net.runelite.client.plugins.microbot.util.settings.Rs2Settings.*;

public class TutorialIslandScript extends Script {

    public static Status status = Status.NAME;
    final int CharacterCreation = 679;
    final int[] CharacterCreation_Arrows = new int[]{13, 17, 21, 25, 29, 33, 37, 44, 48, 52, 56, 60};
    private static final int MIN_RANDOMIZATION_ROUNDS = 8;
    private final TutorialIslandPlugin plugin;
    private final int NameCreation = 558;
    private boolean toggledSettings = false;
    private boolean toggledMusic = false;
    private boolean triedRoofs = false;
    private boolean triedShiftDrop = false;
    private boolean triedLevelUp = false;
    private boolean hasSelectedGender = false;
    private int randomizationRounds = 0;

    @Inject
    public TutorialIslandScript(TutorialIslandPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(TutorialIslandConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.simulateMistakes = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                CalculateStatus();

                if (Rs2Widget.isWidgetVisible(929, 5)) {
                    Rs2Widget.clickWidget(929, 5);
                    Rs2Random.waitEx(1200, 300);
                    return;
                }

                if (Rs2Widget.isWidgetVisible(310, 0)) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                    Rs2Random.waitEx(1200, 300);
                    return;
                }

                if (hasContinue()) {
                    clickContinue();
                    return;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                switch (status) {
                    case NAME:
                        Widget nameSearchBar = Rs2Widget.getWidget(NameCreation, 12); // enterName Field text

                        String nameSearchBarText = nameSearchBar.getText();

                        if (nameSearchBarText.endsWith("*")) {
                            nameSearchBarText = nameSearchBarText.substring(0, nameSearchBarText.length() - 1);
                        }

                        if (!nameSearchBarText.isEmpty()) {
                            Rs2Widget.clickWidget(NameCreation, 7); // enterName Field
                            Rs2Random.waitEx(1200, 300);

                            for (int i = 0; i < nameSearchBarText.length(); i++) {
                                Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
                                Rs2Random.waitEx(600, 100);
                            }

                            return;
                        }

                        String name = new NameGenerator(Rs2Random.between(7, 10)).getName();
                        Rs2Widget.clickWidget(NameCreation, 7); // enterName Field
                        Rs2Random.waitEx(1200, 300);
                        Rs2Keyboard.typeString(name);
                        Rs2Random.waitEx(2400, 600);
                        Rs2Widget.clickWidget(NameCreation, 18); // lookupName Button
                        Rs2Random.waitEx(4800, 600);

                        Widget responseWidget = Rs2Widget.getWidget(NameCreation, 13); // responseText Widget

                        if (responseWidget != null) {
                            String widgetText = responseWidget.getText();
                            String cleanedWidgetText = Rs2UiHelper.stripColTags(widgetText);
                            String expectedText = "Great! The display name " + name + " is available";
                            boolean nameAvailable = cleanedWidgetText.startsWith(expectedText);

                            if (nameAvailable) {
                                Rs2Widget.clickWidget(NameCreation, 19); // setName Button
                                Rs2Random.waitEx(4800, 600);

                                sleepUntil(() -> !isNameCreationVisible());
                            }
                        }
                        break;
                    case CHARACTER:
                        RandomizeCharacter();
                        break;
                    case GETTING_STARTED:
                        GettingStarted();
                        break;
                    case SURVIVAL_GUIDE:
                        SurvivalGuide();
                        break;
                    case COOKING_GUIDE:
                        CookingGuide();
                        break;
                    case QUEST_GUIDE:
                        QuestGuide();
                        break;
                    case MINING_GUIDE:
                        MiningGuide();
                        break;
                    case COMBAT_GUIDE:
                        CombatGuide();
                        break;
                    case BANKER_GUIDE:
                        BankerGuide();
                        break;
                    case PRAYER_GUIDE:
                        PrayerGuide();
                        break;
                    case MAGE_GUIDE:
                        MageGuide();
                        break;
                    case FINISHED:
                        shutdown();
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    private boolean isNameCreationVisible() {
        return Rs2Widget.isWidgetVisible(NameCreation, 2);
    }

    private boolean isCharacterCreationVisible() {
        return Rs2Widget.isWidgetVisible(CharacterCreation, 4);
    }

    public void CalculateStatus() {
        if (isNameCreationVisible()) {
            status = Status.NAME;
        } else if (isCharacterCreationVisible()) {
            status = Status.CHARACTER;
        } else if (Microbot.getVarbitPlayerValue(281) < 10) {
            status = Status.GETTING_STARTED;
        } else if (Microbot.getVarbitPlayerValue(281) >= 10 && Microbot.getVarbitPlayerValue(281) < 120) {
            status = Status.SURVIVAL_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 120 && Microbot.getVarbitPlayerValue(281) < 200) {
            status = Status.COOKING_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 200 && Microbot.getVarbitPlayerValue(281) <= 250) {
            status = Status.QUEST_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 260 && Microbot.getVarbitPlayerValue(281) <= 360) {
            status = Status.MINING_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) > 360 && Microbot.getVarbitPlayerValue(281) < 510) {
            status = Status.COMBAT_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 510 && Microbot.getVarbitPlayerValue(281) < 540) {
            status = Status.BANKER_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 540 && Microbot.getVarbitPlayerValue(281) < 610) {
            status = Status.PRAYER_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) >= 610 && Microbot.getVarbitPlayerValue(281) < 1000) {
            status = Status.MAGE_GUIDE;
        } else if (Microbot.getVarbitPlayerValue(281) == 1000) {
            status = Status.FINISHED;
        }
    }

    public void RandomizeCharacter() {
        int randomIndex = (int) Math.floor(Math.random() * CharacterCreation_Arrows.length);
        int item = CharacterCreation_Arrows[randomIndex];
        item += Math.random() < 0.5 ? 2 : 3; // Select Up / Down Arrow for random index
        Widget widget = Rs2Widget.getWidget(CharacterCreation, item);
        if (widget == null) return;

        for (int i = 0; i < Rs2Random.between(1, 6); i++) {
            Rs2Widget.clickWidget(widget.getId());
            Rs2Random.waitEx(300, 50);
        }
        randomizationRounds++;

        if (randomizationRounds < MIN_RANDOMIZATION_ROUNDS || !Rs2Random.diceFractional(0.2)) return;

        selectGender();

        if (Rs2Random.diceFractional(0.25)) { // chance to change pronouns
            System.out.println("changing pronouns...");
            Widget pronounWidget = Rs2Widget.getWidget(CharacterCreation, 72); // open pronouns DropDown
            Widget currentPronoun = Arrays.stream(pronounWidget.getDynamicChildren()).filter(pnw -> pnw.getText().toLowerCase().contains("he/him") || pnw.getText().toLowerCase().contains("they/them") || pnw.getText().toLowerCase().contains("she/her")).findFirst().orElse(null);
            Rs2Widget.clickWidget(pronounWidget);
            Rs2Random.waitEx(1200, 300);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(CharacterCreation, 76)); // Pronoun DropDown Options
            Widget[] dynamicPronounWidgets = Rs2Widget.getWidget(CharacterCreation, 78).getDynamicChildren();
            Widget pronounSelectionWidget;

            if (currentPronoun != null) {
                if (currentPronoun.getText().toLowerCase().contains("he/him")) {
                    if (Rs2Random.diceFractional(0.5)) {
                        pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("they/them")).findFirst().orElse(null);
                    } else {
                        pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("she/her")).findFirst().orElse(null);
                    }
                } else {
                    if (Rs2Random.diceFractional(0.5)) {
                        pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("they/them")).findFirst().orElse(null);
                    } else {
                        pronounSelectionWidget = Arrays.stream(dynamicPronounWidgets).filter(dpw -> dpw.getText().toLowerCase().contains("he/him")).findFirst().orElse(null);
                    }
                }

                Rs2Widget.clickWidget(pronounSelectionWidget);
                Rs2Random.waitEx(1200, 300);
                sleepUntil(() -> !Rs2Widget.isWidgetVisible(CharacterCreation, 76)); // Pronoun DropDown Options
            }
        }

        Rs2Widget.clickWidget(CharacterCreation, 74); // confirm Button
        Rs2Random.waitEx(1200, 300);
        sleepUntil(() -> !isCharacterCreationVisible());
    }

    /**
     * Selects the gender of the character during the character creation process.
     *
     * <p>This method randomly decides whether to change the gender of the character
     * if it has not been selected yet. It checks the current gender selection and
     * switches to the opposite gender if necessary.</p>
     */
    private void selectGender() {
        // Check if the gender should be changed and if it has not been selected yet
        if (Rs2Random.diceFractional(0.5) && !hasSelectedGender) { // chance to change gender
            System.out.println("changing gender...");
            Widget maleWidget = Rs2Widget.getWidget(CharacterCreation, 68); // maleButton
            Widget femaleWidget = Rs2Widget.getWidget(CharacterCreation, 69); // femaleButton.. nice..
            int selectedColor = 0xaaaaaa;

            // Check if the male gender is currently selected
            boolean hasMaleSelected = Arrays.stream(maleWidget.getDynamicChildren()).anyMatch(mdw -> mdw != null && mdw.getTextColor() == selectedColor);
            // Check if the female gender is currently selected
            boolean hasFemaleSelected = Arrays.stream(femaleWidget.getDynamicChildren()).anyMatch(fdw -> fdw != null && fdw.getTextColor() == selectedColor);

            // Switch to male gender if female is currently selected
            if (hasFemaleSelected) {
                Rs2Widget.clickWidget(maleWidget);
                Rs2Random.waitEx(1200, 300);
                sleepUntil(() -> hasMaleSelected);
                // Switch to female gender if male is currently selected
            } else if (hasMaleSelected) {
                Rs2Widget.clickWidget(femaleWidget);
                Rs2Random.waitEx(1200, 300);
                sleepUntil(() -> hasFemaleSelected);
            }
        }
        // Mark the gender as selected
        hasSelectedGender = true;
    }

    private boolean walkAndTalk(Rs2NpcModel npc) {
        return walkAndTalk(npc, 2);
    }

    private boolean walkAndTalk(Rs2NpcModel npc, int reach) {
        return walkAndAct(npc, reach, "Talk-to", () -> sleepUntil(Rs2Dialogue::isInDialogue, 5000));
    }

    private boolean walkAndAct(Rs2NpcModel npc, int reach, String action, Runnable afterClick) {
        if (npc == null) return false;
        WorldPoint npcLoc = npc.getWorldLocation();
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (npcLoc == null || playerLoc == null) return false;
        if (playerLoc.distanceTo(npcLoc) > reach) {
            Rs2Walker.walkTo(npcLoc, reach);
            Rs2Player.waitForWalking();
            return false;
        }
        if (npc.click(action)) {
            if (afterClick != null) afterClick.run();
            return true;
        }
        return false;
    }

    private boolean walkAndAttackRat() {
        // The rat pit gate (id 9719) blocks both Rs2Walker and native pathfinder.
        // Explicitly open it if we're standing adjacent; after passing through, attack directly.
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc != null && playerLoc.getX() == 3111 && playerLoc.getY() >= 9516 && playerLoc.getY() <= 9519) {
            if (Microbot.getRs2TileObjectCache().query().withId(9719).interact("Open")) {
                sleepUntil(() -> {
                    WorldPoint p = Rs2Player.getWorldLocation();
                    return p != null && p.getY() < 9516;
                }, 3000);
                return false;
            }
        }
        Rs2NpcModel rat = Microbot.getRs2NpcCache().query().withName("Giant rat").nearest();
        if (rat == null || rat.getWorldLocation() == null) return false;
        if (!Rs2Walker.canReach(rat.getWorldLocation())) return false;
        return rat.click("Attack");
    }

    public void GettingStarted() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.GIELINOR_GUIDE).nearest();

        if (hasContinue()) return;

        if (Microbot.getVarbitPlayerValue(281) < 3) {
            if (isInDialogue()) {
                Rs2Keyboard.typeString(Integer.toString(Rs2Random.between(1, 3)));
                return;
            }

            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) < 8) {

            if (!toggledSettings) {
                Rs2Widget.clickWidget(164, 41);
                toggledSettings = true;
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleMusic() && !toggledMusic) {
                toggledMusic = true;
                try { turnOffMusic(); } catch (Exception ignored) { }
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleRoofs() && !triedRoofs && !isHideRoofsEnabled()) {
                triedRoofs = true;
                try { hideRoofs(false); } catch (Exception ignored) { }
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleShiftDrop() && !triedShiftDrop && !isDropShiftSettingEnabled()) {
                triedShiftDrop = true;
                try { enableDropShiftSetting(false); } catch (Exception ignored) { }
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (plugin.isToggleLevelUp() && !triedLevelUp && isLevelUpNotificationsEnabled()) {
                triedLevelUp = true;
                try { disableLevelUpNotifications(true); } catch (Exception ignored) { }
                Rs2Random.waitEx(1200, 300);
                return;
            }

            Rs2Camera.setZoom(Rs2Random.between(400, 450));
            Rs2Random.waitEx(300, 100);
            Rs2Camera.setPitch(280);

            sleepUntil(() -> Rs2Camera.getPitch() > 250);

            walkAndTalk(npc);

        } else {
            walkAndTalk(npc);
        }
    }

    public void SurvivalGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.SURVIVAL_EXPERT).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 10 || Microbot.getVarbitPlayerValue(281) == 20 || Microbot.getVarbitPlayerValue(281) == 60) {
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) < 40) {
            Rs2Random.waitEx(1200, 300);
            var widget = Rs2Widget.findWidget("Inventory", true);
            Rs2Widget.clickWidget(widget); // switchToInventoryTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) < 50) {
            fishShrimp();
        } else if (Microbot.getVarbitPlayerValue(281) < 70) {
            var widget = Rs2Widget.findWidget("Skills", true);
            Rs2Widget.clickWidget(widget); // switchToSkillsTab
            Rs2Random.waitEx(1200, 300);
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) <= 90) {
            if (!Rs2Inventory.hasItem("Bronze Axe") || !Rs2Inventory.hasItem("Tinderbox")) {
                walkAndTalk(npc);
                return;
            }
            if (!Rs2Inventory.contains(false, "shrimps")) {
                fishShrimp();
                return;
            }
            if (!Rs2Inventory.contains("Logs") && (Microbot.getRs2TileObjectCache().query().withId(ObjectID.FIRE_26185).nearest() == null || Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) == 0)) {
                CutTree();
                return;
            }
            if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.FIRE_26185).nearest() == null) {
                LightFire();
                return;
            }
            Rs2Inventory.useItemOnObject(ItemID.RAW_SHRIMPS_2514, ObjectID.FIRE_26185);
        }
    }

    public void MageGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.MAGIC_INSTRUCTOR).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 610 || Microbot.getVarbitPlayerValue(281) == 620) {
            WorldPoint worldPoint = new WorldPoint(3141, 3088, 0);
            WorldPoint targetPoint = (npc != null) ? npc.getWorldLocation() : worldPoint;
            int distance = Rs2Player.distanceTo(targetPoint);

            if (distance > 8) {
                Rs2Walker.walkTo(targetPoint, 8);
            } else {
                walkAndTalk(npc);
            }
        } else if (Microbot.getVarbitPlayerValue(281) == 630) {
            var widget = Rs2Widget.findWidget("Magic", true);
            Rs2Widget.clickWidget(widget); // switchToMagicTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 640) {
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) == 650) {
            widgetCast();
        } else if (Microbot.getVarbitPlayerValue(281) == 680) {
            if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
                Rs2Tab.switchTo(InterfaceTab.MAGIC);
                Rs2Random.waitEx(600, 100);
            }
            Widget homeTeleport = Rs2Widget.findWidget("Lumbridge Home Teleport", true);
            if (homeTeleport != null) {
                Rs2Widget.clickWidget(homeTeleport);
                sleepUntil(() -> {
                    WorldPoint p = Rs2Player.getWorldLocation();
                    return p != null && p.getX() >= 3200;
                }, 15_000);
            }
        } else if (Microbot.getVarbitPlayerValue(281) >= 660) {
            if (isInDialogue()) {
                if (hasSelectAnOption()) {
                    if (Rs2Dialogue.keyPressForDialogueOption("Yes, I'd like to go to the mainland")) return;
                    if (Rs2Dialogue.keyPressForDialogueOption("Yes, send me to the mainland")) return;
                    if (Rs2Dialogue.keyPressForDialogueOption("Yes")) return;
                    Rs2Dialogue.keyPressForDialogueOption(1);
                    return;
                }
                Rs2Dialogue.clickContinue();
                return;
            }

            walkAndTalk(npc);
        }
    }

    public void PrayerGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.BROTHER_BRACE).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 640 || Microbot.getVarbitPlayerValue(281) == 550 || Microbot.getVarbitPlayerValue(281) == 540) {
            Rs2Walker.walkTo(new WorldPoint(3124, 3106, 0));
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) == 560) {
            var widget = Rs2Widget.findWidget("Prayer", true);
            Rs2Widget.clickWidget(widget); // switchToPrayerTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 570) {
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) == 580) {
            var widget = Rs2Widget.findWidget("Friends list", true);
            Rs2Widget.clickWidget(widget); // switchToFriendsTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 600) {
            walkAndTalk(npc);
        }
    }

    public void BankerGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.ACCOUNT_GUIDE).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 510) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.BANK_BOOTH_10083);
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 510);


        } else if (Microbot.getVarbitPlayerValue(281) == 520) {
            if (Rs2Widget.isWidgetVisible(928, 4)) {
                Rs2Widget.clickWidget(928, 4); // Close poll booth interface
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (Rs2Widget.isWidgetVisible(289, 5)) {
                Widget widgetOptions = Rs2Widget.getWidget(289, 4);
                Widget[] dynamicWidgetOptions = widgetOptions.getDynamicChildren();

                for (Widget dynamicWidgetOption : dynamicWidgetOptions) {
                    String widgetText = dynamicWidgetOption.getText();

                    if (widgetText != null) {
                        if (widgetText.equalsIgnoreCase("Want more bank space?")) {
                            Rs2Widget.clickWidget(289, 7);
                            Rs2Random.waitEx(1200, 300);
                            break;
                        }
                    }
                }
            }

            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
            Microbot.getRs2TileObjectCache().query().interact(26815); //interactWithPollBooth
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 520 || Rs2Widget.isWidgetVisible(928, 4));
        } else if (Microbot.getVarbitPlayerValue(281) == 525 || Microbot.getVarbitPlayerValue(281) == 530) {
            if (Rs2Widget.isWidgetVisible(928, 4)) {
                Rs2Widget.clickWidget(928, 4); // Close poll booth interface
                Rs2Random.waitEx(1200, 300);
                return;
            }

            if (Rs2Widget.isWidgetVisible(310, 2)) {
                Widget widgetOptions = Rs2Widget.getWidget(310, 2);
                Widget[] dynamicWidgetOptions = widgetOptions.getDynamicChildren();

                for (Widget dynamicWidgetOption : dynamicWidgetOptions) {
                    String[] actionsText = dynamicWidgetOption.getActions();

                    if (actionsText != null) {
                        if (Arrays.stream(actionsText).anyMatch(at -> at.equalsIgnoreCase("close"))) {
                            Rs2Widget.clickWidget(dynamicWidgetOption);
                            Rs2Random.waitEx(1200, 300);
                            break;
                        }
                    }
                }
            }

            Rs2Walker.walkTo(npc.getWorldLocation(), 3);
            Rs2Player.waitForWalking();
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) == 531) {
            var widget = Rs2Widget.findWidget("Account Management", true);
            Rs2Widget.clickWidget(widget); // switchToAccountManagementTab
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 532) {
            if (Rs2Dialogue.isInDialogue()) {
                clickContinue();
                return;
            }
            walkAndTalk(npc);
        }
    }

    public void CombatGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.COMBAT_INSTRUCTOR).nearest();

        if (Microbot.getVarbitPlayerValue(281) <= 370) {
            Rs2Walker.walkTo(new WorldPoint(Rs2Random.between(3106, 3108), Rs2Random.between(9508, 9510), 0));
            Rs2Player.waitForWalking();
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) <= 410) {
            if (isInDialogue()) {
                clickContinue();
                return;
            }
            var widget = Rs2Widget.findWidget("Worn Equipment", true);
            Rs2Widget.clickWidget(widget); // switchToEquipmentMenu
            Rs2Random.waitEx(1200, 300);
            Rs2Widget.clickWidget(387, 1); //openEquipmentStats
            sleepUntil(() -> Rs2Widget.getWidget(84, 1) != null);
            Rs2Random.waitEx(1200, 300);
            Rs2Widget.clickWidget("Bronze dagger");
            Rs2Random.waitEx(2400, 300);

            if (Rs2Widget.isWidgetVisible(84, 3)) {
                Widget widgetOptions = Rs2Widget.getWidget(84, 3);
                Widget[] dynamicWidgetOptions = widgetOptions.getDynamicChildren();

                for (Widget dynamicWidgetOption : dynamicWidgetOptions) {
                    String[] actionsText = dynamicWidgetOption.getActions();

                    if (actionsText != null) {
                        if (Arrays.stream(actionsText).anyMatch(at -> at.equalsIgnoreCase("close"))) {
                            Rs2Widget.clickWidget(dynamicWidgetOption);
                            Rs2Random.waitEx(1200, 300);
                            break;
                        }
                    }
                }
            }

            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) == 500) {
            Rs2Walker.walkTo(new WorldPoint(3111, 9526, Rs2Player.getWorldLocation().getPlane()));
            Rs2Player.waitForWalking();
            Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query().withName("Ladder").interact("Climb-up"));
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 500);
        } else if (Microbot.getVarbitPlayerValue(281) == 480 || Microbot.getVarbitPlayerValue(281) == 490) {
            Actor rat = Rs2Player.getInteracting();
            if (rat != null && rat.getName().equalsIgnoreCase("giant rat")) return;
            if (Rs2Inventory.hasItem("Shortbow")) {
                Rs2Inventory.wield("Shortbow");
                Rs2Random.waitEx(600, 100);
            }
            if (Rs2Inventory.hasItem("Bronze arrow")) {
                Rs2Inventory.wield("Bronze arrow");
                Rs2Random.waitEx(600, 100);
            }
            walkAndAttackRat();
        } else if (Microbot.getVarbitPlayerValue(281) == 470) {
            if (npc == null) return;
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) == 430) {
            var widget = Rs2Widget.findWidget("Combat Options", true);
            Rs2Widget.clickWidget(widget);
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) >= 420) {
            if (isInDialogue()) {
                clickContinue();
                return;
            }
            if (Microbot.getClient().getLocalPlayer().isInteracting() || Rs2Player.isAnimating()) return;
            if (Rs2Equipment.isWearing("Bronze sword")) {
                walkAndAttackRat();
            } else if (Rs2Inventory.hasItem("Bronze sword")) {
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                Rs2Random.waitEx(600, 100);
                Rs2Inventory.wield("Bronze sword");
                Rs2Random.waitEx(600, 100);
                Rs2Inventory.wield("Wooden shield");
            } else {
                walkAndTalk(npc);
            }
        }
    }

    public void MiningGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.MINING_INSTRUCTOR).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 260) {
            Rs2Walker.walkTo(new WorldPoint(Rs2Random.between(3082, 3085), Rs2Random.between(9502, 9505), 0));
            walkAndTalk(npc);
        } else {
            if (Rs2Inventory.contains("Bronze dagger")) {
                Microbot.getRs2TileObjectCache().query().interact(ObjectID.GATE_9718, "Open");
                sleepUntil(() -> Microbot.getVarbitPlayerValue(281) > 360);
                return;
            }
            if (Rs2Inventory.contains("Bronze bar") && Rs2Inventory.contains("Hammer")) {
                Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query().withName("Anvil").interact("Smith"));
                sleepUntil(Rs2Widget::isSmithingWidgetOpen);
                Rs2Widget.clickWidget(312, 9); // Smith Bronze Dagger
                Rs2Random.waitEx(1200, 300);
                sleepUntil(() -> Rs2Inventory.contains("Bronze dagger") && !Rs2Player.isAnimating(1800));
                return;
            }
            if (Rs2Inventory.contains("Bronze bar") && !Rs2Inventory.contains("Hammer")) {
                walkAndTalk(npc);
                return;
            }
            if (Rs2Inventory.contains("Bronze pickaxe") && (!Rs2Inventory.contains("Copper ore") || !Rs2Inventory.contains("Tin ore"))) {
                List<Integer> rockIds = new ArrayList<>();
                if (!Rs2Inventory.contains("Copper ore")) {
                    rockIds.add(ObjectID.COPPER_ROCKS);
                }
                if (!Rs2Inventory.contains("Tin ore")) {
                    rockIds.add(ObjectID.TIN_ROCKS);
                }

                Collections.shuffle(rockIds);
                int rockId = rockIds.get(0);

                Microbot.getRs2TileObjectCache().query().interact(rockId, "Mine");
                sleepUntil(() -> {
                    if (rockId == ObjectID.COPPER_ROCKS) {
                        return Rs2Inventory.contains("Copper ore") && !Rs2Player.isAnimating(1800);
                    } else {
                        return Rs2Inventory.contains("Tin ore") && !Rs2Player.isAnimating(1800);
                    }
                });
            } else if (Rs2Inventory.contains("Copper ore") && Rs2Inventory.contains("Tin ore")) {
                int[] ores = {ItemID.TIN_ORE, ItemID.COPPER_ORE};
                Collections.shuffle(Arrays.asList(ores));
                Rs2Inventory.useItemOnObject(ores[0], ObjectID.FURNACE_10082);
                sleepUntil(() -> Rs2Inventory.contains("Bronze bar") && !Rs2Player.isAnimating(1800));
            }
        }
    }

    public void QuestGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.QUEST_GUIDE).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 200 || Microbot.getVarbitPlayerValue(281) == 210) {
            Rs2Walker.walkTo(new WorldPoint(Rs2Random.between(3083, 3086), Rs2Random.between(3127, 3129), 0));
            Microbot.getRs2TileObjectCache().query().interact(9716, "Open");
            Rs2Random.waitEx(1200, 300);
        } else if (Microbot.getVarbitPlayerValue(281) == 220 || Microbot.getVarbitPlayerValue(281) == 240) {
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) == 230) {
            var widget = Rs2Widget.findWidget("Quest List", true);
            Rs2Widget.clickWidget(widget); // switchToQuestTab
            Rs2Random.waitEx(1200, 300);
        } else {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            Rs2Random.waitEx(600, 100);
            Microbot.getRs2TileObjectCache().query().interact(9726, "Climb-down");
            Rs2Random.waitEx(2400, 100);
        }
    }

    public void CookingGuide() {
        var npc = Microbot.getRs2NpcCache().query().withId(NpcID.MASTER_CHEF).nearest();

        if (Microbot.getVarbitPlayerValue(281) == 120) {
            Rs2Random.waitEx(1200, 300);
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.GATE_9470, "Open");
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 120);
        } else if (Microbot.getVarbitPlayerValue(281) == 130) {
            Microbot.getRs2TileObjectCache().query().interact(ObjectID.DOOR_9709, "Open");
            sleepUntil(() -> Microbot.getVarbitPlayerValue(281) != 130);
        } else if (Microbot.getVarbitPlayerValue(281) == 140) {
            walkAndTalk(npc);
        } else if (Microbot.getVarbitPlayerValue(281) >= 150 && Microbot.getVarbitPlayerValue(281) < 200) {
            if (!Rs2Inventory.contains("Bread dough") && !Rs2Inventory.contains("Bread")) {
                Rs2Inventory.combine("Bucket of water", "Pot of flour");
                sleepUntil(() -> Rs2Inventory.contains("Dough"), 2000);
            } else if (Rs2Inventory.contains("Bread dough")) {
                Rs2Inventory.interact("Bread dough");
                Microbot.getRs2TileObjectCache().query().interact(9736, "Use");
                sleepUntil(() -> Rs2Inventory.contains("Bread"));
            } else if (Rs2Inventory.contains("Bread")) {
                if (Microbot.getRs2TileObjectCache().query().interact(9710, "Open")) {
                    Rs2Random.waitEx(2400, 100);
                }
            }
        }
    }

    public void LightFire() {
        if (Rs2Player.isStandingOnGameObject()) {
            WorldPoint nearestWalkable = Rs2Tile.getNearestWalkableTileWithLineOfSight(Rs2Player.getWorldLocation());
            Rs2Walker.walkFastCanvas(nearestWalkable);
            Rs2Player.waitForWalking();
        }
        Rs2Inventory.combine("Logs", "Tinderbox");
        sleepUntil(() -> !Rs2Inventory.hasItem("Logs") && !Rs2Player.isAnimating(2400));
    }

    public void CutTree() {
        Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query().withName("Tree").interact("Chop down"));
        sleepUntil(() -> Rs2Inventory.hasItem("Logs") && !Rs2Player.isAnimating(2400));
    }

    public void fishShrimp() {
        Microbot.getRs2NpcCache().query().withId(NpcID.FISHING_SPOT_3317).interact("Net");
        sleepUntil(() -> Rs2Inventory.contains(false, "shrimps"));
    }

    private boolean widgetCast() {
        if (Rs2Player.isAnimating() || Rs2Player.getInteracting() != null) return true;

        Widget windStrike = Rs2Widget.findWidget("Wind Strike", null, true);
        if (windStrike == null) windStrike = Rs2Widget.getWidget(218, 11);
        if (windStrike == null) return false;

        boolean hidden;
        try {
            hidden = Rs2Widget.isHidden(windStrike.getId());
        } catch (Exception ignored) {
            hidden = true;
        }

        if (hidden) {
            var magicTab = Rs2Widget.findWidget("Magic", true);
            if (magicTab != null) {
                Rs2Widget.clickWidget(magicTab);
                Rs2Random.waitEx(200, 50);
            }
            windStrike = Rs2Widget.getWidget(218, 8);
            if (windStrike == null) windStrike = Rs2Widget.findWidget("Wind Strike", null, true);
            if (windStrike == null) return false;
            try {
                if (Rs2Widget.isHidden(windStrike.getId())) return false;
            } catch (Exception ignored) {
                return false;
            }
        }

        Rs2Widget.clickWidget(windStrike);
        Rs2Random.waitEx(150, 50);

        Rs2NpcModel chicken = Microbot.getRs2NpcCache().query().withName("chicken").nearestOnClientThread();
        if (chicken == null) return false;

        if (!chicken.click("Cast")) {
            chicken.click("Cast");
        }

        sleepUntil(() -> Rs2Player.isAnimating() || Microbot.getVarbitPlayerValue(281) != 650, 2_000);
        return true;
    }

    enum Status {
        NAME,
        CHARACTER,
        GETTING_STARTED,
        SURVIVAL_GUIDE,
        COOKING_GUIDE,
        QUEST_GUIDE,
        MINING_GUIDE,
        COMBAT_GUIDE,
        BANKER_GUIDE,
        PRAYER_GUIDE,
        IRONMAN_GUIDE,
        MAGE_GUIDE,
        FINISHED
    }
}
