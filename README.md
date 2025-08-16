
---

# Microbot Hub

Welcome to the Microbot Hub.

The hub is the dedicated place for community created plugins and scripts. It exists to keep the main Microbot client focused on core features while giving contributors a simple way to build, test, and share new ideas. This separation keeps the client lean, fast, and stable. The hub can evolve quickly without risking the reliability of the core application.

## What you will find here

1. Community plugins that extend Microbot
2. A light process to build and test plugins
3. A simple structure that is easy to maintain

## Requirements

1. Java Development Kit that matches the Microbot client version you use
2. Gradle installed or the Gradle wrapper from the repository
3. Git for version control

## Repository layout

Each plugin lives in its own Java package. A typical plugin package can contain the following files and folders:

1. `PestControlPlugin.java` - the primary class for your plugin, extending `Plugin`
2. `PestControlScript.java` - the script class that contains the main logic, extending `Script`
3. Other supporting classes as needed for your plugin

Along side of the plugin's package, comes with a resources folder that contains the following:
1. `docs/README.md` for a short description, setup notes, and known limitations
2. `docs/assets` folder for screenshots or icons that you want to display in the hub
3. `dependencies.txt` for extra Maven coordinates that your plugin needs
4. Any additional resources such as json files, images, or other assets that your plugin needs

Only the files you really use are required. If your plugin has no extra libraries you can omit `dependencies.txt`. If you have no assets/images you can omit the folder.

## Declaring plugin dependencies

If your plugin needs extra libraries, add them to `dependencies.txt`, one line per coordinate in standard Maven format. Example:

```
com.google.guava:guava:33.2.0-jre
org.apache.commons:commons-lang3:3.14.0
```

The build reads this file and adds the coordinates at compile time and packaging time.

# Building the project

## 1. Open the Gradle UI

![img.png](img.png)

## 2. Refresh the gradle projects

![img_1.png](img_1.png)

## 3. Run the gradle build command


![img_4.png](img_4.png)

## 4. Run Microbot in RuneLiteDebug To Test Your Plugin


![img_3.png](img_3.png)



The build produces plugin jars in the usual Gradle output folders. If the project applies a shading step, the final jars will be placed in the shadow or libs folder depending on the build script.

## Running a plugin in RuneLiteDebug for test purpose

Use this minimal driver to start a focused debug session. Replace `PestControlPlugin` with your plugin class if needed.

```java
package net.runelite.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.pestcontrol.PestControlPlugin;

public class Microbot
{

	private static final Class<?>[] debugPlugins = {
		PestControlPlugin.class
	};

	public static void main(String[] args) throws Exception
	{
		List<Class<?>> _debugPlugins = Arrays.stream(debugPlugins).collect(Collectors.toList());
		RuneLiteDebug.pluginsToDebug.addAll(_debugPlugins);
		RuneLiteDebug.main(args);
	}
}
```

Tips for a smooth session

1. Make sure the Java version you use here matches the version used to build the client (Java 11)
2. Confirm that your plugin class is on the classpath of the debug runner
3. If you see a class version error, rebuild the plugin with the same Java release as the client

## Refresh Plugin Dependencies To Use The Latest Version of The Microbot Client

![img_5.png](img_5.png)

## Adding plugin docs and images

1. Create `README.md` in the plugin's docs folder under resources with a short description, setup notes, and known limitations
2. Place screenshots in an `assets` folder within the docs folder, e.g., `docs/assets/overview.png`
3. Use relative links in `README.md` to display screenshots in the hub or on the site that reads these files

Example snippet in `README.md`:

```
# Pest Control
Automates the Pest Control minigame. Supports portals and spinners, smart prayer swaps, and activity checks.

![Overview](assets/overview.png)
```

## Contributing

1. Create a branch with a clear name
2. Keep changes focused on a single plugin or a single feature
3. Run the build and make sure it passes
4. Open a pull request with a short summary and testing steps

## Troubleshooting

**Class was compiled by a newer or older release**
Rebuild the plugin with the same Java release used by the client. Example, if the client uses release 17, set your Gradle Java toolchain to 17 and rebuild.

**Client does not see the plugin**
Confirm the jar is in the plugins folder the client reads. If you use side loading, confirm the folder path in your launcher settings. Make sure the plugin class name matches the expected pattern.

**Missing dependency at runtime**
Place the required coordinate in `dependencies.txt` and rebuild. If the plugin is shaded, ensure the build includes the library inside the final jar.

## Design goals

1. Keep the main client small and focused
2. Allow rapid iteration in the hub without risk to stability
3. Make plugin setup and testing as simple as possible

---
