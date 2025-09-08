package net.runelite.client.plugins.microbot.manager;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.manager.signalr.SignalRClient;

@Slf4j
public class MicrobotManagerScript extends Script {
    private MicrobotManagerConfig config;
    private SignalRClient signalRClient;

    public boolean run(MicrobotManagerConfig config) {
        this.config = config;
        log.info("Starting Microbot Manager Script");
        signalRClient = new SignalRClient();
        signalRClient.connect();
        return true;
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Microbot Manager Script");
        if (signalRClient != null) {
            signalRClient.disconnect();
        }
        super.shutdown();
    }
}
