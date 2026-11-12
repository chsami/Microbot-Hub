package net.runelite.client.plugins.microbot.karambwans;

enum FishTravelFailure {
    HOUSE_RING_NOT_FOUND("Could not find a fairy ring or spiritual fairy tree in your house.\n"
            + "Check that you have a house teleport (Construction or Max cape, house tabs or runes),\n"
            + "that the teleport lands inside the house, and that the house has a fairy ring.\n"
            + "Script stopped."),
    HOUSE_RING_TRAVEL("Could not travel from your house fairy ring to DKP.\n"
            + "Check that you have your Dramen or Lunar staff equipped,\n"
            + "dial DKP on the ring once by hand, then start the script again.\n"
            + "Script stopped."),
    ZANARIS_RING("Could not travel from the Zanaris fairy ring to DKP.\n"
            + "Check that you have your Dramen or Lunar staff equipped,\n"
            + "dial DKP on the ring once by hand, then start the script again.\n"
            + "Script stopped.");

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
