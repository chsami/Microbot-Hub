package net.runelite.client.plugins.microbot.fourtifysbarrows.util.scene;

import java.lang.reflect.Proxy;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SceneQueriesTest
{
    @Test
    public void playerTilesComeOnlyFromTheTopLevelWorldView()
    {
        Tile top = proxy(Tile.class, (name, arguments) -> { throw new AssertionError(name); });
        Tile boat = proxy(Tile.class, (name, arguments) -> { throw new AssertionError(name); });
        WorldView topView = view(top);
        WorldView boatView = view(boat);
        Player player = proxy(Player.class, (name, arguments) ->
        {
            if (name.equals("getWorldView"))
            {
                return boatView;
            }
            throw new AssertionError(name);
        });
        Client client = proxy(Client.class, (name, arguments) ->
        {
            switch (name)
            {
                case "getTopLevelWorldView": return topView;
                case "getLocalPlayer": return player;
                default: throw new AssertionError(name);
            }
        });
        List<Tile> tiles = SceneQueries.playerTiles(client);
        assertEquals(1, tiles.size());
        assertSame(top, tiles.get(0));
    }

    private static WorldView view(Tile tile)
    {
        Scene scene = proxy(Scene.class, (name, arguments) ->
        {
            if (name.equals("getTiles"))
            {
                return new Tile[][][] {{{tile}}};
            }
            throw new AssertionError(name);
        });
        return proxy(WorldView.class, (name, arguments) ->
        {
            switch (name)
            {
                case "getScene": return scene;
                case "getPlane": return 0;
                default: throw new AssertionError(name);
            }
        });
    }

    private interface Handler
    {
        Object call(String name, Object[] arguments);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, arguments) -> handler.call(method.getName(), arguments));
    }
}
