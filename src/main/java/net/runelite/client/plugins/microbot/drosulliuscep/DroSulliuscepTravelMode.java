/*
 * DroSulliuscep - Copyright (c) 2026, Dorian (DRO / droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Created by Dorian (DRO / droplugins). Free and open source.
 * Reuse is permitted only under LICENSE.txt: every copy, fork or excerpt of this
 * file must keep this notice, the author credit and the license text. See CREDITS.txt.
 */
package net.runelite.client.plugins.microbot.drosulliuscep;

import net.runelite.api.coords.WorldPoint;

public enum DroSulliuscepTravelMode
{
    FEROX_DUELING("Ferox / Ring of dueling", false, false, "Ferox Enclave", SulliuscepRouteData.FEROX_BANK_APPROACH),
    POH_CASTLE_WARS("POH / Castle Wars / Ring of dueling", true, false, "Castle Wars", new WorldPoint(2443, 3083, 0)),
    POH_CRAFTING("POH / Crafting Guild / Crafting cape", true, true, "Teleport", new WorldPoint(2936, 3281, 0));

    private final String label;
    final boolean poh;
    final boolean crafting;
    final String bankAction;
    final WorldPoint bankTile;

    DroSulliuscepTravelMode(String label, boolean poh, boolean crafting, String bankAction, WorldPoint bankTile)
    {
        this.label = label;
        this.poh = poh;
        this.crafting = crafting;
        this.bankAction = bankAction;
        this.bankTile = bankTile;
    }

    boolean bankNearby(WorldPoint point)
    {
        return point != null && point.getPlane() == bankTile.getPlane() && point.distanceTo(bankTile) <= 25;
    }

    boolean travelItemsReady(boolean ring, boolean craftingCape, boolean pendant, boolean constructionCape, boolean houseTab)
    {
        return (crafting ? craftingCape : ring) && (poh ? constructionCape || houseTab : pendant);
    }

    @Override public String toString() { return label; }
}
