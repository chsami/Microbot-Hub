package net.runelite.client.plugins.microbot.drozulrah;

import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name="[Dro] Zulrah",
        version="1.10.3",
        minClientVersion = "2.1.32",
        description="Inventory-setup driven Zulrah trips, rotations, prayer, switches, thralls and regear",
        tags={"microbot","zulrah","dro"}
)
public class DroZulrahPlugin extends Plugin
{
    @Inject private DroZulrahScript script;
    @Inject private DroZulrahConfig config;
    @Inject private DroZulrahOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    DroZulrahConfig provideConfig(ConfigManager cm)
    {
        return cm.getConfig(DroZulrahConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved e)
    {
        script.onProjectileMoved(e);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e)
    {
        Actor actor = e.getActor();
        if (actor instanceof NPC && "Zulrah".equalsIgnoreCase(actor.getName()))
        {
            script.onZulrahAnimation(actor.getAnimation());
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned e)
    {
        script.onZulrahDespawned(e.getNpc());
    }

    @Subscribe
    public void onActorDeath(ActorDeath e)
    {
        script.onActorDeath(e.getActor());
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e)
    {
        GameObject object = e.getGameObject();
        if (object != null && object.getId() == DroZulrahScript.VENOM_CLOUD_OBJECT)
        {
            script.onCloudSpawn(object.getLocalLocation());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e)
    {
        GameObject object = e.getGameObject();
        if (object != null && object.getId() == DroZulrahScript.VENOM_CLOUD_OBJECT)
        {
            script.onCloudDespawn(object.getLocalLocation());
        }
    }
}
