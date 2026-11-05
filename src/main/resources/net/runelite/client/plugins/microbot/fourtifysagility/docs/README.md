# Fourtifys Agility

Runs laps of the Agility course you select. It crosses each obstacle,
picks up marks of grace, and can eat food and cast High Level Alchemy
between obstacles.

| Course | Agility level required | Quests required | Items |
| --- | --- | --- | --- |
| Gnome | 1 | None | None |
| Draynor | 1 | None | None |
| Al Kharid | 20 | None | None |
| Varrock | 30 | None | None |
| Canifis | 40 | Priest in Peril | None |
| Falador | 50 | None | None |
| Seers Village | 60 | None | None |
| Pollnivneach | 70 | None | None |
| Rellekka | 80 | None | None |
| Ardougne | 90 | None | None |
| Agility Pyramid | 30 | None | None |
| Brimhaven Agility Arena | 1 | None | 200 coins for each entry |
| Colossal Wyrm | 50 (62 for the advanced route) | Children of the Sun | None |
| Wilderness | 52 | None | None |
| Prifddinas | 75 | Song of the Elves | None |
| Ape Atoll | 48 | Monkey Madness I, up to Chapter 2 | A small or medium ninja monkey greegree, or the Kruk monkey greegree, worn |

## Setup

1. Meet the course's requirements in the table above.
2. Choose the course in **Course**.
3. Stand at the course, within 20 tiles of its first obstacle. The
   plugin does not travel between courses. When a lap ends further
   from the first obstacle, as on Seers Village, Pollnivneach,
   Rellekka and Al Kharid, it walks back on its own.
4. Enable the plugin. It waits while Microbot scripts are paused,
   including Break Handler breaks.

Course settings:

- **Brimhaven Agility Arena**: **Voucher reward on exit** can spend
  vouchers when you leave.
- **Agility Pyramid**: **Turn in Pyramid tops** sets when tops are
  handed in.
- **Seers Village**: **Use diary teleport** casts Camelot Teleport
  after each lap. It needs the Kandarin hard diary, the teleport set to
  Seers' Village (right-click the spell, Toggle-location), 45 Magic,
  and 1 law and 5 air runes or an air staff.

Optional settings:

- **Food item ID** and **Eat below (Hitpoints)**: carry that food,
  for example 385 for sharks.
- **Use High Alchemy** and **Alchemy item ID**: carry the item and
  the runes. The default item, 856, is a noted yew longbow.
- **Ignore marks of grace**: leave marks on the ground.
