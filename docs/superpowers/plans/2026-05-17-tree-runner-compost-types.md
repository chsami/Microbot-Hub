# Farm Tree Runner: Compost Type Selection — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the boolean compost toggle with a dropdown supporting Compost, Supercompost, Ultracompost, and Bottomless Compost Bucket, with automatic quantity calculation and empty-bucket cleanup.

**Architecture:** New `CompostType` enum holds item IDs and a reusable flag. Config swaps the boolean for a dropdown. Script banking calculates withdrawal quantity based on unprotected patch count. `handlePlantingTree()` drops empty buckets after consumable compost use.

**Tech Stack:** Java 11, RuneLite plugin API, Lombok

---

### Task 1: Create `CompostType` enum

**Files:**
- Create: `src/main/java/net/runelite/client/plugins/microbot/farmtreerun/enums/CompostType.java`

- [ ] **Step 1: Create the enum file**

```java
package net.runelite.client.plugins.microbot.farmtreerun.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum CompostType {
    NONE("None", -1, false),
    COMPOST("Compost", ItemID.COMPOST, false),
    SUPERCOMPOST("Supercompost", ItemID.SUPERCOMPOST, false),
    ULTRACOMPOST("Ultracompost", ItemID.ULTRACOMPOST, false),
    BOTTOMLESS_BUCKET("Bottomless bucket", ItemID.BOTTOMLESS_COMPOST_BUCKET_22997, true);

    private final String name;
    private final int itemId;
    private final boolean reusable;

    @Override
    public String toString() {
        return name;
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd /home/alex/Developer/MB/Microbot-Hub/.worktrees/tree-runner-compost && ./gradlew build -PpluginList=FarmTreeRunPlugin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/net/runelite/client/plugins/microbot/farmtreerun/enums/CompostType.java
git commit -m "feat(FarmTreeRun): add CompostType enum for compost selection"
```

---

### Task 2: Update config to use `CompostType` dropdown

**Files:**
- Modify: `src/main/java/net/runelite/client/plugins/microbot/farmtreerun/FarmTreeRunConfig.java`

- [ ] **Step 1: Add import for `CompostType`**

Add to the import block:

```java
import net.runelite.client.plugins.microbot.farmtreerun.enums.CompostType;
```

- [ ] **Step 2: Replace the `useCompost()` boolean config with `compostType()` dropdown**

Replace this block (lines 163-171):

```java
    @ConfigItem(
            keyName = "useCompost",
            name = "Use compost",
            description = "Only bottomless compost bucket is supported",
            position = 1,
            section = gearSection
    )
    default boolean useCompost() { return true; }
```

With:

```java
    @ConfigItem(
            keyName = "compostType",
            name = "Compost type",
            description = "Select compost type. Only applied at patches without protection enabled.",
            position = 1,
            section = gearSection
    )
    default CompostType compostType() { return CompostType.NONE; }
```

- [ ] **Step 3: Update `@ConfigInformation` HTML**

In the `@ConfigInformation` annotation, replace:

```
    <li>Filled Bottomless compost bucket</li>
```

With:

```
    <li>Compost / Supercompost / Ultracompost / Bottomless compost bucket</li>
```

- [ ] **Step 4: Build to verify compilation**

Run: `cd /home/alex/Developer/MB/Microbot-Hub/.worktrees/tree-runner-compost && ./gradlew build -PpluginList=FarmTreeRunPlugin`
Expected: BUILD FAILURE — `FarmTreeRunScript.java` still references `config.useCompost()`. This confirms the config change is wired in and the script needs updating next.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/runelite/client/plugins/microbot/farmtreerun/FarmTreeRunConfig.java
git commit -m "feat(FarmTreeRun): replace useCompost boolean with compostType dropdown"
```

---

### Task 3: Update script to use `CompostType`

**Files:**
- Modify: `src/main/java/net/runelite/client/plugins/microbot/farmtreerun/FarmTreeRunScript.java`

- [ ] **Step 1: Add import for `CompostType`**

Add to the import block:

```java
import net.runelite.client.plugins.microbot.farmtreerun.enums.CompostType;
```

- [ ] **Step 2: Update `isCompostEnabled()` method (line ~998)**

The method currently reads:

```java
private boolean isCompostEnabled(FarmTreeRunConfig config) {
    if (!config.useCompost())
        return false;

    if (!getSelectedTreePatches(config).isEmpty() && !config.protectTrees())
        return true;

    if (!getSelectedHardTreePatches(config).isEmpty() && !config.protectHardTrees())
        return true;

    return !getSelectedFruitTreePatches(config).isEmpty() && !config.protectFruitTrees();
}
```

Replace `config.useCompost()` with `config.compostType() == CompostType.NONE`:

```java
private boolean isCompostEnabled(FarmTreeRunConfig config) {
    if (config.compostType() == CompostType.NONE)
        return false;

    if (!getSelectedTreePatches(config).isEmpty() && !config.protectTrees())
        return true;

    if (!getSelectedHardTreePatches(config).isEmpty() && !config.protectHardTrees())
        return true;

    return !getSelectedFruitTreePatches(config).isEmpty() && !config.protectFruitTrees();
}
```

- [ ] **Step 3: Update the non-banking path (line ~134-135)**

Replace:

```java
if (isCompostEnabled(config)) {
    compostItemId = ItemID.BOTTOMLESS_COMPOST_BUCKET_22997;
}
```

With:

```java
if (isCompostEnabled(config)) {
    compostItemId = config.compostType().getItemId();
}
```

- [ ] **Step 4: Update banking compost withdrawal block (lines ~460-467)**

Replace:

```java
if (isCompostEnabled(config)) {
    if (Rs2Bank.hasItem(ItemID.BOTTOMLESS_COMPOST_BUCKET_22997)) {
        compostItemId = ItemID.BOTTOMLESS_COMPOST_BUCKET_22997;
        items.add(new FarmingItem(compostItemId, 1));
    } else {
        Microbot.log("Only bottomless compost is supported. Skipping composting.");
    }
}
```

With:

```java
if (isCompostEnabled(config)) {
    CompostType compostType = config.compostType();
    compostItemId = compostType.getItemId();
    if (compostType.isReusable()) {
        if (Rs2Bank.hasItem(compostItemId)) {
            items.add(new FarmingItem(compostItemId, 1));
        } else {
            Microbot.log("Bottomless compost bucket not found in bank. Skipping composting.");
            compostItemId = null;
        }
    } else {
        int unprotectedCount = 0;
        if (!config.protectTrees())
            unprotectedCount += getSelectedTreePatches(config).size();
        if (!config.protectFruitTrees())
            unprotectedCount += getSelectedFruitTreePatches(config).size();
        if (!config.protectHardTrees())
            unprotectedCount += getSelectedHardTreePatches(config).size();
        if (unprotectedCount > 0) {
            if (Rs2Bank.hasItem(compostItemId)) {
                items.add(new FarmingItem(compostItemId, unprotectedCount));
            } else {
                Microbot.log("Selected compost not found in bank. Skipping composting.");
                compostItemId = null;
            }
        } else {
            compostItemId = null;
        }
    }
}
```

- [ ] **Step 5: Update `useCompostOnPatch()` method (lines ~927-938)**

Replace:

```java
private boolean useCompostOnPatch(FarmTreeRunConfig config, Patch patch) {
    if (!config.useCompost() || compostItemId == null)
        return false;

    if (!config.protectTrees() && patch.kind == TreeKind.TREE)
        return true;

    if (!config.protectHardTrees() && patch.kind == TreeKind.HARD_TREE)
        return true;

    return !config.protectFruitTrees() && patch.kind == TreeKind.FRUIT_TREE;
}
```

With:

```java
private boolean useCompostOnPatch(FarmTreeRunConfig config, Patch patch) {
    if (config.compostType() == CompostType.NONE || compostItemId == null)
        return false;

    if (!config.protectTrees() && patch.kind == TreeKind.TREE)
        return true;

    if (!config.protectHardTrees() && patch.kind == TreeKind.HARD_TREE)
        return true;

    return !config.protectFruitTrees() && patch.kind == TreeKind.FRUIT_TREE;
}
```

- [ ] **Step 6: Add empty bucket drop in `handlePlantingTree()` (after line ~810)**

After the compost application block, add a bucket drop for consumable compost. The current code:

```java
if (useCompostOnPatch(config, patch)) {
    Rs2Inventory.useItemOnObject(compostItemId, treePatch.getId());
    Rs2Player.waitForXpDrop(Skill.FARMING, 2000);
    sleep(550, 2200);
}
```

Replace with:

```java
if (useCompostOnPatch(config, patch)) {
    Rs2Inventory.useItemOnObject(compostItemId, treePatch.getId());
    Rs2Player.waitForXpDrop(Skill.FARMING, 2000);
    sleep(550, 2200);
    if (!config.compostType().isReusable() && Rs2Inventory.hasItem(ItemID.BUCKET)) {
        Rs2Inventory.drop(ItemID.BUCKET);
        sleep(300, 600);
    }
}
```

- [ ] **Step 7: Build to verify compilation**

Run: `cd /home/alex/Developer/MB/Microbot-Hub/.worktrees/tree-runner-compost && ./gradlew build -PpluginList=FarmTreeRunPlugin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/main/java/net/runelite/client/plugins/microbot/farmtreerun/FarmTreeRunScript.java
git commit -m "feat(FarmTreeRun): support all compost types with qty calc and bucket drop"
```
