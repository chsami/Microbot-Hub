package net.runelite.client.plugins.microbot.pestcontrol;

import net.runelite.client.plugins.pestcontrol.Portal;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class PestControlCombatPlanTest {
    @Test
    void defaultPlanUsesOnlyStyleOne() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new PestControlConfig() { });
        check(plan.enabledStyles().size() == 1, "default should enable only Style 1");
        check(plan.primaryLoadout().combatStyle == PestControlCombatStyle.RANGED,
                "default Style 1 should be ranged");
        check(plan.loadoutForPortal(Portal.RED).combatStyle == PestControlCombatStyle.RANGED,
                "disabled melee should fall back to Style 1");
    }

    @Test
    void tribridPlanResolvesPortalLoadouts() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig());
        check(plan.enabledStyles().size() == 3, "tribrid should enable three styles");
        check(plan.loadoutForPortal(Portal.PURPLE).combatStyle == PestControlCombatStyle.RANGED,
                "Purple should resolve ranged");
        check(plan.loadoutForPortal(Portal.BLUE).combatStyle == PestControlCombatStyle.MAGIC,
                "Blue should resolve magic");
        check(plan.loadoutForPortal(Portal.YELLOW).combatStyle == PestControlCombatStyle.MELEE,
                "Yellow should resolve melee");
    }

    @Test
    void meleeVariantsAreIndependent() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig());
        PestControlLoadout yellow = plan.loadoutForPortal(Portal.YELLOW);
        PestControlLoadout red = plan.loadoutForPortal(Portal.RED);
        check(yellow.meleeStyle == PestControlMeleeStyle.STAB,
                "Yellow should use configured stab variant");
        check("Abyssal dagger".equals(yellow.weapon), "Yellow stab weapon mismatch");
        check("Dragon defender".equals(yellow.offhand), "Yellow stab off-hand mismatch");
        check(red.meleeStyle == PestControlMeleeStyle.CRUSH,
                "Red should use configured crush variant");
        check("Saradomin godsword".equals(red.weapon), "Red crush weapon mismatch");
        check(red.requiresEmptyOffhand(), "two-handed crush loadout should require empty off-hand");
    }

    @Test
    void openingModesExcludeRed() {
        PestControlCombatPlan mainMagic = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public PestControlCombatStyle primaryCombatStyle() {
                return PestControlCombatStyle.MAGIC;
            }

            @Override
            public PestControlOpeningMode openingMode() {
                return PestControlOpeningMode.MAIN_STYLE;
            }
        });
        check(mainMagic.openingPortal(1234) == Portal.BLUE,
                "main magic opening should choose Blue");

        PestControlCombatPlan even = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public PestControlOpeningMode openingMode() {
                return PestControlOpeningMode.EVEN_RANDOM;
            }
        });
        check(even.openingPortal(0) == Portal.PURPLE, "even roll 0 should be Purple");
        check(even.openingPortal(1) == Portal.BLUE, "even roll 1 should be Blue");
        check(even.openingPortal(2) == Portal.YELLOW, "even roll 2 should be Yellow");

        for (int roll = 0; roll < 300; roll++) {
            check(even.openingPortal(roll) != Portal.RED, "Red must never be an opening target");
        }
    }

    @Test
    void duplicateStylesAreIgnored() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new PestControlConfig() {
            @Override
            public PestControlOptionalCombatStyle secondaryCombatStyle() {
                return PestControlOptionalCombatStyle.RANGED;
            }
        });
        check(plan.enabledStyles().size() == 1, "duplicate style should be ignored");
        check(!plan.validationMessages().isEmpty(), "duplicate style should produce a warning");
    }

    @Test
    void weightedOpeningUsesNormalizedBoundaries() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig());
        check(plan.openingPortal(54) == Portal.PURPLE, "roll 54 should be Purple");
        check(plan.openingPortal(55) == Portal.BLUE, "roll 55 should be Blue");
        check(plan.openingPortal(77) == Portal.BLUE, "roll 77 should be Blue");
        check(plan.openingPortal(78) == Portal.YELLOW, "roll 78 should be Yellow");
        check(plan.openingPortal(99) == Portal.YELLOW, "roll 99 should be Yellow");
    }

    @Test
    void missingCrushLoadoutPreservesYellowWeaponStyle() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public String crushWeapon() {
                return "None";
            }
        });
        PestControlLoadout red = plan.loadoutForPortal(Portal.RED);
        check(red.combatStyle == PestControlCombatStyle.MELEE,
                "missing crush loadout should use the configured Yellow melee weapon");
        check("Abyssal dagger".equals(red.weapon),
                "Red should reuse the configured Yellow weapon");
        check("Stab".equals(red.attackOption),
                "Red must retain the configured Yellow weapon style rather than request incompatible Crush");
    }

    @Test
    void crushWeaponAloneSupportsAllMeleeLoadouts() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public PestControlCombatStyle primaryCombatStyle() {
                return PestControlCombatStyle.MELEE;
            }

            @Override
            public String slashStabWeapon() {
                return "None";
            }

            @Override
            public String crushWeapon() {
                return "Dual macuahuitl";
            }
        });
        check("Dual macuahuitl".equals(plan.primaryLoadout().weapon),
                "a crush-only melee setup must provide the primary loadout");
        check("Dual macuahuitl".equals(plan.loadoutForPortal(Portal.YELLOW).weapon),
                "Yellow should fall back to the configured crush weapon");
        check("Crush".equals(plan.loadoutForPortal(Portal.YELLOW).attackOption),
                "Yellow fallback should retain the crush weapon's mode");
        check("Dual macuahuitl".equals(plan.loadoutForPortal(Portal.RED).weapon),
                "Red should retain the configured crush weapon");
    }

    @Test
    void missingAllMeleeWeaponsIsRejected() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public PestControlCombatStyle primaryCombatStyle() {
                return PestControlCombatStyle.MELEE;
            }

            @Override
            public String slashStabWeapon() {
                return "None";
            }

            @Override
            public String crushWeapon() {
                return "None";
            }
        });
        check(!plan.primaryLoadout().isConfigured(),
                "a melee plan with no configured weapons must remain unusable");
        check(plan.validationMessages().stream()
                        .anyMatch(message -> message.contains("Style 1 MELEE weapon is not configured")),
                "a melee plan with no weapons must report a startup validation error");
    }

    @Test
    void sameMeleeWeaponVariantsRemainDistinct() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public String slashStabWeapon() {
                return "Dragon dagger(p++)";
            }

            @Override
            public String crushWeapon() {
                return "Dragon dagger(p++)";
            }
        });
        PestControlLoadout yellow = plan.loadoutForPortal(Portal.YELLOW);
        PestControlLoadout red = plan.loadoutForPortal(Portal.RED);
        check(!yellow.key().equals(red.key()),
                "same melee weapon with different variants must require separate attack modes");
        check("Stab".equals(yellow.attackOption), "Yellow should request Stab");
        check("Crush".equals(red.attackOption), "Red should request the crush option");
    }

    @Test
    void roundOutcomeIsRewardBacked() {
        check(PestControlScript.resolveRoundOutcome(true)
                        == PestControlScript.RoundOutcome.WON,
                "confirmed reward should count as a win");
        check(PestControlScript.resolveRoundOutcome(false)
                        == PestControlScript.RoundOutcome.LOST,
                "a finalized round without a reward should count as a non-win");
    }

    @Test
    void roundCountersAlwaysReconcile() {
        check(PestControlScript.reconcileLostRounds(7, 5) == 2,
                "two unrewarded rounds must not disappear from a 7/5 session");
        check(PestControlScript.reconcileLostRounds(8, 6) == 2,
                "a later paid win must retain earlier non-wins");
        check(PestControlScript.reconcileLostRounds(2, 2) == 0,
                "paid wins should not add losses");
        check(PestControlScript.reconcileLostRounds(0, 0) == 0,
                "an empty session should remain balanced");
    }

    @Test
    void roundFinalizationWaitsForEvidenceOrGrace() {
        check(PestControlScript.shouldFinalizeRound(
                        true, PestControlScript.TeamOutcome.UNKNOWN, 0L, 12_000L),
                "reward evidence should finalize immediately");
        check(PestControlScript.shouldFinalizeRound(
                        false, PestControlScript.TeamOutcome.LOST, 0L, 12_000L),
                "explicit Void Knight death should finalize immediately");
        check(!PestControlScript.shouldFinalizeRound(
                        false, PestControlScript.TeamOutcome.WON, 4_000L, 12_000L),
                "team-win chat should still allow delayed reward evidence");
        check(!PestControlScript.shouldFinalizeRound(
                        false, PestControlScript.TeamOutcome.UNKNOWN, 11_999L, 12_000L),
                "unknown result should remain pending within the grace period");
        check(PestControlScript.shouldFinalizeRound(
                        false, PestControlScript.TeamOutcome.UNKNOWN, 12_000L, 12_000L),
                "unrewarded result should finalize when grace expires");
    }

    @Test
    void awardedPointMessagesAreDeduplicated() {
        check(PestControlScript.newlyAwardedPoints(0, 4) == 4,
                "first award message should add all awarded points");
        check(PestControlScript.newlyAwardedPoints(4, 4) == 0,
                "duplicate award message should add no points");
        check(PestControlScript.newlyAwardedPoints(4, 5) == 1,
                "only newly observed awarded points should be added");
    }

    @Test
    void sessionPointsIgnoreEvidenceOrder() {
        check(PestControlScript.reconcileSessionPoints(4, 100, 104) == 4,
                "award chat and total delta must not count the same points twice");
        check(PestControlScript.reconcileSessionPoints(0, 100, 104) == 4,
                "total delta should recover a missing award message");
        check(PestControlScript.reconcileSessionPoints(4, -1, 104) == 4,
                "award chat should work without an initial total-points baseline");
    }

    @Test
    void destroyedGateVariantIsNeverOpened() {
        check(PestControlScript.isDestroyedGateId(14245),
                "Pest Control's totally destroyed gate variant must be recognized");
        check(!PestControlScript.isDestroyedGateId(14233),
                "an intact closed gate must remain interactable");
        check(!PestControlScript.isDestroyedGateId(14241),
                "a damaged but usable gate must remain interactable");
    }

    @Test
    void voidHelmetMappingFollowsCombatStyle() {
        check("Void ranger helm".equals(PestControlLoadout.helmetFor(PestControlCombatStyle.RANGED)),
                "ranged Void helmet mismatch");
        check("Void mage helm".equals(PestControlLoadout.helmetFor(PestControlCombatStyle.MAGIC)),
                "magic Void helmet mismatch");
        check("Void melee helm".equals(PestControlLoadout.helmetFor(PestControlCombatStyle.MELEE)),
                "melee Void helmet mismatch");
    }

    @Test
    void voidHelmetSwitchingRequiresCompleteSet() {
        Set<String> allHelmets = new HashSet<>(Arrays.asList(
                "Void ranger helm",
                "Void mage helm",
                "Void melee helm"));
        check(PestControlLoadout.hasCompleteVoidHelmetSet(
                        Arrays.asList(
                                PestControlCombatStyle.RANGED,
                                PestControlCombatStyle.MAGIC,
                                PestControlCombatStyle.MELEE),
                        allHelmets::contains),
                "complete enabled-style Void set should enable switching");
        allHelmets.remove("Void mage helm");
        check(!PestControlLoadout.hasCompleteVoidHelmetSet(
                        Arrays.asList(
                                PestControlCombatStyle.RANGED,
                                PestControlCombatStyle.MAGIC,
                                PestControlCombatStyle.MELEE),
                        allHelmets::contains),
                "partial Void set must leave the head slot untouched");
        check(!PestControlLoadout.hasCompleteVoidHelmetSet(
                        Arrays.asList(PestControlCombatStyle.RANGED),
                        helmet -> false),
                "non-Void setup must leave the head slot untouched");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @Test
    void crushAttackOptionAcceptsVisibleAliasesOnly() {
        check(PestControlScript.isAttackOptionMatch("crush", "crush"),
                "literal Crush should remain valid");
        check(PestControlScript.isAttackOptionMatch("pummel", "crush"),
                "Pummel should satisfy the configured crush mode");
        check(PestControlScript.isAttackOptionMatch("smash", "crush"),
                "Smash should satisfy the configured crush mode");
        check(!PestControlScript.isAttackOptionMatch("pound", "crush"),
                "unconfigured crush labels must not be accepted");
        check(!PestControlScript.isAttackOptionMatch("stab", "slash"),
                "Stab must not satisfy Slash");
        check(PestControlScript.isAttackOptionMatch("rapid", "rapid"),
                "Rapid should retain exact matching");
        check(!PestControlScript.isAttackOptionMatch("rapid fire", "rapid"),
                "partial labels must not satisfy Rapid");
    }

    private static class TribridConfig implements PestControlConfig {
        @Override
        public PestControlOptionalCombatStyle secondaryCombatStyle() {
            return PestControlOptionalCombatStyle.MAGIC;
        }

        @Override
        public PestControlOptionalCombatStyle tertiaryCombatStyle() {
            return PestControlOptionalCombatStyle.MELEE;
        }

        @Override
        public String rangedWeapon() {
            return "Magic Shortbow (i)";
        }

        @Override
        public String magicWeapon() {
            return "Trident of the swamp";
        }

        @Override
        public String magicOffhand() {
            return "Mage's book";
        }

        @Override
        public String slashStabWeapon() {
            return "Abyssal dagger";
        }

        @Override
        public String slashOffhand() {
            return "Dragon defender";
        }

        @Override
        public String crushWeapon() {
            return "Saradomin godsword";
        }

        @Override
        public PestControlYellowAttackStyle yellowMeleeStyle() {
            return PestControlYellowAttackStyle.STAB;
        }
    }
}
