/*
 * Copyright (c) 2018, Mika Kuijpers <github.com/mkuijpers>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.farmingprofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HerbYieldTest
{
	private static final int ULTRA = CompostTier.ULTRACOMPOST.getHarvestLives();
	private static final int NONE = CompostTier.NONE.getHarvestLives();

	/**
	 * Reference value: a level-99 Ranarr patch with ultracompost (6 lives) and magic secateurs
	 * (+10%) yields about 9.2 herbs, matching the OSRS wiki herb calculator.
	 */
	@Test
	public void ranarr99UltraSecateursMatchesWiki()
	{
		double yield = HerbYield.expectedYieldPerPatch(Crop.RANARR, 99, ULTRA, 0.10, false);
		assertEquals(9.2, yield, 0.1);
	}

	/** With no compost and no bonuses a level-99 Ranarr patch (3 lives) yields about 4.4. */
	@Test
	public void ranarr99NoCompostNoBonus()
	{
		double yield = HerbYield.expectedYieldPerPatch(Crop.RANARR, 99, NONE, 0.0, false);
		assertEquals(4.4, yield, 0.1);
	}

	@Test
	public void higherLevelYieldsMore()
	{
		double low = HerbYield.expectedYieldPerPatch(Crop.RANARR, 32, ULTRA, 0.0, false);
		double high = HerbYield.expectedYieldPerPatch(Crop.RANARR, 99, ULTRA, 0.0, false);
		assertTrue("yield should increase with farming level", high > low);
	}

	@Test
	public void moreCompostYieldsMore()
	{
		double none = HerbYield.expectedYieldPerPatch(Crop.TORSTOL, 99, NONE, 0.10, false);
		double ultra = HerbYield.expectedYieldPerPatch(Crop.TORSTOL, 99, ULTRA, 0.10, false);
		assertTrue("ultracompost should beat no compost", ultra > none);
	}

	@Test
	public void secateursAndCapeIncreaseYield()
	{
		double bare = HerbYield.expectedYieldPerPatch(Crop.SNAPDRAGON, 99, ULTRA, 0.0, false);
		double secateurs = HerbYield.expectedYieldPerPatch(Crop.SNAPDRAGON, 99, ULTRA, 0.10, false);
		double both = HerbYield.expectedYieldPerPatch(Crop.SNAPDRAGON, 99, ULTRA, 0.15, false);
		assertTrue(secateurs > bare);
		assertTrue("cape on top of secateurs should help further", both > secateurs);
	}

	@Test
	public void attasIncreasesYield()
	{
		double without = HerbYield.expectedYieldPerPatch(Crop.RANARR, 99, ULTRA, 0.10, false);
		double with = HerbYield.expectedYieldPerPatch(Crop.RANARR, 99, ULTRA, 0.10, true);
		assertTrue(with > without);
	}

	@Test
	public void chanceToSaveStaysBelowOne()
	{
		// Even fully boosted, the chance to save must remain a probability < 1.
		int low = HerbYield.boostConstant(Crop.TORSTOL.getCtsLow(), 0.15, true);
		int high = HerbYield.boostConstant(Crop.TORSTOL.getCtsHigh(), 0.15, true);
		double p = HerbYield.chanceToSave(99, low, high);
		assertTrue(p > 0 && p < 1);
	}

	@Test
	public void allHerbsHaveSaneData()
	{
		for (Crop crop : Crop.values())
		{
			if (crop.getPatchType() != PatchType.HERBS)
			{
				continue;
			}
			assertEquals("herbs share chanceHigh=80", 80, crop.getCtsHigh());
			assertTrue(crop.getDisplayName() + " ctsLow", crop.getCtsLow() > 0 && crop.getCtsLow() < 80);
			assertTrue(crop.getDisplayName() + " harvestXp", crop.getHarvestXp() > 0);
			assertTrue(crop.getDisplayName() + " plantXp", crop.getPlantXp() > 0);
			assertTrue(crop.getDisplayName() + " level", crop.getFarmingLevel() >= 1);
			// The harvested item is the grimy herb (last product), distinct from the clean herb.
			assertTrue(crop.getHarvestedItemId() > 0);
		}
	}

	@Test
	public void grimyHerbMapsBackToCrop()
	{
		assertEquals(Crop.RANARR, Crop.fromProductId(Crop.RANARR.getHarvestedItemId()));
		assertEquals(Crop.RANARR, Crop.fromProductId(Crop.RANARR.getProductId()));
	}
}
