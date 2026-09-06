package com.gieligotchi.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local, versioned completion rewards adapted from the documented OSRS TCG event map. */
public final class ActivityRewardPolicy
{
	private static final long SLAYER_TASK_AWARD = 2_500L;
	private static final List<Rule> RULES = buildRules();

	private ActivityRewardPolicy() {}

	public static Reward match(String message)
	{
		if (message == null) { return null; }
		QuestRewardPolicy.QuestReward quest = QuestRewardPolicy.match(message);
		if (quest != null)
		{
			return new Reward("quest_complete", quest.getQuestName() + " (" + quest.getDifficulty() + ")", quest.getAmount());
		}
		for (Rule rule : RULES)
		{
			if (rule.matches(message)) { return new Reward(rule.id, rule.label, rule.amount); }
		}
		return null;
	}

	public static long npcKillAward(String npcName, int combatLevel)
	{
		String name = npcName == null ? "" : npcName.trim().toLowerCase(Locale.ENGLISH);
		long base = SkillRewardPolicy.npcKillAward(combatLevel);
		if ("tztok-jad".equals(name)) { return Math.max(base, 10_000L); }
		if ("tzkal-zuk".equals(name)) { return Math.max(base, 35_000L); }
		if ("sol heredit".equals(name)) { return Math.max(base, 10_000L); }
		return base;
	}

	public static long slayerTaskAward() { return SLAYER_TASK_AWARD; }

	public static boolean isQuestOrClue(String id)
	{
		return "quest_complete".equals(id) || id != null && id.startsWith("clue_");
	}

	public static boolean isMajorChallenge(String id)
	{
		if (id == null) { return false; }
		switch (id)
		{
			case "cox":
			case "cox_cm":
			case "toa":
			case "toa_entry":
			case "toa_expert":
			case "tob":
			case "tob_entry":
			case "tob_hm":
			case "gauntlet":
			case "corrupted_gauntlet":
			case "barbarian_assault":
				return true;
			default:
				return false;
		}
	}

	private static List<Rule> buildRules()
	{
		List<Rule> rules = new ArrayList<>();
		rules.add(Rule.regex("clue_beginner", "Beginner Treasure Trail", 500,
			"^You have completed \\d+ beginner Treasure Trails?\\.$"));
		rules.add(Rule.regex("clue_easy", "Easy Treasure Trail", 1_000,
			"^You have completed \\d+ easy Treasure Trails?\\.$"));
		rules.add(Rule.regex("clue_medium", "Medium Treasure Trail", 2_000,
			"^You have completed \\d+ medium Treasure Trails?\\.$"));
		rules.add(Rule.regex("clue_hard", "Hard Treasure Trail", 3_000,
			"^You have completed \\d+ hard Treasure Trails?\\.$"));
		rules.add(Rule.regex("clue_elite", "Elite Treasure Trail", 4_000,
			"^You have completed \\d+ elite Treasure Trails?\\.$"));
		rules.add(Rule.regex("clue_master", "Master Treasure Trail", 5_000,
			"^You have completed \\d+ master Treasure Trails?\\.$"));

		rules.add(Rule.prefix("cox_cm", "Chambers of Xeric Challenge Mode", 18_500,
			"Your completed Chambers of Xeric Challenge Mode count is:"));
		rules.add(Rule.prefix("cox", "Chambers of Xeric", 12_500,
			"Your completed Chambers of Xeric count is:"));
		rules.add(Rule.prefix("toa_entry", "Tombs of Amascut Entry Mode", 3_500,
			"Your completed Tombs of Amascut: Entry Mode count is:"));
		rules.add(Rule.prefix("toa_expert", "Tombs of Amascut Expert Mode", 18_500,
			"Your completed Tombs of Amascut: Expert Mode count is:"));
		rules.add(Rule.prefix("toa", "Tombs of Amascut", 12_500,
			"Your completed Tombs of Amascut count is:"));
		rules.add(Rule.prefix("tob_entry", "Theatre of Blood Entry Mode", 3_500,
			"Your completed Theatre of Blood: Entry Mode count is:"));
		rules.add(Rule.prefix("tob_hm", "Theatre of Blood Hard Mode", 18_500,
			"Your completed Theatre of Blood: Hard Mode count is:"));
		rules.add(Rule.prefix("tob", "Theatre of Blood", 12_500,
			"Your completed Theatre of Blood count is:"));
		rules.add(Rule.prefix("corrupted_gauntlet", "Corrupted Gauntlet", 4_500,
			"Your Corrupted Gauntlet completion count is:"));
		rules.add(Rule.prefix("gauntlet", "The Gauntlet", 1_750,
			"Your Gauntlet completion count is:"));
		rules.add(Rule.prefix("barbarian_assault", "Barbarian Assault Wave 10", 7_000,
			"Wave 10 duration:"));
		rules.add(Rule.prefix("pest_control_blue", "Pest Control blue portal", 150,
			"The blue, eastern portal shield has dropped!"));
		rules.add(Rule.prefix("pest_control_purple", "Pest Control purple portal", 150,
			"The purple, western portal shield has dropped!"));
		rules.add(Rule.prefix("pest_control_red", "Pest Control red portal", 150,
			"The red, south-western portal shield has dropped!"));
		rules.add(Rule.prefix("pest_control_yellow", "Pest Control yellow portal", 150,
			"The yellow, south-eastern portal shield has dropped!"));
		return Collections.unmodifiableList(rules);
	}

	public static final class Reward
	{
		private final String id;
		private final String label;
		private final long amount;

		private Reward(String id, String label, long amount)
		{
			this.id = id;
			this.label = label;
			this.amount = amount;
		}

		public String getId() { return id; }
		public String getLabel() { return label; }
		public long getAmount() { return amount; }
	}

	private static final class Rule
	{
		private final String id;
		private final String label;
		private final long amount;
		private final String prefix;
		private final Pattern pattern;

		private Rule(String id, String label, long amount, String prefix, Pattern pattern)
		{
			this.id = id;
			this.label = label;
			this.amount = amount;
			this.prefix = prefix;
			this.pattern = pattern;
		}

		private static Rule prefix(String id, String label, long amount, String prefix)
		{
			return new Rule(id, label, amount, prefix, null);
		}

		private static Rule regex(String id, String label, long amount, String regex)
		{
			return new Rule(id, label, amount, null, Pattern.compile(regex));
		}

		private boolean matches(String message)
		{
			return pattern == null ? message.startsWith(prefix) : pattern.matcher(message).matches();
		}
	}
}
