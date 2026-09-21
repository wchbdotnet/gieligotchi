package com.gieligotchi.model;

import java.util.UUID;
import java.security.SecureRandom;

public class EggState
{
	public static final long STARTER_HATCH_XP = 12_500L;
	private static final SecureRandom ATTUNEMENT_RANDOM = new SecureRandom();
	private String instanceId;
	private EggTier tier;
	private long hatchXp;
	private long targetXp;
	private long purchasePricePaid;
	private boolean starter;
	private boolean readyNotificationSent;
	private HatchReceipt sealedResult;
	private SkillingGoal skillingGoal;
	// Fixed at purchase and saved with this egg; 0-20 maps to 5-25% Legendary.
	private int attunement;

	public static EggState starter()
	{
		EggState egg = new EggState();
		egg.instanceId = UUID.randomUUID().toString();
		egg.tier = EggTier.COMMON;
		egg.targetXp = STARTER_HATCH_XP;
		egg.starter = true;
		return egg;
	}

	public static EggState purchased(EggTier tier)
	{
		EggState egg = new EggState();
		egg.instanceId = UUID.randomUUID().toString();
		egg.tier = tier;
		egg.targetXp = tier.getHatchXp();
		egg.purchasePricePaid = tier.getPrice();
		if (tier == EggTier.RIFTGLASS) { egg.attunement = ATTUNEMENT_RANDOM.nextInt(21); }
		return egg;
	}

	public String getInstanceId() { return instanceId; }
	public EggTier getTier() { return tier; }
	public long getHatchXp() { return hatchXp; }
	public long getTargetXp() { return targetXp; }
	public long getPurchasePricePaid() { return purchasePricePaid; }
	public boolean isStarter() { return starter; }
	public boolean isReadyNotificationSent() { return readyNotificationSent; }
	public HatchReceipt getSealedResult() { return sealedResult; }
	public SkillingGoal getSkillingGoal() { return skillingGoal; }
	public double[] getSpeciesOdds()
	{
		if (tier != EggTier.RIFTGLASS) { return tier.getSpeciesOdds(); }
		int roll = Math.max(0, Math.min(20, attunement));
		return new double[]{0, 0, 81d - 1.5d * roll, 14d + 0.5d * roll, 5d + roll};
	}
	public boolean startSkillingGoal(net.runelite.api.Skill skill, int target)
	{
		int maximum = starter ? 5_000 : tier == EggTier.COMMON ? 25_000
			: tier == EggTier.RARE ? 50_000 : 100_000;
		if (isReady() || skillingGoal != null || target > maximum) { return false; }
		skillingGoal = SkillingGoal.egg(skill, target);
		return skillingGoal != null;
	}
	public long claimSkillingGoal()
	{
		if (skillingGoal == null || !skillingGoal.isComplete()) { return 0; }
		long reward = skillingGoal.getReward();
		skillingGoal = null;
		return reward;
	}
	public SkillingGoal detachSkillingGoal()
	{
		SkillingGoal goal = skillingGoal;
		skillingGoal = null;
		return goal;
	}
	public boolean isReady() { return hatchXp >= targetXp; }
	public double getProgress() { return targetXp <= 0 ? 0 : Math.min(1d, hatchXp / (double) targetXp); }
	public void addXp(long amount) { hatchXp = Math.min(targetXp, Math.max(0, hatchXp + amount)); }
	public void setReadyNotificationSent(boolean value) { readyNotificationSent = value; }
	public void seal(HatchReceipt receipt) { sealedResult = receipt; }
	public void rebalanceTarget(long newTargetXp)
	{
		if (newTargetXp <= 0 || newTargetXp == targetXp) { return; }
		double progress = getProgress();
		targetXp = newTargetXp;
		hatchXp = Math.min(targetXp, Math.max(0L, Math.round(progress * targetXp)));
	}

	public void retargetPreservingXp(long newTargetXp)
	{
		if (newTargetXp <= 0 || newTargetXp == targetXp) { return; }
		targetXp = newTargetXp;
		hatchXp = Math.min(targetXp, Math.max(0L, hatchXp));
	}
}
