/*
 * DroSulliuscep - Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by DRO (droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

/** Inventory items that may be discarded when the run fills the backpack. */
public enum DroSulliuscepDropMode
{
    BOTH("Both"),
    BOTH_SMALL_FOSSILS("Both + small fossils"),
    BOTH_ALL_FOSSILS("Both + ALL fossils"),
    MUSHROOMS_ONLY("Mushrooms only"),
    MORT_MYRE_ONLY("Mort myre fungus only");

    private final String displayName;

    DroSulliuscepDropMode(String displayName)
    {
        this.displayName = displayName;
    }

    public boolean dropsMushrooms()
    {
        return this == BOTH || this == BOTH_SMALL_FOSSILS || this == BOTH_ALL_FOSSILS || this == MUSHROOMS_ONLY;
    }

    public boolean dropsMortMyre()
    {
        return this == BOTH || this == BOTH_SMALL_FOSSILS || this == BOTH_ALL_FOSSILS || this == MORT_MYRE_ONLY;
    }

    public boolean dropsFossil(String name)
    {
        if (name == null) return false;
        if (this == BOTH_SMALL_FOSSILS)
            return name.equalsIgnoreCase("Unidentified small fossil");
        return this == BOTH_ALL_FOSSILS && (name.equalsIgnoreCase("Unidentified small fossil")
                || name.equalsIgnoreCase("Unidentified medium fossil")
                || name.equalsIgnoreCase("Unidentified large fossil")
                || name.equalsIgnoreCase("Unidentified rare fossil"));
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
