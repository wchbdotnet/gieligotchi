package com.gieligotchi.service;

import com.gieligotchi.model.ProfileState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Versioned, bounded transfer format; the entire collection travels together. */
@Singleton
public final class SaveCodec
{
	public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
	// Deliberately conservative plugin budget, not a claim about the server's quota.
	public static final int MAX_SYNC_CHARS = 16 * 1024;
	private final Gson gson;
	@Inject
	public SaveCodec(Gson gson) { this.gson = gson; }

	public String json(ProfileState state) { return gson.toJson(state); }

	public ProfileState read(String text, String character)
	{
		if (text == null || text.length() > MAX_FILE_BYTES) { throw new IllegalArgumentException("Save is too large."); }
		try
		{
			JsonObject object = gson.fromJson(text, JsonObject.class);
			if (object == null || !object.has("schemaVersion") || object.get("schemaVersion").getAsInt() != 1
				|| !object.has("profileKey") || !character.equals(object.get("profileKey").getAsString()))
			{
				throw new IllegalArgumentException("This save belongs to a different character or a newer plugin version.");
			}
			if (!object.has("starterEggGranted") || !object.has("hatchHistory") || !object.has("discoveries"))
			{
				throw new IllegalArgumentException("This is not a complete Gieligotchi save.");
			}
			ProfileState state = gson.fromJson(object, ProfileState.class);
			if (state.getSaveVersions().size() > 256) { throw new IllegalArgumentException("Invalid save history."); }
			for (Map.Entry<String, Long> entry : state.getSaveVersions().entrySet())
			{
				if (!entry.getKey().matches("[a-f0-9-]{36}") || entry.getValue() == null || entry.getValue() < 0
					|| entry.getValue() == Long.MAX_VALUE) { throw new IllegalArgumentException("Invalid save history."); }
			}
			state.repair();
			return state;
		}
		catch (RuntimeException error)
		{
			throw new IllegalArgumentException("Cannot read this save. Check the character and file version.", error);
		}
	}

	public String pack(ProfileState state) throws IOException
	{
		byte[] json = json(state).getBytes(StandardCharsets.UTF_8);
		if (json.length > MAX_FILE_BYTES) { throw new IOException("Save is too large for sync; use Export."); }
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(json); }
		String packed = "v1:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
		if (packed.length() > MAX_SYNC_CHARS) { throw new IOException("Save exceeds the sync budget. Use Export to move the full collection."); }
		return packed;
	}

	public ProfileState unpack(String packed, String character) throws IOException
	{
		if (packed == null || !packed.startsWith("v1:") || packed.length() > MAX_SYNC_CHARS)
		{ throw new IOException("Unsupported synced save."); }
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(packed.substring(3)))))
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int count;
			while ((count = gzip.read(buffer)) != -1)
			{
				if (bytes.size() + count > MAX_FILE_BYTES) { throw new IOException("Synced save expands beyond the safety limit."); }
				bytes.write(buffer, 0, count);
			}
			return read(new String(bytes.toByteArray(), StandardCharsets.UTF_8), character);
		}
		catch (IllegalArgumentException error) { throw new IOException("Invalid synced save.", error); }
	}

	public static boolean dominates(ProfileState newer, ProfileState older)
	{
		for (Map.Entry<String, Long> entry : older.getSaveVersions().entrySet())
		{
			if (newer.getSaveVersions().getOrDefault(entry.getKey(), 0L) < entry.getValue()) { return false; }
		}
		return !newer.getSaveVersions().equals(older.getSaveVersions());
	}

	public static void acknowledge(ProfileState selected, ProfileState other)
	{
		other.getSaveVersions().forEach((key, value) -> selected.getSaveVersions().merge(key, value, Math::max));
	}
}
