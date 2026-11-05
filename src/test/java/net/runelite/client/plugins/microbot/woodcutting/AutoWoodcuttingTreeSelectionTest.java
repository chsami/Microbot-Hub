package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoWoodcuttingTreeSelectionTest {

    @Test
    void skipsNearerDepletedTreeWithoutCutActionAndClicksLiveTree() throws Exception {
        Rs2TileObjectModel depleted = tree(false);
        Rs2TileObjectModel live = tree(true);

        handleWoodcutting(WoodcuttingTree.REDWOOD, List.of(depleted, live));

        verify(depleted, never()).click(anyString());
        verify(live).click("Cut");
    }

    @Test
    void clicksNothingWhenEveryTreeInRangeIsDepleted() throws Exception {
        Rs2TileObjectModel depleted = tree(false);

        handleWoodcutting(WoodcuttingTree.REDWOOD, List.of(depleted));

        verify(depleted, never()).click(anyString());
    }

    private static Rs2TileObjectModel tree(boolean hasCut) {
        Rs2TileObjectModel model = mock(Rs2TileObjectModel.class);
        when(model.hasAction("Cut")).thenReturn(hasCut);
        when(model.click(anyString())).thenReturn(false);
        return model;
    }

    @SuppressWarnings("unchecked")
    private static void handleWoodcutting(WoodcuttingTree treeType, List<Rs2TileObjectModel> nearestFirst) throws Exception {
        List<Predicate<Rs2TileObjectModel>> filters = new ArrayList<>();
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(query.where(any())).thenAnswer(invocation -> {
            filters.add(invocation.getArgument(0));
            return query;
        });
        when(query.nearestOnClientThread()).thenAnswer(invocation -> nearestFirst.stream()
                .filter(candidate -> filters.stream().allMatch(filter -> filter.test(candidate)))
                .findFirst()
                .orElse(null));
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);

        AutoWoodcuttingConfig config = mock(AutoWoodcuttingConfig.class);
        when(config.HardwoodTreePatch()).thenReturn(false);
        when(config.distanceToStray()).thenReturn(17);

        AutoWoodcuttingScript script = new AutoWoodcuttingScript(null);
        script.rs2TileObjectCache = cache;
        Field activeTree = AutoWoodcuttingScript.class.getDeclaredField("activeTree");
        activeTree.setAccessible(true);
        activeTree.set(script, treeType);

        Method method = AutoWoodcuttingScript.class.getDeclaredMethod("handleWoodcutting", AutoWoodcuttingConfig.class);
        method.setAccessible(true);
        method.invoke(script, config);
    }
}
