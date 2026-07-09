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

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Side-panel container hosting the two tabs of the plugin: the live run "Tracker" and the herb
 * "Planner".
 */
class FarmingProfitPanel extends PluginPanel
{
	private final FarmingProfitTrackerPanel trackerPanel;
	private final FarmingProfitPlannerPanel plannerPanel;

	FarmingProfitPanel(ItemManager itemManager, boolean xpMode)
	{
		// wrap=true so long tracker/planner lists scroll
		super(true);

		trackerPanel = new FarmingProfitTrackerPanel(itemManager, xpMode);
		plannerPanel = new FarmingProfitPlannerPanel(itemManager);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));

		final JPanel display = new JPanel(new BorderLayout());
		final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(0, 0, 8, 0));

		final MaterialTab trackerTab = new MaterialTab("Tracker", tabGroup, trackerPanel);
		final MaterialTab plannerTab = new MaterialTab("Planner", tabGroup, plannerPanel);
		tabGroup.addTab(trackerTab);
		tabGroup.addTab(plannerTab);
		tabGroup.select(trackerTab);

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void addRun(FarmingProfitRun run)
	{
		trackerPanel.addRun(run);
	}

	void loadHeaderIcon(BufferedImage img)
	{
		trackerPanel.loadHeaderIcon(img);
	}

	void setXpMode(boolean xpMode)
	{
		trackerPanel.setXpMode(xpMode);
	}

	void setGraphConfig(boolean showGraph, java.awt.Color expectedColor, java.awt.Color actualColor)
	{
		trackerPanel.setGraphConfig(showGraph, expectedColor, actualColor);
	}

	void updatePlanner(PlannerInputs inputs, java.util.List<HerbResult> results)
	{
		plannerPanel.update(inputs, results);
	}

	void updateMixology(MixologyStatus status)
	{
		plannerPanel.updateMixology(status);
	}

	void updateHerbRun(java.util.List<HerbPatchStatus> statuses, boolean showLocationIcons)
	{
		trackerPanel.updateHerbRun(statuses, showLocationIcons);
	}

	void updateCompostBucket(int uses)
	{
		trackerPanel.setCompostBucket(uses);
	}

	void setCurrentHarvest(FarmingProfitRun run)
	{
		trackerPanel.setCurrentHarvest(run);
	}

	void setRunHistoryChanged(Runnable onChanged)
	{
		trackerPanel.setOnChanged(onChanged);
	}

	void setDebugLogSupplier(java.util.function.Supplier<String> supplier)
	{
		trackerPanel.setDebugLogSupplier(supplier);
	}

	java.util.List<RunRecord> exportRecords()
	{
		return trackerPanel.exportRecords();
	}

	void importRecords(java.util.List<RunRecord> records)
	{
		trackerPanel.importRecords(records);
	}

	void setPlannerRefreshAction(Runnable onRefresh)
	{
		plannerPanel.setOnRefresh(onRefresh);
	}
}
