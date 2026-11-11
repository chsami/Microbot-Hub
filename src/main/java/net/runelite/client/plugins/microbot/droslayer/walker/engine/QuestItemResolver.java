package net.runelite.client.plugins.microbot.droslayer.walker.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Resolves the inventory items and actions identified by a Quest Helper instruction. */
final class QuestItemResolver {
   final int itemId;
   final int inventorySlot;
   final String name;

   QuestItemResolver(int itemId, int inventorySlot, String name) {
      this.itemId = itemId;
      this.inventorySlot = inventorySlot;
      this.name = name;
   }

   boolean isSelected(Client client) {
      Widget selectedWidget = client.getSelectedWidget();
      return client.isWidgetSelected() && selectedWidget != null && selectedWidget.getId() == 9764864 && selectedWidget.getIndex() == this.inventorySlot && selectedWidget.getItemId() == this.itemId;
   }

   static final class InventoryItemAction {
      final QuestItemResolver selectedItem;
      final QuestItemResolver targetItem;
      final String action;

      InventoryItemAction(QuestItemResolver selectedItem, QuestItemResolver targetItem, String action) {
         this.selectedItem = selectedItem;
         this.targetItem = targetItem;
         this.action = action;
      }
   }
}
