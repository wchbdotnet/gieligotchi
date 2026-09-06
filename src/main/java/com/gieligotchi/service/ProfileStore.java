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
	private final Path directory;
	private final Object fileLock = new Object();

	@Inject
	public ProfileStore(Gson gson, ScheduledExecutorService executor)
	{
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		this.executor = executor;
		this.directory = RuneLite.RUNELITE_DIR.toPath().resolve("gieligotchi").resolve("profiles");
	}

	ProfileStore(Gson gson, ScheduledExecutorService executor, Path directory)
	{
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		this.executor = executor;
		this.directory = directory;
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
					state = loadWithBackup(profileKey, file);
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
		writeAsync(profileKey, fileFor(profileKey), snapshot, "save");
	}

	public void backup(String profileKey, ProfileState state)
	{
		if (profileKey == null || state == null) { return; }
		String snapshot = gson.toJson(state);
		writeAsync(profileKey, backupFileFor(profileKey), snapshot, "back up");
	}

	private ProfileState loadWithBackup(String profileKey, Path file) throws IOException
	{
		if (Files.isRegularFile(file))
		{
			try { return read(file); }
			catch (Exception error)
			{
				log.debug("Unable to read Gieligotchi profile {}; trying backup", profileKey, error);
			}
		}
		Path backup = backupFileFor(profileKey);
		if (Files.isRegularFile(backup)) { return read(backup); }
		return ProfileState.fresh(profileKey);
	}

	private ProfileState read(Path file) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			return gson.fromJson(reader, ProfileState.class);
		}
	}

	private void writeAsync(String profileKey, Path file, String snapshot, String operation)
	{
		executor.execute(() ->
		{
			try
			{
				synchronized (fileLock) { write(file, snapshot); }
			}
			catch (IOException error)
			{
				log.debug("Unable to {} Gieligotchi profile {}", operation, profileKey, error);
			}
		});
	}

	private void write(Path file, String snapshot) throws IOException
	{
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
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

	private Path fileFor(String profileKey)
	{
		String safe = profileKey.replaceAll("[^A-Za-z0-9_-]", "_");
		return directory.resolve(safe + ".json");
	}

	private Path backupFileFor(String profileKey)
	{
		String safe = profileKey.replaceAll("[^A-Za-z0-9_-]", "_");
		return directory.resolve(safe + ".backup.json");
	}
}
