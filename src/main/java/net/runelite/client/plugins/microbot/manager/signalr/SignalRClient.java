package main.java.net.runelite.client.plugins.microbot.manager.signalr;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SignalRClient {
    private HubConnection hubConnection;

    public void connect() {
        String hubUrl = "http://localhost:5000/signalr"; // Or get from config
        hubConnection = HubConnectionBuilder.create(hubUrl).build();

        // Register handlers for events from .NET service
        hubConnection.on("ScheduleEvent", (String scheduleJson) -> {
            log.info("Received schedule: {}", scheduleJson);
            // TODO: Parse and handle schedule
        }, String.class);

        hubConnection.on("LoginEvent", (String username, String password) -> {
            log.info("Received login: {} / {}", username, password);
            // TODO: Handle login
        }, String.class, String.class);

        hubConnection.on("WorldSelectEvent", (int worldId) -> {
            log.info("Received world selection: {}", worldId);
            // TODO: Handle world selection
        }, Integer.class);

        hubConnection.start().blockingAwait();
        log.info("SignalR connection started.");
    }

    public void disconnect() {
        if (hubConnection != null) {
            hubConnection.stop();
            log.info("SignalR connection stopped.");
        }
    }
}
