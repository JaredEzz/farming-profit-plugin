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
import lombok.Setter;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

import java.time.LocalDateTime;
import net.runelite.client.util.QuantityFormatter;

class FarmingProfitRun
{

	// Class variables
	@Getter
	private Crop crop;
	@Getter
	private int gameObjClicked;
	@Getter
	private int amount;
	@Getter
	private WorldPoint latestHarvestWorldPoint;
	@Getter
	private LocalDateTime latestHarvestTime;

	// Util variables
	@Getter
	private int profit;
	/** Farming XP gained from harvesting this patch (per-herb harvest XP times the amount). */
	@Getter
	private double xp;
	@Getter
	private String tooltip;
	/** Expected herbs for this patch given level/compost/gear; 0 when not computed / not a herb. */
	@Getter
	@Setter
	private double expectedYield;

	// Item manager
	private ItemManager itemManager;

	/** Set for runs restored from saved history (no live WorldPoint); overrides the derived region. */
	private Integer restoredRegionId;


	FarmingProfitRun(ItemManager itemManager, Crop crop, int amount, WorldPoint latestHarvest, int gameObjClicked)
	{
		this.itemManager = itemManager;
		this.crop = crop;
		this.gameObjClicked = gameObjClicked;
		updateRun(amount, latestHarvest);
	}

	/** Rebuild a finished run from persisted values (no price recompute / itemManager needed). */
	FarmingProfitRun(Crop crop, int amount, int regionId, int profit, double xp,
		double expectedYield, LocalDateTime time)
	{
		this.crop = crop;
		this.amount = amount;
		this.restoredRegionId = regionId;
		this.profit = profit;
		this.xp = xp;
		this.expectedYield = expectedYield;
		this.latestHarvestTime = time;
		this.tooltip = "<html>" + getPatchName() + ": " + amount + "x " + crop.getDisplayName() + "</html>";
	}

	/**
	 * Update various variables of the farming run which have to be updated after the amount changes
	 *
	 * @param amount            The new amount for the farming run
	 * @param harvestWorldPoint The new location of the farming run
	 */
	private void updateRun(int amount, WorldPoint harvestWorldPoint)
	{
		this.amount = amount;
		latestHarvestWorldPoint = harvestWorldPoint;
		updateProfit();
		latestHarvestTime = LocalDateTime.now();
	}

	/**
	 * Add a certain amount of products to the run, with a new location
	 *
	 * @param toAdd Amount of products to add
	 */
	void addAmount(int toAdd)
	{
		updateRun(amount + toAdd, latestHarvestWorldPoint);
	}

	/**
	 * Update the profit variable, followed by updating the tooltip string
	 * <p>
	 * Created a separate updateRun and getter for the profit because getting the item prices has to be done on
	 * the client thread, which is not possible when the UI tries to access the profit.
	 */
	private void updateProfit()
	{
		int seedPrice = itemManager.getItemPrice(crop.getSeedId());
		int productPrice = itemManager.getItemPrice(crop.getHarvestedItemId());

		profit = productPrice * amount - seedPrice * crop.getSeedAmount();
		xp = amount * crop.getHarvestXp();
		updateTooltip();
	}

	/**
	 * Update the tooltip variable.
	 * <p>
	 * Created a separate updateRun and getter for the tooltip because getting the item compositions has to be done on
	 * the client thread, which is not possible when the UI tries to access the tooltip.
	 */
	private void updateTooltip()
	{
		ItemComposition seed = itemManager.getItemComposition(crop.getSeedId());
		int seedPrice = itemManager.getItemPrice(crop.getSeedId());
		ItemComposition product = itemManager.getItemComposition(crop.getHarvestedItemId());
		int productPrice = itemManager.getItemPrice(crop.getHarvestedItemId());
		tooltip = "<html>Cost: " + crop.getSeedAmount() + "x " + seed.getName() + " " +
			QuantityFormatter.quantityToStackSize(seedPrice * crop.getSeedAmount()) + "gp<br>" +
			"Products: " + amount + "x " + product.getName() + " " +
			QuantityFormatter.quantityToStackSize(productPrice * amount) + "gp<br>" +
			"Profit: " + QuantityFormatter.quantityToStackSize(getProfit()) + "gp<br>" +
			"Farming XP: " + QuantityFormatter.quantityToStackSize(Math.round(xp)) + "</html>";
	}

	/** Map region this patch sits in, used to group patches into run cycles. */
	int getRegionId()
	{
		if (restoredRegionId != null)
		{
			return restoredRegionId;
		}
		return latestHarvestWorldPoint == null ? -1 : latestHarvestWorldPoint.getRegionID();
	}

	/** Friendly patch name (e.g. "Catherby"), or the crop name if the region isn't a known herb patch. */
	String getPatchName()
	{
		final HerbPatches.HerbPatch patch = HerbPatches.forRegion(getRegionId());
		return patch != null ? patch.name : crop.getDisplayName();
	}

	public String toString()
	{
		return "[" + this.crop.getDisplayName() + ", amount: " + this.amount + "x]";
	}

}
