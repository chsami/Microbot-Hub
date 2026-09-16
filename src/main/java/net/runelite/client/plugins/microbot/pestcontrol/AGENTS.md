# Pest Control Plugin Rules

## Strategy Invariants

- Vulnerable portal pressure is primary. A Spinner healing the selected portal preempts the portal attack.
- Activity recovery begins at the configured low threshold and remains committed until the recovery target is reached.
- Brawlers are obstruction targets only: flank first, then attack only when the route remains blocked.
- Never path repeatedly through closed perimeter gates or the southwest no-go grid.
- Treat destroyed gate object `14245` as passable even when it advertises `Open`; never click that action repeatedly.
- Keep a selected vulnerable portal stable unless stronger game evidence invalidates it.
- Do not chase the opposite opening flank when the first portal drop would sacrifice the round's activity.
- Pre-position at the last shielded portal without abandoning active local combat prematurely.

## Combat Invariants

- Style 1 is mandatory and is the fallback for incomplete optional profiles.
- Verify weapon, off-hand, optional Void helmet and combat mode before the final attack interaction.
- A blank or `None` off-hand means the shield slot must be empty.
- Complete enabled-style Void helmet sets may switch automatically; incomplete/non-Void setups leave the head slot untouched.
- Magic autocast is prepared once per configured weapon and then treated as remembered.
- Specials are portal-only and require enough current energy for that weapon's special.
- Do not switch away from ongoing portal combat solely to prepare a later target.

## Result Invariants

- Finalize each round exactly once.
- Reward chat or a reliable positive total-points delta is authoritative paid-win evidence.
- Portal destruction count and team outcome are diagnostic, not authoritative reward evidence.
- Reconcile counters so `roundsPlayed == roundsWon + roundsLost` after every finalization.
- Deduplicate point-award messages and preserve evidence across a rapid boat transition.

## Runtime Diagnosis

Correlate state with tile, movement, animation, interaction, destination,
activity, portal vulnerability, gate variant, target NPC, loadout, combat mode,
special energy, point delta and timestamps. One delayed scheduler pass is not a
stall.

Prioritize these logs:

- `Pest Control state:`
- `Pest Control round ended`
- `Pest Control round accounted:`
- `Pest Control session:`
- `Pest Control activity recovery started`
- `Pest Control activity recovered`
- `Pest Control bypassing destroyed`
- `Pest Control gate diagnostic`
- `Pest Control watchdog recovery`
- `Pest Control command guard`
- `Pest Control combat config:`

Log transitions and bounded retries, not every scheduler iteration.
