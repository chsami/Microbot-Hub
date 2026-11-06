# Fourtifys Barrows

Runs Barrows trips. It travels to Barrows, kills the six brothers with
the matching protection prayer, solves the tunnel puzzle and loots the
chest. It then teleports to Ferox Enclave with a Ring of dueling,
restores at the pool, banks the loot, restocks and goes again.

## Travel methods

| Travel method | Requirements | Items |
| --- | --- | --- |
| Barrows teleport tablet | Spoken to Drezel after Priest in Peril | A Barrows teleport tablet in the inventory |
| Mort'ton teleport scroll | Spoken to Drezel after Priest in Peril | A Mort'ton teleport scroll in the inventory |
| Barrows Teleport (Arceuus) | 83 Magic and the Arceuus spellbook; spoken to Drezel after Priest in Peril | 2 law, 2 soul and 1 blood rune |
| Morytania legs 3 | Hard Morytania Diary | Morytania legs 3 in the inventory |
| Morytania legs 4 | Elite Morytania Diary | Morytania legs 4 in the inventory |
| Shades of Mort'ton | Shades of Mort'ton quest. The teleport has a 20-minute cooldown, so the plugin often waits at Ferox Enclave before using it | None |

## Setup

1. Complete Priest in Peril and start His Faithful Servants.
2. Have 43 Prayer or higher for the protection prayers.
3. Wear or carry a charged Ring of dueling, and carry a spade.
4. Put food, prayer potions and the items for your travel method in
   your bank.
5. Choose **Teleport to Barrows** from the table above and meet its
   requirements.
6. Optionally enter gear to swap to, as comma-separated item IDs. Each
   list is blank by default, which keeps what you wear. Wear or carry
   every listed item: the plugin checks this at start.
   - **Magic equipment IDs**: for every brother except Ahrim. Set
     autocast or use a powered staff.
   - **Ahrim equipment IDs**: ranged gear for Ahrim.
   - **Tunnel equipment IDs**: melee gear for tunnel monsters.
7. Set **Food item ID**, **Food count**, **Prayer potion count** and
   their thresholds. Use **Retained inventory IDs** and
   **Additional supplies/ammunition** for runes, ammunition and other
   items you keep or restock.
8. Enable the plugin at Ferox Enclave or at Barrows.

## While it runs

- **Emergency teleport:** at Barrows only, low Hitpoints make the plugin
  use the Ring of dueling to Ferox Enclave, turn off the protection
  prayer and stop. Set the level in **At or below Hitpoints (%)**. Elsewhere,
  low Hitpoints start the normal bank trip, and the Ferox pool heals you.
- **Auto-retaliate:** the plugin turns it off so that only the planned
  fights count toward the reward. It stays off after the plugin stops;
  turn it back on yourself.
- **Stops:** when something stops the plugin, it posts one red chat
  message, "Fourtifys Barrows stopped:" followed by the reason and what
  to do. It then switches itself off. A stop during a fight at Barrows
  first teleports you to Ferox Enclave.
