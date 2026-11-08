# Fourtifys Tormented Demons

Kills Tormented Demons in a single-combat room of the Ancient Guthixian
Temple. It switches protection prayers and equipment, steps off fire
bombs and picks up drops. When supplies run out, it teleports to Ferox
Enclave with a Ring of dueling, banks the loot, restocks, drinks from
the Pool of Refreshment and goes again.

## Setup

1. Complete While Guthix Sleeps.
2. Have 43 Prayer or higher for the protection prayers.
3. Wear or carry a charged Ring of dueling, and keep spares in your bank.
4. Put food, prayer potions or super restores, Guthixian temple
   teleports and any other supplies in your bank. Set bank withdrawals
   to **Item**. If your bank has a PIN, save it in your Microbot profile,
   or type it yourself when the plugin asks.
5. Choose the **Room**.
6. Enter your gear as comma-separated item IDs in two or more of
   **Magic equipment IDs**, **Ranged equipment IDs** and **Melee
   equipment IDs**. When the demon protects against the worn style, the
   plugin wears the next style in the order Magic, Ranged, Melee. All
   three are blank at first, and the plugin does not start until two are
   filled in.
7. Set **Food item ID**, **Food count**, **Prayer restore count**,
   **Temple teleports** and their thresholds. Use **Retained inventory
   IDs** and **Additional supplies/ammunition** for runes, ammunition and
   other items you keep or restock.
8. Set **Minimum value** and **Always take** for the drops to pick up.
9. Enable the plugin at Ferox Enclave or inside the temple.

At low Hitpoints, the plugin teleports to Ferox Enclave with the Ring of
dueling. Set the level in **At or below HP (%)**.

When the plugin stops, it says why and what to do next in the game chat
and with a RuneLite notification. It waits while Microbot scripts are
paused, including Break Handler breaks.

## Not supported

Multi-demon rooms, special attacks, thralls, demonbane spells, Slayer
tasks and the route through the temple without teleport scrolls.
