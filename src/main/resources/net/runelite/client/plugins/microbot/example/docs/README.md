# Example Plugin

This plugin serves as a comprehensive template and reference for developing Microbot plugins. It demonstrates the complete plugin architecture including configuration, scripting, overlay rendering, and proper RuneLite integration.

## Overview

The Example Plugin showcases:
- Basic plugin structure and lifecycle management
- Configuration system with user-configurable options
- Script implementation with game loop logic
- Overlay rendering for UI feedback
- Event handling and game state interaction
- Proper dependency injection and resource management

## Plugin Architecture

### Core Components

#### 1. Plugin Descriptor
The `@PluginDescriptor` annotation provides essential metadata about the plugin:

```java
@PluginDescriptor(
    name = PluginConstants.DEFAULT_PREFIX + "Example", // Field to define the plugin name (required)
    description = "Microbots Example Plugin", // A brief description of the plugin (optional, default is '')
    tags = {"example", "microbot"}, // Tags to categorize the plugin (optional, default is '')
    author =  "Mocrosoft", // Author of the plugin (optional, default is "Unknown Author")
    version = ExamplePlugin.version, // Version of the plugin (required)
    minClientVersion = "1.9.8", // Minimum client version required to run the plugin (required)
    enabledByDefault = PluginConstants.DEFAULT_ENABLED, // Whether the plugin is enabled by default
    isExternal = PluginConstants.IS_EXTERNAL // Whether the plugin is external
)
```

#### 2. Plugin Class (`ExamplePlugin.java`)
The main plugin class that extends `Plugin` and handles:
- Plugin lifecycle (startup/shutdown)
- Dependency injection setup
- Event subscription
- Overlay management
- Configuration binding

**Key responsibilities:**
- Initialize and manage plugin components
- Handle plugin enable/disable states
- Provide configuration access
- Manage overlay registration

#### 3. Script Class (`ExampleScript.java`)
The script class extends `Script` and contains the main automation logic:
- Implements the game loop that runs continuously
- Performs game actions and state checks
- Handles script start/stop functionality
- Manages script timing and delays

**Key responsibilities:**
- Execute automation tasks
- Monitor game state
- Handle script lifecycle
- Provide feedback on script status

#### 4. Configuration Class (`ExampleConfig.java`)
Defines user-configurable options through the RuneLite interface:
- Plugin settings and preferences
- Runtime behavior modifications
- User input validation
- Default value specifications

**Key responsibilities:**
- Define configuration options
- Provide user interface for settings
- Validate user inputs
- Supply default values

#### 5. Overlay Class (`ExampleOverlay.java`)
Extends `OverlayPanel` to provide visual feedback:
- Renders information on the game screen
- Displays plugin status and statistics
- Shows real-time data and feedback
- Provides user interaction elements

**Key responsibilities:**
- Render visual elements
- Display plugin information
- Update UI in real-time
- Handle user interactions

## File Structure

```
src/main/java/net/runelite/client/plugins/microbot/example/
├── ExamplePlugin.java      # Main plugin class
├── ExampleScript.java      # Script implementation
├── ExampleConfig.java      # Configuration interface
└── ExampleOverlay.java     # UI overlay component

src/main/resources/net/runelite/client/plugins/microbot/example/
└── docs/
    └── README.md          # This documentation
```

## Getting Started

### Prerequisites
- Java 11 or higher
- Gradle build tool

### Building the Plugin
1. Clone the repository
2. Navigate to the project directory
3. Run the build command:
   ```bash
   ./gradlew build
   ```

## Development Guide

### Creating a New Plugin Based on This Example

1. **Copy the Example Structure**:
   - Duplicate all four core files
   - Rename classes to match your plugin name
   - Update package declarations

2. **Update Plugin Descriptor**:
   - Change the plugin name and description
   - Update version and author information
   - Modify tags to reflect your plugin's purpose

3. **Implement Your Logic**:
   - **Config**: Define your plugin's configuration options
   - **Script**: Implement your automation logic
   - **Overlay**: Create your UI feedback
   - **Plugin**: Wire everything together

4. **Testing**:
   - Test with different game states
   - Verify configuration changes work
   - Ensure proper startup/shutdown behavior

### Best Practices

#### Code Organization
- Keep each class focused on a single responsibility
- Use dependency injection for component access
- Follow RuneLite coding conventions
- Implement proper error handling

#### Configuration Management
- Provide sensible default values
- Validate user inputs
- Use descriptive configuration names
- Group related settings logically

#### Script Implementation
- Check game state before performing actions
- Implement proper delays and timing
- Handle edge cases and errors gracefully
- Provide clear feedback to users

#### Overlay Design
- Keep overlays informative but not intrusive
- Update information in real-time
- Allow users to configure overlay settings

## Common Patterns

### Event Handling
```java
@Subscribe
public void onGameTick(GameTick event) {
    // Handle game tick events
}
```

### Configuration Access
```java
private ExampleConfig config;

// Use configuration values
boolean settingValue = config.someConfigOption();
```

### Script Management
```java
// Start script
exampleScript.run();

// Stop script
exampleScript.shutdown();
```

## Troubleshooting

### Common Issues

1. **Plugin Won't Load**:
   - Check minimum client version compatibility
   - Verify all dependencies are available
   - Check for compilation errors

2. **Script Not Starting**:
   - Ensure plugin is enabled
   - Check configuration values
   - Verify game state requirements

3. **Overlay Not Displaying**:
   - Check overlay manager registration
   - Verify overlay is enabled in settings
   - Ensure overlay positioning is correct

### Debugging Tips
- Use logging statements for debugging
- Check RuneLite console for error messages
- Test configuration changes incrementally
- Verify event subscriptions are working
- Use breakpoints in your IDE for step-by-step debugging

## Contributing

When contributing to this example:
1. Maintain the educational purpose
2. Add clear comments explaining concepts
3. Follow established coding patterns
4. Update documentation for any changes
5. Test thoroughly across different scenarios

## Version History

- **1.1.0**: Current version with comprehensive structure
- **1.0.0**: Initial basic implementation