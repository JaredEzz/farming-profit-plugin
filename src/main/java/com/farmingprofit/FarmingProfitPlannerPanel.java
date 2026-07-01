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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

	/** Mastering Mixology section (below the herb ranking); hidden when disabled in config. */
	private final JPanel mixologyContainer = new JPanel();
	private boolean mixologyExpanded = true;
	private MixologyStatus mixologyStatus;

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

		mixologyContainer.setLayout(new BoxLayout(mixologyContainer, BoxLayout.Y_AXIS));
		mixologyContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		mixologyContainer.setBorder(new EmptyBorder(10, 0, 0, 0));
		mixologyContainer.setVisible(false);

		layoutPanel.add(header);
		layoutPanel.add(rowsContainer);
		layoutPanel.add(mixologyContainer);

		bestLabel.setText("Herb planner");
		assumptionsLabel.setText("Log in to rank herbs for your account.");
	}

	void setOnRefresh(Runnable onRefresh)
	{
		this.onRefresh = onRefresh;
	}

	/**
	 * Redraw the planner for the given inputs and pre-ranked herb list. The ranking (which reads
	 * live GE prices) is computed on the client thread by the plugin and passed in here, so this
	 * method only touches Swing and never the client.
	 */
	void update(PlannerInputs in, List<HerbResult> results)
	{
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

	/**
	 * A null status (feature disabled) hides the section; a status with no goals shows balances
	 * only; with goals it shows herbs needed per resin colour. Called on the EDT.
	 */
	void updateMixology(MixologyStatus status)
	{
		this.mixologyStatus = status;
		rebuildMixology();
	}

	private void rebuildMixology()
	{
		mixologyContainer.removeAll();
		final MixologyStatus status = mixologyStatus;
		if (status == null)
		{
			mixologyContainer.setVisible(false);
			mixologyContainer.revalidate();
			mixologyContainer.repaint();
			return;
		}
		mixologyContainer.setVisible(true);

		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 8, 6, 8));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		final JLabel title = new JLabel((mixologyExpanded ? "▾ " : "▸ ") + "Mastering Mixology");
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.WEST);
		if (status.hasGoals() && status.isAllMet())
		{
			final JLabel right = new JLabel("all met ✓");
			right.setFont(FontManager.getRunescapeSmallFont());
			right.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
			right.setHorizontalAlignment(SwingConstants.RIGHT);
			header.add(right, BorderLayout.EAST);
		}
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				mixologyExpanded = !mixologyExpanded;
				rebuildMixology();
			}
		});
		mixologyContainer.add(header);

		if (mixologyExpanded)
		{
			if (!status.hasGoals())
			{
				final JLabel note = new JLabel("<html>Showing resin balances. Set reward goals in the "
					+ "Easy Mixology plugin to see herbs needed.</html>");
				note.setFont(FontManager.getRunescapeSmallFont());
				note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				note.setBorder(new EmptyBorder(4, 8, 0, 8));
				mixologyContainer.add(note);
			}
			for (MixologyStatus.ColourLine line : status.getLines())
			{
				mixologyContainer.add(buildMixologyRow(line, status.hasGoals()));
			}
		}

		mixologyContainer.revalidate();
		mixologyContainer.repaint();
	}

	private JPanel buildMixologyRow(MixologyStatus.ColourLine line, boolean hasGoals)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBorder(new EmptyBorder(4, 0, 0, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JPanel info = new JPanel(new GridLayout(2, 1));
		info.setBorder(new EmptyBorder(4, 8, 4, 6));
		info.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JLabel name = new JLabel(hasGoals
			? line.colourName + "  " + line.balance + " / " + line.goal
			: line.colourName + "  " + line.balance);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		final JLabel detail = new JLabel();
		detail.setFont(FontManager.getRunescapeSmallFont());
		if (!hasGoals)
		{
			detail.setText("resin");
			detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else if (line.met)
		{
			detail.setText("met ✓");
			detail.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		}
		else
		{
			final String herb = line.bestHerbName != null ? " " + line.bestHerbName : "";
			detail.setText("need " + line.deficit + " · ~" + line.herbsNeeded + herb);
			detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		info.add(name);
		info.add(detail);
		row.add(info, BorderLayout.CENTER);
		return row;
	}
}
