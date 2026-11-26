package net.runelite.client.plugins.microbot.accountstarter.run;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.accountstarter.logic.Action;
import net.runelite.client.plugins.microbot.accountstarter.logic.StyleResolver;
import net.runelite.client.plugins.microbot.accountstarter.logic.XpGuard;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Slf4j
public final class CombatStyle {
    private static final int RAPID_BUTTON = 1;
    private static final int[] BUTTONS = {
            InterfaceID.CombatInterface._0, InterfaceID.CombatInterface._1,
            InterfaceID.CombatInterface._2, InterfaceID.CombatInterface._3};
    private static final int[] LABELS = {
            InterfaceID.CombatInterface._0_TEXT, InterfaceID.CombatInterface._1_TEXT,
            InterfaceID.CombatInterface._2_TEXT, InterfaceID.CombatInterface._3_TEXT};

    private CombatStyle() {}

    /** Null when set; the caller must not attack on a non-null answer. */
    public static String ensure(Action a, Set<Skill> protectedSkills, Rs2CombatSpells spell) {
        if (!XpGuard.allowed(a, protectedSkills)) return a + " would train a protected skill";
        if (a == Action.MAGIC_CAST) {
            // never defensive casting: that splits XP into Defence
            if (!Rs2Combat.setAutoCastSpell(spell, false) || Rs2Magic.getCurrentAutoCastSpell() != spell) {
                return "could not autocast " + spell + " (needs a staff and the runes)";
            }
        } else if (!activeSafe(a, protectedSkills) || (a == Action.RANGED_RAPID && !rapidOn())) {
            // Rapid reads as safe on Accurate too (same XP type), so its button is checked by label
            Rs2Tab.switchTo(InterfaceTab.COMBAT);
            if (!Global.sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2000)) {
                return "the combat tab did not open";
            }
            String[] types = xpTypes();
            String[] labels = labels();
            int i = StyleResolver.indexFor(a, labels, types, protectedSkills);
            if (i < 0) return "the wielded weapon has no style for " + a;
            if (Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) != i) {
                log.info("Account Starter: combat style {} ({})", labels[i], types[i]);
                Rs2Widget.clickWidget(BUTTONS[i]);
                Global.sleepUntil(() -> Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == i, 2400);
            }
        }
        return activeSafe(a, protectedSkills) ? null : "the combat style did not change to " + a;
    }

    private static boolean rapidOn() {
        return Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == RAPID_BUTTON;
    }

    /** Weapon swaps change the style, so this is re-read before every attack. */
    public static boolean activeSafe(Action a, Set<Skill> protectedSkills) {
        int mode = Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE);
        int defMode = Microbot.getVarbitValue(VarbitID.AUTOCAST_DEFMODE);
        return StyleResolver.safe(mode, defMode, xpTypes(), a, protectedSkills);
    }


    static String[] xpTypes() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client c = Microbot.getClient();
            EnumComposition all = c.getEnum(EnumID.WEAPON_STYLES);
            int styles = all == null ? -1 : all.getIntValue(c.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY));
            if (styles == -1) return new String[0];
            int[] structs = c.getEnum(styles).getIntVals();
            String[] out = new String[structs.length];
            for (int i = 0; i < structs.length; i++) {
                StructComposition s = c.getStructComposition(structs[i]);
                out[i] = s == null ? null : s.getStringValue(ParamID.ATTACK_STYLE_NAME);
            }
            return out;
        }).orElse(new String[0]);
    }

    private static String[] labels() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            String[] out = new String[LABELS.length];
            for (int i = 0; i < LABELS.length; i++) {
                Widget w = Microbot.getClient().getWidget(LABELS[i]);
                out[i] = w == null || w.isHidden() || w.getText() == null ? "" : w.getText();
            }
            return out;
        }).orElse(new String[0]);
    }
}
