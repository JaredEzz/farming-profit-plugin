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

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ItemID;

/**
 * The game's 10 herb patches and the decode for a herb patch varbit value, reimplemented from
 * RuneLite's (package-private) timetracking {@code PatchImplementation.HERB.forVarbitValue}.
 *
 * <p>A single herb patch varbit encodes both the planted herb and its growth state. The contiguous
 * value bands (verified against client 1.12.31.1) are: {@code 0-3} weeds, then per herb a GROWING
 * sub-band followed by a HARVESTABLE sub-band, e.g. Ranarr {@code 32-35} growing / {@code 36-38}
 * harvestable. Diseased/dead/empty live in higher bands.
 *
 * <p>The region ids are {@link net.runelite.api.coords.WorldPoint#getRegionID()} values of each
 * patch, so the patch a player is standing on can be found with {@link #forRegion(int)}.
 */
final class HerbPatches
{
	/**
	 * A herb patch: the map region it sits in, the varbit holding its state, a display name, and an
	 * in-game item that represents its location (the teleport players use to get there).
	 */
	static final class HerbPatch
	{
		final int regionId;
		final int varbit;
		final String name;
		/** Default icon for the "Teleport tablets" style. */
		final int teleportItemId;
		/** Default icon for the "Themed" style (cape/ring/etc.). */
		final int themedItemId;

		HerbPatch(int regionId, int varbit, String name, int teleportItemId, int themedItemId)
		{
			this.regionId = regionId;
			this.varbit = varbit;
			this.name = name;
			this.teleportItemId = teleportItemId;
			this.themedItemId = themedItemId;
		}
	}

	static final List<HerbPatch> PATCHES = new ArrayList<>();

	static
	{
		//                       region  varbit  name                   teleport icon                      themed icon
		PATCHES.add(new HerbPatch(11062, 4774, "Catherby", ItemID.CATHERBY_TELEPORT, ItemID.CAMELOT_TELEPORT));
		PATCHES.add(new HerbPatch(12083, 4774, "Falador", ItemID.FALADOR_TELEPORT, ItemID.EXPLORERS_RING_4));
		PATCHES.add(new HerbPatch(10548, 4774, "Ardougne", ItemID.ARDOUGNE_TELEPORT, ItemID.ARDOUGNE_CLOAK_4));
		PATCHES.add(new HerbPatch(14391, 4774, "Morytania", ItemID.KHARYRLL_TELEPORT, ItemID.ECTOPHIAL));
		PATCHES.add(new HerbPatch(6967, 4774, "Hosidius", ItemID.HOSIDIUS_TELEPORT, ItemID.XERICS_TALISMAN));
		PATCHES.add(new HerbPatch(6192, 4774, "Civitas illa Fortis", ItemID.CIVITAS_ILLA_FORTIS_TELEPORT, ItemID.PERFECTED_QUETZAL_WHISTLE));
		PATCHES.add(new HerbPatch(4922, 4775, "Farming Guild", ItemID.SKILLS_NECKLACE, ItemID.FARMING_CAPE));
		PATCHES.add(new HerbPatch(11321, 4771, "Troll Stronghold", ItemID.STONY_BASALT, ItemID.STONY_BASALT));
		PATCHES.add(new HerbPatch(11325, 4771, "Weiss", ItemID.ICY_BASALT, ItemID.ICY_BASALT));
		PATCHES.add(new HerbPatch(15148, 4772, "Harmony", ItemID.HARMONY_ISLAND_TELEPORT, ItemID.HARMONY_ISLAND_TELEPORT));
	}

	// Herb crops in varbit-band order, each with its [low, high] "alive" value range (growing +
	// harvestable). Diseased/dead/empty fall outside these ranges and decode to UNKNOWN.
	private static final Crop[] BAND_CROP = {
		Crop.GUAM, Crop.MARRENTIL, Crop.TARROMIN, Crop.HARRALANDER, Crop.RANARR,
		Crop.TOADFLAX, Crop.IRIT, Crop.AVANTOE, Crop.HUASCA, Crop.KWUARM,
		Crop.SNAPDRAGON, Crop.CADANTINE, Crop.LANTADYME, Crop.DWARF_WEED, Crop.TORSTOL,
	};
	private static final int[] BAND_LOW  = {4, 11, 18, 25, 32, 39, 46, 53, 60, 68, 75, 82, 89, 96, 103};
	private static final int[] BAND_HIGH = {10, 17, 24, 31, 38, 45, 52, 59, 66, 74, 81, 88, 95, 102, 109};

	/** The herb patch in the given map region, or {@code null} if that region has no herb patch. */
	static HerbPatch forRegion(int regionId)
	{
		for (HerbPatch p : PATCHES)
		{
			if (p.regionId == regionId)
			{
				return p;
			}
		}
		return null;
	}

	/** Decode a herb patch varbit value to its planted {@link Crop}, or UNKNOWN if weeds/empty/dead. */
	static Crop decodeCrop(int varbitValue)
	{
		for (int i = 0; i < BAND_CROP.length; i++)
		{
			if (varbitValue >= BAND_LOW[i] && varbitValue <= BAND_HIGH[i])
			{
				return BAND_CROP[i];
			}
		}
		return Crop.UNKNOWN;
	}

	/** True if the value is in a herb's HARVESTABLE sub-band (the top 3 values of its range). */
	static boolean isHarvestable(int varbitValue)
	{
		for (int i = 0; i < BAND_CROP.length; i++)
		{
			if (varbitValue >= BAND_LOW[i] && varbitValue <= BAND_HIGH[i])
			{
				return varbitValue >= BAND_HIGH[i] - 2;
			}
		}
		return false;
	}

	/**
	 * Growth stage 0..3 within a herb's GROWING sub-band (the first 4 values of its range), or -1 if
	 * the value isn't a growing herb (harvestable / weeds / diseased / dead).
	 */
	static int decodeStage(int varbitValue)
	{
		for (int i = 0; i < BAND_CROP.length; i++)
		{
			if (varbitValue >= BAND_LOW[i] && varbitValue <= BAND_LOW[i] + 3)
			{
				return varbitValue - BAND_LOW[i];
			}
		}
		return -1;
	}

	/**
	 * Full patch state from a herb varbit value, ported from {@code PatchImplementation.HERB}:
	 * harvestable/growing in the alive bands, then diseased (128-169, 173-175), dead (170-172),
	 * everything else (weeds/empty) EMPTY.
	 */
	static HerbPatchStatus.State decodeState(int v)
	{
		if (isHarvestable(v))
		{
			return HerbPatchStatus.State.READY;
		}
		if (decodeCrop(v) != Crop.UNKNOWN)
		{
			return HerbPatchStatus.State.GROWING;
		}
		if (v >= 170 && v <= 172)
		{
			return HerbPatchStatus.State.DEAD;
		}
		if ((v >= 128 && v <= 169) || (v >= 173 && v <= 175))
		{
			return HerbPatchStatus.State.DISEASED;
		}
		return HerbPatchStatus.State.EMPTY;
	}

	private HerbPatches()
	{
	}
}
