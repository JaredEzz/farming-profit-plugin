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

import lombok.Value;

/**
 * Immutable snapshot of everything the herb planner needs to rank herbs: the player's level and
 * detected gear, plus the configured run assumptions.
 */
@Value
class PlannerInputs
{
	/** Farming level used for the yield calculation. */
	int farmingLevel;
	/** True if the level was read from the logged-in player (false = assumed). */
	boolean levelKnown;
	/** Magic secateurs detected on the player. */
	boolean magicSecateurs;
	/** Farming (or max) cape detected on the player. */
	boolean farmingCape;
	/** Attas plant assumed grown this run. */
	boolean attas;
	/** Compost applied to the patches. */
	CompostTier compost;
	/** Number of herb patches planted per run. */
	int patches;
	/** True to rank/show by Farming XP, false for GE profit. */
	boolean xpMode;

	/** Combined chance-to-save item bonus: +10% secateurs, +5% cape (herbs only). */
	double itemBonusPct()
	{
		double bonus = 0.0;
		if (magicSecateurs)
		{
			bonus += 0.10;
		}
		if (farmingCape)
		{
			bonus += 0.05;
		}
		return bonus;
	}
}
