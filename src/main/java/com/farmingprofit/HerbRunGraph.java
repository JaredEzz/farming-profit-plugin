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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A small cumulative line graph for one herb run: the expected (target) line and the actual
 * (harvested) line, each plotted as a running total after each patch, with configurable colours.
 */
class HerbRunGraph extends JPanel
{
	private static final int HEIGHT = 90;
	private static final int PAD = 8;

	private final double[] cumExpected;
	private final long[] cumActual;
	private final Color expectedColor;
	private final Color actualColor;

	/**
	 * @param cumExpected cumulative expected herbs after each patch (length = patch count)
	 * @param cumActual   cumulative actual herbs after each patch (same length)
	 */
	HerbRunGraph(double[] cumExpected, long[] cumActual, Color expectedColor, Color actualColor)
	{
		this.cumExpected = cumExpected;
		this.cumActual = cumActual;
		this.expectedColor = expectedColor;
		this.actualColor = actualColor;
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(4, 4, 4, 4));
		setPreferredSize(new Dimension(0, HEIGHT));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		final int n = cumExpected.length;
		if (n == 0)
		{
			return;
		}

		final Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			final int w = getWidth();
			final int h = getHeight();
			final int left = PAD;
			final int right = w - PAD;
			final int top = PAD;
			final int bottom = h - PAD;

			// Scale to the largest cumulative value across both series (min 1 to avoid /0).
			double max = 1;
			for (int i = 0; i < n; i++)
			{
				max = Math.max(max, Math.max(cumExpected[i], cumActual[i]));
			}

			// Baseline
			g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
			g2.drawLine(left, bottom, right, bottom);

			final Stroke line = new BasicStroke(2f);
			g2.setStroke(line);

			drawSeriesD(g2, cumExpected, n, left, right, top, bottom, max, expectedColor);
			drawSeriesL(g2, cumActual, n, left, right, top, bottom, max, actualColor);

			drawTotals(g2, right, bottom);
		}
		finally
		{
			g2.dispose();
		}
	}

	/** Run total "actual / expected (±%)" in the bottom-right corner (the lines end top-right). */
	private void drawTotals(Graphics2D g2, int right, int bottom)
	{
		final int n = cumActual.length;
		final long actual = cumActual[n - 1];
		final double expected = cumExpected[n - 1];

		final String text;
		final Color color;
		if (expected > 0)
		{
			final long pct = Math.round((actual - expected) / expected * 100.0);
			text = String.format("%d / %.1f  %s%d%%", actual, expected, pct >= 0 ? "+" : "", pct);
			color = pct >= 0 ? actualColor : ColorScheme.PROGRESS_ERROR_COLOR;
		}
		else
		{
			text = actual + " herbs";
			color = Color.WHITE;
		}

		g2.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics fm = g2.getFontMetrics();
		g2.setColor(color);
		g2.drawString(text, right - fm.stringWidth(text), bottom - 2);
	}

	private static int xAt(int i, int n, int left, int right)
	{
		return n <= 1 ? (left + right) / 2 : left + (right - left) * i / (n - 1);
	}

	private static int yAt(double v, int top, int bottom, double max)
	{
		return (int) Math.round(bottom - (bottom - top) * (v / max));
	}

	private static void drawSeriesD(Graphics2D g2, double[] vals, int n, int left, int right, int top, int bottom, double max, Color c)
	{
		g2.setColor(c);
		int px = 0, py = 0;
		for (int i = 0; i < n; i++)
		{
			final int x = xAt(i, n, left, right);
			final int y = yAt(vals[i], top, bottom, max);
			if (i > 0)
			{
				g2.drawLine(px, py, x, y);
			}
			g2.fillOval(x - 2, y - 2, 4, 4);
			px = x;
			py = y;
		}
	}

	private static void drawSeriesL(Graphics2D g2, long[] vals, int n, int left, int right, int top, int bottom, double max, Color c)
	{
		g2.setColor(c);
		int px = 0, py = 0;
		for (int i = 0; i < n; i++)
		{
			final int x = xAt(i, n, left, right);
			final int y = yAt(vals[i], top, bottom, max);
			if (i > 0)
			{
				g2.drawLine(px, py, x, y);
			}
			g2.fillOval(x - 2, y - 2, 4, 4);
			px = x;
			py = y;
		}
	}
}
