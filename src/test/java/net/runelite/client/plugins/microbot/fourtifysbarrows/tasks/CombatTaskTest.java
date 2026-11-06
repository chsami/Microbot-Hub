package net.runelite.client.plugins.microbot.fourtifysbarrows.tasks;

import java.lang.reflect.Proxy;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.fourtifysbarrows.config.BarrowsConfig;
import net.runelite.client.plugins.microbot.fourtifysbarrows.crypt.Brother;
import net.runelite.client.plugins.microbot.fourtifysbarrows.util.action.BarrowsActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CombatTaskTest
{
    @Test
    public void observedProtectionAllowsAttackOnNextTick()
    {
        FakeActions actions = new FakeActions();
        actions.npcPresent = true;
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.active = true;
        task.execute();
        assertEquals(0, actions.attacks);
        actions.now = 1;
        task.execute();
        assertEquals(1, actions.attacks);
        assertEquals(1, actions.protections);
    }

    @Test
    public void observedEquipmentAllowsAttackOnNextTickButRequestAloneDoesNot()
    {
        FakeActions actions = new FakeActions();
        actions.npcPresent = true;
        actions.active = true;
        actions.gearReady = false;
        CombatTask task = new CombatTask(actions);
        task.execute();
        assertEquals(0, actions.attacks);
        actions.now = 1;
        task.execute();
        assertEquals(0, actions.attacks);
        actions.gearReady = true;
        actions.now = 2;
        task.execute();
        assertEquals(1, actions.attacks);
    }

    @Test
    public void observedProtectionClearsPreparationEvenWhenCombatBecomesInvalid()
    {
        FakeActions actions = new FakeActions();
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.now = 2;
        actions.active = true;
        assertFalse(task.isValid());
        actions.now = 100;
        actions.active = false;
        task.execute();
        assertFalse(actions.trip);
        assertEquals(2, actions.protections);
        actions.now = 124;
        task.execute();
        assertTrue(actions.trip);
    }

    @Test
    public void enteringAnotherCryptGetsItsOwnPreparationBudgetAfterTravel()
    {
        FakeActions actions = new FakeActions();
        CombatTask task = new CombatTask(actions);
        task.execute();
        // A higher-priority task or travel can prevent further combat polling until the next crypt.
        actions.now = 100;
        actions.location = Brother.KARIL.getSarcophagus();
        task.execute();
        assertFalse(actions.trip);
        assertEquals(2, actions.protections);
        assertEquals(Prayer.PROTECT_FROM_MISSILES, actions.lastPrayer);
        actions.now = 124;
        task.execute();
        assertTrue(actions.trip);
    }

    @Test
    public void differentBrothersUsingTheSamePrayerHaveIndependentPreparationBudgets()
    {
        FakeActions actions = new FakeActions();
        actions.location = Brother.DHAROK.getSarcophagus();
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.now = 100;
        actions.location = Brother.TORAG.getSarcophagus();
        task.execute();
        assertFalse(actions.trip);
        assertEquals(2, actions.protections);
        assertEquals(Prayer.PROTECT_FROM_MELEE, actions.lastPrayer);
    }

    @Test
    public void leavingACombatContextClearsItsPreparationDeadline()
    {
        FakeActions actions = new FakeActions();
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.location = new WorldPoint(3565, 3315, 0);
        actions.now = 100;
        assertFalse(task.isValid());
        actions.location = Brother.AHRIM.getSarcophagus();
        task.execute();
        assertFalse(actions.trip);
        assertEquals(2, actions.protections);
    }

    @Test
    public void unavailableProtectionReleasesEscapeAfterDeadline()
    {
        FakeActions actions = new FakeActions();
        CombatTask task = new CombatTask(actions);
        task.execute();
        assertEquals(1, actions.protections);
        assertEquals(0, actions.attacks);
        actions.now = 24;
        task.execute();
        assertTrue(actions.trip);
        assertFalse(task.isValid());
    }

    @Test
    public void prayerRequestDoesNotCountAsObservedProtection()
    {
        FakeActions actions = new FakeActions();
        actions.npcPresent = true;
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.now = 2;
        task.execute();
        assertEquals(0, actions.attacks);
        actions.active = true;
        actions.now = 4;
        task.execute();
        assertEquals(1, actions.attacks);
        assertEquals("ranged", actions.selectedGear);
    }

    @Test
    public void bankIntentStopsNewCombatRequests()
    {
        FakeActions actions = new FakeActions();
        actions.npcPresent = true;
        actions.trip = true;
        CombatTask task = new CombatTask(actions);
        task.execute();
        assertEquals(0, actions.attacks);
        assertEquals(0, actions.protections);
    }

    @Test
    public void unchangedEnemyHealthStopsEvenWhenAlreadyInteracting()
    {
        FakeActions actions = new FakeActions();
        actions.npcPresent = true;
        actions.active = true;
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.now = 100;
        task.execute();
        assertTrue(actions.failure.contains("Combat stalled"));
    }

    @Test
    public void observedDamageExtendsCombatBudget()
    {
        FakeActions actions = new FakeActions();
        actions.npcPresent = true;
        actions.active = true;
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.now = 90;
        actions.health = 8;
        task.execute();
        actions.now = 150;
        task.execute();
        assertEquals("", actions.failure);
        actions.now = 190;
        task.execute();
        assertTrue(actions.failure.contains("Combat stalled"));
    }

    @Test
    public void unknownHealthStillHasAFiniteCombatBudget()
    {
        FakeActions actions = new FakeActions();
        actions.npcPresent = true;
        actions.active = true;
        actions.health = -1;
        CombatTask task = new CombatTask(actions);
        task.execute();
        actions.now = 149;
        task.execute();
        assertEquals("", actions.failure);
        actions.now = 151;
        task.execute();
        assertTrue(actions.failure.contains("Combat stalled"));
    }

    private static final class FakeActions extends BarrowsActions
    {
        private int now;
        private int health = 10;
        private String failure = "";
        private int protections;
        private int attacks;
        private boolean trip;
        private boolean active;
        private boolean npcPresent;
        private boolean gearReady = true;
        private String selectedGear;
        private Prayer lastPrayer;
        private WorldPoint location = Brother.AHRIM.getSarcophagus();
        private final BarrowsConfig config = new BarrowsConfig()
        {
            @Override
            public String rangedGear()
            {
                return "ranged";
            }
        };
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, arguments) ->
        {
            if (method.getName().equals("getWorldLocation"))
            {
                return location;
            }
            return null;
        });
        private final NPC npc = (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[] {NPC.class}, (proxy, method, arguments) ->
        {
            if (method.getName().equals("getName"))
            {
                return Brother.AHRIM.getNpcName();
            }
            if (method.getName().equals("getHealthRatio"))
            {
                return health;
            }
            if (method.getName().equals("isDead"))
            {
                return false;
            }
            return null;
        });

        @Override
        public void fail(String reason)
        {
            failure = reason;
        }

        @Override
        public BarrowsConfig config()
        {
            return config;
        }
        @Override
        public Player player()
        {
            return player;
        }
        @Override
        public boolean ready()
        {
            return true;
        }
        @Override
        public boolean bankPending()
        {
            return trip;
        }
        @Override
        public void requestBank()
        {
            trip = true;
        }
        @Override
        public int tick()
        {
            return now;
        }
        @Override
        public NPC brotherNpc()
        {
            return npcPresent ? npc : null;
        }
        @Override
        public boolean killed(Brother brother)
        {
            return false;
        }
        @Override
        public boolean prayerActive(Prayer prayer)
        {
            return active;
        }
        @Override
        public int prayerPoints()
        {
            return 50;
        }
        @Override
        public boolean gear(String gear, Runnable callback)
        {
            selectedGear = gear;
            if (!gearReady)
            {
                callback.run();
            }
            return gearReady;
        }
        @Override
        public boolean protect(Prayer prayer, boolean enabled, Runnable callback)
        {
            protections++;
            lastPrayer = prayer;
            callback.run();
            return true;
        }
        @Override
        public boolean attack(NPC target, Runnable callback)
        {
            attacks++;
            callback.run();
            return true;
        }
    }
}
