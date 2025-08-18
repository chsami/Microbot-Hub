package net.runelite.client.plugins.microbot.thieving;

public class SelfInterruptException extends RuntimeException {
    public SelfInterruptException(String message) {
        super(message);
    }
}
