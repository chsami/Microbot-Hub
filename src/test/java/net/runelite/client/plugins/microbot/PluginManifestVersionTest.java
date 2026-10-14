package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.PluginDescriptor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PluginManifestVersionTest
{
	private static Properties loadBuildVersions() throws IOException
	{
		String path = System.getProperty("microbot.hub.pluginVersions");
		assumeTrue(path != null, "run through Gradle so the build-resolved plugin versions are available");
		Properties versions = new Properties();
		try (Reader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8))
		{
			versions.load(reader);
		}
		return versions;
	}

	@Test
	void buildVersionMatchesCompiledDescriptorForEveryPlugin() throws Exception
	{
		Properties versions = loadBuildVersions();
		assertFalse(versions.isEmpty(), "no plugin versions were resolved by the build");

		List<String> mismatches = new ArrayList<>();
		ClassLoader loader = getClass().getClassLoader();
		for (String className : versions.stringPropertyNames())
		{
			PluginDescriptor descriptor = Class.forName(className, false, loader).getAnnotation(PluginDescriptor.class);
			assertNotNull(descriptor, className + " has no @PluginDescriptor");
			String buildVersion = versions.getProperty(className);
			if (!descriptor.version().equals(buildVersion))
			{
				mismatches.add(className + ": plugins.json/jar " + buildVersion + ", @PluginDescriptor " + descriptor.version());
			}
		}

		assertEquals(List.of(), mismatches);
	}
}
