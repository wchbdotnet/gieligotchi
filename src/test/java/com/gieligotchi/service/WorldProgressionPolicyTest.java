package com.gieligotchi.service;

import java.util.Collections;
import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

import static org.junit.Assert.*;

public class WorldProgressionPolicyTest
{
	@Test
	public void ordinaryAndPersistentPvpWorldsAllowProgression()
	{
		assertTrue(WorldProgressionPolicy.allows(Collections.emptySet()));
		assertTrue(WorldProgressionPolicy.allows(EnumSet.of(WorldType.MEMBERS)));
		assertTrue(WorldProgressionPolicy.allows(EnumSet.of(WorldType.MEMBERS, WorldType.PVP)));
	}

	@Test
	public void temporaryAndSeparateProfileWorldsBlockProgression()
	{
		for (WorldType type : new WorldType[] {
			WorldType.PVP_ARENA,
			WorldType.QUEST_SPEEDRUNNING,
			WorldType.LAST_MAN_STANDING,
			WorldType.BETA_WORLD,
			WorldType.NOSAVE_MODE,
			WorldType.TOURNAMENT_WORLD,
			WorldType.FRESH_START_WORLD,
			WorldType.DEADMAN,
			WorldType.SEASONAL
		})
		{
			assertFalse(type.name(), WorldProgressionPolicy.allows(EnumSet.of(WorldType.MEMBERS, type)));
		}
		assertFalse(WorldProgressionPolicy.allows(null));
	}
}
