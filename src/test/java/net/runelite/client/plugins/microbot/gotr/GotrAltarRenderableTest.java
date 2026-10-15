package net.runelite.client.plugins.microbot.gotr;

import net.runelite.api.Animation;
import net.runelite.api.DynamicObject;
import net.runelite.api.Model;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GotrAltarRenderableTest
{
    @Test
    void plainModelRenderableIsNotActive()
    {
        assertFalse(GotrScript.isActiveAltarRenderable(mock(Model.class)));
    }

    @Test
    void nullRenderableIsNotActive()
    {
        assertFalse(GotrScript.isActiveAltarRenderable(null));
    }

    @Test
    void dynamicObjectWithoutAnimationIsNotActive()
    {
        DynamicObject altar = mock(DynamicObject.class);
        when(altar.getAnimation()).thenReturn(null);
        assertFalse(GotrScript.isActiveAltarRenderable(altar));
    }

    @Test
    void dynamicObjectWithOtherAnimationIsNotActive()
    {
        assertFalse(GotrScript.isActiveAltarRenderable(animated(9362)));
    }

    @Test
    void dynamicObjectWithActiveAnimationIsActive()
    {
        assertTrue(GotrScript.isActiveAltarRenderable(animated(9363)));
    }

    private static DynamicObject animated(int animationId)
    {
        Animation animation = mock(Animation.class);
        when(animation.getId()).thenReturn(animationId);
        DynamicObject altar = mock(DynamicObject.class);
        when(altar.getAnimation()).thenReturn(animation);
        return altar;
    }
}
