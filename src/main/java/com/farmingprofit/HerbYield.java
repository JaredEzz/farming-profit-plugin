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

/**
 * Pure functions implementing the Old School RuneScape herb-patch yield model.
 *
 * <p>A herb patch has a fixed number of "harvest lives" (set by the compost used). Each pick has
 * a "chance to save a harvest life" {@code p}; a saved life yields an extra herb without
 * consuming a life, so the expected number of herbs per patch is
 * {@code lives / (1 - p)}.</p>
 *
 * <p>{@code p} is interpolated by Farming level between the crop's level-1 ({@code ctsLow}) and
 * level-99 ({@code ctsHigh}) constants, after the yield-boosting items are applied to those
 * constants. Magic secateurs (+10%) and the Farming/Max cape (+5%, herbs only) are multiplicative
 * on the constants; the Attas plant (+5%) is applied last. This mirrors the wiki's
 * {@code Module:Herb_Farming_calculator} mechanics.</p>
 */
final class HerbYield
{
	private HerbYield()
	{
	}

	/**
	 * Apply the yield-boosting items to a chance-to-save constant, in the game's fixed order:
	 * item bonus (secateurs + cape, summed) first, then Attas.
	 *
	 * @param constant     the crop's raw ctsLow or ctsHigh
	 * @param itemBonusPct combined magic secateurs / cape bonus, e.g. 0.15 for both
	 * @param attas        whether an Attas plant is grown alongside the run (+5%)
	 * @return the boosted constant
	 */
	static int boostConstant(int constant, double itemBonusPct, boolean attas)
	{
		int boosted = (int) Math.floor(constant * (1.0 + itemBonusPct));
		if (attas)
		{
			boosted = (int) Math.floor(boosted * 1.05);
		}
		return boosted;
	}

	/**
	 * Chance to save a harvest life at the given Farming level, interpolating between the
	 * (already boosted) low and high constants.
	 *
	 * @param level    Farming level (1-99)
	 * @param lowConst boosted level-1 constant
	 * @param highConst boosted level-99 constant
	 * @return probability in [0, 1)
	 */
	static double chanceToSave(int level, int lowConst, int highConst)
	{
		int clamped = Math.max(1, Math.min(99, level));
		double interpolated = (lowConst * (99.0 - clamped) + highConst * (clamped - 1.0)) / 98.0;
		// floor(x + 0.5) is round-half-up. The herb chance-to-save specifically rounds (not a
		// plain floor) per the wiki Crop_yield formula, so this +0.5 is intentional.
		double numerator = 1.0 + Math.floor(interpolated + 0.5);
		return numerator / 256.0;
	}

	/**
	 * Expected number of herbs harvested from a single patch.
	 *
	 * @param crop         the herb crop
	 * @param level        Farming level
	 * @param lives        harvest lives (from the compost tier)
	 * @param itemBonusPct combined secateurs/cape bonus (e.g. 0.15)
	 * @param attas        whether Attas is grown
	 * @return expected herbs per patch
	 */
	static double expectedYieldPerPatch(Crop crop, int level, int lives, double itemBonusPct, boolean attas)
	{
		int low = boostConstant(crop.getCtsLow(), itemBonusPct, attas);
		int high = boostConstant(crop.getCtsHigh(), itemBonusPct, attas);
		double p = chanceToSave(level, low, high);
		if (p >= 1.0)
		{
			p = 0.999;
		}
		return lives / (1.0 - p);
	}
}
