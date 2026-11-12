package net.runelite.client.plugins.microbot.droinfernoprayers.plugins.inferno;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droinfernoprayers.execution.Messages.GroundItem;
public final class InfernoState {
 private final Client client;
 public InfernoState(Client client) { this.client=client; }
 public List<GroundItem> groundItems() {
  return Microbot.getRs2TileItemCache().query().fromWorldView().toListOnClientThread().stream()
   .filter(i -> i.getWorldLocation().getPlane()==client.getPlane())
   .map(i -> { WorldPoint t=WorldPoint.fromLocalInstance(client,i.getTile().getLocalLocation());
    return new GroundItem(i.getId(),i.getName(),i.getQuantity(),t.getX(),t.getY()); })
   .collect(Collectors.toList());
 }
}
