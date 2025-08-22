# Glass Blowing

A simple crafting plugin that takes glass and crafts any glass product. Start with a glass blowing pipe near a bank with molten glass in the bank or inventory to begin crafting. Also serves as an example plugin for Tasks and TaskScript architecture.

## 🚀 Getting Started

### Prerequisites
- **Inventory**: A Glassblowing pipe is required in the inventory. Molten glass is required in bank. Rest of inventory can be empty or Molten glass.
- **Location**: Start near a bank


## ⚙️ Configuration Options

### Product
- **Product**: Choose the craft to be performed


# Task Node Architecture for Bot Scripting

## Overview

This repository implements a modular **TaskScript** architecture, inspired by DreamBot's `TaskScript` model. It separates behaviors into small, manageable nodes that make bot development **cleaner, more maintainable**, and **easier to debug**.

## Why Use Tasks?

Traditional monolithic scripts often suffer from:
- Spaghetti code and poor readability
- Difficulty scaling with complex behavior trees
- Fragile control flow

The Task Node approach solves these problems by:
- **Encapsulating** logic into discrete, testable units (`TaskNode`)
- Enabling **priority-based task switching**
- Supporting **parent-child** relationships for hierarchical tasks
- Promoting **code reuse** across scripts

## Key Concepts

### Task
A `Task` is the base unit of behavior. Each implements:
- `accept()`: Determines if this task should run
- `execute()`: Executes the logic if accepted

```java
public abstract class Task {
    public abstract boolean accept();
    public abstract int execute(); // Return delay in ms
}
```

### TaskScript
A `TaskScript` is an advanced `Script` that allows passing a list of `Tasks` to be executed. 

```java
@Inject
    TaskScript taskScript = new TaskScript();


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(glassBlowingOverlay);
        }
        taskScript.addNodes(
                Arrays.asList(
                        new BankTask(config),
                        new BlowTask(config))
        );
        taskScript.run();
    }

    protected void shutDown() {
        taskScript.shutdown();
        overlayManager.remove(glassBlowingOverlay);
    }
```
