package com.gieligotchi.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gieligotchi.model.PetDefinition;
import com.gieligotchi.model.SpeciesRarity;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PetCatalogue
{
	private final List<PetDefinition> pets;

	@Inject
	public PetCatalogue(Gson gson)
	{
		Type type = new TypeToken<List<PetDefinition>>() {}.getType();
		try (InputStreamReader reader = new InputStreamReader(
			PetCatalogue.class.getResourceAsStream("/com/gieligotchi/pets.json"), StandardCharsets.UTF_8))
		{
			List<PetDefinition> loaded = gson.fromJson(reader, type);
			pets = Collections.unmodifiableList(new ArrayList<>(loaded));
		}
		catch (Exception error)
		{
			throw new IllegalStateException("Unable to load Gieligotchi pet catalogue", error);
		}
	}

	public List<PetDefinition> all() { return pets; }
	public PetDefinition find(String id)
	{
		return pets.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
	}
	public Map<SpeciesRarity, List<PetDefinition>> byRarity()
	{
		return pets.stream().collect(Collectors.groupingBy(PetDefinition::getRarity,
			() -> new EnumMap<>(SpeciesRarity.class), Collectors.toList()));
	}
}
