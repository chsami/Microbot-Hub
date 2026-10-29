package net.runelite.client.plugins.microbot.fourtify.core.game;

import lombok.Value;
import java.math.BigInteger;

/** One GE-eligible stack. The base ID identifies the offer; inventoryId identifies the carried form. */
@Value
public class SaleItem {
    int itemId;
    int inventoryId;
    String name;
    int quantity;
    long unitPrice;
    /** The true combined stock can exceed one offer's signed-int quantity when notes and loose items coexist. */
    long stockQuantity;

    public SaleItem(int itemId, int inventoryId, String name, int quantity, long unitPrice) {
        this(itemId, inventoryId, name, quantity, unitPrice, quantity);
    }

    public SaleItem(int itemId, int inventoryId, String name, int quantity, long unitPrice, long stockQuantity) {
        this.itemId = itemId;
        this.inventoryId = inventoryId;
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.stockQuantity = stockQuantity;
    }

    /** Guide prices are long values; even a long can overflow when multiplied by a full stack. */
    public BigInteger value() {
        return BigInteger.valueOf(Math.max(0L, unitPrice)).multiply(BigInteger.valueOf(stockQuantity));
    }
}
