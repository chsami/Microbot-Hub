# Scurrius

Banks, walks to Scurrius in the Rat Pits (private or public entrance), fights him, loots, and restocks.

## Features

- Eats and drinks prayer potions at configurable percentages; loots by name list and value.
- **Prayer mode** (config, default Always on):
  - *Always on*: the protection prayer for Scurrius's current attack style stays on.
  - *Lazy flick*: the prayer is on only for the tick before a hit lands and the tick it lands.
  - *One-tick flick*: the prayer is switched off and on every tick so it never drains.
  - Flicking learns the animation-to-hit delay of each attack style from the hits it takes (two equal observations per
    style) and acts on game ticks. Until all three styles are learned, after any unblocked hit, and while giant rats
    attack you, it holds the prayer on (always-on behaviour).
- **Falling rocks**: tracks the rock tiles itself, steps to the nearest safe tile (preferring tiles where Scurrius can
  still be attacked) and does not click Attack until the rocks are gone from your tile.
- **Emergency teleport**: with food left at or under the threshold (default 2) and hitpoints under the percent of your
  real hitpoints level (default 50), breaks a Varrock teleport tab, banks, walks back and fights again. With no tab
  it keeps eating what is left. The old rule (no food and under 25 hitpoints) still applies.

- **Lazy flick** is not perfectly regular: the prayer goes on one tick before the hit (usually), two ticks before, or on the
  animation tick, stays on for a configurable hold (default 1-2 ticks) after the hit, is switched off a few tens of
  milliseconds into the tick, and now and then stays on for a whole attack (setting "Lazy flick variation"). A hit is never
  left unprotected because of this; a new attack during the hold only switches the style.
- **Restock trips** (off by default): after a random 2-5 kills (configurable), once Scurrius is dead, the loot is picked
  up and there is food left, it teleports out (or walks without a tab), banks, and comes back. Every bank visit restarts
  the count.
- **Breaks** (off by default): after a random number of completed bank visits it takes a random-length break right after
  restocking, logged out if configured, and logs in again with the client's active login profile (the Auto Login plugin or
  account selector must have one). Use this or Microbot's own Break Handler, not both: if the Break Handler is on a break,
  ours is skipped or dropped. Stopping the plugin during a break leaves you logged out.
- **Mixed prayer** (a prayer mode option, not the default): each kill picks always on or lazy flick by weight (default 50/50).
- **Anti-ban** (section in the config): prayers clicked with the mouse on the icon (an instant menu click when the tick is
  nearly over, so timing is never at risk), Microbot's antiban with a combat activity (no action cooldown, no micro
  breaks), and rare habits between kills: mouse off-screen (off by default), a look at the skills tab, a small camera turn,
  1-8 second pauses after banking and before the room, a re-click up to a tick late, an eat a few percent early.
  Nothing in the fight is ever delayed.
- **Special attack** (off by default): whenever there is enough energy (the weapon's own cost when known, else 50%) and it
  is attacking Scurrius himself (never rats, never while dodging), it turns the spec on through the orb after a short random
  delay and counts it only when the energy drops. Optional spec weapon from the inventory (the main weapon and shield are
  put back afterwards) and an optional "skip spec under boss HP" threshold.
- **Inventory Setup** (optional): pick a saved Inventory Setup in the config. The bank loads its gear and inventory (loot is
  banked as anything not in the setup), and the food, prayer-dose and teleport-tab amounts that count as "full supplies" come from
  the setup (tabs by stack quantity). If the bank cannot supply the setup the script stops with a message naming the items.
  With no setup chosen the food, potion and teleport amounts in the config are used.
- **Overlay**: runtime, experience and experience per hour for Attack, Strength, Defence, Ranged, Magic and Hitpoints
  (skills with no gain are hidden), kills and kills per hour, kills since the last bank, and the break countdown.

## Requirements

Food, prayer potions and Varrock teleport tabs in the bank (amounts are configurable).
