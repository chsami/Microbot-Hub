# Microbot 2.6.25 / RuneLite 1.13 compatibility

RuneLite 1.13 changes ItemManager and HTTP item prices to long. Microbot 2.6.25 also widens Rs2ItemModel prices, tile-item totals, PvP risk, and Grand Exchange offer details.

This migration keeps prices, totals, profit accumulators, comparisons, and monetary displays in long arithmetic. Wilderness Agility uses mapToLong; Thieving uses comparingLong. Lunar profit displays use QuantityFormatter.quantityToStackSize(long). Zulrah replaces the removed Client.getVar call with getVarcIntValue for the inventory tab.

The eleven affected plugins have incremented versions and require client 2.6.25. Rebuild them: old binaries reference int-returning method descriptors and cannot be reused with the new client.

Validate against the candidate before publication:

```sh
./gradlew build -PmicrobotClientPath=/absolute/path/to/microbot-2.6.25.jar -PmicrobotClientVersion=2.6.25
```

Publish this coordinated Hub update only after the stable 2.6.25 client artifact is available and the client-version endpoint resolves to it. The Hub main workflow builds against that endpoint by default. Do not build these sources against 2.6.24 or serve the rebuilt affected plugins to older clients.
