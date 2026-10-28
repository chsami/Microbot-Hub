package net.runelite.client.plugins.microbot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RetiredClientApiGuardTest
{
	private static final Pattern RETIRED = Pattern.compile(
		"\\bRs2GameObject\\b"
			+ "|\\bRs2ObjectModel\\b"
			+ "|microbot\\.util\\.tileobject\\."
			+ "|microbot\\.util\\.gameobject\\.\\*");

	@Test
	void pluginsDoNotUseTheRetiredTileObjectApi() throws IOException
	{
		Path sources = Paths.get("src", "main", "java");
		assumeTrue(Files.isDirectory(sources), "run from the Hub project root");

		List<String> hits = new ArrayList<>();
		List<Path> files;
		try (Stream<Path> walk = Files.walk(sources))
		{
			files = walk.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
		}
		for (Path file : files)
		{
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			for (int i = 0; i < lines.size(); i++)
			{
				if (RETIRED.matcher(lines.get(i)).find())
				{
					hits.add(sources.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
				}
			}
		}

		assertEquals(List.of(), hits,
			"Rs2GameObject, Rs2ObjectModel and util.tileobject are deprecated for removal; "
				+ "use Microbot.getRs2TileObjectCache().query() and api.tileobject.models.Rs2TileObjectModel");
	}
}
