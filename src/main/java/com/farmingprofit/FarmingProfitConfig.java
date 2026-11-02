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

import java.awt.Color;
import net.runelite.client.config.Alpha;
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

	@ConfigSection(
		name = "Location icons",
		description = "Item shown as each patch's location icon (enter any item id to override)",
		position = 2,
		closedByDefault = true
	)
	String iconSection = "icons";

	@ConfigSection(
		name = "Mastering Mixology",
		description = "Estimate herbs needed to hit your Mastering Mixology reward goals",
		position = 3,
		closedByDefault = true
	)
	String mixologySection = "mixology";

	@ConfigSection(
		name = "Notifications",
		description = "Push a phone notification (via ntfy) when your next herb run is ready. Warning: "
			+ "when enabled, a short text message (a patch/herb count, no account info) is sent over "
			+ "HTTPS to the ntfy URL you configure below, which may be a third-party server (ntfy.sh by "
			+ "default, or any self-hosted server you choose).",
		position = 4,
		closedByDefault = true
	)
	String notificationsSection = "notifications";

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
		keyName = "showRunGraph",
		name = "Show run graph",
		description = "Show a cumulative expected-vs-actual herb graph in each run",
		position = 6,
		section = trackerSection
	)
	default boolean showRunGraph()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "expectedLineColor",
		name = "Expected line",
		description = "Colour of the expected (target) line in the run graph",
		position = 7,
		section = trackerSection
	)
	default Color expectedLineColor()
	{
		return new Color(144, 238, 144); // light green
	}

	@Alpha
	@ConfigItem(
		keyName = "actualLineColor",
		name = "Actual line",
		description = "Colour of the actual (harvested) line in the run graph",
		position = 8,
		section = trackerSection
	)
	default Color actualLineColor()
	{
		return new Color(255, 215, 0); // yellow
	}

	@ConfigItem(
		keyName = "showLocationIcons",
		name = "Show location icons",
		description = "Show a small teleport-item icon next to each patch in the herb-run helper",
		position = 9,
		section = trackerSection
	)
	default boolean showLocationIcons()
	{
		return true;
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

	@ConfigItem(
		keyName = "iconStyle",
		name = "Icon style",
		description = "Default item set for the patch location icons; per-patch ids below override it",
		position = 0,
		section = iconSection
	)
	default IconStyle iconStyle()
	{
		return IconStyle.TELEPORTS;
	}

	@ConfigItem(keyName = "iconCatherby", name = "Catherby", description = "Item id override for Catherby (0 = use icon style)", position = 1, section = iconSection)
	default int iconCatherby()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconFalador", name = "Falador", description = "Item id override for Falador (0 = use icon style)", position = 2, section = iconSection)
	default int iconFalador()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconArdougne", name = "Ardougne", description = "Item id override for Ardougne (0 = use icon style)", position = 3, section = iconSection)
	default int iconArdougne()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconMorytania", name = "Morytania", description = "Item id override for Morytania (0 = use icon style)", position = 4, section = iconSection)
	default int iconMorytania()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconHosidius", name = "Hosidius", description = "Item id override for Hosidius (0 = use icon style)", position = 5, section = iconSection)
	default int iconHosidius()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconCivitas", name = "Civitas illa Fortis", description = "Item id override for Civitas illa Fortis (0 = use icon style)", position = 6, section = iconSection)
	default int iconCivitas()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconFarmingGuild", name = "Farming Guild", description = "Item id override for Farming Guild (0 = use icon style)", position = 7, section = iconSection)
	default int iconFarmingGuild()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconTrollStronghold", name = "Troll Stronghold", description = "Item id override for Troll Stronghold (0 = use icon style)", position = 8, section = iconSection)
	default int iconTrollStronghold()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconWeiss", name = "Weiss", description = "Item id override for Weiss (0 = use icon style)", position = 9, section = iconSection)
	default int iconWeiss()
	{
		return 0;
	}

	@ConfigItem(keyName = "iconHarmony", name = "Harmony", description = "Item id override for Harmony (0 = use icon style)", position = 10, section = iconSection)
	default int iconHarmony()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "mixologyEnabled",
		name = "Show Mixology helper",
		description = "Show a Mastering Mixology section in the Planner: resin balances, your reward "
			+ "goals (read from the Easy Mixology plugin if installed), and herbs needed to reach them",
		position = 0,
		section = mixologySection
	)
	default boolean mixologyEnabled()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "resinPerPastePct",
		name = "Resin per paste (%)",
		description = "Estimated spendable resin gained per paste deposited into the hopper. Tune this "
			+ "to match your conversion rate; the herbs-needed estimate scales with it.",
		position = 1,
		section = mixologySection
	)
	default int resinPerPastePct()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "ntfyEnabled",
		name = "Push herb-run notifications",
		description = "When your whole herb run is ready, send a push notification (via an HTTPS POST "
			+ "containing only a short status message, no account details) to the ntfy URL configured "
			+ "below. Off by default; only fires while RuneLite is running.",
		position = 0,
		section = notificationsSection
	)
	default boolean ntfyEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "ntfyUrl",
		name = "ntfy topic URL",
		description = "Your ntfy topic URL, e.g. https://ntfy.sh/my-herb-runs (or a self-hosted server "
			+ "URL). For a protected topic, append your access token after a pipe: "
			+ "https://ntfy.sh/my-topic|tk_xxxxx",
		position = 1,
		section = notificationsSection
	)
	default String ntfyUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "ntfyPerPatch",
		name = "Notify per patch (not whole run)",
		description = "Send a notification as each individual patch finishes growing, instead of one "
			+ "notification when the entire run is ready.",
		position = 2,
		section = notificationsSection
	)
	default boolean ntfyPerPatch()
	{
		return false;
	}
}
