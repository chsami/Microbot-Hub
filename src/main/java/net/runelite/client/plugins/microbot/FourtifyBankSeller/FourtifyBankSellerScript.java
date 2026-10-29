package net.runelite.client.plugins.microbot.FourtifyBankSeller;

import net.runelite.client.plugins.microbot.fourtify.core.activity.ActivityQueue;
import net.runelite.client.plugins.microbot.fourtify.core.client.ExchangeActions;
import net.runelite.client.plugins.microbot.fourtify.core.client.ExchangeReader;
import net.runelite.client.plugins.microbot.fourtify.core.client.TaskScript;
import net.runelite.client.plugins.microbot.FourtifyBankSeller.tasks.BankSeller;

public class FourtifyBankSellerScript extends TaskScript {
    public boolean run(FourtifyBankSellerConfig config) {
        ActivityQueue queue = BankSeller.queue();
        return run(queue.tasks(), new ExchangeReader(queue.surroundings()), new ExchangeActions(),
                BankSeller.stuckTimer(), queue.stops());
    }
}
