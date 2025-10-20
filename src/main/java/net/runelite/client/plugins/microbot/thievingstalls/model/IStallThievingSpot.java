package net.runelite.client.plugins.microbot.thievingstalls.model;

public interface IStallThievingSpot {
    void thieve();
    void bank();

    Integer[] getItemIdsToDrop();
}
