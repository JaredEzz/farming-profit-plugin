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
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * The live farm-run tracker tab: one box per harvested patch plus a running total. Values are
 * shown as Grand Exchange profit or Farming XP depending on the resolved {@link DisplayMode}.
 */
@Slf4j
class FarmingProfitTrackerPanel extends JPanel
{
	private static final String HTML_LABEL_TEMPLATE =
		"<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

	private final JPanel runsContainer = new JPanel();
	private final JLabel overallIcon = new JLabel();
	private final JLabel overallValueLabel = new JLabel();
	private final JLabel overallPatchesLabel = new JLabel();
	private final JLabel overallProductsLabel = new JLabel();
	private final ItemManager itemManager;

	/** Harvested patches, newest first. */
	private final List<FarmingProfitRun> runs = new ArrayList<>();

	/** Whether to show Farming XP (true) or GE profit (false). */
	private boolean xpMode;

	FarmingProfitTrackerPanel(ItemManager itemManager, boolean xpMode)
	{
		this.itemManager = itemManager;
		this.xpMode = xpMode;

		// Set panel properties
		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		// Create layout panel
		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		// Create panel that will contain overall data
		JPanel overallPanel = new JPanel();
		overallPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overallPanel.setLayout(new BorderLayout());

		// Add icon and contents
		final JPanel overallInfo = new JPanel();
		overallInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overallInfo.setLayout(new GridLayout(3, 1));
		overallInfo.setBorder(new EmptyBorder(0, 10, 0, 0));
		overallValueLabel.setFont(FontManager.getRunescapeSmallFont());
		overallPatchesLabel.setFont(FontManager.getRunescapeSmallFont());
		overallProductsLabel.setFont(FontManager.getRunescapeSmallFont());
		overallInfo.add(overallValueLabel, 0);
		overallInfo.add(overallPatchesLabel, 1);
		overallInfo.add(overallProductsLabel, 2);
		overallPanel.add(overallIcon, BorderLayout.WEST);
		overallPanel.add(overallInfo, BorderLayout.CENTER);

		// Create reset all popup menu
		final JMenuItem reset = new JMenuItem("Reset");
		reset.addActionListener(e ->
		{
			runs.clear();
			rebuild();
		});

		final JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
		popupMenu.add(reset);
		overallPanel.setComponentPopupMenu(popupMenu);

		// Create runs wrapper
		runsContainer.setLayout(new BoxLayout(runsContainer, BoxLayout.Y_AXIS));
		layoutPanel.add(overallPanel);
		layoutPanel.add(runsContainer);

		rebuild();
	}

	void addRun(FarmingProfitRun run)
	{
		runs.add(0, run);
		rebuild();
	}

	void setXpMode(boolean xpMode)
	{
		if (this.xpMode != xpMode)
		{
			this.xpMode = xpMode;
			rebuild();
		}
	}

	void loadHeaderIcon(BufferedImage img)
	{
		// SpriteManager delivers the sprite on the client thread; hop to the EDT for Swing.
		SwingUtilities.invokeLater(() -> overallIcon.setIcon(new ImageIcon(img)));
	}

	/**
	 * Rebuild every run box and the overall totals from the {@link #runs} list. Called on add,
	 * remove, reset and whenever the display mode changes.
	 */
	private void rebuild()
	{
		runsContainer.removeAll();

		long overallProfit = 0;
		double overallXp = 0;
		int overallProducts = 0;

		for (FarmingProfitRun run : runs)
		{
			final FarmingProfitBox box = new FarmingProfitBox(itemManager, run, xpMode);

			final JMenuItem remove = new JMenuItem("Remove");
			remove.addActionListener(e ->
			{
				runs.remove(run);
				rebuild();
			});
			final JPopupMenu popupMenu = new JPopupMenu();
			popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
			popupMenu.add(remove);
			box.setComponentPopupMenu(popupMenu);

			runsContainer.add(box);

			overallProfit += run.getProfit();
			overallXp += run.getXp();
			overallProducts += run.getAmount();
		}

		if (xpMode)
		{
			overallValueLabel.setText(htmlLabel("Total XP: ", Math.round(overallXp)));
		}
		else
		{
			overallValueLabel.setText(htmlLabel("Total profit: ", overallProfit));
		}
		overallPatchesLabel.setText(htmlLabel("Total patches: ", runs.size()));
		overallProductsLabel.setText(htmlLabel("Total products: ", overallProducts));

		runsContainer.revalidate();
		runsContainer.repaint();
	}

	private static String htmlLabel(String key, long value)
	{
		final String valueStr = QuantityFormatter.quantityToStackSize(value);
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}
}
