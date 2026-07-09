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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * The live farm-run tracker tab. Patch harvests are grouped into "herb runs" (a run = one lap of
 * your patches; re-harvesting a patch already in the run, or pressing "New run", starts the next
 * one). Runs are listed newest-first; older runs collapse to a header. Each run shows a cumulative
 * expected-vs-actual graph and the per-patch data beneath.
 */
@Slf4j
class FarmingProfitTrackerPanel extends JPanel
{
	private static final String HTML_LABEL_TEMPLATE =
		"<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");
	private static final int MAX_RUNS = 200;

	/** One herb run: the patches harvested in it (in harvest order) and the regions they cover. */
	private static final class RunGroup
	{
		final int number;
		final List<FarmingProfitRun> patches = new ArrayList<>();
		final Set<Integer> regionIds = new HashSet<>();

		RunGroup(int number)
		{
			this.number = number;
		}
	}

	private final JPanel runsContainer = new JPanel();
	private final HerbRunHelperPanel herbRunPanel;
	private final JLabel overallIcon = new JLabel();
	private final JLabel overallValueLabel = new JLabel();
	private final JLabel overallPatchesLabel = new JLabel();
	private final JLabel overallProductsLabel = new JLabel();
	private final ItemManager itemManager;

	/** Runs, newest first. */
	private final List<RunGroup> groups = new ArrayList<>();
	private int nextRunNumber = 1;
	/** When set, the next harvested patch starts a fresh run (the "New run" button). */
	private boolean forceNewRun = false;
	/** Run numbers currently expanded; new runs auto-expand and collapse the previous. */
	private final Set<Integer> expanded = new HashSet<>();

	/** The patch being harvested right now, shown live above the runs; null when not harvesting. */
	private FarmingProfitRun currentHarvest;
	/** Fired when committed runs change, so the plugin can persist them. */
	private Runnable onChanged = () -> { };
	/** Backed by {@link DebugLog#snapshot()}; supplies the current in-memory debug log for copying. */
	private Supplier<String> debugLogSupplier = () -> "";

	private boolean xpMode;
	private boolean showGraph = true;
	private Color expectedColor = new Color(144, 238, 144);
	private Color actualColor = new Color(255, 215, 0);

	FarmingProfitTrackerPanel(ItemManager itemManager, boolean xpMode)
	{
		this.itemManager = itemManager;
		this.xpMode = xpMode;
		this.herbRunPanel = new HerbRunHelperPanel(itemManager);

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		add(layoutPanel, BorderLayout.NORTH);

		// Overall totals across all runs.
		final JPanel overallPanel = new JPanel();
		overallPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overallPanel.setLayout(new BorderLayout());

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

		final JMenuItem reset = new JMenuItem("Reset");
		reset.addActionListener(e ->
		{
			groups.clear();
			expanded.clear();
			nextRunNumber = 1;
			rebuild();
			onChanged.run();
		});
		final JMenuItem copyDebugLog = new JMenuItem("Copy debug log");
		copyDebugLog.addActionListener(e -> copyDebugLogToClipboard());
		final JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
		popupMenu.add(reset);
		popupMenu.add(copyDebugLog);
		overallPanel.setComponentPopupMenu(popupMenu);

		// "New run" button forces the next patch into a fresh run.
		final JButton newRun = new JButton("＋ New run");
		newRun.setFocusPainted(false);
		newRun.setFont(FontManager.getRunescapeSmallFont());
		newRun.setToolTipText("Start a new herb run; the next patch you harvest begins it");
		newRun.addActionListener(e -> forceNewRun = true);
		final JPanel newRunRow = new JPanel(new BorderLayout());
		newRunRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		newRunRow.setBorder(new EmptyBorder(6, 0, 2, 0));
		newRunRow.add(newRun, BorderLayout.CENTER);

		// Collapse/expand all PAST runs at once (the latest run is left untouched).
		final JButton hideAll = new JButton("Hide all");
		hideAll.setFocusPainted(false);
		hideAll.setFont(FontManager.getRunescapeSmallFont());
		hideAll.setMargin(new java.awt.Insets(2, 6, 2, 6));
		hideAll.setToolTipText("Collapse all past runs (keeps the latest run as-is)");
		hideAll.addActionListener(e -> setPastRunsExpanded(false));
		final JButton showAll = new JButton("Show all");
		showAll.setFocusPainted(false);
		showAll.setFont(FontManager.getRunescapeSmallFont());
		showAll.setMargin(new java.awt.Insets(2, 6, 2, 6));
		showAll.setToolTipText("Expand all past runs");
		showAll.addActionListener(e -> setPastRunsExpanded(true));
		final JPanel collapseRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
		collapseRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		collapseRow.setBorder(new EmptyBorder(0, 0, 2, 0));
		collapseRow.add(hideAll);
		collapseRow.add(showAll);

		runsContainer.setLayout(new BoxLayout(runsContainer, BoxLayout.Y_AXIS));

		layoutPanel.add(overallPanel);
		layoutPanel.add(newRunRow);
		layoutPanel.add(collapseRow);
		layoutPanel.add(runsContainer);
		layoutPanel.add(herbRunPanel);

		rebuild();
	}

	void updateHerbRun(List<HerbPatchStatus> statuses, boolean showLocationIcons)
	{
		herbRunPanel.setStatuses(statuses, showLocationIcons);
	}

	/** Show the bottomless compost bucket's remaining uses below the herb run; pass -1 to hide it. */
	void setCompostBucket(int uses)
	{
		herbRunPanel.setCompostBucket(uses);
	}

	/** Permanently delete one run from the history and persist the change. */
	private void deleteRun(RunGroup group)
	{
		groups.remove(group);
		expanded.remove(group.number);
		rebuild();
		onChanged.run();
	}

	/** Collapse or expand every past run (all groups except index 0, the newest). */
	private void setPastRunsExpanded(boolean expand)
	{
		for (int i = 1; i < groups.size(); i++)
		{
			final int number = groups.get(i).number;
			if (expand)
			{
				expanded.add(number);
			}
			else
			{
				expanded.remove(number);
			}
		}
		rebuild();
	}

	void addRun(FarmingProfitRun run)
	{
		final int regionId = run.getRegionId();
		RunGroup current = groups.isEmpty() ? null : groups.get(0);
		final boolean startNew = forceNewRun || current == null || current.regionIds.contains(regionId);
		log.debug("addRun: region={} crop={} amount={} forceNewRun={} currentRun={} regionAlreadyInCurrent={} -> startNew={}",
			regionId, run.getCrop(), run.getAmount(), forceNewRun,
			current == null ? "none" : current.number,
			current != null && current.regionIds.contains(regionId), startNew);
		if (startNew)
		{
			current = new RunGroup(nextRunNumber++);
			groups.add(0, current);
			expanded.clear();              // collapse older runs
			expanded.add(current.number);  // expand the new one
			forceNewRun = false;
		}
		current.patches.add(run);
		current.regionIds.add(regionId);
		currentHarvest = null; // this patch is now committed; the live row goes away
		// Cap retained runs so a marathon session can't grow the list unbounded.
		while (groups.size() > MAX_RUNS)
		{
			final RunGroup dropped = groups.remove(groups.size() - 1);
			expanded.remove(dropped.number);
		}
		rebuild();
		onChanged.run();
	}

	void setXpMode(boolean xpMode)
	{
		if (this.xpMode != xpMode)
		{
			this.xpMode = xpMode;
			rebuild();
		}
	}

	/** Show the patch currently being harvested live (counting up), or pass null to hide it. */
	void setCurrentHarvest(FarmingProfitRun run)
	{
		this.currentHarvest = run;
		rebuild();
	}

	/** Notified whenever the committed run history changes, so the plugin can persist it. */
	void setOnChanged(Runnable onChanged)
	{
		this.onChanged = onChanged != null ? onChanged : () -> { };
	}

	/** Supplies the current in-memory debug log for the "Copy debug log" menu item. */
	void setDebugLogSupplier(Supplier<String> supplier)
	{
		this.debugLogSupplier = supplier != null ? supplier : () -> "";
	}

	/** Copy the in-memory debug log to the system clipboard, or explain how to turn it on if empty. */
	private void copyDebugLogToClipboard()
	{
		final String log = debugLogSupplier.get();
		if (log == null || log.isEmpty())
		{
			JOptionPane.showMessageDialog(this,
				"Debug log is empty. Enable 'Debug' -> 'Enable debug logging' in the plugin config, "
					+ "reproduce the issue, then come back and copy it.",
				"Copy debug log", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(log), null);
		final int lines = log.split("\n", -1).length;
		JOptionPane.showMessageDialog(this,
			"Copied " + lines + " debug log line" + (lines == 1 ? "" : "s") + " to the clipboard.",
			"Copy debug log", JOptionPane.INFORMATION_MESSAGE);
	}

	/** Flatten the committed runs to records for saving (newest run first, patches in harvest order). */
	List<RunRecord> exportRecords()
	{
		final List<RunRecord> records = new ArrayList<>();
		for (RunGroup g : groups)
		{
			for (FarmingProfitRun r : g.patches)
			{
				final long millis = r.getLatestHarvestTime() == null ? 0
					: r.getLatestHarvestTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
				records.add(new RunRecord(g.number, r.getRegionId(), r.getCrop().name(),
					r.getAmount(), r.getExpectedYield(), r.getProfit(), r.getXp(), millis));
			}
		}
		return records;
	}

	/** Rebuild the committed runs from saved records (replaces current history). EDT only. */
	void importRecords(List<RunRecord> records)
	{
		groups.clear();
		expanded.clear();
		int maxNumber = 0;
		if (records != null)
		{
			final Map<Integer, RunGroup> byNumber = new LinkedHashMap<>();
			for (RunRecord rec : records)
			{
				// Crop.UNKNOWN is the marker for a cleared dead patch (a 0-herb run visit), so it is
				// kept rather than skipped — dead patches must survive a reload.
				final Crop crop = parseCrop(rec.crop);
				final LocalDateTime time = rec.timeMillis > 0
					? LocalDateTime.ofInstant(Instant.ofEpochMilli(rec.timeMillis), ZoneId.systemDefault())
					: null;
				final RunGroup g = byNumber.computeIfAbsent(rec.runNumber, RunGroup::new);
				g.patches.add(new FarmingProfitRun(crop, rec.amount, rec.regionId, rec.profit, rec.xp,
					rec.expectedYield, time));
				g.regionIds.add(rec.regionId);
				maxNumber = Math.max(maxNumber, rec.runNumber);
			}
			groups.addAll(byNumber.values());
			groups.sort((a, b) -> Integer.compare(b.number, a.number)); // newest first
		}
		nextRunNumber = maxNumber + 1;
		if (!groups.isEmpty())
		{
			expanded.add(groups.get(0).number);
		}
		rebuild();
	}

	private static Crop parseCrop(String name)
	{
		try
		{
			return Crop.valueOf(name);
		}
		catch (IllegalArgumentException | NullPointerException ex)
		{
			return Crop.UNKNOWN;
		}
	}

	void setGraphConfig(boolean showGraph, Color expectedColor, Color actualColor)
	{
		// A corrupted/hand-edited config Color item can decode to null; fall back to the defaults.
		this.showGraph = showGraph;
		this.expectedColor = expectedColor != null ? expectedColor : new Color(144, 238, 144);
		this.actualColor = actualColor != null ? actualColor : new Color(255, 215, 0);
		rebuild();
	}

	void loadHeaderIcon(BufferedImage img)
	{
		SwingUtilities.invokeLater(() -> overallIcon.setIcon(new ImageIcon(img)));
	}

	private void rebuild()
	{
		runsContainer.removeAll();

		// The live "currently harvesting" row lives below the current run's chart (see buildRunPanel).
		// Only when there's no run yet (first patch of the session) does it stand alone at the top.
		if (currentHarvest != null && groups.isEmpty())
		{
			runsContainer.add(buildCurrentHarvestRow(currentHarvest));
		}

		long overallProfit = 0;
		double overallXp = 0;
		int overallProducts = 0;
		int overallPatches = 0;

		for (int i = 0; i < groups.size(); i++)
		{
			final RunGroup group = groups.get(i);
			runsContainer.add(buildRunPanel(group, i == 0));
			for (FarmingProfitRun run : group.patches)
			{
				overallProfit += run.getProfit();
				overallXp += run.getXp();
				overallProducts += run.getAmount();
				overallPatches++;
			}
		}

		if (xpMode)
		{
			overallValueLabel.setText(htmlLabel("Total XP: ", Math.round(overallXp)));
		}
		else
		{
			overallValueLabel.setText(htmlLabel("Total profit: ", overallProfit));
		}
		overallPatchesLabel.setText(htmlLabel("Total patches: ", overallPatches));
		overallProductsLabel.setText(htmlLabel("Total products: ", overallProducts));

		runsContainer.revalidate();
		runsContainer.repaint();
	}

	/**
	 * Build one run: a clickable header, and (when expanded) the graph, the live harvesting row
	 * (newest run only, below the chart), and the per-patch rows.
	 */
	private JPanel buildRunPanel(RunGroup group, boolean isNewest)
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 0, 0, 0));

		long actual = 0;
		double expected = 0;
		for (FarmingProfitRun run : group.patches)
		{
			actual += run.getAmount();
			expected += run.getExpectedYield();
		}
		final boolean isExpanded = expanded.contains(group.number);
		final boolean showLive = isNewest && currentHarvest != null;

		panel.add(buildHeader(group, actual, expected, isExpanded));

		if (isExpanded)
		{
			if (showGraph && !group.patches.isEmpty())
			{
				panel.add(buildGraph(group));
			}
			// Live "currently harvesting" row sits directly below the chart.
			if (showLive)
			{
				panel.add(buildCurrentHarvestRow(currentHarvest));
			}
			for (int i = group.patches.size() - 1; i >= 0; i--)
			{
				panel.add(buildPatchRow(group.patches.get(i)));
			}
		}
		else if (showLive)
		{
			// Collapsed current run: still surface the live progress under the header.
			panel.add(buildCurrentHarvestRow(currentHarvest));
		}
		return panel;
	}

	private JPanel buildHeader(RunGroup group, long actual, double expected, boolean isExpanded)
	{
		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 8, 6, 8));

		final JPanel text = new JPanel(new GridLayout(2, 1));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final String arrow = isExpanded ? "▾ " : "▸ ";
		final JLabel title = new JLabel(arrow + "Herb run #" + group.number
			+ "  ·  " + group.patches.size() + " patch" + (group.patches.size() == 1 ? "" : "es"));
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(Color.WHITE);

		// Second line: date/time, plus the totals only when collapsed (when expanded the graph
		// shows the totals in its bottom-right instead, to avoid a cramped header).
		final JLabel sub = new JLabel(subText(group, actual, expected, isExpanded));
		sub.setFont(FontManager.getRunescapeSmallFont());

		text.add(title);
		text.add(sub);
		header.add(text, BorderLayout.CENTER);

		// Delete this run (with a confirm). The button consumes its own click, so it won't also toggle
		// the header's expand/collapse.
		final JButton delete = new JButton("✕");
		delete.setFocusPainted(false);
		delete.setFont(FontManager.getRunescapeSmallFont());
		delete.setMargin(new java.awt.Insets(0, 6, 0, 6));
		delete.setToolTipText("Delete this run");
		delete.addActionListener(e ->
		{
			final int ok = JOptionPane.showConfirmDialog(this,
				"Delete herb run #" + group.number + "? This can't be undone.",
				"Delete run", JOptionPane.YES_NO_OPTION);
			if (ok == JOptionPane.YES_OPTION)
			{
				deleteRun(group);
			}
		});
		header.add(delete, BorderLayout.EAST);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (expanded.contains(group.number))
				{
					expanded.remove(group.number);
				}
				else
				{
					expanded.add(group.number);
				}
				rebuild();
			}
		});
		return header;
	}

	private String subText(RunGroup group, long actual, double expected, boolean isExpanded)
	{
		final String gray = ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR);
		final String date = runDate(group);
		if (isExpanded)
		{
			return "<html><span style='color:" + gray + "'>" + date + "</span></html>";
		}
		return "<html><span style='color:" + gray + "'>" + date + "  ·  </span>"
			+ totalsInner(actual, expected) + "</html>";
	}

	private String runDate(RunGroup group)
	{
		if (group.patches.isEmpty())
		{
			return "";
		}
		final LocalDateTime t = group.patches.get(0).getLatestHarvestTime();
		return t == null ? "" : t.format(DATE_FMT);
	}

	/** Inner spans (no &lt;html&gt; wrapper) of "9 / 9.2 +x%", percentage coloured over/under. */
	private String totalsInner(long actual, double expected)
	{
		if (expected <= 0)
		{
			return "<span style='color:white'>" + actual + " herbs</span>";
		}
		final long pct = Math.round((actual - expected) / expected * 100.0);
		final String pctStr = (pct >= 0 ? "+" : "") + pct + "%";
		final Color pctColor = pct >= 0 ? actualColor : ColorScheme.PROGRESS_ERROR_COLOR;
		return String.format("<span style='color:white'>%d / %.1f</span> <span style='color:%s'>%s</span>",
			actual, expected, ColorUtil.toHexColor(pctColor), pctStr);
	}

	private HerbRunGraph buildGraph(RunGroup group)
	{
		final int n = group.patches.size();
		final double[] cumExpected = new double[n];
		final long[] cumActual = new long[n];
		double e = 0;
		long a = 0;
		for (int i = 0; i < n; i++)
		{
			e += group.patches.get(i).getExpectedYield();
			a += group.patches.get(i).getAmount();
			cumExpected[i] = e;
			cumActual[i] = a;
		}
		return new HerbRunGraph(cumExpected, cumActual, expectedColor, actualColor);
	}

	/** One patch line beneath the graph: icon, patch name, actual/expected, and profit/XP. */
	private JPanel buildPatchRow(FarmingProfitRun run)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setToolTipText(run.getTooltip());

		// A 0-herb UNKNOWN run is the marker for a cleared dead patch.
		final boolean dead = run.getCrop() == Crop.UNKNOWN;

		final JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (dead)
		{
			icon.setText("✗");
			icon.setFont(FontManager.getRunescapeSmallFont());
			icon.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		}
		else
		{
			itemManager.getImage(run.getCrop().getHarvestedItemId()).addTo(icon);
		}
		row.add(icon, BorderLayout.WEST);

		final JPanel info = new JPanel(new GridLayout(2, 1));
		info.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		info.setBorder(new EmptyBorder(0, 6, 0, 0));
		final JLabel name = new JLabel(run.getPatchName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		final String yield = dead ? "Dead patch cleared"
			: run.getExpectedYield() > 0
				? String.format("%d / %.1f", run.getAmount(), run.getExpectedYield())
				: run.getAmount() + "x " + run.getCrop().getDisplayName();
		final JLabel detail = new JLabel(yield);
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(dead ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		info.add(name);
		info.add(detail);
		row.add(info, BorderLayout.CENTER);

		final JLabel value = new JLabel(dead ? "—" : xpMode
			? QuantityFormatter.quantityToStackSize(Math.round(run.getXp())) + " xp"
			: QuantityFormatter.quantityToStackSize(run.getProfit()) + " gp");
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(value, BorderLayout.EAST);

		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	/** The live "currently harvesting" row: patch + herb, count / ~expected, ~remaining, progress bar. */
	private JPanel buildCurrentHarvestRow(FarmingProfitRun run)
	{
		final int amount = run.getAmount();
		final double expected = run.getExpectedYield();
		final long expRounded = Math.round(expected);
		final long remaining = Math.max(0, expRounded - amount);

		final JPanel box = new JPanel(new BorderLayout(0, 2));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(6, 8, 6, 8));

		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		final JLabel title = new JLabel("● Harvesting · " + run.getPatchName());
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		header.add(title, BorderLayout.WEST);
		final JLabel count = new JLabel(expected > 0 ? amount + " / ~" + expRounded : amount + "x");
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(Color.WHITE);
		count.setHorizontalAlignment(SwingConstants.RIGHT);
		header.add(count, BorderLayout.EAST);
		box.add(header, BorderLayout.NORTH);

		final JLabel detail = new JLabel(run.getCrop().getDisplayName()
			+ (expected > 0 ? "  ·  ~" + remaining + " left" : ""));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		box.add(detail, BorderLayout.CENTER);

		if (expected > 0)
		{
			final JProgressBar bar = new JProgressBar(0, (int) Math.max(expRounded, amount));
			bar.setValue(amount);
			bar.setForeground(actualColor);
			bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
			bar.setBorderPainted(false);
			bar.setPreferredSize(new Dimension(0, 6));
			box.add(bar, BorderLayout.SOUTH);
		}
		return box;
	}

	private static String htmlLabel(String key, long value)
	{
		final String valueStr = QuantityFormatter.quantityToStackSize(value);
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}
}
