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

import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of crops, containing a display name, seed and possible products, plus the
 * Farming data (level, planting/harvest XP and the per-herb chance-to-save constants) used by
 * the XP tracker and the herb planner.
 *
 * <p>The {@code ctsLow} / {@code ctsHigh} values are the herb-specific "chance to save a
 * harvest life" constants (out of 256) used by the planner; they are only meaningful for
 * {@link PatchType#HERBS} and are left at 0 for everything else.</p>
 */
public enum Crop
{
	UNKNOWN("Unknown", PatchType.UNKNOWN, -1, 1, 0, 0, 0, 0, -1, -1),

	// Allotments
	POTATO("Potatoes", PatchType.ALLOTMENTS, 3, 1, 8, 9, 0, 0, ItemID.POTATO_SEED, ItemID.POTATO),
	ONION("Onions", PatchType.ALLOTMENTS, 3, 5, 9.5, 10.5, 0, 0, ItemID.ONION_SEED, ItemID.ONION),
	CABBAGE("Cabbages", PatchType.ALLOTMENTS, 3, 7, 10, 11.5, 0, 0, ItemID.CABBAGE_SEED, ItemID.CABBAGE),
	TOMATO("Tomatoes", PatchType.ALLOTMENTS, 3, 12, 12.5, 14, 0, 0, ItemID.TOMATO_SEED, ItemID.TOMATO),
	SWEETCORN("Sweetcorn", PatchType.ALLOTMENTS, 3, 20, 17, 19, 0, 0, ItemID.SWEETCORN_SEED, ItemID.SWEETCORN),
	STRAWBERRY("Strawberries", PatchType.ALLOTMENTS, 3, 31, 26, 29, 0, 0, ItemID.STRAWBERRY_SEED, ItemID.STRAWBERRY),
	WATERMELON("Watermelons", PatchType.ALLOTMENTS, 3, 47, 48.5, 54.5, 0, 0, ItemID.WATERMELON_SEED, ItemID.WATERMELON),

	// Herbs (ctsLow / ctsHigh from the OSRS herb farming calculator data)
	GUAM("Guam", PatchType.HERBS, 1, 9, 11, 12.5, 25, 80, ItemID.GUAM_SEED, ItemID.GUAM_LEAF, ItemID.GRIMY_GUAM_LEAF),
	MARRENTIL("Marrentill", PatchType.HERBS, 1, 14, 13.5, 15, 28, 80, ItemID.MARRENTILL_SEED, ItemID.MARRENTILL, ItemID.GRIMY_MARRENTILL),
	TARROMIN("Tarromin", PatchType.HERBS, 1, 19, 16, 18, 31, 80, ItemID.TARROMIN_SEED, ItemID.TARROMIN, ItemID.GRIMY_TARROMIN),
	HARRALANDER("Harralander", PatchType.HERBS, 1, 26, 21.5, 24, 36, 80, ItemID.HARRALANDER_SEED, ItemID.HARRALANDER, ItemID.GRIMY_HARRALANDER),
	RANARR("Ranarr", PatchType.HERBS, 1, 32, 27, 30.5, 39, 80, ItemID.RANARR_SEED, ItemID.RANARR_WEED, ItemID.GRIMY_RANARR_WEED),
	TOADFLAX("Toadflax", PatchType.HERBS, 1, 38, 34, 38.5, 43, 80, ItemID.TOADFLAX_SEED, ItemID.TOADFLAX, ItemID.GRIMY_TOADFLAX),
	IRIT("Irit", PatchType.HERBS, 1, 44, 43, 48.5, 46, 80, ItemID.IRIT_SEED, ItemID.IRIT_LEAF, ItemID.GRIMY_IRIT_LEAF),
	AVANTOE("Avantoe", PatchType.HERBS, 1, 50, 54.5, 61.5, 50, 80, ItemID.AVANTOE_SEED, ItemID.AVANTOE, ItemID.GRIMY_AVANTOE),
	KWUARM("Kwuarm", PatchType.HERBS, 1, 56, 69, 78, 54, 80, ItemID.KWUARM_SEED, ItemID.KWUARM, ItemID.GRIMY_KWUARM),
	SNAPDRAGON("Snapdragon", PatchType.HERBS, 1, 62, 87.5, 98.5, 57, 80, ItemID.SNAPDRAGON_SEED, ItemID.SNAPDRAGON, ItemID.GRIMY_SNAPDRAGON),
	HUASCA("Huasca", PatchType.HERBS, 1, 65, 86.5, 110, 58, 80, ItemID.HUASCA_SEED, ItemID.HUASCA, ItemID.GRIMY_HUASCA),
	CADANTINE("Cadantine", PatchType.HERBS, 1, 67, 106.5, 120, 60, 80, ItemID.CADANTINE_SEED, ItemID.CADANTINE, ItemID.GRIMY_CADANTINE),
	LANTADYME("Lantadyme", PatchType.HERBS, 1, 73, 134.5, 151.5, 64, 80, ItemID.LANTADYME_SEED, ItemID.LANTADYME, ItemID.GRIMY_LANTADYME),
	DWARF_WEED("Dwarf weed", PatchType.HERBS, 1, 79, 170.5, 192, 67, 80, ItemID.DWARF_WEED_SEED, ItemID.DWARF_WEED, ItemID.GRIMY_DWARF_WEED),
	TORSTOL("Torstol", PatchType.HERBS, 1, 85, 199.5, 224.5, 71, 80, ItemID.TORSTOL_SEED, ItemID.TORSTOL, ItemID.GRIMY_TORSTOL),

	// Hops
	BARLEY("Barley", PatchType.HOPS, 4, 3, 8.5, 9.5, 0, 0, ItemID.BARLEY_SEED, ItemID.BARLEY),
	HAMMERSTONE("Hammerstone hops", PatchType.HOPS, 4, 4, 9, 10, 0, 0, ItemID.HAMMERSTONE_SEED, ItemID.HAMMERSTONE_HOPS),
	ASGARNIAN("Asgarnian hops", PatchType.HOPS, 4, 8, 10.9, 12, 0, 0, ItemID.ASGARNIAN_SEED, ItemID.ASGARNIAN_HOPS),
	JUTE("Jute fibre", PatchType.HOPS, 3, 13, 13, 14.5, 0, 0, ItemID.JUTE_SEED, ItemID.JUTE_FIBRE),
	YANILLIAN("Yanillian hops", PatchType.HOPS, 4, 16, 14.5, 16, 0, 0, ItemID.YANILLIAN_SEED, ItemID.YANILLIAN_HOPS),
	KRANDORIAN("Krandorian hops", PatchType.HOPS, 4, 21, 17.5, 19.5, 0, 0, ItemID.KRANDORIAN_SEED, ItemID.KRANDORIAN_HOPS),
	WILDBLOOD("Wildblood hops", PatchType.HOPS, 4, 28, 23, 26, 0, 0, ItemID.WILDBLOOD_SEED, ItemID.WILDBLOOD_HOPS),

	// Bushes
	REDBERRY("Redberries", PatchType.BUSHES, 1, 10, 11.5, 4.5, 0, 0, ItemID.REDBERRY_SEED, ItemID.REDBERRIES),
	CADAVABERRY("Cadava berries", PatchType.BUSHES, 1, 22, 18, 7, 0, 0, ItemID.CADAVABERRY_SEED, ItemID.CADAVA_BERRIES),
	DWELLBERRY("Dwellberries", PatchType.BUSHES, 1, 36, 31.5, 12, 0, 0, ItemID.DWELLBERRY_SEED, ItemID.DWELLBERRIES),
	JANGERBERRY("Jangerberries", PatchType.BUSHES, 1, 48, 50.5, 19, 0, 0, ItemID.JANGERBERRY_SEED, ItemID.JANGERBERRIES),
	WHITEBERRY("White berries", PatchType.BUSHES, 1, 59, 78, 29, 0, 0, ItemID.WHITEBERRY_SEED, ItemID.WHITE_BERRIES),
	POISON_IVY("Poison ivy berries", PatchType.BUSHES, 1, 70, 120, 45, 0, 0, ItemID.POISON_IVY_SEED, ItemID.POISON_IVY_BERRIES),

	// Special
	SEAWEED("Seaweed", PatchType.SPECIAL, 1, 23, 19, 21, 0, 0, ItemID.SEAWEED_SPORE, ItemID.GIANT_SEAWEED),
	CACTUS("Cactus", PatchType.SPECIAL, 1, 55, 66.5, 25, 0, 0, ItemID.CACTUS_SEED, ItemID.CACTUS_SPINE);


	Crop(String displayName, PatchType patchType, int seedAmount, int farmingLevel, double plantXp,
		double harvestXp, int ctsLow, int ctsHigh, int seedId, int... products)
	{
		this.seedAmount = seedAmount;
		this.seedId = seedId;
		this.products = products;
		this.patchType = patchType;
		this.displayName = displayName;
		this.farmingLevel = farmingLevel;
		this.plantXp = plantXp;
		this.harvestXp = harvestXp;
		this.ctsLow = ctsLow;
		this.ctsHigh = ctsHigh;
	}

	@Getter
	private final int seedAmount;
	@Getter
	private final int seedId;
	private final int[] products;
	@Getter
	private final PatchType patchType;
	@Getter
	private final String displayName;
	/** Farming level required to plant. */
	@Getter
	private final int farmingLevel;
	/** Farming XP awarded for planting the seed. */
	@Getter
	private final double plantXp;
	/** Farming XP awarded per individual product harvested. */
	@Getter
	private final double harvestXp;
	/** Chance-to-save-a-harvest-life constant at Farming level 1 (herbs only, out of 256). */
	@Getter
	private final int ctsLow;
	/** Chance-to-save-a-harvest-life constant at Farming level 99 (herbs only, out of 256). */
	@Getter
	private final int ctsHigh;

	private static final Map<Integer, Crop> map = Collections.unmodifiableMap(initializeMapping());

	public int getProductId()
	{
		if (products.length > 0)
		{
			return products[0];
		}
		return -1;
	}

	/**
	 * The item actually obtained when harvesting this crop. For herbs the products are listed
	 * as {@code [clean, grimy]}, and harvesting yields the grimy herb (the last product); for
	 * every other crop this is the same as {@link #getProductId()}.
	 */
	public int getHarvestedItemId()
	{
		if (products.length > 0)
		{
			return products[products.length - 1];
		}
		return -1;
	}

	public static Crop fromProductId(int product)
	{
		if (map.containsKey(product))
		{
			return map.get(product);
		}
		return Crop.UNKNOWN;
	}

	private static Map<Integer, Crop> initializeMapping()
	{
		Map<Integer, Crop> _map = new HashMap<>();
		for (Crop s : Crop.values())
		{
			for (Integer productId : s.products)
			{
				_map.put(productId, s);
			}
		}
		return _map;
	}

	public static Crop fromHarvestMessage(String message)
	{
		return HarvestMessages.cropFromMessage(message);
	}

	public String toString()
	{
		return displayName;
	}

}
