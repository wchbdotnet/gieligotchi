package com.gieligotchi.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gieligotchi.model.ProfileState;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Slf4j
@Singleton
public class ProfileStore
{
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Path directory = RuneLite.RUNELITE_DIR.toPath().resolve("gieligotchi").resolve("profiles");
	private final Object fileLock = new Object();

	@Inject
	public ProfileStore(Gson gson, ScheduledExecutorService executor)
	{
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		this.executor = executor;
	}

	public void load(String profileKey, Consumer<ProfileState> callback)
	{
		executor.execute(() ->
		{
			ProfileState state;
			Path file = fileFor(profileKey);
			try
			{
				synchronized (fileLock)
				{
					if (Files.isRegularFile(file))
					{
						try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
						{
							state = gson.fromJson(reader, ProfileState.class);
						}
					}
					else { state = ProfileState.fresh(profileKey); }
				}
				if (state == null) { state = ProfileState.fresh(profileKey); }
				state.repair();
			}
			catch (Exception error)
			{
				log.debug("Unable to load Gieligotchi profile {}", profileKey, error);
				state = ProfileState.fresh(profileKey);
			}
			callback.accept(state);
		});
	}

	public void save(String profileKey, ProfileState state)
	{
		String snapshot = gson.toJson(state);
		executor.execute(() ->
		{
			Path file = fileFor(profileKey);
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			try
			{
				synchronized (fileLock)
				{
					Files.createDirectories(directory);
					try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
					{
						writer.write(snapshot);
					}
					try
					{
						Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
							StandardCopyOption.ATOMIC_MOVE);
					}
					catch (IOException atomicNotSupported)
					{
						Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			catch (IOException error)
			{
				log.debug("Unable to save Gieligotchi profile {}", profileKey, error);
			}
		});
	}

	private Path fileFor(String profileKey)
	{
		String safe = profileKey.replaceAll("[^A-Za-z0-9_-]", "_");
		return directory.resolve(safe + ".json");
	}
}
