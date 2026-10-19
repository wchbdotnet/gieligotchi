package com.gieligotchi;

import com.gieligotchi.service.ItemAcquisitionGuard;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ItemAcquisitionGuardTest
{
	@Test
	public void repeatedlyDroppingAndTakingOneOreNeverCreatesFreshProgress()
	{
		ItemAcquisitionGuard guard = new ItemAcquisitionGuard();
		for (int tick = 1; tick <= 20; tick += 2)
		{
			guard.recordDrop(440, 1, 3200, 3200, 0, tick);
			guard.recordTake(440, 3200, 3200, 0, tick + 1);
			assertEquals(0, guard.freshQuantity(440, 1, tick + 1));
		}
	}

	@Test
	public void genuinelyMinedOreStillCounts()
	{
		ItemAcquisitionGuard guard = new ItemAcquisitionGuard();
		assertEquals(1, guard.freshQuantity(440, 1, 10));
	}

	@Test
	public void anotherGroundItemOrLocationIsNotMistakenForThePlayersDrop()
	{
		ItemAcquisitionGuard guard = new ItemAcquisitionGuard();
		guard.recordDrop(440, 1, 3200, 3200, 0, 1);
		guard.recordTake(440, 3201, 3200, 0, 2);
		assertEquals(1, guard.freshQuantity(440, 1, 2));
		guard.recordTake(441, 3200, 3200, 0, 3);
		assertEquals(1, guard.freshQuantity(441, 1, 3));
	}

	@Test
	public void onlyTheReclaimedPartOfAStackIsIgnored()
	{
		ItemAcquisitionGuard guard = new ItemAcquisitionGuard();
		guard.recordDrop(995, 10, 3200, 3200, 0, 1);
		guard.recordTake(995, 3200, 3200, 0, 2);
		assertEquals(3, guard.freshQuantity(995, 13, 2));
	}

	@Test
	public void staleDropRecordsDoNotSuppressLaterSkilling()
	{
		ItemAcquisitionGuard guard = new ItemAcquisitionGuard();
		guard.recordDrop(440, 1, 3200, 3200, 0, 1);
		guard.recordTake(440, 3200, 3200, 0, 302);
		assertEquals(1, guard.freshQuantity(440, 1, 302));
	}

	@Test
	public void movingAnExistingEquippedItemBackToInventoryIsNotAnAcquisition()
	{
		ItemAcquisitionGuard guard = new ItemAcquisitionGuard();
		guard.recordTransfer(25975, 1, 10);
		assertEquals(0, guard.freshQuantity(25975, 1, 11));
	}
}
