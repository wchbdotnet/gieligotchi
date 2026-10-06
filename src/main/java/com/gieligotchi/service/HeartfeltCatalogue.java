package com.gieligotchi.service;

import com.gieligotchi.model.HeartfeltStep;
import com.gieligotchi.model.HeartfeltWish;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Versioned Heartfelt Wish catalogue. Item groups and prerequisite ideas for several
 * collection hunts were adapted from Xtreme Tasker (BSD-2-Clause); see README.
 */
public final class HeartfeltCatalogue
{
	private static final List<HeartfeltTask> TASKS = build();
	private static final Map<String, HeartfeltTask> BY_ID = index(TASKS);

	private HeartfeltCatalogue() {}

	public static HeartfeltWish roll(Map<String, Integer> levels, int totalLevel,
		List<String> recentTaskIds, Random random)
	{
		Map<String, Integer> safeLevels = levels == null ? Collections.emptyMap() : levels;
		Random rng = random == null ? new Random() : random;
		HeartfeltWish.Readiness desired = weightedReadiness(rng.nextInt(100));
		List<HeartfeltTask> candidates = candidates(desired, safeLevels, totalLevel, recentTaskIds);
		if (candidates.isEmpty()) { candidates = candidates(null, safeLevels, totalLevel, recentTaskIds); }
		if (candidates.isEmpty()) { candidates = TASKS; }
		return candidates.get(rng.nextInt(candidates.size())).create(safeLevels, totalLevel);
	}

	public static HeartfeltWish force(String taskId, Map<String, Integer> levels, int totalLevel)
	{
		HeartfeltTask task = taskId == null ? null : BY_ID.get(taskId);
		if (task == null) { task = TASKS.get(0); }
		return task.create(levels == null ? Collections.emptyMap() : levels, totalLevel);
	}

	public static List<String> taskIds()
	{
		List<String> result = new ArrayList<>();
		for (HeartfeltTask task : TASKS) { result.add(task.getId()); }
		return Collections.unmodifiableList(result);
	}

	private static HeartfeltWish.Readiness weightedReadiness(int roll)
	{
		return roll < 60 ? HeartfeltWish.Readiness.READY
			: roll < 90 ? HeartfeltWish.Readiness.WITHIN_REACH : HeartfeltWish.Readiness.DISTANT;
	}

	private static List<HeartfeltTask> candidates(HeartfeltWish.Readiness desired,
		Map<String, Integer> levels, int totalLevel, List<String> recent)
	{
		List<HeartfeltTask> result = new ArrayList<>();
		for (HeartfeltTask task : TASKS)
		{
			if (desired != null && task.readiness(levels, totalLevel) != desired) { continue; }
			if (recent != null && recent.contains(task.getId())) { continue; }
			result.add(task);
		}
		return result;
	}

	private static Map<String, HeartfeltTask> index(List<HeartfeltTask> tasks)
	{
		Map<String, HeartfeltTask> result = new HashMap<>();
		for (HeartfeltTask task : tasks) { result.put(task.getId(), task); }
		return result;
	}

	private static List<HeartfeltTask> build()
	{
		List<HeartfeltTask> tasks = new ArrayList<>();
		// Location-bound jobs: the item must enter the inventory inside the named area.
		tasks.add(task("rimmington_tin", "Back to the Beginning", "Mine 50 tin ore in the Rimmington mine.",
			"A pickaxe", "MINING", 1, 0, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.items("Tin mined at Rimmington", 50, 438).inArea(2940, 3005, 3180, 3255, 0)));
		tasks.add(task("varrock_copper", "Copper in the Capital", "Mine 75 copper ore at the south-east Varrock mine.",
			"A pickaxe", "MINING", 1, 0, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.items("Copper mined near Varrock", 75, 436).inArea(3270, 3305, 3350, 3388, 0)));
		tasks.add(task("alkharid_iron", "Desert Iron", "Mine 100 iron ore at the Al Kharid mine.",
			"15 Mining", "MINING", 15, 0, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.items("Iron mined at Al Kharid", 100, 440).inArea(3290, 3325, 3280, 3325, 0)));
		tasks.add(task("mining_guild_coal", "Below Falador", "Mine 150 coal inside the Mining Guild.",
			"60 Mining", "MINING", 60, 700, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.items("Coal mined in the Guild", 150, 453).inArea(3010, 3055, 9720, 9765, 0)));
		tasks.add(task("castlewars_diamonds", "A Banker's Best Friend", "Cut 100 diamonds inside Castle Wars bank.",
			"43 Crafting and 100 uncut diamonds", "CRAFTING", 43, 600, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.items("Diamonds cut at Castle Wars", 100, 1601).inArea(2430, 2458, 3070, 3105, 0)));
		tasks.add(task("edgeville_emeralds", "Emerald Edge", "Cut 150 emeralds inside Edgeville bank.",
			"27 Crafting and 150 uncut emeralds", "CRAFTING", 27, 450, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.items("Emeralds cut at Edgeville", 150, 1605).inArea(3085, 3102, 3485, 3502, 0)));
		tasks.add(task("catherby_lobsters", "A Catherby Catch", "Catch 100 lobsters along Catherby's shore.",
			"40 Fishing and a lobster pot", "FISHING", 40, 500, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.items("Lobsters caught at Catherby", 100, 377).inArea(2790, 2860, 3420, 3458, 0)));
		tasks.add(task("seers_yews", "The Seers' Old Trees", "Chop 100 yew logs around Seers' Village.",
			"60 Woodcutting", "WOODCUTTING", 60, 750, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.items("Yew logs chopped at Seers", 100, 1515).inArea(2680, 2755, 3450, 3510, 0)));

		// Crafting chains. Separate persisted steps make progress and failures understandable.
		tasks.add(task("green_dhide_set", "The Green Outfit", "Obtain or craft every piece of a green dragonhide set after accepting.",
			"63 Crafting for the complete set", "CRAFTING", 63, 700, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.items("Green d'hide vambraces", 1, 1065), HeartfeltStep.items("Green d'hide chaps", 1, 1099),
			HeartfeltStep.items("Green d'hide body", 1, 1135)));
		tasks.add(task("blue_dhide_set", "A Tailor's Tale", "Obtain blue dragonhides, tan them, then make a complete blue dragonhide set.",
			"71 Crafting for the complete set", "CRAFTING", 71, 1_000, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.items("Blue dragonhides obtained", 6, 1751), HeartfeltStep.items("Blue dragon leather prepared", 6, 2505),
			HeartfeltStep.items("Blue d'hide vambraces", 1, 2487), HeartfeltStep.items("Blue d'hide chaps", 1, 2493),
			HeartfeltStep.items("Blue d'hide body", 1, 2499)));
		tasks.add(task("red_dhide_set", "Crimson Needlework", "Obtain or craft every piece of a red dragonhide set after accepting.",
			"77 Crafting for the complete set", "CRAFTING", 77, 1_250, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.items("Red d'hide vambraces", 1, 2489), HeartfeltStep.items("Red d'hide chaps", 1, 2495),
			HeartfeltStep.items("Red d'hide body", 1, 2501)));
		tasks.add(task("black_dhide_set", "Dressed for the Dark", "Obtain or craft every piece of a black dragonhide set after accepting.",
			"84 Crafting for the complete set", "CRAFTING", 84, 1_500, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Black d'hide vambraces", 1, 2491), HeartfeltStep.items("Black d'hide chaps", 1, 2497),
			HeartfeltStep.items("Black d'hide body", 1, 2503)));

		// Recognised completions and deliberately particular excursions.
		tasks.add(task("three_easy_clues", "Three Small Mysteries", "Complete three easy Treasure Trails.",
			"Access to easy clue steps", null, 0, 300, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.activity("Easy clues completed", 3, "clue_easy")));
		tasks.add(task("two_hard_clues", "A Hard Trail to Follow", "Complete two hard Treasure Trails.",
			"The varied requirements of hard clues", null, 0, 1_200, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.activity("Hard clues completed", 2, "clue_hard")));
		tasks.add(task("gauntlet_pair", "Two Trips Through the Gauntlet", "Complete the Gauntlet twice.",
			"Song of the Elves", null, 0, 1_700, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.activity("Gauntlets completed", 2, "gauntlet", "corrupted_gauntlet")));
		tasks.add(task("corrupted_gauntlet", "A Corrupted Promise", "Complete the Corrupted Gauntlet.",
			"Song of the Elves", null, 0, 1_900, HeartfeltTask.Scale.LONG,
			HeartfeltStep.activity("Corrupted Gauntlet completed", 1, "corrupted_gauntlet")));
		tasks.add(task("barbarian_wave_ten", "The Tenth Wave", "Finish Barbarian Assault Wave 10 twice.",
			"A Barbarian Assault team", null, 0, 900, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.activity("Wave 10 completions", 2, "barbarian_assault")));
		tasks.add(task("wintertodt_five", "Warm Work", "Subdue the Wintertodt five times.",
			"50 Firemaking", "FIREMAKING", 50, 500, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.activity("Wintertodt completions", 5, "WINTERTODT")));
		tasks.add(task("tempoross_five", "A Tempered Tide", "Subdue Tempoross five times.",
			"35 Fishing", "FISHING", 35, 500, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.activity("Tempoross completions", 5, "TEMPOROSS")));
		tasks.add(task("rift_five", "Close the Rift", "Close five rifts with the Guardians.",
			"Temple of the Eye and 27 Runecraft", "RUNECRAFT", 27, 650, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.activity("Rifts closed", 5, "GUARDIANS_OF_THE_RIFT")));
		tasks.add(task("sepulchre_three", "Three Perfect Descents", "Complete Hallowed Sepulchre floor 5 three times.",
			"Sins of the Father and 92 Agility", "AGILITY", 92, 1_850, HeartfeltTask.Scale.LONG,
			HeartfeltStep.activity("Floor 5 completions", 3, "HALLOWED_SEPULCHRE")));

		// Boss outings are specific named targets rather than broad combat counters.
		tasks.add(task("giant_mole_five", "Under Falador", "Defeat the Giant Mole five times.",
			"A light source and a spade", null, 0, 700, HeartfeltTask.Scale.SIMPLE,
			HeartfeltStep.kills("Giant Mole defeated", 5, "Giant Mole")));
		tasks.add(task("barrows_unique", "A Brother's Heirloom", "Obtain a Barrows armour piece after accepting this wish.",
			"Priest in Peril", null, 0, 950, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Barrows armour obtained", 1, 4708, 4710, 4712, 4714, 4716, 4718, 4720, 4722, 4724, 4726, 4728, 4730, 4732, 4734, 4736, 4738, 4745, 4747, 4749, 4751, 4753, 4755, 4757, 4759)));
		tasks.add(task("zulrah_ten", "Ten Coils", "Defeat Zulrah ten times.",
			"Regicide", null, 0, 1_500, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.kills("Zulrah defeated", 10, "Zulrah")));
		tasks.add(task("vorkath_ten", "Ten Frozen Returns", "Defeat Vorkath ten times.",
			"Dragon Slayer II", null, 0, 1_750, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.kills("Vorkath defeated", 10, "Vorkath")));
		tasks.add(task("sarachnis_ten", "Web of Ten", "Defeat Sarachnis ten times.",
			"Access to the Forthos Dungeon", null, 0, 1_000, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.kills("Sarachnis defeated", 10, "Sarachnis")));

		// Fresh post-acceptance drops. These are intentionally high variance.
		tasks.add(task("wintertodt_unique", "A Warmer Wardrobe", "Obtain a Wintertodt unique after accepting this wish.",
			"50 Firemaking", "FIREMAKING", 50, 500, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Wintertodt unique obtained", 1, 20704, 20706, 20708, 20710, 20712, 20716, 20718, 20720)));
		tasks.add(task("vale_totems_unique", "A Gift from the Vale", "Obtain a unique from Vale Totems after accepting this wish.",
			"Children of the Sun, Vale Totems and 20 Fletching", "FLETCHING", 20, 700, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Vale Totems unique obtained", 1, 31032, 31052, 31043, 31034)));
		tasks.add(task("tempoross_unique", "Treasure from the Tempest", "Obtain a Tempoross unique after accepting this wish.",
			"35 Fishing", "FISHING", 35, 500, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Tempoross unique obtained", 1, 25559, 25576, 25578, 25580, 25582, 25588, 25592, 25594, 25596, 25598)));
		tasks.add(task("gotr_unique", "Something Beyond the Rift", "Obtain a Guardians of the Rift unique after accepting this wish.",
			"Temple of the Eye and 27 Runecraft", "RUNECRAFT", 27, 700, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Rift unique obtained", 1, 26792, 26798, 26807, 26809, 26811, 26813, 26815, 26820, 26822, 26850, 26852, 26854, 26856, 26908, 26910, 26912)));
		tasks.add(task("mole_trophies", "Claw and Hide", "Obtain both a mole claw and mole skin after accepting.",
			"Access to the Giant Mole", null, 0, 700, HeartfeltTask.Scale.MULTI,
			HeartfeltStep.items("Mole claw obtained", 1, 7416), HeartfeltStep.items("Mole skin obtained", 1, 7418)));
		tasks.add(task("zulrah_unique", "A Serpentine Keepsake", "Obtain a unique from Zulrah after accepting this wish.",
			"Regicide", null, 0, 1_500, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Zulrah unique obtained", 1, 6571, 12922, 12927, 12932, 12934, 12938)));
		tasks.add(task("zalcano_unique", "A Shard of Zalcano", "Obtain a Zalcano unique after accepting this wish.",
			"Song of the Elves and its skill requirements", "MINING", 70, 1_750, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Zalcano unique obtained", 1, 23953, 23908)));
		tasks.add(task("sepulchre_unique", "Loot from Below", "Obtain a Hallowed Sepulchre unique after accepting this wish.",
			"Sins of the Father and access to the Sepulchre", "AGILITY", 72, 1_600, HeartfeltTask.Scale.LONG,
			HeartfeltStep.items("Sepulchre unique obtained", 1, 24711, 24719, 24721, 24723, 24725, 24727, 24729, 24731, 24733, 24740, 24763, 24765, 24767, 24769, 24771, 24844)));

		// The only 1,000-point class: a personal raid unique entering the inventory.
		tasks.add(task("cox_purple", "A Purple from the Chambers", "Receive a Chambers of Xeric unique in your name.",
			"Chambers of Xeric", null, 0, 2_000, HeartfeltTask.Scale.APEX,
			HeartfeltStep.items("Chambers unique received", 1, 13652, 20997, 21000, 21003, 21012, 21015, 24664, 24668, 24666, 21034, 21079, 21043)));
		tasks.add(task("tob_purple", "A Purple from the Theatre", "Receive a Theatre of Blood unique in your name.",
			"Priest in Peril and a Theatre of Blood team", null, 0, 2_000, HeartfeltTask.Scale.APEX,
			HeartfeltStep.items("Theatre unique received", 1, 22324, 22326, 22327, 22328, 22477, 22481, 22486)));
		tasks.add(task("toa_purple", "A Purple from the Tombs", "Receive a Tombs of Amascut unique in your name.",
			"Beneath Cursed Sands", null, 0, 2_000, HeartfeltTask.Scale.APEX,
			HeartfeltStep.items("Tombs unique received", 1, 25975, 25985, 26219, 27226, 27229, 27232, 27277, 27279, 27283, 27285, 27289, 27293, 30893)));
		return Collections.unmodifiableList(tasks);
	}

	private static HeartfeltTask task(String id, String title, String description, String requirements,
		String skill, int level, int totalLevel, HeartfeltTask.Scale scale, HeartfeltStep... steps)
	{
		return HeartfeltTask.of(id, title, description, requirements, skill, level, totalLevel, scale, steps);
	}
}
