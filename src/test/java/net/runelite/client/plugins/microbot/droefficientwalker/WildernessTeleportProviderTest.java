package net.runelite.client.plugins.microbot.droefficientwalker;

import java.lang.reflect.*;
import java.util.function.*;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WildernessTeleportProviderTest {
    @Test public void realProviderAcceptsLegalWildernessDepartureButRetainsRequirements() throws Exception {
        Constructor<?> loaderCtor = Class.forName(EfficientWalker.class.getName()+"$NavigationEngineClassLoader")
            .getDeclaredConstructor(ClassLoader.class);
        loaderCtor.setAccessible(true);
        ClassLoader loader = (ClassLoader)loaderCtor.newInstance(getClass().getClassLoader());
        Class<?> type = Class.forName("net.runelite.client.plugins.microbot.droefficientwalker.engine.TeleportProviders$TeleportProvider",true,loader);
        Constructor<?> ctor = type.getDeclaredConstructor(String.class,Supplier.class,Spell.class,int.class,
            String.class,IntSupplier.class,int.class,int[].class,Supplier.class,BooleanSupplier.class,boolean.class);
        ctor.setAccessible(true);
        Object provider = ctor.newInstance("Amulet of glory: Edgeville",(Supplier<WorldPoint>)()->new WorldPoint(3087,3496,0),
            null,-1,null,(IntSupplier)()->1,-1,new int[]{1704},(Supplier<String>)()->"Rub",(BooleanSupplier)()->true,true);
        Method eligible = WalkerTestReflection.declaredMethod(type, "isAvailableWithRequirements",boolean.class,int.class,Predicate.class,IntPredicate.class,boolean.class);
        eligible.setAccessible(true);
        Predicate<Spell> spells = spell -> false;
        IntPredicate items = id -> id == 1704;
        assertEquals(true,eligible.invoke(provider,false,1,spells,items,true));
        assertEquals(false,eligible.invoke(provider,true,1,spells,items,true));
        assertEquals(false,eligible.invoke(provider,false,1,spells,items,false));
        assertEquals(false,eligible.invoke(provider,false,1,spells,(IntPredicate)id->false,true));
    }

    @Test public void outerPolicyEnforcesGloryThirtyOtherItemsTwentyAndTeleBlock() throws Exception {
        var config = mock(EfficientWalkerConfig.class);
        var support = new CatalogTeleportSupport(mock(Client.class),config,getClass().getClassLoader(),Object.class,new Object(),"unused");
        Method allowed = WalkerTestReflection.declaredMethod(CatalogTeleportSupport.class, "originalProviderAllowed",String.class);
        allowed.setAccessible(true);
        try (var player=mockStatic(Rs2Player.class); var pvp=mockStatic(Rs2Pvp.class)) {
            var location=new WorldPoint(3034,3733,0);
            player.when(Rs2Player::getWorldLocation).thenReturn(location);
            pvp.when(()->Rs2Pvp.getWildernessLevelFrom(location)).thenReturn(27);
            assertEquals(true,allowed.invoke(support,"Amulet of glory: Edgeville"));
            assertEquals(false,allowed.invoke(support,"Varrock tablet"));
            pvp.when(()->Rs2Pvp.getWildernessLevelFrom(location)).thenReturn(30);
            assertEquals(true,allowed.invoke(support,"Amulet of glory: Edgeville"));
            pvp.when(()->Rs2Pvp.getWildernessLevelFrom(location)).thenReturn(31);
            assertEquals(false,allowed.invoke(support,"Amulet of glory: Edgeville"));
            pvp.when(()->Rs2Pvp.getWildernessLevelFrom(location)).thenReturn(27);
            player.when(Rs2Player::isTeleBlocked).thenReturn(true);
            assertEquals(false,allowed.invoke(support,"Amulet of glory: Edgeville"));
        }
    }
}
