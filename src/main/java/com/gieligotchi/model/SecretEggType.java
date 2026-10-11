package com.gieligotchi.model;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A discovered egg whose hatch is constrained by the activity that revealed it. */
public enum SecretEggType
{
	SKILLING("Skilling", "A quiet egg found while training. It can hatch only skilling companions.",
		new Color(0x76B97C), ids(
		"phoenix,tiny_tempor,smolcano,baby_chinchompa,beaver,giant_squirrel,heron,rift_guardian,"
			+ "rock_golem,rocky,tangleroot,herbi,abyssal_protector,quetzin,mr_mcgroot,soup")),
	BOSSING("Bossing", "A battle-scarred egg found after a boss kill. It can hatch only boss companions.",
		new Color(0xC96D5C), ids(
		"abyssal_orphan,aggy,baby_mole,baron,bran,butch,callisto_cub,dom,hellpuppy,huberte,"
			+ "ikkle_hydra,jal_nib_rek,kalphite_princess,lilviathan,little_nightmare,maggot_marquess,"
			+ "moxi,muphin,nexling,nid,noon,pet_chaos_elemental,pet_dagannoth_prime,pet_dagannoth_rex,"
			+ "pet_dagannoth_supreme,pet_dark_core,pet_general_graardor,pet_kril_tsutsaroth,pet_kraken,"
			+ "pet_kreearra,pet_smoke_devil,pet_snakeling,pet_zilyana,prince_black_dragon,scorpias_offspring,"
			+ "scurry,skotos,smol_heredit,sraracha,tzrek_jad,venenatis_spiderling,vetion_jr,vorki,wisp,yami,youngllef")),
	RAIDS("Raids", "A marked egg found after a raid. It can hatch only raid companions.",
		new Color(0x9B7BD1), ids("olmlet,lil_zik,tumekens_guardian"));

	public static final long HATCH_XP = 150_000L;
	private final String displayName;
	private final String description;
	private final Color accent;
	private final Set<String> speciesIds;

	SecretEggType(String displayName, String description, Color accent, Set<String> speciesIds)
	{
		this.displayName = displayName;
		this.description = description;
		this.accent = accent;
		this.speciesIds = speciesIds;
	}

	public String getDisplayName() { return displayName; }
	public String getDescription() { return description; }
	public Color getAccent() { return accent; }
	public boolean allows(String speciesId) { return speciesIds.contains(speciesId); }
	public Set<String> getSpeciesIds() { return speciesIds; }

	private static Set<String> ids(String csv)
	{
		return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(csv.split(","))));
	}
}
