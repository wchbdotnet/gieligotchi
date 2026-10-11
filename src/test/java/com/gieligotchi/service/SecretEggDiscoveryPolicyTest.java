package com.gieligotchi.service;

import com.gieligotchi.model.EggTier;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.*;

public class SecretEggDiscoveryPolicyTest
{
	@Test
	public void recognisesAllSupportedRaidVariants()
	{
		for (String id : new String[]{"cox", "cox_cm", "toa", "toa_entry", "toa_expert", "tob", "tob_entry", "tob_hm"})
		{
			assertTrue(id, SecretEggDiscoveryPolicy.isRaid(id));
		}
		assertFalse(SecretEggDiscoveryPolicy.isRaid("nightmare"));
	}

	@Test
	public void randomPetRewardUsesDocumentedBoundaries()
	{
		assertEquals(EggTier.COMMON, new SecretEggDiscoveryPolicy(fixedInt(0)).randomPetRewardTier());
		assertEquals(EggTier.RARE, new SecretEggDiscoveryPolicy(fixedInt(55)).randomPetRewardTier());
		assertEquals(EggTier.MEGA_RARE, new SecretEggDiscoveryPolicy(fixedInt(85)).randomPetRewardTier());
		assertEquals(EggTier.RIFTGLASS, new SecretEggDiscoveryPolicy(fixedInt(98)).randomPetRewardTier());
	}

	private static Random fixedInt(int value)
	{
		return new Random() { @Override public int nextInt(int bound) { return value; } };
	}
}
