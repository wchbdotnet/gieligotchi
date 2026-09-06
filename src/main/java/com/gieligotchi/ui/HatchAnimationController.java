package com.gieligotchi.ui;

import com.gieligotchi.model.CompanionInstance;
import com.gieligotchi.model.EggState;
import com.gieligotchi.model.EggTier;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.ProfileState;
import com.gieligotchi.service.GieligotchiStateService;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Timer;

/** Owns the single hatch animation shown simultaneously in the overlay and sidebar. */
@Singleton
public class HatchAnimationController
{
	private static final String[] FRAME_NAMES = {
		"idle_0", "wobble_left", "idle_1", "wobble_right", "idle_2",
		"crack_1", "crack_2", "crack_3", "split_open"
	};
	private static final int[] FRAME_MS = {600, 90, 90, 90, 600, 220, 220, 350, 500};
	private static final int[] CEREMONY_FRAMES = {1, 0, 3, 0, 1, 3, 5, 6, 7, 8};
	private static final int[] CEREMONY_MS = {280, 220, 280, 220, 200, 200, 700, 850, 1_000, 1_100};
	private static final int REVEAL_HOLD_MS = 5_000;
	private static final int REVEAL_FADE_MS = 1_000;
	private final GieligotchiStateService stateService;
	private volatile boolean ceremonyActive;
	private volatile boolean revealing;
	private volatile int crackFrame = -1;
	private volatile int ceremonyStep;
	private volatile EggTier crackingTier;
	private volatile HatchReceipt lastReceipt;
	private volatile CompanionInstance lastCompanion;
	private volatile long fadeStartedAt;
	private Timer timer;

	@Inject
	public HatchAnimationController(GieligotchiStateService stateService)
	{
		this.stateService = stateService;
	}

	public synchronized boolean beginHatch()
	{
		ProfileState state = stateService.getState();
		EggState egg = state == null ? null : state.getActiveEgg();
		if (ceremonyActive || egg == null || !egg.isReady() || egg.getSealedResult() == null) { return false; }
		ceremonyActive = true;
		revealing = false;
		crackingTier = egg.getTier();
		ceremonyStep = 0;
		crackFrame = CEREMONY_FRAMES[ceremonyStep];
		lastReceipt = null;
		lastCompanion = null;
		timer = new Timer(CEREMONY_MS[ceremonyStep], event -> advanceCeremony());
		timer.setRepeats(true);
		timer.start();
		return true;
	}

	private synchronized void advanceCeremony()
	{
		if (!ceremonyActive || timer == null) { return; }
		if (revealing)
		{
			return;
		}
		if (ceremonyStep < CEREMONY_FRAMES.length - 1)
		{
			ceremonyStep++;
			crackFrame = CEREMONY_FRAMES[ceremonyStep];
			timer.setDelay(CEREMONY_MS[ceremonyStep]);
			return;
		}
		ProfileState state = stateService.getState();
		EggState egg = state == null ? null : state.getActiveEgg();
		HatchReceipt receipt = egg == null ? null : egg.getSealedResult();
		CompanionInstance companion = stateService.reveal();
		lastReceipt = companion == null ? null : receipt;
		lastCompanion = companion;
		if (companion == null)
		{
			reset();
			return;
		}
		revealing = true;
		timer.stop();
		timer = new Timer(REVEAL_HOLD_MS, event -> beginFade());
		timer.setRepeats(false);
		timer.start();
	}

	private synchronized void beginFade()
	{
		if (!ceremonyActive || !revealing) { return; }
		fadeStartedAt = System.currentTimeMillis();
		if (timer != null) { timer.stop(); }
		timer = new Timer(50, event ->
		{
			if (System.currentTimeMillis() - fadeStartedAt >= REVEAL_FADE_MS) { finishCeremony(); }
		});
		timer.start();
	}

	private synchronized void finishCeremony()
	{
		if (timer != null) { timer.stop(); timer = null; }
		ceremonyActive = false;
		revealing = false;
		crackFrame = -1;
		crackingTier = null;
		lastReceipt = null;
		lastCompanion = null;
		fadeStartedAt = 0L;
	}

	public BufferedImage eggImage(EggState egg, boolean reducedMotion)
	{
		if (ceremonyActive && !revealing && crackingTier != null && crackFrame >= 0)
		{
			return SpriteAssets.eggFrame(crackingTier, crackFrame, FRAME_NAMES[crackFrame]);
		}
		int frame = reducedMotion ? 0 : idleFrame(egg);
		return SpriteAssets.eggFrame(egg.getTier(), frame, FRAME_NAMES[frame]);
	}

	private static int idleFrame(EggState egg)
	{
		double speed = egg.isReady() ? 0.62d : 1d - egg.getProgress() * 0.28d;
		long[] ends = new long[5];
		long total = 0;
		for (int i = 0; i < 5; i++)
		{
			total += Math.max(55L, Math.round(FRAME_MS[i] * speed));
			ends[i] = total;
		}
		long position = System.currentTimeMillis() % total;
		for (int i = 0; i < ends.length; i++) { if (position < ends[i]) { return i; } }
		return 0;
	}

	public boolean isCracking() { return ceremonyActive && !revealing; }
	public boolean isCeremonyActive() { return ceremonyActive; }
	public boolean isRevealing() { return revealing; }
	public HatchReceipt getLastReceipt() { return lastReceipt; }
	public CompanionInstance getLastCompanion() { return lastCompanion; }
	public float getRevealOpacity()
	{
		if (!revealing || fadeStartedAt == 0L) { return 1f; }
		long fadeElapsed = System.currentTimeMillis() - fadeStartedAt;
		return Math.max(0f, 1f - fadeElapsed / (float) REVEAL_FADE_MS);
	}
	public synchronized void clearResult() { lastReceipt = null; lastCompanion = null; }

	public synchronized void reset()
	{
		if (timer != null) { timer.stop(); timer = null; }
		ceremonyActive = false;
		revealing = false;
		crackFrame = -1;
		crackingTier = null;
		lastReceipt = null;
		lastCompanion = null;
		fadeStartedAt = 0L;
	}
}
