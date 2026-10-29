package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.Value;

import java.util.List;

/** An open chat dialogue: what is said, the options to choose from, and whether "Click here to continue" shows. */
@Value
public class Dialogue {
    String text;
    List<String> options;
    boolean canContinue;

    public boolean hasOption(String part) {
        for (String option : options) {
            if (option.toLowerCase().contains(part.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
