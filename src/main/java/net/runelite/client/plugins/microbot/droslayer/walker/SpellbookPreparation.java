package net.runelite.client.plugins.microbot.droslayer.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;

/** AIO's observed-state preparation flow, without its activity or shopping dependencies. */
final class SpellbookPreparation
{
    enum HouseStep { IN_PROGRESS, UNAVAILABLE }

    interface Port
    {
        boolean preferHostedHouse();
        default boolean ownHouseEnabled() { return false; }
        default HouseStep ownHouse(Rs2Spellbook target) { return HouseStep.UNAVAILABLE; }
        HouseStep hostedHouse(Rs2Spellbook target);
        boolean leaveHostedHouse();
        default void nextHostedHouse() { }
        default boolean returnToExchange() { return false; }
        default long houseProgress() { return 0; }
        Rs2Spellbook current();
        default WorldPoint switchLocation(Rs2Spellbook target)
        {
            return (target == Rs2Spellbook.MODERN ? current() : target).getSwitchLocation();
        }
        boolean unlocked(Rs2Spellbook target);
        boolean bankReady();
        default boolean canUseCape() {return true;}
        int availableCape();
        boolean withdrawCape(int id);
        boolean closeBank();
        void cape(int id, Rs2Spellbook target);
        boolean arrived(WorldPoint point);
        void walk(WorldPoint point);
        void altar(Rs2Spellbook target);
        void cancelTravel();
    }

    /** A temporary input hold must not mark a host unavailable. */
    static final class InputHeld extends RuntimeException { }

    private boolean bookConfirmed;
    private final Port port;
    private final Rs2Spellbook target;
    private long started, capeAt, nextAction, houseAt, ownHouseAt;
    private int capeId, houseAttempts;
    private long houseSignature, houseProgressAt;
    private boolean houseProgressKnown, retryHouse, hostedExhausted;
    private boolean bankChecked, bankClosed, houseAttempted, ownHouseUnavailable, ownHouseExitObserved;
    private String status;

    SpellbookPreparation(Port port, Rs2Spellbook target, long now)
    {
        this.port = port;
        this.target = target;
        started = now;
        status = "Preparing " + target + " spellbook";
    }

    void resumeAfter(long millis)
    {
        started += millis;
        if (capeAt > 0) capeAt += millis;
        if (houseAt > 0) houseAt += millis;
        if (ownHouseAt > 0) ownHouseAt += millis;
        nextAction += millis;
        if (houseProgressKnown) houseProgressAt += millis;
    }

    String status() { return status; }

    private void recoverHostedHouse()
    {
        port.cancelTravel();
        retryHouse = houseAttempts < 2;
        // Both hosts failed: fall through to the public altar route instead of stopping.
        hostedExhausted = !retryHouse;
    }

    boolean tick(long now)
    {
        if (port.current() == target || bookConfirmed)
        {
            bookConfirmed = true;
            port.cancelTravel();
            if (houseAttempted && !port.leaveHostedHouse())
            {
                if (now - started > 480_000)
                    throw new IllegalStateException("Spellbook confirmed but house exit was not observed");
                status = target + " spellbook confirmed; leaving house";
                return false;
            }
            if (port.current() != target) throw new IllegalStateException("Spellbook changed while leaving the house; preparation will restart");
            status = target + " spellbook confirmed";
            return true;
        }
        if (!port.unlocked(target))
            throw new IllegalStateException(target + " spellbook requirements are not completed");
        if (retryHouse)
        {
            status = "Leaving the first house before trying one other host";
            if (!port.leaveHostedHouse()) return false;
            port.nextHostedHouse();
            houseAttempts = 2;
            houseAt = now;
            nextAction = now;
            houseProgressKnown = false;
            retryHouse = false;
        }
        if (now - started > (port.preferHostedHouse() ? 600_000 : 240_000))
            throw new IllegalStateException("Could not confirm " + target + " spellbook after preparation");
        if (!bankChecked)
        {
            status = port.canUseCape() ? "Checking for an owned Magic cape" : "Preparing spellbook travel";
            if (!port.bankReady()) return false;
            capeId = port.canUseCape() ? port.availableCape() : 0;
            if (capeId > 0 && !port.withdrawCape(capeId)) return false;
            bankChecked = true;
        }
        // Closing the bank is an observed step, not proof that the close input succeeded.
        if (!bankClosed)
        {
            if (!port.closeBank()) return false;
            bankClosed = true;
        }
        if (now < nextAction) return false;
        nextAction = now + 1800;
        // Order: owned Magic cape, the player's own POH altar, a world 330 hosted house
        // (two hosts at most), then the public altar route as the final fallback.
        if (capeId > 0)
        {
            if (capeAt == 0) capeAt = now;
            if (now - capeAt < 12_000)
            {
                status = "Switching to " + target + " using Magic cape";
                port.cape(capeId, target);
                return false;
            }
            capeId = 0;
        }
        if (port.ownHouseEnabled() && !ownHouseUnavailable)
        {
            if (ownHouseAt == 0) ownHouseAt = now;
            houseAttempted = true;
            status = "Checking your POH altar for " + target;
            if (now - ownHouseAt < 45_000 && port.ownHouse(target) == HouseStep.IN_PROGRESS) return false;
            ownHouseUnavailable = true;
            port.cancelTravel();
        }
        // Leave an unavailable personal house before travelling to a hosted one or a public altar.
        if (houseAttempted && ownHouseUnavailable && !ownHouseExitObserved)
        {
            if (!port.leaveHostedHouse()) return false;
            ownHouseExitObserved = true;
        }
        if (port.preferHostedHouse() && !hostedExhausted)
        {
            if (houseAt == 0) houseAt = now;
            if (houseAttempts == 0) houseAttempts = 1;
            long signature = port.houseProgress();
            if (!houseProgressKnown || signature != houseSignature)
            {
                houseSignature = signature;
                houseProgressAt = now;
                houseProgressKnown = true;
            }
            houseAttempted = true;
            status = "World 330: preparing hosted-house Occult altar";
            if (now - houseAt < 180_000 && now - houseProgressAt < 90_000)
            {
                try
                {
                    if (port.hostedHouse(target) == HouseStep.IN_PROGRESS) return false;
                }
                catch (InputHeld held) { throw held; }
                catch (IllegalStateException unavailable) { port.cancelTravel(); }
            }
            recoverHostedHouse();
            return false;
        }
        if (houseAttempted && !port.leaveHostedHouse())
        {
            status = "Leaving hosted house for the direct spellbook route";
            return false;
        }
        WorldPoint destination = port.switchLocation(target);
        if (destination == null) throw new IllegalStateException("No switch location for " + target);
        status = "Travelling to " + target + " spellbook switch";
        if (!port.arrived(destination))
        {
            port.walk(destination);
            return false;
        }
        port.cancelTravel();
        status = "Changing to " + target + " spellbook";
        port.altar(target);
        return false;
    }
}
