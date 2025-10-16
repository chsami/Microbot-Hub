package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

public class DismissNpcEvent implements BlockingEvent {

    private final EventDismissConfig config;

    public DismissNpcEvent(EventDismissConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        if (randomEventNPC == null) {
            return false;
        }
        return Rs2Npc.hasLineOfSight(randomEventNPC);
    }

    @Override
    public boolean execute() {
        Rs2NpcModel npc = Rs2Npc.getRandomEventNPC();
        if (npc == null)
            return true;

        String name = npc.getName();

        if (name == null)
            return true;

        if (("Count Check".equals(name) && !config.dismissCountCheck()) ||
                ("Genie".equals(name) && !config.dismissGenie())) {
            talkTo(npc);
            return !validate();
        }

        dismiss(npc);
        return !validate();
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.LOWEST;
    }

    private void talkTo(Rs2NpcModel npc) {
        Rs2Npc.interact(npc, "Talk-to");
        Rs2Dialogue.sleepUntilHasContinue();
        Rs2Dialogue.clickContinue();
    }

    private void dismiss(Rs2NpcModel npc) {
        Rs2Npc.interact(npc, "Dismiss");
        Global.sleepUntil(() -> Rs2Npc.getRandomEventNPC() == null);
    }
}
