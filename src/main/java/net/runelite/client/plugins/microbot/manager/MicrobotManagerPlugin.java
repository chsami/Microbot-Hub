package net.runelite.client.plugins.microbot.manager;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.manager.signalr.SignalRClient;

@Slf4j
public class MicrobotManagerPlugin extends Script {
    private SignalRClient signalRClient;

    @Override
    public boolean run() {
        log.info("Starting Microbot Manager Plugin");
        signalRClient = new SignalRClient();
        signalRClient.connect();
        return true;
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Microbot Manager Plugin");
        if (signalRClient != null) {
            signalRClient.disconnect();
        }
        super.shutdown();
    }
}
