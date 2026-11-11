/* Copyright (c) 2026, DRO (droplugins). SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.microbot.drofirecape;

/** The two cave input choices share every fight decision, as in the Inferno and Colosseum arenas. */
public enum FcFightInput {
    /** The same menu action, carried by a Natural mouse travel and press. */
    NATURAL_MOUSE("Natural mouse"),
    /** The menu action is sent to the client directly; walking still uses the Natural mouse. */
    INVOKES("Direct invokes");

    private final String label;
    FcFightInput(String label){this.label=label;}
    @Override public String toString(){return label;}
}
