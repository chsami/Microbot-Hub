package net.runelite.client.plugins.microbot.karambwans;

final class FairyRingDial {
    static final int FULL_ROTATION = 2048;
    static final int STEP = 512;

    private FairyRingDial() {
    }

    static String findAction(String[] actions, String... wanted) {
        if (actions == null) {
            return null;
        }
        for (String want : wanted) {
            for (String action : actions) {
                if (action != null && action.replaceAll("<[^>]*>", "").trim().equalsIgnoreCase(want)) {
                    return want;
                }
            }
        }
        return null;
    }

    static int rotationFor(char letter) {
        switch (Character.toUpperCase(letter)) {
            case 'A':
            case 'I':
            case 'P':
                return 0;
            case 'B':
            case 'J':
            case 'Q':
                return STEP;
            case 'C':
            case 'K':
            case 'R':
                return 2 * STEP;
            case 'D':
            case 'L':
            case 'S':
                return 3 * STEP;
            default:
                return -1;
        }
    }

    static int clockwiseTurns(int currentRotation, int desiredRotation) {
        int clockwise = Math.floorMod(currentRotation - desiredRotation, FULL_ROTATION);
        int anticlockwise = Math.floorMod(desiredRotation - currentRotation, FULL_ROTATION);
        if (clockwise <= anticlockwise) {
            return clockwise / STEP;
        }
        return -(anticlockwise / STEP);
    }
}
