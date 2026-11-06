package net.runelite.client.plugins.microbot.fourtifysagility.util.action;

import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

/** Runs the Microbot API's blocking spell operations without blocking client ticks. */
@Singleton
public final class Spellbook
{
    @Inject private Client client;
    @Inject private AgilityActions actions;
    public void alchemy(int itemId)
    {
        Rs2ItemModel item = Rs2Inventory.get(itemId);
        if (item == null)
        {
            return;
        }
        int version = actions.generation();
        MagicAction spell = client.getBoostedSkillLevel(Skill.MAGIC) < 55
            ? MagicAction.LOW_LEVEL_ALCHEMY : MagicAction.HIGH_LEVEL_ALCHEMY;
        actions.enqueueBlocking(() -> Rs2Inventory.slotContains(item.getSlot(), itemId), () ->
        {
            if (!Rs2Magic.cast(spell))
            {
                return;
            }
            Widget widget = Rs2Widget.findWidget(spell.getName());
            if (widget != null && Global.sleepUntil(() -> version != actions.generation() || Microbot.getClientThread()
                .runOnClientThreadOptional(() -> client.getSelectedWidget() != null
                    && client.getSelectedWidget().getId() == widget.getId()).orElse(false), 5000)
                // The game shows the inventory again after the spell is selected; until then the item cannot be clicked.
                && Global.sleepUntil(() -> version != actions.generation() || Microbot.getClientThread()
                    .runOnClientThreadOptional(() -> client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)
                        == InterfaceTab.INVENTORY.getVarcIntIndex()).orElse(false), 3000)
                && version == actions.generation() && !Thread.currentThread().isInterrupted()
                && Rs2Inventory.slotContains(item.getSlot(), itemId))
            {
                Rs2Inventory.interact(item, "Cast");
                return;
            }
            if (widget != null)
            {
                // A spell left selected turns every later click into a cast, so the course would stall.
                Microbot.getClientThread().runOnClientThreadOptional(() ->
                {
                    Widget selected = client.getSelectedWidget();
                    if (client.isWidgetSelected() && selected != null && selected.getId() == widget.getId())
                    {
                        client.setWidgetSelected(false);
                    }
                    return true;
                });
            }
        });
    }

    public void camelotTeleport(int operation, BooleanSupplier canDispatch, Runnable onDispatch, Runnable onUnavailable)
    {
        int version = actions.generation();
        actions.enqueueBlocking(canDispatch, () ->
        {
            if (!Rs2Magic.canCast(MagicAction.CAMELOT_TELEPORT))
            {
                Microbot.getClientThread().invoke(() ->
                {
                    if (version == actions.generation())
                    {
                        onUnavailable.run();
                    }
                });
                return;
            }
            boolean current = Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                if (version != actions.generation() || !canDispatch.getAsBoolean())
                {
                    return false;
                }
                onDispatch.run();
                return true;
            }).orElse(false);
            if (current && version == actions.generation() && !Thread.currentThread().isInterrupted()
                && !Rs2Magic.cast(MagicAction.CAMELOT_TELEPORT, "Cast", operation))
            {
                Microbot.getClientThread().invoke(() ->
                {
                    if (version == actions.generation())
                    {
                        onUnavailable.run();
                    }
                });
            }
        });
    }

}
