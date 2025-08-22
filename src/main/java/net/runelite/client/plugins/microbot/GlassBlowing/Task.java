package net.runelite.client.plugins.microbot.GlassBlowing;


import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;

import java.io.IOException;
import java.util.Objects;

@Slf4j
public abstract class Task{

    public abstract boolean accept();

    public abstract int execute() throws IOException, InterruptedException;

    private String lastChatMessage;

    public void logOnceToChat(String message) {
        if (!Objects.equals(lastChatMessage, message)) {
            Microbot.log(message);
            lastChatMessage = message;
        }
    }
}
