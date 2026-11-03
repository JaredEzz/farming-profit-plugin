package com.farmingprofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CompostBucketMessagesTest
{
	@Test
	public void readsTheCheckMessage()
	{
		assertEquals(133, CompostBucketMessages.usesFrom("Your bottomless compost bucket is currently holding 133 uses of ultracompost."));
		assertEquals(1, CompostBucketMessages.usesFrom("Your bottomless compost bucket is currently holding one use of supercompost."));
	}

	@Test
	public void readsTheLowUsesMessage()
	{
		assertEquals(5, CompostBucketMessages.usesFrom("Your bottomless compost bucket has 5 uses of ultracompost remaining."));
		assertEquals(1, CompostBucketMessages.usesFrom("Your bottomless compost bucket has a single use of compost remaining."));
	}

	@Test
	public void readsTheFillTotalNotTheBucketsAdded()
	{
		assertEquals(1500, CompostBucketMessages.usesFrom(
			"You fill your bottomless compost bucket with 2 buckets of ultracompost. Your bottomless compost bucket now contains a total of 1,500 uses."));
		assertEquals(10000, CompostBucketMessages.usesFrom("Your bottomless compost bucket is now full!"));
	}

	@Test
	public void handlesDialogFormatting()
	{
		assertEquals(1234, CompostBucketMessages.usesFrom("Your bottomless compost bucket<br>is currently holding 1,234 uses of ultracompost."));
	}

	@Test
	public void readsEmptyMessages()
	{
		assertEquals(0, CompostBucketMessages.usesFrom("Your bottomless compost bucket has run out of compost!"));
		assertEquals(0, CompostBucketMessages.usesFrom("Your compost bucket is currently empty."));
		assertEquals(0, CompostBucketMessages.usesFrom("Your bottomless compost bucket doesn't currently have any compost in it!"));
		assertEquals(0, CompostBucketMessages.usesFrom("You discard the contents of your bottomless compost bucket."));
	}

	@Test
	public void ignoresOtherText()
	{
		assertEquals(CompostBucketMessages.NO_COUNT, CompostBucketMessages.usesFrom("You treat the herb patch with ultracompost."));
		assertEquals(CompostBucketMessages.NO_COUNT, CompostBucketMessages.usesFrom("You fill your bottomless compost bucket with 2 buckets of ultracompost."));
	}

	@Test
	public void recognisesCompostBeingApplied()
	{
		assertTrue(CompostBucketMessages.isCompostApplied("You treat the herb patch with ultracompost."));
		assertTrue(CompostBucketMessages.isCompostApplied("You treat the allotment with compost."));
		assertFalse(CompostBucketMessages.isCompostApplied("Your bottomless compost bucket has 5 uses of ultracompost remaining."));
	}
}
