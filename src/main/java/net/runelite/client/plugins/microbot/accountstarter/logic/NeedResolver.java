package net.runelite.client.plugins.microbot.accountstarter.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;
import net.runelite.client.plugins.microbot.accountstarter.logic.Resolution.Kind;
import net.runelite.client.plugins.microbot.accountstarter.model.ApTask;
import net.runelite.client.plugins.microbot.accountstarter.model.RewardItem;
import net.runelite.client.plugins.microbot.accountstarter.model.TaskState;

public final class NeedResolver {
    private NeedResolver() {}

    /** Finlay gives a net to anyone without one, so only the skills decide. */
    public static boolean canCatchAndCook(AccountSnapshot s) {
        return !s.isFoodBlocked() && XpGuard.allowed(Action.FISH, s.getProtectedSkills()) && XpGuard.allowed(Action.COOK, s.getProtectedSkills());
    }

    public static List<Resolution> resolveAll(List<Need> needs, AccountSnapshot snap, boolean buyAllowed, int budgetLeft,
                                              LongUnaryOperator guidePrice, double buyBuffer) {
        List<Resolution> out = new ArrayList<>();
        int left = budgetLeft;
        for (Need n : needs) {
            Resolution r = resolve(n, snap, buyAllowed, left, guidePrice, buyBuffer);
            if (r.getKind() == Kind.BUY) left -= r.getCoinsNeeded();
            out.add(r);
        }
        return out;
    }

    public static Resolution resolve(Need need, AccountSnapshot snap, boolean buyAllowed, int budgetLeft, LongUnaryOperator guidePrice,
                                     double buyBuffer) {
        long have = need.getAnyOf().stream().mapToLong(snap::owned).sum();
        if (have >= need.getMinimum()) {
            return new Resolution(Kind.OWNED, need.getLabel() + ": owned", 0, null);
        }
        ApTask reward = claimable(need, snap);
        if (reward != null) {
            return new Resolution(Kind.CLAIM_REWARD, need.getLabel() + ": claim " + reward.name(), 0, reward);
        }
        if (!snap.isBankKnown()) {
            return new Resolution(Kind.OPEN_BANK, "bank not read yet", 0, null);
        }
        FreeSource free = need.getFree();
        int qty = Surplus.buyQuantity(need.getBuyItemId(), (int) (need.getQuantity() - have), buyBuffer);
        long cost = need.getBuyItemId() == 0 ? 0 : guidePrice.applyAsLong(need.getBuyItemId()) * qty;
        int coinsNeeded = (int) Math.min(cost, Integer.MAX_VALUE);
        boolean affordable = need.getBuyItemId() != 0 && buyAllowed && cost <= Math.min(budgetLeft, snap.getCoins());
        if (free == FreeSource.CATCH_AND_COOK) {
            if (canCatchAndCook(snap)) return new Resolution(Kind.FREE, need.getLabel() + ": catch and cook", 0, null);
        } else if (free != null) {
            return new Resolution(Kind.FREE, need.getLabel() + ": free from " + free.tutorName(), 0, null);
        }
        if (affordable) {
            return new Resolution(Kind.BUY, need.getLabel() + ": buy " + need.getBuyName(), coinsNeeded, null, qty);
        }
        if (need.getBuyItemId() != 0 && buyAllowed && have < need.getMinimum()) {
            int startQty = Surplus.buyQuantity(need.getBuyItemId(), (int) (need.getMinimum() - have), buyBuffer);
            long startCost = guidePrice.applyAsLong(need.getBuyItemId()) * startQty;
            if (startCost <= Math.min(budgetLeft, snap.getCoins())) {
                return new Resolution(Kind.BUY, need.getLabel() + ": buy a start of " + need.getBuyName(), (int) startCost, null,
                        startQty);
            }
        }
        if (have >= need.getLeast()) {
            return new Resolution(Kind.OWNED, need.getLabel() + ": " + have + " owned, all there is for now", 0, null);
        }
        if (need.getBuyItemId() == 0) return new Resolution(Kind.PARK, need.getLabel() + ": cannot be bought", 0, null);
        if (free == FreeSource.CATCH_AND_COOK && !buyAllowed) {
            return new Resolution(Kind.PARK, "no food: Fishing/Cooking protected and buying off", coinsNeeded, null);
        }
        if (!buyAllowed) {
            return new Resolution(Kind.PARK, need.getLabel() + ": buying is off", coinsNeeded, null);
        }
        return new Resolution(Kind.PARK, "need " + coinsNeeded + " coins for " + need.getLabel(), coinsNeeded, null);
    }

    private static ApTask claimable(Need need, AccountSnapshot snap) {
        if (snap.getProgress() == null) return null;
        for (ApTask t : ApTask.values()) {
            if (snap.getProgress().of(t) != TaskState.DONE_UNCLAIMED) continue;
            for (RewardItem r : t.f2pRewards()) {
                if (need.getAnyOf().contains(r.getItemId())) return t;
            }
        }
        return null;
    }
}
