package com.gieligotchi.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Difficulty-scaled quest rewards backed by a bundled OSRS Wiki snapshot. */
public final class QuestRewardPolicy
{
	private static final String COMPLETION_PREFIX = "Congratulations, you've completed a quest:";
	private static final String RESOURCE = "/com/gieligotchi/data/quest_difficulties.tsv";
	private static final Map<String, String> DIFFICULTIES = loadDifficulties();

	private QuestRewardPolicy() {}

	public static QuestReward match(String message)
	{
		if (message == null || !message.startsWith(COMPLETION_PREFIX)) { return null; }
		String questName = message.substring(COMPLETION_PREFIX.length()).trim();
		String difficulty = DIFFICULTIES.get(normalize(questName));
		if (difficulty == null) { difficulty = "Experienced"; }
		return new QuestReward(questName, difficulty, amountForDifficulty(difficulty));
	}

	public static long amountForDifficulty(String difficulty)
	{
		if (difficulty == null) { return 5_000L; }
		switch (difficulty.toLowerCase(Locale.ENGLISH))
		{
			case "novice": return 1_500L;
			case "intermediate": return 3_000L;
			case "master": return 10_000L;
			case "grandmaster": return 20_000L;
			case "special":
			case "experienced":
			default: return 5_000L;
		}
	}

	private static Map<String, String> loadDifficulties()
	{
		InputStream stream = QuestRewardPolicy.class.getResourceAsStream(RESOURCE);
		if (stream == null) { return Collections.emptyMap(); }
		Map<String, String> result = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#")) { continue; }
				String[] fields = line.split("\\t", 2);
				if (fields.length == 2) { result.put(normalize(fields[0]), fields[1].trim()); }
			}
		}
		catch (IOException ignored)
		{
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(result);
	}

	private static String normalize(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'').replace('\u2018', '\'').replace('\u2013', '-');
	}

	public static final class QuestReward
	{
		private final String questName;
		private final String difficulty;
		private final long amount;

		private QuestReward(String questName, String difficulty, long amount)
		{
			this.questName = questName;
			this.difficulty = difficulty;
			this.amount = amount;
		}

		public String getQuestName() { return questName; }
		public String getDifficulty() { return difficulty; }
		public long getAmount() { return amount; }
	}
}
