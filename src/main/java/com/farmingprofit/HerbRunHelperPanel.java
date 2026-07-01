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
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ColorUtil;

/**
 * The live "herb run" helper below the tracker: lists each herb patch's crop and ready/growing
 * state, plus a countdown to when the whole next run can be started (the last patch to finish).
 * The countdown animates every second on the EDT; the underlying statuses are refreshed by the
 * plugin on the client thread.
 */
class HerbRunHelperPanel extends JPanel
{
	private static final String TWO_TONE =
		"<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

	private final ItemManager itemManager;
	private final JLabel countdownLabel = new JLabel();
	private final JPanel rowsContainer = new JPanel();
	/** Footer showing the bottomless compost bucket's remaining uses; hidden when not carried. */
	private final JLabel compostLabel = new JLabel();

	private List<HerbPatchStatus> cached = Collections.emptyList();
	private boolean showLocationIcons = true;
	private final Timer ticker = new Timer(1000, e -> refreshCountdown());

	HerbRunHelperPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 0, 0, 0));

		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 8, 6, 8));
		final JLabel title = new JLabel("Herb run");
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.WEST);
		countdownLabel.setFont(FontManager.getRunescapeSmallFont());
		countdownLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		header.add(countdownLabel, BorderLayout.EAST);

		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		compostLabel.setFont(FontManager.getRunescapeSmallFont());
		compostLabel.setBorder(new EmptyBorder(4, 8, 2, 8));
		compostLabel.setVisible(false);

		add(header);
		add(rowsContainer);
		add(compostLabel);

		refreshCountdown();
	}

	/** Set the bottomless compost bucket's remaining uses (negative hides the footer). EDT only. */
	void setCompostBucket(int uses)
	{
		if (uses < 0)
		{
			compostLabel.setVisible(false);
		}
		else
		{
			compostLabel.setText(twoTone("Compost bucket: ", uses + " left",
				ColorScheme.LIGHT_GRAY_COLOR));
			compostLabel.setVisible(true);
		}
		compostLabel.revalidate();
		compostLabel.repaint();
	}

	/** Replace the cached patch statuses and redraw. Call on the EDT. */
	void setStatuses(List<HerbPatchStatus> statuses, boolean showLocationIcons)
	{
		this.showLocationIcons = showLocationIcons;
		cached = statuses != null ? statuses : Collections.emptyList();
		rowsContainer.removeAll();
		for (HerbPatchStatus s : cached)
		{
			rowsContainer.add(buildRow(s));
		}
		refreshCountdown();
		rowsContainer.revalidate();
		rowsContainer.repaint();
	}

	private JPanel buildRow(HerbPatchStatus s)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));

		// Left: a small location icon (the teleport item players use to reach the patch).
		if (showLocationIcons && s.teleportItemId > 0)
		{
			final JLabel loc = new JLabel();
			loc.setPreferredSize(new Dimension(28, 24));
			loc.setHorizontalAlignment(SwingConstants.CENTER);
			itemManager.getImage(s.teleportItemId).addTo(loc);
			row.add(loc, BorderLayout.WEST);
		}

		final JPanel info = new JPanel(new GridLayout(2, 1));
		info.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		info.setBorder(new EmptyBorder(0, 6, 0, 0));
		final JLabel name = new JLabel(s.patchName);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		final JLabel detail = new JLabel(stateText(s));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(stateColor(s.state));
		info.add(name);
		info.add(detail);
		row.add(info, BorderLayout.CENTER);

		// Right: the herb icon.
		if (s.crop != Crop.UNKNOWN)
		{
			final JLabel herb = new JLabel();
			herb.setPreferredSize(new Dimension(24, 24));
			herb.setHorizontalAlignment(SwingConstants.CENTER);
			itemManager.getImage(s.crop.getHarvestedItemId()).addTo(herb);
			row.add(herb, BorderLayout.EAST);
		}

		return row;
	}

	private static String stateText(HerbPatchStatus s)
	{
		switch (s.state)
		{
			case READY:
				return (s.crop != Crop.UNKNOWN ? s.crop.getDisplayName() + " — " : "") + "Ready";
			case GROWING:
				return (s.crop != Crop.UNKNOWN ? s.crop.getDisplayName() + " — " : "") + "Growing"
					+ (s.staleVerify ? " (verify)" : "");
			case DISEASED:
				return "Diseased — needs attention";
			case DEAD:
				return "Dead — needs attention";
			case EMPTY:
			default:
				return "Empty";
		}
	}

	private static Color stateColor(HerbPatchStatus.State state)
	{
		switch (state)
		{
			case READY:
				return ColorScheme.PROGRESS_COMPLETE_COLOR;
			case GROWING:
				return ColorScheme.LIGHT_GRAY_COLOR;
			case DISEASED:
			case DEAD:
				return ColorScheme.PROGRESS_ERROR_COLOR;
			case EMPTY:
			default:
				return ColorScheme.MEDIUM_GRAY_COLOR;
		}
	}

	/** Recompute the "next run in mm:ss" label from cached ready times. EDT only; no client reads. */
	private void refreshCountdown()
	{
		final long now = Instant.now().getEpochSecond();
		final long maxReady = cached.stream()
			.filter(HerbPatchStatus::countsTowardRun)
			.mapToLong(s -> s.readyAtEpochSeconds)
			.max()
			.orElse(0);
		final long rem = maxReady - now;
		if (maxReady == 0 || rem <= 0)
		{
			countdownLabel.setText(twoTone("Next run: ", "ready", ColorScheme.PROGRESS_COMPLETE_COLOR));
		}
		else
		{
			countdownLabel.setText(twoTone("Next run in ",
				String.format("%d:%02d", rem / 60, rem % 60), ColorScheme.LIGHT_GRAY_COLOR));
		}
	}

	private static String twoTone(String key, String value, Color keyColor)
	{
		return String.format(TWO_TONE, ColorUtil.toHexColor(keyColor), key, value);
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		ticker.start();
		refreshCountdown();
	}

	@Override
	public void removeNotify()
	{
		ticker.stop();
		super.removeNotify();
	}
}
