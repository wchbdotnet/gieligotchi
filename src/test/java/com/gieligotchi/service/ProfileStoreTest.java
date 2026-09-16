package com.gieligotchi.service;

import com.gieligotchi.model.ProfileState;
import com.gieligotchi.model.Discovery;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileStoreTest
{
	@Test
	public void resetReplacesPrimaryAndBackupWithFreshAccount() throws Exception
	{
		Path directory = Files.createTempDirectory("gieligotchi-reset-test");
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			ProfileStore store = new ProfileStore(new Gson(), executor, directory);
			ProfileState existing = ProfileState.fresh("account-1");
			existing.grantGotchiPoints(999);
			existing.getDiscoveries().put("soup", new Discovery());
			store.save("account-1", existing);
			store.backup("account-1", existing);
			store.reset("account-1", ProfileState.fresh("account-1"));
			executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

			CountDownLatch loaded = new CountDownLatch(1);
			ProfileState[] result = new ProfileState[1];
			store.load("account-1", state ->
			{
				result[0] = state;
				loaded.countDown();
			});

			assertTrue(loaded.await(5, TimeUnit.SECONDS));
			assertEquals(0L, result[0].getGotchiPoints());
			assertTrue(result[0].getDiscoveries().isEmpty());
			assertTrue(result[0].getHatchHistory().isEmpty());
			assertTrue(result[0].getActiveEgg().isStarter());
		}
		finally
		{
			executor.shutdownNow();
			try (java.util.stream.Stream<Path> paths = Files.walk(directory))
			{
				paths.sorted(Comparator.reverseOrder()).forEach(path ->
				{
					try { Files.deleteIfExists(path); }
					catch (Exception ignored) { }
				});
			}
		}
	}

	@Test
	public void unreadablePrimarySaveFallsBackToLogoutBackup() throws Exception
	{
		Path directory = Files.createTempDirectory("gieligotchi-profile-test");
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try
		{
			ProfileStore store = new ProfileStore(new Gson(), executor, directory);
			ProfileState expected = ProfileState.fresh("account-1");
			expected.grantGotchiPoints(321);
			store.save("account-1", expected);
			store.backup("account-1", expected);
			executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

			Files.write(directory.resolve("account-1.json"), "not valid json".getBytes(StandardCharsets.UTF_8));
			CountDownLatch loaded = new CountDownLatch(1);
			long[] points = new long[1];
			store.load("account-1", state ->
			{
				points[0] = state.getGotchiPoints();
				loaded.countDown();
			});

			assertTrue(loaded.await(5, TimeUnit.SECONDS));
			assertEquals(321L, points[0]);
		}
		finally
		{
			executor.shutdownNow();
			try (java.util.stream.Stream<Path> paths = Files.walk(directory))
			{
				paths.sorted(Comparator.reverseOrder()).forEach(path ->
				{
					try { Files.deleteIfExists(path); }
					catch (Exception ignored) { }
				});
			}
		}
	}
}
