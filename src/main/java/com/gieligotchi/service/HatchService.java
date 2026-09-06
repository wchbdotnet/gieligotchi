package com.gieligotchi.service;

import com.gieligotchi.model.EggState;
import com.gieligotchi.model.HatchReceipt;
import com.gieligotchi.model.Palette;
import com.gieligotchi.model.PetDefinition;
import com.gieligotchi.model.SpeciesRarity;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HatchService
{
	private final PetCatalogue catalogue;
	private final SecureRandom random = new SecureRandom();

	@Inject
	public HatchService(PetCatalogue catalogue) { this.catalogue = catalogue; }

	public HatchReceipt roll(EggState egg)
	{
		double[] odds = egg.getTier().getSpeciesOdds();
		SpeciesRarity rarity = SpeciesRarity.values()[weightedIndex(odds)];
		Map<SpeciesRarity, List<PetDefinition>> pools = catalogue.byRarity();
		List<PetDefinition> pool = pools.get(rarity);
		PetDefinition pet = pool.get(random.nextInt(pool.size()));
		Palette palette = Palette.values()[weightedIndex(paletteWeights())];
		double tierChance = odds[rarity.ordinal()] / 100d;
		double paletteChance = palette.getChancePercent() / 100d;
		return new HatchReceipt(egg.getInstanceId(), egg.getTier(), pet.getId(), rarity,
			tierChance, pool.size(), palette, paletteChance,
			tierChance * (1d / pool.size()) * paletteChance);
	}

	private int weightedIndex(double[] weights)
	{
		double roll = random.nextDouble() * 100d;
		double cursor = 0;
		for (int i = 0; i < weights.length; i++)
		{
			cursor += weights[i];
			if (roll < cursor) { return i; }
		}
		return weights.length - 1;
	}

	private static double[] paletteWeights()
	{
		Palette[] values = Palette.values();
		double[] out = new double[values.length];
		for (int i = 0; i < values.length; i++) { out[i] = values[i].getChancePercent(); }
		return out;
	}
}
