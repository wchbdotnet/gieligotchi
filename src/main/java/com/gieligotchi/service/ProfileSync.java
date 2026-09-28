package com.gieligotchi.service;

import com.gieligotchi.model.ProfileState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigManager;

/** Each device owns a separate configuration key, so offline writers cannot erase each other. */
@Singleton
public class ProfileSync
{
	private static final String GROUP = "gieligotchisaves";
	private static final String PREFIX = "device";
	@Inject private ConfigManager config;
	@Inject private SessionManager sessions;

	public String unavailable(String rsProfile)
	{
		if (Boolean.getBoolean("gieligotchi.devTools")) { return "Test client: cloud sync isolated. Export/import available."; }
		if (sessions.getAccountSession() == null) { return "Local save. Sign into RuneLite on both devices to sync."; }
		if (rsProfile == null) { return "Waiting for RuneLite character profile."; }
		return null;
	}

	public List<ProfileState> read(String rsProfile, String character) throws IOException
	{
		List<ProfileState> snapshots = new ArrayList<>();
		List<String> keys = config.getRSProfileConfigurationKeys(GROUP, rsProfile, PREFIX);
		java.util.Collections.sort(keys);
		if (keys.size() > 16) { throw new IOException("Too many device snapshots. Use Export/Import."); }
		for (String key : keys)
		{
			ProfileState snapshot = SaveCodec.unpack(config.getConfiguration(GROUP, rsProfile, key), character);
			if (snapshot.getSaveVersions().isEmpty()) { throw new IOException("Incomplete synced save; keeping local progress."); }
			snapshots.add(snapshot);
		}
		return snapshots;
	}

	public void publish(String rsProfile, String device, ProfileState state) throws IOException
	{
		String key = PREFIX + device.replace("-", "");
		List<String> keys = config.getRSProfileConfigurationKeys(GROUP, rsProfile, PREFIX);
		if (keys.size() >= 16 && !keys.contains(key)) { throw new IOException("Device limit reached. Use Export/Import."); }
		String packed = SaveCodec.pack(state);
		if (!packed.equals(config.getConfiguration(GROUP, rsProfile, key)))
		{
			config.setConfiguration(GROUP, rsProfile, key, packed);
		}
	}
}
