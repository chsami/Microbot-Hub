package net.runelite.client.plugins.microbot.karambwans;

enum FishTravelFailure {
    HOUSE_RING_NOT_FOUND("Could not find a fairy ring or spiritual fairy tree in your house. Check that you have a house teleport (Construction or Max cape, house tabs or runes), that the teleport lands inside the house, and that the house has a fairy ring. Script stopped."),
    HOUSE_RING_TRAVEL("Could not travel from your house fairy ring to DKP. Dial DKP on the ring once by hand, then start the script again. Script stopped."),
    ZANARIS_RING("Could not reach the karambwan fishing spot from the Zanaris fairy ring. Travel to DKP once by hand so it is the ring's last destination, then start the script again. Script stopped.");

    private final String message;

    FishTravelFailure(String message) {
        this.message = message;
    }

    String getMessage() {
        return message;
    }

    static boolean shouldStop(FishTravelFailure failure, boolean houseTeleportLanded, int failedAttempts, int maxAttempts) {
        if (failure == HOUSE_RING_NOT_FOUND && houseTeleportLanded) {
            return true;
        }
        return failedAttempts >= maxAttempts;
    }
}
