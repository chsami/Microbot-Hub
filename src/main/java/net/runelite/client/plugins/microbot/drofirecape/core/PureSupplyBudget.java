/*
 * Copyright (c) 2026, DRO (droplugins).
 * SPDX-License-Identifier: BSD-2-Clause
 * Free and open source. Retain this notice and the LICENSE.txt terms.
 * Developed with OpenAI Codex; see CREDITS.txt. Third-party notices follow.
 */
package net.runelite.client.plugins.microbot.drofirecape.core;

/** Confirmed brew doses, not click attempts, accrue debt. A prepared heal never
 * outranks critical prayer or the restore due after three confirmed doses. */
public final class PureSupplyBudget {
    private PureSupplyBudget() { }
    public static boolean restoreFirst(int prayer,int confirmedBrews) {
        return prayer<=8||confirmedBrews>=3;
    }
    public static boolean brewAllowed(int prayer,int confirmedBrews,boolean pure,boolean restoreAvailable) {
        return pure&&!restoreAvailable||brewAllowed(prayer,confirmedBrews);
    }
    public static boolean brewAllowed(int prayer,int confirmedBrews) {
        return !restoreFirst(prayer,confirmedBrews);
    }
}
