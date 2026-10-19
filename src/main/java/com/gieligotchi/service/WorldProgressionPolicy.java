package com.gieligotchi.service;

import java.util.Collection;
import java.util.EnumSet;
import net.runelite.api.WorldType;

/** Prevents temporary or separate-profile worlds from contributing permanent Gieligotchi progress. */
public final class WorldProgressionPolicy
{
	private static final EnumSet<WorldType> NON_PERSISTENT_WORLDS = EnumSet.of(
		WorldType.PVP_ARENA,
		WorldType.QUEST_SPEEDRUNNING,
		WorldType.LAST_MAN_STANDING,
		WorldType.BETA_WORLD,
		WorldType.NOSAVE_MODE,
		WorldType.TOURNAMENT_WORLD,
		WorldType.FRESH_START_WORLD,
		WorldType.DEADMAN,
		WorldType.SEASONAL
	);

	private WorldProgressionPolicy() {}

	public static boolean allows(Collection<WorldType> worldTypes)
	{
		if (worldTypes == null) { return false; }
		for (WorldType type : NON_PERSISTENT_WORLDS)
		{
			if (worldTypes.contains(type)) { return false; }
		}
		return true;
	}
}
