package com.gieligotchi.service;

import com.gieligotchi.model.ProfileState;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ProfileSyncTest
{
	@Rule public TemporaryFolder folder = new TemporaryFolder();
	private static final String CHARACTER = "account-123";
	private static final String DEVICE_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
	private static final String DEVICE_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
	private static final SaveCodec CODEC = new SaveCodec(new Gson());

	private static ProfileState saved(String device, long version, long points)
	{
		ProfileState state = ProfileState.fresh(CHARACTER);
		state.getSaveVersions().put(device, version);
		state.grantGotchiPoints(points);
		return state;
	}

	@Test public void packedSaveRoundTripsWithoutLosingProgress() throws Exception
	{
		ProfileState state = saved(DEVICE_A, 3, 4321);
		state.award(456);
		String json = CODEC.json(state);
		assertEquals(json, CODEC.json(CODEC.unpack(CODEC.pack(state), CHARACTER)));
	}

	@Test public void rejectsWrongCharacterAndBrokenOrFutureFiles() throws Exception
	{
		String json = CODEC.json(saved(DEVICE_A, 1, 2));
		assertThrows(IllegalArgumentException.class, () -> CODEC.read(json, "account-other"));
		assertThrows(IllegalArgumentException.class, () -> CODEC.read("{}", CHARACTER));
		assertThrows(IllegalArgumentException.class, () -> CODEC.read(json.replace("\"schemaVersion\":1", "\"schemaVersion\":9"), CHARACTER));
		assertThrows(java.io.IOException.class, () -> CODEC.unpack("v1:broken", CHARACTER));
	}

	@Test public void divergentDevicesAreNotOrderedByClockTime()
	{
		ProfileState a = saved(DEVICE_A, 10, 100);
		ProfileState b = saved(DEVICE_B, 1, 200);
		assertFalse(SaveCodec.dominates(a, b));
		assertFalse(SaveCodec.dominates(b, a));
		SaveCodec.acknowledge(b, a);
		assertTrue(SaveCodec.dominates(b, a));
	}

	@Test public void emptyDeviceAdoptsCloudBeforeCreatingStarterSave() throws Exception
	{
		try (Fixture f = new Fixture(null))
		{
			f.cloud.add(saved(DEVICE_A, 4, 777));
			f.load();
			assertEquals(777, f.service.getState().getGotchiPoints());
			assertFalse(f.service.hasSaveConflict());
		}
	}

	@Test public void existingLegacySaveIsNeverSilentlyOverwritten() throws Exception
	{
		ProfileState legacy = ProfileState.fresh(CHARACTER);
		legacy.grantGotchiPoints(123);
		try (Fixture f = new Fixture(legacy))
		{
			f.cloud.add(saved(DEVICE_A, 3, 999));
			f.load();
			assertEquals(123, f.service.getState().getGotchiPoints());
			assertTrue(f.service.hasSaveConflict());
			f.service.syncNow("rsprofile.test");
			assertEquals(0, f.cloud.publishes);
		}
	}

	@Test public void newerCloudSaveReplacesAncestorButKeepsRecoveryFile() throws Exception
	{
		try (Fixture f = new Fixture(saved(DEVICE_A, 1, 100)))
		{
			f.cloud.add(saved(DEVICE_A, 2, 200));
			f.load();
			assertEquals(200, f.service.getState().getGotchiPoints());
			assertEquals(1, f.recoveries());
		}
	}

	@Test public void resolvingConflictArchivesBothAndAcknowledgesBothBranches() throws Exception
	{
		try (Fixture f = new Fixture(saved(DEVICE_A, 3, 100)))
		{
			f.cloud.add(saved(DEVICE_B, 2, 200));
			f.load();
			assertTrue(f.service.hasSaveConflict());
			f.service.resolveSave(1, CHARACTER, f.service.getConflictToken());
			assertEquals(200, f.service.getState().getGotchiPoints());
			assertFalse(f.service.hasSaveConflict());
			assertEquals(2, f.recoveries());
			assertEquals(Long.valueOf(3), f.service.getState().getSaveVersions().get(DEVICE_A));
			assertEquals(Long.valueOf(2), f.service.getState().getSaveVersions().get(DEVICE_B));
		}
	}

	@Test public void importPreservesPreviousSaveAndRejectsCharacterSwitch() throws Exception
	{
		try (Fixture f = new Fixture(saved(DEVICE_A, 3, 100)))
		{
			f.load();
			f.service.importSave(CODEC.json(saved(DEVICE_B, 2, 200)), CHARACTER);
			assertEquals(200, f.service.getState().getGotchiPoints());
			assertEquals(2, f.recoveries());
			assertThrows(java.io.IOException.class, () -> f.service.importSave(f.service.exportSave(), "account-other"));
			assertEquals(200, f.service.getState().getGotchiPoints());
		}
	}

	@Test public void oversizedSyncNeverTrimsTheLocalCollection() throws Exception
	{
		ProfileState state = saved(DEVICE_A, 1, 200);
		for (int i = 0; i < 2000; i++) { state.getSkillBaselines().put(UUID.randomUUID().toString(), i); }
		String before = CODEC.json(state);
		assertThrows(java.io.IOException.class, () -> CODEC.pack(state));
		assertEquals(before, CODEC.json(state));
	}

	@Test public void corruptRemoteBlocksPublishingWithoutReplacingLocal() throws Exception
	{
		try (Fixture f = new Fixture(saved(DEVICE_A, 1, 123)))
		{
			f.cloud.broken = true;
			f.load();
			f.service.syncNow("rsprofile.test");
			assertEquals(123, f.service.getState().getGotchiPoints());
			assertEquals(0, f.cloud.publishes);
			assertTrue(f.service.getSyncStatus().contains("Sync paused"));
		}
	}

	private class Fixture implements AutoCloseable
	{
		final Path directory = folder.newFolder().toPath();
		final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		final ProfileStore store = new ProfileStore(new Gson(), executor, directory);
		final FakeCloud cloud = new FakeCloud();
		final GieligotchiStateService service = new GieligotchiStateService(store, new HatchService(null), cloud, CODEC);
		Fixture(ProfileState local) throws Exception
		{
			if (local != null) { store.save(CHARACTER, local); drain(); }
		}
		void drain() throws Exception { executor.submit(() -> { }).get(5, TimeUnit.SECONDS); }
		void load() throws Exception { service.load(CHARACTER, "rsprofile.test"); drain(); }
		long recoveries() throws Exception
		{
			try (java.util.stream.Stream<Path> files = Files.list(directory))
			{ return files.filter(p -> p.getFileName().toString().startsWith("recovery-")).count(); }
		}
		@Override public void close() throws Exception { executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS); }
	}

	private static class FakeCloud extends ProfileSync
	{
		List<ProfileState> saves = new ArrayList<>();
		int publishes;
		boolean broken;
		void add(ProfileState state) { saves.add(state); }
		@Override public String unavailable(String profile) { return null; }
		@Override public List<ProfileState> read(String profile, String character) throws java.io.IOException
		{
			if (broken) { throw new java.io.IOException("Invalid synced save"); }
			List<ProfileState> copies = new ArrayList<>();
			for (ProfileState state : saves) { copies.add(CODEC.read(CODEC.json(state), character)); }
			return copies;
		}
		@Override public void publish(String profile, String device, ProfileState state) { publishes++; }
	}
}
