package com.gieligotchi.model;

import java.util.UUID;

public class EggState
{
	private String instanceId;
	private EggTier tier;
	private long hatchXp;
	private long targetXp;
	private long purchasePricePaid;
	private boolean starter;
	private boolean readyNotificationSent;
	private HatchReceipt sealedResult;

	public static EggState starter()
	{
		EggState egg = new EggState();
		egg.instanceId = UUID.randomUUID().toString();
		egg.tier = EggTier.COMMON;
		egg.targetXp = 16_000L;
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
}
