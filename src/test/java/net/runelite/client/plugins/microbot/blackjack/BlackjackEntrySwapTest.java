package net.runelite.client.plugins.microbot.blackjack;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.plugins.microbot.blackjack.entryswapper.BlackjackEntrySwapPlugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackEntrySwapTest
{
    @Test
    void bothBanditsAndThugSwapStandingKnockoutAndUnconsciousPickpocket() throws Exception
    {
        for (int level : new int[]{41, 56, 55})
        {
            for (int animation : new int[]{-1, AnimationID.HUMAN_UNCONSCIOUS})
            {
                NPC target = npc(level == 55 ? "Menaphite Thug" : "Bandit", level, animation, 10);
                AtomicBoolean force = new AtomicBoolean();
                String desired = animation == -1 ? "Knock-Out" : "Pickpocket";
                MenuEntry[] entries = {entry(target, "Knock-Out", force),
                        entry(target, "Pickpocket", force), entry(target, "Talk-to", force)};
                AtomicReference<MenuEntry[]> live = new AtomicReference<>(entries);
                plugin(live, false).onPostMenuSort(new PostMenuSort());
                assertEquals(desired, live.get()[2].getOption());
                assertTrue(force.get());
            }
        }
    }

    @Test
    void unsupportedBanditsAndOpenMenusRemainUntouched() throws Exception
    {
        for (boolean menuOpen : new boolean[]{false, true})
        {
            NPC target = npc("Bandit", menuOpen ? 41 : 57, -1, 10);
            AtomicBoolean force = new AtomicBoolean();
            MenuEntry top = entry(target, "Talk-to", force);
            AtomicReference<MenuEntry[]> live = new AtomicReference<>(new MenuEntry[]{
                    entry(target, "Knock-Out", force), top});
            plugin(live, menuOpen).onPostMenuSort(new PostMenuSort());
            assertSame(top, live.get()[1]);
            assertFalse(force.get());
        }
    }

    @Test
    void overlappingNpcNeverBorrowsAnotherTargetsAction() throws Exception
    {
        AtomicBoolean force = new AtomicBoolean();
        NPC other = npc("Bandit", 41, -1, 9);
        NPC target = npc("Bandit", 56, -1, 10);
        MenuEntry top = entry(target, "Talk-to", force);
        AtomicReference<MenuEntry[]> live = new AtomicReference<>(new MenuEntry[]{
                entry(other, "Knock-Out", force), entry(target, "Pickpocket", force), top});
        plugin(live, false).onPostMenuSort(new PostMenuSort());
        assertSame(top, live.get()[2]);
        assertFalse(force.get());
    }

    private static BlackjackEntrySwapPlugin plugin(AtomicReference<MenuEntry[]> entries, boolean open) throws Exception
    {
        Menu menu = (Menu) Proxy.newProxyInstance(Menu.class.getClassLoader(), new Class<?>[]{Menu.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMenuEntries")) { return entries.get(); }
                    if (method.getName().equals("setMenuEntries")) { entries.set((MenuEntry[]) args[0]); }
                    return null;
                });
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> method.getName().equals("isMenuOpen") ? open : menu);
        BlackjackEntrySwapPlugin plugin = new BlackjackEntrySwapPlugin();
        Field field = BlackjackEntrySwapPlugin.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(plugin, client);
        return plugin;
    }

    private static NPC npc(String name, int level, int animation, int index)
    {
        return (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getName": return name;
                        case "getCombatLevel": return level;
                        case "getAnimation": return animation;
                        case "getIndex": return index;
                        default: return null;
                    }
                });
    }

    private static MenuEntry entry(NPC npc, String option, AtomicBoolean force)
    {
        return (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(), new Class<?>[]{MenuEntry.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getNpc": return npc;
                        case "getOption": return option;
                        case "setForceLeftClick": force.set((boolean) args[0]); return proxy;
                        case "setDeprioritized": return proxy;
                        default: return null;
                    }
                });
    }
}
