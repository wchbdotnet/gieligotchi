package com.gieligotchi.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Completion counters emitted by the game, not client-side location guesses. */
public enum SkillingActivity
{
	WINTERTODT("Wintertodt", 10),
	TEMPOROSS("Tempoross", 10),
	GUARDIANS_OF_THE_RIFT("Guardians of the Rift", 10),
	ZALCANO("Zalcano", 10),
	HERBIBOAR("Herbiboar", 20),
	HALLOWED_SEPULCHRE("Hallowed Sepulchre floor 5", 10);

	private static final Pattern COUNT = Pattern.compile(
		"^Your (?:(?:completion count for|subdued|completed) )?(.+?) "
			+ "(?:(?:kill|harvest|lap|completion|success) )?(?:count )?is: ?([0-9,]+)\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern RIFTS = Pattern.compile(
		"^Amount of Rifts you have closed: ([0-9,]+)\\.$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SEPULCHRE = Pattern.compile(
		"^You have completed Floor 5 of the Hallowed Sepulchre! Total completions: ([0-9,]+)\\.$",
		Pattern.CASE_INSENSITIVE);

	private final String label;
	private final int target;

	SkillingActivity(String label, int target) { this.label = label; this.target = target; }
	public String getLabel() { return label; }
	public int getTarget() { return target; }
	@Override public String toString() { return label + " × " + target; }

	public static Completion match(String message)
	{
		if (message == null) { return null; }
		Matcher rifts = RIFTS.matcher(message);
		if (rifts.matches()) { return completion(GUARDIANS_OF_THE_RIFT, rifts.group(1)); }
		Matcher sepulchre = SEPULCHRE.matcher(message);
		if (sepulchre.matches()) { return completion(HALLOWED_SEPULCHRE, sepulchre.group(1)); }
		Matcher count = COUNT.matcher(message);
		if (!count.matches()) { return null; }
		String name = count.group(1);
		for (SkillingActivity activity : values())
		{
			if (activity != GUARDIANS_OF_THE_RIFT && activity != HALLOWED_SEPULCHRE
				&& activity.label.equalsIgnoreCase(name))
			{
				return completion(activity, count.group(2));
			}
		}
		return null;
	}

	private static Completion completion(SkillingActivity activity, String rawCount)
	{
		try { return new Completion(activity, Long.parseLong(rawCount.replace(",", ""))); }
		catch (NumberFormatException ignored) { return null; }
	}

	public static final class Completion
	{
		private final SkillingActivity activity;
		private final long count;
		private Completion(SkillingActivity activity, long count)
		{
			this.activity = activity;
			this.count = count;
		}
		public SkillingActivity getActivity() { return activity; }
		public long getCount() { return count; }
	}
}
