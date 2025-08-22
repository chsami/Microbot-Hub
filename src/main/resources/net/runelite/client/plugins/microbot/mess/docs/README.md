The Mess – Microbot RuneLite Client
===================================

![preview](assets/img.gif)

**The Mess** plugin for the Microbot RuneLite client automates the Hosidius Mess Hall cooking activity, providing an efficient way to train your Cooking skill. It is especially useful for ironman accounts as it requires no starting items to gain competitive experience rates. The plugin handles the entire process, from gathering ingredients to serving the final dish.

Feature Overview
----------------

**Fully Automated Cooking**

Automates the entire cooking process in The Mess hall, from gathering utensils and ingredients to serving the soldiers.

**Multiple Dish Support**

Supports cooking Servery Meat Pies, Servery Stews, and Servery Pineapple Pizzas.

**Efficient XP Rates**

Achieve excellent experience rates: ~60k/hr (Meat Pies), ~80k/hr (Stews), and ~120k+/hr (Pineapple Pizzas).

**Appreciation Hopping**

Automatically hops worlds when the appreciation for your chosen dish falls below a configurable threshold, ensuring high XP rates.

**Automatic Banking**

Before starting, the script travels to the nearby bank to deposit any items in your inventory.

**Live Status Overlay**

A built-in overlay displays your current status, XP per hour, total XP gained, and current Cooking level in real-time.

**Intelligent Flow**

Follows the precise, multi-step recipes for each dish, including combining, cooking, and cutting ingredients in the correct sequence.

Requirements
------------

*   Microbot RuneLite client

*   Plugin enabled in the Microbot plugin list

*   **Cooking Levels:**

    *   **20 Cooking** for Servery Meat Pie

    *   **25 Cooking** for Servery Stew

    *   **65 Cooking** for Servery Pineapple Pizza


Configuration Options
---------------------

Each option can be configured under: **Microbot Plugins -> The Mess**.

*   **Dish:** Select the dish you want the script to cook. Make sure you meet the required Cooking level.

*   **Minimum Appreciation:** Set the appreciation percentage threshold. If the current appreciation drops below this value, the script will hop worlds. A value above 33 may cause continuous hopping.

*   **Debug Mode:** Toggles detailed logging in the console, which is useful for troubleshooting.


How It Works
------------

1.  If you are not in the Mess Hall, the script will navigate there.

2.  Upon arrival, it checks your inventory. If it contains items, it will walk to the bank and deposit them.

3.  The script begins the cooking loop based on your selected dish:

    *   It gathers the required utensils (bowls, pie dishes, knives) and food items from the cupboards.

    *   It methodically prepares the ingredients by filling bowls with water, combining items, cooking meat, and cutting pineapples.

    *   The script follows the complete recipe until the final dish is ready.

4.  Before serving, it checks the appreciation percentage for the selected dish. If it's below the configured **Minimum Appreciation** threshold, it will hop to a different world.

5.  Finally, it serves the food at the buffet table to earn Cooking experience.

6.  The script repeats this loop, ensuring a continuous and efficient training session.


Disclaimer
----------

This plugin is intended for automating a skilling activity within the **Microbot RuneLite Client**. It provides gameplay advantages and you use it at your own risk.

Feedback
--------

Open an issue or feel free to contribute improvements.