package com.farmingprofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class HerbHighScoreTest
{
	private static final long BEFORE_FIX = Instant.parse("2026-11-02T00:00:00Z").toEpochMilli();
	private static final long AFTER_FIX = Instant.parse("2026-11-03T06:19:42Z").toEpochMilli();

	private static RunRecord record(String crop, int amount, long time)
	{
		return new RunRecord(1, 12083, crop, amount, 0, 0, 0, time);
	}

	@Test
	public void historyBeforeTheFixLosesItsExtraHerb()
	{
		final HerbHighScore score = HerbHighScore.fromHistory(Collections.singletonList(record("TORSTOL", 14, BEFORE_FIX)));
		assertEquals(13, score.amount);
		assertEquals("TORSTOL", score.crop);
	}

	@Test
	public void historyAfterTheFixIsKeptAsIs()
	{
		final HerbHighScore score = HerbHighScore.fromHistory(Arrays.asList(
			record("TORSTOL", 18, BEFORE_FIX), record("SNAPDRAGON", 18, AFTER_FIX)));
		assertEquals(18, score.amount);
		assertEquals("SNAPDRAGON", score.crop);
	}

	@Test
	public void onlyHerbsCount()
	{
		assertNull(HerbHighScore.fromHistory(Arrays.asList(
			record("WATERMELON", 30, AFTER_FIX), record("UNKNOWN", 0, AFTER_FIX), record(null, 50, AFTER_FIX))));
	}

	@Test
	public void aTieKeepsTheEarlierRecord()
	{
		final HerbHighScore current = new HerbHighScore(18, "SNAPDRAGON", 12083, AFTER_FIX);
		assertFalse(HerbHighScore.beats(current, 18));
		assertTrue(HerbHighScore.beats(current, 19));
		assertTrue(HerbHighScore.beats(null, 1));
		assertFalse(HerbHighScore.beats(null, 0));
	}
}
