package com.gieligotchi.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Prevents player-dropped items from becoming fresh Heartfelt acquisitions when reclaimed. */
public final class ItemAcquisitionGuard
{
	private static final int DROP_LIFETIME_TICKS = 300;
	private static final int PICKUP_LIFETIME_TICKS = 5;
	private final List<Drop> drops = new ArrayList<>();
	private final List<PendingPickup> pendingPickups = new ArrayList<>();

	public void recordDrop(int itemId, int quantity, int x, int y, int plane, int tick)
	{
		if (itemId < 0 || quantity <= 0) { return; }
		prune(tick);
		for (Drop drop : drops)
		{
			if (drop.matches(itemId, x, y, plane))
			{
				drop.quantity += quantity;
				drop.expiresAt = tick + DROP_LIFETIME_TICKS;
				return;
			}
		}
		drops.add(new Drop(itemId, quantity, x, y, plane, tick + DROP_LIFETIME_TICKS));
	}

	public void recordTake(int itemId, int x, int y, int plane, int tick)
	{
		if (itemId < 0) { return; }
		prune(tick);
		for (Iterator<Drop> iterator = drops.iterator(); iterator.hasNext();)
		{
			Drop drop = iterator.next();
			if (!drop.matches(itemId, x, y, plane)) { continue; }
			pendingPickups.add(new PendingPickup(itemId, drop.quantity, tick + PICKUP_LIFETIME_TICKS));
			iterator.remove();
			return;
		}
	}

	public void recordTransfer(int itemId, int quantity, int tick)
	{
		if (itemId < 0 || quantity <= 0) { return; }
		prune(tick);
		pendingPickups.add(new PendingPickup(itemId, quantity, tick + PICKUP_LIFETIME_TICKS));
	}

	/** Returns only the part of an inventory increase that is a genuinely new acquisition. */
	public int freshQuantity(int itemId, int gained, int tick)
	{
		if (gained <= 0) { return 0; }
		prune(tick);
		int fresh = gained;
		for (Iterator<PendingPickup> iterator = pendingPickups.iterator(); iterator.hasNext() && fresh > 0;)
		{
			PendingPickup pickup = iterator.next();
			if (pickup.itemId != itemId) { continue; }
			int reclaimed = Math.min(fresh, pickup.quantity);
			fresh -= reclaimed;
			pickup.quantity -= reclaimed;
			if (pickup.quantity == 0) { iterator.remove(); }
		}
		return fresh;
	}

	public void clear()
	{
		drops.clear();
		pendingPickups.clear();
	}

	private void prune(int tick)
	{
		drops.removeIf(drop -> drop.expiresAt < tick);
		pendingPickups.removeIf(pickup -> pickup.expiresAt < tick);
	}

	private static final class Drop
	{
		private final int itemId;
		private int quantity;
		private final int x;
		private final int y;
		private final int plane;
		private int expiresAt;

		private Drop(int itemId, int quantity, int x, int y, int plane, int expiresAt)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.expiresAt = expiresAt;
		}

		private boolean matches(int candidateItemId, int candidateX, int candidateY, int candidatePlane)
		{
			return itemId == candidateItemId && x == candidateX && y == candidateY && plane == candidatePlane;
		}
	}

	private static final class PendingPickup
	{
		private final int itemId;
		private int quantity;
		private final int expiresAt;

		private PendingPickup(int itemId, int quantity, int expiresAt)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.expiresAt = expiresAt;
		}
	}
}
