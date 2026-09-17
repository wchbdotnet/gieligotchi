package com.gieligotchi.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Maintained boss classification used by the Bonding XP policy.
 *
 * RuneLite exposes NPC identity and combat data, but no authoritative boss
 * flag. Keep the bundled registry in sync with the OSRS Wiki boss catalogue
 * when new encounters are released.
 */
public final class BossRegistry
{
	private static final String RESOURCE = "/com/gieligotchi/data/bosses.txt";
	private static final Registry REGISTRY = load();

	private BossRegistry() {}

	public static boolean isBoss(int npcId, String npcName)
	{
		return REGISTRY.ids.contains(npcId) || REGISTRY.names.contains(normalize(npcName));
	}

	private static Registry load()
	{
		InputStream stream = BossRegistry.class.getResourceAsStream(RESOURCE);
		if (stream == null) { throw new IllegalStateException("Missing boss registry: " + RESOURCE); }
		Set<String> names = new HashSet<>();
		Set<Integer> ids = new HashSet<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				String entry = line.trim();
				if (entry.isEmpty() || entry.startsWith("#")) { continue; }
				if (entry.startsWith("id:"))
				{
					ids.add(Integer.parseInt(entry.substring(3).trim()));
				}
				else
				{
					names.add(normalize(entry));
				}
			}
		}
		catch (IOException | NumberFormatException ex)
		{
			throw new IllegalStateException("Unable to load boss registry: " + RESOURCE, ex);
		}
		return new Registry(Collections.unmodifiableSet(names), Collections.unmodifiableSet(ids));
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'').replace('\u2018', '\'').replace('\u2013', '-');
	}

	private static final class Registry
	{
		private final Set<String> names;
		private final Set<Integer> ids;

		private Registry(Set<String> names, Set<Integer> ids)
		{
			this.names = names;
			this.ids = ids;
		}
	}
}
