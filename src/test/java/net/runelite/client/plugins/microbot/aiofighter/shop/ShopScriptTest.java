package net.runelite.client.plugins.microbot.aiofighter.shop;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ShopScriptTest {

    private static final ShopItem SALT = new ShopItem("Bag of salt", 50, 4161, ShopType.SLAYER_SHOP);
    private static final ShopItem WATERSKIN = new ShopItem("Waterskin(4)", 5, 1823, ShopType.GRAND_EXCHANGE);

    @Test
    void slayerItemsAreBoughtAtTheSlayerShop() {
        assertEquals(ShopType.SLAYER_SHOP, ShopScript.nextShopType(Collections.singletonList(SALT)));
    }

    @Test
    void mixedQueueServesTheSlayerShopFirst() {
        assertEquals(ShopType.SLAYER_SHOP, ShopScript.nextShopType(Arrays.asList(WATERSKIN, SALT)));
    }

    @Test
    void grandExchangeOnlyQueueStaysOnTheGrandExchange() {
        assertEquals(ShopType.GRAND_EXCHANGE, ShopScript.nextShopType(Collections.singletonList(WATERSKIN)));
        assertEquals(ShopType.GRAND_EXCHANGE, ShopScript.nextShopType(Collections.emptyList()));
    }
}
