package com.farmingprofit;

import java.time.Instant;
import java.util.List;

/** The most herbs ever harvested from one patch. Kept apart from the run history, so it outlives it. */
final class HerbHighScore
{
	/**
	 * Runs saved before this time counted one herb too many: the last pick of a patch also pays the
	 * planting XP, which read as a second herb. Only used to seed the score from old history.
	 */
	private static final long ACCURATE_COUNTS_SINCE = Instant.parse("2026-11-03T06:13:00Z").toEpochMilli();

	final int amount;
	final String crop;
	final int regionId;
	final long timeMillis;

	HerbHighScore(int amount, String crop, int regionId, long timeMillis)
	{
		this.amount = amount;
		this.crop = crop;
		this.regionId = regionId;
		this.timeMillis = timeMillis;
	}

	/** Whether a patch of {@code amount} herbs beats {@code current}. A tie keeps the earlier record. */
	static boolean beats(HerbHighScore current, int amount)
	{
		return amount > (current == null ? 0 : current.amount);
	}

	/** Work out the score from saved run history, or null if it holds no herb harvests. */
	static HerbHighScore fromHistory(List<RunRecord> records)
	{
		HerbHighScore best = null;
		for (RunRecord record : records)
		{
			if (!isHerb(record.crop))
			{
				continue;
			}
			final int amount = record.timeMillis < ACCURATE_COUNTS_SINCE ? record.amount - 1 : record.amount;
			if (beats(best, amount))
			{
				best = new HerbHighScore(amount, record.crop, record.regionId, record.timeMillis);
			}
		}
		return best;
	}

	private static boolean isHerb(String crop)
	{
		try
		{
			return Crop.valueOf(crop).getPatchType() == PatchType.HERBS;
		}
		catch (IllegalArgumentException | NullPointerException ex)
		{
			return false;
		}
	}
}
