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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("farmingProfit")
public interface FarmingProfitConfig extends Config
{
	@ConfigSection(
		name = "Tracker",
		description = "Settings for the live farm-run tracker",
		position = 0
	)
	String trackerSection = "tracker";

	@ConfigSection(
		name = "Herb planner",
		description = "Assumptions used by the herb-run profit/XP planner",
		position = 1
	)
	String plannerSection = "planner";

	@ConfigItem(
		keyName = "displayMode",
		name = "Value patches by",
		description = "Show Grand Exchange profit, Farming XP, or auto-detect (XP for ironmen, profit otherwise)",
		position = 0,
		section = trackerSection
	)
	default DisplayMode displayMode()
	{
		return DisplayMode.AUTO;
	}

	@ConfigItem(
		keyName = "trackAllotments",
		name = "Track allotment patches",
		description = "Track profit made of allotment patches",
		position = 1,
		section = trackerSection
	)
	default boolean trackAllotments()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackHerbs",
		name = "Track herb patches",
		description = "Track profit made of herb patches",
		position = 2,
		section = trackerSection
	)
	default boolean trackHerbs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackHops",
		name = "Track hops patches",
		description = "Track profit made of hops patches",
		position = 3,
		section = trackerSection
	)
	default boolean trackHops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackBushes",
		name = "Track bush patches",
		description = "Track profit made of bush patches",
		position = 4,
		section = trackerSection
	)
	default boolean trackBushes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackSpecial",
		name = "Track special patches",
		description = "Track profit made of the special patches, cactus and seaweed",
		position = 5,
		section = trackerSection
	)
	default boolean trackSpecial()
	{
		return true;
	}

	@ConfigItem(
		keyName = "plannerCompost",
		name = "Compost",
		description = "Compost applied to your herb patches. Sets the number of harvest lives (more = more herbs).",
		position = 0,
		section = plannerSection
	)
	default CompostTier plannerCompost()
	{
		return CompostTier.ULTRACOMPOST;
	}

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "plannerPatches",
		name = "Number of patches",
		description = "How many herb patches you plant per run",
		position = 1,
		section = plannerSection
	)
	default int plannerPatches()
	{
		return 9;
	}

	@ConfigItem(
		keyName = "plannerAttas",
		name = "Attas plant",
		description = "Whether you grow an Attas plant alongside your run (+5% herb yield)",
		position = 2,
		section = plannerSection
	)
	default boolean plannerAttas()
	{
		return false;
	}
}
