package net.runelite.client.plugins.microbot.irkedmlm;

import net.runelite.client.plugins.microbot.irkedmlm.enums.DepositMethod;

/**
 * Pure, client-free decision logic for MLM startup preflight. Kept dependency-free (only the
 * DepositMethod enum) so it is unit-checkable without a running client.
 */
public final class MlmPreflightLogic {
    private MlmPreflightLogic() {}

    public enum HammerStep { DONE, WIELD_OFFHAND, SWAP_THEN_WIELD, WITHDRAW, FALLBACK_CRATE }

    /**
     * Route toward "an Imcando hammer that survives Deposit-All". Any equipped variant (main- or
     * off-hand) already survives — equipment is never swept — so an equipped hammer is DONE. Only an
     * Imcando sitting in the inventory needs equipping (off-hand preferred, so an equipped pickaxe
     * keeps its weapon slot). First match wins.
     */
    public static HammerStep nextHammerStep(boolean offhandEquipped, boolean mainhandEquipped,
                                            boolean offhandInInv, boolean mainhandInInv, boolean inBank) {
        if (offhandEquipped || mainhandEquipped) return HammerStep.DONE;
        if (offhandInInv)  return HammerStep.WIELD_OFFHAND;
        if (mainhandInInv) return HammerStep.SWAP_THEN_WIELD;
        if (inBank)        return HammerStep.WITHDRAW;
        return HammerStep.FALLBACK_CRATE;
    }

    /**
     * The deposit method actually safe to use. Deposit-All sweeps the whole inventory, so it is only
     * safe with a gem bag when the bag's slot-1 lock is actually established; otherwise degrade to Items.
     */
    public static DepositMethod resolveEffectiveDepositMethod(DepositMethod configured, boolean useGemBag,
                                                              boolean lockEstablished) {
        if (configured != DepositMethod.ALL) return configured; // ITEMS is always safe
        if (!useGemBag) return DepositMethod.ALL;                // nothing in inv to protect
        return lockEstablished ? DepositMethod.ALL : DepositMethod.ITEMS;
    }
}
