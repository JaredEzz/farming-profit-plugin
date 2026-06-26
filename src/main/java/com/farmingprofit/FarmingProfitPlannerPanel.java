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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * The herb planner tab: ranks every herb you can plant by GP profit (mains) or Farming XP
 * (ironmen) for a full run, using live Grand Exchange prices and your detected level/gear.
 */
class FarmingProfitPlannerPanel extends JPanel
{
	private final ItemManager itemManager;
	private final JLabel assumptionsLabel = new JLabel();
	private final JLabel bestLabel = new JLabel();
	private final JPanel rowsContainer = new JPanel();

	/** Set by the plugin; asks it to re-read live state and call {@link #update}. */
	private Runnable onRefresh = () -> { };

	FarmingProfitPlannerPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		// Header: assumptions + best herb + refresh
		final JPanel header = new JPanel(new BorderLayout());
		header.setBorder(new EmptyBorder(8, 10, 8, 10));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JPanel headerText = new JPanel(new GridLayout(2, 1));
		headerText.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bestLabel.setFont(FontManager.getRunescapeSmallFont());
		bestLabel.setForeground(Color.WHITE);
		assumptionsLabel.setFont(FontManager.getRunescapeSmallFont());
		assumptionsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		headerText.add(bestLabel);
		headerText.add(assumptionsLabel);
		header.add(headerText, BorderLayout.CENTER);

		final JButton refresh = new JButton("↻");
		refresh.setToolTipText("Recalculate from live prices and your current level/gear");
		refresh.setFocusPainted(false);
		refresh.setMargin(new java.awt.Insets(0, 6, 0, 6));
		refresh.addActionListener(e -> onRefresh.run());
		header.add(refresh, BorderLayout.EAST);

		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		layoutPanel.add(header);
		layoutPanel.add(rowsContainer);

		bestLabel.setText("Herb planner");
		assumptionsLabel.setText("Log in to rank herbs for your account.");
	}

	void setOnRefresh(Runnable onRefresh)
	{
		this.onRefresh = onRefresh;
	}

	/**
	 * Recompute and redraw the ranked herb list for the given inputs.
	 */
	void update(PlannerInputs in)
	{
		final int lives = in.getCompost().getHarvestLives();
		final double itemBonus = in.itemBonusPct();

		final List<HerbResult> results = new ArrayList<>();
		for (Crop crop : Crop.values())
		{
			if (crop.getPatchType() != PatchType.HERBS)
			{
				continue;
			}
			if (crop.getFarmingLevel() > in.getFarmingLevel())
			{
				continue;
			}

			final double yieldPerPatch = HerbYield.expectedYieldPerPatch(
				crop, in.getFarmingLevel(), lives, itemBonus, in.isAttas());

			final int seedPrice = itemManager.getItemPrice(crop.getSeedId());
			final int herbPrice = itemManager.getItemPrice(crop.getHarvestedItemId());

			final double profitPerRun = (yieldPerPatch * herbPrice - (double) seedPrice * crop.getSeedAmount())
				* in.getPatches();
			final double xpPerRun = (crop.getPlantXp() + yieldPerPatch * crop.getHarvestXp()) * in.getPatches();

			results.add(new HerbResult(crop, yieldPerPatch, Math.round(profitPerRun), xpPerRun));
		}

		final Comparator<HerbResult> byValue = in.isXpMode()
			? Comparator.comparingDouble(r -> r.xpPerRun)
			: Comparator.comparingLong(r -> r.profitPerRun);
		results.sort(byValue.reversed());

		// Header text
		final String mode = in.isXpMode() ? "XP" : "profit";
		final StringBuilder gear = new StringBuilder();
		gear.append("Lv ").append(in.getFarmingLevel());
		if (!in.isLevelKnown())
		{
			gear.append(" (assumed)");
		}
		gear.append(" · ").append(in.getCompost());
		gear.append(" · ").append(in.getPatches()).append(" patches");
		if (in.isMagicSecateurs())
		{
			gear.append(" · secateurs");
		}
		if (in.isFarmingCape())
		{
			gear.append(" · cape");
		}
		if (in.isAttas())
		{
			gear.append(" · attas");
		}
		assumptionsLabel.setText("<html>" + gear + "</html>");

		if (results.isEmpty())
		{
			bestLabel.setText("No herbs available");
		}
		else
		{
			final HerbResult best = results.get(0);
			bestLabel.setText("Best: " + best.crop.getDisplayName() + " — " + value(best, in.isXpMode())
				+ "/run " + mode);
		}

		// Rows
		rowsContainer.removeAll();
		for (HerbResult r : results)
		{
			rowsContainer.add(buildRow(r, in.isXpMode()));
		}
		rowsContainer.revalidate();
		rowsContainer.repaint();
	}

	private JPanel buildRow(HerbResult r, boolean xpMode)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBorder(new EmptyBorder(4, 0, 0, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(r.crop.getHarvestedItemId()).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		final JPanel info = new JPanel(new GridLayout(2, 1));
		info.setBorder(new EmptyBorder(4, 6, 4, 6));
		info.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		final JLabel name = new JLabel(r.crop.getDisplayName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		final JLabel detail = new JLabel(String.format("%.1f/patch", r.yieldPerPatch));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		info.add(name);
		info.add(detail);
		row.add(info, BorderLayout.CENTER);

		final JLabel valueLabel = new JLabel(value(r, xpMode));
		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		valueLabel.setBorder(new EmptyBorder(0, 4, 0, 6));
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		if (!xpMode && r.profitPerRun < 0)
		{
			valueLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		}
		else
		{
			valueLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		}
		valueLabel.setText(value(r, xpMode));
		row.add(valueLabel, BorderLayout.EAST);

		return row;
	}

	private static String value(HerbResult r, boolean xpMode)
	{
		if (xpMode)
		{
			return QuantityFormatter.quantityToStackSize(Math.round(r.xpPerRun)) + " xp";
		}
		return QuantityFormatter.quantityToStackSize(r.profitPerRun) + " gp";
	}

	/** One ranked herb, with its expected yield and per-run value. */
	private static final class HerbResult
	{
		private final Crop crop;
		private final double yieldPerPatch;
		private final long profitPerRun;
		private final double xpPerRun;

		private HerbResult(Crop crop, double yieldPerPatch, long profitPerRun, double xpPerRun)
		{
			this.crop = crop;
			this.yieldPerPatch = yieldPerPatch;
			this.profitPerRun = profitPerRun;
			this.xpPerRun = xpPerRun;
		}
	}
}
