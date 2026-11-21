package net.runelite.client.plugins.microbot.drorecorder;

import java.awt.event.MouseEvent;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;

/** Shared passive event capture for the manual recorder and embedded activity recordings. */
final class DroRecorderCapture implements AutoCloseable {
    private final DroRecorderSession session;
    private final EventBus eventBus;
    private final MouseManager mouseManager;
    private final Runnable beforeTick;
    private volatile boolean closed;
    DroRecorderCapture(DroRecorderSession session, EventBus eventBus, MouseManager mouseManager, Runnable beforeTick) {
        this.session=session;this.eventBus=eventBus;this.mouseManager=mouseManager;this.beforeTick=beforeTick;
    }
    void start(){eventBus.register(this);mouseManager.registerMouseListener(mouseListener);}
    @Override public void close(){closed=true;eventBus.unregister(this);mouseManager.unregisterMouseListener(mouseListener);}
    @Subscribe public void onDroRecorderDiagnostic(DroRecorderDiagnostic event){if(!closed)session.recordDiagnostic(event.values());}
    private final MouseAdapter mouseListener = new MouseAdapter()
    {
        @Override
        public MouseEvent mouseMoved(MouseEvent e)
        {
            if (!closed)
            {
                session.recordMouseMove(e, false);
            }
            return e;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent e)
        {
            if (!closed)
            {
                session.recordMouseMove(e, true);
            }
            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e)
        {
            if (!closed)
            {
                session.recordMouseButton("mouse_pressed", e);
            }
            return e;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent e)
        {
            if (!closed)
            {
                session.recordMouseButton("mouse_released", e);
            }
            return e;
        }

        @Override
        public MouseEvent mouseEntered(MouseEvent e)
        {
            if (!closed)
            {
                session.recordMouseBoundary("mouse_entered_canvas", e);
            }
            return e;
        }

        @Override
        public MouseEvent mouseExited(MouseEvent e)
        {
            if (!closed)
            {
                session.recordMouseBoundary("mouse_exited_canvas", e);
            }
            return e;
        }
    };

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!closed)
        {
            beforeTick.run();
            session.recordGameTick();
        }
    }

    @Subscribe
    public void onFocusChanged(FocusChanged event)
    {
        if (!closed) session.recordFocus("runelite", event.isFocused(), "change", false);
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!closed)
        {
            session.recordMenuClick(event);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        if (!closed)
        {
            session.recordNpcLifecycle("npc_spawned", event.getNpc());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        if (!closed)
        {
            session.recordNpcLifecycle("npc_despawned", event.getNpc());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_spawned", "game", event.getGameObject());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_despawned", "game", event.getGameObject());
        }
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_spawned", "wall", event.getWallObject());
        }
    }

    @Subscribe
    public void onWallObjectDespawned(WallObjectDespawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_despawned", "wall", event.getWallObject());
        }
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_spawned", "decorative", event.getDecorativeObject());
        }
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_despawned", "decorative", event.getDecorativeObject());
        }
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_spawned", "ground", event.getGroundObject());
        }
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event)
    {
        if (!closed)
        {
            session.recordTileObjectLifecycle("object_despawned", "ground", event.getGroundObject());
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (!closed)
        {
            session.recordAnimation(event.getActor());
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (!closed)
        {
            session.recordInteraction(event.getSource(), event.getTarget());
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (!closed)
        {
            session.recordHitsplat(event.getActor(), event.getHitsplat());
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        if (!closed)
        {
            session.recordProjectile(event.getProjectile(), event.getPosition(), event.getZ());
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!closed)
        {
            session.recordItemContainerChanged(event);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (!closed)
        {
            session.recordGameState(event.getGameState());
        }
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event)
    {
        if (!closed) { session.recordNpcChanged(event); }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (!closed) { session.recordActorEvent("actor_death", event.getActor()); }
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event)
    {
        if (!closed) { session.recordActorEvent("actor_graphic_changed", event.getActor()); }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event)
    {
        if (!closed) { session.recordGraphic(event.getGraphicsObject()); }
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event)
    {
        if (!closed) { session.recordGroundItem("ground_item_spawned", event.getTile(), event.getItem(), 0, event.getItem().getQuantity()); }
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event)
    {
        if (!closed) { session.recordGroundItem("ground_item_despawned", event.getTile(), event.getItem(), event.getItem().getQuantity(), 0); }
    }

    @Subscribe
    public void onItemQuantityChanged(ItemQuantityChanged event)
    {
        if (!closed) { session.recordGroundItem("ground_item_quantity_changed", event.getTile(), event.getItem(), event.getOldQuantity(), event.getNewQuantity()); }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (!closed) { session.recordStat(event); }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (!closed) { session.recordVarbit(event); }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!closed) { session.recordGameMessage(event); }
    }

}
