# Left-Click Cast

Replaces the left-click **Attack** option on attackable NPCs and on players (wilderness / PvP) with a preconfigured **Cast Spell** action. The plugin stays invisible when you swap to a melee or ranged weapon, so leaving it enabled is safe.

## How it works

When an "Attack" menu entry is added for an NPC, the plugin inserts a new menu entry for the selected spell and places it above Attack, making the spell the left-click action.

All casting is dispatched through the Microbot client's existing `Rs2Magic.castOn(MagicAction, Actor)` — the plugin does not re-implement rune checks, spellbook switching, or targeting.

## Configuration

| Option | Default | Description |
| --- | --- | --- |
| **Enabled** | `true` | Master switch. When off, no menu entries are inserted. |
| **Spell** | `Fire Strike` | The spell cast on left-click. Only spells in the dropdown are supported. |
| **Require magic weapon** | `true` | When on, the Cast entry only shows while a staff, bladed staff, powered staff, or powered wand is equipped (detected via varbit `EQUIPPED_WEAPON_TYPE`). Disable to cast regardless of weapon. |

## Limitations

- **No rune auto-management.** If you run out of runes, the cast fails cleanly and you see the normal "You do not have enough ..." chat message.
- **No auto-spellbook switching.** If the selected spell is not on your current spellbook, the cast fails silently. Switch spellbooks manually.
- **The dropdown is the source of truth.** Spells not listed in the dropdown are not supported by this plugin.
- **Staff-only default.** With `Require magic weapon` enabled (default), non-magic weapon types produce normal Attack behavior. Disable the toggle if you want to cast from melee/ranged weapons as well.
- **Cooperative menu composition.** If another plugin inserts menu entries after this one on the same tick, its entry becomes the top entry instead — a known limitation of RuneLite's menu model.

## Supported spells

**Modern combat (Strike → Surge):** Wind Strike, Water Strike, Earth Strike, Fire Strike, Wind Bolt, Water Bolt, Earth Bolt, Fire Bolt, Wind Blast, Water Blast, Earth Blast, Fire Blast, Wind Wave, Water Wave, Earth Wave, Fire Wave, Wind Surge, Water Surge, Earth Surge, Fire Surge.

**Ancient combat:** Smoke / Shadow / Blood / Ice — Rush, Burst, Blitz, Barrage.

**Non-autocastable combat:** Crumble Undead, Iban Blast, Magic Dart, Saradomin Strike, Claws of Guthix, Flames of Zamorak.

**Arceuus offensive:** Ghostly Grasp, Skeletal Grasp, Undead Grasp, Inferior Demonbane, Superior Demonbane, Dark Demonbane, Lesser Corruption, Greater Corruption.

**Utility target spells:** Confuse, Weaken, Curse, Bind, Snare, Entangle, Vulnerability, Enfeeble, Stun, Tele Block, Tele Other (Lumbridge / Falador / Camelot).
