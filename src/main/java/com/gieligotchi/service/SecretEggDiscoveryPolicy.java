package com.gieligotchi.service;

import com.gieligotchi.model.EggTier;
import java.security.SecureRandom;
import java.util.Random;

/** Rare-event rolls kept separate from Bonding XP rewards and hatch odds. */
public final class SecretEggDiscoveryPolicy
{
	public static final int RAID_DENOMINATOR = 100;
	public static final int BOSS_DENOMINATOR = 2_000;
	public static final long SKILLING_XP_MEAN = 5_000_000L;
	private final Random random;

	public SecretEggDiscoveryPolicy() { this(new SecureRandom()); }
	SecretEggDiscoveryPolicy(Random random) { this.random = random; }

	public boolean raidCompletion() { return random.nextInt(RAID_DENOMINATOR) == 0; }
	public boolean bossKill() { return random.nextInt(BOSS_DENOMINATOR) == 0; }

	/** Independent chance per XP, collapsed into one numerically stable roll for the observed delta. */
	public boolean skillingXp(long xp)
	{
		if (xp <= 0) { return false; }
		double chance = -Math.expm1(xp * Math.log1p(-1d / SKILLING_XP_MEAN));
		return random.nextDouble() < chance;
	}

	/** Pet rewards favour everyday eggs while retaining a small Riftglass jackpot. */
	public EggTier randomPetRewardTier()
	{
		int roll = random.nextInt(100);
		if (roll < 55) { return EggTier.COMMON; }
		if (roll < 85) { return EggTier.RARE; }
		if (roll < 98) { return EggTier.MEGA_RARE; }
		return EggTier.RIFTGLASS;
	}

	public static boolean isRaid(String activityId)
	{
		return "cox".equals(activityId) || "cox_cm".equals(activityId)
			|| "toa".equals(activityId) || "toa_entry".equals(activityId) || "toa_expert".equals(activityId)
			|| "tob".equals(activityId) || "tob_entry".equals(activityId) || "tob_hm".equals(activityId);
	}
}
