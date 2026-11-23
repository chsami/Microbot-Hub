# Colosseum Prayers

1-tick protection prayers in the Fortis Colosseum. You play everything else: movement, attacks, offensive prayers,
gear, potions, inventory and Sol Heredit's grapple.

## How it prays

- Each monster's attack rhythm is learned from its attack animations. An overhead is switched on the tick before an
  attack is checked and released early in a tick once nothing is due, so between attacks and between waves no
  overhead is left on.
- A monster that has not attacked yet, or is overdue, is treated as able to attack on any tick, so its overhead stays
  up until its rhythm is known.
- When the same overhead is needed on back-to-back ticks it is held with an off/on pair early in each tick, so it does
  not drain. Pairs are skipped while you are moving and on ticks where two styles land together.
- The Fremennik warband is only prayed against from melee distance, where it attacks.
- Manticore: the first orb's overhead is held while it is charged and can see you, then the second and third orbs are
  answered on the two ticks after it throws. The order is read from its charged orbs.
- Sol Heredit: only Triple Parry is prayable. Protect from Melee is switched on for each spear's tick and nowhere
  else, because praying early makes Sol shut the prayer off. His other attacks are typeless.
- Two styles checked on the same tick: a known attack beats a guessed one (a monster that has not attacked yet, is
  overdue, or is pre-armed out of sight), so a guess never leaves a real hit unprotected. Between two of the same kind
  the harder-hitting one is protected.

Releases and pairs are only sent while ticks arrive on a steady cadence. After a lag spike the overhead is kept
rather than released late.

## Settings

- **Hold with same-tick pairs** (on): keep a held overhead from draining. Off: the overhead is just left on.
- **Show overlay** (on): the overhead being prayed, the attack it answers and the next one.

## Requirements

Microbot client 2.6.55 or newer.
