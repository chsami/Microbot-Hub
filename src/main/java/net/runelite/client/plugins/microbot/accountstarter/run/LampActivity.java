package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.accountstarter.logic.AccountSnapshot;
import net.runelite.client.plugins.microbot.accountstarter.logic.ApPace;
import net.runelite.client.plugins.microbot.accountstarter.logic.Choices;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Slf4j
public final class LampActivity implements Activity {
    private final int lampId;
    private final String lampName;
    private final Function<Map<Skill, Integer>, Optional<Skill>> pick;
    private boolean cleared;
    private static final int MAX_TRIES = 3;

    private final Choices choices;
    private final ApPace pace;
    private final Random random;
    private int tries;

    public LampActivity(Choices choices, int lampId, String lampName, Function<Map<Skill, Integer>, Optional<Skill>> pick,
                        ApPace pace, Random random) {
        this.lampId = lampId;
        this.lampName = lampName;
        this.pick = pick;
        this.choices = choices;
        this.pace = pace;
        this.random = random;
    }

    @Override
    public String describe() {
        return "Rubbing the " + lampName;
    }

    @Override
    public Outcome tick(AccountSnapshot s) {
        if (!cleared) {
            Dialogs.close();
            cleared = true;
        }
        if (!Rs2Inventory.hasItem(lampId)) {
            return Outcome.done(lampName + " used");
        }
        Optional<Skill> skill = pick.apply(s.getLevels());
        if (!skill.isPresent() || choices.getProtectedSkills().contains(skill.get())) {
            return Outcome.failed("the " + lampName + " has no chosen skill to go to");
        }
        if (++tries > MAX_TRIES) {
            return Outcome.failed("could not use the " + lampName + "; map its interface live");
        }
        String name = skill.get().getName();
        int carried = Rs2Inventory.count(lampId);
        Global.sleep((int) pace.reactionMs(random));
        if (!xpWindowOpen() && !Rs2Dialogue.hasSelectAnOption()) {
            if (!Rs2Inventory.interact(lampId, "Rub")
                    || !Global.sleepUntil(() -> xpWindowOpen() || Rs2Dialogue.hasSelectAnOption(), 3000)) {
                log.info("Account Starter: lamp: no window after Rub");
                return Outcome.cont();
            }
            Global.sleep((int) pace.reactionMs(random));
        }
        if (xpWindowOpen()) {
            if (!Rs2Widget.clickWidget(name, Optional.of(InterfaceID.XPREWARD), 0, true)
                    && !Rs2Widget.clickWidget(component(skill.get()))) {
                log.info("Account Starter: lamp: {} not found in the XP window", name);
                return Outcome.cont();
            }
            Global.sleep((int) pace.reactionMs(random));
            if (!Rs2Widget.clickWidget("Confirm", Optional.of(InterfaceID.XPREWARD), 0, false)
                    && !Rs2Widget.clickWidget(InterfaceID.Xpreward.CONFIRM)) {
                log.info("Account Starter: lamp: no confirm button");
                return Outcome.cont();
            }
        } else if (Rs2Dialogue.hasDialogueOption(name + ".", true) || Rs2Dialogue.hasDialogueOption(name, true)) {
            // the quest lamp lists "Attack." with a full stop; five skills a page
            Rs2Dialogue.clickOption(Rs2Dialogue.hasDialogueOption(name + ".", true) ? name + "." : name, true);
            if (!Global.sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Yes", true), 3000)) return Outcome.cont();
            Global.sleep((int) pace.reactionMs(random));
            String q = String.valueOf(Rs2Dialogue.getQuestion()).toLowerCase();
            String all = q + " " + String.valueOf(Rs2Dialogue.getDialogueText()).toLowerCase();
            if (!all.contains(name.toLowerCase())) {
                log.info("Account Starter: lamp: the confirmation does not name {}; answering No", name);
                Rs2Dialogue.clickOption("No", true);
                return Outcome.cont();
            }
            Rs2Dialogue.clickOption("Yes", true);
        } else if (Rs2Dialogue.hasDialogueOption("More options...", true)) {
            Rs2Dialogue.clickOption("More options...", true);
            return Outcome.cont();
        } else {
            log.info("Account Starter: lamp: options do not name {}: {}", name, Rs2Dialogue.getQuestion());
            return Outcome.cont();
        }
        int before = carried;
        if (Global.sleepUntil(() -> Rs2Inventory.count(lampId) < before, 3000)) {
            log.info("Account Starter: {} rubbed on {}", lampName, name);
            tries = 0;
        }
        return Outcome.cont();
    }

    private static boolean xpWindowOpen() {
        return Rs2Widget.isWidgetVisible(InterfaceID.Xpreward.UNIVERSE);
    }

    private static int component(Skill s) {
        switch (s) {
            case FISHING: return InterfaceID.Xpreward.FISHING;
            case WOODCUTTING: return InterfaceID.Xpreward.WOODCUTTING;
            case ATTACK: return InterfaceID.Xpreward.ATTACK;
            case STRENGTH: return InterfaceID.Xpreward.STRENGTH;
            case DEFENCE: return InterfaceID.Xpreward.DEFENCE;
            case RANGED: return InterfaceID.Xpreward.RANGED;
            case MAGIC: return InterfaceID.Xpreward.MAGIC;
            case PRAYER: return InterfaceID.Xpreward.PRAYER;
            case HITPOINTS: return InterfaceID.Xpreward.HITPOINTS;
            default: return InterfaceID.Xpreward.MINING;
        }
    }
}
